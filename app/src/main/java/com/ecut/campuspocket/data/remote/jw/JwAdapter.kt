package com.ecut.campuspocket.data.remote.jw

import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.SessionExpiredException
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalDate
import kotlin.math.ceil

data class JwCourse(
    val name: String,
    val teacher: String,
    val room: String,
    val campus: String,
    val dayOfWeek: Int,
    val startSlot: Int,
    val slotSpan: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
    val weeksSpec: String,
    val courseCode: String
)

data class JwScheduleResult(
    val xnm: String,
    val xqm: String,
    val courses: List<JwCourse>
)

data class JwGrade(
    val key: String,
    val courseName: String,
    val score: String,
    val credit: Double,
    val gpa: Double,
    val term: String,
    val courseCode: String,
    val nature: String,
    val examType: String,
    val passed: Boolean
)

data class JwCampus(
    val id: String,
    val name: String
)

data class JwClassroomBuilding(
    val id: String,
    val name: String
)

data class JwClassroomFilters(
    val campusId: String,
    val campusName: String,
    val campuses: List<JwCampus>,
    val buildings: List<JwClassroomBuilding>,
    val xnm: String,
    val xqm: String
)

data class JwClassroom(
    val id: String,
    val name: String,
    val building: String,
    val campus: String,
    val type: String,
    val seats: Int,
    val examSeats: Int,
    val borrowable: Boolean
)

data class JwExam(
    val key: String,
    val courseName: String,
    val courseCode: String,
    val examTime: String,
    val examDate: String,
    val startClock: String,
    val endClock: String,
    val examName: String,
    val examType: String,
    val location: String,
    val campus: String,
    val seatNo: String,
    val ticketNo: String,
    val note: String,
    val term: String
)

data class JwNotice(
    val id: String,
    val title: String,
    val publisher: String,
    val publishedAt: String,
    /** 公告正文 HTML，列表接口会一并返回。 */
    val contentHtml: String
)

/**
 * 正方教务（jwglxt）只读适配器。
 *
 * 依赖 WebView 登录后写入加密库的 JSESSIONID；所有接口只做查询。
 */
class JwAdapter(
    private val client: OkHttpClient,
    /** 校内直连 `http://172.20.130.13`，校外走零信任 WebVPN 代理。 */
    private val baseUrlProvider: suspend () -> String
) {

    private suspend fun baseUrl(): String = baseUrlProvider()

    suspend fun fetchSchedule(): JwScheduleResult = withContext(Dispatchers.IO) {
        val page = get(SCHEDULE_PAGE)
        val (xnm, xqm) = parseSelectedTerm(page)
        val body = FormBody.Builder()
            .add("xnm", xnm)
            .add("xqm", xqm)
            .add("kzlx", "ck")
            .add("xsdm", "")
            .add("kclbdm", "")
            .add("kclxdm", "")
            .build()
        val json = postForm(SCHEDULE_API, body, SCHEDULE_PAGE)
        val root = parseObject(json)
        val kbList = root.getAsJsonArray("kbList") ?: JsonArray()
        val courses = kbList.mapNotNull { element ->
            val item = element.asJsonObject
            toCourse(item)
        }
        JwScheduleResult(xnm = xnm, xqm = xqm, courses = courses)
    }

    /**
     * 查询全部学期成绩。传空 xnm/xqm 时教务返回所有已出分课程。
     */
    suspend fun fetchGrades(): List<JwGrade> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("xnm", "")
            .add("xqm", "")
            .add("sfzgcj", "")
            .add("cxbkjgcj", "")
            .add("kcbj", "")
            .add("pkey", "")
            .add("_search", "false")
            .add("nd", System.currentTimeMillis().toString())
            .add("queryModel.showCount", "200")
            .add("queryModel.currentPage", "1")
            .add("queryModel.sortName", " ")
            .add("queryModel.sortOrder", "asc")
            .add("time", "0")
            .build()
        val json = postForm(GRADE_API, body, GRADE_PAGE)
        val root = parseObject(json)
        val items = root.getAsJsonArray("items") ?: JsonArray()
        items.mapNotNull { element ->
            val item = element.asJsonObject
            toGrade(item)
        }
    }

    /**
     * 查询当前学期的考试安排（含考场、座位号）。
     */
    suspend fun fetchExams(): List<JwExam> = withContext(Dispatchers.IO) {
        val page = get(EXAM_PAGE)
        val (xnm, xqm) = parseSelectedTerm(page)
        val body = FormBody.Builder()
            .add("xnm", xnm)
            .add("xqm", xqm)
            .add("ksmcdmb_id", "")
            .add("kch", "")
            .add("kc", "")
            .add("ksrq", "")
            .add("kkbm_id", "")
            .add("_search", "false")
            .add("nd", System.currentTimeMillis().toString())
            .add("queryModel.showCount", "200")
            .add("queryModel.currentPage", "1")
            .add("queryModel.sortName", " ")
            .add("queryModel.sortOrder", "asc")
            .add("time", "0")
            .build()
        val json = postForm(EXAM_API, body, EXAM_PAGE)
        val root = parseObject(json)
        val items = root.getAsJsonArray("items") ?: JsonArray()
        val term = termLabel(xnm, xqm)
        items.mapNotNull { element ->
            toExam(element.asJsonObject, term)
        }
    }

    /**
     * 读取空闲教室页的校区、楼号与当前学年学期。
     */
    suspend fun fetchClassroomFilters(): JwClassroomFilters = withContext(Dispatchers.IO) {
        val html = get(CLASSROOM_PAGE)
        val campuses = JwParser.parseSelectOptions(html, "xqh_id")
        val buildings = JwParser.parseSelectOptions(html, "lh")
        val campusId = JwParser.selectedOptionValue(html, "xqh_id")
            ?: campuses.firstOrNull()?.first
            ?: "05"
        val campusName = campuses.firstOrNull { it.first == campusId }?.second
            ?: campuses.firstOrNull()?.second
            ?: "枫林校园"
        val fallback = fallbackTerm()
        JwClassroomFilters(
            campusId = campusId,
            campusName = campusName,
            campuses = campuses.map { (id, name) -> JwCampus(id, name) },
            buildings = buildings.map { (id, name) -> JwClassroomBuilding(id, name) },
            xnm = JwParser.parseInputValue(html, "xnm") ?: fallback.first,
            xqm = JwParser.parseInputValue(html, "xqm") ?: fallback.second
        )
    }

    /**
     * 切换校区后重新加载该校区楼号。
     */
    suspend fun fetchClassroomBuildings(
        xnm: String,
        xqm: String,
        campusId: String
    ): List<JwClassroomBuilding> = withContext(Dispatchers.IO) {
        val path = "$CLASSROOM_BUILDING_API?xqh_id=$campusId&xnm=$xnm&xqm=$xqm&gnmkdm=N2155"
        val json = get(path)
        val root = parseObject(json)
        val list = root.getAsJsonArray("lhList") ?: JsonArray()
        val buildings = list.mapNotNull { element ->
            val item = element.asJsonObject
            val id = item.string("JXLDM")
            val name = item.string("JXLMC")
            if (id.isBlank() && name.isBlank()) null else JwClassroomBuilding(id, name)
        }
        // 接口只返回具体楼号，补一个“全部”选项，方便清空筛选。
        listOf(JwClassroomBuilding("", "全部")) + buildings.filter { it.id.isNotBlank() }
    }

    /**
     * 查询某一周、星期几、哪些节次空闲的教室。
     *
     * @param week 1..20
     * @param weekday 1..7（周一为 1）
     * @param periodMask 节次位掩码，第 1 节为 1，第 12 节为 2048
     */
    suspend fun queryClassrooms(
        filters: JwClassroomFilters,
        buildingId: String,
        week: Int,
        weekday: Int,
        periodMask: Int
    ): List<JwClassroom> = withContext(Dispatchers.IO) {
        val weekMask = 1 shl (week.coerceIn(1, 20) - 1)
        val result = mutableListOf<JwClassroom>()
        var page = 1
        var total = Int.MAX_VALUE
        while (page <= 20 && result.size < total) {
            val body = FormBody.Builder()
                .add("xqh_id", filters.campusId)
                .add("xnm", filters.xnm)
                .add("xqm", filters.xqm)
                .add("cdlb_id", "")
                .add("cdejlb_id", "")
                .add("qszws", "")
                .add("jszws", "")
                .add("cdmc", "")
                .add("lh", buildingId)
                .add("jyfs", "0")
                .add("cdjylx", "")
                .add("sfbhkc", "")
                .add("zcd", weekMask.toString())
                .add("xqj", weekday.coerceIn(1, 7).toString())
                .add("jcd", periodMask.toString())
                .add("_search", "false")
                .add("nd", System.currentTimeMillis().toString())
                .add("queryModel.showCount", CLASSROOM_PAGE_SIZE.toString())
                .add("queryModel.currentPage", page.toString())
                .add("queryModel.sortName", "cdbh ")
                .add("queryModel.sortOrder", "asc")
                .add("time", "0")
                .build()
            val root = parseObject(postForm(CLASSROOM_API, body, CLASSROOM_PAGE))
            val items = root.getAsJsonArray("items") ?: break
            val pageItems = items.mapNotNull { element -> toClassroom(element.asJsonObject) }
            result += pageItems
            root.get("totalResult")?.takeIf { !it.isJsonNull }?.asString?.toIntOrNull()?.let {
                total = it
            }
            if (items.size() < CLASSROOM_PAGE_SIZE || pageItems.isEmpty()) break
            page++
        }
        result
    }

    /**
     * 查询学生通知公告。
     *
     * 正方移动端接口用 `njdm` / `bjdm` 表示分页区间 [起始, 结束)，
     * 列表项已带完整正文 `fbnr`，点击后无需再请求详情。
     *
     * @param keyword 标题关键词，空串表示全部
     * @param page 从 0 开始
     */
    suspend fun fetchNotices(
        keyword: String = "",
        page: Int = 0,
        pageSize: Int = NOTICE_PAGE_SIZE
    ): List<JwNotice> = withContext(Dispatchers.IO) {
        val start = page.coerceAtLeast(0) * pageSize
        val body = FormBody.Builder()
            .add("njdm", start.toString())
            .add("bjdm", (start + pageSize).toString())
            .add("doType", "app")
            .add("xwbt", keyword)
            .build()
        val json = postFormText(NOTICE_LIST_API, body, NOTICE_LIST_API)
        val first = json.trimStart().firstOrNull()
        if (first != '[' && first != '{') {
            throw SessionExpiredException("教务会话已失效")
        }
        val array = parseArray(json)
        array.mapNotNull { element -> toNotice(element.asJsonObject) }
    }

    /**
     * 读取单条公告详情。列表接口通常已返回正文，仅在正文缺失时兜底。
     */
    suspend fun fetchNoticeDetail(xwbh: String): JwNotice? = withContext(Dispatchers.IO) {
        if (xwbh.isBlank()) return@withContext null
        val id = URLEncoder.encode(xwbh, "UTF-8")
        val json = postForm(
            "$NOTICE_DETAIL_API?doType=app&xwbh=$id",
            FormBody.Builder().build(),
            NOTICE_LIST_API
        )
        toNotice(parseObject(json))
    }

    private suspend fun get(path: String): String {
        val base = baseUrl()
        val request = Request.Builder()
            .url(base + path)
            .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", base + "/jwglxt/xtgl/index_initMenu.html?jsdm=xs")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("教务系统返回 ${response.code}")
            }
            val responseBody = response.body?.string().orEmpty()
            if (looksLikeLogin(responseBody)) throw SessionExpiredException()
            return responseBody
        }
    }

    private suspend fun postForm(path: String, body: FormBody, refererPath: String): String {
        val text = postFormText(path, body, refererPath)
        if (!text.trimStart().startsWith("{")) {
            throw SessionExpiredException("教务会话已失效")
        }
        return text
    }

    private suspend fun postFormText(
        path: String,
        body: FormBody,
        refererPath: String
    ): String {
        val base = baseUrl()
        val request = Request.Builder()
            .url(base + path)
            .post(body)
            .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .header("Origin", base)
            .header("Referer", base + refererPath)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("教务系统返回 ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) throw SessionExpiredException()
            // 公告正文里可能出现“统一身份认证”等字样，JSON 响应只看结构，不按正文关键词误判。
            val trimmed = text.trimStart()
            val jsonLike = trimmed.startsWith("{") || trimmed.startsWith("[")
            if (!jsonLike && looksLikeLogin(text)) throw SessionExpiredException()
            return text
        }
    }

    private fun parseObject(json: String): JsonObject {
        return try {
            JsonParser.parseString(json).asJsonObject
        } catch (exception: Exception) {
            throw IOException("教务返回数据无法解析", exception)
        }
    }

    private fun parseArray(json: String): JsonArray {
        return try {
            JsonParser.parseString(json).asJsonArray
        } catch (exception: Exception) {
            throw IOException("教务返回数据无法解析", exception)
        }
    }

    private fun looksLikeLogin(text: String): Boolean {
        if (text.isBlank()) return true
        return text.contains("authserver/login") ||
            text.contains("login_slogin") ||
            text.contains("统一身份认证")
    }

    private fun parseSelectedTerm(html: String): Pair<String, String> {
        val fallback = fallbackTerm()
        // 课表页用 id=xnm/xqm，考试页用 id=cx_xnm/cx_xqm。
        val xnm = JwParser.selectedOptionValue(html, "xnm")
            ?: JwParser.selectedOptionValue(html, "cx_xnm")
            ?: fallback.first
        val xqm = JwParser.selectedOptionValue(html, "xqm")
            ?: JwParser.selectedOptionValue(html, "cx_xqm")
            ?: fallback.second
        return xnm to xqm
    }

    private fun fallbackTerm(): Pair<String, String> {
        val today = LocalDate.now()
        return if (today.monthValue >= 8) {
            today.year.toString() to "3"
        } else {
            (today.year - 1).toString() to "12"
        }
    }

    private fun toCourse(item: JsonObject): JwCourse? {
        val name = item.string("kcmc")
        if (name.isBlank()) return null
        val day = item.string("xqj").toIntOrNull() ?: return null
        if (day !in 1..7) return null
        val periodText = item.string("jc")
        val (startPeriod, endPeriod) = JwParser.parsePeriods(periodText) ?: return null
        val slotStart = (startPeriod - 1) / 2
        val slotSpan = ceil((endPeriod - startPeriod + 1) / 2.0).toInt().coerceAtLeast(1)
        val weeksSpec = item.string("zcd")
        val room = item.string("cdmc").ifBlank {
            item.string("lh") + item.string("cdbh")
        }
        val campus = item.string("xqmc").ifBlank { item.string("cdxqmc") }
        return JwCourse(
            name = name,
            teacher = item.string("xm"),
            room = room,
            campus = campus,
            dayOfWeek = day - 1,
            startSlot = slotStart.coerceIn(0, 5),
            slotSpan = slotSpan.coerceAtMost(6 - slotStart),
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weeks = JwParser.parseWeeks(weeksSpec),
            weeksSpec = weeksSpec,
            courseCode = item.string("kch")
        )
    }

    private fun toGrade(item: JsonObject): JwGrade? {
        val score = item.string("bfzcj").ifBlank { item.string("cj") }
        if (score.isBlank()) return null
        val courseName = item.string("kcmc")
        if (courseName.isBlank()) return null
        val termLabel = buildString {
            append(item.string("xnmmc"))
            val term = item.string("xqmmc")
            if (term.isNotBlank()) append(" · 第").append(term).append("学期")
        }.trim()
        val key = item.string("row_id").ifBlank {
            listOf(
                item.string("kch"),
                item.string("xnmmc"),
                item.string("xqmmc"),
                item.string("jxbmc")
            ).joinToString("_")
        }
        val numericScore = score.toDoubleOrNull()
        return JwGrade(
            key = key,
            courseName = courseName,
            score = score,
            credit = item.string("xf").toDoubleOrNull() ?: 0.0,
            gpa = item.string("jd").toDoubleOrNull() ?: 0.0,
            term = termLabel,
            courseCode = item.string("kch"),
            nature = item.string("kcxzmc").ifBlank { item.string("kcbj") },
            examType = item.string("khfsmc").ifBlank { item.string("ksxz") },
            passed = numericScore?.let { it >= 60 } ?: true
        )
    }

    private fun toClassroom(item: JsonObject): JwClassroom? {
        val name = item.string("cdmc").ifBlank { item.string("cdbh") }
        if (name.isBlank()) return null
        return JwClassroom(
            id = item.string("cd_id").ifBlank { item.string("cdxqxx_id") },
            name = name,
            building = item.string("jxlmc"),
            campus = item.string("xqmc"),
            type = item.string("cdlbmc"),
            seats = item.string("zws").toIntOrNull() ?: 0,
            examSeats = item.string("kszws1").toIntOrNull() ?: 0,
            borrowable = item.string("sfkjy") == "是"
        )
    }

    private fun toExam(item: JsonObject, term: String): JwExam? {
        val courseName = item.string("kcmc").ifBlank { item.string("kc") }
        val examTime = item.string("kssj")
        if (courseName.isBlank() && examTime.isBlank()) return null
        val (date, startClock, endClock) = JwParser.parseExamTime(examTime)
        val examName = item.string("ksmc")
        val courseCode = item.string("kch")
        val key = item.string("sjbh").ifBlank {
            listOf(courseCode, examTime, examName).joinToString("_")
        }
        val itemTerm = buildString {
            append(item.string("xnmc"))
            val termName = item.string("xqmmc")
            if (termName.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append("第").append(termName).append("学期")
            }
        }.ifBlank { term }
        return JwExam(
            key = key,
            courseName = courseName,
            courseCode = courseCode,
            examTime = examTime,
            examDate = date,
            startClock = startClock,
            endClock = endClock,
            examName = examName,
            examType = item.string("ksfs").ifBlank {
                item.string("ksxz")
            },
            location = item.string("cdmc").ifBlank {
                item.string("cd")
            },
            campus = item.string("cdxqmc").ifBlank {
                item.string("xqmc")
            },
            seatNo = item.string("zwh"),
            ticketNo = item.string("zkzh"),
            note = item.string("ksbz"),
            term = itemTerm
        )
    }

    private fun toNotice(item: JsonObject): JwNotice? {
        val id = item.string("xwbh")
        val title = item.string("xwbt")
        if (id.isBlank() && title.isBlank()) return null
        return JwNotice(
            id = id,
            title = title.ifBlank { "通知公告" },
            publisher = item.string("fbr").ifBlank { item.string("fbrxm") },
            publishedAt = item.string("fbsj"),
            contentHtml = item.string("fbnr")
        )
    }

    private fun termLabel(xnm: String, xqm: String): String {
        val startYear = xnm.toIntOrNull()
        val yearLabel = if (startYear != null) "$startYear-${startYear + 1}" else xnm
        val termLabel = when (xqm) {
            "3" -> "第一学期"
            "12" -> "第二学期"
            "16" -> "小学期"
            else -> ""
        }
        return listOf(yearLabel, termLabel)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else value.asString
    }

    companion object {
        private const val SCHEDULE_PAGE =
            "/jwglxt/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N253508&layout=default"
        private const val SCHEDULE_API = "/jwglxt/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508"
        private const val GRADE_PAGE =
            "/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"
        private const val GRADE_API =
            "/jwglxt/cjcx/cjcx_cxXsgrcj.html?doType=query&gnmkdm=N305005"
        private const val EXAM_PAGE =
            "/jwglxt/kwgl/kscx_cxXsksxxIndex.html?gnmkdm=N358105&layout=default"
        private const val EXAM_API =
            "/jwglxt/kwgl/kscx_cxXsksxxIndex.html?doType=query&gnmkdm=N358105"
        private const val CLASSROOM_PAGE =
            "/jwglxt/cdjy/cdjy_cxKxcdlb.html?gnmkdm=N2155&layout=default"
        private const val CLASSROOM_API =
            "/jwglxt/cdjy/cdjy_cxKxcdlb.html?doType=query&gnmkdm=N2155"
        private const val CLASSROOM_BUILDING_API = "/jwglxt/cdjy/cdjy_cxXqjc.html"
        private const val CLASSROOM_PAGE_SIZE = 300
        private const val NOTICE_LIST_API = "/jwglxt/xtgl/xwglMobile_cxMoreXwList.html"
        private const val NOTICE_DETAIL_API = "/jwglxt/xtgl/xwglMobile_cxXwDetail.html"
        private const val NOTICE_PAGE_SIZE = 10
    }
}
