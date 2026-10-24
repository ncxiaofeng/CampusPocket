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
import com.ecut.campuspocket.data.remote.card.CampusCardBill
import com.ecut.campuspocket.data.remote.card.CampusCardClient
import com.ecut.campuspocket.data.remote.card.CampusCardOverview
import com.ecut.campuspocket.network.SessionExpiredException
import kotlinx.coroutines.launch

class CampusCardViewModel(
    private val client: CampusCardClient
) : ViewModel() {

    var overview by mutableStateOf<CampusCardOverview?>(null)
        private set

    var bills by mutableStateOf<List<CampusCardBill>>(emptyList())
        private set

    var total by mutableStateOf(0)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var needsLogin by mutableStateOf(false)
        private set

    private var nextPage = 2

    init {
        load()
    }

    fun load() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            message = null
            needsLogin = false
            runCatching {
                val overview = client.fetchOverview()
                val page = client.fetchBills(page = 1, pageSize = PAGE_SIZE)
                overview to page
            }.fold(
                onSuccess = { (overview, page) ->
                    this@CampusCardViewModel.overview = overview
                    bills = page.items
                    total = page.total
                    nextPage = 2
                },
                onFailure = { error ->
                    needsLogin = error is SessionExpiredException
                    message = if (needsLogin) {
                        "请先打开校园卡官方页完成统一认证"
                    } else {
                        "读取失败：${error.message ?: "请检查网络连接"}"
                    }
                }
            )
            isLoading = false
        }
    }

    fun loadMore() {
        if (isLoadingMore || isLoading || bills.size >= total) return
        viewModelScope.launch {
            isLoadingMore = true
            runCatching { client.fetchBills(page = nextPage, pageSize = PAGE_SIZE) }
                .onSuccess { page ->
                    bills = bills + page.items
                    total = page.total
                    nextPage += 1
                }
                .onFailure { error ->
                    needsLogin = error is SessionExpiredException
                    message = if (needsLogin) {
                        "请先打开校园卡官方页完成统一认证"
                    } else {
                        "加载更多失败：${error.message ?: "请检查网络连接"}"
                    }
                }
            isLoadingMore = false
        }
    }

    fun clearMessage() {
        message = null
        needsLogin = false
    }

    companion object {
        private const val PAGE_SIZE = 30

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CampusCardViewModel(client = AppContainer.campusCardClient)
            }
        }
    }
}
