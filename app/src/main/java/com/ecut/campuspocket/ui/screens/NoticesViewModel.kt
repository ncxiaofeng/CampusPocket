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
import com.ecut.campuspocket.data.remote.jw.JwAdapter
import com.ecut.campuspocket.data.remote.jw.JwNotice
import com.ecut.campuspocket.network.SessionExpiredException
import kotlinx.coroutines.launch

class NoticesViewModel(
    private val jwAdapter: JwAdapter
) : ViewModel() {

    var notices by mutableStateOf<List<JwNotice>>(emptyList())
        private set

    /** 输入框里的文字；点“搜索”后才写入 [keyword] 并请求。 */
    var searchText by mutableStateOf("")
        private set

    var keyword by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var hasLoaded by mutableStateOf(false)
        private set

    var hasMore by mutableStateOf(false)
        private set

    var selectedNotice by mutableStateOf<JwNotice?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var needsLogin by mutableStateOf(false)
        private set

    private var page = 0
    private var pendingRefresh = false

    init {
        refresh()
    }

    fun updateSearchText(value: String) {
        searchText = value
    }

    fun search() {
        keyword = searchText.trim()
        requestRefresh()
    }

    fun clearSearch() {
        if (searchText.isEmpty() && keyword.isEmpty()) return
        searchText = ""
        keyword = ""
        requestRefresh()
    }

    fun refresh() {
        requestRefresh()
    }

    private fun requestRefresh() {
        if (isLoading || isLoadingMore) {
            // 首次加载还没回来时点搜索，等这次请求结束后按新关键词再拉一次。
            pendingRefresh = true
            return
        }
        load(reset = true)
    }

    fun loadMore() {
        if (!hasMore || isLoading || isLoadingMore) return
        load(reset = false)
    }

    fun openNotice(notice: JwNotice) {
        selectedNotice = notice
    }

    fun closeNotice() {
        selectedNotice = null
    }

    fun clearMessage() {
        message = null
        needsLogin = false
    }

    private fun load(reset: Boolean) {
        if (isLoading || isLoadingMore) return
        viewModelScope.launch {
            if (reset) isLoading = true else isLoadingMore = true
            message = null
            needsLogin = false
            val pageToLoad = if (reset) 0 else page + 1
            runCatching {
                jwAdapter.fetchNotices(
                    keyword = keyword,
                    page = pageToLoad,
                    pageSize = PAGE_SIZE
                )
            }.fold(
                onSuccess = { list ->
                    page = pageToLoad
                    notices = if (reset) {
                        list
                    } else {
                        val known = notices.mapTo(mutableSetOf()) { it.id }
                        notices + list.filter { it.id.isBlank() || known.add(it.id) }
                    }
                    hasMore = list.size >= PAGE_SIZE
                    hasLoaded = true
                    if (reset && list.isEmpty() && keyword.isNotBlank()) {
                        message = "没有找到标题包含“$keyword”的公告"
                    }
                },
                onFailure = { error ->
                    hasLoaded = true
                    needsLogin = error is SessionExpiredException
                    message = if (needsLogin) {
                        "教务系统登录已过期，请重新登录"
                    } else {
                        "公告加载失败：${error.message ?: "请检查校园网连接"}"
                    }
                }
            )
            isLoading = false
            isLoadingMore = false
            if (pendingRefresh) {
                pendingRefresh = false
                load(reset = true)
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 10

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NoticesViewModel(jwAdapter = AppContainer.jwAdapter)
            }
        }
    }
}
