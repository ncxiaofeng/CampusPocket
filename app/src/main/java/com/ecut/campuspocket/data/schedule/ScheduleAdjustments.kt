package com.ecut.campuspocket.data.schedule

import android.content.Context
import com.ecut.campuspocket.data.SemesterConfig
import com.ecut.campuspocket.data.local.CourseEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class ScheduleAdjustmentType {
    /** 节假日放假停课：隐藏当天常规课程（考试仍正常显示）。 */
    REST,

    /** 节假日调休补课：当天改上 [ScheduleDayAdjustment.sourceDate] 的课表。 */
    SWAP
}

/**
 * 单日节假日调休/停课规则（按自然日 yyyy-MM-dd 存储，教务同步时不覆盖、删除即可一键复原）。
 */
data class ScheduleDayAdjustment(
    val targetDate: String, // yyyy-MM-dd
    val type: ScheduleAdjustmentType,
    val sourceDate: String = "", // SWAP 时必填 yyyy-MM-dd
    val label: String = ""
)

data class ResolvedDayCourse(
    val course: CourseEntity,
    val displayDayOfWeek: Int,
    val adjustedTag: String = "",
    val adjustedDetail: String = ""
)

data class ParsedHolidayNotice(
    val swaps: List<ScheduleDayAdjustment> = emptyList(),
    val rests: List<ScheduleDayAdjustment> = emptyList()
)

object ScheduleAdjustments {

    private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun formatDateWithWeek(isoDate: String): String {
        val d = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
        val w = SemesterConfig.weekFor(d)
        val wd = weekdayNames[d.dayOfWeek.value - 1]
        return "${d.monthValue}/${d.dayOfMonth} $wd (第${w}周)"
    }

    fun shortDateWithWeekday(isoDate: String): String {
        val d = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
        val wd = weekdayNames[d.dayOfWeek.value - 1]
        return "${d.monthValue}/${d.dayOfMonth} $wd"
    }

    /**
     * 解析某一个自然日实际应上的课程列表（已叠加放假停课或调休补课规则）。
     */
    fun resolveDayCourses(
        allCourses: List<CourseEntity>,
        date: LocalDate,
        adjustmentsByDate: Map<String, ScheduleDayAdjustment>
    ): List<ResolvedDayCourse> {
        val iso = date.toString()
        val displayDay = date.dayOfWeek.value - 1
        val adj = adjustmentsByDate[iso]

        return when (adj?.type) {
            ScheduleAdjustmentType.REST -> emptyList()
            ScheduleAdjustmentType.SWAP -> {
                val src = runCatching { LocalDate.parse(adj.sourceDate) }.getOrNull()
                    ?: return emptyList()
                val srcWeek = SemesterConfig.weekFor(src)
                val srcDay = src.dayOfWeek.value - 1
                val detail = buildString {
                    append("原 ")
                    append(shortDateWithWeekday(adj.sourceDate))
                    append(" 课表")
                    if (adj.label.isNotBlank()) append("（${adj.label}）")
                }
                allCourses
                    .filter { it.dayOfWeek == srcDay && isCourseActiveInWeek(it, srcWeek) }
                    .map { course ->
                        ResolvedDayCourse(
                            course = course,
                            displayDayOfWeek = displayDay,
                            adjustedTag = "调休",
                            adjustedDetail = detail
                        )
                    }
            }
            null -> {
                val week = SemesterConfig.weekFor(date)
                allCourses
                    .filter { it.dayOfWeek == displayDay && isCourseActiveInWeek(it, week) }
                    .map { course ->
                        ResolvedDayCourse(
                            course = course,
                            displayDayOfWeek = displayDay
                        )
                    }
            }
        }
    }

    /**
     * 解析整周（周一至周日）的实际课程列表。
     */
    fun resolveWeekCourses(
        allCourses: List<CourseEntity>,
        weekStart: LocalDate,
        adjustmentsByDate: Map<String, ScheduleDayAdjustment>
    ): List<ResolvedDayCourse> {
        val out = mutableListOf<ResolvedDayCourse>()
        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())
            out.addAll(resolveDayCourses(allCourses, date, adjustmentsByDate))
        }
        return out
    }

    /**
     * 返回该周内各列（0=周一..6=周日）对应的调休/放假规则。
     */
    fun adjustmentsInWeek(
        weekStart: LocalDate,
        adjustmentsByDate: Map<String, ScheduleDayAdjustment>
    ): Map<Int, ScheduleDayAdjustment> {
        if (adjustmentsByDate.isEmpty()) return emptyMap()
        val map = linkedMapOf<Int, ScheduleDayAdjustment>()
        for (offset in 0..6) {
            val iso = weekStart.plusDays(offset.toLong()).toString()
            adjustmentsByDate[iso]?.let { map[offset] = it }
        }
        return map
    }

    /**
     * 生成连续日期区间内的放假停课规则（单日或连休多日，单次最多 31 天）。
     */
    fun expandRestRange(
        startIso: String,
        endIso: String,
        label: String
    ): List<ScheduleDayAdjustment> {
        val s = runCatching { LocalDate.parse(startIso.trim()) }.getOrNull() ?: return emptyList()
        val e = runCatching { LocalDate.parse(endIso.trim()) }.getOrNull() ?: s
        val from = if (s <= e) s else e
        val to = if (s <= e) e else s
        val cleanLabel = label.trim().ifBlank { "节假日放假" }
        val result = mutableListOf<ScheduleDayAdjustment>()
        var cur = from
        var guard = 0
        while (!cur.isAfter(to) && guard < 31) {
            result.add(
                ScheduleDayAdjustment(
                    targetDate = cur.toString(),
                    type = ScheduleAdjustmentType.REST,
                    label = cleanLabel
                )
            )
            cur = cur.plusDays(1)
            guard++
        }
        return result
    }

    /**
     * 离线解析常见高校及国务院节假日放假/调休通知，兼容：
     * 1. 正向明确补课："10月10日（周六）上10月5日（周一）的课" / "9月20日上班，补第5周周一（10月5日）课程"
     * 2. 反向调课："将10月5日（周一）的课调至9月20日（周日）上"
     * 3. 成对分别调课："9月20日（周日）、10月10日（周六）上班，分别补上10月5日、10月6日的课"
     * 4. 按星期补课："9月20日（周日）补星期一的课" / "9月20日上第5周周一的课"
     * 5. 国务院简明通知："国庆节：10月1日至7日放假调休，共7天。9月20日（周日）、10月10日（周六）上班。"
     */
    fun parseHolidayNotice(
        rawText: String,
        referenceDate: LocalDate = LocalDate.now()
    ): ParsedHolidayNotice {
        val text = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isBlank()) return ParsedHolidayNotice()

        val docYear = Regex("(20\\d{2})\\s*年").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val holidayKeywords = listOf("元旦", "春节", "清明", "五一", "劳动节", "端午", "中秋", "国庆", "寒假", "暑假")

        fun holidayNameNear(pos: Int): String {
            val windowStart = (pos - 100).coerceAtLeast(0)
            val before = text.substring(windowStart, pos.coerceAtMost(text.length))
            val lastBreak = maxOf(before.lastIndexOf('\n'), before.lastIndexOf('；'))
            val seg = if (lastBreak >= 0) before.substring(lastBreak + 1) else before
            val inSeg = holidayKeywords
                .mapNotNull { kw -> seg.lastIndexOf(kw).takeIf { it >= 0 }?.let { it to kw } }
                .maxByOrNull { it.first }
                ?.second
            if (inSeg != null) return inSeg
            return holidayKeywords
                .mapNotNull { kw -> before.lastIndexOf(kw).takeIf { it >= 0 }?.let { it to kw } }
                .maxByOrNull { it.first }
                ?.second
                .orEmpty()
        }

        val dateToken = "(?:(?:20\\d{2})\\s*[-/.年]\\s*)?\\d{1,2}\\s*[月/.]\\s*\\d{1,2}\\s*[日号]?"
        val optWeekday = "(?:\\s*[（(]?(?:第\\d{1,2}周\\s*)?(?:星期|礼拜|周)[一二三四五六日天][)）]?)?"
        val dateWithOptWd = "(?:$dateToken)$optWeekday"
        val dateList = "$dateWithOptWd(?:\\s*[、,，和与及]\\s*$dateWithOptWd)*"
        val weekdayToken = "(?:第\\d{1,2}周\\s*)?(?:单周|双周)?(?:星期|礼拜|周)[一二三四五六日天]"
        val weekdayList = "$weekdayToken(?:\\s*[、,，和与及]\\s*$weekdayToken)*"
        val optWorkPrefix = "(?:(?:正常|调休)?(?:上班|上课|补班|补课|行课)[，,、\\s]*)?"

        data class RestBlock(
            val matchStart: Int,
            val holiday: String,
            val dates: List<LocalDate>
        )

        val rests = mutableListOf<ScheduleDayAdjustment>()
        val restBlocks = mutableListOf<RestBlock>()
        val existingRestDates = linkedSetOf<String>()

        // 1. 先解析放假区间与单日放假，供后续“补周一课”与国务院“上班”通知匹配对应节假日工作日
        val rangeRegex = Regex(
            "($dateToken)$optWeekday\\s*[-~～—至到]\\s*(?:($dateToken)|(\\d{1,2})\\s*[日号])$optWeekday\\s*(?:放假|停课|调休放假)"
        )
        for (m in rangeRegex.findAll(text)) {
            val start = parseLooseDate(m.groupValues[1], referenceDate, docYear) ?: continue
            val end = if (m.groupValues[2].isNotBlank()) {
                parseLooseDate(m.groupValues[2], referenceDate, docYear)
            } else {
                val endDay = m.groupValues[3].toIntOrNull()
                if (endDay != null) {
                    runCatching { LocalDate.of(start.year, start.monthValue, endDay) }.getOrNull()
                } else {
                    null
                }
            } ?: start
            val hName = holidayNameNear(m.range.first)
            val expanded = expandRestRange(
                startIso = start.toString(),
                endIso = end.toString(),
                label = if (hName.isNotBlank()) "${hName}放假" else "节假日放假"
            )
            val blockDates = mutableListOf<LocalDate>()
            for (r in expanded) {
                runCatching { LocalDate.parse(r.targetDate) }.getOrNull()?.let { blockDates.add(it) }
                if (existingRestDates.add(r.targetDate)) {
                    rests.add(r)
                }
            }
            if (blockDates.isNotEmpty()) {
                restBlocks.add(RestBlock(m.range.first, hName, blockDates))
            }
        }

        val singleRestRegex = Regex("($dateToken)$optWeekday\\s*(?:放假|停课|不上课)")
        for (m in singleRestRegex.findAll(text)) {
            val d = parseLooseDate(m.groupValues[1], referenceDate, docYear) ?: continue
            val iso = d.toString()
            if (existingRestDates.add(iso)) {
                val hName = holidayNameNear(m.range.first)
                rests.add(
                    ScheduleDayAdjustment(
                        targetDate = iso,
                        type = ScheduleAdjustmentType.REST,
                        label = if (hName.isNotBlank()) "${hName}放假" else "放假停课"
                    )
                )
                restBlocks.add(RestBlock(m.range.first, hName, listOf(d)))
            }
        }

        val allRestDates = rests.mapNotNull { runCatching { LocalDate.parse(it.targetDate) }.getOrNull() }
        val swapsByTarget = linkedMapOf<String, ScheduleDayAdjustment>()

        fun addSwap(target: LocalDate, source: LocalDate, matchPos: Int, inferred: Boolean = false) {
            if (target == source) return
            val key = target.toString()
            if (key in swapsByTarget) return
            val hName = holidayNameNear(matchPos)
            val baseLabel = if (hName.isNotBlank()) "${hName}调休" else "节假日调休"
            swapsByTarget[key] = ScheduleDayAdjustment(
                targetDate = key,
                type = ScheduleAdjustmentType.SWAP,
                sourceDate = source.toString(),
                label = if (inferred) "${baseLabel}·待核对" else baseLabel
            )
        }

        fun extractDatesFromList(chunk: String): List<LocalDate> =
            Regex(dateToken).findAll(chunk)
                .mapNotNull { parseLooseDate(it.value, referenceDate, docYear) }
                .toList()

        fun resolveWeekdaySource(
            target: LocalDate,
            weekdayChar: Char,
            weekNum: Int?,
            matchPos: Int
        ): LocalDate? {
            val dow = parseChineseWeekday(weekdayChar) ?: return null
            if (weekNum != null && weekNum in 1..30) {
                return SemesterConfig.weekStart(weekNum).plusDays((dow - 1).toLong())
            }
            // 优先匹配同一节假日放假区间内对应星期的工作日
            val preferredBlock = restBlocks.lastOrNull { it.matchStart <= matchPos }
            val inPreferred = preferredBlock?.dates
                ?.firstOrNull { it.dayOfWeek.value == dow && it != target }
            if (inPreferred != null) return inPreferred

            val inAnyRest = allRestDates
                .filter { it.dayOfWeek.value == dow && it != target }
                .minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(target, it)) }
            if (inAnyRest != null &&
                kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(target, inAnyRest)) <= 25
            ) {
                return inAnyRest
            }
            // 退化为距离 target 最近的对应星期日期（±7 天内）
            var best: LocalDate? = null
            var bestDist = Long.MAX_VALUE
            for (delta in -7L..7L) {
                if (delta == 0L) continue
                val cand = target.plusDays(delta)
                if (cand.dayOfWeek.value == dow && kotlin.math.abs(delta) < bestDist) {
                    best = cand
                    bestDist = kotlin.math.abs(delta)
                }
            }
            return best
        }

        // 2a. 成对“分别”正向调课：9月20日、10月10日上班，分别补上10月5日、10月6日的课 / 分别上星期一、星期二的课
        val pairedForwardRegex = Regex(
            "($dateList)\\s*$optWorkPrefix\\s*分别\\s*(?:补上?|改上|上|执行|按照?)\\s*($dateList|$weekdayList)\\s*(?:的)?(?:课|课程|课表|教学安排)"
        )
        for (m in pairedForwardRegex.findAll(text)) {
            val targets = extractDatesFromList(m.groupValues[1])
            val rhs = m.groupValues[2]
            val rhsDates = extractDatesFromList(rhs)
            if (rhsDates.isNotEmpty()) {
                for (i in 0 until minOf(targets.size, rhsDates.size)) {
                    addSwap(targets[i], rhsDates[i], m.range.first)
                }
            } else {
                val wdMatches = Regex("(?:第(\\d{1,2})周\\s*)?(?:单周|双周)?(?:星期|礼拜|周)([一二三四五六日天])")
                    .findAll(rhs).toList()
                for (i in 0 until minOf(targets.size, wdMatches.size)) {
                    val wn = wdMatches[i].groupValues[1].toIntOrNull()
                    val wc = wdMatches[i].groupValues[2].firstOrNull() ?: continue
                    val src = resolveWeekdaySource(targets[i], wc, wn, m.range.first) ?: continue
                    addSwap(targets[i], src, m.range.first)
                }
            }
        }

        // 2b. 成对“分别”反向调课：将10月5日、10月6日的课分别调至9月20日、10月10日上
        val pairedReverseRegex = Regex(
            "(?:原|将|把)?($dateList)\\s*(?:的)?(?:课|课程|课表|教学安排)?\\s*分别\\s*(?:调至|调到|调整至|调整到|移至|改至|改到|安排至|安排到)\\s*($dateList)"
        )
        for (m in pairedReverseRegex.findAll(text)) {
            val sources = extractDatesFromList(m.groupValues[1])
            val targets = extractDatesFromList(m.groupValues[2])
            for (i in 0 until minOf(targets.size, sources.size)) {
                addSwap(targets[i], sources[i], m.range.first)
            }
        }

        // 2c. 单条正向明确补课：9月20日（周日）上/补上/按 10月5日（周一）的课
        val forwardSwapRegex = Regex(
            "($dateToken)$optWeekday\\s*$optWorkPrefix(?:调休)?\\s*(?:补上?|改上|上|执行|按照?|调整为上?)\\s*(?:第\\d{1,2}周\\s*)?(?:(?:星期|礼拜|周)[一二三四五六日天]\\s*)?[（(]?($dateToken)[)）]?$optWeekday\\s*(?:的)?(?:课|课程|课表|教学安排)"
        )
        for (m in forwardSwapRegex.findAll(text)) {
            val target = parseLooseDate(m.groupValues[1], referenceDate, docYear) ?: continue
            val source = parseLooseDate(m.groupValues[2], referenceDate, docYear) ?: continue
            addSwap(target, source, m.range.first)
        }

        // 2d. 单条反向调课：10月5日（周一）的课调至9月20日（周日）上
        val reverseSwapRegex = Regex(
            "(?:原|将|把)?($dateToken)$optWeekday\\s*(?:的)?(?:课|课程|课表|教学安排)?\\s*(?:调至|调到|调整至|调整到|移至|改至|改到|挪至|挪到|安排至|安排到)\\s*($dateToken)$optWeekday"
        )
        for (m in reverseSwapRegex.findAll(text)) {
            val source = parseLooseDate(m.groupValues[1], referenceDate, docYear) ?: continue
            val target = parseLooseDate(m.groupValues[2], referenceDate, docYear) ?: continue
            addSwap(target, source, m.range.first)
        }

        // 2e. 单条按星期补课：9月20日（周日）补（第5周）星期一的课 / 按周一课表上课
        val weekdaySwapRegex = Regex(
            "($dateToken)$optWeekday\\s*$optWorkPrefix(?:调休)?\\s*(?:补上?|改上|上|执行|按照?|调整为上?)\\s*(?:第(\\d{1,2})周\\s*)?(?:单周|双周)?(?:星期|礼拜|周)([一二三四五六日天])\\s*(?:的)?(?:课|课程|课表|教学安排)"
        )
        for (m in weekdaySwapRegex.findAll(text)) {
            val target = parseLooseDate(m.groupValues[1], referenceDate, docYear) ?: continue
            val wn = m.groupValues[2].toIntOrNull()
            val wc = m.groupValues[3].firstOrNull() ?: continue
            val source = resolveWeekdaySource(target, wc, wn, m.range.first) ?: continue
            addSwap(target, source, m.range.first)
        }

        // 2f. 国务院/简明放假通知兜底："9月20日（周日）、10月10日（周六）上班"（未写明补哪天课）
        val bareWorkRegex = Regex("($dateList)\\s*(?:正常|调休)?(?:上班|补班|补课|上课|行课)")
        for (m in bareWorkRegex.findAll(text)) {
            val workDates = extractDatesFromList(m.groupValues[1])
                .distinct()
                .filter { it.toString() !in swapsByTarget && it.toString() !in existingRestDates }
            if (workDates.isEmpty()) continue

            val block = restBlocks.lastOrNull { it.matchStart <= m.range.first }
                ?: restBlocks.minByOrNull { blk ->
                    val anchor = blk.dates.first()
                    kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(workDates.first(), anchor))
                }
                ?: continue

            val candidateSources = pickSwapSourceWeekdays(block.dates)
            for (i in 0 until minOf(workDates.size, candidateSources.size)) {
                addSwap(workDates[i], candidateSources[i], m.range.first, inferred = true)
            }
        }

        val finalSwaps = swapsByTarget.values.sortedBy { it.targetDate }
        val finalRests = rests
            .filter { it.targetDate !in swapsByTarget }
            .distinctBy { it.targetDate }
            .sortedBy { it.targetDate }

        return ParsedHolidayNotice(swaps = finalSwaps, rests = finalRests)
    }

    /**
     * 从一段连休放假日期中挑选最可能是调休借用的工作日：
     * 优先取连休区间内周末之后的紧邻工作日（如国庆 10/1-10/7 中 10/3-10/4 周末后的 10/5 周一、10/6 周二），
     * 其次取非首日的工作日（避开 10/1、5/1、1/1 等法定首日），最后兜底全部工作日。
     */
    private fun pickSwapSourceWeekdays(blockDates: List<LocalDate>): List<LocalDate> {
        val sorted = blockDates.distinct().sorted()
        val weekdays = sorted.filter { it.dayOfWeek.value in 1..5 }
        if (weekdays.isEmpty()) return emptyList()
        val firstWeekendIdx = sorted.indexOfFirst { it.dayOfWeek.value >= 6 }
        val afterWeekend = if (firstWeekendIdx >= 0) {
            sorted.drop(firstWeekendIdx + 1).filter { it.dayOfWeek.value in 1..5 }
        } else {
            emptyList()
        }
        val nonFirst = if (weekdays.size > 1) weekdays.drop(1) else weekdays
        return (afterWeekend + nonFirst + weekdays).distinct()
    }

    private fun parseChineseWeekday(ch: Char): Int? = when (ch) {
        '一' -> 1
        '二' -> 2
        '三' -> 3
        '四' -> 4
        '五' -> 5
        '六' -> 6
        '日', '天' -> 7
        else -> null
    }

    private fun parseLooseDate(token: String, ref: LocalDate, defaultYear: Int? = null): LocalDate? {
        val full = Regex("(20\\d{2})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})").find(token)
        if (full != null) {
            val y = full.groupValues[1].toIntOrNull() ?: return null
            val mo = full.groupValues[2].toIntOrNull() ?: return null
            val d = full.groupValues[3].toIntOrNull() ?: return null
            return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
        }
        val md = Regex("(\\d{1,2})\\s*[月/.]\\s*(\\d{1,2})").find(token) ?: return null
        val mo = md.groupValues[1].toIntOrNull() ?: return null
        val d = md.groupValues[2].toIntOrNull() ?: return null
        val year = defaultYear ?: if (ref.monthValue >= 9 && mo <= 2) ref.year + 1 else ref.year
        return runCatching { LocalDate.of(year, mo, d) }.getOrNull()
    }

    private fun isCourseActiveInWeek(course: CourseEntity, week: Int): Boolean {
        if (course.weeksCsv.isBlank()) return true
        return course.weeksCsv.split(",").any { it.trim().toIntOrNull() == week }
    }

    fun toJson(items: List<ScheduleDayAdjustment>): String {
        val arr = JSONArray()
        for (item in items.sortedBy { it.targetDate }) {
            arr.put(
                JSONObject().apply {
                    put("targetDate", item.targetDate)
                    put("type", item.type.name)
                    put("sourceDate", item.sourceDate)
                    put("label", item.label)
                }
            )
        }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<ScheduleDayAdjustment> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val target = o.optString("targetDate").trim()
                    if (target.isBlank()) continue
                    val type = runCatching {
                        ScheduleAdjustmentType.valueOf(o.optString("type"))
                    }.getOrDefault(ScheduleAdjustmentType.REST)
                    add(
                        ScheduleDayAdjustment(
                            targetDate = target,
                            type = type,
                            sourceDate = o.optString("sourceDate").trim(),
                            label = o.optString("label").trim()
                        )
                    )
                }
            }.sortedBy { it.targetDate }
        }.getOrDefault(emptyList())
    }
}

/**
 * 节假日调休/停课本地持久化存储（SharedPreferences JSON + StateFlow，不干扰教务课表数据库）。
 */
class ScheduleAdjustmentStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _adjustments = MutableStateFlow(
        ScheduleAdjustments.fromJson(prefs.getString(KEY_RULES, null))
    )
    val adjustments: StateFlow<List<ScheduleDayAdjustment>> = _adjustments.asStateFlow()

    fun upsertAll(rules: List<ScheduleDayAdjustment>) {
        if (rules.isEmpty()) return
        val merged = _adjustments.value.associateBy { it.targetDate }.toMutableMap()
        for (rule in rules) {
            merged[rule.targetDate] = rule
        }
        persist(merged.values.sortedBy { it.targetDate })
    }

    fun upsertSwap(
        targetDate: String,
        sourceDate: String,
        label: String,
        markSourceAsRest: Boolean
    ) {
        val cleanLabel = label.trim().ifBlank { "节假日调休" }
        val toAdd = mutableListOf(
            ScheduleDayAdjustment(
                targetDate = targetDate.trim(),
                type = ScheduleAdjustmentType.SWAP,
                sourceDate = sourceDate.trim(),
                label = cleanLabel
            )
        )
        if (markSourceAsRest && sourceDate.trim() != targetDate.trim()) {
            toAdd.add(
                ScheduleDayAdjustment(
                    targetDate = sourceDate.trim(),
                    type = ScheduleAdjustmentType.REST,
                    label = cleanLabel.replace("调休", "放假").ifBlank { "已调休放假" }
                )
            )
        }
        upsertAll(toAdd)
    }

    fun upsertRestRange(startDate: String, endDate: String, label: String): Int {
        val rules = ScheduleAdjustments.expandRestRange(startDate, endDate, label)
        upsertAll(rules)
        return rules.size
    }

    fun delete(targetDate: String) {
        persist(_adjustments.value.filterNot { it.targetDate == targetDate })
    }

    fun clearAll() {
        persist(emptyList())
    }

    private fun persist(list: List<ScheduleDayAdjustment>) {
        prefs.edit().putString(KEY_RULES, ScheduleAdjustments.toJson(list)).apply()
        _adjustments.value = list
    }

    companion object {
        private const val PREFS_NAME = "campuspocket_schedule_adjustments"
        private const val KEY_RULES = "rules_json_v1"
    }
}
