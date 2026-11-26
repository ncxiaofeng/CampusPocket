package com.ecut.campuspocket.network

import android.util.Log
import com.ecut.campuspocket.data.local.AuthDao
import com.ecut.campuspocket.data.local.CookieDao
import com.ecut.campuspocket.data.local.CookieEntity
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * OkHttp CookieJar：请求时从加密库读取 Cookie，响应时把 Set-Cookie 写回。
 *
 * OkHttp 会在自己的后台线程调用这些方法，因此这里允许 runBlocking；
 * 数据量只有几十条，不会阻塞主线程。
 */
class CampusCookieJar(
    private val authDao: AuthDao,
    private val cookieDao: CookieDao
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val requestPath = url.encodedPath.ifBlank { "/" }
        val now = System.currentTimeMillis()
        val result = LinkedHashMap<String, Cookie>()

        runBlocking {
            val rows = cookieDao.getForHost(host)
            selectCookies(rows, host, requestPath, now).forEach { row ->
                val path = row.path.ifBlank { "/" }
                buildCookie(host, row.name, row.value, path)?.let {
                    result[row.name] = it
                }
            }

            // 兜底：没有按域名拆分的 Cookie 时，使用 WebView 保存的原始 Cookie。
            if (result.isEmpty()) {
                authDao.getForHost(host)
                    .filter { it.domain == host }
                    .forEach { credential ->
                        parsePairs(credential.cookie).forEach { (name, value) ->
                            buildCookie(host, name, value, "/")?.let {
                                result.putIfAbsent(name, it)
                            }
                        }
                    }
            }
        }

        if (result.isNotEmpty()) {
            Log.d(
                "JwDiag",
                "cookies host=$host path=$requestPath -> " +
                    result.values.joinToString(",") { "${it.name}@${it.path}" }
            )
        }
        return result.values.toList()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        val host = url.host
        val rows = cookies.map { cookie ->
            CookieEntity(
                domain = host,
                name = cookie.name,
                value = cookie.value,
                path = cookie.path.ifBlank { "/" },
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                updatedAt = now
            )
        }
        runBlocking {
            val existing = cookieDao.getForHost(host).filter { it.domain == host }
            rows.forEach { incoming ->
                // 清理同一 (domain, name) 下互为父子前缀的旧路径副本：
                // 例如 authserver 的 route 从 /authserver 变为 /，或 xgwx 的 JSESSIONID 从 /app 变为 /；
                // 教务 /sso 与 /jwglxt 互不为前缀，因此完全不受影响。
                existing.filter { old ->
                    old.name == incoming.name &&
                        old.path != incoming.path &&
                        pathsOverlap(old.path, incoming.path)
                }.forEach { stale ->
                    cookieDao.deleteCookie(host, stale.name, stale.path)
                }
            }
            cookieDao.upsertAll(rows)
        }
    }

    private fun buildCookie(host: String, name: String, value: String, path: String): Cookie? {
        return try {
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(host)
                .path(path)
                .build()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun parsePairs(raw: String): List<Pair<String, String>> {
        return raw.split(";").mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val name = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            if (name.isEmpty()) null else name to value
        }
    }

    companion object {
        internal fun hostMatches(requestHost: String, cookieDomain: String): Boolean {
            val req = requestHost.lowercase()
            val dom = cookieDomain.trimStart('.').lowercase()
            if (dom.isEmpty()) return false
            return req == dom || req.endsWith(".$dom")
        }

        internal fun pathMatches(requestPath: String, cookiePath: String): Boolean {
            if (cookiePath.isBlank() || cookiePath == "/") return true
            val normalizedCookie = cookiePath.trimEnd('/')
            val normalizedReq = requestPath.ifBlank { "/" }
            if (normalizedReq == normalizedCookie || normalizedReq == "$normalizedCookie/") return true
            return normalizedReq.startsWith("$normalizedCookie/")
        }

        /** 两条路径存在父子前缀关系（含一方为 /）时返回 true；/sso 与 /jwglxt 返回 false。 */
        internal fun pathsOverlap(first: String, second: String): Boolean {
            val a = first.ifBlank { "/" }
            val b = second.ifBlank { "/" }
            return pathMatches(a, b) || pathMatches(b, a)
        }

        /**
         * 为指定请求挑出每个 name 的最佳 Cookie：
         * 1. 过滤域名边界、过期时间与路径匹配；
         * 2. 同名 Cookie 优先精确域名、再优先更具体路径；若更新晚于旧路径（超过 5 秒），以新 Cookie 为准。
         */
        internal fun selectCookies(
            rows: List<CookieEntity>,
            host: String,
            requestPath: String,
            now: Long
        ): List<CookieEntity> {
            val chosen = LinkedHashMap<String, CookieEntity>()
            rows.asSequence()
                .filter { hostMatches(host, it.domain) }
                .filter { it.expiresAt == 0L || it.expiresAt > now }
                .filter { pathMatches(requestPath, it.path) }
                .forEach { row ->
                    val current = chosen[row.name]
                    if (current == null || shouldReplace(current, row, host)) {
                        chosen[row.name] = row
                    }
                }
            return chosen.values.toList()
        }

        private fun shouldReplace(
            current: CookieEntity,
            candidate: CookieEntity,
            host: String
        ): Boolean {
            val currentExact = current.domain.equals(host, ignoreCase = true)
            val candidateExact = candidate.domain.equals(host, ignoreCase = true)
            if (candidateExact != currentExact) return candidateExact

            // 若其中一条明显更新（说明经历过新一轮登录/换票），以新 Cookie 为准，防止旧长路径遮蔽。
            val timeDiff = candidate.updatedAt - current.updatedAt
            if (timeDiff > 5_000L) return true
            if (timeDiff < -5_000L) return false

            val currentLen = current.path.ifBlank { "/" }.length
            val candidateLen = candidate.path.ifBlank { "/" }.length
            if (candidateLen != currentLen) return candidateLen > currentLen
            return candidate.updatedAt >= current.updatedAt
        }
    }
}
