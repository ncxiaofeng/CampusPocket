package com.ecut.campuspocket.data.remote

import java.io.IOException

data class ElectricitySample(
    val lightingKwh: Double,
    val acKwh: Double,
    val balance: Double?
)

class AdapterNotConfiguredException(
    val systemName: String
) : IOException("${systemName}接口尚未配置")

/**
 * 电费查询数据源。接入真实接口时实现该接口并在 AppContainer 中替换。
 */
interface ElectricitySource {
    suspend fun fetch(): ElectricitySample
}

object UnconfiguredElectricitySource : ElectricitySource {
    override suspend fun fetch(): ElectricitySample {
        throw AdapterNotConfiguredException("电费查询")
    }
}
