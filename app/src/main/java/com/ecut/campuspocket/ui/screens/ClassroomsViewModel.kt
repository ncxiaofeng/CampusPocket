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
import com.ecut.campuspocket.data.SemesterConfig
import com.ecut.campuspocket.data.remote.jw.JwAdapter
import com.ecut.campuspocket.data.remote.jw.JwClassroom
import com.ecut.campuspocket.data.remote.jw.JwClassroomFilters
import com.ecut.campuspocket.network.SessionExpiredException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class ClassroomsViewModel(
    private val jwAdapter: JwAdapter
) : ViewModel() {

    var filters by mutableStateOf<JwClassroomFilters?>(null)
        private set

    var selectedBuildingId by mutableStateOf("")
        private set

    var selectedWeek by mutableStateOf(
        SemesterConfig.weekFor(LocalDate.now()).coerceIn(1, 20)
    )
        private set

    var selectedWeekday by mutableStateOf(LocalDate.now().dayOfWeek.value)
        private set

    var selectedPeriods by mutableStateOf(defaultPeriods())
        private set

    var classrooms by mutableStateOf<List<JwClassroom>>(emptyList())
        private set

    var isQuerying by mutableStateOf(false)
        private set

    var hasQueried by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var needsLogin by mutableStateOf(false)
        private set

    init {
        loadFilters()
    }

    fun selectCampus(campusId: String) {
        val loaded = filters ?: return
        if (campusId == loaded.campusId || isQuerying) return
        val campus = loaded.campuses.firstOrNull { it.id == campusId } ?: return
        selectedBuildingId = ""
        filters = loaded.copy(
            campusId = campus.id,
            campusName = campus.name,
            buildings = emptyList()
        )
        viewModelScope.launch {
            isQuerying = true
            message = null
            needsLogin = false
            runCatching {
                jwAdapter.fetchClassroomBuildings(loaded.xnm, loaded.xqm, campus.id)
            }.fold(
                onSuccess = { buildings ->
                    filters = filters?.copy(buildings = buildings)
                    isQuerying = false
                    query()
                },
                onFailure = { error ->
                    isQuerying = false
                    needsLogin = error is SessionExpiredException
                    message = if (needsLogin) {
                        "教务系统登录已过期，请重新登录"
                    } else {
                        "楼号加载失败：${error.message ?: "请检查校园网连接"}"
                    }
                }
            )
        }
    }

    fun selectBuilding(id: String) {
        selectedBuildingId = id
    }

    fun selectWeek(week: Int) {
        selectedWeek = week.coerceIn(1, 20)
    }

    fun selectWeekday(weekday: Int) {
        selectedWeekday = weekday.coerceIn(1, 7)
    }

    fun togglePeriod(period: Int) {
        val next = selectedPeriods.toMutableSet()
        if (!next.add(period)) next.remove(period)
        if (next.isEmpty()) return
        selectedPeriods = next
    }

    fun loadFilters() {
        if (isQuerying) return
        viewModelScope.launch {
            isQuerying = true
            message = null
            needsLogin = false
            runCatching { jwAdapter.fetchClassroomFilters() }.fold(
                onSuccess = { loaded ->
                    filters = loaded
                    selectedBuildingId = ""
                    isQuerying = false
                    if (loaded.buildings.none { it.id == "" }) {
                        selectedBuildingId = loaded.buildings.firstOrNull()?.id.orEmpty()
                    }
                    query()
                },
                onFailure = { error ->
                    isQuerying = false
                    needsLogin = error is SessionExpiredException
                    message = if (needsLogin) {
                        "教务系统登录已过期，请重新登录"
                    } else {
                        "教室列表加载失败：${error.message ?: "请检查校园网连接"}"
                    }
                }
            )
        }
    }

    fun query() {
        val loaded = filters ?: return
        if (isQuerying) return
        viewModelScope.launch {
            isQuerying = true
            message = null
            needsLogin = false
            val mask = selectedPeriods.fold(0) { acc, period ->
                acc or (1 shl (period - 1))
            }
            runCatching {
                jwAdapter.queryClassrooms(
                    filters = loaded,
                    buildingId = selectedBuildingId,
                    week = selectedWeek,
                    weekday = selectedWeekday,
                    periodMask = mask
                )
            }.fold(
                onSuccess = { list ->
                    classrooms = list
                    hasQueried = true
                    if (list.isEmpty()) {
                        message = "这个时间段没有符合条件的空教室"
                    }
                },
                onFailure = { error ->
                    needsLogin = error is SessionExpiredException
                    message = if (needsLogin) {
                        "教务系统登录已过期，请重新登录"
                    } else {
                        "查询失败：${error.message ?: "请检查校园网连接"}"
                    }
                }
            )
            isQuerying = false
        }
    }

    fun clearMessage() {
        message = null
        needsLogin = false
    }

    /** 与课表一致：每两节一个时间块，默认查当前时间块。 */
    private fun defaultPeriods(): Set<Int> {
        val now = LocalTime.now()
        val ends = listOf(
            LocalTime.of(9, 40),
            LocalTime.of(11, 40),
            LocalTime.of(15, 40),
            LocalTime.of(17, 40),
            LocalTime.of(20, 40),
            LocalTime.of(22, 40)
        )
        val index = ends.indexOfFirst { now <= it }.let { if (it < 0) ends.lastIndex else it }
        val start = index * 2 + 1
        return setOf(start, start + 1)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ClassroomsViewModel(jwAdapter = AppContainer.jwAdapter)
            }
        }
    }
}
