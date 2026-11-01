package com.ecut.campuspocket.data.remote.huilife

import android.util.Log
import com.ecut.campuspocket.BuildConfig
import com.ecut.campuspocket.network.CampusEndpoints
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

class HuiLifeTokenExpiredException(
    message: String = "慧生活登录已过期，请重新登录"
) : IOException(message)

/**
 * 慧生活798 只读与设备控制接口。
 *
 * 网关与字段来自官方 App / 公开第三方客户端：
 * - 查询端 ApplicationType=1,5，无需签名；
 * - 设备端 ApplicationType=1,1，短信登录需要 App 签名盐。
 *
 * 只做用户主动确认后的启动/停止，不自动接水、不充值。
 */
class HuiLifeClient(
    private val client: OkHttpClient
) {

    suspend fun captcha(sessionKey: String): ByteArray = withContext(Dispatchers.IO) {
        val url = "${CampusEndpoints.HUILIFE_BASE_URL}/captcha/" +
            "?s=${encode(sessionKey)}&r=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA_ACCOUNT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("图形验证码返回 HTTP ${response.code}")
            }
            response.body?.bytes() ?: throw IOException("图形验证码为空")
        }
    }

    suspend fun sendSms(
        phone: String,
        graphCode: String,
        sessionKey: String,
        platform: HuiLifePlatform
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JsonObject().apply {
                addProperty("un", phone)
                addProperty("authCode", graphCode)
                addProperty("s", sessionKey)
            }
            val json = postJson("/acc/login/code", body, platform, token = null)
            requireSuccess(json, "短信发送失败")
        }
    }

    suspend fun login(
        phone: String,
        smsCode: String,
        platform: HuiLifePlatform
    ): Result<HuiLifeSession> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JsonObject().apply {
                addProperty("authCode", smsCode)
                addProperty("un", phone)
                if (platform == HuiLifePlatform.ACCOUNT) {
                    addProperty("openCode", "")
                    addProperty("cid", CampusEndpoints.HUILIFE_CID)
                }
            }
            val path = if (platform == HuiLifePlatform.APP) {
                "/acc/login?sign=${signAppLogin(phone, smsCode)}"
            } else {
                "/acc/login"
            }
            val json = postJson(path, body, platform, token = null)
            requireSuccess(json, "登录失败")
            val al = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("al")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IOException("登录返回缺少 token")
            val token = al.string("token")
            if (token.isBlank()) throw IOException("登录返回缺少 token")
            HuiLifeSession(
                token = token,
                uid = al.string("uid"),
                eid = al.string("eid")
            )
        }
    }

    suspend fun fetchMaster(
        token: String,
        platform: HuiLifePlatform
    ): Result<List<HuiLifeDevice>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("/ui/app/master", platform, token)
            requireSuccess(json, "设备列表读取失败")
            val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            Log.d(
                "HuiLifeApi",
                "master dataKeys=${data?.keySet()?.joinToString(",")}"
            )
            HuiLifeDiagnostics.lastMaster =
                "dataKeys=${data?.keySet()?.joinToString(",") ?: "null"}"
            val favos = data?.get("favos")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: data?.get("devices")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: data?.get("list")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: data?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: JsonArray()
            HuiLifeDiagnostics.lastMaster += " favos=${favos.size()}"
            val parsed = favos.mapNotNull { element ->
                runCatching {
                    val item = element.takeIf { it.isJsonObject }?.asJsonObject
                        ?: return@runCatching null
                    val id = item.string("id")
                        .ifBlank { item.string("did") }
                        .ifBlank { item.string("deviceId") }
                        .ifBlank { item.string("devId") }
                    if (id.isBlank()) return@runCatching null
                    HuiLifeDevice(
                        id = id,
                        name = item.string("name")
                            .ifBlank { item.string("devName") }
                            .ifBlank { item.string("deviceName") }
                            .ifBlank { "未命名设备" },
                        detail = buildList {
                            item.string("status").takeIf { it.isNotBlank() }?.let(::add)
                            item.string("state").takeIf { it.isNotBlank() }?.let(::add)
                            item.string("addr").takeIf { it.isNotBlank() }?.let(::add)
                        }.joinToString(" · ")
                    )
                }.getOrNull()
            }
            HuiLifeDiagnostics.lastMaster += " parsed=${parsed.size}"
            parsed
        }
    }

    suspend fun fetchWallet(
        token: String,
        platform: HuiLifePlatform
    ): Result<HuiLifeWallet?> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("/acc/wallet/owner", platform, token)
            HuiLifeDiagnostics.lastWallet =
                "code=${json.int("code")} msg=${json.string("msg")}"
            requireSuccess(json, "钱包读取失败")
            val data = json.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching null
            val wallets = mutableListOf<JsonObject>()
            data.get("aw")?.takeIf { it.isJsonObject }?.asJsonObject?.let(wallets::add)
            data.get("eps")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                if (element.isJsonObject) wallets += element.asJsonObject
            }
            val wallet = wallets.firstOrNull() ?: return@runCatching null
            val endpoint = wallet.get("ep")?.takeIf { it.isJsonObject }?.asJsonObject
            val balance = wallet.doubleOrNull("total")
                ?: wallet.doubleOrNull("olCash")
                ?: wallet.doubleOrNull("balance")
                ?: 0.0
            HuiLifeWallet(
                name = endpoint?.string("name").orEmpty().ifBlank { "校园钱包" },
                balance = balance
            )
        }
    }

    suspend fun fetchBills(
        token: String,
        platform: HuiLifePlatform
    ): Result<List<HuiLifeBill>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("/bill/lst-owner?page=0&size=20", platform, token)
            HuiLifeDiagnostics.lastBills =
                "code=${json.int("code")} msg=${json.string("msg")}"
            requireSuccess(json, "消费记录读取失败")
            val data = json.get("data") ?: return@runCatching emptyList()
            val array = when {
                data.isJsonArray -> data.asJsonArray
                data.isJsonObject -> {
                    val obj = data.asJsonObject
                    obj.get("list")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?: obj.get("records")?.takeIf { it.isJsonArray }?.asJsonArray
                        ?: JsonArray()
                }
                else -> JsonArray()
            }
            array.mapNotNull { element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@mapNotNull null
                val id = item.string("id")
                val amount = item.doubleOrNull("payment")
                    ?: item.doubleOrNull("amount")
                    ?: item.doubleOrNull("total")
                    ?: return@mapNotNull null
                val time = item.long("utime").takeIf { it > 0 }
                    ?: item.long("ctime")
                val device = item.get("dev")?.takeIf { it.isJsonObject }?.asJsonObject
                val deviceName = device?.string("name").orEmpty()
                HuiLifeBill(
                    id = id.ifBlank { "$time|$amount" },
                    title = deviceName.ifBlank { "接水消费" },
                    amount = amount,
                    time = normalizeTime(time),
                    deviceId = device?.string("id").orEmpty(),
                    deviceName = deviceName
                )
            }
        }
    }

    suspend fun startDevice(
        appToken: String,
        deviceId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "/dev/start?did=${encode(deviceId)}" +
                "&upgrade=true&ptype=91&rcp=false&cnt=1"
            val json = getJson(url, HuiLifePlatform.APP, appToken)
            requireSuccess(json, "启动失败")
            "启动请求已发送"
        }
    }

    suspend fun stopDevice(
        appToken: String,
        deviceId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "/dev/end?did=${encode(deviceId)}&rcp=false"
            val json = getJson(url, HuiLifePlatform.APP, appToken)
            requireSuccess(json, "停止失败")
            "停止请求已发送"
        }
    }

    private fun getJson(
        path: String,
        platform: HuiLifePlatform,
        token: String
    ): JsonObject {
        val request = Request.Builder()
            .url(CampusEndpoints.HUILIFE_BASE_URL + path)
            .headers(headers(platform, token))
            .get()
            .build()
        return executeJson(request)
    }

    private fun postJson(
        path: String,
        body: JsonObject,
        platform: HuiLifePlatform,
        token: String?
    ): JsonObject {
        val request = Request.Builder()
            .url(CampusEndpoints.HUILIFE_BASE_URL + path)
            .headers(headers(platform, token))
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("慧生活返回 HTTP ${response.code}")
            }
            val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrElse {
                HuiLifeDiagnostics.lastHttp =
                    "HTTP ${response.code} ${request.url.encodedPath}"
                HuiLifeDiagnostics.lastResponse = "非 JSON：${text.take(120)}"
                throw IOException("慧生活返回数据无法解析")
            }
            HuiLifeDiagnostics.lastHttp =
                "HTTP ${response.code} ${request.url.encodedPath}"
            HuiLifeDiagnostics.lastResponse =
                "code=${json.int("code")} msg=${json.string("msg")}"
            Log.d(
                "HuiLifeApi",
                "HTTP ${response.code} ${request.url.encodedPath} " +
                    "code=${json.int("code")} msg=${json.string("msg")}"
            )
            return json
        }
    }

    private fun headers(
        platform: HuiLifePlatform,
        token: String?
    ) = okhttp3.Headers.Builder().apply {
        add("Content-Type", "application/json")
        add("Accept-Language", "zh-Hans-CN;q=1")
        add(
            "ApplicationType",
            if (platform == HuiLifePlatform.APP) "1,1" else "1,5"
        )
        add(
            "VersionCode",
            if (platform == HuiLifePlatform.APP) "3.1.9" else "2.0.83"
        )
        add(
            "User-Agent",
            if (platform == HuiLifePlatform.APP) UA_APP else UA_ACCOUNT
        )
        if (!token.isNullOrBlank()) add("Authorization", token)
    }.build()

    private fun requireSuccess(json: JsonObject, fallback: String) {
        val code = json.int("code")
        if (code == -99) throw HuiLifeTokenExpiredException()
        if (code != 0) {
            throw IOException(
                json.string("msg").ifBlank { fallback } + "（code=$code）"
            )
        }
    }

    private fun signAppLogin(phone: String, smsCode: String): String {
        val salt = BuildConfig.HUILIFE_APP_SIGN_SALT
        if (salt.isBlank()) throw IOException("未配置慧生活设备登录签名，请更新 App 配置")
        return signAppLogin(phone, smsCode, salt)
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: return ""
        if (value.isJsonNull || !value.isJsonPrimitive) return ""
        return runCatching { value.asString }.getOrDefault("")
    }

    private fun JsonObject.int(key: String): Int {
        val value = get(key) ?: return 0
        if (value.isJsonNull) return 0
        return runCatching { value.asInt }.getOrDefault(0)
    }

    private fun JsonObject.long(key: String): Long {
        val value = get(key) ?: return 0L
        if (value.isJsonNull) return 0L
        return runCatching { value.asLong }.getOrDefault(0L)
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        val value = get(key) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asDouble }.getOrNull()
    }

    private fun normalizeTime(raw: Long): Long = when {
        raw <= 0L -> 0L
        raw < 10_000_000_000L -> raw * 1000L
        else -> raw
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val UA_ACCOUNT = "WaterWidget/5.5.0 (Android)"
        private const val UA_APP = "Android_ilife798_3.1.9"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val random = SecureRandom()
        private const val RANDOM_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun newCaptchaKey(): String = StringBuilder(10).apply {
            repeat(10) { append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)]) }
        }.toString()

        /** 设备端短信登录签名：md5("authCode="+code+"&un="+phone+salt)。 */
        internal fun signAppLogin(phone: String, smsCode: String, salt: String): String {
            val digest = MessageDigest.getInstance("MD5")
                .digest("authCode=$smsCode&un=$phone$salt".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
