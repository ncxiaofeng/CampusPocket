package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.CourseDao
import com.ecut.campuspocket.data.local.CourseEntity
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(private val courseDao: CourseDao) {

    fun observeCourses(): Flow<List<CourseEntity>> = courseDao.observeAll()

    /**
     * 教务接口同步成功后替换教务同步课表，同时保留用户自定义课程。
     */
    suspend fun replaceCourses(courses: List<CourseEntity>) {
        courseDao.replaceAll(courses)
    }

    suspend fun upsertCustomCourse(course: CourseEntity): Long =
        courseDao.upsert(course.copy(isCustom = true))

    suspend fun deleteCourse(id: Long) = courseDao.deleteById(id)

    suspend fun clear() = courseDao.clear()
}
