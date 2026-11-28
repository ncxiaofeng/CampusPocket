package com.ecut.campuspocket.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.ClassTimes
import com.ecut.campuspocket.data.SemesterConfig
import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.data.local.ExamEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ecut.campuspocket.data.ocr.OfflineOcrRecognizer
import com.ecut.campuspocket.data.schedule.ScheduleAdjustmentType
import com.ecut.campuspocket.data.schedule.ScheduleAdjustments
import com.ecut.campuspocket.data.schedule.ScheduleExamFusion
import com.ecut.campuspocket.ui.components.AddOrEditCourseDialog
import com.ecut.campuspocket.ui.components.AddOrEditExamDialog
import com.ecut.campuspocket.ui.components.AddScheduleEntryDialog
import com.ecut.campuspocket.ui.components.CourseBlock
import com.ecut.campuspocket.ui.components.HolidayAdjustmentDialog
import com.ecut.campuspocket.ui.components.ScheduleGrid
import com.ecut.campuspocket.ui.components.SmartImportReviewDialog
import com.ecut.campuspocket.ui.components.liquidGlassBottomBarContentInset
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.ScheduleBlue
import com.ecut.campuspocket.ui.theme.ScheduleGreen
import com.ecut.campuspocket.ui.theme.ScheduleOrange
import com.ecut.campuspocket.ui.theme.SchedulePink
import com.ecut.campuspocket.ui.theme.SchedulePurple
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ExamHighlightColor = Color(0xFFD93848)

private val sampleBlocks = listOf(
    CourseBlock(0, 1, 2, "高等数学", "A101", "广兰校园", "张老师", SchedulePink),
    CourseBlock(0, 5, 6, "大学物理", "B203", "广兰校园", "李老师", ScheduleBlue),
    CourseBlock(1, 3, 4, "程序设计基础", "实验楼302", "广兰校园", "王老师", ScheduleGreen),
    CourseBlock(1, 9, 10, "体育", "东操场", "广兰校园", "", ScheduleOrange),
    CourseBlock(2, 1, 2, "高等数学", "A101", "广兰校园", "张老师", SchedulePink),
    CourseBlock(2, 5, 6, "大学英语", "C105", "广兰校园", "赵老师", SchedulePurple),
    CourseBlock(3, 3, 4, "线性代数", "A204", "广兰校园", "孙老师", ScheduleBlue),
    CourseBlock(3, 7, 8, "思政理论", "理论楼", "广兰校园", "周老师", ScheduleOrange),
    CourseBlock(4, 1, 2, "数据结构", "实验楼201", "广兰校园", "吴老师", ScheduleGreen),
    CourseBlock(4, 9, 10, "艺术鉴赏", "艺术楼", "广兰校园", "", SchedulePurple)
)

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    onOpenLogin: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory)
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val currentWeek = SemesterConfig.weekFor(today)
    var week by rememberSaveable { mutableIntStateOf(currentWeek) }
    var selectedBlock by remember { mutableStateOf<CourseBlock?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddCourseDialog by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var showAddExamDialog by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<ExamEntity?>(null) }
    var showHolidayDialog by remember { mutableStateOf(false) }
    var reviewPayload by remember { mutableStateOf<Pair<String, String>?>(null) } // (sourceLabel, rawText)
    var ocrLoading by remember { mutableStateOf(false) }

    val entities by viewModel.courses.collectAsStateWithLifecycle()
    val examEntities by viewModel.exams.collectAsStateWithLifecycle()
    val adjustments by viewModel.adjustments.collectAsStateWithLifecycle()
    val adjustmentsByDate = remember(adjustments) {
        adjustments.associateBy { it.targetDate }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ocrLoading = true
        viewModel.showNotice("正在本地离线识别截图文字…")
        scope.launch {
            OfflineOcrRecognizer.recognizeFromUri(context, uri).fold(
                onSuccess = { text ->
                    ocrLoading = false
                    viewModel.clearMessage()
                    if (text.isBlank()) {
                        viewModel.showNotice("未从截图中识别到文字，请换一张清晰截图或改用剪贴板导入")
                    } else {
                        reviewPayload = "截图离线识别" to text
                    }
                },
                onFailure = { err ->
                    ocrLoading = false
                    viewModel.showNotice("截图识别失败：${err.message ?: "请重试"}")
                }
            )
        }
    }

    val weekStart = SemesterConfig.weekStart(week)
    val weekEnd = weekStart.plusDays(6)

    val usingDemo = entities.isEmpty() && examEntities.isEmpty()
    val weekAdjustments = remember(weekStart, adjustmentsByDate) {
        ScheduleAdjustments.adjustmentsInWeek(weekStart, adjustmentsByDate)
    }
    val weekCourseBlocks = remember(entities, weekStart, adjustmentsByDate, usingDemo) {
        if (usingDemo) {
            sampleBlocks
        } else {
            ScheduleAdjustments.resolveWeekCourses(entities, weekStart, adjustmentsByDate)
                .map { resolved ->
                    resolved.course.toBlock(
                        overrideDayOfWeek = resolved.displayDayOfWeek,
                        adjustedTag = resolved.adjustedTag,
                        adjustedDetail = resolved.adjustedDetail
                    )
                }
        }
    }
    val weekExamBlocks = remember(examEntities, weekStart) {
        examEntities.mapNotNull { exam -> exam.toScheduleBlockOrNull(weekStart) }
    }
    val visibleBlocks = remember(weekCourseBlocks, weekExamBlocks) {
        weekCourseBlocks + weekExamBlocks
    }

    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA) }
    val shortDateFormatter = remember { DateTimeFormatter.ofPattern("M/d", Locale.CHINA) }
    val todayName = remember(today) {
        listOf("一", "二", "三", "四", "五", "六", "日")[today.dayOfWeek.value - 1]
    }
    val periodCount = remember(visibleBlocks) {
        val used = visibleBlocks.maxOfOrNull { it.endPeriod } ?: ClassTimes.regularPeriods
        used.coerceIn(ClassTimes.regularPeriods, 12)
    }
    val showCampus = remember(visibleBlocks) {
        visibleBlocks.map { it.campus }.filter { it.isNotBlank() }.toSet().size > 1
    }
    val scrollState = rememberScrollState()
    val isCurrentWeek = week == currentWeek

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
            .padding(bottom = liquidGlassBottomBarContentInset())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 10.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "第 $week 周",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (isCurrentWeek) "周$todayName" else "回到本周",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = if (isCurrentWeek) {
                            Modifier
                        } else {
                            Modifier.clickable { week = currentWeek }
                        }
                    )
                }
                Text(
                    text = buildString {
                        append(weekStart.format(fullDateFormatter))
                        append(" - ")
                        append(weekEnd.format(shortDateFormatter))
                        append(" · ")
                        if (usingDemo) {
                            append("示例课表")
                        } else {
                            append("${weekCourseBlocks.size} 课")
                            if (weekExamBlocks.isNotEmpty()) {
                                append(" · ${weekExamBlocks.size} 考")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            WeekArrow(
                icon = Icons.Outlined.ChevronLeft,
                label = "上一周",
                enabled = week > 1,
                onClick = { if (week > 1) week-- }
            )
            WeekArrow(
                icon = Icons.Outlined.ChevronRight,
                label = "下一周",
                enabled = week < SemesterConfig.totalWeeks,
                onClick = { if (week < SemesterConfig.totalWeeks) week++ }
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !ocrLoading) { showAddMenu = true },
                contentAlignment = Alignment.Center
            ) {
                if (ocrLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "添加或智能识别",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !viewModel.isSyncing) { viewModel.sync() },
                contentAlignment = Alignment.Center
            ) {
                if (viewModel.isSyncing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "同步课表",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (viewModel.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.message.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (viewModel.needsLogin) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.weight(1f)
                )
                if (viewModel.needsLogin) {
                    TextButton(
                        onClick = {
                            viewModel.clearMessage()
                            onOpenLogin()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("去登录") }
                } else {
                    TextButton(
                        onClick = { viewModel.clearMessage() },
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("知道了") }
                }
            }
        }

        if (weekAdjustments.isNotEmpty()) {
            val summary = weekAdjustments.values.joinToString(" · ") { adj ->
                if (adj.type == ScheduleAdjustmentType.SWAP) {
                    "${ScheduleAdjustments.shortDateWithWeekday(adj.targetDate)} 调上 ${ScheduleAdjustments.shortDateWithWeekday(adj.sourceDate)}"
                } else {
                    "${ScheduleAdjustments.shortDateWithWeekday(adj.targetDate)} 休"
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .clickable { showHolidayDialog = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本周调休：$summary",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "管理",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val headerHeight = 58.dp
            val available = maxHeight
            val rowHeight = ((available - headerHeight) / periodCount)
                .coerceIn(48.dp, 76.dp)
            val needsScroll = headerHeight + rowHeight * periodCount > available
            Column(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (needsScroll) Modifier.verticalScroll(scrollState)
                        else Modifier
                    )
            ) {
                ScheduleGrid(
                    blocks = visibleBlocks,
                    weekStart = weekStart,
                    periodCount = periodCount,
                    rowHeight = rowHeight,
                    headerHeight = headerHeight,
                    highlightToday = isCurrentWeek,
                    showCampus = showCampus,
                    dayAdjustments = weekAdjustments,
                    onBlockClick = { selectedBlock = it }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    selectedBlock?.let { block ->
        BlockDetailDialog(
            block = block,
            onDismiss = { selectedBlock = null },
            onEditCustom = {
                selectedBlock = null
                if (block.isExam) {
                    editingExam = examEntities.firstOrNull { it.key == block.examKey }
                } else {
                    editingCourse = entities.firstOrNull { it.id == block.courseId }
                }
            },
            onDeleteCustom = {
                selectedBlock = null
                if (block.isExam) {
                    block.examKey?.let { viewModel.deleteCustomExam(it) }
                } else {
                    block.courseId?.let { viewModel.deleteCustomCourse(it) }
                }
            }
        )
    }

    if (showAddMenu) {
        AddScheduleEntryDialog(
            onDismiss = { showAddMenu = false },
            onImportClipboard = {
                val clipText = clipboard.getText()?.text.orEmpty().trim()
                if (clipText.isBlank()) {
                    viewModel.showNotice("剪贴板为空，请先复制加课或考试通知消息")
                } else {
                    reviewPayload = "剪贴板消息" to clipText
                }
            },
            onImportScreenshot = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onAddCourse = { showAddCourseDialog = true },
            onAddExam = { showAddExamDialog = true },
            onHolidayAdjust = { showHolidayDialog = true }
        )
    }

    if (showHolidayDialog) {
        HolidayAdjustmentDialog(
            adjustments = adjustments,
            onReadClipboard = { clipboard.getText()?.text.orEmpty() },
            onSaveSwap = { target, source, label, markRest ->
                viewModel.saveSwapAdjustment(target, source, label, markRest)
            },
            onSaveRestRange = { start, end, label ->
                viewModel.saveRestRangeAdjustment(start, end, label)
            },
            onApplyBatch = { rules ->
                viewModel.saveAdjustmentsBatch(rules)
            },
            onDelete = { target ->
                viewModel.deleteAdjustment(target)
            },
            onClearAll = {
                viewModel.clearAllAdjustments()
            },
            onDismiss = { showHolidayDialog = false }
        )
    }

    if (showAddCourseDialog || editingCourse != null) {
        AddOrEditCourseDialog(
            initial = editingCourse,
            defaultWeek = week,
            onDismiss = {
                showAddCourseDialog = false
                editingCourse = null
            },
            onSave = { id, title, dayOfWeek, sp, ep, weeksSpec, room, campus, teacher ->
                viewModel.saveCustomCourse(
                    id = id,
                    title = title,
                    dayOfWeek = dayOfWeek,
                    startPeriod = sp,
                    endPeriod = ep,
                    weeksSpec = weeksSpec,
                    room = room,
                    campus = campus,
                    teacher = teacher
                )
            },
            onDelete = { id -> viewModel.deleteCustomCourse(id) }
        )
    }

    if (showAddExamDialog || editingExam != null) {
        AddOrEditExamDialog(
            initial = editingExam,
            onDismiss = {
                showAddExamDialog = false
                editingExam = null
            },
            onSave = { key, courseName, examDate, startClock, endClock, location, campus, examType, seatNo, note ->
                viewModel.saveCustomExam(
                    existingKey = key,
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
            },
            onDelete = { key -> viewModel.deleteCustomExam(key) }
        )
    }

    reviewPayload?.let { (sourceLabel, rawText) ->
        SmartImportReviewDialog(
            sourceLabel = sourceLabel,
            initialRawText = rawText,
            onDismiss = { reviewPayload = null },
            onConfirm = { approved -> viewModel.saveReviewedDrafts(approved) }
        )
    }
}

@Composable
private fun WeekArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
            },
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun BlockDetailDialog(
    block: CourseBlock,
    onDismiss: () -> Unit,
    onEditCustom: () -> Unit,
    onDeleteCustom: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = if (block.isCustom) {
            {
                Row {
                    TextButton(onClick = onDeleteCustom) {
                        Text("删除", color = ErrorRed)
                    }
                    TextButton(onClick = onEditCustom) {
                        Text("编辑")
                    }
                }
            }
        } else {
            null
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        title = {
            Text(
                text = if (block.isExam) "【考试】${block.title}" else block.title
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (block.isExam) {
                    if (block.examDate.isNotBlank()) {
                        DetailRow(label = "日期", value = block.examDate)
                    }
                    DetailRow(
                        label = "时间",
                        value = block.examClockRange.ifBlank {
                            "第 ${block.startPeriod}-${block.endPeriod} 节"
                        }
                    )
                    if (block.campus.isNotBlank() || block.room.isNotBlank()) {
                        DetailRow(
                            label = "考场",
                            value = listOf(block.campus, block.room)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                        )
                    }
                    if (block.examType.isNotBlank()) {
                        DetailRow(label = "类型", value = block.examType)
                    }
                    if (block.seatNo.isNotBlank()) {
                        DetailRow(label = "座位", value = block.seatNo)
                    }
                    if (block.note.isNotBlank()) {
                        DetailRow(label = "备注", value = block.note)
                    }
                    DetailRow(
                        label = "来源",
                        value = if (block.isCustom) "自定义考试（教务同步不覆盖）" else "教务系统"
                    )
                } else {
                    DetailRow(
                        label = "时间",
                        value = buildString {
                            append("第 ${block.startPeriod}-${block.endPeriod} 节")
                            val start = ClassTimes.startTime(block.startPeriod)
                            val end = ClassTimes.endTime(block.endPeriod)
                            if (start != null && end != null) {
                                append(" · ").append(start).append("-").append(end)
                            }
                        }
                    )
                    if (block.campus.isNotBlank() || block.room.isNotBlank()) {
                        DetailRow(
                            label = "地点",
                            value = listOf(block.campus, block.room)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                        )
                    }
                    if (block.teacher.isNotBlank()) {
                        DetailRow(label = "教师", value = block.teacher)
                    }
                    DetailRow(
                        label = "周次",
                        value = block.weeksSpec.ifBlank {
                            if (block.weeks.isEmpty()) {
                                "每周"
                            } else {
                                "第 ${block.weeks.sorted().joinToString(",")} 周"
                            }
                        }
                    )
                    if (block.adjustedDetail.isNotBlank()) {
                        DetailRow(label = "调休", value = block.adjustedDetail)
                    }
                    if (block.isCustom) {
                        DetailRow(label = "来源", value = "自定义课程（教务同步不覆盖）")
                    }
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(42.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val blockPalette = listOf(
    SchedulePink,
    ScheduleBlue,
    ScheduleGreen,
    ScheduleOrange,
    SchedulePurple
)

private fun CourseEntity.toBlock(
    overrideDayOfWeek: Int = dayOfWeek,
    adjustedTag: String = "",
    adjustedDetail: String = ""
): CourseBlock {
    val size = blockPalette.size
    val color = blockPalette[((colorSeed % size) + size) % size]
    val weeks = weeksCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    val start = startPeriod.takeIf { it > 0 } ?: (startSlot * 2 + 1)
    val end = endPeriod.takeIf { it >= start } ?: ((startSlot + slotSpan) * 2)
    return CourseBlock(
        dayOfWeek = overrideDayOfWeek,
        startPeriod = start,
        endPeriod = end,
        title = title,
        room = room,
        campus = campus,
        teacher = teacher,
        color = color,
        weeks = weeks,
        weeksSpec = weeksSpec,
        isCustom = isCustom,
        isExam = false,
        courseId = id,
        adjustedTag = adjustedTag,
        adjustedDetail = adjustedDetail
    )
}

private fun ExamEntity.toScheduleBlockOrNull(weekStart: LocalDate): CourseBlock? {
    val (dayOfWeek, startPeriod, endPeriod) =
        ScheduleExamFusion.examSlotInWeek(this, weekStart) ?: return null
    val clockRange = when {
        startClock.isNotBlank() && endClock.isNotBlank() -> "$startClock-$endClock"
        startClock.isNotBlank() -> startClock
        else -> ""
    }
    return CourseBlock(
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        title = courseName,
        room = location,
        campus = campus,
        color = ExamHighlightColor,
        isCustom = isCustom,
        isExam = true,
        examKey = key,
        examDate = examDate,
        examClockRange = clockRange,
        examType = examType.ifBlank { examName },
        seatNo = seatNo,
        note = note
    )
}
