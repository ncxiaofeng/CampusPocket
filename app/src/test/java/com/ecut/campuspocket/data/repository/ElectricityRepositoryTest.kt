package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.ElectricityDao
import com.ecut.campuspocket.data.local.ElectricityRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElectricityRepositoryTest {

    @Test
    fun `drops legacy zero rows and keeps real usage`() = runBlocking {
        val dao = FakeElectricityDao(
            listOf(
                // 旧版本把接口拿不到的日期写成了 0 度。
                record(day = 100, kwh = 0.0, balance = null, at = 1),
                record(day = 101, kwh = 4.6, balance = null, at = 2),
                record(day = 101, kwh = 0.0, balance = 61.55, at = 3),
                record(day = 102, kwh = 3.5, balance = null, at = 4)
            )
        )
        val repository = ElectricityRepository(dao)

        val days = repository.observeDays().first()

        assertEquals(listOf(101L, 102L), days.map { it.epochDay })
        assertEquals(4.6, days[0].totalKwh, 0.001)
        assertEquals(61.55, days[0].balance!!, 0.001)
        assertNull(days[1].balance)
    }

    @Test
    fun `keeps a balance only day so today can show the meter balance`() = runBlocking {
        val dao = FakeElectricityDao(
            listOf(record(day = 200, kwh = 0.0, balance = 20.0, at = 10))
        )
        val repository = ElectricityRepository(dao)

        val days = repository.observeDays().first()

        assertEquals(1, days.size)
        assertEquals(20.0, days[0].balance!!, 0.001)
        assertEquals(0.0, days[0].totalKwh, 0.001)
    }

    private fun record(
        day: Long,
        kwh: Double,
        balance: Double?,
        at: Long
    ) = ElectricityRecordEntity(
        epochDay = day,
        lightingKwh = kwh,
        acKwh = 0.0,
        balance = balance,
        recordedAt = at
    )

    private class FakeElectricityDao(
        private val records: List<ElectricityRecordEntity>
    ) : ElectricityDao {
        override suspend fun insert(record: ElectricityRecordEntity): Long = 0

        override suspend fun latest(): ElectricityRecordEntity? = records.maxByOrNull { it.recordedAt }

        override suspend fun since(sinceMillis: Long): List<ElectricityRecordEntity> =
            records.filter { it.recordedAt >= sinceMillis }

        override suspend fun latestBalance(): Double? =
            records.filter { it.balance != null }.maxByOrNull { it.recordedAt }?.balance

        override fun observeRecent(): Flow<List<ElectricityRecordEntity>> = flowOf(records)

        override suspend fun deleteForDay(epochDay: Long) = Unit

        override suspend fun clear() = Unit
    }
}
