package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.CourseDao
import com.ecut.campuspocket.data.local.CourseEntity
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(private val courseDao: CourseDao) {

    fun observeCourses(): Flow<List<CourseEntity>> = courseDao.observeAll()

    /**
     * 教务接口同步成功后整体替换本地课表，避免残留旧课程。
     */
    suspend fun replaceCourses(courses: List<CourseEntity>) {
        courseDao.replaceAll(courses)
    }

    suspend fun clear() = courseDao.clear()
}
