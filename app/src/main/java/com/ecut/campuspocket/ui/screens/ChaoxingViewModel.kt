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
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingSnapshot
import com.ecut.campuspocket.data.repository.ChaoxingRepository
import com.ecut.campuspocket.work.ChaoxingReminderWorker
import kotlinx.coroutines.launch

class ChaoxingViewModel(
    private val repository: ChaoxingRepository,
    private val preferences: AppPreferences,
    private val appContext: android.content.Context
) : ViewModel() {

    var loggedIn by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var snapshot by mutableStateOf<ChaoxingSnapshot?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var remindersEnabled by mutableStateOf(preferences.chaoxingReminders)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var lastRefreshAt = 0L

    init {
        refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        if (loading) return
        if (!force && System.currentTimeMillis() - lastRefreshAt < REFRESH_THROTTLE_MS) return
        lastRefreshAt = System.currentTimeMillis()
        viewModelScope.launch {
            loading = true
            error = null
            val hasSession = runCatching { repository.hasSession() }.getOrDefault(false)
            loggedIn = hasSession
            if (!hasSession) {
                loading = false
                return@launch
            }
            runCatching { repository.loadSnapshot() }.fold(
                onSuccess = { snapshot = it
                    error = null
                    message = if (it.warnings.isEmpty()) {
                        null
                    } else {
                        "部分课程读取失败：${it.warnings.take(2).joinToString("；")}"
                    }
                },
                onFailure = {
                    error = it.message ?: "学习通数据读取失败"
                    if (it is com.ecut.campuspocket.network.ChaoxingSessionExpiredException) {
                        loggedIn = false
                    }
                }
            )
            loading = false
        }
    }

    fun setReminders(enabled: Boolean) {
        remindersEnabled = enabled
        preferences.chaoxingReminders = enabled
        if (enabled) {
            ChaoxingReminderWorker.ensureScheduled(appContext)
        } else {
            ChaoxingReminderWorker.disable(appContext)
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { repository.logout() }
            loggedIn = false
            snapshot = null
            message = "已退出学习通"
        }
    }

    fun clearMessage() {
        message = null
        error = null
    }

    companion object {
        private const val REFRESH_THROTTLE_MS = 30_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChaoxingViewModel(
                    repository = AppContainer.chaoxingRepository,
                    preferences = AppContainer.preferences,
                    appContext = AppContainer.applicationContext
                )
            }
        }
    }
}
