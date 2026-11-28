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
import com.ecut.campuspocket.data.repository.ScheduleRepository
import com.ecut.campuspocket.data.schedule.ParsedItemKind
import com.ecut.campuspocket.data.schedule.ParsedScheduleDraft
import com.ecut.campuspocket.data.schedule.ScheduleExamFusion
import com.ecut.campuspocket.network.SessionExpiredException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExamsViewModel(
    private val repository: ExamRepository,
    private val scheduleRepository: ScheduleRepository,
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

    fun saveCustomExam(
        existingKey: String? = null,
        courseName: String,
        examDate: String,
        startClock: String,
        endClock: String,
        location: String,
        campus: String,
        examType: String,
        seatNo: String,
        note: String
    ) {
        viewModelScope.launch {
            val entity = ScheduleExamFusion.buildCustomExamEntity(
                existingKey = existingKey,
                courseName = courseName,
                examDate = examDate,
                startClock = startClock,
                endClock = endClock,
                location = location,
                campus = campus,
                examType = examType,
                seatNo = seatNo,
                note = note
            )
            repository.upsertCustomExam(entity)
            message = if (existingKey.isNullOrBlank()) {
                "已添加自定义考试「${entity.courseName}」"
            } else {
                "已更新自定义考试「${entity.courseName}」"
            }
        }
    }

    fun deleteCustomExam(key: String) {
        viewModelScope.launch {
            repository.deleteExam(key)
            message = "已删除该自定义考试"
        }
    }

    fun saveReviewedDrafts(drafts: List<ParsedScheduleDraft>) {
        val active = drafts.filter { it.selected && it.title.isNotBlank() }
        if (active.isEmpty()) return
        viewModelScope.launch {
            var courseCount = 0
            var examCount = 0
            active.forEach { d ->
                if (d.kind == ParsedItemKind.EXAM) {
                    repository.upsertCustomExam(
                        ScheduleExamFusion.buildCustomExamEntity(
                            courseName = d.title,
                            examDate = d.examDate,
                            startClock = d.startClock,
                            endClock = d.endClock,
                            location = d.room,
                            campus = d.campus,
                            examType = d.examType,
                            seatNo = d.seatNo,
                            note = d.note
                        )
                    )
                    examCount++
                } else {
                    scheduleRepository.upsertCustomCourse(
                        ScheduleExamFusion.buildCustomCourseEntity(
                            title = d.title,
                            dayOfWeek = d.dayOfWeek,
                            startPeriod = d.startPeriod,
                            endPeriod = d.endPeriod,
                            weeksSpec = d.weeksSpec,
                            room = d.room,
                            campus = d.campus,
                            teacher = d.teacher
                        )
                    )
                    courseCount++
                }
            }
            val parts = buildList {
                if (examCount > 0) add("$examCount 场考试")
                if (courseCount > 0) add("$courseCount 节课程")
            }
            message = "审核确认完成，已加入 ${parts.joinToString("、")}"
        }
    }

    fun showNotice(text: String) {
        needsLogin = false
        message = text
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
                    scheduleRepository = AppContainer.scheduleRepository,
                    syncManager = AppContainer.jwSyncManager
                )
            }
        }
    }
}
