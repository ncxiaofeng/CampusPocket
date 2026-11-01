package com.ecut.campuspocket.data.remote.chaoxing

import com.ecut.campuspocket.network.CampusEndpoints

/** 学习通作业/考试 HTML 的纯解析逻辑，便于单元测试。 */
object ChaoxingHtmlParser {

    private val STATUS_HINTS = listOf(
        "待完成", "未完成", "未提交", "待提交", "做作业", "去完成",
        "未开始", "进行中", "待参加", "待考试",
        "已完成", "已提交", "待批阅", "已批阅", "已过期", "已结束", "已评分"
    )

    fun parseWorkList(html: String, course: ChaoxingCourse): List<ChaoxingTask> {
        val dtRows = parseWorkListDt(html, course)
        return if (dtRows.isNotEmpty()) dtRows else parseWorkListLi(html, course)
    }

    private fun parseWorkListDt(html: String, course: ChaoxingCourse): List<ChaoxingTask> {
        val result = mutableListOf<ChaoxingTask>()
        val rowRegex = Regex(
            """<dt\b([^>]*)>(.*?)</dt>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        rowRegex.findAll(html).forEach { match ->
            val attrs = match.groupValues[1]
            val inner = match.groupValues[2]
            val data = attr(attrs, "data") ?: return@forEach
            if (data.isBlank()) return@forEach
            result += ChaoxingTask(
                type = ChaoxingClient.TYPE_WORK,
                title = innerTag(inner, "h3").cleanHtml().ifBlank { "未命名作业" },
                courseName = course.name,
                courseId = course.courseId,
                classId = course.classId,
                url = absoluteUrl(data),
                statusText = workStatus(inner),
                detail = innerTag(inner, "p").cleanHtml(),
                status = attr(attrs, "status")?.toIntOrNull() ?: 0,
                termRank = course.termRank
            )
        }
        return result
    }

    fun parseExamList(html: String, course: ChaoxingCourse): List<ChaoxingTask> {
        val tableRows = parseExamListTable(html, course)
        return if (tableRows.isNotEmpty()) tableRows else parseExamListLi(html, course)
    }

    private fun parseExamListTable(html: String, course: ChaoxingCourse): List<ChaoxingTask> {
        val result = mutableListOf<ChaoxingTask>()
        val rowRegex = Regex(
            """<tr\b([^>]*\bdata="[^"]*"[^>]*)>(.*?)</tr>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val cellRegex = Regex(
            """<td\b[^>]*>(.*?)</td>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        rowRegex.findAll(html).forEach { match ->
            val attrs = match.groupValues[1]
            val inner = match.groupValues[2]
            val data = attr(attrs, "data") ?: return@forEach
            if (data.isBlank()) return@forEach
            val cells = cellRegex.findAll(inner).map { it.groupValues[1].cleanHtml() }.toList()
            val title = attr(attrs, "title").orEmpty().cleanHtml().ifBlank {
                cells.firstOrNull().orEmpty().ifBlank { "未命名考试" }
            }
            val statusText = cells.firstOrNull { cell ->
                STATUS_HINTS.any { cell.contains(it) }
            } ?: cells.getOrNull(2).orEmpty()
            val detail = cells.firstOrNull { cell ->
                cell != title && cell != statusText &&
                    (cell.contains("分") || cell.contains("截止") ||
                        cell.contains("时间") || cell.contains("日期") ||
                        cell.contains("时长"))
            } ?: cells.getOrNull(1).orEmpty()
            result += ChaoxingTask(
                type = ChaoxingClient.TYPE_EXAM,
                title = title,
                courseName = course.name,
                courseId = course.courseId,
                classId = course.classId,
                url = absoluteUrl(data),
                statusText = statusText,
                detail = detail,
                status = attr(attrs, "status")?.toIntOrNull() ?: 0,
                termRank = course.termRank
            )
        }
        return result
    }

    /** 兜底：部分页面用 ul.nav > li（用户脚本 work/stu-work 的结构）。 */
    private fun parseWorkListLi(html: String, course: ChaoxingCourse): List<ChaoxingTask> {
        val result = mutableListOf<ChaoxingTask>()
        val rowRegex = Regex(
            """<li\b([^>]*)>(.*?)</li>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        rowRegex.findAll(html).forEach { match ->
            val attrs = match.groupValues[1]
            val inner = match.groupValues[2]
            val data = attr(attrs, "data") ?: return@forEach
            if (data.isBlank()) return@forEach
            val title = innerTag(inner, "p").cleanHtml().ifBlank { "未命名作业" }
            result += ChaoxingTask(
                type = ChaoxingClient.TYPE_WORK,
                title = title,
                courseName = course.name,
                courseId = course.courseId,
                classId = course.classId,
                url = absoluteUrl(data),
                statusText = workStatus(inner),
                detail = innerTag(inner, "dd").cleanHtml(),
                status = attr(attrs, "status")?.toIntOrNull() ?: 0,
                termRank = course.termRank
            )
        }
        return result
    }

    /** 兜底：部分页面用 ul.ks_list > li（考试卡片结构）。 */
    private fun parseExamListLi(html: String, course: ChaoxingCourse): List<ChaoxingTask> {
        val result = mutableListOf<ChaoxingTask>()
        val rowRegex = Regex(
            """<li\b([^>]*)>(.*?)</li>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        rowRegex.findAll(html).forEach { match ->
            val attrs = match.groupValues[1]
            val inner = match.groupValues[2]
            val data = attr(attrs, "data") ?: return@forEach
            if (data.isBlank()) return@forEach
            val title = innerTag(inner, "dt").cleanHtml()
                .ifBlank { innerTag(inner, "p").cleanHtml() }
                .ifBlank { "未命名考试" }
            val spans = Regex(
                """<span\b[^>]*>(.*?)</span>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).findAll(inner).map { it.groupValues[1].cleanHtml() }.filter { it.isNotBlank() }.toList()
            val statusText = spans.firstOrNull { span -> STATUS_HINTS.any { span.contains(it) } }
                ?: innerTag(inner, "b").cleanHtml()
            result += ChaoxingTask(
                type = ChaoxingClient.TYPE_EXAM,
                title = title,
                courseName = course.name,
                courseId = course.courseId,
                classId = course.classId,
                url = absoluteUrl(data),
                statusText = statusText,
                detail = innerTag(inner, "dd").cleanHtml(),
                status = attr(attrs, "status")?.toIntOrNull() ?: 0,
                termRank = course.termRank
            )
        }
        return result
    }

    /**
     * 作业状态在 `<b>` 里（“做作业/查看”），有的页面在带 status 类的
     * `<span>` 里（“待完成/待批阅”）。优先取能识别成状态的那个。
     */
    private fun workStatus(inner: String): String {
        val spans = Regex(
            """<span\b[^>]*>(.*?)</span>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).findAll(inner)
            .map { it.groupValues[1].cleanHtml() }
            .filter { it.isNotBlank() }
            .toList()
        // 带 status 类的 span 才是真正的状态；<b> 往往只是“做作业/查看”按钮。
        spans.firstOrNull { span -> STATUS_HINTS.any { span.contains(it) } }?.let { return it }
        val bold = innerTag(inner, "b").cleanHtml()
        if (bold.isNotBlank() && (STATUS_HINTS.any { bold.contains(it) } || bold == "查看")) {
            return bold
        }
        return bold.ifBlank { spans.firstOrNull().orEmpty() }
    }

    private fun absoluteUrl(path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> CampusEndpoints.CHAOXING_MOOC_API + path
        else -> CampusEndpoints.CHAOXING_MOOC_API + "/" + path
    }

    private fun attr(attrs: String, name: String): String? =
        Regex("$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
            .find(attrs)?.groupValues?.get(1)?.decodeHtml()

    private fun innerTag(html: String, tag: String): String =
        Regex(
            """<$tag\b[^>]*>(.*?)</$tag>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).find(html)?.groupValues?.get(1).orEmpty()

    private fun String.cleanHtml(): String = decodeHtml()
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.decodeHtml(): String = this
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
