package com.ecut.campuspocket.data.remote.jw

/**
 * 正方教务页面的纯文本解析逻辑，独立出来便于单元测试。
 */
object JwParser {

    fun selectedOptionValue(html: String, id: String): String? {
        val select = Regex(
            """<select[^>]*\bid\s*=\s*["']$id["'][\s\S]*?</select>""",
            RegexOption.IGNORE_CASE
        ).find(html)?.value ?: return null
        return Regex(
            """<option[^>]*\bvalue\s*=\s*["']([^"']*)["'][^>]*\bselected\b""",
            RegexOption.IGNORE_CASE
        ).find(select)?.groupValues?.get(1)
    }

    /**
     * 解析 <select id="..."> 的选项，返回 value 与可读文本。
     */
    fun parseSelectOptions(html: String, id: String): List<Pair<String, String>> {
        val select = Regex(
            """<select[^>]*\bid\s*=\s*["']$id["'][\s\S]*?</select>""",
            RegexOption.IGNORE_CASE
        ).find(html)?.value ?: return emptyList()
        return Regex(
            """<option[^>]*\bvalue\s*=\s*["']([^"']*)["'][^>]*>([\s\S]*?)</option>""",
            RegexOption.IGNORE_CASE
        ).findAll(select).map { match ->
            match.groupValues[1] to decodeEntities(
                match.groupValues[2].replace(Regex("<[^>]+>"), "")
            ).trim()
        }.toList()
    }

    /**
     * 读取隐藏 input 的 value，例如空闲教室页的 xnm / xqm。
     */
    fun parseInputValue(html: String, id: String): String? {
        val tag = Regex(
            """<input[^>]*\bid\s*=\s*["']$id["'][^>]*>""",
            RegexOption.IGNORE_CASE
        ).find(html)?.value ?: return null
        return Regex("""\bvalue\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)
    }

    fun decodeEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
    }

    fun parsePeriods(text: String): Pair<Int, Int>? {
        val match = Regex("""(\d+)(?:\s*-\s*(\d+))?""").find(text) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
        return start to end
    }

    /**
     * 解析考试时间，返回 (yyyy-MM-dd, HH:mm, HH:mm)。
     * 兼容 2026-01-10(09:00-11:00)、2026-1-4 9:00-11:00、
     * 2026年1月10日 09:00-11:00 等写法。
     */
    fun parseExamTime(text: String): Triple<String, String, String> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Triple("", "", "")

        val isoDate = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(trimmed)
        val cnDate = Regex("""(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""").find(trimmed)
        val dateMatch = isoDate ?: cnDate
        val date = dateMatch?.let {
            val year = it.groupValues[1]
            val month = it.groupValues[2].toIntOrNull() ?: return@let ""
            val day = it.groupValues[3].toIntOrNull() ?: return@let ""
            "%s-%02d-%02d".format(year, month, day)
        }.orEmpty()

        val range = Regex("""(\d{1,2}:\d{2})\s*[-~～至]\s*(\d{1,2}:\d{2})""").find(trimmed)
        val startClock = range?.groupValues?.get(1)?.let { normalizeClock(it) }
            ?: Regex("""(\d{1,2}:\d{2})""").find(trimmed)?.groupValues?.get(1)?.let { normalizeClock(it) }
            ?: ""
        val endClock = range?.groupValues?.get(2)?.let { normalizeClock(it) }.orEmpty()
        return Triple(date, startClock, endClock)
    }

    private fun normalizeClock(value: String): String {
        val parts = value.split(":")
        if (parts.size != 2) return value
        val hour = parts[0].toIntOrNull() ?: return value
        val minute = parts[1].toIntOrNull() ?: return value
        return "%02d:%02d".format(hour, minute)
    }

    /**
     * 支持 4-9周、1-16周(单)、1-16周（双）、1-4,6-8周 等写法。
     */
    fun parseWeeks(spec: String): Set<Int> {
        if (spec.isBlank()) return emptySet()
        val single = spec.contains("单")
        val double = spec.contains("双")
        val main = spec.replace(Regex("""[（(][^）)]*[）)]"""), "")
        val weeks = linkedSetOf<Int>()
        Regex("""(\d+)(?:\s*-\s*(\d+))?""").findAll(main).forEach { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@forEach
            val end = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
            for (week in start..end) {
                if (single && week % 2 == 0) continue
                if (double && week % 2 == 1) continue
                weeks.add(week)
            }
        }
        return weeks
    }
}
