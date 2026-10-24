package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.ExamDao
import com.ecut.campuspocket.data.local.ExamEntity
import kotlinx.coroutines.flow.Flow

class ExamRepository(private val examDao: ExamDao) {

    fun observeExams(): Flow<List<ExamEntity>> = examDao.observeAll()

    suspend fun replaceAll(exams: List<ExamEntity>) = examDao.replaceAll(exams)

    suspend fun clear() = examDao.clear()
}
