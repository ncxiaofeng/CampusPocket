package com.ecut.campuspocket.network

import android.util.Log
import com.ecut.campuspocket.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 统一身份认证（直连 CAS + 零信任门户 aTrust CAS）的账号密码自动登录。
 *
 * 只在用户明确开启「加密保存账号密码」后调用。流程与官方登录页一致：
 * 读取一次 HTML 里的隐藏表单字段与加密盐，按 encrypt.js 的算法加密密码，
 * 再提交表单并跟随 CAS 回调。
 *
 * 验证码、二次验证、密码错误都会返回可读的失败原因，由界面回退到手动登录。
 */
class CasAutoLogin(
    private val client: OkHttpClient,
    private val authRepository: AuthRepository,
    private val serviceUrl: String = CampusEndpoints.EHALL_CARD_SERVICE_URL
) {

    /** 直连统一身份认证自动登录（authserver.ecut.edu.cn）。 */
    suspend fun login(username: String, password: String): Result<Unit> =
        loginTarget(
            username = username,
            password = password,
            loginUrl = CasSsoManager.buildServiceLoginUrl(serviceUrl),
            casHost = CampusEndpoints.CAS_HOST,
            portalHost = null
        )

    /** 零信任门户自动登录（authserver-ecut-edu-cn-s.atrust.ecut.edu.cn -> atrust1.ecut.edu.cn）。 */
    suspend fun loginPortal(username: String, password: String): Result<Unit> =
        loginTarget(
            username = username,
            password = password,
            loginUrl = CampusEndpoints.ATRUST_LOGIN_URL,
            casHost = ATRUST_CAS_HOST,
            portalHost = ATRUST_PORTAL_HOST
        )

    /** 仅检查零信任门户现有会话是否依然有效，不提交密码。 */
    suspend fun checkPortalSessionValid(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val first = get(CampusEndpoints.ATRUST_LOGIN_URL)
            val leftCas = first.finalUrl.host != ATRUST_CAS_HOST &&
                !first.finalUrl.encodedPath.contains("/authserver/")
            if (!leftCas) return@runCatching false
            val portalRaw = loadCookieHeader(ATRUST_PORTAL_HOST)
            val casRaw = loadCookieHeader(ATRUST_CAS_HOST)
            if (portalRaw.isBlank() && !CasSsoManager.hasCasTicket(casRaw)) {
                return@runCatching false
            }
            authRepository.savePortalSession(
                casHost = ATRUST_CAS_HOST,
                casCookieHeader = casRaw,
                portalHost = ATRUST_PORTAL_HOST,
                portalCookieHeader = portalRaw
            )
            true
        }.getOrDefault(false)
    }

    private suspend fun loginTarget(
        username: String,
        password: String,
        loginUrl: String,
        casHost: String,
        portalHost: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            var page = get(loginUrl)
            if (isAwayFromCas(page.finalUrl, casHost)) {
                // 本机 CAS 会话仍然有效，不需要提交密码，直接刷新落库。
                if (portalHost != null) {
                    savePortalCookies(casHost, portalHost)
                } else {
                    if (!page.isSuccessful && !CasSsoManager.hasCasTicket(loadCookieHeader(casHost))) {
                        error("统一认证返回 HTTP ${page.code}")
                    }
                    saveMasterCookies(casHost)
                }
                return@runCatching
            }

            // 停在 CAS 登录页说明本机旧 TGT/route 已失效。
            // 先清理该 CAS 域残留的旧 Cookie 再重新拉一次登录页，确保 GET 拿到的
            // route / JSESSIONID / lt / execution 严格来自同一台集群节点。
            authRepository.clearCookiesForHost(casHost)
            if (portalHost != null) {
                authRepository.clearCookiesForHost(portalHost)
            }
            page = get(loginUrl)
            if (isAwayFromCas(page.finalUrl, casHost)) {
                if (portalHost != null) savePortalCookies(casHost, portalHost)
                else saveMasterCookies(casHost)
                return@runCatching
            }

            val form = parseLoginForm(page.body)
            val encryptedPassword = encryptCasPassword(password, form.encryptSalt)
            val actionUrl = resolveAction(page.finalUrl, form.action)
            val formBuilder = FormBody.Builder()
                .add("username", username)
                .add("password", encryptedPassword)
            val fields = LinkedHashMap<String, String>()
            // 保留页面原样隐藏域（lt / execution / cllt / dllt / _eventId / rmShown 等）。
            fields.putAll(form.hiddenFields)
            if (!fields.containsKey("_eventId")) fields["_eventId"] = "submit"
            if (!fields.containsKey("dllt") && !fields.containsKey("cllt")) {
                fields["dllt"] = "userNamePasswordLogin"
            }
            if (!fields.containsKey("rmShown")) fields["rmShown"] = "1"
            fields["rememberMe"] = "on"
            fields.forEach { (key, value) -> formBuilder.add(key, value) }

            val origin = "${page.finalUrl.scheme}://${page.finalUrl.host}" +
                if ((page.finalUrl.scheme == "https" && page.finalUrl.port == 443) ||
                    (page.finalUrl.scheme == "http" && page.finalUrl.port == 80)
                ) "" else ":${page.finalUrl.port}"

            val request = Request.Builder()
                .url(actionUrl)
                .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                .header("Origin", origin)
                .header("Referer", page.finalUrl.toString())
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url
                val text = response.body?.string().orEmpty()
                Log.d(
                    "CasAutoLogin",
                    "target=$casHost post final=$finalUrl code=${response.code}"
                )
                if (!isAwayFromCas(finalUrl, casHost)) {
                    error(loginErrorMessage(text) ?: "自动登录未完成，可能需要验证码或二次验证")
                }
                if (portalHost != null) {
                    savePortalCookies(casHost, portalHost)
                } else {
                    val casRaw = loadCookieHeader(casHost)
                    if (!response.isSuccessful && !CasSsoManager.hasCasTicket(casRaw)) {
                        val message = loginErrorMessage(text)
                            ?: "统一认证返回 HTTP ${response.code}"
                        error(message)
                    }
                    saveMasterCookies(casHost)
                }
            }
        }
    }

    private fun isAwayFromCas(finalUrl: HttpUrl, casHost: String): Boolean =
        !finalUrl.host.equals(casHost, ignoreCase = true) &&
            !finalUrl.encodedPath.contains("/authserver/")

    private suspend fun saveMasterCookies(casHost: String) {
        val raw = loadCookieHeader(casHost)
        if (!CasSsoManager.hasCasTicket(raw)) {
            error("登录成功但未拿到 CASTGC，请手动登录一次")
        }
        authRepository.saveCasMasterCookies(raw, fromCasHost = true)
    }

    private suspend fun savePortalCookies(casHost: String, portalHost: String) {
        val casRaw = loadCookieHeader(casHost)
        val portalRaw = loadCookieHeader(portalHost)
        if (portalRaw.isBlank() && !CasSsoManager.hasCasTicket(casRaw)) {
            error("零信任门户登录未拿到有效会话，请手动登录一次")
        }
        authRepository.savePortalSession(
            casHost = casHost,
            casCookieHeader = casRaw,
            portalHost = portalHost,
            portalCookieHeader = portalRaw
        )
    }

    private suspend fun loadCookieHeader(host: String): String {
        val chosen = LinkedHashMap<String, Pair<String, Long>>()
        authRepository.loadCookiesForHost(host)
            .filter { it.domain.equals(host, ignoreCase = true) }
            .forEach { row ->
                val existing = chosen[row.name]
                if (existing == null || row.updatedAt >= existing.second) {
                    chosen[row.name] = row.value to row.updatedAt
                }
            }
        return chosen.entries.joinToString("; ") { "${it.key}=${it.value.first}" }
    }

    private fun get(url: String): LoginPageResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            LoginPageResponse(
                code = response.code,
                isSuccessful = response.isSuccessful,
                body = response.body?.string().orEmpty(),
                finalUrl = response.request.url
            )
        }
    }

    private fun resolveAction(finalUrl: HttpUrl, action: String): String {
        if (action.isBlank()) return finalUrl.toString()
        return finalUrl.resolve(action)?.toString() ?: finalUrl.toString()
    }

    private data class LoginPageResponse(
        val code: Int,
        val isSuccessful: Boolean,
        val body: String,
        val finalUrl: HttpUrl
    )

    internal data class CasLoginForm(
        val action: String,
        val encryptSalt: String,
        val hiddenFields: Map<String, String>
    )

    companion object {
        const val ATRUST_CAS_HOST = "authserver-ecut-edu-cn-s.atrust.ecut.edu.cn"
        const val ATRUST_PORTAL_HOST = "atrust1.ecut.edu.cn"

        private const val RANDOM_CHARS =
            "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

        private val random = SecureRandom()

        private val FORM_TAG_REGEX = Regex(
            """<form\b[^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val INPUT_TAG_REGEX = Regex(
            """<input\b[^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        private val ATTR_REGEX = Regex(
            """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))"""
        )
        private val JS_SALT_REGEX = Regex(
            """(?:pwdDefaultEncryptSalt|pwdEncryptSalt)\s*=\s*["']([^"']{16})["']"""
        )

        /**
         * 解析 CAS 登录表单：
         * - 不依赖 HTML 属性先后顺序（支持 value 在 name/id 前面）
         * - 兼容单引号、双引号与新版 CAS（可选 lt、pwdEncryptSalt / pwdDefaultEncryptSalt）
         * - 自动收集表单里全部 hidden 字段原样提交
         */
        internal fun parseLoginForm(html: String): CasLoginForm {
            val formStart = findLoginFormStart(html)
            val section = if (formStart >= 0) {
                val formEnd = html.indexOf("</form>", formStart, ignoreCase = true)
                val end = if (formEnd > formStart) formEnd + 7 else minOf(html.length, formStart + 25_000)
                html.substring(formStart, end)
            } else {
                html
            }

            val formTag = FORM_TAG_REGEX.find(section)?.value.orEmpty()
            val formAttrs = parseAttributes(formTag)
            val action = formAttrs["action"].orEmpty()

            val hiddenFields = LinkedHashMap<String, String>()
            var salt = ""
            INPUT_TAG_REGEX.findAll(section).forEach { match ->
                val attrs = parseAttributes(match.value)
                val id = attrs["id"].orEmpty()
                val name = attrs["name"].orEmpty()
                val type = attrs["type"].orEmpty().lowercase()
                val value = attrs["value"].orEmpty()
                if (id.equals("pwdDefaultEncryptSalt", ignoreCase = true) ||
                    id.equals("pwdEncryptSalt", ignoreCase = true) ||
                    name.equals("pwdDefaultEncryptSalt", ignoreCase = true) ||
                    name.equals("pwdEncryptSalt", ignoreCase = true)
                ) {
                    if (value.isNotBlank()) salt = value
                }
                if (type == "hidden" && name.isNotBlank() &&
                    !name.equals("username", ignoreCase = true) &&
                    !name.equals("password", ignoreCase = true)
                ) {
                    hiddenFields[name] = value
                }
            }

            if (salt.isBlank()) {
                // 某些页面把盐放在表单外的隐藏 input 或 JS 变量里。
                INPUT_TAG_REGEX.findAll(html).forEach { match ->
                    if (salt.isNotBlank()) return@forEach
                    val attrs = parseAttributes(match.value)
                    val id = attrs["id"].orEmpty()
                    if (id.equals("pwdDefaultEncryptSalt", ignoreCase = true) ||
                        id.equals("pwdEncryptSalt", ignoreCase = true)
                    ) {
                        salt = attrs["value"].orEmpty()
                    }
                }
            }
            if (salt.isBlank()) {
                salt = JS_SALT_REGEX.find(html)?.groupValues?.getOrNull(1).orEmpty()
            }

            val execution = hiddenFields["execution"].orEmpty()
            if (execution.isBlank() || salt.isBlank()) {
                error("登录页格式已变化，无法自动登录，请手动登录")
            }
            return CasLoginForm(
                action = action,
                encryptSalt = salt,
                hiddenFields = hiddenFields
            )
        }

        private fun findLoginFormStart(html: String): Int {
            listOf("id=\"casLoginForm\"", "id='casLoginForm'", "id=\"pwdFromId\"", "id='pwdFromId'")
                .forEach { marker ->
                    val idx = html.indexOf(marker, ignoreCase = true)
                    if (idx >= 0) {
                        val tagStart = html.lastIndexOf('<', idx)
                        if (tagStart >= 0) return tagStart
                    }
                }
            return -1
        }

        private fun parseAttributes(tag: String): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            ATTR_REGEX.findAll(tag).forEach { match ->
                val key = match.groupValues[1].lowercase()
                val raw = match.groupValues[2].ifEmpty {
                    match.groupValues[3].ifEmpty { match.groupValues[4] }
                }
                map[key] = decodeHtml(raw)
            }
            return map
        }

        internal fun loginErrorMessage(html: String): String? = when {
            html.contains("用户名或者密码有误") ||
                html.contains("用户名或密码") ||
                html.contains("密码错误") ||
                html.contains("用户名不存在") ->
                "账号或密码不正确，请检查后重试"
            html.contains("账号已锁定") || html.contains("已被锁定") ->
                "账号已被临时锁定，请稍后手动登录"
            html.contains("验证码错误") || html.contains("请输入验证码") ||
                Regex("id=[\"']captchaImg[\"'][^>]*src=[\"'][^\"']+[\"']", RegexOption.IGNORE_CASE)
                    .containsMatchIn(html) ->
                "需要图形验证码，请手动登录一次"
            html.contains("二次验证") || html.contains("动态码验证") ->
                "需要二次验证，请手动登录一次"
            else -> null
        }

        private fun decodeHtml(raw: String): String = raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

        /**
         * 复刻 CAS 登录页 encrypt.js：
         * AES/CBC/PKCS7，明文 = 64 位随机串 + 密码，IV = 16 位随机串。
         */
        fun encryptCasPassword(password: String, salt: String, iv: String? = null): String {
            require(salt.toByteArray(Charsets.UTF_8).size == 16) {
                "CAS 密码加密盐长度异常"
            }
            val prefix = randomString(64)
            val actualIv = iv ?: randomString(16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(salt.toByteArray(Charsets.UTF_8), "AES"),
                IvParameterSpec(actualIv.toByteArray(Charsets.UTF_8))
            )
            val plain = prefix + password
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(encrypted)
        }

        private fun randomString(length: Int): String {
            val builder = StringBuilder(length)
            repeat(length) {
                builder.append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)])
            }
            return builder.toString()
        }

        fun encodeFormValue(value: String): String =
            URLEncoder.encode(value, "UTF-8")
    }
}
