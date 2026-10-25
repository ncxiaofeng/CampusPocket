package com.ecut.campuspocket.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.ecut.campuspocket.data.remote.weather.WeatherLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 使用系统 LocationManager 读取一次设备位置。
 *
 * 不引入 Google Play Services / 高德 SDK，只依赖 Android 原生定位；
 * 先取最近一次缓存位置，缓存太旧再尝试一次单次定位，最后回退缓存。
 */
class DeviceLocationProvider(
    private val context: Context
) {

    fun hasPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    suspend fun currentLocation(): Location? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val cached = bestLastKnown(manager)
        if (cached != null && System.currentTimeMillis() - cached.time <= FRESH_MS) {
            return@withContext cached
        }

        val fresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getCurrentLocation(manager)
        } else {
            requestSingleUpdate(manager)
        }
        fresh ?: cached
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(manager: LocationManager): Location? {
        if (!hasPermission()) return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    /**
     * Android 11+ 的单次定位接口，带 10 秒超时。
     */
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(manager: LocationManager): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }

                val providers = availableProviders(manager)
                if (providers.isEmpty()) {
                    if (continuation.isActive) continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                var remaining = providers.size
                var finished = false
                fun finish(location: Location?) {
                    if (finished) return
                    finished = true
                    if (continuation.isActive) continuation.resume(location)
                }

                providers.forEach { provider ->
                    runCatching {
                        manager.getCurrentLocation(
                            provider,
                            cancellation,
                            ContextCompat.getMainExecutor(context)
                        ) { location ->
                            if (location != null) {
                                finish(location)
                            } else {
                                remaining -= 1
                                if (remaining == 0) finish(null)
                            }
                        }
                    }.onFailure {
                        remaining -= 1
                        if (remaining == 0) finish(null)
                    }
                }
            }
        }
    }

    /**
     * Android 8–10 的单次定位接口；requestSingleUpdate 已废弃，但低版本仍需它。
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(manager: LocationManager): Location? {
        val provider = availableProviders(manager).firstOrNull() ?: return null
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) continuation.resume(location)
                    }

                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }
                runCatching {
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    continuation.invokeOnCancellation {
                        runCatching { manager.removeUpdates(listener) }
                    }
                }.onFailure {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private fun availableProviders(manager: LocationManager): List<String> {
        val providers = buildList {
            if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }
                    .getOrDefault(false)
            ) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
                    .getOrDefault(false)
            ) {
                add(LocationManager.GPS_PROVIDER)
            }
        }
        return providers
    }

    private companion object {
        const val FRESH_MS = 10 * 60 * 1000L
        const val TIMEOUT_MS = 10_000L
    }
}

/** 把 Android Location 转成天气接口需要的坐标。 */
fun Location.toWeatherLocation(): WeatherLocation = WeatherLocation(
    name = "当前位置",
    latitude = latitude,
    longitude = longitude
)
