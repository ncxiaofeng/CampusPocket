package com.ecut.campuspocket.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.AppPreferences
import com.ecut.campuspocket.data.location.DeviceLocationProvider
import com.ecut.campuspocket.data.location.toWeatherLocation
import com.ecut.campuspocket.data.remote.weather.WeatherClient
import com.ecut.campuspocket.data.remote.weather.WeatherLocation
import com.ecut.campuspocket.data.remote.weather.WeatherSnapshot
import kotlinx.coroutines.launch

class WeatherViewModel(
    private val client: WeatherClient,
    private val locationProvider: DeviceLocationProvider,
    private val preferences: AppPreferences
) : ViewModel() {

    var snapshot by mutableStateOf<WeatherSnapshot?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /** null 表示跟随设备定位；非 null 是用户手动选择的校区。 */
    var locationOverride by mutableStateOf<WeatherLocation?>(null)
        private set

    private var lastSuccessAt = 0L

    init {
        // 恢复上次手动选择的校区；默认跟随设备定位。
        locationOverride = locationForKey(preferences.weatherLocation)
        refreshWithDeviceLocation()
    }

    fun refreshIfStale(maxAgeMillis: Long = 20 * 60 * 1000L) {
        if (isLoading) return
        if (snapshot != null && System.currentTimeMillis() - lastSuccessAt < maxAgeMillis) return
        refreshWithDeviceLocation()
    }

    /** 手动刷新也优先使用当前选中的位置。 */
    fun refresh() {
        refreshWithDeviceLocation()
    }

    fun selectLocation(location: WeatherLocation?) {
        locationOverride = location
        preferences.weatherLocation = keyForLocation(location)
        if (location != null) {
            refreshWithLocation(location)
        } else {
            refreshWithDeviceLocation()
        }
    }

    /**
     * 请求一次设备位置后刷新天气。
     *
     * 权限被拒绝或定位失败时，WeatherClient.fetch() 会回退到广兰校区，
     * 不会让天气卡片变成空白。
     */
    fun refreshWithDeviceLocation() {
        if (isLoading) return
        val override = locationOverride
        if (override != null) {
            refreshWithLocation(override)
            return
        }
        viewModelScope.launch {
            isLoading = true
            val location = locationProvider.currentLocation()
            runCatching {
                if (location != null) {
                    client.fetch(location.toWeatherLocation())
                } else {
                    client.fetch()
                }
            }.fold(
                onSuccess = { result ->
                    snapshot = result
                    lastSuccessAt = System.currentTimeMillis()
                    message = null
                },
                onFailure = { error ->
                    message = error.message ?: "天气服务暂不可用"
                }
            )
            isLoading = false
        }
    }

    private fun refreshWithLocation(location: WeatherLocation) {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            runCatching { client.fetch(location) }.fold(
                onSuccess = { result ->
                    snapshot = result
                    lastSuccessAt = System.currentTimeMillis()
                    message = null
                },
                onFailure = { error ->
                    message = error.message ?: "天气服务暂不可用"
                }
            )
            isLoading = false
        }
    }

    private fun locationForKey(key: String): WeatherLocation? = when (key) {
        "guanglan" -> WeatherClient.GUANGLAN
        "qinglan" -> WeatherClient.QINGLAN
        "fuzhou" -> WeatherClient.FUZHOU
        else -> null
    }

    private fun keyForLocation(location: WeatherLocation?): String = when (location) {
        WeatherClient.GUANGLAN -> "guanglan"
        WeatherClient.QINGLAN -> "qinglan"
        WeatherClient.FUZHOU -> "fuzhou"
        else -> "device"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WeatherViewModel(
                    client = AppContainer.weatherClient,
                    locationProvider = AppContainer.deviceLocationProvider,
                    preferences = AppContainer.preferences
                )
            }
        }
    }
}
