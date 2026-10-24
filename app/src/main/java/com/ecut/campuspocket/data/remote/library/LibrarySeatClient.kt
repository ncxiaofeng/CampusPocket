package com.ecut.campuspocket.data.remote.library

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class LibrarySeatArea(
    val id: String,
    val name: String,
    val free: Int,
    val total: Int,
    val sort: Int,
    val children: List<LibrarySeatArea>
)

data class LibrarySeatSnapshot(
    val dates: List<String>,
    val campuses: List<LibrarySeatArea>
)

/**
 * 图书馆空间预约系统（libspace）只读接口。
 *
 * `POST /v4/space/pcTopFor` 无需登录，返回各校区 / 楼层 / 区域的空闲座位数。
 * 预约、签到等写操作不在这里实现，统一走官方 H5。
 */
class LibrarySeatClient(
    private val client: OkHttpClient,
    private val baseUrl: String
) {

    suspend fun fetchAvailability(
        day: String,
        startTime: String? = null,
        endTime: String? = null
    ): LibrarySeatSnapshot = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("day", day)
            if (startTime != null) addProperty("start_time", startTime)
            if (endTime != null) addProperty("end_time", endTime)
        }
        val request = Request.Builder()
            .url("$baseUrl/v4/space/pcTopFor")
            .post(payload.toString().toRequestBody(JSON))
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("图书馆系统返回 ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            val root = try {
                JsonParser.parseString(text).asJsonObject
            } catch (exception: Exception) {
                throw IOException("图书馆返回数据无法解析", exception)
            }
            val code = root.get("code")?.takeIf { !it.isJsonNull }?.asInt ?: -1
            if (code != 0) {
                throw IOException(
                    root.get("message")?.takeIf { !it.isJsonNull }?.asString
                        ?: "图书馆系统返回错误"
                )
            }
            val data = root.getAsJsonObject("data")
            val dates = data.getAsJsonArray("date")
                ?.mapNotNull { if (it.isJsonNull) null else it.asString }
                .orEmpty()
            val campuses = data.getAsJsonArray("list")
                ?.mapNotNull { element ->
                    if (element.isJsonNull) null else toArea(element.asJsonObject)
                }
                .orEmpty()
            LibrarySeatSnapshot(dates = dates, campuses = campuses)
        }
    }

    private fun toArea(item: JsonObject): LibrarySeatArea {
        val children = item.getAsJsonArray("children")
            ?.mapNotNull { element ->
                if (element.isJsonNull) null else toArea(element.asJsonObject)
            }
            .orEmpty()
        return LibrarySeatArea(
            id = item.string("id"),
            name = item.string("name"),
            free = item.number("free_num"),
            total = item.number("total_num"),
            sort = item.number("sort"),
            children = children
        )
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else value.asString
    }

    private fun JsonObject.number(key: String): Int {
        val value = get(key) ?: return 0
        if (value.isJsonNull) return 0
        return runCatching { value.asInt }
            .getOrElse { value.asString.toIntOrNull() ?: 0 }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
