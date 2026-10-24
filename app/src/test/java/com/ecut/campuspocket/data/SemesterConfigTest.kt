package com.ecut.campuspocket.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SemesterConfigTest {

    @Test
    fun `first day of semester is week one`() {
        assertEquals(1, SemesterConfig.weekFor(LocalDate.of(2026, 9, 7)))
    }

    @Test
    fun `october 24 falls in week seven`() {
        assertEquals(7, SemesterConfig.weekFor(LocalDate.of(2026, 10, 24)))
    }

    @Test
    fun `week seven starts on october 19`() {
        assertEquals(LocalDate.of(2026, 10, 19), SemesterConfig.weekStart(7))
    }

    @Test
    fun `week start is a monday`() {
        assertEquals(
            java.time.DayOfWeek.MONDAY,
            SemesterConfig.weekStart(7).dayOfWeek
        )
    }
}
