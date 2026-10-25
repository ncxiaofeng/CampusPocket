package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.AuthCredentialEntity
import com.ecut.campuspocket.data.local.AuthDao
import com.ecut.campuspocket.data.local.CookieDao
import com.ecut.campuspocket.data.local.CookieEntity
import com.ecut.campuspocket.data.local.CourseDao
import com.ecut.campuspocket.data.local.ElectricityDao
import com.ecut.campuspocket.data.local.ExamDao
import com.ecut.campuspocket.data.local.GradeDao
import com.ecut.campuspocket.network.CampusEndpoints
import kotlinx.coroutines.flow.Flow

/**
 * 凭据读写。所有内容都落在 SQLCipher 加密库中。
 */
class AuthRepository(
    private val authDao: AuthDao,
    private val cookieDao: CookieDao,
    private val electricityDao: ElectricityDao,
    private val courseDao: CourseDao,
    private val gradeDao: GradeDao,
    private val examDao: ExamDao
) {

    suspend fun getCredential(systemId: String): AuthCredentialEntity? =
        authDao.get(systemId)

    fun observeCredential(systemId: String): Flow<AuthCredentialEntity?> =
        authDao.observe(systemId)

    /**
     * 保存 WebView 捕获的登录态。
     *
     * @param systemId 系统标识，例如 ecut_cas
     * @param primaryHost 主认证域名（写入 AuthCredential 表）
     * @param cookieByHost 每个域名对应的 CookieManager 原始字符串
     */
    suspend fun saveLoginCookies(
        systemId: String,
        primaryHost: String,
        cookieByHost: Map<String, String>
    ) {
        val now = System.currentTimeMillis()
        val merged = cookieByHost.values
            .filter { it.isNotBlank() }
            .joinToString("; ")
            .let { dedupeCookiePairs(it) }

        if (merged.isBlank()) return

        authDao.upsert(
            AuthCredentialEntity(
                systemId = systemId,
                domain = primaryHost,
                cookie = cookieByHost[primaryHost]?.takeIf { it.isNotBlank() } ?: merged,
                updatedAt = now
            )
        )

        cookieByHost.forEach { (host, raw) ->
            if (raw.isBlank()) return@forEach
            val rows = parseCookiePairs(raw).map { (name, value) ->
                CookieEntity(
                    domain = host,
                    name = name,
                    value = value,
                    path = "/",
                    expiresAt = 0L,
                    secure = false,
                    updatedAt = now
                )
            }
            if (rows.isNotEmpty()) {
                cookieDao.deleteForDomain(host)
                cookieDao.upsertAll(rows)
            }
        }
    }

    /**
     * 保存统一身份认证主凭据（CASTGC）。
     *
     * 任何一个业务系统的 WebView 登录都可能经过 CAS，
     * 只要 Cookie 里出现 CASTGC，就把它抽出来单独存一份，
     * 之后所有 service 都能用它静默换票。
     */
    suspend fun saveCasMasterCookies(raw: String) {
        // 只保留 CASTGC 本身：其它 Cookie（route / JSESSIONID）可能来自
        // 零信任代理域名，复制到 CAS 主域反而添乱，下一次请求会重新下发。
        val ticketPairs = parseCookiePairs(raw).filter { (name, _) ->
            name.lowercase() in CampusEndpoints.CAS_TICKET_COOKIE_NAMES
        }
        if (ticketPairs.isEmpty()) return
        val ticketHeader = ticketPairs.joinToString("; ") { (name, value) ->
            "$name=$value"
        }
        saveLoginCookies(
            systemId = CampusEndpoints.SYSTEM_CAS_MASTER,
            primaryHost = CampusEndpoints.CAS_HOST,
            cookieByHost = mapOf(CampusEndpoints.CAS_HOST to ticketHeader)
        )
    }

    suspend fun saveSetCookies(
        host: String,
        cookies: List<Pair<String, String>>,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        if (cookies.isEmpty()) return
        cookieDao.upsertAll(
            cookies.map { (name, value) ->
                CookieEntity(
                    domain = host,
                    name = name,
                    value = value,
                    path = "/",
                    expiresAt = 0L,
                    secure = false,
                    updatedAt = updatedAt
                )
            }
        )
    }

    /**
     * 读取某个主机的会话 Cookie，供内嵌官方网页（WebView）注入使用。
     */
    suspend fun loadCookiesForHost(host: String): List<CookieEntity> =
        cookieDao.getForHost(host)

    suspend fun clearSystem(systemId: String, hosts: Collection<String>) {
        authDao.delete(systemId)
        hosts.forEach { cookieDao.deleteForDomain(it) }
    }

    /**
     * 彻底退出：清空凭据、Cookie、课表缓存与电费历史。
     */
    suspend fun clearAll() {
        authDao.clear()
        cookieDao.clear()
        electricityDao.clear()
        courseDao.clear()
        gradeDao.clear()
        examDao.clear()
    }

    private fun dedupeCookiePairs(raw: String): String {
        val seen = LinkedHashMap<String, String>()
        parseCookiePairs(raw).forEach { (name, value) -> seen[name] = value }
        return seen.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun parseCookiePairs(raw: String): List<Pair<String, String>> {
        return raw.split(";")
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val name = part.substring(0, index).trim()
                val value = part.substring(index + 1).trim()
                if (name.isEmpty()) null else name to value
            }
    }
}
