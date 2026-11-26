package com.ecut.campuspocket.data.remote.electricity

import com.ecut.campuspocket.network.SessionExpiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class XgwxElectricityClientTest {

    @Test
    fun `formats query dates with zero padded month and day`() {
        assertEquals("2026-11-01", XgwxElectricityClient.formatQueryDate(LocalDate.of(2026, 11, 1)))
        assertEquals("2026-05-09", XgwxElectricityClient.formatQueryDate(LocalDate.of(2026, 5, 9)))
        assertEquals("2026-10-28", XgwxElectricityClient.formatQueryDate(LocalDate.of(2026, 10, 28)))
    }

    @Test
    fun `extracts userid across html and json formats`() {
        assertEquals("202400123", XgwxElectricityClient.extractUserId("href='?userid=202400123&a=1'"))
        assertEquals("202400123", XgwxElectricityClient.extractUserId("var userId = '202400123';"))
        assertEquals("2024AB09", XgwxElectricityClient.extractUserId("""{"userid":"2024AB09"}"""))
        assertNull(XgwxElectricityClient.extractUserId("<html>login</html>"))
    }

    @Test
    fun `parses multiple dosage date formats`() {
        val expected = LocalDate.of(2026, 11, 10)
        assertEquals(expected, XgwxElectricityClient.parseDosageDate("20261110"))
        assertEquals(expected, XgwxElectricityClient.parseDosageDate("2026-11-10"))
        assertEquals(expected, XgwxElectricityClient.parseDosageDate("2026/11/10"))
        assertEquals(expected, XgwxElectricityClient.parseDosageDate("2026-11-10 00:00:00"))
        assertEquals(LocalDate.of(2026, 5, 3), XgwxElectricityClient.parseDosageDate("2026-5-3"))
    }

    @Test
    fun `detects middle gap days for single-day backfill`() {
        val windowStart = LocalDate.of(2026, 10, 27)
        val yesterday = LocalDate.of(2026, 11, 25)
        // 模拟 11/10~11/12 中间断档，以及 11/20~11/21 缺失。
        val found = setOf(
            LocalDate.of(2026, 11, 8),
            LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 13),
            LocalDate.of(2026, 11, 14),
            LocalDate.of(2026, 11, 22),
            LocalDate.of(2026, 11, 25)
        )
        val missing = XgwxElectricityClient.computeMissingDates(
            foundDates = found,
            windowStart = windowStart,
            yesterday = yesterday,
            recentDays = 15
        )
        assertTrue(LocalDate.of(2026, 11, 10) in missing)
        assertTrue(LocalDate.of(2026, 11, 11) in missing)
        assertTrue(LocalDate.of(2026, 11, 12) in missing)
        assertTrue(LocalDate.of(2026, 11, 20) in missing)
        assertTrue(LocalDate.of(2026, 11, 21) in missing)
        assertTrue(LocalDate.of(2026, 11, 8) !in missing)
        // 超出最早有数据日且早于近 15 天的日期不盲目补查。
        assertTrue(LocalDate.of(2026, 10, 28) !in missing)
    }

    @Test
    fun `throws SessionExpiredException when dosage api returns session error`() {
        try {
            XgwxElectricityClient.parseDosageRangeJson(
                """{"ret":"0","msg":"用户未登录或会话已过期"}"""
            )
            fail("应该抛出 SessionExpiredException")
        } catch (_: SessionExpiredException) {
            // expected
        }
    }
}
