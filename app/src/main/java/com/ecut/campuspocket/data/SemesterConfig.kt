package com.ecut.campuspocket.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 学期起点（第 1 周的周一）。
 *
 * 2026 秋季学期第 1 周为 2026-09-07（周一）。
 * 已用手机里的 WakeUp 课程表核对：2026-10-24 是第 7 周，周次日期 10/19-10/25。
 */
object SemesterConfig {
    val startDate: LocalDate = LocalDate.of(2026, 9, 7)
    const val totalWeeks: Int = 20

    fun weekFor(date: LocalDate = LocalDate.now()): Int {
        if (date.isBefore(startDate)) return 1
        val days = ChronoUnit.DAYS.between(startDate, date)
        return (days / 7 + 1).toInt().coerceIn(1, totalWeeks)
    }

    fun weekStart(week: Int): LocalDate =
        startDate.plusWeeks((week - 1).toLong())
}
