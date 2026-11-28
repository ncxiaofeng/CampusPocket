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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HuiLifeViewModel(
    private val repository: HuiLifeRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    var phone by mutableStateOf(preferences.huiLifePhone)
    var graphCode by mutableStateOf("")
    var smsCode by mutableStateOf("")
    var platform by mutableStateOf(HuiLifePlatform.APP)
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
    var confirmStopDevice by mutableStateOf<HuiLifeDevice?>(null)
        private set
    /** 本次进程内由本机亲手点启动成功的设备及其启动时间戳；仅用于安全判断可停止的自己设备。 */
    var startedByMeAt by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    private var appSessionUid: String = ""
    var loginFormVisible by mutableStateOf(false)
        private set
    var scannedDevices by mutableStateOf(preferences.huiLifeScannedDevices)
        private set
    var cachedDeviceNames by mutableStateOf(preferences.huiLifeDeviceNames)
        private set
    var cachedDeviceDetails by mutableStateOf(preferences.huiLifeDeviceDetails)
        private set
    var customDeviceNames by mutableStateOf(preferences.huiLifeCustomDeviceNames)
        private set
    var renameTarget by mutableStateOf<HuiLifeDevice?>(null)
        private set
    var statusById by mutableStateOf<Map<String, HuiLifeDeviceStatus>>(emptyMap())
        private set
    var probedInfoById by mutableStateOf<Map<String, HuiLifeDevice>>(emptyMap())
        private set

    private var statusRefreshing = false

    /**
     * 设备列表 + 用户扫过但接口没返回的编号；只有扫码的设备能启动。
     *
     * 名称优先级：用户自定义备注 > 收藏列表名 > 状态接口名 > 最近账单名 > 本地缓存名 > 带尾号的可区分兜底名。
     */
    val allDevices: List<HuiLifeDevice>
        get() {
            val billsById = dashboard?.bills.orEmpty()
                .filter { it.deviceId.isNotBlank() && it.deviceName.isNotBlank() }
                .associate { it.deviceId to it.deviceName }
            val known = dashboard?.devices.orEmpty().map { device ->
                val status = statusById[device.id]
                val resolvedName = resolveDisplayName(
                    id = device.id,
                    serverName = device.name,
                    statusName = status?.deviceName.orEmpty(),
                    billName = billsById[device.id].orEmpty(),
                    cachedName = cachedDeviceNames[device.id].orEmpty(),
                    customName = customDeviceNames[device.id].orEmpty()
                )
                val resolvedDetail = device.detail.ifBlank {
                    status?.deviceDetail.orEmpty()
                }
                device.copy(name = resolvedName, detail = resolvedDetail)
            }
            val knownIds = known.mapTo(mutableSetOf()) { it.id }
            val extras = scannedDevices
                .filter { it !in knownIds }
                .map { id ->
                    val status = statusById[id]
                    val probed = probedInfoById[id]
                    val resolvedName = resolveDisplayName(
                        id = id,
                        serverName = probed?.name.orEmpty(),
                        statusName = status?.deviceName.orEmpty(),
                        billName = billsById[id].orEmpty(),
                        cachedName = cachedDeviceNames[id].orEmpty(),
                        customName = customDeviceNames[id].orEmpty()
                    )
                    val detail = probed?.detail.orEmpty()
                        .ifBlank { status?.deviceDetail.orEmpty() }
                        .ifBlank { cachedDeviceDetails[id].orEmpty() }
                        .ifBlank { "编号 ${shortDeviceSuffix(id)}" }
                    HuiLifeDevice(
                        id = id,
                        name = resolvedName,
                        detail = detail,
                        typeCode = probed?.typeCode.orEmpty()
                            .ifBlank { status?.deviceTypeCode.orEmpty() }
                    )
                }
            return known + extras
        }

    fun isScanned(deviceId: String): Boolean = deviceId in scannedDevices

    fun statusFor(deviceId: String): HuiLifeDeviceStatus? = statusById[deviceId]

    fun customNameFor(deviceId: String): String = customDeviceNames[deviceId].orEmpty()

    /** 已扫码 + 状态为空闲/在线，才允许启动；使用中/离线/禁用都不显示启动。 */
    fun canStart(deviceId: String): Boolean =
        appLoggedIn && isScanned(deviceId) && (statusById[deviceId]?.canStart ?: true)

    /** 仅允许停止确认由本人启动且当前正在运行中的设备，绝不误停他人设备。 */
    fun canStop(deviceId: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        shouldAllowStop(
            appLoggedIn = appLoggedIn,
            status = statusById[deviceId],
            startedByMeMillis = startedByMeAt[deviceId],
            myUid = appSessionUid,
            nowMillis = nowMillis
        )

    private val probedInfoIds = mutableSetOf<String>()

    fun refreshStatuses() {
        if (statusRefreshing) return
        if (!accountLoggedIn && !appLoggedIn) return
        val devices = allDevices
        if (devices.isEmpty()) return
        statusRefreshing = true
        viewModelScope.launch {
            try {
                val updated = statusById.toMutableMap()
                val knownServerIds = dashboard?.devices.orEmpty()
                    .filter { it.name.isNotBlank() && it.name != "未命名设备" }
                    .mapTo(mutableSetOf()) { it.id }
                devices.forEach { device ->
                    val cached = cachedDeviceNames[device.id].orEmpty()
                    val needsName = device.id !in knownServerIds &&
                        (cached.isBlank() || isGenericPlaceholderName(cached))
                    val needsDetail = device.id !in knownServerIds &&
                        cachedDeviceDetails[device.id].isNullOrBlank()
                    repository.fetchDeviceStatus(device.id, more = needsName)
                        .onSuccess { status ->
                            updated[device.id] = status
                            if (status.deviceName.isNotBlank() &&
                                !isGenericPlaceholderName(status.deviceName)
                            ) {
                                cachedDeviceNames = preferences.saveHuiLifeDeviceName(
                                    device.id,
                                    status.deviceName
                                )
                            }
                            if (status.deviceDetail.isNotBlank()) {
                                cachedDeviceDetails = preferences.saveHuiLifeDeviceDetail(
                                    device.id,
                                    status.deviceDetail
                                )
                            }
                        }
                    if ((needsName || needsDetail) && probedInfoIds.add(device.id)) {
                        repository.fetchDeviceInfo(device.id).onSuccess { info ->
                            if (info != null) {
                                probedInfoById = probedInfoById + (device.id to info)
                                if (info.name.isNotBlank() &&
                                    !isGenericPlaceholderName(info.name)
                                ) {
                                    cachedDeviceNames = preferences.saveHuiLifeDeviceName(
                                        device.id,
                                        info.name
                                    )
                                }
                                if (info.detail.isNotBlank()) {
                                    cachedDeviceDetails = preferences.saveHuiLifeDeviceDetail(
                                        device.id,
                                        info.detail
                                    )
                                }
                            }
                        }
                    }
                }
                statusById = updated
                startedByMeAt = reconcileStartedByMe(
                    startedByMeAt = startedByMeAt,
                    statuses = updated,
                    nowMillis = System.currentTimeMillis()
                )
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
                onSuccess = { session ->
                    if (platform == HuiLifePlatform.APP && session.uid.isNotBlank()) {
                        appSessionUid = session.uid
                    }
                    accountLoggedIn = repository.hasAccount()
                    appLoggedIn = repository.hasApp()
                    smsSent = false
                    smsCode = ""
                    graphCode = ""
                    captcha = null
                    loginFormVisible = false
                    succeeded = true
                    val hasAppControlConfig = AppContainer.runtimeConfig.get().hasHuiLife
                    message = if (platform == HuiLifePlatform.ACCOUNT && !appLoggedIn) {
                        if (hasAppControlConfig) {
                            "查询登录成功。需要一键启动设备时，请切换「设备控制」再登录一次。"
                        } else {
                            "查询登录成功，已同步钱包余额与设备状态。"
                        }
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
                onSuccess = { loaded ->
                    dashboard = loaded
                    // 把收藏设备和最近账单里看到的真实设备名都沉淀到本地缓存。
                    loaded.devices.forEach { device ->
                        if (device.name.isNotBlank() && device.name != "未命名设备") {
                            cachedDeviceNames = preferences.saveHuiLifeDeviceName(
                                device.id,
                                device.name
                            )
                        }
                    }
                    loaded.bills.forEach { bill ->
                        if (bill.deviceId.isNotBlank() && bill.deviceName.isNotBlank()) {
                            cachedDeviceNames = preferences.saveHuiLifeDeviceName(
                                bill.deviceId,
                                bill.deviceName
                            )
                        }
                    }
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
                    onSuccess = { device ->
                        markScanned(device.id, resolvedName = device.name)
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

    private fun markScanned(id: String, resolvedName: String = "") {
        scannedDevices = preferences.markHuiLifeDeviceScanned(id)
        if (resolvedName.isNotBlank() && resolvedName != "未命名设备") {
            cachedDeviceNames = preferences.saveHuiLifeDeviceName(id, resolvedName)
        }
        val known = dashboard?.devices?.firstOrNull { it.id == id }
        val displayName = resolveDisplayName(
            id = id,
            serverName = known?.name.orEmpty().ifBlank { resolvedName },
            statusName = statusById[id]?.deviceName.orEmpty(),
            billName = dashboard?.bills?.firstOrNull { it.deviceId == id }?.deviceName.orEmpty(),
            cachedName = cachedDeviceNames[id].orEmpty(),
            customName = customDeviceNames[id].orEmpty()
        )
        message = if (known != null || resolvedName.isNotBlank()) {
            "已扫码：$displayName，现在可以启动"
        } else {
            "已添加 $displayName，正在同步设备名称与状态…"
        }
        // 扫码后立刻用 more=true 拉一次设备状态，把服务端真实设备名取回来。
        viewModelScope.launch {
            repository.fetchDeviceStatus(id, more = true).onSuccess { status ->
                statusById = statusById + (id to status)
                if (status.deviceName.isNotBlank()) {
                    cachedDeviceNames = preferences.saveHuiLifeDeviceName(id, status.deviceName)
                    val updatedName = resolveDisplayName(
                        id = id,
                        serverName = known?.name.orEmpty(),
                        statusName = status.deviceName,
                        billName = dashboard?.bills?.firstOrNull { it.deviceId == id }?.deviceName.orEmpty(),
                        cachedName = cachedDeviceNames[id].orEmpty(),
                        customName = customDeviceNames[id].orEmpty()
                    )
                    message = "已扫码：$updatedName（${status.label}）"
                }
            }
            refreshStatuses()
        }
    }

    fun openRename(device: HuiLifeDevice) {
        renameTarget = device
    }

    fun dismissRename() {
        renameTarget = null
    }

    fun saveCustomName(deviceId: String, name: String) {
        customDeviceNames = preferences.saveHuiLifeCustomDeviceName(deviceId, name)
        renameTarget = null
        message = if (name.trim().isBlank()) {
            "已恢复默认设备名称"
        } else {
            "已备注为「${name.trim()}」"
        }
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
            var started = false
            repository.startDevice(device.id).fold(
                onSuccess = {
                    started = true
                    startedByMeAt = startedByMeAt + (device.id to System.currentTimeMillis())
                    message = "${device.name}：$it（使用完毕可点「停止」）"
                },
                onFailure = { message = "启动失败：${it.message}" }
            )
            loading = false
            refreshStatuses()
            if (started) {
                delay(1500)
                refreshStatuses()
            }
        }
    }

    fun cancelStart() {
        confirmDevice = null
    }

    fun askStop(device: HuiLifeDevice) {
        if (!canStop(device.id)) {
            message = "只支持停止由你本人启动且正在使用中的设备"
            return
        }
        confirmStopDevice = device
    }

    fun cancelStop() {
        confirmStopDevice = null
    }

    fun confirmStop() {
        val device = confirmStopDevice ?: return
        confirmStopDevice = null
        if (!canStop(device.id)) {
            message = "设备状态已变化，无法停止"
            return
        }
        viewModelScope.launch {
            loading = true
            repository.stopDevice(device.id).fold(
                onSuccess = {
                    startedByMeAt = startedByMeAt - device.id
                    message = "${device.name}：$it"
                },
                onFailure = { message = "停止失败：${it.message}" }
            )
            loading = false
            refreshStatuses()
            delay(1500)
            refresh()
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
            if (platform == HuiLifePlatform.APP) {
                startedByMeAt = emptyMap()
                appSessionUid = ""
            }
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
        private const val SELF_START_TTL_MS = 30 * 60 * 1000L
        private const val SELF_START_GRACE_MS = 15 * 1000L

        /**
         * 判断是否允许显示/执行「停止」：
         * 1. 必须已登录设备控制端（APP token）；
         * 2. 设备当前正在运行（isRunning，或在刚点启动的 15 秒过渡期内）；
         * 3. 归属确认：本机 30 分钟内亲手点过启动，或服务端状态返回 usingUserId == 当前 uid。
         */
        internal fun shouldAllowStop(
            appLoggedIn: Boolean,
            status: HuiLifeDeviceStatus?,
            startedByMeMillis: Long?,
            myUid: String = "",
            nowMillis: Long = System.currentTimeMillis()
        ): Boolean {
            if (!appLoggedIn) return false
            val startedRecently = startedByMeMillis != null &&
                nowMillis >= startedByMeMillis &&
                nowMillis - startedByMeMillis <= SELF_START_TTL_MS
            val inStartupGrace = startedByMeMillis != null &&
                nowMillis >= startedByMeMillis &&
                nowMillis - startedByMeMillis <= SELF_START_GRACE_MS
            val matchedServerOwner = myUid.isNotBlank() &&
                status?.usingUserId?.isNotBlank() == true &&
                status.usingUserId == myUid
            if (status == null) return inStartupGrace
            if (!status.isRunning) return false
            return startedRecently || matchedServerOwner
        }

        /** 轮询状态后清理已自动结束或超时的本机启动记录。 */
        internal fun reconcileStartedByMe(
            startedByMeAt: Map<String, Long>,
            statuses: Map<String, HuiLifeDeviceStatus>,
            nowMillis: Long = System.currentTimeMillis()
        ): Map<String, Long> {
            if (startedByMeAt.isEmpty()) return startedByMeAt
            return startedByMeAt.filter { (id, startedAt) ->
                val age = nowMillis - startedAt
                if (age < 0L || age > SELF_START_TTL_MS) return@filter false
                val status = statuses[id] ?: return@filter true
                // 刚启动 15 秒内允许状态短暂仍为 99，超过后只要已空闲/离线/禁用即清除。
                if (status.isRunning) true else age <= SELF_START_GRACE_MS
            }
        }

        internal fun isGenericPlaceholderName(text: String): Boolean {
            val cleaned = text.trim()
            return cleaned.isBlank() ||
                cleaned == "未命名设备" ||
                cleaned == "扫码设备" ||
                cleaned == "新注册设备"
        }

        internal fun resolveDisplayName(
            id: String,
            serverName: String = "",
            statusName: String = "",
            billName: String = "",
            cachedName: String = "",
            customName: String = ""
        ): String {
            fun valid(text: String): String? =
                text.trim().takeIf { !isGenericPlaceholderName(it) }
            val specific = valid(customName)
                ?: valid(serverName)
                ?: valid(statusName)
                ?: valid(billName)
                ?: valid(cachedName)
            if (specific != null) return specific
            val hasNewReg = listOf(serverName, statusName, cachedName)
                .any { it.trim() == "新注册设备" }
            val prefix = if (hasNewReg) "未命名设备" else "扫码设备"
            return "$prefix · ${shortDeviceSuffix(id)}"
        }

        internal fun shortDeviceSuffix(id: String): String {
            val cleaned = id.trim()
            return if (cleaned.length <= 6) cleaned else cleaned.takeLast(6)
        }

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
