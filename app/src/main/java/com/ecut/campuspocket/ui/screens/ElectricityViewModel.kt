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
import com.ecut.campuspocket.data.repository.ElectricityDay
import com.ecut.campuspocket.data.repository.XgwxElectricityRepository
import com.ecut.campuspocket.network.SessionExpiredException
import com.ecut.campuspocket.network.SessionRecoveryResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ElectricityViewModel(
    private val repository: XgwxElectricityRepository
) : ViewModel() {

    val days: StateFlow<List<ElectricityDay>> = repository.observeDays()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    var isSyncing by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var needsLogin by mutableStateOf(false)
        private set

    private var lastSyncAt = 0L

    /**
     * 同步电费。默认 20 秒内重复进入页面不重复请求；
     * 从登录页返回时用 force = true。
     */
    fun sync(force: Boolean = false) {
        if (isSyncing) return
        if (!force && System.currentTimeMillis() - lastSyncAt < SYNC_THROTTLE_MS) return
        lastSyncAt = System.currentTimeMillis()
        viewModelScope.launch {
            isSyncing = true
            message = null
            needsLogin = false
            val first = repository.refresh()
            if (first.isSuccess) {
                reportSuccess(first.getOrThrow())
                isSyncing = false
                return@launch
            }

            val error = first.exceptionOrNull()
            if (error !is SessionExpiredException) {
                message = "同步失败：${error?.message ?: "请检查校园网连接"}"
                isSyncing = false
                return@launch
            }

            // 会话过期：先尝试用保存的账号密码自愈，再重试一次。
            message = "统一认证已过期，正在尝试自动恢复…"
            val recovery = runCatching {
                AppContainer.sessionRecoveryManager.recoverElectricitySession()
            }.getOrElse {
                SessionRecoveryResult.Failed(it.message ?: "自动恢复失败")
            }
            when (recovery) {
                is SessionRecoveryResult.AlreadyValid,
                is SessionRecoveryResult.AutoLoggedIn -> {
                    val second = repository.refresh()
                    if (second.isSuccess) {
                        reportSuccess(second.getOrThrow())
                    } else {
                        needsLogin = true
                        message = "自动恢复后电费仍读取失败：${second.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                }
                is SessionRecoveryResult.NoCredentials -> {
                    needsLogin = true
                    message = "统一认证已过期，请登录一次；可在「更多 → 自动登录」开启会话失效自动重登。"
                }
                is SessionRecoveryResult.Failed -> {
                    needsLogin = true
                    message = "自动登录失败：${recovery.message}。请手动登录一次。"
                }
            }
            isSyncing = false
        }
    }

    private fun reportSuccess(snapshot: com.ecut.campuspocket.data.remote.electricity.XgwxSnapshot) {
        val balance = snapshot.balance?.let { "¥%.2f".format(it) } ?: "--"
        message = "已同步 ${snapshot.houseLabel} · 电表余额 $balance"
    }

    fun clearMessage() {
        message = null
        needsLogin = false
    }

    companion object {
        private const val SYNC_THROTTLE_MS = 20_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ElectricityViewModel(AppContainer.xgwxElectricityRepository)
            }
        }
    }
}
