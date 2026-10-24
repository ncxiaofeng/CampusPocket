package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.remote.electricity.XgwxElectricityClient
import com.ecut.campuspocket.data.remote.electricity.XgwxSnapshot
import java.time.LocalDate

/**
 * 宿舍电费同步：查询余额和最近 7 天用量，写入本地采样表。
 */
class XgwxElectricityRepository(
    private val client: XgwxElectricityClient,
    private val electricityRepository: ElectricityRepository
) {

    fun observeDays() = electricityRepository.observeDays()

    suspend fun refresh(): Result<XgwxSnapshot> = runCatching {
        val snapshot = client.fetchSnapshot()
        val today = LocalDate.now()
        snapshot.dailyUsage.forEach { (date, usage) ->
            electricityRepository.addDaySample(
                epochDay = date.toEpochDay(),
                totalKwh = usage,
                balance = if (date == today) snapshot.balance else null
            )
        }
        snapshot
    }
}
