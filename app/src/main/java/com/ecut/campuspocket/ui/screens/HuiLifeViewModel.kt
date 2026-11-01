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
        }
    }

    fun askStart(device: HuiLifeDevice) {
        if (!appLoggedIn) {
            message = "需要先完成「设备控制」登录"
            return
        }
        confirmDevice = device
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
        }
    }

    fun cancelStart() {
        confirmDevice = null
    }

    fun stop(device: HuiLifeDevice) {
        viewModelScope.launch {
            loading = true
            repository.stopDevice(device.id).fold(
                onSuccess = { message = "${device.name}：$it" },
                onFailure = { message = "停止失败：${it.message}" }
            )
            loading = false
        }
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
