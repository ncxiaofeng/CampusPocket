package com.ecut.campuspocket.data.remote.chaoxing

import java.time.LocalDate
import kotlin.math.abs

/**
 * 学习通课程/任务的学期排序。
 *
 * 课程列表接口不保证返回明确的学期字段，但课程名、班级名或 content JSON
 * 里经常带 “2026-2027” 这样的学年区间。按区间与当前学年匹配：
 * 0=本学期，1=上一学年，2=下一学年，3=更早/更晚，4=无法判断。
 */
object ChaoxingTerm {

    fun currentStartYear(today: LocalDate = LocalDate.now()): Int =
        if (today.monthValue >= 8) today.year else today.year - 1

    fun rank(text: String, today: LocalDate = LocalDate.now()): Int {
        if (text.isBlank()) return UNKNOWN
        val target = currentStartYear(today)
        val ranges = Regex("""(20\d{2})\s*[-–—~至]\s*(20\d{2})""")
            .findAll(text)
            .mapNotNull { match ->
                val start = match.groupValues[1].toIntOrNull()
                val end = match.groupValues[2].toIntOrNull()
                if (start != null && end != null && end in start..start + 2) start else null
            }
            .toList()
        if (ranges.isEmpty()) return UNKNOWN
        val start = ranges.minByOrNull { abs(it - target) } ?: return UNKNOWN
        return when (start) {
            target -> CURRENT
            target - 1 -> PREVIOUS
            target + 1 -> NEXT
            else -> OTHER
        }
    }

    fun label(rank: Int): String = when (rank) {
        CURRENT -> "本学期"
        PREVIOUS, OTHER -> "往期"
        NEXT -> "下学期"
        else -> ""
    }

    const val CURRENT = 0
    const val PREVIOUS = 1
    const val NEXT = 2
    const val OTHER = 3
    const val UNKNOWN = 4
}
