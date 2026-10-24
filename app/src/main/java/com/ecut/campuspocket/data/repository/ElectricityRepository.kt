package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.local.ElectricityDao
import com.ecut.campuspocket.data.local.ElectricityRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ElectricityDay(
    val epochDay: Long,
    val lightingKwh: Double,
    val acKwh: Double,
    val balance: Double?,
    val updatedAt: Long
) {
    val totalKwh: Double get() = lightingKwh + acKwh
}

class ElectricityRepository(private val dao: ElectricityDao) {

    /**
     * 按天聚合采样记录：同一天取最大读数（电控接口通常返回“当日累计用量”）。
     */
    fun observeDays(): Flow<List<ElectricityDay>> =
        dao.observeRecent().map { records -> aggregate(records) }

    suspend fun addSample(lightingKwh: Double, acKwh: Double, balance: Double?) {
        addDaySample(
            epochDay = java.time.LocalDate.now().toEpochDay(),
            totalKwh = lightingKwh + acKwh,
            balance = balance
        )
    }

    suspend fun addDaySample(epochDay: Long, totalKwh: Double, balance: Double?) {
        dao.insert(
            ElectricityRecordEntity(
                epochDay = epochDay,
                lightingKwh = totalKwh,
                acKwh = 0.0,
                balance = balance,
                recordedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear() = dao.clear()

    private fun aggregate(records: List<ElectricityRecordEntity>): List<ElectricityDay> {
        return records.groupBy { it.epochDay }
            .map { (epochDay, samples) ->
                ElectricityDay(
                    epochDay = epochDay,
                    lightingKwh = samples.maxOf { it.lightingKwh },
                    acKwh = samples.maxOf { it.acKwh },
                    balance = samples.maxByOrNull { it.recordedAt }?.balance,
                    updatedAt = samples.maxOf { it.recordedAt }
                )
            }
            .sortedBy { it.epochDay }
    }
}
