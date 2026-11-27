package com.ecut.campuspocket.data.remote.electricity

import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.ConfigurationRequiredException
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
    private val ssoManager: CasSsoManager? = null,
    private val operatorPwdProvider: () -> String,
    private val clearSessionCookies: (suspend () -> Unit)? = null,
    private val onSessionRecovery: (suspend () -> Boolean)? = null
) {

    suspend fun fetchSnapshot(): XgwxSnapshot = withContext(Dispatchers.IO) {
        val candidates = candidateIndexUrls()
        val resolved = loadIndexUserId(candidates)
        var userId = resolved.userId
        var referer = resolved.resolvedUrl
        val houseJson = try {
            fetchBindHouses(userId, referer)
        } catch (expired: SessionExpiredException) {
            // 首页里读到旧缓存/半过期状态时，清掉损坏的 xgwx Cookie 并换票重试一次。
            if (!renewElectricitySession(candidates.first())) throw expired
            val retried = loadIndexUserId(candidates, allowRecovery = false)
            userId = retried.userId
            referer = retried.resolvedUrl
            fetchBindHouses(userId, referer)
        }
        val root = parseObject(houseJson)
        if (root.string("ret") != "1") {
            val msg = root.string("msg").ifBlank { "宿舍绑定信息读取失败" }
            if (looksLikeSessionError(msg)) {
                throw SessionExpiredException("宿舍缴费会话已失效：$msg")
            }
            throw IOException(msg)
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

    private fun fetchBindHouses(userId: String, referer: String): String =
        postForm(
            path = "/app/app/api/user/searchBindHouseListForMoney",
            body = FormBody.Builder().add("userid", userId).build(),
            referer = referer
        )

    private data class ResolvedUser(
        val userId: String,
        val resolvedUrl: String
    )

    private data class PageResponse(
        val finalUrl: String,
        val body: String
    )

    /**
     * 读取宿舍缴费首页里的 userid。
     *
     * 未配置 operatorPwd 时会依次尝试裸地址（index.jsp / houseMeterSurplus.jsp 等）；
     * 旧 xgwx Cookie 失效或损坏时，先清掉 xgwx 旧会话，再用 CASTGC 静默换票并重试。
     */
    private suspend fun loadIndexUserId(
        candidates: List<String>,
        allowRecovery: Boolean = true
    ): ResolvedUser {
        var lastError: IOException? = null
        var landedOnXgwxWithoutUserId = false

        fun tryCandidates(): ResolvedUser? {
            for (url in candidates) {
                val page = runCatching { getPage(url) }.onFailure {
                    if (it is IOException) lastError = it
                }.getOrNull() ?: continue
                val userId = extractUserId(page.body)
                if (userId != null) {
                    return ResolvedUser(userId = userId, resolvedUrl = page.finalUrl.ifBlank { url })
                }
                if (isXgwxAuthenticatedPage(page.finalUrl, page.body)) {
                    landedOnXgwxWithoutUserId = true
                }
            }
            return null
        }

        tryCandidates()?.let { return it }

        if (!allowRecovery) {
            if (landedOnXgwxWithoutUserId && operatorPwdProvider().isBlank()) {
                throw ConfigurationRequiredException(
                    "宿舍缴费系统拒绝了免参入口；可在「更多 → 高级配置」补充 operatorPwd，或直接用今日校园查电费"
                )
            }
            throw SessionExpiredException("统一认证已过期，请重新登录")
        }

        if (renewElectricitySession(candidates.first())) {
            tryCandidates()?.let { return it }
        }
        if (landedOnXgwxWithoutUserId && operatorPwdProvider().isBlank()) {
            throw ConfigurationRequiredException(
                "宿舍缴费系统需要 operatorPwd 才能识别宿舍；可在「更多 → 高级配置」填写，或直接用今日校园查电费"
            )
        }
        if (lastError != null && lastError !is SessionExpiredException &&
            !isRecoverableHttpError(lastError!!)
        ) {
            throw lastError!!
        }
        throw SessionExpiredException("统一认证或宿舍缴费会话已过期，请重新登录")
    }

    private suspend fun renewElectricitySession(indexUrl: String): Boolean {
        runCatching { clearSessionCookies?.invoke() }
        if (ssoManager?.ensureServiceSession(indexUrl) == true) {
            return true
        }
        if (onSessionRecovery?.invoke() == true) {
            runCatching { clearSessionCookies?.invoke() }
            if (ssoManager?.ensureServiceSession(indexUrl) == true) {
                return true
            }
            return true
        }
        return false
    }

    private fun isRecoverableHttpError(error: IOException): Boolean {
        val msg = error.message.orEmpty()
        return msg.contains("401") || msg.contains("403") || msg.contains("500") || msg.contains("502")
    }

    private fun candidateIndexUrls(): List<String> {
        val operatorPwd = operatorPwdProvider().trim()
        if (operatorPwd.isEmpty()) {
            throw ConfigurationRequiredException(
                "未配置电费参数：公开版不内置；可直接用「今日校园」查电费，或在「更多 → 高级配置」填写"
            )
        }
        return buildCandidateIndexUrls(operatorPwd, baseUrl)
    }

    private fun selectMeter(houses: JsonArray): MeterSelection? {
        val candidates = mutableListOf<Pair<Int, MeterSelection>>()
        houses.forEach { houseElement ->
            val house = houseElement.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val meters = house.get("meter")?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
            meters.forEach { meterElement ->
                val meter = meterElement.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val meterId = meter.string("meter_id")
                val houseId = house.string("house_id")
                if (meterId.isNotBlank() && houseId.isNotBlank()) {
                    val balance = meter.doubleOrNull("meter_balance")
                    val typeHint = listOf(
                        meter.string("meter_type"),
                        meter.string("meter_name"),
                        meter.string("meter_no"),
                        meter.string("remark")
                    ).joinToString(" ")
                    var score = 0
                    if (typeHint.contains("电") || typeHint.contains("elec", ignoreCase = true)) score += 10
                    if (typeHint.contains("水") || typeHint.contains("water", ignoreCase = true)) score -= 5
                    if (balance != null) score += 2
                    candidates += score to MeterSelection(
                        houseId = houseId,
                        meterId = meterId,
                        houseLabel = house.string("house_no").ifBlank {
                            house.string("customer_no")
                        },
                        houseAddress = meter.string("house_addr").ifBlank {
                            house.string("house_addr")
                        },
                        balance = balance
                    )
                }
            }
        }
        return candidates.maxByOrNull { it.first }?.second
    }

    /**
     * 按 7 天分块查询最近 30 天，并对缺失日期逐日补查。
     *
     * 注意：xgwx 服务端对 startTime / endTime 按字符串比较，必须用补零的 `yyyy-MM-dd`
     * （如 `2026-11-01`），否则每月 1~9 号会因缺少前导零而查不出数据。
     */
    private fun queryDosageChunked(
        houseId: String,
        meterId: String,
        start: LocalDate,
        end: LocalDate
    ): List<Pair<LocalDate, Double>> {
        val merged = java.util.TreeMap<LocalDate, Double>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            val chunkEnd = if (cursor.plusDays(6).isAfter(end)) end else cursor.plusDays(6)
            val part = try {
                queryDosageRange(houseId, meterId, cursor, chunkEnd)
            } catch (expired: SessionExpiredException) {
                throw expired
            } catch (_: IOException) {
                emptyList()
            }
            part.forEach { (date, usage) -> merged[date] = usage }
            cursor = chunkEnd.plusDays(1)
        }

        // 对范围查询未返回的中间空洞及近期缺失日期逐日补查（单日失败重试 1 次）。
        val yesterday = end.minusDays(1)
        val missingDates = computeMissingDates(
            foundDates = merged.keys,
            windowStart = start,
            yesterday = yesterday,
            recentDays = 15
        )
        missingDates.forEach { date ->
            val dateText = formatQueryDate(date)
            var value: Double? = null
            for (attempt in 0..1) {
                val result = runCatching {
                    queryDosage(
                        houseId = houseId,
                        meterId = meterId,
                        start = dateText,
                        end = dateText
                    )
                }
                val error = result.exceptionOrNull()
                if (error is SessionExpiredException) throw error
                value = result.getOrNull()
                if (value != null || error == null) break
            }
            if (value != null && value > 0.0) {
                merged[date] = value
            }
        }
        return merged.entries.map { it.key to it.value }
    }

    /**
     * 按日期范围查询每日用量。
     *
     * 只接受真正有读数的日期；0 度视为“接口未返回数据”。
     */
    private fun queryDosageRange(
        houseId: String,
        meterId: String,
        start: LocalDate,
        end: LocalDate
    ): List<Pair<LocalDate, Double>> {
        val body = FormBody.Builder()
            .add("houseId", houseId)
            .add("meterId", meterId)
            .add("startTime", formatQueryDate(start))
            .add("endTime", formatQueryDate(end))
            .build()
        val json = postForm(
            path = "/app/app/api/data/dosage",
            body = body,
            referer = "$baseUrl/app/wechat/houseMeterSurplus.jsp"
        )
        return parseDosageRangeJson(json)
    }

    /** 单日查询；没有有效读数时返回 null，而不是伪造 0 度。 */
    private fun queryDosage(
        houseId: String,
        meterId: String,
        start: String,
        end: String
    ): Double? {
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
        if (root.string("ret") != "1") {
            val msg = root.string("msg")
            if (looksLikeSessionError(msg)) {
                throw SessionExpiredException("宿舍缴费会话已失效：$msg")
            }
            return null
        }
        val data = root.get("data")
        if (data == null || data.isJsonNull) return null
        if (data.isJsonObject) {
            val objectData = data.asJsonObject
            val list = objectData.get("list")?.takeIf { it.isJsonArray }?.asJsonArray
            val listTotal = list?.sumOf { item ->
                runCatching {
                    val obj = item.asJsonObject
                    obj.doubleOrNull("energy_used")
                        ?: obj.doubleOrNull("energyUsed")
                        ?: obj.doubleOrNull("dosage")
                        ?: 0.0
                }.getOrDefault(0.0)
            } ?: 0.0
            if (listTotal > 0.0) return listTotal
            val total = objectData.get("totle") ?: objectData.get("total")
            val totalValue = total
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.let { runCatching { it.asDouble }.getOrNull() }
            return totalValue?.takeIf { it > 0.0 }
        }
        return runCatching { data.asDouble }.getOrNull()?.takeIf { it > 0.0 }
    }

    private fun getPage(url: String): PageResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw SessionExpiredException("宿舍缴费返回 ${response.code}")
            }
            if (!response.isSuccessful) {
                throw IOException("宿舍缴费系统返回 ${response.code}")
            }
            return PageResponse(
                finalUrl = response.request.url.toString(),
                body = response.body?.string().orEmpty()
            )
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
            if (response.code == 401 || response.code == 403) {
                throw SessionExpiredException("宿舍缴费会话已失效（HTTP ${response.code}）")
            }
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
        if (value.isJsonNull || !value.isJsonPrimitive) return ""
        return runCatching { value.asString }.getOrDefault("")
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        val value: JsonElement = get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return runCatching { value.asDouble }.getOrElse {
            value.asString.toDoubleOrNull()
        }
    }

    private data class MeterSelection(
        val houseId: String,
        val meterId: String,
        val houseLabel: String,
        val houseAddress: String,
        val balance: Double?
    )

    companion object {
        internal fun buildCandidateIndexUrls(
            operatorPwd: String,
            baseUrl: String = CampusEndpoints.ELECTRICITY_H5_BASE
        ): List<String> {
            val trimmed = operatorPwd.trim()
            val normalizedBase = baseUrl.trimEnd('/')
            if (trimmed.isEmpty()) return emptyList()
            return listOf(
                "$normalizedBase/app/wechat/index.jsp" +
                    "?appId=wx3be3089d48d67c4e&operatorId=admin" +
                    "&operatorPwd=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
            )
        }

        internal fun isXgwxAuthenticatedPage(finalUrl: String, html: String): Boolean {
            if (finalUrl.contains("authserver.ecut.edu.cn", ignoreCase = true)) return false
            if (finalUrl.contains("login", ignoreCase = true) && !finalUrl.contains("index.jsp")) return false
            if (html.contains("统一身份认证") && html.contains("password", ignoreCase = true)) return false
            return finalUrl.contains("xgwx.ecut.edu.cn", ignoreCase = true)
        }

        /** xgwx 接口要求补零日期 yyyy-MM-dd，否则每月 1~9 号会返回空。 */
        internal fun formatQueryDate(date: LocalDate): String =
            date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        internal fun extractUserId(html: String): String? {
            val invalidPlaceholders = setOf("null", "undefined", "none", "false", "true")
            val patterns = listOf(
                Regex("""userid\s*=\s*['"]?([A-Za-z0-9_-]{4,32})""", RegexOption.IGNORE_CASE),
                Regex(""""userid"\s*:\s*"([A-Za-z0-9_-]{4,32})"""", RegexOption.IGNORE_CASE)
            )
            return patterns.firstNotNullOfOrNull { regex ->
                regex.find(html)?.groupValues?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() && it.lowercase() !in invalidPlaceholders }
            }
        }

        internal fun looksLikeSessionError(msg: String): Boolean {
            if (msg.isBlank()) return false
            return listOf("登录", "失效", "过期", "超时", "认证", "session", "login")
                .any { msg.contains(it, ignoreCase = true) }
        }

        internal fun parseDosageDate(raw: String): LocalDate? {
            val cleaned = raw.trim()
            if (cleaned.isBlank()) return null
            val datePart = cleaned.substringBefore(' ').substringBefore('T')
            val digitsOnly = datePart.filter(Char::isDigit)
            if (digitsOnly.length == 8 && datePart.length == 8) {
                return runCatching {
                    LocalDate.parse(datePart, DateTimeFormatter.BASIC_ISO_DATE)
                }.getOrNull()
            }
            val normalized = datePart.replace('/', '-')
            return runCatching {
                LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d"))
            }.getOrNull() ?: runCatching {
                LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrNull()
        }

        internal fun parseDosageRangeJson(json: String): List<Pair<LocalDate, Double>> {
            val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
                ?: return emptyList()
            if (root.get("ret")?.takeIf { it.isJsonPrimitive }?.asString != "1") {
                val msg = root.get("msg")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (looksLikeSessionError(msg)) {
                    throw SessionExpiredException("宿舍缴费会话已失效：$msg")
                }
                return emptyList()
            }
            val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return emptyList()
            val list = data.get("list")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return emptyList()
            return list.mapNotNull { element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@mapNotNull null
                val dateText = listOf("data_date", "dataDate", "date")
                    .firstNotNullOfOrNull { key ->
                        item.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
                    }.orEmpty()
                val date = parseDosageDate(dateText) ?: return@mapNotNull null
                val usage = listOf("energy_used", "energyUsed", "dosage", "usage")
                    .firstNotNullOfOrNull { key ->
                        item.get(key)?.takeIf { it.isJsonPrimitive }?.let { prim ->
                            runCatching { prim.asDouble }.getOrElse { prim.asString.toDoubleOrNull() }
                        }
                    } ?: return@mapNotNull null
                if (usage <= 0.0) return@mapNotNull null
                date to usage
            }
        }

        /**
         * 计算需要逐日补查的缺失日期：
         * - 覆盖 [min(已有最早日, 近 recentDays 起点) .. yesterday] 中未返回的全部日期。
         */
        internal fun computeMissingDates(
            foundDates: Set<LocalDate>,
            windowStart: LocalDate,
            yesterday: LocalDate,
            recentDays: Int = 15
        ): List<LocalDate> {
            if (yesterday.isBefore(windowStart)) return emptyList()
            val recentStart = yesterday.minusDays((recentDays - 1).coerceAtLeast(0).toLong())
                .let { if (it.isBefore(windowStart)) windowStart else it }
            val earliestFound = foundDates.minOrNull()
            val checkStart = when {
                earliestFound == null -> recentStart
                earliestFound.isBefore(recentStart) -> maxOf(earliestFound, windowStart)
                else -> recentStart
            }
            val missing = mutableListOf<LocalDate>()
            var cursor = checkStart
            while (!cursor.isAfter(yesterday)) {
                if (cursor !in foundDates) missing += cursor
                cursor = cursor.plusDays(1)
            }
            return missing
        }
    }
}
