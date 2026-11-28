package com.ecut.campuspocket.data.schedule

import com.ecut.campuspocket.data.ClassTimes
import com.ecut.campuspocket.data.SemesterConfig
import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.data.local.ExamEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 自定义课表/考试周次解析、格式化与考试融合到课表的纯函数工具。
 */
object ScheduleExamFusion {

    /**
     * 解析周次描述，支持：
     * - "1-16周" / "1-16"
     * - "1-16周(单)" / "1-16周(双)" / "1-16单"
     * - "第12周" / "12,14,16周" / "3-5周,8-10周"
     */
    fun parseWeeksSpec(spec: String, maxWeeks: Int = SemesterConfig.totalWeeks): Set<Int> {
        val cleaned = spec.trim()
            .replace("，", ",")
            .replace("、", ",")
            .replace("～", "-")
            .replace("~", "-")
            .replace("—", "-")
            .replace("至", "-")
            .replace("到", "-")
        if (cleaned.isBlank()) return emptySet()

        val result = linkedSetOf<Int>()
        val segments = cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (segment in segments) {
            val isOdd = segment.contains("单")
            val isEven = segment.contains("双")
            val rangeMatch = Regex("(\\d{1,2})\\s*-\\s*(\\d{1,2})").find(segment)
            if (rangeMatch != null) {
                val a = rangeMatch.groupValues[1].toIntOrNull() ?: continue
                val b = rangeMatch.groupValues[2].toIntOrNull() ?: continue
                val start = minOf(a, b).coerceIn(1, maxWeeks)
                val end = maxOf(a, b).coerceIn(start, maxWeeks)
                for (w in start..end) {
                    if (isOdd && w % 2 == 0) continue
                    if (isEven && w % 2 != 0) continue
                    result.add(w)
                }
            } else {
                Regex("\\d{1,2}").findAll(segment).forEach { m ->
                    val w = m.value.toIntOrNull()
                    if (w != null && w in 1..maxWeeks) {
                        result.add(w)
                    }
                }
            }
        }
        return result
    }

    /** 将周次集合格式化成紧凑易读的描述，如 "1-16周"、"1-15周(单)"、"12、14周"。 */
    fun formatWeeksSpec(weeks: Set<Int>): String {
        val sorted = weeks.filter { it > 0 }.sorted()
        if (sorted.isEmpty()) return ""
        if (sorted.size == 1) return "第${sorted.first()}周"

        val step = sorted[1] - sorted[0]
        val isUniform = (step == 1 || step == 2) &&
            (1 until sorted.size).all { sorted[it] - sorted[it - 1] == step }
        if (isUniform) {
            return when {
                step == 1 -> "${sorted.first()}-${sorted.last()}周"
                sorted.first() % 2 == 1 -> "${sorted.first()}-${sorted.last()}周(单)"
                else -> "${sorted.first()}-${sorted.last()}周(双)"
            }
        }

        // 合并连续区间
        val parts = mutableListOf<String>()
        var rangeStart = sorted.first()
        var prev = sorted.first()
        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            if (cur == prev + 1) {
                prev = cur
            } else {
                parts.add(if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev")
                rangeStart = cur
                prev = cur
            }
        }
        parts.add(if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev")
        return "${parts.joinToString(",")}周"
    }

    /**
     * 将考试起止时刻（如 "08:30", "10:30"）映射到课表节次区间 (startPeriod, endPeriod)。
     *
     * 策略：
     * 1. 找到与 [start, end] 有实际时间重叠的所有课节；
     * 2. 若无直接重叠（如午休 12:30-13:30），取距离开始时刻最近的课节，默认占 2 节；
     * 3. 若缺失时间，默认返回 (1, 2)。
     */
    fun clockRangeToPeriods(startClock: String, endClock: String): Pair<Int, Int> {
        val start = parseClock(startClock) ?: return 1 to 2
        val end = parseClock(endClock)?.takeIf { !it.isBefore(start) }
            ?: start.plusMinutes(100)

        val overlapped = mutableListOf<Int>()
        ClassTimes.periods.forEachIndexed { index, (sText, eText) ->
            val pStart = LocalTime.parse(sText)
            val pEnd = LocalTime.parse(eText)
            // 只要重叠超过 5 分钟即算占用该节
            val overlapStart = if (start.isAfter(pStart)) start else pStart
            val overlapEnd = if (end.isBefore(pEnd)) end else pEnd
            if (ChronoUnit.MINUTES.between(overlapStart, overlapEnd) >= 5) {
                overlapped.add(index + 1)
            }
        }
        if (overlapped.isNotEmpty()) {
            return overlapped.first() to overlapped.last()
        }

        // 无直接重叠时，找最接近的节次
        var bestPeriod = 1
        var bestDiff = Long.MAX_VALUE
        ClassTimes.periods.forEachIndexed { index, (sText, _) ->
            val diff = abs(ChronoUnit.MINUTES.between(LocalTime.parse(sText), start))
            if (diff < bestDiff) {
                bestDiff = diff
                bestPeriod = index + 1
            }
        }
        val endPeriod = (bestPeriod + 1).coerceAtMost(ClassTimes.regularPeriods)
        return bestPeriod to endPeriod
    }

    /** 判断某场考试是否落在 [weekStart, weekStart+6]，若命中返回 (dayOfWeek, startPeriod, endPeriod)。 */
    fun examSlotInWeek(exam: ExamEntity, weekStart: LocalDate): Triple<Int, Int, Int>? {
        val date = runCatching { LocalDate.parse(exam.examDate.trim()) }.getOrNull() ?: return null
        val offset = ChronoUnit.DAYS.between(weekStart, date).toInt()
        if (offset !in 0..6) return null
        val (startPeriod, endPeriod) = clockRangeToPeriods(exam.startClock, exam.endClock)
        return Triple(offset, startPeriod, endPeriod)
    }

    /** 构造一条规范化的自定义课程实体。 */
    fun buildCustomCourseEntity(
        id: Long = 0L,
        title: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        weeksSpec: String,
        room: String = "",
        campus: String = "",
        teacher: String = ""
    ): CourseEntity {
        val safeDay = dayOfWeek.coerceIn(0, 6)
        val safeStart = startPeriod.coerceIn(1, 12)
        val safeEnd = endPeriod.coerceIn(safeStart, 12)
        val parsedWeeks = parseWeeksSpec(weeksSpec).ifEmpty {
            setOf(SemesterConfig.weekFor())
        }
        val sortedWeeks = parsedWeeks.sorted()
        val normalizedSpec = formatWeeksSpec(parsedWeeks)
        val startSlot = ((safeStart - 1) / 2).coerceAtLeast(0)
        val endSlot = ((safeEnd - 1) / 2).coerceAtLeast(startSlot)
        return CourseEntity(
            id = id,
            dayOfWeek = safeDay,
            startSlot = startSlot,
            slotSpan = (endSlot - startSlot + 1).coerceAtLeast(1),
            title = title.trim(),
            room = room.trim(),
            teacher = teacher.trim(),
            startPeriod = safeStart,
            endPeriod = safeEnd,
            campus = campus.trim(),
            startWeek = sortedWeeks.first(),
            endWeek = sortedWeeks.last(),
            colorSeed = (title.trim().hashCode() and 0x7fffffff),
            weeksSpec = normalizedSpec,
            weeksCsv = sortedWeeks.joinToString(","),
            courseCode = "",
            isCustom = true
        )
    }

    /** 构造一条规范化的自定义考试实体。 */
    fun buildCustomExamEntity(
        existingKey: String? = null,
        courseName: String,
        examDate: String,
        startClock: String,
        endClock: String,
        location: String = "",
        campus: String = "",
        examType: String = "自定义考试",
        seatNo: String = "",
        note: String = ""
    ): ExamEntity {
        val now = System.currentTimeMillis()
        val key = existingKey?.takeIf { it.isNotBlank() }
            ?: "custom_exam_${now}_${abs((courseName + examDate + startClock).hashCode())}"
        val cleanDate = examDate.trim()
        val cleanStart = normalizeClock(startClock)
        val cleanEnd = normalizeClock(endClock)
        val examTime = buildString {
            if (cleanDate.isNotBlank()) append(cleanDate)
            if (cleanStart.isNotBlank()) {
                if (isNotEmpty()) append("(")
                append(cleanStart)
                if (cleanEnd.isNotBlank()) append("-").append(cleanEnd)
                if (cleanDate.isNotBlank()) append(")")
            }
        }
        return ExamEntity(
            key = key,
            courseName = courseName.trim(),
            courseCode = "",
            examTime = examTime,
            examDate = cleanDate,
            startClock = cleanStart,
            endClock = cleanEnd,
            examName = examType.trim().ifBlank { "自定义考试" },
            examType = examType.trim().ifBlank { "自定义" },
            location = location.trim(),
            campus = campus.trim(),
            seatNo = seatNo.trim(),
            ticketNo = "",
            note = note.trim(),
            term = "",
            updatedAt = now,
            isCustom = true
        )
    }

    fun normalizeClock(raw: String): String {
        val m = Regex("(\\d{1,2})\\s*[:：点時时]\\s*(\\d{1,2})?").find(raw.trim()) ?: return ""
        val h = m.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return ""
        val min = m.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
        return "%02d:%02d".format(h, min)
    }

    private fun parseClock(raw: String): LocalTime? {
        val norm = normalizeClock(raw)
        if (norm.isBlank()) return null
        return runCatching { LocalTime.parse(norm) }.getOrNull()
    }
}
