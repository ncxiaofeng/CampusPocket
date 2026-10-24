package com.ecut.campuspocket.data.remote.jw

import org.junit.Assert.assertEquals
import org.junit.Test

class JwParserTest {

    @Test
    fun `parses plain week range`() {
        assertEquals((4..9).toSet(), JwParser.parseWeeks("4-9周"))
    }

    @Test
    fun `parses odd weeks`() {
        assertEquals((1..15 step 2).toSet(), JwParser.parseWeeks("1-16周(单)"))
    }

    @Test
    fun `parses even weeks with chinese parentheses`() {
        assertEquals((2..16 step 2).toSet(), JwParser.parseWeeks("1-16周（双）"))
    }

    @Test
    fun `parses multiple ranges`() {
        assertEquals(
            setOf(1, 2, 3, 4, 6, 7, 8),
            JwParser.parseWeeks("1-4,6-8周")
        )
    }

    @Test
    fun `parses period text`() {
        assertEquals(1 to 2, JwParser.parsePeriods("1-2节"))
        assertEquals(9 to 10, JwParser.parsePeriods("9-10节"))
        assertEquals(1 to 4, JwParser.parsePeriods("1-4节"))
    }

    @Test
    fun `parses exam time with range`() {
        assertEquals(
            Triple("2026-01-10", "09:00", "11:00"),
            JwParser.parseExamTime("2026-01-10(09:00-11:00)")
        )
    }

    @Test
    fun `parses exam time with single digit date and time`() {
        assertEquals(
            Triple("2026-01-04", "09:05", "10:35"),
            JwParser.parseExamTime("2026-1-4 9:05-10:35")
        )
    }

    @Test
    fun `parses chinese exam date`() {
        assertEquals(
            Triple("2026-06-20", "14:00", "16:00"),
            JwParser.parseExamTime("2026年6月20日（14:00-16:00）")
        )
    }

    @Test
    fun `keeps raw text when exam date is unknown`() {
        assertEquals(Triple("", "", ""), JwParser.parseExamTime("另行通知"))
    }

    @Test
    fun `parses select options for classroom filters`() {
        val html = """
            <select name="lh" id="lh" class="form-control">
              <option value="">全部</option>
              <option value="18">田径场</option>
              <option value="17">图书馆（枫林）</option>
            </select>
        """.trimIndent()
        assertEquals(
            listOf("" to "全部", "18" to "田径场", "17" to "图书馆（枫林）"),
            JwParser.parseSelectOptions(html, "lh")
        )
    }

    @Test
    fun `reads hidden term inputs from classroom page`() {
        val html = """<input type="hidden" name="xnm" value="2026" id="xnm">"""
        assertEquals("2026", JwParser.parseInputValue(html, "xnm"))
    }

    @Test
    fun `picks selected option from term select`() {
        val html = """
            <select name="xnm" id="xnm" class="form-control">
              <option value="2025">2025-2026</option>
              <option value="2026" selected="selected">2026-2027</option>
            </select>
            <select name="xqm" id="xqm">
              <option value="3" selected="selected">1</option>
              <option value="12">2</option>
            </select>
        """.trimIndent()
        assertEquals("2026", JwParser.selectedOptionValue(html, "xnm"))
        assertEquals("3", JwParser.selectedOptionValue(html, "xqm"))
    }
}
