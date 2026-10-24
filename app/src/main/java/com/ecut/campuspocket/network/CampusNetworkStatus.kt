package com.ecut.campuspocket.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface CampusNetworkState {
    data object Unknown : CampusNetworkState
    data object Checking : CampusNetworkState
    data object OnCampus : CampusNetworkState
    data object OffCampus : CampusNetworkState
}

/**
 * 校园网连通性检测。
 *
 * 直接探测两个内网主机的 TCP/HTTP 是否可达：
 * 只要收到任意 HTTP 响应（包括 403）就说明当前在校园网内。
 * 不使用 Wi-Fi SSID，避免申请定位权限。
 */
object CampusNetworkStatus {

    private val _state = MutableStateFlow<CampusNetworkState>(CampusNetworkState.Unknown)
    val state: StateFlow<CampusNetworkState> = _state.asStateFlow()

    private val mutex = Mutex()

    private val probeClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build()
    }

    private val probeUrls = listOf(
        "${CampusEndpoints.JW_BASE_URL}/jwglxt/",
        "http://172.20.134.7:8080/"
    )

    fun isOnCampus(): Boolean = _state.value is CampusNetworkState.OnCampus

    /**
     * 两次尝试，避免 Wi-Fi 刚连上时误判为校外。
     */
    suspend fun refresh(): Boolean = mutex.withLock {
        _state.value = CampusNetworkState.Checking
        var reachable = false
        for (attempt in 0 until 2) {
            reachable = withContext(Dispatchers.IO) { probe() }
            if (reachable) break
            if (attempt == 0) delay(700)
        }
        _state.value = if (reachable) {
            CampusNetworkState.OnCampus
        } else {
            CampusNetworkState.OffCampus
        }
        reachable
    }

    private fun probe(): Boolean {
        for (url in probeUrls) {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", CampusEndpoints.DESKTOP_USER_AGENT)
                .header("Cache-Control", "no-cache")
                .build()
            try {
                probeClient.newCall(request).execute().use {
                    return true
                }
            } catch (_: IOException) {
                // 试下一个内网地址
            }
        }
        return false
    }
}
