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
import com.ecut.campuspocket.data.local.GradeEntity
import com.ecut.campuspocket.data.repository.GradeRepository
import com.ecut.campuspocket.data.repository.JwSyncManager
import com.ecut.campuspocket.network.SessionExpiredException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GradesViewModel(
    private val repository: GradeRepository,
    private val syncManager: JwSyncManager
) : ViewModel() {

    val grades: StateFlow<List<GradeEntity>> = repository.observeGrades()
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

    fun sync() {
        if (isSyncing) return
        viewModelScope.launch {
            isSyncing = true
            message = null
            needsLogin = false
            syncManager.syncGrades().fold(
                onSuccess = { count -> message = "已同步 $count 门成绩" },
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
                GradesViewModel(
                    repository = AppContainer.gradeRepository,
                    syncManager = AppContainer.jwSyncManager
                )
            }
        }
    }
}
