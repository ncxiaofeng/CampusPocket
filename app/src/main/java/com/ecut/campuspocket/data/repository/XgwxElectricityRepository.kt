package com.ecut.campuspocket.data.repository

import com.ecut.campuspocket.data.remote.electricity.XgwxElectricityClient
import com.ecut.campuspocket.data.remote.electricity.XgwxSnapshot
import java.time.LocalDate

/**
 * 宿舍电费同步：查询余额和最近 30 天用量，写入本地采样表。
 */
class XgwxElectricityRepository(
    private val client: XgwxElectricityClient,
    private val electricityRepository: ElectricityRepository
) {

    fun observeDays() = electricityRepository.observeDays()

    suspend fun refresh(): Result<XgwxSnapshot> = runCatching {
        val snapshot = client.fetchSnapshot()
        val today = LocalDate.now()

        // 接口的“当天”通常还没结算，dailyUsage 的最后一个日期往往是昨天。
        // 余额必须挂到实际返回的最新一天上，否则永远没有一条记录带余额。
        val latestUsageDate = snapshot.dailyUsage.maxOfOrNull { it.first }
            ?: today
        val balance = snapshot.balance ?: electricityRepository.latestBalance()

        if (snapshot.dailyUsage.isEmpty()) {
            // 连历史用量都没有时，单独写一条余额记录，保证首页仍能看到余额。
            electricityRepository.addDaySample(
                epochDay = today.toEpochDay(),
                totalKwh = 0.0,
                balance = balance
            )
        } else {
            snapshot.dailyUsage.forEach { (date, usage) ->
                electricityRepository.addDaySample(
                    epochDay = date.toEpochDay(),
                    totalKwh = usage,
                    balance = if (date == latestUsageDate) balance else null
                )
            }
        }
        snapshot.copy(balance = balance)
    }
}
