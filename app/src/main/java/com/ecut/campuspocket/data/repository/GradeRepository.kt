package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.GradeDao
import com.ecut.campuspocket.data.local.GradeEntity
import kotlinx.coroutines.flow.Flow

class GradeRepository(private val gradeDao: GradeDao) {

    fun observeGrades(): Flow<List<GradeEntity>> = gradeDao.observeAll()

    suspend fun replaceAll(grades: List<GradeEntity>) = gradeDao.replaceAll(grades)

    suspend fun clear() = gradeDao.clear()
}
