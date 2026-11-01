package com.ecut.campuspocket.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.AppPreferences
import com.ecut.campuspocket.data.remote.huilife.HuiLifeClient
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDevice
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceIdParser
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceStatus
import com.ecut.campuspocket.data.remote.huilife.HuiLifePlatform
import com.ecut.campuspocket.data.repository.HuiLifeDashboard
import com.ecut.campuspocket.data.repository.HuiLifeRepository
import kotlinx.coroutines.launch

class HuiLifeViewModel(
    private val repository: HuiLifeRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    var phone by mutableStateOf(preferences.huiLifePhone)
    var graphCode by mutableStateOf("")
    var smsCode by mutableStateOf("")
    var platform by mutableStateOf(HuiLifePlatform.ACCOUNT)
        private set
    var captcha by mutableStateOf<ImageBitmap?>(null)
        private set
    var smsSent by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var dashboard by mutableStateOf<HuiLifeDashboard?>(null)
        private set
    var accountLoggedIn by mutableStateOf(false)
        private set
    var appLoggedIn by mutableStateOf(false)
        private set
    var confirmDevice by mutableStateOf<HuiLifeDevice?>(null)
        private set
    var loginFormVisible by mutableStateOf(false)
        private set
    var scannedDevices by mutableStateOf(preferences.huiLifeScannedDevices)
        private set
    var statusById by mutableStateOf<Map<String, HuiLifeDeviceStatus>>(emptyMap())
        private set

    private var statusRefreshing = false

    /** 设备列表 + 用户扫过但接口没返回的编号；只有扫码的设备能启动。 */
    val allDevices: List<HuiLifeDevice>
        get() {
            val known = dashboard?.devices.orEmpty()
            val extras = scannedDevices
                .filter { id -> known.none { it.id == id } }
                .map {
                    HuiLifeDevice(
                        id = it,
                        name = "扫码设备",
                        detail = "已添加，等待状态同步"
                    )
                }
            return known + extras
        }

    fun isScanned(deviceId: String): Boolean = deviceId in scannedDevices

    fun statusFor(deviceId: String): HuiLifeDeviceStatus? = statusById[deviceId]

    /** 已扫码 + 状态为空闲/在线，才允许启动；使用中/离线/禁用都不显示启动。 */
    fun canStart(deviceId: String): Boolean =
        appLoggedIn && isScanned(deviceId) && (statusById[deviceId]?.canStart ?: true)

    fun refreshStatuses() {
        if (statusRefreshing) return
        if (!accountLoggedIn && !appLoggedIn) return
        val devices = allDevices
        if (devices.isEmpty()) return
        statusRefreshing = true
        viewModelScope.launch {
            try {
                val updated = statusById.toMutableMap()
                devices.forEach { device ->
                    repository.fetchDeviceStatus(device.id)
                        .onSuccess { updated[device.id] = it }
                }
                statusById = updated
            } finally {
                statusRefreshing = false
            }
        }
    }

    private var sessionKey = ""

    init {
        viewModelScope.launch {
            accountLoggedIn = repository.hasAccount()
            appLoggedIn = repository.hasApp()
            if (accountLoggedIn || appLoggedIn) refresh()
        }
    }

    fun choosePlatform(value: HuiLifePlatform) {
        platform = value
        smsSent = false
        smsCode = ""
        message = null
    }

    fun loadCaptcha() {
        if (phone.length != 11) {
            message = "请输入 11 位手机号"
            return
        }
        if (loading) return
        sessionKey = HuiLifeClient.newCaptchaKey()
        viewModelScope.launch {
            loading = true
            runCatching { repository.captcha(sessionKey) }.fold(
                onSuccess = { bytes ->
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) {
                        message = "图形验证码解析失败"
                    } else {
                        captcha = bitmap.asImageBitmap()
                        smsSent = false
                        message = "图形验证码已加载"
                    }
                },
                onFailure = { message = "图形验证码获取失败：${it.message}" }
            )
            loading = false
        }
    }

    fun sendSms() {
        if (phone.length != 11) {
            message = "请输入 11 位手机号"
            return
        }
        if (graphCode.isBlank() || captcha == null) {
            message = "请先输入图形验证码"
            return
        }
        if (loading) return
        viewModelScope.launch {
            loading = true
            repository.sendSms(phone, graphCode.trim(), sessionKey, platform).fold(
                onSuccess = {
                    smsSent = true
                    message = "短信已发送，请查收"
                },
                onFailure = { message = "发送失败：${it.message}" }
            )
            loading = false
        }
    }

    fun login() {
        if (smsCode.isBlank()) {
            message = "请输入短信验证码"
            return
        }
        if (loading) return
        viewModelScope.launch {
            loading = true
            var succeeded = false
            repository.login(phone, smsCode.trim(), platform).fold(
                onSuccess = {
                    accountLoggedIn = repository.hasAccount()
                    appLoggedIn = repository.hasApp()
                    smsSent = false
                    smsCode = ""
                    graphCode = ""
                    captcha = null
                    loginFormVisible = false
                    succeeded = true
                    message = if (platform == HuiLifePlatform.ACCOUNT && !appLoggedIn) {
                        "查询登录成功。需要一键启动设备时，请切换「设备控制」再登录一次。"
                    } else if (platform == HuiLifePlatform.APP) {
                        "设备控制登录成功，可以一键启动设备了"
                    } else {
                        "登录成功"
                    }
                },
                onFailure = { message = "登录失败：${it.message}" }
            )
            loading = false
            if (succeeded) refresh()
        }
    }

    fun refresh() {
        if (loading) return
        if (!accountLoggedIn && !appLoggedIn) {
            viewModelScope.launch {
                accountLoggedIn = repository.hasAccount()
                appLoggedIn = repository.hasApp()
                if (accountLoggedIn || appLoggedIn) refresh()
            }
            return
        }
        viewModelScope.launch {
            loading = true
            repository.loadDashboard().fold(
                onSuccess = {
                    dashboard = it
                    if (message?.startsWith("读取失败") == true) message = null
                },
                onFailure = { message = "读取失败：${it.message}" }
            )
            accountLoggedIn = repository.hasAccount()
            appLoggedIn = repository.hasApp()
            loading = false
            refreshStatuses()
        }
    }

    fun askStart(device: HuiLifeDevice) {
        if (!appLoggedIn) {
            message = "需要先完成「设备控制」登录"
            return
        }
        if (!isScanned(device.id)) {
            message = "请先扫描设备二维码，确认你就在设备旁边后再启动"
            return
        }
        if (statusById[device.id]?.canStart == false) {
            message = "设备当前${statusById[device.id]?.label.orEmpty()}，不能启动"
            return
        }
        confirmDevice = device
    }

    fun addScannedDevice(raw: String) {
        val parsed = HuiLifeDeviceIdParser.parse(raw)
        val qrId = parsed.qrId
        if (qrId != null) {
            if (!appLoggedIn) {
                message = "这个二维码需要设备控制登录后才能解析"
                return
            }
            if (loading) return
            viewModelScope.launch {
                loading = true
                repository.resolveQrDevice(qrId).fold(
                    onSuccess = { id ->
                        markScanned(id)
                    },
                    onFailure = { message = "二维码解析失败：${it.message}" }
                )
                loading = false
            }
            return
        }
        val id = parsed.deviceId
        if (id.isNullOrBlank()) {
            message = "二维码里没有识别到设备编号"
            return
        }
        markScanned(id)
    }

    private fun markScanned(id: String) {
        scannedDevices = preferences.markHuiLifeDeviceScanned(id)
        val known = dashboard?.devices?.firstOrNull { it.id == id }
        message = if (known != null) {
            "已扫码：${known.name}，现在可以启动"
        } else {
            "已添加设备 $id；状态同步后即可启动"
        }
        refreshStatuses()
    }

    fun removeScannedDevice(device: HuiLifeDevice) {
        scannedDevices = preferences.removeHuiLifeScannedDevice(device.id)
        message = "已取消 ${device.name} 的扫码授权，之后只能查看"
    }

    fun probeStatus(device: HuiLifeDevice) {
        if (loading) return
        viewModelScope.launch {
            loading = true
            repository.probeDeviceStatus(device.id).fold(
                onSuccess = { message = "设备状态诊断（${device.id}）：\n$it" },
                onFailure = { message = "设备状态诊断失败：${it.message}" }
            )
            loading = false
        }
    }

    fun confirmStart() {
        val device = confirmDevice ?: return
        confirmDevice = null
        viewModelScope.launch {
            loading = true
            repository.startDevice(device.id).fold(
                onSuccess = { message = "${device.name}：$it" },
                onFailure = { message = "启动失败：${it.message}" }
            )
            loading = false
            refreshStatuses()
        }
    }

    fun cancelStart() {
        confirmDevice = null
    }

    fun openLogin(platform: HuiLifePlatform) {
        choosePlatform(platform)
        loginFormVisible = true
        message = null
    }

    fun closeLogin() {
        loginFormVisible = false
    }

    fun logout(platform: HuiLifePlatform) {
        viewModelScope.launch {
            runCatching { repository.logout(platform) }
            accountLoggedIn = repository.hasAccount()
            appLoggedIn = repository.hasApp()
            if (!accountLoggedIn && !appLoggedIn) dashboard = null
            message = "已退出${if (platform == HuiLifePlatform.APP) "设备控制" else "查询"}登录"
        }
    }

    fun clearMessage() {
        message = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HuiLifeViewModel(
                    repository = AppContainer.huiLifeRepository,
                    preferences = AppContainer.preferences
                )
            }
        }
    }
}
