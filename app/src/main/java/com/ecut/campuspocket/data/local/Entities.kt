package com.ecut.campuspocket.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 认证凭证：整个 Cookie 字符串加密落库。
 */
@Entity(tableName = "auth_credentials", indices = [Index("domain")])
data class AuthCredentialEntity(
    @PrimaryKey val systemId: String,
    val domain: String,
    val cookie: String,
    val updatedAt: Long
)

/**
 * 按域名拆分的 Cookie，供 OkHttp CookieJar 精确注入。
 */
@Entity(
    tableName = "cookies",
    primaryKeys = ["domain", "name", "path"],
    indices = [Index("domain")]
)
data class CookieEntity(
    val domain: String,
    val name: String,
    val value: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val updatedAt: Long
)

/**
 * 每小时采样一次的电费记录，形成本地历史曲线。
 */
@Entity(tableName = "electricity_records")
data class ElectricityRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val lightingKwh: Double,
    val acKwh: Double,
    val balance: Double?,
    val recordedAt: Long
)

/**
 * 课表条目（周次为闭区间）。
 */
@Entity(tableName = "course_blocks")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int,
    val startSlot: Int,
    val slotSpan: Int,
    val title: String,
    val room: String,
    val teacher: String,
    /** 精确节次（1 起），用于 WakeUp 式逐节课表；0 表示旧数据，回退到 startSlot/slotSpan。 */
    val startPeriod: Int = 0,
    val endPeriod: Int = 0,
    /** 校区，例如 广兰校园。 */
    val campus: String = "",
    val startWeek: Int,
    val endWeek: Int,
    val colorSeed: Int,
    /** 原始周次描述，例如 4-9周 / 1-16周(单)。 */
    val weeksSpec: String = "",
    /** 展开后的周次，逗号分隔，例如 4,5,6,7,8,9。 */
    val weeksCsv: String = "",
    val courseCode: String = ""
)

/**
 * 考试安排缓存。examDate 统一为 yyyy-MM-dd，便于排序与“即将考试”判断。
 */
@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val key: String,
    val courseName: String,
    val courseCode: String,
    /** 原始考试时间文本，例如 2026-01-10(09:00-11:00)。 */
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
    val term: String,
    val updatedAt: Long
)

/**
 * 成绩缓存（仅课程成绩，不保存学生姓名 / 学号）。
 */
@Entity(tableName = "grades")
data class GradeEntity(
    @PrimaryKey val key: String,
    val courseName: String,
    val score: String,
    val credit: Double,
    val gpa: Double,
    val term: String,
    val courseCode: String,
    val nature: String,
    val examType: String,
    val passed: Boolean,
    val updatedAt: Long
)
