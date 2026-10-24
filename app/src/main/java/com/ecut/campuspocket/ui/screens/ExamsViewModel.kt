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
import com.ecut.campuspocket.data.local.ExamEntity
import com.ecut.campuspocket.data.repository.ExamRepository
import com.ecut.campuspocket.data.repository.JwSyncManager
import com.ecut.campuspocket.network.SessionExpiredException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExamsViewModel(
    private val repository: ExamRepository,
    private val syncManager: JwSyncManager
) : ViewModel() {

    val exams: StateFlow<List<ExamEntity>> = repository.observeExams()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    var isSyncing by mutableStateOf(false)
        private set

    /** 是否已经成功同步过（用于区分“未同步”和“教务确实没有考试”）。 */
    var hasSynced by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var needsLogin by mutableStateOf(false)
        private set

    fun sync() {
        if (isSyncing) return
        viewModelScope.launch {
            isSyncing = true
            message = null
            needsLogin = false
            syncManager.syncExams().fold(
                onSuccess = { count ->
                    hasSynced = true
                    message = if (count == 0) {
                        "教务系统尚未发布本学期考试安排"
                    } else {
                        "已同步 $count 场考试"
                    }
                },
                onFailure = { error ->
                    needsLogin = error is SessionExpiredException
                    message = if (needsLogin) {
                        "教务系统登录已过期，请重新登录"
                    } else {
                        "同步失败：${error.message ?: "请检查校园网连接"}"
                    }
                }
            )
            isSyncing = false
        }
    }

    fun clearMessage() {
        message = null
        needsLogin = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExamsViewModel(
                    repository = AppContainer.examRepository,
                    syncManager = AppContainer.jwSyncManager
                )
            }
        }
    }
}
