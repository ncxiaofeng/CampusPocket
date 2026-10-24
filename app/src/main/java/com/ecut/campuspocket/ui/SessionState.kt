package com.ecut.campuspocket.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局会话状态。Adapter 捕获 SessionExpiredException 后调用 reportExpired()，
 * 根布局显示红色警告条并提供重新授权入口。
 */
object SessionState {
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    fun reportExpired() {
        _expired.value = true
    }

    fun clear() {
        _expired.value = false
    }
}
