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
import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.data.local.ExamEntity
import com.ecut.campuspocket.data.repository.ExamRepository
import com.ecut.campuspocket.data.repository.JwSyncManager
import com.ecut.campuspocket.data.repository.ScheduleRepository
import com.ecut.campuspocket.data.schedule.ParsedItemKind
import com.ecut.campuspocket.data.schedule.ParsedScheduleDraft
import com.ecut.campuspocket.data.schedule.ScheduleAdjustmentStore
import com.ecut.campuspocket.data.schedule.ScheduleDayAdjustment
import com.ecut.campuspocket.data.schedule.ScheduleExamFusion
import com.ecut.campuspocket.network.SessionExpiredException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val examRepository: ExamRepository,
    private val adjustmentStore: ScheduleAdjustmentStore,
    private val syncManager: JwSyncManager
) : ViewModel() {

    val courses: StateFlow<List<CourseEntity>> = repository.observeCourses()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val exams: StateFlow<List<ExamEntity>> = examRepository.observeExams()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val adjustments: StateFlow<List<ScheduleDayAdjustment>> = adjustmentStore.adjustments

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
            val result = syncManager.syncSchedule()
            result.fold(
                onSuccess = { count -> message = "已同步 $count 个课程块" },
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

    fun saveCustomCourse(
        id: Long = 0L,
        title: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        weeksSpec: String,
        room: String,
        campus: String,
        teacher: String
    ) {
        viewModelScope.launch {
            val entity = ScheduleExamFusion.buildCustomCourseEntity(
                id = id,
                title = title,
                dayOfWeek = dayOfWeek,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                weeksSpec = weeksSpec,
                room = room,
                campus = campus,
                teacher = teacher
            )
            repository.upsertCustomCourse(entity)
            message = if (id == 0L) "已添加自定义课程「${entity.title}」" else "已更新自定义课程「${entity.title}」"
        }
    }

    fun deleteCustomCourse(id: Long) {
        viewModelScope.launch {
            repository.deleteCourse(id)
            message = "已删除该自定义课程"
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
            examRepository.upsertCustomExam(entity)
            message = if (existingKey.isNullOrBlank()) {
                "已添加自定义考试「${entity.courseName}」，已同步显示在对应周课表"
            } else {
                "已更新自定义考试「${entity.courseName}」"
            }
        }
    }

    fun deleteCustomExam(key: String) {
        viewModelScope.launch {
            examRepository.deleteExam(key)
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
                if (d.kind == ParsedItemKind.COURSE) {
                    repository.upsertCustomCourse(
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
                } else {
                    examRepository.upsertCustomExam(
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
                }
            }
            val parts = buildList {
                if (courseCount > 0) add("$courseCount 节课程")
                if (examCount > 0) add("$examCount 场考试")
            }
            message = "审核确认完成，已加入 ${parts.joinToString("、")}"
        }
    }

    fun saveSwapAdjustment(
        targetDate: String,
        sourceDate: String,
        label: String,
        markSourceAsRest: Boolean
    ) {
        adjustmentStore.upsertSwap(targetDate, sourceDate, label, markSourceAsRest)
        message = if (markSourceAsRest) {
            "已设置 $targetDate 调休补上 $sourceDate 课表（原日期已标为放假）"
        } else {
            "已设置 $targetDate 调休补上 $sourceDate 课表"
        }
    }

    fun saveRestRangeAdjustment(startDate: String, endDate: String, label: String) {
        val count = adjustmentStore.upsertRestRange(startDate, endDate, label)
        if (count > 0) {
            message = "已设置 $count 天放假停课"
        }
    }

    fun saveAdjustmentsBatch(rules: List<ScheduleDayAdjustment>) {
        if (rules.isEmpty()) return
        adjustmentStore.upsertAll(rules)
        message = "已应用 ${rules.size} 条节假日调休规则"
    }

    fun deleteAdjustment(targetDate: String) {
        adjustmentStore.delete(targetDate)
        message = "已撤销 $targetDate 的调休设置"
    }

    fun clearAllAdjustments() {
        adjustmentStore.clearAll()
        message = "已清空全部节假日调休规则"
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
                ScheduleViewModel(
                    repository = AppContainer.scheduleRepository,
                    examRepository = AppContainer.examRepository,
                    adjustmentStore = AppContainer.scheduleAdjustments,
                    syncManager = AppContainer.jwSyncManager
                )
            }
        }
    }
}
