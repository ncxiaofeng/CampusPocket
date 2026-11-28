package com.ecut.campuspocket.data.schedule

import com.ecut.campuspocket.data.SemesterConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleFusionAndParserTest {

    @Test
    fun `parses and formats weeks specs including odd even and discrete`() {
        assertEquals((1..16).toSet(), ScheduleExamFusion.parseWeeksSpec("1-16周"))
        assertEquals(setOf(1, 3, 5, 7), ScheduleExamFusion.parseWeeksSpec("1-8周(单)"))
        assertEquals(setOf(2, 4, 6, 8), ScheduleExamFusion.parseWeeksSpec("1-8周(双)"))
        assertEquals(setOf(12, 14, 16), ScheduleExamFusion.parseWeeksSpec("12、14、16周"))
        assertEquals("1-8周", ScheduleExamFusion.formatWeeksSpec((1..8).toSet()))
        assertEquals("1-7周(单)", ScheduleExamFusion.formatWeeksSpec(setOf(1, 3, 5, 7)))
        assertEquals("第15周", ScheduleExamFusion.formatWeeksSpec(setOf(15)))
    }

    @Test
    fun `maps exam clocks to schedule periods accurately`() {
        assertEquals(1 to 3, ScheduleExamFusion.clockRangeToPeriods("08:30", "10:30"))
        assertEquals(5 to 6, ScheduleExamFusion.clockRangeToPeriods("14:00", "16:00"))
        assertEquals(9 to 10, ScheduleExamFusion.clockRangeToPeriods("19:00", "21:00"))
        assertEquals(1 to 2, ScheduleExamFusion.clockRangeToPeriods("", ""))
    }

    @Test
    fun `maps exam entity into matching week only`() {
        val week12Start = SemesterConfig.weekStart(12) // 2026-11-23 (Mon)
        val exam = ScheduleExamFusion.buildCustomExamEntity(
            courseName = "高等数学",
            examDate = week12Start.plusDays(2).toString(), // Wed
            startClock = "14:00",
            endClock = "16:00",
            location = "3-201"
        )
        val slot = ScheduleExamFusion.examSlotInWeek(exam, week12Start)
        assertEquals(Triple(2, 5, 6), slot)

        val otherWeek = ScheduleExamFusion.examSlotInWeek(exam, SemesterConfig.weekStart(13))
        assertNull(otherWeek)
    }

    @Test
    fun `parses single exam notification from clipboard`() {
        val raw = "【期末考试通知】《高等数学》 12月15日 14:00-16:00 地点：广兰 3-201 座位号：18 闭卷"
        val drafts = ScheduleTextParser.parse(raw, referenceDate = LocalDate.of(2026, 11, 28))
        assertEquals(1, drafts.size)
        val d = drafts.first()
        assertEquals(ParsedItemKind.EXAM, d.kind)
        assertEquals("高等数学", d.title)
        assertEquals("2026-12-15", d.examDate)
        assertEquals("14:00", d.startClock)
        assertEquals("16:00", d.endClock)
        assertEquals("广兰校园", d.campus)
        assertEquals("3-201", d.room)
        assertEquals("18", d.seatNo)
        assertTrue(d.note.contains("闭卷"))
    }

    @Test
    fun `rolls january exam date into next year when received in autumn`() {
        val raw = "1月8日 上午9点-11点 大学物理期末考试 广兰 阶18"
        val drafts = ScheduleTextParser.parse(raw, referenceDate = LocalDate.of(2026, 11, 28))
        assertEquals(1, drafts.size)
        val d = drafts.first()
        assertEquals(ParsedItemKind.EXAM, d.kind)
        assertEquals("2027-01-08", d.examDate)
        assertEquals("09:00", d.startClock)
        assertEquals("11:00", d.endClock)
        assertEquals("阶18", d.room)
    }

    @Test
    fun `parses makeup course notice accurately`() {
        val raw = "【补课通知】《大地测量学基础》 第13-16周 周五 第3-4节 广兰 阶18 王老师"
        val drafts = ScheduleTextParser.parse(raw, referenceDate = LocalDate.of(2026, 11, 28))
        assertEquals(1, drafts.size)
        val d = drafts.first()
        assertEquals(ParsedItemKind.COURSE, d.kind)
        assertEquals("大地测量学基础", d.title)
        assertEquals(4, d.dayOfWeek)
        assertEquals(3, d.startPeriod)
        assertEquals(4, d.endPeriod)
        assertEquals("13-16周", d.weeksSpec)
        assertEquals("广兰校园", d.campus)
        assertEquals("阶18", d.room)
        assertEquals("王老师", d.teacher)
    }

    @Test
    fun `splits multi-line OCR exam table and skips header`() {
        val ocr = """
            课程名称 考试时间 考场
            数据结构 2026-12-20 08:30-10:30 教3-105
            线性代数 2026-12-22 14:00-16:00 3-201
            大学英语 2026-12-24 19:00-21:00 实验楼302
        """.trimIndent()
        val drafts = ScheduleTextParser.parse(ocr, referenceDate = LocalDate.of(2026, 11, 28))
        assertEquals(3, drafts.size)
        assertEquals("数据结构", drafts[0].title)
        assertEquals("2026-12-20", drafts[0].examDate)
        assertEquals("教3-105", drafts[0].room)
        assertEquals("线性代数", drafts[1].title)
        assertEquals("2026-12-22", drafts[1].examDate)
        assertEquals("大学英语", drafts[2].title)
        assertEquals("2026-12-24", drafts[2].examDate)
    }

    @Test
    fun `resolves relative weekday and oral afternoon clock`() {
        // 2026-11-28 is Saturday; next Wednesday is 2026-12-02
        val raw = "下周三下午2点-4点 《概率论与数理统计》随堂测验 教3-105"
        val drafts = ScheduleTextParser.parse(raw, referenceDate = LocalDate.of(2026, 11, 28))
        assertEquals(1, drafts.size)
        val d = drafts.first()
        assertNotNull(d)
        assertEquals(ParsedItemKind.EXAM, d.kind)
        assertEquals("概率论与数理统计", d.title)
        assertEquals("2026-12-02", d.examDate)
        assertEquals("14:00", d.startClock)
        assertEquals("16:00", d.endClock)
    }

    @Test
    fun `parses wrapped ECUT exam table OCR screenshot accurately`() {
        val realOcr = """
            工程力学 | 3教-阶|14周周四7-8节(12月10日
            24205012, 2420502 69 16:00-17:40)
            10
            3教-阶|14周周四7-8节(12月10日
            2420503, 2420504, 2425093 工程力学 82
            15 16:00-17:40)
            工程力学 |3教-阶|14周周四7-8节(12月10日
            3 2425901, 2425902 75
            16 16:00-17:40)
        """.trimIndent()
        val drafts = ScheduleTextParser.parse(
            rawText = realOcr,
            referenceDate = LocalDate.of(2026, 11, 28),
            defaultKindHint = ParsedItemKind.COURSE
        )
        assertEquals(3, drafts.size)

        assertEquals(ParsedItemKind.EXAM, drafts[0].kind)
        assertEquals("工程力学", drafts[0].title)
        assertEquals("2026-12-10", drafts[0].examDate)
        assertEquals("16:00", drafts[0].startClock)
        assertEquals("17:40", drafts[0].endClock)
        assertEquals("3教-阶10", drafts[0].room)
        assertTrue(drafts[0].note.contains("24205012"))

        assertEquals(ParsedItemKind.EXAM, drafts[1].kind)
        assertEquals("工程力学", drafts[1].title)
        assertEquals("2026-12-10", drafts[1].examDate)
        assertEquals("16:00", drafts[1].startClock)
        assertEquals("17:40", drafts[1].endClock)
        assertEquals("3教-阶15", drafts[1].room)
        assertTrue(drafts[1].note.contains("2420503"))

        assertEquals(ParsedItemKind.EXAM, drafts[2].kind)
        assertEquals("工程力学", drafts[2].title)
        assertEquals("2026-12-10", drafts[2].examDate)
        assertEquals("16:00", drafts[2].startClock)
        assertEquals("17:40", drafts[2].endClock)
        assertEquals("3教-阶16", drafts[2].room)
        assertTrue(drafts[2].note.contains("2425901"))
    }

    @Test
    fun `handles katakana dash periods and avoids date-day false week match`() {
        val realOcr = """
            2420501Z |3-602、三教地理信息系统原|4十天早基 李大军、吴静、王建
            2026/12/2 周三3ー4节 13 |阶16 理(B) |强、刘向鋼 128
            2420502-4
        """.trimIndent()
        val drafts = ScheduleTextParser.parse(
            rawText = realOcr,
            referenceDate = LocalDate.of(2026, 11, 28),
            defaultKindHint = ParsedItemKind.COURSE
        )
        // 同一考试行含两个考场与两个班级组，按考场拆成两条独立待选记录，绝不合并考场
        assertEquals(2, drafts.size)

        val d1 = drafts[0]
        assertEquals(ParsedItemKind.EXAM, d1.kind)
        assertEquals("地理信息系统原", d1.title)
        assertEquals("2026-12-02", d1.examDate)
        assertEquals("第13周", d1.weeksSpec)
        assertEquals(2, d1.dayOfWeek)
        assertEquals(3, d1.startPeriod)
        assertEquals(4, d1.endPeriod)
        assertEquals("10:00", d1.startClock)
        assertEquals("11:40", d1.endClock)
        assertEquals("3-602", d1.room)
        assertEquals("班级 2420501Z", d1.note)

        val d2 = drafts[1]
        assertEquals(ParsedItemKind.EXAM, d2.kind)
        assertEquals("地理信息系统原", d2.title)
        assertEquals("2026-12-02", d2.examDate)
        assertEquals("阶16", d2.room)
        assertEquals("班级 2420502-4", d2.note)
    }

    @Test
    fun `does not confuse class code suffix before weekday with week range`() {
        val realOcr = """
            2026/12/9 24205017-2 周三3-4节 14 3-622 数字图像处理
            2026/12/9 2420503-4 周三3-4节 14 3-623 数字图像处理
        """.trimIndent()
        val drafts = ScheduleTextParser.parse(
            rawText = realOcr,
            referenceDate = LocalDate.of(2026, 11, 28),
            defaultKindHint = ParsedItemKind.COURSE
        )
        assertEquals(2, drafts.size)

        assertEquals(ParsedItemKind.EXAM, drafts[0].kind)
        assertEquals("数字图像处理", drafts[0].title)
        assertEquals("2026-12-09", drafts[0].examDate)
        assertEquals("第14周", drafts[0].weeksSpec)
        assertEquals("10:00", drafts[0].startClock)
        assertEquals("11:40", drafts[0].endClock)
        assertEquals("3-622", drafts[0].room)
        assertEquals("班级 24205017-2", drafts[0].note)

        assertEquals(ParsedItemKind.EXAM, drafts[1].kind)
        assertEquals("数字图像处理", drafts[1].title)
        assertEquals("2026-12-09", drafts[1].examDate)
        assertEquals("第14周", drafts[1].weeksSpec)
        assertEquals("3-623", drafts[1].room)
        assertEquals("班级 2420503-4", drafts[1].note)
    }

    @Test
    fun `resolves holiday swap across weeks and rest days without mutating courses`() {
        // 2026-09-07 is Week 1 Monday -> Week 5 Monday is 2026-10-05, Week 5 Saturday is 2026-10-10
        val monCourse = ScheduleExamFusion.buildCustomCourseEntity(
            title = "高等数学",
            dayOfWeek = 0, // 周一
            startPeriod = 1,
            endPeriod = 2,
            weeksSpec = "1-16周(单)", // 第5周单周有课
            room = "3-201"
        )
        val satCourse = ScheduleExamFusion.buildCustomCourseEntity(
            title = "周末选修",
            dayOfWeek = 5, // 周六
            startPeriod = 5,
            endPeriod = 6,
            weeksSpec = "1-16周",
            room = "阶10"
        )
        val adjustments = listOf(
            ScheduleDayAdjustment("2026-10-05", ScheduleAdjustmentType.REST, label = "国庆放假"),
            ScheduleDayAdjustment("2026-10-10", ScheduleAdjustmentType.SWAP, sourceDate = "2026-10-05", label = "国庆调休")
        ).associateBy { it.targetDate }

        // 10月5日放假：无课
        val oct5 = ScheduleAdjustments.resolveDayCourses(
            listOf(monCourse, satCourse),
            LocalDate.of(2026, 10, 5),
            adjustments
        )
        assertTrue(oct5.isEmpty())

        // 10月10日周六调休上10月5日周一的课：显示高等数学在周六（day=5）且带调休标记，原周六选修不显示
        val oct10 = ScheduleAdjustments.resolveDayCourses(
            listOf(monCourse, satCourse),
            LocalDate.of(2026, 10, 10),
            adjustments
        )
        assertEquals(1, oct10.size)
        assertEquals("高等数学", oct10[0].course.title)
        assertEquals(5, oct10[0].displayDayOfWeek)
        assertEquals("调休", oct10[0].adjustedTag)
        assertTrue(oct10[0].adjustedDetail.contains("10/5"))
    }

    @Test
    fun `parses chinese university holiday rest and swap notice from clipboard`() {
        val notice = "【国庆放假通知】10月1日至10月7日放假调休，共7天。9月27日（周日）上10月2日（周五）的课，10月10日（周六）上10月5日（周一）的课。"
        val parsed = ScheduleAdjustments.parseHolidayNotice(notice, LocalDate.of(2026, 9, 20))
        assertEquals(7, parsed.rests.size)
        assertEquals("2026-10-01", parsed.rests.first().targetDate)
        assertEquals("2026-10-07", parsed.rests.last().targetDate)
        assertEquals(2, parsed.swaps.size)
        assertEquals("2026-09-27", parsed.swaps[0].targetDate)
        assertEquals("2026-10-02", parsed.swaps[0].sourceDate)
        assertEquals("2026-10-10", parsed.swaps[1].targetDate)
        assertEquals("2026-10-05", parsed.swaps[1].sourceDate)
    }

    @Test
    fun `parses 2026 mid-autumn and national day state council notice with per-holiday labels and inferred swaps`() {
        val notice = """
            2026年放假安排：
            中秋节：9月25日（周五）至27日（周日）放假，共3天。
            国庆节：10月1日（周四）至7日（周三）放假调休，共7天。9月20日（周日）、10月10日（周六）上班。
        """.trimIndent()
        val parsed = ScheduleAdjustments.parseHolidayNotice(notice, LocalDate.of(2026, 11, 28))
        assertEquals(10, parsed.rests.size)
        assertEquals("中秋放假", parsed.rests.first { it.targetDate == "2026-09-25" }.label)
        assertEquals("国庆放假", parsed.rests.first { it.targetDate == "2026-10-01" }.label)

        assertEquals(2, parsed.swaps.size)
        assertEquals("2026-09-20", parsed.swaps[0].targetDate)
        assertEquals("2026-10-05", parsed.swaps[0].sourceDate)
        assertTrue(parsed.swaps[0].label.contains("国庆调休"))
        assertEquals("2026-10-10", parsed.swaps[1].targetDate)
        assertEquals("2026-10-06", parsed.swaps[1].sourceDate)
    }

    @Test
    fun `parses reversed, paired, and weekday-only university holiday swap notices`() {
        val reversed = "国庆节：10月1日至7日放假。将10月5日（周一）的课调至9月20日（周日）上，原10月6日（周二）课程调整至10月10日（周六）。"
        val p1 = ScheduleAdjustments.parseHolidayNotice(reversed, LocalDate.of(2026, 9, 15))
        assertEquals(2, p1.swaps.size)
        assertEquals("2026-09-20", p1.swaps[0].targetDate)
        assertEquals("2026-10-05", p1.swaps[0].sourceDate)
        assertEquals("2026-10-10", p1.swaps[1].targetDate)
        assertEquals("2026-10-06", p1.swaps[1].sourceDate)

        val pairedWd = "国庆节：10月1日至7日放假调休。9月20日（周日）、10月10日（周六）上班，分别补上星期一、星期二的课。"
        val p2 = ScheduleAdjustments.parseHolidayNotice(pairedWd, LocalDate.of(2026, 9, 15))
        assertEquals(2, p2.swaps.size)
        assertEquals("2026-09-20", p2.swaps[0].targetDate)
        assertEquals("2026-10-05", p2.swaps[0].sourceDate)
        assertEquals("2026-10-10", p2.swaps[1].targetDate)
        assertEquals("2026-10-06", p2.swaps[1].sourceDate)
    }
}
