package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.data.local.ExamEntity
import com.ecut.campuspocket.data.local.GradeEntity
import com.ecut.campuspocket.data.remote.jw.JwAdapter
import com.ecut.campuspocket.data.remote.jw.JwCourse
import com.ecut.campuspocket.data.remote.jw.JwExam
import com.ecut.campuspocket.data.remote.jw.JwGrade

/**
 * 教务数据同步：网络结果整体替换本地缓存，读取时始终先走 Room。
 */
class JwSyncManager(
    private val jwAdapter: JwAdapter,
    private val scheduleRepository: ScheduleRepository,
    private val gradeRepository: GradeRepository,
    private val examRepository: ExamRepository
) {

    suspend fun syncSchedule(): Result<Int> = runCatching {
        val result = jwAdapter.fetchSchedule()
        val entities = result.courses.map { it.toEntity() }
        scheduleRepository.replaceCourses(entities)
        entities.size
    }

    suspend fun syncGrades(): Result<Int> = runCatching {
        val grades = jwAdapter.fetchGrades()
        val now = System.currentTimeMillis()
        val entities = grades.map { it.toEntity(now) }
        gradeRepository.replaceAll(entities)
        entities.size
    }

    suspend fun syncExams(): Result<Int> = runCatching {
        val exams = jwAdapter.fetchExams()
        val now = System.currentTimeMillis()
        val entities = exams.map { it.toEntity(now) }
        examRepository.replaceAll(entities)
        entities.size
    }

    private fun JwCourse.toEntity(): CourseEntity {
        val sortedWeeks = weeks.sorted()
        return CourseEntity(
            dayOfWeek = dayOfWeek,
            startSlot = startSlot,
            slotSpan = slotSpan,
            title = name,
            room = room,
            teacher = teacher,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            campus = campus,
            startWeek = sortedWeeks.firstOrNull() ?: 1,
            endWeek = sortedWeeks.lastOrNull() ?: 20,
            colorSeed = (name.hashCode() and 0x7fffffff),
            weeksSpec = weeksSpec,
            weeksCsv = sortedWeeks.joinToString(","),
            courseCode = courseCode
        )
    }

    private fun JwExam.toEntity(now: Long): ExamEntity {
        return ExamEntity(
            key = key,
            courseName = courseName,
            courseCode = courseCode,
            examTime = examTime,
            examDate = examDate,
            startClock = startClock,
            endClock = endClock,
            examName = examName,
            examType = examType,
            location = location,
            campus = campus,
            seatNo = seatNo,
            ticketNo = ticketNo,
            note = note,
            term = term,
            updatedAt = now
        )
    }

    private fun JwGrade.toEntity(now: Long): GradeEntity {
        return GradeEntity(
            key = key,
            courseName = courseName,
            score = score,
            credit = credit,
            gpa = gpa,
            term = term,
            courseCode = courseCode,
            nature = nature,
            examType = examType,
            passed = passed,
            updatedAt = now
        )
    }
}
