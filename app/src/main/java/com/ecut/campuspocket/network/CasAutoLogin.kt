package com.ecut.campuspocket.network

import com.ecut.campuspocket.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 统一身份认证的账号密码自动登录。
 *
 * 只在用户明确开启「加密保存账号密码」后调用。流程与官方登录页一致：
 * 读取一次 HTML 里的 lt / execution / pwdDefaultEncryptSalt，按 encrypt.js
 * 的算法加密密码，再提交表单并跟随 CAS 回调。
 *
 * 验证码、二次验证、密码错误都会返回可读的失败原因，由界面回退到手动登录。
 */
class CasAutoLogin(
    private val client: OkHttpClient,
    private val authRepository: AuthRepository,
    private val serviceUrl: String = CampusEndpoints.EHALL_CARD_SERVICE_URL
) {

    suspend fun login(username: String, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val serviceLoginUrl = CasSsoManager.buildServiceLoginUrl(serviceUrl)
                val first = get(serviceLoginUrl)
                if (first.finalUrl.host != CampusEndpoints.CAS_HOST) {
                    // 本机 CAS 会话仍然有效，不需要密码，顺手把主凭据刷新一遍。
                    if (!first.response.isSuccessful) {
                        error("统一认证返回 HTTP ${first.response.code}")
                    }
                    saveMasterCookies()
                    return@runCatching
                }

                val form = parseLoginForm(first.body)
                val encryptedPassword = encryptCasPassword(password, form.encryptSalt)
                val actionUrl = resolveAction(serviceLoginUrl, form.action)
                val body = FormBody.Builder()
                    .add("username", username)
                    .add("password", encryptedPassword)
                    .add("lt", form.lt)
                    .add("execution", form.execution)
                    .add("_eventId", "submit")
                    .add("dllt", "userNamePasswordLogin")
                    .add("rmShown", "1")
                    .add("rememberMe", "on")
                    .build()
                val request = Request.Builder()
                    .url(actionUrl)
                    .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                    )
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val message = loginErrorMessage(text)
                            ?: "统一认证返回 HTTP ${response.code}"
                        error(message)
                    }
                    if (finalUrl.host == CampusEndpoints.CAS_HOST ||
                        finalUrl.encodedPath.contains("/authserver/")
                    ) {
                        error(loginErrorMessage(text) ?: "自动登录未完成，可能需要验证码或二次验证")
                    }
                    saveMasterCookies()
                }
            }
        }

    private fun saveMasterCookies() {
        val raw = runBlockingLoadCookieHeader()
        if (!CasSsoManager.hasCasTicket(raw)) {
            error("登录成功但未拿到 CASTGC，请手动登录一次")
        }
        kotlinx.coroutines.runBlocking {
            authRepository.saveCasMasterCookies(raw, fromCasHost = true)
        }
    }

    private fun runBlockingLoadCookieHeader(): String =
        kotlinx.coroutines.runBlocking {
            val chosen = LinkedHashMap<String, Pair<String, Int>>()
            authRepository.loadCookiesForHost(CampusEndpoints.CAS_HOST)
                .filter { it.domain == CampusEndpoints.CAS_HOST }
                .forEach { row ->
                    val pathLength = row.path.ifBlank { "/" }.length
                    val existing = chosen[row.name]
                    if (existing == null || pathLength >= existing.second) {
                        chosen[row.name] = row.value to pathLength
                    }
                }
            chosen.entries.joinToString("; ") { "${it.key}=${it.value.first}" }
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
                response = response,
                body = response.body?.string().orEmpty(),
                finalUrl = response.request.url
            )
        }
    }

    private fun parseLoginForm(html: String): CasLoginForm {
        val formStart = html.indexOf("id=\"casLoginForm\"", ignoreCase = true)
        val section = if (formStart >= 0) {
            html.substring(formStart, minOf(html.length, formStart + 20_000))
        } else {
            html
        }
        val lt = attrValue(section, "name=\"lt\"", "value")
        val execution = attrValue(section, "name=\"execution\"", "value")
        val salt = attrValue(section, "id=\"pwdDefaultEncryptSalt\"", "value")
        val action = attrValue(section, "id=\"casLoginForm\"", "action")
        if (lt.isNullOrBlank() || execution.isNullOrBlank() || salt.isNullOrBlank()) {
            error("登录页格式已变化，无法自动登录，请手动登录")
        }
        return CasLoginForm(
            lt = lt,
            execution = execution,
            encryptSalt = salt,
            action = action.orEmpty()
        )
    }

    private fun attrValue(html: String, marker: String, attribute: String): String? {
        val index = html.indexOf(marker, ignoreCase = true)
        if (index < 0) return null
        val tagEnd = html.indexOf('>', index)
        if (tagEnd < 0) return null
        val tag = html.substring(index, tagEnd)
        val regex = Regex("$attribute\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
        return regex.find(tag)?.groupValues?.get(1)?.let(::decodeHtml)
    }

    private fun resolveAction(loginUrl: String, action: String): String {
        if (action.isBlank()) return loginUrl
        return when {
            action.startsWith("http://") || action.startsWith("https://") -> action
            action.startsWith("/") -> "https://${CampusEndpoints.CAS_HOST}$action"
            else -> loginUrl.substringBeforeLast('/') + "/" + action
        }
    }

    private fun loginErrorMessage(html: String): String? = when {
        html.contains("用户名或者密码有误") ||
            html.contains("用户名或密码") ||
            html.contains("密码错误") ||
            html.contains("用户名不存在") ->
            "账号或密码不正确，请检查后重试"
        html.contains("账号已锁定") || html.contains("已被锁定") ->
            "账号已被临时锁定，请稍后手动登录"
        Regex("id=\"captchaImg\"[^>]*src=\"[^\"]+\"", RegexOption.IGNORE_CASE)
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

    private data class LoginPageResponse(
        val response: okhttp3.Response,
        val body: String,
        val finalUrl: okhttp3.HttpUrl
    )

    private data class CasLoginForm(
        val lt: String,
        val execution: String,
        val encryptSalt: String,
        val action: String
    )

    companion object {

        private const val RANDOM_CHARS =
            "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

        private val random = SecureRandom()

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
