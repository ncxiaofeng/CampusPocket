package com.ecut.campuspocket.network

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
        val chosenPathLength = HashMap<String, Int>()

        runBlocking {
            cookieDao.getForHost(host)
                .filter { it.expiresAt == 0L || it.expiresAt > now }
                .filter { pathMatches(requestPath, it.path) }
                .forEach { row ->
                    val path = row.path.ifBlank { "/" }
                    // 同名 Cookie 优先使用路径更长（更具体）的那一个。
                    val existingLength = chosenPathLength[row.name] ?: -1
                    if (path.length >= existingLength) {
                        buildCookie(host, row.name, row.value, path)?.let {
                            result[row.name] = it
                            chosenPathLength[row.name] = path.length
                        }
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

        return result.values.toList()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows = cookies.map { cookie ->
            CookieEntity(
                domain = url.host,
                name = cookie.name,
                value = cookie.value,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                updatedAt = now
            )
        }
        runBlocking { cookieDao.upsertAll(rows) }
    }

    private fun pathMatches(requestPath: String, cookiePath: String): Boolean {
        if (cookiePath.isBlank() || cookiePath == "/") return true
        if (requestPath == cookiePath) return true
        return requestPath.startsWith(cookiePath.trimEnd('/') + "/")
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
}
