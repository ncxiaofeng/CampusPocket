package com.ecut.campuspocket.data.remote.electricity

import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.CasSsoManager
import com.ecut.campuspocket.network.SessionExpiredException
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class XgwxSnapshot(
    val userId: String,
    val houseLabel: String,
    val houseAddress: String,
    val meterId: String,
    val balance: Double?,
    val dailyUsage: List<Pair<LocalDate, Double>>
)

/**
 * 今日校园“宿舍缴费”背后的水电 H5 接口（xgwx.ecut.edu.cn）。
 *
 * 只读：查绑定宿舍、电表余额、按天查询用量，不做缴费。
 */
class XgwxElectricityClient(
    private val client: OkHttpClient,
    private val baseUrl: String = CampusEndpoints.ELECTRICITY_H5_BASE,
    private val ssoManager: CasSsoManager? = null
) {

    suspend fun fetchSnapshot(): XgwxSnapshot = withContext(Dispatchers.IO) {
        val userId = loadIndexUserId()
        val houseJson = postForm(
            path = "/app/app/api/user/searchBindHouseListForMoney",
            body = FormBody.Builder().add("userid", userId).build(),
            referer = CampusEndpoints.ELECTRICITY_INDEX_URL
        )
        val root = parseObject(houseJson)
        if (root.string("ret") != "1") {
            throw IOException(root.string("msg").ifBlank { "宿舍绑定信息读取失败" })
        }
        val houses = root.getAsJsonArray("data") ?: JsonArray()
        val selection = selectMeter(houses)
            ?: throw IOException("没有找到可查询的电表")

        val today = LocalDate.now()
        val usage = queryDosageChunked(
            houseId = selection.houseId,
            meterId = selection.meterId,
            start = today.minusDays(29),
            end = today
        )

        XgwxSnapshot(
            userId = userId,
            houseLabel = selection.houseLabel,
            houseAddress = selection.houseAddress,
            meterId = selection.meterId,
            balance = selection.balance,
            dailyUsage = usage
        )
    }

    /**
     * 读取宿舍缴费首页里的 userid。
     *
     * 会话过期时页面会 302 到 CAS 登录页，拿不到 userid。
     * 此时用本机保存的 CASTGC 静默换票，再重试一次，用户不需要重新登录。
     */
    private suspend fun loadIndexUserId(): String {
        val first = get(CampusEndpoints.ELECTRICITY_INDEX_URL)
        extractUserId(first)?.let { return it }

        if (ssoManager?.ensureServiceSession(CampusEndpoints.ELECTRICITY_INDEX_URL) == true) {
            val second = get(CampusEndpoints.ELECTRICITY_INDEX_URL)
            extractUserId(second)?.let { return it }
        }
        throw SessionExpiredException("统一认证已过期，请重新登录")
    }

    private fun extractUserId(html: String): String? =
        Regex("""userid=(\d+)""").find(html)?.groupValues?.get(1)

    private fun selectMeter(houses: JsonArray): MeterSelection? {
        houses.forEach { houseElement ->
            val house = houseElement.asJsonObject
            val meters = house.getAsJsonArray("meter") ?: JsonArray()
            meters.forEach { meterElement ->
                val meter = meterElement.asJsonObject
                val meterId = meter.string("meter_id")
                val houseId = house.string("house_id")
                if (meterId.isNotBlank() && houseId.isNotBlank()) {
                    return MeterSelection(
                        houseId = houseId,
                        meterId = meterId,
                        houseLabel = house.string("house_no").ifBlank {
                            house.string("customer_no")
                        },
                        houseAddress = meter.string("house_addr").ifBlank {
                            house.string("house_addr")
                        },
                        balance = meter.doubleOrNull("meter_balance")
                    )
                }
            }
        }
        return null
    }

    /**
     * 按 7 天分块查询最近 30 天；接口若拒绝范围查询，则对这一块逐日兜底。
     */
    private fun queryDosageChunked(
        houseId: String,
        meterId: String,
        start: LocalDate,
        end: LocalDate
    ): List<Pair<LocalDate, Double>> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-M-d")
        val result = mutableListOf<Pair<LocalDate, Double>>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            val chunkEnd = if (cursor.plusDays(6).isAfter(end)) end else cursor.plusDays(6)
            var part = queryDosageRange(houseId, meterId, cursor, chunkEnd)
            if (part.isEmpty()) {
                val days = ChronoUnit.DAYS.between(cursor, chunkEnd).toInt()
                part = (0..days).mapNotNull { offset ->
                    val date = cursor.plusDays(offset.toLong())
                    val value = runCatching {
                        queryDosage(
                            houseId = houseId,
                            meterId = meterId,
                            start = date.format(formatter),
                            end = date.format(formatter)
                        )
                    }.getOrNull() ?: return@mapNotNull null
                    date to value
                }
            }
            result += part
            cursor = chunkEnd.plusDays(1)
        }
        return result
    }

    /**
     * 按日期范围查询每日用量。data.list[].data_date 为 yyyyMMdd。
     */
    private fun queryDosageRange(
        houseId: String,
        meterId: String,
        start: LocalDate,
        end: LocalDate
    ): List<Pair<LocalDate, Double>> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-M-d")
        val body = FormBody.Builder()
            .add("houseId", houseId)
            .add("meterId", meterId)
            .add("startTime", start.format(formatter))
            .add("endTime", end.format(formatter))
            .build()
        val json = postForm(
            path = "/app/app/api/data/dosage",
            body = body,
            referer = "$baseUrl/app/wechat/houseMeterSurplus.jsp"
        )
        val root = parseObject(json)
        val data = root.get("data")?.takeIf { !it.isJsonNull }?.asJsonObject ?: return emptyList()
        val list = data.getAsJsonArray("list") ?: return emptyList()
        return list.mapNotNull { element ->
            val item = element.asJsonObject
            val dateText = item.string("data_date")
            val date = runCatching {
                LocalDate.parse(dateText, DateTimeFormatter.BASIC_ISO_DATE)
            }.getOrNull() ?: return@mapNotNull null
            val usage = item.doubleOrNull("energy_used") ?: return@mapNotNull null
            date to usage
        }
    }

    private fun queryDosage(
        houseId: String,
        meterId: String,
        start: String,
        end: String
    ): Double {
        val body = FormBody.Builder()
            .add("houseId", houseId)
            .add("meterId", meterId)
            .add("startTime", start)
            .add("endTime", end)
            .build()
        val json = postForm(
            path = "/app/app/api/data/dosage",
            body = body,
            referer = "$baseUrl/app/wechat/houseMeterSurplus.jsp"
        )
        val root = parseObject(json)
        if (root.string("ret") != "1") return 0.0
        val data = root.get("data")
        if (data == null || data.isJsonNull) return 0.0
        if (data.isJsonObject) {
            val objectData = data.asJsonObject
            val total = objectData.get("totle") ?: objectData.get("total")
            if (total != null && !total.isJsonNull) {
                return runCatching { total.asDouble }.getOrDefault(0.0)
            }
            val list = objectData.getAsJsonArray("list") ?: return 0.0
            return list.sumOf { item ->
                runCatching {
                    item.asJsonObject.get("energy_used")?.asDouble ?: 0.0
                }.getOrDefault(0.0)
            }
        }
        return runCatching { data.asDouble }.getOrDefault(0.0)
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("宿舍缴费系统返回 ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun postForm(path: String, body: FormBody, referer: String): String {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body)
            .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .header("Referer", referer)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("宿舍缴费系统返回 ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            // 接口应该始终返回 JSON；被重定向到登录页时是 HTML。
            if (!text.trimStart().startsWith("{")) {
                throw SessionExpiredException("宿舍缴费会话已失效")
            }
            return text
        }
    }

    private fun parseObject(json: String): JsonObject {
        return try {
            JsonParser.parseString(json).asJsonObject
        } catch (exception: Exception) {
            throw IOException("宿舍缴费返回数据无法解析", exception)
        }
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else value.asString
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        val value: JsonElement = get(key) ?: return null
        if (value.isJsonNull) return null
        return value.asString.toDoubleOrNull()
    }

    private data class MeterSelection(
        val houseId: String,
        val meterId: String,
        val houseLabel: String,
        val houseAddress: String,
        val balance: Double?
    )
}
