package com.ecut.campuspocket.data.remote.weather

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WeatherClientTest {

    private val client = WeatherClient(OkHttpClient())

    @Test
    fun `parses current hourly and daily weather`() {
        val snapshot = client.parseWeather(SAMPLE, WeatherClient.GUANGLAN)

        val now = snapshot.now
        assertNotNull(now)
        assertEquals(24.1, now!!.temperature, 0.001)
        assertEquals(65, now.humidity)
        assertEquals(0, now.weatherCode)

        assertEquals(3, snapshot.hourly.size)
        assertEquals(22.0, snapshot.hourly[0].temperature, 0.001)
        assertEquals(10, snapshot.hourly[0].precipitationProbability)
        assertEquals(LocalTime.of(8, 0), snapshot.hourly[0].time.toLocalTime())

        assertEquals(2, snapshot.daily.size)
        assertEquals(24.0, snapshot.daily[0].maxTemperature, 0.001)
        assertEquals(18.0, snapshot.daily[0].minTemperature, 0.001)
        assertEquals(6, snapshot.daily[0].precipitationProbabilityMax)
    }

    @Test
    fun `tolerates missing optional arrays`() {
        val json = """
            {
              "current": {
                "time": "2026-10-25T16:00",
                "temperature_2m": 20.5,
                "apparent_temperature": 21.0,
                "relative_humidity_2m": 70,
                "wind_speed_10m": 8.0,
                "precipitation": 0,
                "weather_code": 3
              },
              "hourly": {
                "time": ["2026-10-25T16:00"],
                "temperature_2m": [20.5],
                "weather_code": [3]
              }
            }
        """.trimIndent()

        val snapshot = client.parseWeather(json, WeatherClient.GUANGLAN)

        assertEquals(1, snapshot.hourly.size)
        assertEquals(0, snapshot.hourly[0].precipitationProbability)
        assertTrue(snapshot.daily.isEmpty())
    }

    @Test
    fun `maps wmo codes and aligns course start hour`() {
        assertEquals("晴", weatherDescription(0))
        assertEquals("雷阵雨", weatherDescription(95))
        assertEquals("阵雪", weatherDescription(85))
        assertEquals(LocalTime.of(8, 0), alignHour("08:00"))
        assertEquals(LocalTime.of(14, 0), alignHour("14:00"))
        assertNull(alignHour("--:--"))
    }

    private companion object {
        val SAMPLE = """
            {
              "latitude": 28.7,
              "longitude": 115.8,
              "timezone": "Asia/Shanghai",
              "current": {
                "time": "2026-10-25T16:00",
                "temperature_2m": 24.1,
                "relative_humidity_2m": 65,
                "apparent_temperature": 25.0,
                "precipitation": 0.0,
                "weather_code": 0,
                "wind_speed_10m": 10.7
              },
              "hourly": {
                "time": ["2026-10-25T08:00", "2026-10-25T09:00", "2026-10-25T10:00"],
                "temperature_2m": [22.0, 23.0, 24.0],
                "precipitation_probability": [10, 5, null],
                "weather_code": [0, 1, 2]
              },
              "daily": {
                "time": ["2026-10-25", "2026-10-26"],
                "weather_code": [0, 3],
                "temperature_2m_max": [24.0, 26.0],
                "temperature_2m_min": [18.0, 18.0],
                "precipitation_probability_max": [6, 0]
              }
            }
        """.trimIndent()
    }
}
