package com.ecut.campuspocket.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.local.ExamEntity
import com.ecut.campuspocket.data.ocr.OfflineOcrRecognizer
import com.ecut.campuspocket.data.schedule.ParsedItemKind
import com.ecut.campuspocket.ui.components.AddOrEditExamDialog
import com.ecut.campuspocket.ui.components.AddScheduleEntryDialog
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.SmartImportReviewDialog
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ExamsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: ExamsViewModel = viewModel(factory = ExamsViewModel.Factory)
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val todayIso = remember(today) { today.toString() }
    val upcoming = remember(exams, todayIso) {
        exams.filter { it.examDate.isBlank() || it.examDate >= todayIso }
    }
    val past = remember(exams, todayIso) {
        exams.filter { it.examDate.isNotBlank() && it.examDate < todayIso }.reversed()
    }

    var showAddMenu by remember { mutableStateOf(false) }
    var showAddExamDialog by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<ExamEntity?>(null) }
    var reviewPayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ocrLoading by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Text("考试安排", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { showAddMenu = true },
                enabled = !ocrLoading
            ) {
                if (ocrLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "添加或识别考试",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            TextButton(onClick = { viewModel.sync() }, enabled = !viewModel.isSyncing) {
                if (viewModel.isSyncing) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("同步中")
                } else {
                    Text("同步")
                }
            }
        }

        if (viewModel.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.message.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (viewModel.needsLogin) ErrorRed else TealPrimary,
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

        if (exams.isEmpty()) {
            EmptyExams(
                hasSynced = viewModel.hasSynced,
                onSync = { viewModel.sync() },
                onAddOrImport = { showAddMenu = true },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { ExamSummary(upcoming = upcoming, total = exams.size, today = today) }

                if (upcoming.isNotEmpty()) {
                    item(key = "upcoming") {
                        ExamGroup(
                            title = "即将考试",
                            exams = upcoming,
                            today = today,
                            onEditCustom = { editingExam = it }
                        )
                    }
                }
                if (past.isNotEmpty()) {
                    item(key = "past") {
                        ExamGroup(
                            title = "已结束",
                            exams = past,
                            today = today,
                            onEditCustom = { editingExam = it }
                        )
                    }
                }

                item {
                    Text(
                        text = "支持教务同步与自定义添加（点自定义条目可编辑/删除）；所有考试均会自动融合展示在对应周课表上。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (showAddMenu) {
        AddScheduleEntryDialog(
            onDismiss = { showAddMenu = false },
            onImportClipboard = {
                val clipText = clipboard.getText()?.text.orEmpty().trim()
                if (clipText.isBlank()) {
                    viewModel.showNotice("剪贴板为空，请先复制考试或加课通知消息")
                } else {
                    reviewPayload = "剪贴板消息" to clipText
                }
            },
            onImportScreenshot = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onAddCourse = {
                viewModel.showNotice("可在「课表」页右上角 + 添加自定义课程，或直接粘贴文本智能识别")
            },
            onAddExam = { showAddExamDialog = true }
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
            defaultKindHint = ParsedItemKind.EXAM,
            onDismiss = { reviewPayload = null },
            onConfirm = { approved -> viewModel.saveReviewedDrafts(approved) }
        )
    }
}

@Composable
private fun ExamSummary(upcoming: List<ExamEntity>, total: Int, today: LocalDate) {
    val next = upcoming.firstOrNull { it.examDate.isNotBlank() }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (next != null) "最近一场" else "待考安排",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (next != null) {
                        next.courseName
                    } else {
                        "暂无待考课程"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (next != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(formatDateLabel(next.examDate))
                            if (next.startClock.isNotBlank()) {
                                append(" · ")
                                append(next.startClock)
                                if (next.endClock.isNotBlank()) append("-").append(next.endClock)
                            }
                            if (next.location.isNotBlank()) append(" · ").append(next.location)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${upcoming.size}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "场待考 / 共 $total 场",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            IconBadge(text = "考", color = TealPrimary, size = 44.dp)
        }
    }
}

@Composable
private fun ExamGroup(
    title: String,
    exams: List<ExamEntity>,
    today: LocalDate,
    onEditCustom: (ExamEntity) -> Unit
) {
    Column {
        SectionHeader(title = title, action = "${exams.size} 场")
        Spacer(Modifier.height(10.dp))
        AppCard(contentPadding = 0.dp) {
            Column {
                exams.forEachIndexed { index, exam ->
                    ExamRow(
                        exam = exam,
                        today = today,
                        onClick = if (exam.isCustom) ({ onEditCustom(exam) }) else null
                    )
                    if (index != exams.lastIndex) {
                        ThinDivider(Modifier.padding(start = 78.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamRow(
    exam: ExamEntity,
    today: LocalDate,
    onClick: (() -> Unit)? = null
) {
    val examDate = remember(exam.examDate) {
        runCatching { LocalDate.parse(exam.examDate) }.getOrNull()
    }
    val daysUntil = examDate?.let { ChronoUnit.DAYS.between(today, it) }
    val isUpcoming = daysUntil == null || daysUntil >= 0
    val accent = if (isUpcoming) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        DateBadge(examDate = examDate, accent = accent)

        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exam.courseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                if (exam.isCustom) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(CourseRose.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "自定义",
                            style = MaterialTheme.typography.labelSmall,
                            color = CourseRose
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                StatusChip(daysUntil = daysUntil, isUpcoming = isUpcoming)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (exam.examName.isNotBlank()) append(exam.examName)
                    if (exam.examType.isNotBlank() && exam.examType != exam.examName) {
                        if (isNotEmpty()) append(" · ")
                        append(exam.examType)
                    }
                    if (isBlank()) append("考试")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    val timeRange = when {
                        exam.startClock.isNotBlank() && exam.endClock.isNotBlank() ->
                            "${exam.startClock}-${exam.endClock}"
                        exam.startClock.isNotBlank() -> exam.startClock
                        exam.examTime.isNotBlank() -> exam.examTime
                        else -> ""
                    }
                    if (timeRange.isNotBlank()) append(timeRange)
                    val room = listOf(exam.campus, exam.location)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (room.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(room)
                    }
                }.ifBlank { "时间地点待定" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val details = buildList {
                if (exam.seatNo.isNotBlank()) add("座位 ${exam.seatNo}")
                if (exam.ticketNo.isNotBlank()) add("准考证 ${exam.ticketNo}")
                if (exam.note.isNotBlank()) add(exam.note)
                if (exam.isCustom) add("点此编辑")
            }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DateBadge(examDate: LocalDate?, accent: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .background(
                color = accent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (examDate == null) {
            Text(
                text = "待定",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            return@Column
        }
        Text(
            text = examDate.format(DateTimeFormatter.ofPattern("MM月", Locale.CHINA)),
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
        Text(
            text = examDate.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = examDate.format(DateTimeFormatter.ofPattern("E", Locale.CHINA)),
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

@Composable
private fun StatusChip(daysUntil: Long?, isUpcoming: Boolean) {
    val text = when {
        !isUpcoming -> "已结束"
        daysUntil == 0L -> "今天"
        daysUntil == 1L -> "明天"
        daysUntil != null && daysUntil <= 7 -> "${daysUntil}天后"
        else -> "待考"
    }
    val color = if (isUpcoming) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun EmptyExams(
    hasSynced: Boolean,
    onSync: () -> Unit,
    onAddOrImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AppCard(modifier = Modifier.padding(horizontal = 24.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                IconBadge(text = "考", color = CourseRose, size = 54.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (hasSynced) "教务暂未发布考试安排" else "还没有考试安排",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (hasSynced) {
                        "可先点下方按钮手动添加，或通过剪贴板/截图离线智能识别群内考试通知。"
                    } else {
                        "可从教务同步，也可手动添加或从剪贴板/截图智能识别，并自动显示在课表上。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onAddOrImport) { Text("添加 / 智能识别") }
                    OutlinedButton(onClick = onSync) { Text("同步教务") }
                }
            }
        }
    }
}

private fun formatDateLabel(isoDate: String): String {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    return date.format(DateTimeFormatter.ofPattern("MM月dd日 E", Locale.CHINA))
}
