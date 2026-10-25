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
import com.ecut.campuspocket.data.location.DeviceLocationProvider
import com.ecut.campuspocket.data.remote.ujing.UJING_CAMPUSES
import com.ecut.campuspocket.data.remote.ujing.UjingCampus
import com.ecut.campuspocket.data.remote.ujing.UjingOrder
import com.ecut.campuspocket.data.remote.ujing.UjingProgramInfo
import com.ecut.campuspocket.data.remote.ujing.UjingStore
import com.ecut.campuspocket.data.remote.ujing.UjingStoreInfo
import com.ecut.campuspocket.data.remote.ujing.UjingStoreRef
import com.ecut.campuspocket.data.repository.UjingRepository
import com.ecut.campuspocket.network.SessionExpiredException
import kotlinx.coroutines.launch

class UjingViewModel(
    private val repository: UjingRepository,
    private val locationProvider: DeviceLocationProvider
) : ViewModel() {

    var hasSession by mutableStateOf(false)
        private set

    var needsLogin by mutableStateOf(false)
        private set

    var isBusy by mutableStateOf(false)
        private set

    var isStoreLoading by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    val campuses: List<UjingCampus> = UJING_CAMPUSES

    var selectedCampus by mutableStateOf(UJING_CAMPUSES.first())
        private set

    /** 是否使用设备实际坐标查询附近洗衣房；手动切换校区后自动关闭。 */
    var usingDeviceLocation by mutableStateOf(false)
        private set

    /** 顶部栏/校区选择器显示的定位来源。 */
    val locationLabel: String
        get() = if (usingDeviceLocation) "当前位置" else selectedCampus.name

    var stores by mutableStateOf<List<UjingStore>>(emptyList())
        private set

    var selectedStore by mutableStateOf<UjingStore?>(null)
        private set

    var storeInfo by mutableStateOf<UjingStoreInfo?>(null)
        private set

    var programInfo by mutableStateOf<UjingProgramInfo?>(null)
        private set

    var waitMinutes by mutableStateOf<Int?>(null)
        private set

    var runningOrders by mutableStateOf<List<UjingOrder>>(emptyList())
        private set

    var historyOrders by mutableStateOf<List<UjingOrder>>(emptyList())
        private set

    var recentStores by mutableStateOf<List<UjingStoreRef>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            hasSession = repository.hasSession()
            // 如果天气页已经授权过定位，U净默认直接按当前位置查楼栋。
            usingDeviceLocation = locationProvider.hasPermission()
            if (hasSession) refresh()
        }
    }

    fun sendCaptcha(mobile: String) {
        if (mobile.length != 11 || !mobile.all { it.isDigit() }) {
            message = "请输入 11 位手机号"
            return
        }
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            message = null
            runCatching { repository.sendCaptcha(mobile) }
                .onSuccess { message = "验证码已发送" }
                .onFailure { message = it.message ?: "验证码发送失败" }
            isBusy = false
        }
    }

    fun login(mobile: String, captcha: String) {
        if (mobile.length != 11 || !mobile.all { it.isDigit() }) {
            message = "请输入 11 位手机号"
            return
        }
        if (captcha.isBlank()) {
            message = "请输入验证码"
            return
        }
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            message = null
            runCatching { repository.login(mobile, captcha) }
                .onSuccess {
                    hasSession = true
                    needsLogin = false
                    // 先释放 isBusy，refresh() 会检查它。
                    isBusy = false
                    refresh()
                    return@launch
                }
                .onFailure {
                    message = it.message ?: "登录失败"
                    hasSession = false
                }
            isBusy = false
        }
    }

    fun selectRecentStore(ref: UjingStoreRef) {
        selectStore(
            UjingStore(
                id = ref.storeId,
                name = ref.storeName,
                distanceKm = 0.0,
                deviceNum = 0,
                serviceSubjectName = "",
                address = "",
                latitude = 0.0,
                longitude = 0.0,
                washerCount = 0,
                dryerCount = 0
            )
        )
    }

    fun selectCampus(campus: UjingCampus) {
        if (campus.name == selectedCampus.name && !usingDeviceLocation) return
        usingDeviceLocation = false
        selectedCampus = campus
        selectedStore = null
        storeInfo = null
        programInfo = null
        waitMinutes = null
        refresh()
    }

    /** 使用设备实际位置查询附近洗衣房（需要定位权限）。 */
    fun useCurrentLocation() {
        if (usingDeviceLocation && !isBusy) return
        usingDeviceLocation = true
        selectedStore = null
        storeInfo = null
        programInfo = null
        waitMinutes = null
        refresh()
    }

    fun reportLocationDenied() {
        usingDeviceLocation = false
        message = "未授予定位权限，继续使用${selectedCampus.name}"
    }

    fun refresh() {
        if (isBusy) return
        viewModelScope.launch {
            isBusy = true
            message = null
            try {
                val deviceLocation = if (usingDeviceLocation) {
                    locationProvider.currentLocation()
                } else {
                    null
                }
                if (usingDeviceLocation && deviceLocation == null) {
                    // 权限被撤销或系统没有可用定位时，回到当前选中的校区。
                    usingDeviceLocation = false
                    message = "定位失败，已回退到${selectedCampus.name}"
                }
                stores = repository.nearbyStores(
                    deviceLocation?.latitude ?: selectedCampus.latitude,
                    deviceLocation?.longitude ?: selectedCampus.longitude
                )
                val target = selectedStore?.let { current ->
                    stores.firstOrNull { it.id == current.id } ?: current
                } ?: stores.firstOrNull()
                if (target != null) {
                    selectStore(target, refreshOrders = false)
                } else {
                    selectedStore = null
                    storeInfo = null
                    programInfo = null
                    waitMinutes = null
                }
                runningOrders = repository.runningOrders()
                val history = repository.historyOrders()
                historyOrders = history
                recentStores = try {
                    repository.recentStores(history)
                } catch (error: Exception) {
                    message = "最近使用加载失败：${error.message}"
                    emptyList()
                }
                hasSession = true
                needsLogin = false
            } catch (error: SessionExpiredException) {
                needsLogin = true
                hasSession = false
                message = "U净登录已过期，请重新登录"
            } catch (error: Exception) {
                message = error.message ?: "U净数据加载失败"
            } finally {
                isBusy = false
            }
        }
    }

    fun selectStore(store: UjingStore, refreshOrders: Boolean = true) {
        selectedStore = store
        viewModelScope.launch {
            isStoreLoading = true
            try {
                val info = repository.storeInfo(store.id)
                storeInfo = info
                val tag = info.devices.firstOrNull { it.deviceTypeId == 2 }
                    ?: info.devices.firstOrNull()
                if (tag != null) {
                    loadProgramsInternal(store.id, tag.deviceTypeId)
                } else {
                    programInfo = null
                    waitMinutes = null
                }
                if (refreshOrders) {
                    runningOrders = repository.runningOrders()
                    historyOrders = repository.historyOrders()
                }
            } catch (error: SessionExpiredException) {
                needsLogin = true
                hasSession = false
                message = "U净登录已过期，请重新登录"
            } catch (error: Exception) {
                message = error.message ?: "洗衣房数据加载失败"
            } finally {
                isStoreLoading = false
            }
        }
    }

    fun loadPrograms(deviceTypeId: Int) {
        val store = selectedStore ?: return
        viewModelScope.launch {
            try {
                loadProgramsInternal(store.id, deviceTypeId)
            } catch (error: Exception) {
                message = error.message ?: "洗护程序加载失败"
            }
        }
    }

    private suspend fun loadProgramsInternal(storeId: String, deviceTypeId: Int) {
        programInfo = repository.programInfo(storeId, deviceTypeId)
        waitMinutes = runCatching {
            repository.waitTime(storeId, deviceTypeId)
        }.getOrNull()
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            hasSession = false
            stores = emptyList()
            selectedStore = null
            storeInfo = null
            programInfo = null
            waitMinutes = null
            runningOrders = emptyList()
            historyOrders = emptyList()
            recentStores = emptyList()
            message = null
        }
    }

    fun clearMessage() {
        message = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UjingViewModel(
                    repository = AppContainer.ujingRepository,
                    locationProvider = AppContainer.deviceLocationProvider
                )
            }
        }
    }
}
