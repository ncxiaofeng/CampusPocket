package com.ecut.campuspocket.data.schedule

import com.ecut.campuspocket.data.ClassTimes
import com.ecut.campuspocket.data.SemesterConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

enum class ParsedItemKind {
    COURSE,
    EXAM
}

/**
 * 智能识别草稿（来自剪贴板通知或离线截图 OCR），必须经用户核对/修改后才落库。
 */
data class ParsedScheduleDraft(
    val id: String = UUID.randomUUID().toString(),
    val selected: Boolean = true,
    val kind: ParsedItemKind,
    val title: String,
    val campus: String = "",
    val room: String = "",
    val teacher: String = "",
    // 课程字段
    val dayOfWeek: Int = 0, // 0 = 周一 .. 6 = 周日
    val startPeriod: Int = 1,
    val endPeriod: Int = 2,
    val weeksSpec: String = "",
    // 考试字段
    val examDate: String = "", // yyyy-MM-dd
    val startClock: String = "",
    val endClock: String = "",
    val examType: String = "考试",
    val seatNo: String = "",
    val note: String = "",
    val rawSnippet: String = ""
)

/**
 * 纯本地离线智能文本解析器：将群聊消息或截图 OCR 文本解析为结构化加课/考试草稿。
 */
object ScheduleTextParser {

    private val examKeywords = listOf(
        "考试", "期末", "期中", "补考", "缓考", "随堂考", "结课考", "测验",
        "考场", "座位", "监考", "闭卷", "开卷", "笔试", "机考", "答辩"
    )
    private val explicitCourseKeywords = listOf(
        "补课", "加课", "调课", "上课", "实验课", "重修", "选修"
    )
    private val tableHeaderKeywords = listOf(
        "课程名称", "考试时间", "考场", "考试地点", "教室", "班级", "人数", "序号", "监考", "备注", "星期", "节次"
    )
    private val campusKeywords = listOf(
        "广兰校园" to "广兰校园",
        "广兰校区" to "广兰校园",
        "广兰" to "广兰校园",
        "枫林校园" to "枫林校园",
        "枫林校区" to "枫林校园",
        "枫林" to "枫林校园",
        "青岚校区" to "青岚校区",
        "青岚" to "青岚校区",
        "抚州校区" to "抚州校区",
        "抚州" to "抚州校区"
    )

    private val weekdayCharMap = mapOf(
        '一' to 0, '二' to 1, '三' to 2, '四' to 3,
        '五' to 4, '六' to 5, '日' to 6, '天' to 6, '末' to 6,
        '1' to 0, '2' to 1, '3' to 2, '4' to 3, '5' to 4, '6' to 5, '7' to 6
    )

    fun parse(
        rawText: String,
        referenceDate: LocalDate = LocalDate.now(),
        defaultKindHint: ParsedItemKind? = null
    ): List<ParsedScheduleDraft> {
        val normalized = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            // ML Kit / Vision 离线 OCR 常把数字间的短横线识别成片假名长音符 `ー` 或全角破折号
            .replace(Regex("(?<=\\d)[ー–—－―](?=\\d)"), "-")
            .trim()
        if (normalized.isBlank()) return emptyList()

        val chunks = splitIntoChunks(normalized, referenceDate)
        val drafts = mutableListOf<ParsedScheduleDraft>()
        for (chunk in chunks) {
            drafts.addAll(parseChunkToDrafts(chunk, referenceDate, defaultKindHint))
        }
        return drafts
    }

    /**
     * 将大段通知拆分成独立条目：
     * 1. 若按编号（1. / 2. / ① / (1)）或空行分段后存在多段有效信息，则逐段解析；
     * 2. 若像教务/Excel 考试安排表截图那样包含多条记录（包括单元格换行导致的多行记录），
     *    按锚点与未闭合括号/续行规则智能合并后再逐条解析；
     * 3. 否则视为单条完整通知整体解析。
     */
    private fun splitIntoChunks(text: String, ref: LocalDate): List<String> {
        // 先尝试按空行切段
        val paragraphs = text.split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paragraphs.size > 1 && paragraphs.count { hasScheduleSignal(it, ref) } >= 2) {
            return paragraphs.flatMap { splitNumberedOrTable(it, ref) }
        }
        return splitNumberedOrTable(text, ref)
    }

    private fun splitNumberedOrTable(block: String, ref: LocalDate): List<String> {
        // 序号列表（1. / 1、 / (1) / ①）
        val numberedSplit = block.split(Regex("(?m)(?=^(?:[（(]?\\d{1,2}[)）\\.、]|[①②③④⑤⑥⑦⑧⑨⑩])\\s*)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (numberedSplit.size >= 2 && numberedSplit.count { hasScheduleSignal(it, ref) } >= 2) {
            return numberedSplit
        }

        // 表格式多行（支持单元格换行，例如日期在上行、16:00-17:40) 与教室尾号 10/15/16 在下行）
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 2) {
            val grouped = groupTableRows(lines, ref)
            val validGroups = grouped
                .map { it.joinToString("\n") }
                .filter { hasDateOrTimeOrPeriod(it, ref) }
            if (validGroups.size >= 2) {
                return validGroups
            }
        }
        return listOf(block)
    }

    private fun groupTableRows(lines: List<String>, ref: LocalDate): List<List<String>> {
        val groups = mutableListOf<MutableList<String>>()
        for (line in lines) {
            val hasDate = extractDate(line, ref) != null
            val hasPeriod = extractPeriodRange(line) != null
            val hasClock = extractClockRange(line) != null

            if (groups.isEmpty()) {
                if (isTableHeaderLine(line, hasDate, hasPeriod, hasClock)) continue
                groups.add(mutableListOf(line))
                continue
            }

            if (isTableHeaderLine(line, hasDate, hasPeriod, hasClock)) continue

            val cur = groups.last()
            val curText = cur.joinToString("\n")
            val curHasDate = extractDate(curText, ref) != null
            val curHasPeriod = extractPeriodRange(curText) != null
            val curHasClock = extractClockRange(curText) != null
            val curOpenParens = unclosedParenCount(curText)

            val startsNewRow = when {
                !curHasDate && !curHasPeriod && !curHasClock -> false
                hasDate && curHasDate -> true
                hasPeriod && curHasPeriod -> true
                hasClock && curHasClock && curOpenParens <= 0 -> true
                else -> false
            }

            if (startsNewRow) {
                groups.add(mutableListOf(line))
            } else {
                cur.add(line)
            }
        }
        return groups
    }

    private fun isTableHeaderLine(
        line: String,
        hasDate: Boolean,
        hasPeriod: Boolean,
        hasClock: Boolean
    ): Boolean {
        if (hasDate || hasPeriod || hasClock) return false
        return tableHeaderKeywords.count { line.contains(it) } >= 2
    }

    private fun unclosedParenCount(text: String): Int {
        var count = 0
        for (c in text) {
            when (c) {
                '(', '（' -> count++
                ')', '）' -> count--
            }
        }
        return count
    }

    private fun hasDateOrTimeOrPeriod(text: String, ref: LocalDate): Boolean {
        return extractDate(text, ref) != null ||
            extractClockRange(text) != null ||
            extractPeriodRange(text) != null
    }

    private fun hasScheduleSignal(text: String, ref: LocalDate): Boolean {
        if (hasDateOrTimeOrPeriod(text, ref)) return true
        if (extractWeekday(text) != null) return true
        if (extractWeeksSpec(text).isNotBlank()) return true
        return false
    }

    private fun parseChunkToDrafts(
        chunk: String,
        referenceDate: LocalDate,
        defaultKindHint: ParsedItemKind?
    ): List<ParsedScheduleDraft> {
        val compact = chunk.trim()
        if (compact.length < 2) return emptyList()

        val kind = detectKind(compact, referenceDate, defaultKindHint)
        val date = extractDate(compact, referenceDate)
        val weeksSpecRaw = extractWeeksSpec(compact)
        val weekdayRaw = extractWeekday(compact)
        val clockRange = extractClockRange(compact)
        val periodRange = extractPeriodRange(compact)
        val campus = extractCampus(compact)
        val rooms = extractRooms(compact, campus)
        val teacher = extractTeacher(compact)
        val seatNo = extractSeatNo(compact)
        val examType = extractExamType(compact)
        val classCodes = extractClassCodes(compact)
        val title = extractTitle(compact, rooms, teacher, campus)

        // 既没有标题也没有时间/日期/节次信号，视为无效噪声段（例如“收到请回复”）
        if (title.isBlank() && date == null && clockRange == null && periodRange == null) {
            return emptyList()
        }

        // 双向推算日期 <-> 周次+星期
        val resolvedWeeks: String
        val resolvedDayOfWeek: Int
        val resolvedExamDate: String

        if (date != null) {
            val weekNum = SemesterConfig.weekFor(date)
            resolvedExamDate = date.toString()
            resolvedDayOfWeek = date.dayOfWeek.value - 1
            resolvedWeeks = weeksSpecRaw.ifBlank { "第${weekNum}周" }
        } else {
            val singleWeek = ScheduleExamFusion.parseWeeksSpec(weeksSpecRaw).firstOrNull()
                ?: SemesterConfig.weekFor(referenceDate)
            resolvedDayOfWeek = weekdayRaw ?: (referenceDate.dayOfWeek.value - 1)
            resolvedWeeks = weeksSpecRaw.ifBlank { "第${singleWeek}周" }
            resolvedExamDate = if (weeksSpecRaw.isNotBlank() || weekdayRaw != null) {
                SemesterConfig.weekStart(singleWeek).plusDays(resolvedDayOfWeek.toLong()).toString()
            } else {
                ""
            }
        }

        // 双向推算节次 <-> 起止时刻
        val resolvedStartPeriod: Int
        val resolvedEndPeriod: Int
        val resolvedStartClock: String
        val resolvedEndClock: String

        if (periodRange != null) {
            resolvedStartPeriod = periodRange.first
            resolvedEndPeriod = periodRange.second
            resolvedStartClock = clockRange?.first
                ?: ClassTimes.startTime(resolvedStartPeriod).orEmpty()
            resolvedEndClock = clockRange?.second
                ?: ClassTimes.endTime(resolvedEndPeriod).orEmpty()
        } else if (clockRange != null) {
            resolvedStartClock = clockRange.first
            resolvedEndClock = clockRange.second
            val mapped = ScheduleExamFusion.clockRangeToPeriods(clockRange.first, clockRange.second)
            resolvedStartPeriod = mapped.first
            resolvedEndPeriod = mapped.second
        } else {
            resolvedStartPeriod = 1
            resolvedEndPeriod = 2
            resolvedStartClock = ""
            resolvedEndClock = ""
        }

        val commonNotes = mutableListOf<String>()
        if (compact.contains("补课")) commonNotes.add("补课")
        if (compact.contains("调课")) commonNotes.add("调课")
        if (compact.contains("闭卷")) commonNotes.add("闭卷")
        if (compact.contains("开卷")) commonNotes.add("开卷")
        if (compact.contains("带学生证") || compact.contains("校园卡")) commonNotes.add("需携带证件")

        val resolvedTitle = title.ifBlank { if (kind == ParsedItemKind.EXAM) "未命名考试" else "未命名课程" }

        // 同一场考试表格行含多个独立考场（如不同班级分考场 3-602 与 阶16）时，按考场拆成独立待选条目，避免合并
        if (kind == ParsedItemKind.EXAM && rooms.size >= 2) {
            return rooms.mapIndexed { idx, singleRoom ->
                val perNote = mutableListOf<String>()
                if (classCodes.size == rooms.size) {
                    perNote.add("班级 ${classCodes[idx]}")
                } else if (classCodes.isNotEmpty()) {
                    perNote.add("班级 ${classCodes.joinToString(", ")}")
                }
                perNote.addAll(commonNotes)
                ParsedScheduleDraft(
                    kind = kind,
                    title = resolvedTitle,
                    campus = campus,
                    room = singleRoom,
                    teacher = teacher,
                    dayOfWeek = resolvedDayOfWeek,
                    startPeriod = resolvedStartPeriod,
                    endPeriod = resolvedEndPeriod,
                    weeksSpec = resolvedWeeks,
                    examDate = resolvedExamDate,
                    startClock = resolvedStartClock,
                    endClock = resolvedEndClock,
                    examType = examType,
                    seatNo = seatNo,
                    note = perNote.distinct().joinToString(" · "),
                    rawSnippet = compact.take(180)
                )
            }
        }

        val allNotes = mutableListOf<String>()
        if (classCodes.isNotEmpty()) allNotes.add("班级 ${classCodes.joinToString(", ")}")
        allNotes.addAll(commonNotes)

        return listOf(
            ParsedScheduleDraft(
                kind = kind,
                title = resolvedTitle,
                campus = campus,
                room = rooms.firstOrNull().orEmpty(),
                teacher = teacher,
                dayOfWeek = resolvedDayOfWeek,
                startPeriod = resolvedStartPeriod,
                endPeriod = resolvedEndPeriod,
                weeksSpec = resolvedWeeks,
                examDate = resolvedExamDate,
                startClock = resolvedStartClock,
                endClock = resolvedEndClock,
                examType = examType,
                seatNo = seatNo,
                note = allNotes.distinct().joinToString(" · "),
                rawSnippet = compact.take(180)
            )
        )
    }

    private fun detectKind(text: String, ref: LocalDate, hint: ParsedItemKind?): ParsedItemKind {
        val examScore = examKeywords.count { text.contains(it) }
        val explicitCourseScore = explicitCourseKeywords.count { text.contains(it) }
        val hasMultiWeek = hasMultiWeekRange(text)
        val hasConcreteDate = extractDate(text, ref) != null
        val hasClock = extractClockRange(text) != null
        val hasPeriod = extractPeriodRange(text) != null

        return when {
            examScore > 0 && examScore >= explicitCourseScore -> ParsedItemKind.EXAM
            explicitCourseScore > 0 -> ParsedItemKind.COURSE
            // 具体单日日期 + 时钟/节次且无多周范围（如教务考试安排表 2026/12/2 周三3-4节 或 12月10日 16:00-17:40）
            hasConcreteDate && (hasClock || hasPeriod) && !hasMultiWeek -> ParsedItemKind.EXAM
            hasMultiWeek -> ParsedItemKind.COURSE
            hint != null -> hint
            else -> ParsedItemKind.COURSE
        }
    }

    private fun hasMultiWeekRange(text: String): Boolean {
        val m = Regex("(?<![\\d/.\\-])第?\\s*(\\d{1,2})\\s*[-~～至到]\\s*(\\d{1,2})\\s*周(?![一二三四五六日天末])")
            .find(text) ?: return false
        val a = m.groupValues[1].toIntOrNull() ?: return false
        val b = m.groupValues[2].toIntOrNull() ?: return false
        return a in 1..SemesterConfig.totalWeeks && b in a..SemesterConfig.totalWeeks
    }

    internal fun extractDate(text: String, ref: LocalDate): LocalDate? {
        // 1. YYYY-MM-DD / YYYY/MM/DD / YYYY.MM.DD / YYYY年M月D日
        Regex("(20\\d{2})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?")
            .find(text)?.let { m ->
                val y = m.groupValues[1].toIntOrNull()
                val mo = m.groupValues[2].toIntOrNull()
                val d = m.groupValues[3].toIntOrNull()
                if (y != null && mo != null && d != null) {
                    runCatching { LocalDate.of(y, mo, d) }.getOrNull()?.let { return it }
                }
            }

        // 2. M月D日 / M月D号
        Regex("(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?")
            .find(text)?.let { m ->
                val mo = m.groupValues[1].toIntOrNull()
                val d = m.groupValues[2].toIntOrNull()
                if (mo != null && d != null) {
                    resolveMonthDay(mo, d, ref)?.let { return it }
                }
            }

        // 3. M.D 或 M/D（避免匹配到 14:00 或教室 3-201 或 1.5学分）
        Regex("(?<![\\d\\-.])(1[0-2]|0?[1-9])\\s*[/.]\\s*(3[01]|[12]\\d|0?[1-9])(?![\\d.])")
            .find(text)?.let { m ->
                val mo = m.groupValues[1].toIntOrNull()
                val d = m.groupValues[2].toIntOrNull()
                if (mo != null && d != null) {
                    resolveMonthDay(mo, d, ref)?.let { return it }
                }
            }

        // 4. 相对日期：今天 / 明天 / 后天 / 本周X / 下周X
        if (text.contains("大后天")) return ref.plusDays(3)
        if (text.contains("后天")) return ref.plusDays(2)
        if (text.contains("明天")) return ref.plusDays(1)
        if (text.contains("今天")) return ref

        Regex("(本|这|下)周([一二三四五六日天])").find(text)?.let { m ->
            val targetIdx = weekdayCharMap[m.groupValues[2].first()] ?: return@let
            val monday = ref.with(DayOfWeek.MONDAY)
            val weekOffset = if (m.groupValues[1] == "下") 7L else 0L
            return monday.plusDays(weekOffset + targetIdx.toLong())
        }
        return null
    }

    private fun resolveMonthDay(month: Int, day: Int, ref: LocalDate): LocalDate? {
        val candidate = runCatching { LocalDate.of(ref.year, month, day) }.getOrNull() ?: return null
        // 如果当前是 11/12 月而通知写 1 月，自动滚到下一年（如期末 1 月考试）
        return if (ref.monthValue >= 9 && month <= 2) {
            runCatching { LocalDate.of(ref.year + 1, month, day) }.getOrNull() ?: candidate
        } else {
            candidate
        }
    }

    internal fun extractWeekday(text: String): Int? {
        Regex("(?:星期|礼拜|周)\\s*([一二三四五六日天])").find(text)?.let { m ->
            return weekdayCharMap[m.groupValues[1].first()]
        }
        return null
    }

    internal fun extractWeeksSpec(text: String): String {
        // 区间多周：1-16周(单) / 第13-16周 / 4~9周（避免把 "24205017-2 周三" 中的 "17-2 周三" 误判成周次）
        Regex("(?<![\\d/.\\-])第?\\s*((\\d{1,2})\\s*[-~～至到]\\s*(\\d{1,2})\\s*周(?:\\s*[（(]?[单双]周?[)）]?)?)(?![一二三四五六日天末])")
            .find(text)?.let { m ->
                val a = m.groupValues[2].toIntOrNull()
                val b = m.groupValues[3].toIntOrNull()
                if (a != null && b != null && a in 1..SemesterConfig.totalWeeks && b in a..SemesterConfig.totalWeeks) {
                    val weeks = ScheduleExamFusion.parseWeeksSpec(m.groupValues[1])
                    if (weeks.isNotEmpty()) return ScheduleExamFusion.formatWeeksSpec(weeks)
                }
            }
        // 枚举周：第12、14、16周 / 12,14周
        Regex("(?<![\\d/.\\-])第?\\s*(\\d{1,2}(?:\\s*[,，、]\\s*\\d{1,2})+\\s*周)(?![一二三四五六日天末])")
            .find(text)?.let { m ->
                val weeks = ScheduleExamFusion.parseWeeksSpec(m.groupValues[1])
                if (weeks.isNotEmpty()) return ScheduleExamFusion.formatWeeksSpec(weeks)
            }
        // 单周：第15周 或 14周（避免把 "2026/12/2 周三" 中的 "2 周三" 误判成第2周）
        Regex("(?<![\\d/.\\-])第?\\s*(\\d{1,2})\\s*周(?![一二三四五六日天末])").find(text)?.let { m ->
            val w = m.groupValues[1].toIntOrNull()
            if (w != null && w in 1..SemesterConfig.totalWeeks) return "第${w}周"
        }
        return ""
    }

    internal fun extractClockRange(text: String): Pair<String, String>? {
        // 1. 标准 HH:mm - HH:mm
        Regex("(\\d{1,2})\\s*[:：]\\s*(\\d{2})\\s*[-~～—至到]\\s*(\\d{1,2})\\s*[:：]\\s*(\\d{2})")
            .find(text)?.let { m ->
                val sh = m.groupValues[1].toIntOrNull() ?: return@let
                val sm = m.groupValues[2].toIntOrNull() ?: return@let
                val eh = m.groupValues[3].toIntOrNull() ?: return@let
                val em = m.groupValues[4].toIntOrNull() ?: return@let
                if (sh in 0..23 && eh in 0..23 && sm in 0..59 && em in 0..59) {
                    return "%02d:%02d".format(sh, sm) to "%02d:%02d".format(eh, em)
                }
            }
        // 2. 口语时钟：下午2点-4点 / 上午9点半-11点半 / 晚上7点-9点
        Regex("(上午|早上|下午|晚上|晚)?\\s*(\\d{1,2})\\s*[点时](半|\\d{1,2}分?)?\\s*[-~～—至到]\\s*(上午|早上|下午|晚上|晚)?\\s*(\\d{1,2})\\s*[点时](半|\\d{1,2}分?)?")
            .find(text)?.let { m ->
                val p1 = m.groupValues[1]
                val p2 = m.groupValues[4].ifBlank { p1 }
                val sh = adjustHour(m.groupValues[2].toIntOrNull() ?: return@let, p1)
                val sm = parseOralMinute(m.groupValues[3])
                val eh = adjustHour(m.groupValues[5].toIntOrNull() ?: return@let, p2)
                val em = parseOralMinute(m.groupValues[6])
                if (sh in 0..23 && eh in 0..23) {
                    return "%02d:%02d".format(sh, sm) to "%02d:%02d".format(eh, em)
                }
            }
        return null
    }

    private fun adjustHour(hour: Int, period: String): Int {
        return when {
            (period == "下午" || period == "晚上" || period == "晚") && hour in 1..11 -> hour + 12
            else -> hour
        }
    }

    private fun parseOralMinute(raw: String): Int = when {
        raw == "半" -> 30
        raw.isBlank() -> 0
        else -> raw.removeSuffix("分").toIntOrNull()?.coerceIn(0, 59) ?: 0
    }

    internal fun extractPeriodRange(text: String): Pair<Int, Int>? {
        Regex("(?<!\\d)第?\\s*(\\d{1,2})\\s*[-~～—–－ー―一至到、,]\\s*(\\d{1,2})\\s*大?节").find(text)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            val b = m.groupValues[2].toIntOrNull() ?: return@let
            if (a in 1..12 && b in a..12) return a to b
        }
        Regex("第\\s*(\\d{1,2})\\s*节").find(text)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            if (a in 1..12) return a to a
        }
        return null
    }

    private fun extractCampus(text: String): String {
        for ((kw, canonical) in campusKeywords) {
            if (text.contains(kw)) return canonical
        }
        return ""
    }

    private fun extractRooms(text: String, campus: String): List<String> {
        // 1. 显式标签：地点/考场/教室：xxx（支持顿号分隔多个考场）
        Regex("(?:考场|地点|教室|课室|位置)\\s*[:：]\\s*([^\\n;；。]+)").find(text)?.let { m ->
            val raw = m.groupValues[1].trim()
                .replace(Regex("(?:座[位号]?|老师|教师|时间|日期|备注).*$"), "")
                .trim()
            if (raw.isNotBlank()) {
                val pieces = raw.split(Regex("[、,，/]+"))
                    .map { completeWrappedRoomNumber(stripCampusPrefix(it.trim(), campus), text) }
                    .filter { it.length >= 2 }
                    .distinct()
                if (pieces.isNotEmpty()) return pieces.take(4)
            }
        }
        // 2. 常见校园楼号/阶梯教室/实验楼格式（含 3教-阶10 / 3教-阶 / 3教201 / 教3-105 / 3-602 / 阶16 等）
        val roomRegex = Regex(
            "(?:(?<!\\d)\\d{1,2}\\s*教\\s*[-—]?\\s*(?:阶梯?\\s*\\d{0,3}|[A-Za-z]?\\d{2,4})|" +
                "[一二三四五六七八九十]\\s*教\\s*[-—]?\\s*(?:阶梯?\\s*\\d{0,3}|[A-Za-z]?\\d{2,4})|" +
                "教\\s*\\d{1,2}\\s*[-—]?\\s*(?:阶梯?\\s*\\d{0,3}|[A-Za-z]?\\d{2,4})|" +
                "(?<![A-Za-z0-9])[A-Za-z]?\\d{1,2}[-—]\\d{3}(?!\\d)|" +
                "(?:阶梯?|实验楼|实训楼|地学楼|软件楼|经管楼|文法楼|理工楼|教学楼|综合楼|主楼|核工楼|逸夫楼|机房|体育馆|操场|图书馆)\\s*[A-Za-z0-9\\-—]{1,10}|" +
                "(?<![A-Za-z0-9])[A-Za-z]\\d{3,4}(?!\\d))"
        )
        return roomRegex.findAll(text)
            .map { it.value.replace(Regex("\\s+"), "").trim() }
            .filter { cand -> !Regex("^\\d{1,2}-\\d{1,2}$").matches(cand) }
            .map { cand -> completeWrappedRoomNumber(cand, text) }
            .distinct()
            .take(4)
            .toList()
    }

    /**
     * 处理表格单元格换行导致的教室截断：例如上行是 `3教-阶`，下行单独是 `10` 或 `15 16:00-17:40)`，
     * 自动拼回 `3教-阶10` / `3教-阶15`。
     */
    private fun completeWrappedRoomNumber(room: String, fullText: String): String {
        if (!room.endsWith("阶") && !room.endsWith("阶梯") && !room.endsWith("-") && !room.endsWith("—")) {
            return room
        }
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val matchLineIdx = lines.indexOfFirst { it.replace(" ", "").contains(room) }
        val tailLines = if (matchLineIdx >= 0 && matchLineIdx + 1 < lines.size) {
            lines.subList(matchLineIdx + 1, lines.size)
        } else {
            emptyList()
        }
        // 优先 1：独占一行的 1-3 位房间号（如 "10"）
        for (ln in tailLines) {
            if (Regex("^\\d{1,3}$").matches(ln)) {
                return "$room$ln"
            }
        }
        // 优先 2：紧跟时钟区间的行首 1-3 位房间号（如 "15 16:00-17:40)"）
        for (ln in tailLines) {
            Regex("^(\\d{1,3})\\s+\\d{1,2}\\s*[:：]\\s*\\d{2}").find(ln)?.let { m ->
                return "$room${m.groupValues[1]}"
            }
        }
        return room
    }

    private fun extractClassCodes(text: String): List<String> {
        // 保留班级后缀（如 2420501Z、2420502-4）
        return Regex("(?<!\\d)(\\d{6,9}[A-Za-z]?(?:-\\d{1,2})?)(?!\\d)")
            .findAll(text)
            .map { it.groupValues[1] }
            .distinct()
            .take(6)
            .toList()
    }

    private fun stripCampusPrefix(raw: String, campus: String): String {
        var s = raw
        for ((kw, _) in campusKeywords) {
            if (s.startsWith(kw)) {
                s = s.removePrefix(kw).trim()
            }
        }
        if (campus.isNotBlank() && s.startsWith(campus)) {
            s = s.removePrefix(campus).trim()
        }
        return s.ifBlank { raw }
    }

    private fun extractTeacher(text: String): String {
        Regex("(?:教师|授课教师|任课教师|老师)\\s*[:：]\\s*([\\u4e00-\\u9fa5A-Za-z·]{2,8})")
            .find(text)?.let { return it.groupValues[1].trim() }
        Regex("([\\u4e00-\\u9fa5]{1,3}老师)").find(text)?.let { return it.groupValues[1].trim() }
        return ""
    }

    private fun extractSeatNo(text: String): String {
        Regex("(?:座位号?|座号)\\s*[:：]?\\s*([A-Za-z0-9\\-]{1,8})").find(text)?.let {
            return it.groupValues[1].trim()
        }
        return ""
    }

    private fun extractExamType(text: String): String = when {
        text.contains("期末") -> "期末考试"
        text.contains("期中") -> "期中考试"
        text.contains("补考") -> "补考"
        text.contains("缓考") -> "缓考"
        text.contains("随堂") -> "随堂测验"
        text.contains("机考") -> "上机考试"
        text.contains("答辩") -> "课程答辩"
        else -> "自定义考试"
    }

    private fun extractTitle(text: String, rooms: List<String>, teacher: String, campus: String): String {
        // 1. 《课程名》
        Regex("《([^》\\n]{1,40})》").find(text)?.let {
            return it.groupValues[1].trim()
        }
        // 2. 显式字段标签
        Regex("(?:课程名称|考试科目|考试课程|课程名|科目|课程)\\s*[:：]\\s*([^\\n,，;；。]+)")
            .find(text)?.let { m ->
                val cleaned = m.groupValues[1]
                    .replace(Regex("\\s+(?:时间|日期|地点|考场|教室|教师|老师|周次|第\\d).*$"), "")
                    .trim()
                if (cleaned.isNotBlank()) return cleaned.take(32)
            }

        // 3. 剥离通知标签、日期、时间、节次、地点、老师、班级号后，提取剩余首个中文/字母词组
        var s = text
            .replace(Regex("^(?:[（(]?\\d{1,2}[)）\\.、]|[①②③④⑤⑥⑦⑧⑨⑩])\\s*"), "")
            .replace(Regex("【[^】]{1,20}】"), " ")
            .replace(Regex("\\[[^\\]]{1,20}\\]"), " ")
            .replace(Regex("(?:关于|通知|提醒|各位同学|请大家|注意)[:：]?"), " ")
            .replace(Regex("20\\d{2}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?"), " ")
            .replace(Regex("\\d{1,2}月\\d{1,2}[日号]?"), " ")
            .replace(Regex("\\d{1,2}[:：]\\d{2}\\s*[-~～—至到]\\s*\\d{1,2}[:：]\\d{2}"), " ")
            .replace(Regex("(?<!\\d)第?\\d{1,2}[-~～—–－ー―一至到、,]\\d{1,2}大?节"), " ")
            .replace(Regex("(?<![\\d/.\\-])第?\\d{1,2}[-~～至到]\\d{1,2}周(?:[（(]?[单双]周?[)）]?)?(?![一二三四五六日天末])"), " ")
            .replace(Regex("(?<![\\d/.\\-])第?\\d{1,2}周(?![一二三四五六日天末])"), " ")
            .replace(Regex("(?:本|这|下)?(?:星期|礼拜|周)[一二三四五六日天]"), " ")
            .replace(Regex("(?:考场|地点|教室|座位号?|教师|老师)\\s*[:：]\\s*[^\\n\\s,，;；]+"), " ")
            .replace(Regex("(?<!\\d)\\d{5,}[A-Za-z]?(-\\d{1,2})?"), " ")

        for (r in rooms) {
            val part = r.trim()
            if (part.isNotBlank()) {
                s = s.replace(part, " ")
                val roomPrefix = part.replace(Regex("\\d{1,3}$"), "")
                if (roomPrefix.length >= 2) s = s.replace(roomPrefix, " ")
            }
        }
        if (teacher.isNotBlank()) s = s.replace(teacher, " ")
        if (campus.isNotBlank()) s = s.replace(campus, " ")
        for ((kw, _) in campusKeywords) s = s.replace(kw, " ")

        val candidate = s.split(Regex("[\\n,，、;；。|｜\\t()（）]+|\\s{2,}"))
            .map {
                it.trim()
                    .replace(Regex("^\\d+\\s+"), "")
                    .replace(Regex("\\s+\\d+$"), "")
                    // OCR 表格紧贴相邻列时，常把“三教”“3教”粘在课程名前面
                    .replace(Regex("^(?:[一二三四五六七八九十\\d]{1,2}教(?:学楼)?)(?=[\\u4e00-\\u9fa5]{2,})"), "")
                    .removeSuffix("期末考试")
                    .removeSuffix("期中考试")
                    .removeSuffix("考试")
                    .removeSuffix("补课")
                    .removeSuffix("加课")
                    .removeSuffix("调课")
                    .trim(':', '：', '-', '—', '、', ' ')
            }
            .firstOrNull { token ->
                token.length >= 2 &&
                    Regex("[\\u4e00-\\u9fa5A-Za-z]").containsMatchIn(token) &&
                    token !in setOf("考试", "补课", "加课", "调课", "通知", "闭卷", "开卷", "上午", "下午", "晚上")
            }
        return candidate?.take(32).orEmpty()
    }
}
