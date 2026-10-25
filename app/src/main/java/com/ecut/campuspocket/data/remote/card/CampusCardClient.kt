package com.ecut.campuspocket.data.remote.card

import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.CasSsoManager
import com.ecut.campuspocket.network.SessionExpiredException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.YearMonth

data class CampusCardOverview(
    val balance: Double,
    val cardState: String,
    val cardNumber: String,
    val monthSpend: Double,
    val monthRecharge: Double,
    val monthSpendCount: Int,
    val monthRechargeCount: Int
)

data class CampusCardBill(
    val time: String,
    val location: String,
    val amount: Double,
    val isRecharge: Boolean,
    val machine: String,
    val typeCode: String
)

data class CampusCardBillPage(
    val items: List<CampusCardBill>,
    val total: Int
)

/**
 * ehall 一卡通只读接口。
 *
 * 需要先在“校园卡”官方页完成统一认证；CampusWebPageScreen 会把 ehall 会话 Cookie
 * 存进加密库，这里通过 CampusCookieJar 注入。
 */
class CampusCardClient(
    private val client: OkHttpClient,
    private val baseUrl: String = CampusEndpoints.EHALL_BASE_URL,
    private val ssoManager: CasSsoManager? = null
) {

    suspend fun fetchOverview(): CampusCardOverview = withContext(Dispatchers.IO) {
        val info = postData(
            path = "/psfw/sys/psxykapp/modules/WisCardViewController/getWisCardInformation.do",
            payload = """{"XFNY":"${YearMonth.now()}"}"""
        )
        val infoData = info.getAsJsonObject("data")
        val csxx = infoData.getAsJsonObject("CSXX")
        val csdm = csxx?.string("CSDM").orEmpty()
        val jkfs = csxx?.string("JKFS").orEmpty()
        val base = postData(
            path = "/psfw/sys/psxykapp/modules/CampusCardActionController/queryBaseInfo.do",
            payload = """{"CSDM":"$csdm","JKFS":"$jkfs"}"""
        )
        val baseData = base.getAsJsonObject("data")
        val month = infoData.getAsJsonObject("MONTHBILL")
        CampusCardOverview(
            balance = baseData?.double("BALANCE") ?: 0.0,
            cardState = baseData?.string("CARD_STATE").orEmpty(),
            cardNumber = baseData?.string("CARD_ACCNUM").orEmpty(),
            monthSpend = month?.double("XFJE") ?: 0.0,
            monthRecharge = month?.double("CZJE") ?: 0.0,
            monthSpendCount = month?.int("XFZS") ?: 0,
            monthRechargeCount = month?.int("CZZS") ?: 0
        )
    }

    suspend fun fetchBills(page: Int, pageSize: Int): CampusCardBillPage =
        withContext(Dispatchers.IO) {
            val payload =
                """{"pagenum":${page.coerceAtLeast(1)},""" +
                    """"pagesize":${pageSize.coerceIn(1, 100)},"querySetting":"[]"}"""
            val root = postData(
                path = "/psfw/sys/psxykapp/modules/WisCardViewController/getWisCardBill.do",
                payload = payload
            )
            val data = root.getAsJsonObject("data")
            val items = data?.getAsJsonArray("BILL_ARRAY")?.mapNotNull { element ->
                val item = element.asJsonObject
                val amount = item.double("JE")
                CampusCardBill(
                    time = item.string("SJ"),
                    location = item.string("ZDMC"),
                    amount = amount,
                    isRecharge = item.string("XFFS") == "1",
                    machine = item.string("MC"),
                    typeCode = item.string("LXDM")
                )
            }.orEmpty()
            val total = data?.getAsJsonObject("COUNT")?.int("COUNT") ?: items.size
            CampusCardBillPage(items = items, total = total)
        }

    /**
     * ehall 会话过期时，先用 CASTGC 静默换票再重试一次；
     * 主凭据也失效才提示用户登录。
     */
    private suspend fun postData(path: String, payload: String): JsonObject {
        val first = postDataRaw(path, payload)
        val text = if (!first.sessionExpired) {
            first.body
        } else {
            val renewed = ssoManager
                ?.ensureServiceSession(CampusEndpoints.EHALL_CARD_SERVICE_URL) == true
            if (!renewed) {
                throw SessionExpiredException("统一认证已过期，请重新登录")
            }
            val second = postDataRaw(path, payload)
            if (second.sessionExpired) {
                throw SessionExpiredException("统一认证已过期，请重新登录")
            }
            second.body
        }
        val root = try {
            JsonParser.parseString(text).asJsonObject
        } catch (exception: Exception) {
            throw IOException("一卡通返回数据无法解析", exception)
        }
        val code = root.get("code")?.takeIf { !it.isJsonNull }?.asString
        if (code != "0") {
            throw IOException(
                root.get("msg")?.takeIf { !it.isJsonNull }?.asString
                    ?: "一卡通系统返回错误"
            )
        }
        return root
    }

    private fun postDataRaw(path: String, payload: String): RawResponse {
        val body = FormBody.Builder().add("data", payload).build()
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body)
            .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .header("Origin", baseUrl)
            .header("Referer", "$baseUrl/psfw/sys/psxykapp/*default/index.do")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("一卡通系统返回 ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            val expired = text.contains("authserver") ||
                text.contains("login_slogin") ||
                text.trimStart().startsWith("<")
            return RawResponse(text, expired)
        }
    }

    private data class RawResponse(val body: String, val sessionExpired: Boolean)

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else value.asString
    }

    private fun JsonObject.double(key: String): Double {
        val value = get(key) ?: return 0.0
        if (value.isJsonNull) return 0.0
        return runCatching { value.asDouble }
            .getOrElse { value.asString.toDoubleOrNull() ?: 0.0 }
    }

    private fun JsonObject.int(key: String): Int {
        val value = get(key) ?: return 0
        if (value.isJsonNull) return 0
        return runCatching { value.asInt }
            .getOrElse { value.asString.toIntOrNull() ?: 0 }
    }
}
