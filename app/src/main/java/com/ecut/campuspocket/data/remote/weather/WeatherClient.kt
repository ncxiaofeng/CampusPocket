package com.ecut.campuspocket.data.remote.weather

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class WeatherLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class WeatherNow(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val precipitation: Double,
    val weatherCode: Int
)

data class WeatherHourly(
    val time: LocalDateTime,
    val temperature: Double,
    val precipitationProbability: Int,
    val weatherCode: Int
)

data class WeatherDaily(
    val date: LocalDate,
    val weatherCode: Int,
    val maxTemperature: Double,
    val minTemperature: Double,
    val precipitationProbabilityMax: Int
)

data class WeatherSnapshot(
    val location: WeatherLocation,
    val now: WeatherNow?,
    val hourly: List<WeatherHourly>,
    val daily: List<WeatherDaily>,
    val fetchedAt: Long
)

/**
 * Open-Meteo 公开天气接口（免 API Key）。
 *
 * 只发送校区固定坐标，不携带账号、学号、设备标识等任何个人信息。
 */
class WeatherClient(
    private val client: OkHttpClient
) {

    suspend fun fetch(location: WeatherLocation = GUANGLAN): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.open-meteo.com")
                .addPathSegment("v1")
                .addPathSegment("forecast")
                .addQueryParameter("latitude", location.latitude.toString())
                .addQueryParameter("longitude", location.longitude.toString())
                .addQueryParameter(
                    "current",
                    "temperature_2m,relative_humidity_2m,apparent_temperature," +
                        "precipitation,weather_code,wind_speed_10m"
                )
                .addQueryParameter(
                    "hourly",
                    "temperature_2m,precipitation_probability,weather_code"
                )
                .addQueryParameter(
                    "daily",
                    "weather_code,temperature_2m_max,temperature_2m_min," +
                        "precipitation_probability_max"
                )
                .addQueryParameter("timezone", "Asia/Shanghai")
                .addQueryParameter("forecast_days", "2")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CampusPocket")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("天气服务返回 HTTP ${response.code}")
                }
                parseWeather(body, location)
            }
        }

    internal fun parseWeather(body: String, location: WeatherLocation): WeatherSnapshot {
        val root = try {
            JsonParser.parseString(body).asJsonObject
        } catch (exception: Exception) {
            throw IOException("天气数据无法解析", exception)
        }
        val isError = root.get("error")
            ?.takeIf { !it.isJsonNull }
            ?.let { runCatching { it.asBoolean }.getOrDefault(false) }
            ?: false
        if (isError) {
            throw IOException(
                root.get("reason")?.takeIf { !it.isJsonNull }?.asString
                    ?: "天气服务返回错误"
            )
        }

        val current = root.getAsJsonObject("current")
        val now = current?.let {
            val time = it.localDateTime("time") ?: return@let null
            WeatherNow(
                time = time,
                temperature = it.double("temperature_2m"),
                apparentTemperature = it.double("apparent_temperature"),
                humidity = it.int("relative_humidity_2m"),
                windSpeed = it.double("wind_speed_10m"),
                precipitation = it.double("precipitation"),
                weatherCode = it.int("weather_code")
            )
        }

        val hourlyRoot = root.getAsJsonObject("hourly")
        val hourlyTimes = hourlyRoot?.getAsJsonArray("time")
        val hourlyTemps = hourlyRoot?.getAsJsonArray("temperature_2m")
        val hourlyRain = hourlyRoot?.getAsJsonArray("precipitation_probability")
        val hourlyCodes = hourlyRoot?.getAsJsonArray("weather_code")
        val hourly = buildList {
            val count = listOfNotNull(
                hourlyTimes?.size(),
                hourlyTemps?.size(),
                hourlyCodes?.size()
            ).minOrNull() ?: 0
            for (index in 0 until count) {
                val time = hourlyTimes?.stringAt(index)
                    ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                    ?: continue
                add(
                    WeatherHourly(
                        time = time,
                        temperature = hourlyTemps?.doubleAt(index) ?: continue,
                        precipitationProbability = hourlyRain?.intAt(index) ?: 0,
                        weatherCode = hourlyCodes?.intAt(index) ?: 0
                    )
                )
            }
        }

        val dailyRoot = root.getAsJsonObject("daily")
        val dailyTimes = dailyRoot?.getAsJsonArray("time")
        val dailyCodes = dailyRoot?.getAsJsonArray("weather_code")
        val dailyMax = dailyRoot?.getAsJsonArray("temperature_2m_max")
        val dailyMin = dailyRoot?.getAsJsonArray("temperature_2m_min")
        val dailyRain = dailyRoot?.getAsJsonArray("precipitation_probability_max")
        val daily = buildList {
            val count = listOfNotNull(
                dailyTimes?.size(),
                dailyCodes?.size(),
                dailyMax?.size(),
                dailyMin?.size()
            ).minOrNull() ?: 0
            for (index in 0 until count) {
                val date = dailyTimes?.stringAt(index)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: continue
                add(
                    WeatherDaily(
                        date = date,
                        weatherCode = dailyCodes?.intAt(index) ?: 0,
                        maxTemperature = dailyMax?.doubleAt(index) ?: continue,
                        minTemperature = dailyMin?.doubleAt(index) ?: continue,
                        precipitationProbabilityMax = dailyRain?.intAt(index) ?: 0
                    )
                )
            }
        }

        return WeatherSnapshot(
            location = location,
            now = now,
            hourly = hourly,
            daily = daily,
            fetchedAt = System.currentTimeMillis()
        )
    }

    private fun JsonObject.localDateTime(key: String): LocalDateTime? {
        val text = get(key)?.takeIf { !it.isJsonNull }?.asString ?: return null
        return runCatching { LocalDateTime.parse(text) }.getOrNull()
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

    private fun JsonArray.elementAt(index: Int): JsonElement? =
        if (index in 0 until size()) get(index) else null

    private fun JsonArray.stringAt(index: Int): String? {
        val value = elementAt(index) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()
    }

    private fun JsonArray.doubleAt(index: Int): Double? {
        val value = elementAt(index) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asDouble }
            .getOrElse { value.asString.toDoubleOrNull() }
    }

    private fun JsonArray.intAt(index: Int): Int? {
        val value = elementAt(index) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asInt }
            .getOrElse { value.asString.toIntOrNull() }
    }

    companion object {
        val GUANGLAN = WeatherLocation(
            name = "南昌 · 广兰校区",
            latitude = 28.718795,
            longitude = 115.825926
        )
    }
}

/** WMO 天气代码 → 中文短语。 */
fun weatherDescription(code: Int): String = when (code) {
    0 -> "晴"
    1 -> "大部晴朗"
    2 -> "多云"
    3 -> "阴"
    45, 48 -> "有雾"
    51, 53, 55 -> "毛毛雨"
    56, 57 -> "冻雨"
    61, 63, 65 -> "下雨"
    66, 67 -> "冻雨"
    71, 73, 75, 77 -> "下雪"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95 -> "雷阵雨"
    96, 99 -> "雷暴伴冰雹"
    else -> "多云"
}

/** 把“08:05”这类课程时间对齐到整点，用于匹配逐小时预报。 */
fun alignHour(timeText: String): LocalTime? =
    runCatching { LocalTime.parse(timeText, DateTimeFormatter.ofPattern("HH:mm")) }
        .getOrNull()
        ?.withMinute(0)
        ?.withSecond(0)
        ?.withNano(0)
