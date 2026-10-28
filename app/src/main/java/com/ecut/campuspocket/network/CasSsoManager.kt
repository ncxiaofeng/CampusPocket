package com.ecut.campuspocket.network

import com.ecut.campuspocket.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * 统一身份认证（CAS）单点登录。
 *
 * 学校所有需要登录的系统共用 `authserver.ecut.edu.cn` 的 CASTGC：
 * 课表 / 成绩 / 考试 / 空闲教室 / 通知公告（正方教务）、宿舍电费（xgwx）、
 * 网上办事大厅与一卡通（ehall）都只是 CAS 的不同 service。
 *
 * 因此真正需要用户登录的只有一次：在 CAS 页面拿到 CASTGC 并加密保存。
 * 其它系统会话（各自的 JSESSIONID）缺失或过期时，用 CASTGC 静默换票即可。
 */
class CasSsoManager(
    private val authRepository: AuthRepository,
    private val client: OkHttpClient
) {

    private val bootstrapMutex = Mutex()

    /**
     * 本机是否保存了仍在有效期内的 CAS 主凭据（CASTGC）。
     * 注意：这里只判断“有没有”，服务端是否已注销要等真正换票时才知道。
     */
    suspend fun hasMasterSession(): Boolean = withContext(Dispatchers.IO) {
        hasCasTicket(loadCasCookieHeader())
    }

    /**
     * 用 CAS 主凭据为某个 service 静默换票。
     *
     * 流程：GET `/authserver/login?service=<serviceUrl>`。
     * CAS 识别到有效 CASTGC 后会 302 回 service 并带上 ticket，
     * service 校验 ticket 后下发自己的 JSESSIONID，由 [CampusCookieJar] 自动写回加密库。
     * 之后原来的业务请求就能直接通过。
     *
     * @return true 表示最终回到了 service（换票成功）；false 表示主凭据缺失或已过期。
     */
    suspend fun ensureServiceSession(serviceUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasMasterSession()) return@withContext false
        bootstrapMutex.withLock {
            val request = Request.Builder()
                .url(buildServiceLoginUrl(serviceUrl))
                .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url
                    val finalPath = finalUrl.encodedPath
                    when {
                        // 业务系统校验 ticket 失败时会直接返回 5xx（正方 jziotlogin 就是 500），
                        // 不能因为“已经离开 CAS 域”就当成换票成功。
                        !response.isSuccessful -> false
                        // 跟随重定向后仍停在 CAS 域，说明 CAS 让用户重新登录，即主凭据失效。
                        finalUrl.host == CampusEndpoints.CAS_HOST -> false
                        // 被 service 退回它自己的登录页，同样视为失败。
                        finalPath.contains("login_slogin") || finalPath.contains("/authserver/") -> false
                        else -> true
                    }
                }
            } catch (_: IOException) {
                false
            }
        }
    }

    /**
     * 从 WebView 里读到的 Cookie 串中提取 CAS 主凭据并保存。
     * 登录任意一个学校系统后都可以调用，实现“一次登录，全站共用”。
     */
    suspend fun harvestCasCookies(raw: String) {
        if (!hasCasTicket(raw)) return
        authRepository.saveCasMasterCookies(raw)
    }

    private suspend fun loadCasCookieHeader(): String =
        authRepository.loadCookiesForHost(CampusEndpoints.CAS_HOST)
            .joinToString("; ") { "${it.name}=${it.value}" }

    companion object {

        fun hasCasTicket(raw: String): Boolean = casTicketValue(raw) != null

        /** 取出 CASTGC 的值；没有则返回 null。用于判断主凭据是否真的换了新值。 */
        fun casTicketValue(raw: String): String? {
            raw.split(";").forEach { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@forEach
                val name = part.substring(0, index).trim().lowercase()
                if (name in CampusEndpoints.CAS_TICKET_COOKIE_NAMES) {
                    return part.substring(index + 1).trim()
                }
            }
            return null
        }

        fun buildServiceLoginUrl(serviceUrl: String): String =
            CampusEndpoints.AUTH_SERVER_LOGIN_URL +
                "?service=" +
                URLEncoder.encode(serviceUrl, "UTF-8")
    }
}
