package com.ecut.campuspocket.data.remote.chaoxing

import com.ecut.campuspocket.data.AppPreferences
import com.ecut.campuspocket.data.repository.AuthRepository
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.ChaoxingSessionExpiredException
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 超星学习通只读接口。
 *
 * - 课程/作业/考试：网页版 HTML 页面（cookie 鉴权），字段结构来自 7.0.2 客户端。
 * - 签到活动：mobilelearn 的 activeList，使用官方格式 UA。
 *
 * 本类不提交签到、不交作业，只负责把「有什么」读出来。
 */
class ChaoxingClient(
    client: OkHttpClient,
    private val authRepository: AuthRepository,
    private val preferences: AppPreferences
) {

    private val http = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val browserUa = CampusEndpoints.MOBILE_BROWSER_USER_AGENT

    private val officialUa: String
        get() = ChaoxingUserAgent.build(preferences.chaoxingUniqueId)

    suspend fun hasSession(): Boolean = withContext(Dispatchers.IO) {
        uidOrNull() != null
    }

    suspend fun fetchSnapshot(): ChaoxingSnapshot = withContext(Dispatchers.IO) {
        val courses = listCourses()
        val semaphore = Semaphore(4)
        val warnings = mutableListOf<String>()
        val pairs = coroutineScope {
            courses.map { course ->
                async {
                    semaphore.withPermit {
                        runCatching { fetchCourseTasks(course) }
                            .onFailure {
                                warnings += "${course.name}：${it.message ?: "读取失败"}"
                            }
                            .getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }
        val signs = runCatching { listActiveSigns(courses) }
            .onFailure { warnings += "签到活动：${it.message ?: "读取失败"}" }
            .getOrDefault(emptyList())
        ChaoxingSnapshot(
            courses = courses,
            works = pairs.filter { it.type == TYPE_WORK },
            exams = pairs.filter { it.type == TYPE_EXAM },
            signs = signs.filter { it.isOngoing || it.status == 0 },
            fetchedAt = System.currentTimeMillis(),
            warnings = warnings
        )
    }

    /** 只查签到活动，供 15 分钟后台任务使用。 */
    suspend fun fetchSigns(): List<ChaoxingSign> = withContext(Dispatchers.IO) {
        listActiveSigns(emptyList())
    }

    suspend fun listCourses(): List<ChaoxingCourse> = withContext(Dispatchers.IO) {
        val url = "${CampusEndpoints.CHAOXING_MOOC_API}/mycourse/backclazzdata" +
            "?view=json&getTchClazzType=1&mcode="
        val root = parseObject(get(url, browserUa))
        if (root.int("result") != 1) {
            throw ChaoxingSessionExpiredException(
                root.string("msg").ifBlank { "学习通课程列表读取失败" }
            )
        }
        val channels = root.get("channelList")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray()
        val courses = mutableListOf<ChaoxingCourse>()
        channels.forEach { element ->
            val channel = element.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@forEach
            val content = parseContent(channel.get("content")) ?: return@forEach
            val courseData = content.objectOrNull("course")
                ?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
            val classId = content.string("id").ifBlank { channel.string("key") }
            if (courseData != null && classId.isNotBlank()) {
                courseData.forEach { courseElement ->
                    val course = courseElement.takeIf { it.isJsonObject }?.asJsonObject
                        ?: return@forEach
                    val courseId = course.string("id")
                    if (courseId.isBlank()) return@forEach
                    val termText = content.toString() + course.toString()
                    courses += ChaoxingCourse(
                        courseId = courseId,
                        classId = classId,
                        name = course.string("name").ifBlank { "未命名课程" },
                        className = content.string("name"),
                        teacher = course.string("teacherfactor"),
                        termRank = ChaoxingTerm.rank(termText)
                    )
                }
                return@forEach
            }
            // 旧版 course-centric 结构：content 是课程，clazz[] 是班。
            val courseId = content.string("id")
            val clazzList = content.get("clazz")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return@forEach
            if (courseId.isBlank()) return@forEach
            clazzList.forEach { clazzElement ->
                val clazz = clazzElement.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@forEach
                val legacyClassId = clazz.string("id")
                if (legacyClassId.isBlank()) return@forEach
                courses += ChaoxingCourse(
                    courseId = courseId,
                    classId = legacyClassId,
                    name = content.string("name").ifBlank { "未命名课程" },
                    className = clazz.string("name"),
                    teacher = content.string("teacherfactor"),
                    termRank = ChaoxingTerm.rank(content.toString() + clazz.toString())
                )
            }
        }
        courses.distinctBy { it.courseId to it.classId }
    }

    private suspend fun fetchCourseTasks(course: ChaoxingCourse): List<ChaoxingTask> {
        val work = runCatching { listWorks(course) }.getOrDefault(emptyList())
        val exam = runCatching { listExams(course) }.getOrDefault(emptyList())
        return work + exam
    }

    suspend fun listWorks(course: ChaoxingCourse): List<ChaoxingTask> =
        withContext(Dispatchers.IO) {
            val url = "${CampusEndpoints.CHAOXING_MOOC_API}/phone/moocAnalysis/workList" +
                "?courseId=${encode(course.courseId)}&classId=${encode(course.classId)}"
            ChaoxingHtmlParser.parseWorkList(get(url, browserUa), course)
        }

    suspend fun listExams(course: ChaoxingCourse): List<ChaoxingTask> =
        withContext(Dispatchers.IO) {
            val url = "${CampusEndpoints.CHAOXING_MOOC_API}/phone/moocAnalysis/examList" +
                "?courseId=${encode(course.courseId)}&classId=${encode(course.classId)}"
            ChaoxingHtmlParser.parseExamList(get(url, browserUa), course)
        }

    private suspend fun listActiveSigns(courses: List<ChaoxingCourse>): List<ChaoxingSign> {
        val uid = uidOrNull() ?: throw ChaoxingSessionExpiredException("学习通登录已过期")
        val warnings = mutableListOf<String>()
        val all = runCatching {
            val url = "${CampusEndpoints.CHAOXING_MOBILE_LEARN}/ppt/activeAPI/courseactivelist" +
                "?puid=${encode(uid)}&status=1&page=1&pageSize=20"
            parseActiveList(parseObject(get(url, officialUa)), courses)
        }.onFailure { warnings += it.message.orEmpty() }.getOrDefault(emptyList())
        if (all.any { it.isOngoing }) return all

        // 全课程接口偶尔只返回分页或空数组，退回到逐课程任务列表。
        val fallbackCourses = courses.ifEmpty { listCourses() }
        val perCourse = coroutineScope {
            fallbackCourses.map { course ->
                async {
                    runCatching {
                        val url = "${CampusEndpoints.CHAOXING_MOBILE_LEARN}/ppt/activeAPI/taskactivelist" +
                            "?courseId=${encode(course.courseId)}" +
                            "&classId=${encode(course.classId)}" +
                            "&uid=${encode(uid)}"
                        parseActiveList(
                            parseObject(get(url, officialUa)),
                            listOf(course)
                        )
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
        return (all + perCourse).distinctBy { it.key }
    }

    private fun parseActiveList(root: JsonObject, courses: List<ChaoxingCourse>): List<ChaoxingSign> {
        val dataElement = root.get("data")
        val array = root.get("activeList")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.objectOrNull("data")
                ?.get("activeList")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
            ?: dataElement?.takeIf { it.isJsonArray }?.asJsonArray
            ?: JsonArray()
        val byId = courses.associateBy { it.courseId }
        val result = mutableListOf<ChaoxingSign>()
        array.forEach { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@forEach
            val activeId = item.string("id").ifBlank { item.string("activeId") }
            if (activeId.isBlank()) return@forEach
            val courseId = item.string("courseId")
            val classId = item.string("classId")
            val course = byId[courseId]
            val activeType = item.int("activeType").takeIf { it != 0 } ?: item.int("type")
            val title = item.string("nameOne")
                .ifBlank { item.string("name") }
                .ifBlank { item.string("title") }
                .ifBlank { "课堂活动" }
            val courseName = course?.name
                ?: item.string("nameTwo").ifBlank {
                    item.string("nameFour").ifBlank { "学习通课程" }
                }
            val status = item.int("status")
            val startTime = normalizeTime(item.long("startTime"))
            val url = item.string("url")
            result += ChaoxingSign(
                activeId = activeId,
                title = title,
                courseName = courseName,
                courseId = courseId.ifBlank { course?.courseId.orEmpty() },
                classId = classId.ifBlank { course?.classId.orEmpty() },
                startTime = startTime,
                status = status,
                activeType = activeType,
                url = url.ifBlank {
                    "${CampusEndpoints.CHAOXING_MOBILE_LEARN}/newsign/studentSign?activeId=$activeId"
                },
                termRank = course?.termRank ?: ChaoxingTerm.UNKNOWN
            )
        }
        // activeType=2 是签到；部分旧课程返回 0，用标题里的“签到/手势”兜底。
        // 不再把缺失 activeType 的所有活动都当成签到，避免误报。
        return result.filter {
            it.activeType == 2 ||
                it.title.contains("签到") ||
                it.title.contains("手势")
        }
    }

    private fun get(url: String, userAgent: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json,text/html,application/xhtml+xml,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code in 300..399) {
                throw ChaoxingSessionExpiredException("学习通登录已过期，请重新登录")
            }
            if (!response.isSuccessful) {
                throw IOException("学习通返回 HTTP ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            if (text.contains("请重新登录") ||
                text.contains("passport2.chaoxing.com/login") ||
                text.contains("请用学习通客户端打开")
            ) {
                throw ChaoxingSessionExpiredException("学习通登录已过期，请重新登录")
            }
            return text
        }
    }

    private suspend fun uidOrNull(): String? {
        val hosts = listOf(
            CampusEndpoints.CHAOXING_HOST,
            "passport2.chaoxing.com",
            "passport2-api.chaoxing.com",
            "mooc1-api.chaoxing.com",
            "mobilelearn.chaoxing.com"
        )
        val values = HashMap<String, String>()
        hosts.forEach { host ->
            authRepository.loadCookiesForHost(host).forEach { row ->
                values[row.name.lowercase()] = row.value
            }
        }
        return values["_uid"]?.takeIf { it.isNotBlank() }
            ?: values["uid"]?.takeIf { it.isNotBlank() }
    }

    private fun parseContent(element: JsonElement?): JsonObject? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonObject) return element.asJsonObject
        if (element.isJsonPrimitive) {
            return runCatching { JsonParser.parseString(element.asString).asJsonObject }.getOrNull()
        }
        return null
    }

    private fun parseObject(text: String): JsonObject =
        runCatching { JsonParser.parseString(text).asJsonObject }.getOrElse {
            throw IOException("学习通返回数据无法解析")
        }

    private fun normalizeTime(raw: Long): Long = when {
        raw <= 0L -> 0L
        raw < 10_000_000_000L -> raw * 1000L
        else -> raw
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else value.asString
    }

    private fun JsonObject.int(key: String): Int {
        val value = get(key) ?: return 0
        if (value.isJsonNull) return 0
        return runCatching { value.asInt }.getOrDefault(0)
    }

    private fun JsonObject.long(key: String): Long {
        val value = get(key) ?: return 0L
        if (value.isJsonNull) return 0L
        return runCatching { value.asLong }.getOrDefault(0L)
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? {
        val value = get(key) ?: return null
        if (value.isJsonNull || !value.isJsonObject) return null
        return value.asJsonObject
    }

    companion object {
        const val TYPE_WORK = "work"
        const val TYPE_EXAM = "exam"
    }
}

/**
 * 学习通官方 UA 的 Kotlin 版本（7.0.2 协议）。
 * mobilelearn 会拒绝普通浏览器 UA，courseactivelist 也可能受影响。
 */
object ChaoxingUserAgent {
    private const val SALT = "ipL\$TkeiEmfy1gTXb2XHrdLN0a@7c^vu"

    fun build(uniqueId: String): String {
        val httpAgent =
            "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 6 Build/UQ1A.240205.004)"
        val suffix = " (device:Pixel 6) Language/zh_CN com.chaoxing.mobile/ChaoXingStudy" +
            "_3_7.0.2_android_phone_10993_264 (@Kalimdor)_$uniqueId"
        return "$httpAgent (schild:${md5(suffix + SALT)})$suffix"
    }

    private fun md5(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
