package com.ecut.campuspocket.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthDao {
    @Query("SELECT * FROM auth_credentials WHERE systemId = :systemId LIMIT 1")
    suspend fun get(systemId: String): AuthCredentialEntity?

    @Query("SELECT * FROM auth_credentials WHERE systemId = :systemId LIMIT 1")
    fun observe(systemId: String): Flow<AuthCredentialEntity?>

    @Query("SELECT * FROM auth_credentials WHERE :host = domain OR :host LIKE '%' || domain")
    suspend fun getForHost(host: String): List<AuthCredentialEntity>

    @Upsert
    suspend fun upsert(credential: AuthCredentialEntity)

    @Query("DELETE FROM auth_credentials WHERE systemId = :systemId")
    suspend fun delete(systemId: String)

    @Query("DELETE FROM auth_credentials")
    suspend fun clear()
}

@Dao
interface CookieDao {
    @Query("SELECT * FROM cookies WHERE :host = domain OR :host LIKE '%' || domain")
    suspend fun getForHost(host: String): List<CookieEntity>

    @Upsert
    suspend fun upsertAll(cookies: List<CookieEntity>)

    @Query("DELETE FROM cookies WHERE domain = :domain")
    suspend fun deleteForDomain(domain: String)

    @Query("DELETE FROM cookies")
    suspend fun clear()
}

@Dao
interface ElectricityDao {
    @Insert
    suspend fun insert(record: ElectricityRecordEntity): Long

    @Query("SELECT * FROM electricity_records ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latest(): ElectricityRecordEntity?

    @Query("SELECT * FROM electricity_records WHERE recordedAt >= :sinceMillis ORDER BY recordedAt ASC")
    suspend fun since(sinceMillis: Long): List<ElectricityRecordEntity>

    @Query("SELECT * FROM electricity_records ORDER BY recordedAt DESC LIMIT 1000")
    fun observeRecent(): Flow<List<ElectricityRecordEntity>>

    @Query("DELETE FROM electricity_records")
    suspend fun clear()
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM course_blocks ORDER BY dayOfWeek ASC, startSlot ASC")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course_blocks ORDER BY dayOfWeek ASC, startSlot ASC")
    suspend fun getAll(): List<CourseEntity>

    @Insert
    suspend fun insertAll(courses: List<CourseEntity>)

    @Query("DELETE FROM course_blocks")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(courses: List<CourseEntity>) {
        clear()
        insertAll(courses)
    }
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY examDate ASC, startClock ASC, courseName ASC")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams ORDER BY examDate ASC, startClock ASC, courseName ASC")
    suspend fun getAll(): List<ExamEntity>

    @Insert
    suspend fun insertAll(exams: List<ExamEntity>)

    @Query("DELETE FROM exams")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(exams: List<ExamEntity>) {
        clear()
        insertAll(exams)
    }
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades ORDER BY term DESC, courseName ASC")
    fun observeAll(): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades ORDER BY term DESC, courseName ASC")
    suspend fun getAll(): List<GradeEntity>

    @Insert
    suspend fun insertAll(grades: List<GradeEntity>)

    @Query("DELETE FROM grades")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(grades: List<GradeEntity>) {
        clear()
        insertAll(grades)
    }
}
