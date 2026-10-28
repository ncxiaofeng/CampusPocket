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
     * ehall 会话过期时，先用 CASTGC 静默换票 + 打开一次卡片页（建立 psxykapp 应用上下文），
     * 再重试一次；主凭据也失效才提示用户登录。
     *
     * 401/403 既可能是会话失效，也可能是 ehall 的“当前角色权限不足”；
     * 两种都先重建会话重试一次，避免把本可以自愈的 403 直接抛给用户。
     */
    private suspend fun postData(path: String, payload: String): JsonObject {
        var response = postDataRaw(path, payload)
        if (response.sessionExpired) {
            val renewed = ssoManager
                ?.ensureServiceSession(CampusEndpoints.EHALL_CARD_SERVICE_URL) == true
            if (!renewed) {
                throw SessionExpiredException("统一认证已过期，请重新登录")
            }
            primeCardPage()
            response = postDataRaw(path, payload)
            if (response.sessionExpired) {
                throw SessionExpiredException("一卡通接口拒绝了当前会话，请打开校园卡官方页刷新一次")
            }
        }
        val text = response.body
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

    /**
     * 用浏览器的方式把 ehall 卡片页“预热”一次。
     *
     * 只靠 CAS 换票得到的 `MOD_AUTH_CAS` 可能还没有 psxykapp 的应用/角色上下文；
     * 官方页面加载时会请求 getAppConfig / emappagelog，这里按同样顺序补上。
     * 全部按尽力而为处理，失败不影响后续原接口重试。
     */
    private suspend fun primeCardPage() {
        val urls = listOf(
            CampusEndpoints.EHALL_CARD_SERVICE_URL,
            "$baseUrl/psfw/sys/xgpspubapp/indexmenu/getAppConfig.do" +
                "?appId=$CARD_APP_ID&appName=psxykapp",
            "$baseUrl/psfw/sys/emappagelog/config/psxykapp.do"
        )
        urls.forEach { url ->
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
                    .header("Referer", CampusEndpoints.EHALL_CARD_SERVICE_URL)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    response.body?.string()
                }
            }
        }
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
            .header("Referer", CampusEndpoints.EHALL_CARD_SERVICE_URL)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val status = response.code
            // 401/403：会话失效或 ehall 角色权限不足，交给 postData 重建会话后重试。
            if (status == 401 || status == 403) {
                return RawResponse(text, sessionExpired = true, status = status)
            }
            if (!response.isSuccessful) {
                throw IOException("一卡通系统返回 $status")
            }
            val expired = text.contains("authserver") ||
                text.contains("login_slogin") ||
                text.trimStart().startsWith("<")
            return RawResponse(text, expired, status)
        }
    }

    private data class RawResponse(
        val body: String,
        val sessionExpired: Boolean,
        val status: Int
    )

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

    private companion object {
        /** ehall 里 psxykapp（一卡通）的应用 ID，来自官方页面 getAppConfig 请求。 */
        const val CARD_APP_ID = "5476902092873882"
    }
}
