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
import com.ecut.campuspocket.data.remote.library.LibrarySeatClient
import com.ecut.campuspocket.data.remote.library.LibrarySeatSnapshot
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class SeatTimeMode { ALL_DAY, NOW }

class LibrarySeatsViewModel(
    private val client: LibrarySeatClient
) : ViewModel() {

    var snapshot by mutableStateOf<LibrarySeatSnapshot?>(null)
        private set

    var selectedDate by mutableStateOf(LocalDate.now().toString())
        private set

    var selectedCampusId by mutableStateOf("")
        private set

    var mode by mutableStateOf(SeatTimeMode.ALL_DAY)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            message = null
            val timeRange = if (mode == SeatTimeMode.NOW) currentTimeRange() else null
            runCatching {
                client.fetchAvailability(
                    day = selectedDate,
                    startTime = timeRange?.first,
                    endTime = timeRange?.second
                )
            }.fold(
                onSuccess = { loaded ->
                    snapshot = loaded
                    if (loaded.dates.isNotEmpty() && selectedDate !in loaded.dates) {
                        selectedDate = loaded.dates.first()
                    }
                    if (loaded.campuses.none { it.id == selectedCampusId }) {
                        selectedCampusId = loaded.campuses.firstOrNull()?.id.orEmpty()
                    }
                },
                onFailure = { error ->
                    message = "空位加载失败：${error.message ?: "请检查网络连接"}"
                }
            )
            isLoading = false
        }
    }

    fun selectDate(date: String) {
        if (date == selectedDate) return
        selectedDate = date
        load()
    }

    fun selectCampus(id: String) {
        selectedCampusId = id
    }

    fun selectMode(next: SeatTimeMode) {
        if (next == mode) return
        mode = next
        load()
    }

    /** 现在 → 闭馆；已过 22:00 则按全天处理。 */
    private fun currentTimeRange(): Pair<String, String>? {
        val now = LocalTime.now()
        if (now.hour >= 22) return null
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return now.format(formatter) to "22:00"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibrarySeatsViewModel(client = AppContainer.librarySeatClient)
            }
        }
    }
}
