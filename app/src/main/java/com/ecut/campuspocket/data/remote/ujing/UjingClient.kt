package com.ecut.campuspocket.data.remote.ujing

import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.SessionExpiredException
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class UjingCampus(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * U净 洗衣房目前只在广兰 / 青岚两片有服务主体；抚州保留作为兜底。
 * 坐标来自高德地理编码 + 真机实测（广兰 28.7188,115.8259）。
 */
val UJING_CAMPUSES = listOf(
    UjingCampus("广兰校区", 28.718795, 115.825926),
    UjingCampus("青岚校区", 28.7202, 115.8203),
    UjingCampus("抚州校区", 27.94705, 116.41202)
)

data class UjingSession(
    val token: String,
    val userId: Long,
    val lbUserId: Long,
    val mobile: String,
    val nickname: String,
    val college: String,
    val serviceSubjectId: Long
)

data class UjingStore(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val deviceNum: Int,
    val serviceSubjectName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val washerCount: Int,
    val dryerCount: Int
)

data class UjingDeviceTag(
    val deviceTypeId: Int,
    val name: String,
    val total: Int,
    val idle: Int
)

data class UjingStoreInfo(
    val storeId: String,
    val storeName: String,
    val serviceSubjectName: String,
    val devices: List<UjingDeviceTag>
)

data class UjingProgram(
    val workModelId: Int,
    val name: String,
    val priceCents: Int,
    val minutes: Int,
    val tags: List<String>,
    val description: String
)

data class UjingProgramInfo(
    val deviceTypeId: Int,
    val deviceTypeName: String,
    val programs: List<UjingProgram>
)

data class UjingStoreRef(
    val storeId: String,
    val storeName: String
)

data class UjingOrder(
    val orderId: Long,
    val deviceTypeId: Int,
    val deviceTypeName: String,
    val deviceNo: String,
    val storeName: String,
    val createAt: String,
    val status: String,
    val statusRemark: String,
    val remainTime: Int
)

/**
 * U净洗衣只读接口。
 *
 * H5 与原生 App 共用 `phoenix.ujing.online:443/api/v1`。短信验证码需要
 * HMAC-SHA256 签名（key 为 H5 内置常量），登录后拿到的 JWT 直接放在
 * `Authorization: Bearer` 里。这里只实现查询，不实现下单 / 支付。
 */
class UjingClient(
    private val client: OkHttpClient,
    private val baseUrl: String = CampusEndpoints.UJING_BASE_URL
) {

    suspend fun sendCaptcha(mobile: String) = withContext(Dispatchers.IO) {
        val (nonce, timestamp, signature) = captchaSignature()
        val url = "$baseUrl/wechat/captcha/create".toHttpUrlBuilder()
            .addQueryParameter("mobile", mobile)
            .addQueryParameter("type", "1")
            .addQueryParameter("nonce", nonce)
            .addQueryParameter("timestamp", timestamp.toString())
            .addQueryParameter("signature", signature)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("x-app-code", APP_CODE)
            .header("x-app-version", APP_VERSION)
            .build()
        execute(request)
    }

    suspend fun login(mobile: String, captcha: String): UjingSession =
        withContext(Dispatchers.IO) {
            val payload = JsonObject().apply {
                addProperty("mobile", mobile)
                addProperty("captcha", captcha)
            }
            val root = post("$baseUrl/login", payload, token = null)
            val data = root.obj("data")
            UjingSession(
                token = data.str("token"),
                userId = data.long("id"),
                lbUserId = data.long("lbUserId"),
                mobile = data.str("mobile"),
                nickname = data.str("nickName"),
                college = data.str("college"),
                serviceSubjectId = data.long("serviceSubjectId")
            )
        }

    suspend fun nearbyStores(lat: Double, lng: Double, token: String): List<UjingStore> =
        withContext(Dispatchers.IO) {
            val root = get(
                "$baseUrl/stores/near".toHttpUrlBuilder()
                    .addQueryParameter("lat", lat.toString())
                    .addQueryParameter("lont", lng.toString())
                    .addQueryParameter("skip", "0")
                    .addQueryParameter("limit", "20")
                    .build(),
                token
            )
            root.obj("data").array("storeList").mapNotNull { item ->
                if (!item.isJsonObject) return@mapNotNull null
                val store = item.asJsonObject
                val coordinates = store.array("coordinates")
                val latValue = if (coordinates.size() >= 2) coordinates[1].asDouble else 0.0
                val lngValue = if (coordinates.size() >= 2) coordinates[0].asDouble else 0.0
                var washer = 0
                var dryer = 0
                store.array("storeInfo").forEach { info ->
                    if (!info.isJsonObject) return@forEach
                    val obj = info.asJsonObject
                    val count = obj.int("access")
                    when (obj.int("category")) {
                        1 -> washer += count
                        2 -> dryer += count
                    }
                }
                UjingStore(
                    id = store.str("id"),
                    name = store.str("name"),
                    distanceKm = store.dbl("distance"),
                    deviceNum = store.int("deviceNum"),
                    serviceSubjectName = store.obj("serviceSubject").str("name"),
                    address = store.obj("serviceSubject").str("address"),
                    latitude = latValue,
                    longitude = lngValue,
                    washerCount = washer,
                    dryerCount = dryer
                )
            }
        }

    suspend fun storeInfo(storeId: String, token: String): UjingStoreInfo =
        withContext(Dispatchers.IO) {
            val root = get(
                "$baseUrl/wechat/washer/store/info".toHttpUrlBuilder()
                    .addQueryParameter("storeId", storeId)
                    .build(),
                token
            )
            val data = root.obj("data")
            val tags = data.array("deviceTags").mapNotNull { item ->
                if (!item.isJsonObject) return@mapNotNull null
                val obj = item.asJsonObject
                UjingDeviceTag(
                    deviceTypeId = obj.int("deviceTypeId"),
                    name = obj.str("deviceTypeName"),
                    total = obj.int("totalNum"),
                    idle = obj.int("idleNum")
                )
            }
            UjingStoreInfo(
                storeId = data.str("storeId").ifBlank { storeId },
                storeName = data.str("storeName"),
                serviceSubjectName = data.str("serviceSubjectName"),
                devices = tags
            )
        }

    suspend fun programInfo(
        storeId: String,
        deviceTypeId: Int,
        token: String
    ): UjingProgramInfo = withContext(Dispatchers.IO) {
        val root = get(
            "$baseUrl/wechat/washer/devices/program/info".toHttpUrlBuilder()
                .addQueryParameter("storeId", storeId)
                .addQueryParameter("deviceTypeId", deviceTypeId.toString())
                .build(),
            token
        )
        val data = root.obj("data")
        val programs = data.array("deviceWashModel").mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            val obj = item.asJsonObject
            UjingProgram(
                workModelId = obj.int("workModelId"),
                name = obj.str("workModelName"),
                priceCents = obj.int("basePrice"),
                minutes = obj.int("time"),
                tags = obj.array("tags").mapNotNull { if (it.isJsonPrimitive) it.asString else null },
                description = obj.str("description")
            )
        }
        UjingProgramInfo(
            deviceTypeId = data.int("deviceTypeId"),
            deviceTypeName = data.str("deviceTypeName"),
            programs = programs
        )
    }

    suspend fun waitTime(storeId: String, deviceTypeId: Int, token: String): Int =
        withContext(Dispatchers.IO) {
            val root = get(
                "$baseUrl/wechat/washer/store/waitTime".toHttpUrlBuilder()
                    .addQueryParameter("storeId", storeId)
                    .addQueryParameter("deviceTypeId", deviceTypeId.toString())
                    .build(),
                token
            )
            root.obj("data").int("waitTime")
        }

    suspend fun runningOrders(token: String): List<UjingOrder> =
        withContext(Dispatchers.IO) {
            val root = get("$baseUrl/orders/running", token)
            root.array("data").mapNotNull { parseOrder(it) }
        }

    suspend fun historyOrders(token: String, limit: Int = 10): List<UjingOrder> =
        withContext(Dispatchers.IO) {
            val root = get(
                "$baseUrl/orders/history".toHttpUrlBuilder()
                    .addQueryParameter("skip", "0")
                    .addQueryParameter("limit", limit.toString())
                    .build(),
                token
            )
            root.array("data").mapNotNull { parseOrder(it) }
        }

    suspend fun orderDetailStore(orderId: Long, token: String): UjingStoreRef? =
        withContext(Dispatchers.IO) {
            val root = get("$baseUrl/orders/$orderId/detail", token)
            val data = root.obj("data")
            val storeId = data.str("storeId")
            if (storeId.isBlank()) {
                null
            } else {
                UjingStoreRef(storeId = storeId, storeName = data.str("storeName"))
            }
        }

    private fun parseOrder(element: com.google.gson.JsonElement): UjingOrder? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        return UjingOrder(
            orderId = obj.long("orderId"),
            deviceTypeId = obj.int("deviceTypeId"),
            deviceTypeName = obj.str("deviceTypeName"),
            deviceNo = obj.str("deviceNo"),
            storeName = obj.str("storeName"),
            createAt = obj.str("createAt"),
            status = obj.str("status"),
            statusRemark = obj.str("statusRemark"),
            remainTime = obj.int("remainTime")
        )
    }

    private fun get(url: String, token: String?): JsonObject =
        get(
            url.toHttpUrlOrNull() ?: throw IOException("U净接口地址无效"),
            token
        )

    private fun get(url: okhttp3.HttpUrl, token: String?): JsonObject {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("x-app-code", APP_CODE)
            .header("x-app-version", APP_VERSION)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return execute(builder.build())
    }

    private fun post(url: String, payload: JsonObject, token: String?): JsonObject {
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("x-app-code", APP_CODE)
            .header("x-app-version", APP_VERSION)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return execute(builder.build())
    }

    private fun execute(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw SessionExpiredException()
            val root = try {
                JsonParser.parseString(text).asJsonObject
            } catch (exception: Exception) {
                throw IOException("U净返回数据无法解析", exception)
            }
            val code = root.int("code")
            if (code == 401) throw SessionExpiredException()
            if (code != 0) {
                throw IOException(
                    root.str("message").ifBlank { "U净返回错误码 $code" }
                )
            }
            return root
        }
    }

    private fun captchaSignature(): Triple<String, Long, String> {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val timestamp = System.currentTimeMillis() / 1000
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signature = Base64.getEncoder().encodeToString(
            mac.doFinal((nonce + timestamp).toByteArray(Charsets.UTF_8))
        )
        return Triple(nonce, timestamp, signature)
    }

    private fun String.toHttpUrlBuilder() =
        (toHttpUrlOrNull() ?: throw IOException("U净接口地址无效")).newBuilder()

    private fun JsonObject.str(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else runCatching { value.asString }.getOrDefault("")
    }

    private fun JsonObject.int(key: String): Int {
        val value = get(key) ?: return 0
        if (value.isJsonNull) return 0
        return runCatching { value.asInt }.getOrElse {
            runCatching { value.asString.toInt() }.getOrDefault(0)
        }
    }

    private fun JsonObject.long(key: String): Long {
        val value = get(key) ?: return 0L
        if (value.isJsonNull) return 0L
        return runCatching { value.asLong }.getOrElse {
            runCatching { value.asString.toLong() }.getOrDefault(0L)
        }
    }

    private fun JsonObject.dbl(key: String): Double {
        val value = get(key) ?: return 0.0
        if (value.isJsonNull) return 0.0
        return runCatching { value.asDouble }.getOrElse {
            runCatching { value.asString.toDouble() }.getOrDefault(0.0)
        }
    }

    private fun JsonObject.obj(key: String): JsonObject {
        val value = get(key) ?: return JsonObject()
        return if (value.isJsonObject) value.asJsonObject else JsonObject()
    }

    private fun JsonObject.array(key: String): JsonArray {
        val value = get(key) ?: return JsonArray()
        return if (value.isJsonArray) value.asJsonArray else JsonArray()
    }

    private companion object {
        const val APP_CODE = "BO"
        const val APP_VERSION = "1.1.4"
        const val HMAC_KEY = "REDACTED"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
