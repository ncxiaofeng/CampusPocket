package com.ecut.campuspocket.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ecut.campuspocket.data.SemesterConfig
import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.data.local.ExamEntity
import com.ecut.campuspocket.data.schedule.ParsedItemKind
import com.ecut.campuspocket.data.schedule.ParsedScheduleDraft
import com.ecut.campuspocket.data.schedule.ScheduleAdjustmentType
import com.ecut.campuspocket.data.schedule.ScheduleAdjustments
import com.ecut.campuspocket.data.schedule.ScheduleDayAdjustment
import com.ecut.campuspocket.data.schedule.ScheduleTextParser
import com.ecut.campuspocket.ui.theme.CourseAmber
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import java.time.LocalDate

private val dayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 点击「+」后的快捷菜单：剪贴板智能识别 / 截图离线识别 / 手动添加课程 / 手动添加考试。
 */
@Composable
fun AddScheduleEntryDialog(
    onDismiss: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportScreenshot: () -> Unit,
    onAddCourse: () -> Unit,
    onAddExam: () -> Unit,
    onHolidayAdjust: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("添加课表 / 考试") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "支持离线智能解析剪贴板通知或群聊截图，解析结果会先交给你核对修改后再保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionOptionRow(
                    badge = "贴",
                    title = "从剪贴板智能识别",
                    subtitle = "自动提取加课、调课或考试时间地点，审核后加入",
                    onClick = {
                        onDismiss()
                        onImportClipboard()
                    }
                )
                ActionOptionRow(
                    badge = "图",
                    title = "从截图离线识别（OCR）",
                    subtitle = "本地识别截图文字，不上传云端，审核后加入",
                    onClick = {
                        onDismiss()
                        onImportScreenshot()
                    }
                )
                ActionOptionRow(
                    badge = "课",
                    title = "手动添加自定义课程",
                    subtitle = "教务同步时自动保留，不会覆盖",
                    onClick = {
                        onDismiss()
                        onAddCourse()
                    }
                )
                ActionOptionRow(
                    badge = "考",
                    title = "手动添加自定义考试",
                    subtitle = "自动同步展示在对应周次课表与今日安排中",
                    onClick = {
                        onDismiss()
                        onAddExam()
                    }
                )
                if (onHolidayAdjust != null) {
                    ActionOptionRow(
                        badge = "调",
                        title = "节假日调休 / 调课",
                        subtitle = "设置放假停课或某日改上另一天的课，课表与今日同步生效",
                        onClick = {
                            onDismiss()
                            onHolidayAdjust()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun ActionOptionRow(
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(
            text = badge,
            color = when (badge) {
                "考" -> CourseRose
                "调" -> CourseAmber
                else -> TealPrimary
            },
            size = 34.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 手动新增 / 编辑自定义课程弹窗。
 */
@Composable
fun AddOrEditCourseDialog(
    initial: CourseEntity? = null,
    defaultWeek: Int = SemesterConfig.weekFor(),
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        title: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        weeksSpec: String,
        room: String,
        campus: String,
        teacher: String
    ) -> Unit,
    onDelete: ((Long) -> Unit)? = null
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var dayOfWeek by remember { mutableIntStateOf(initial?.dayOfWeek ?: (LocalDate.now().dayOfWeek.value - 1)) }
    var startPeriodText by remember {
        mutableStateOf(
            (initial?.startPeriod?.takeIf { it > 0 } ?: 1).toString()
        )
    }
    var endPeriodText by remember {
        mutableStateOf(
            (initial?.endPeriod?.takeIf { it > 0 } ?: 2).toString()
        )
    }
    var weeksSpec by remember {
        mutableStateOf(
            initial?.weeksSpec?.ifBlank { "第${defaultWeek}周" } ?: "第${defaultWeek}周"
        )
    }
    var campus by remember { mutableStateOf(initial?.campus.orEmpty()) }
    var room by remember { mutableStateOf(initial?.room.orEmpty()) }
    var teacher by remember { mutableStateOf(initial?.teacher.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        title = { Text(if (initial == null) "添加自定义课程" else "编辑自定义课程") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; error = null },
                    label = { Text("课程名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("星期", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    dayLabels.forEachIndexed { idx, label ->
                        val selected = dayOfWeek == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { dayOfWeek = idx }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label.removePrefix("周"),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startPeriodText,
                        onValueChange = { startPeriodText = it.filter(Char::isDigit).take(2) },
                        label = { Text("开始节次 (1-12)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endPeriodText,
                        onValueChange = { endPeriodText = it.filter(Char::isDigit).take(2) },
                        label = { Text("结束节次") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = weeksSpec,
                    onValueChange = { weeksSpec = it },
                    label = { Text("周次（如 1-16周 / 13-16周(单) / 第12周）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = campus,
                        onValueChange = { campus = it },
                        label = { Text("校区（可选）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("教室（如 3-201）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = { onDelete(initial.id); onDismiss() }) {
                        Text("删除", color = ErrorRed)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanTitle = title.trim()
                val sp = startPeriodText.toIntOrNull()?.coerceIn(1, 12) ?: 0
                val ep = endPeriodText.toIntOrNull()?.coerceIn(1, 12) ?: 0
                if (cleanTitle.isBlank()) {
                    error = "请填写课程名称"
                    return@Button
                }
                if (sp !in 1..12 || ep !in sp..12) {
                    error = "节次需在 1-12 之间且结束节次不小于开始节次"
                    return@Button
                }
                onSave(
                    initial?.id ?: 0L,
                    cleanTitle,
                    dayOfWeek,
                    sp,
                    ep,
                    weeksSpec.trim().ifBlank { "第${defaultWeek}周" },
                    room.trim(),
                    campus.trim(),
                    teacher.trim()
                )
                onDismiss()
            }) {
                Text("保存")
            }
        }
    )
}

/**
 * 手动新增 / 编辑自定义考试弹窗。
 */
@Composable
fun AddOrEditExamDialog(
    initial: ExamEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        existingKey: String?,
        courseName: String,
        examDate: String,
        startClock: String,
        endClock: String,
        location: String,
        campus: String,
        examType: String,
        seatNo: String,
        note: String
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val todayIso = remember { LocalDate.now().toString() }
    var courseName by remember { mutableStateOf(initial?.courseName.orEmpty()) }
    var examDate by remember { mutableStateOf(initial?.examDate?.ifBlank { todayIso } ?: todayIso) }
    var startClock by remember { mutableStateOf(initial?.startClock?.ifBlank { "14:00" } ?: "14:00") }
    var endClock by remember { mutableStateOf(initial?.endClock?.ifBlank { "16:00" } ?: "16:00") }
    var campus by remember { mutableStateOf(initial?.campus.orEmpty()) }
    var location by remember { mutableStateOf(initial?.location.orEmpty()) }
    var examType by remember { mutableStateOf(initial?.examType?.ifBlank { "期末考试" } ?: "期末考试") }
    var seatNo by remember { mutableStateOf(initial?.seatNo.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        title = { Text(if (initial == null) "添加自定义考试" else "编辑自定义考试") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it; error = null },
                    label = { Text("考试科目 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = examDate,
                    onValueChange = { examDate = it; error = null },
                    label = { Text("考试日期 *（yyyy-MM-dd，如 2026-12-15）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startClock,
                        onValueChange = { startClock = it },
                        label = { Text("开始时间（如 14:00）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endClock,
                        onValueChange = { endClock = it },
                        label = { Text("结束时间（如 16:00）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = campus,
                        onValueChange = { campus = it },
                        label = { Text("校区（可选）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("考场 / 教室") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = examType,
                        onValueChange = { examType = it },
                        label = { Text("考试类型") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seatNo,
                        onValueChange = { seatNo = it },
                        label = { Text("座位号（可选）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（如 闭卷 / 带计算器）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = { onDelete(initial.key); onDismiss() }) {
                        Text("删除", color = ErrorRed)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanName = courseName.trim()
                val cleanDate = examDate.trim()
                if (cleanName.isBlank()) {
                    error = "请填写考试科目"
                    return@Button
                }
                if (runCatching { LocalDate.parse(cleanDate) }.isFailure) {
                    error = "日期格式需为 yyyy-MM-dd，例如 2026-12-15"
                    return@Button
                }
                onSave(
                    initial?.key,
                    cleanName,
                    cleanDate,
                    startClock.trim(),
                    endClock.trim(),
                    location.trim(),
                    campus.trim(),
                    examType.trim().ifBlank { "自定义考试" },
                    seatNo.trim(),
                    note.trim()
                )
                onDismiss()
            }) {
                Text("保存")
            }
        }
    )
}

/**
 * 智能识别审核与编辑弹窗：
 * 展示剪贴板或离线 OCR 解析出的草稿列表，每条均可由用户逐项修改、切换类型或取消勾选，
 * 必须经用户点击「确认加入」后才写入本机数据库。
 */
@Composable
fun SmartImportReviewDialog(
    sourceLabel: String,
    initialRawText: String,
    defaultKindHint: ParsedItemKind? = null,
    onDismiss: () -> Unit,
    onConfirm: (List<ParsedScheduleDraft>) -> Unit
) {
    var rawText by remember { mutableStateOf(initialRawText) }
    var showRawEditor by remember { mutableStateOf(false) }
    var drafts by remember {
        mutableStateOf(
            ScheduleTextParser.parse(
                rawText = initialRawText,
                defaultKindHint = defaultKindHint
            )
        )
    }

    val selectedCount = drafts.count { it.selected && it.title.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "智能识别审核",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "$sourceLabel · 请核对修改后再加入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (drafts.isEmpty()) "暂未自动提取到条目" else "识别出 ${drafts.size} 条待审核",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showRawEditor = !showRawEditor },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (showRawEditor) "收起原文" else "编辑原文重解析",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (drafts.size >= 2) {
                    val allSelected = drafts.all { it.selected }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactBatchPill(
                            text = if (allSelected) "取消全选" else "全选",
                            active = false,
                            onClick = {
                                val next = !allSelected
                                drafts = drafts.map { it.copy(selected = next) }
                            }
                        )
                        CompactBatchPill(
                            text = "全设考试",
                            active = drafts.all { it.kind == ParsedItemKind.EXAM },
                            activeColor = CourseRose,
                            onClick = {
                                drafts = drafts.map { it.copy(kind = ParsedItemKind.EXAM) }
                            }
                        )
                        CompactBatchPill(
                            text = "全设加课",
                            active = drafts.all { it.kind == ParsedItemKind.COURSE },
                            activeColor = TealPrimary,
                            onClick = {
                                drafts = drafts.map { it.copy(kind = ParsedItemKind.COURSE) }
                            }
                        )
                    }
                }

                if (showRawEditor || drafts.isEmpty()) {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("识别文本（可修改错字后重新解析）") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                drafts = ScheduleTextParser.parse(
                                    rawText = rawText,
                                    defaultKindHint = defaultKindHint
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("重新识别")
                        }
                        OutlinedButton(
                            onClick = {
                                val today = LocalDate.now()
                                drafts = drafts + ParsedScheduleDraft(
                                    kind = defaultKindHint ?: ParsedItemKind.EXAM,
                                    title = "",
                                    dayOfWeek = today.dayOfWeek.value - 1,
                                    startPeriod = 1,
                                    endPeriod = 2,
                                    weeksSpec = "第${SemesterConfig.weekFor(today)}周",
                                    examDate = today.toString(),
                                    startClock = "14:00",
                                    endClock = "16:00"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ 手动补一条")
                        }
                    }
                }

                drafts.forEachIndexed { index, draft ->
                    DraftReviewCard(
                        index = index,
                        draft = draft,
                        onChange = { updated ->
                            drafts = drafts.toMutableList().also { it[index] = updated }
                        },
                        onRemove = {
                            drafts = drafts.toMutableList().also { it.removeAt(index) }
                        }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        confirmButton = {
            Button(
                enabled = selectedCount > 0,
                onClick = {
                    onConfirm(drafts.filter { it.selected && it.title.isNotBlank() })
                    onDismiss()
                }
            ) {
                Text("确认加入 ($selectedCount)")
            }
        }
    )
}

@Composable
private fun CompactBatchPill(
    text: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color = TealPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (active) activeColor.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun DraftReviewCard(
    index: Int,
    draft: ParsedScheduleDraft,
    onChange: (ParsedScheduleDraft) -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = draft.selected,
                onCheckedChange = { onChange(draft.copy(selected = it)) },
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "#${index + 1}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val isCourse = draft.kind == ParsedItemKind.COURSE
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isCourse) TealPrimary else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onChange(draft.copy(kind = ParsedItemKind.COURSE)) }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加课",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCourse) androidx.compose.ui.graphics.Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (!isCourse) CourseRose else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onChange(draft.copy(kind = ParsedItemKind.EXAM)) }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "考试",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!isCourse) androidx.compose.ui.graphics.Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = ErrorRed,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (draft.note.isNotBlank()) {
            Text(
                text = draft.note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        OutlinedTextField(
            value = draft.title,
            onValueChange = { onChange(draft.copy(title = it)) },
            label = { Text(if (draft.kind == ParsedItemKind.EXAM) "考试科目 *" else "课程名称 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (draft.kind == ParsedItemKind.COURSE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dayLabels.forEachIndexed { idx, label ->
                    val active = draft.dayOfWeek == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onChange(draft.copy(dayOfWeek = idx)) }
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label.removePrefix("周"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.startPeriod.toString(),
                    onValueChange = { s ->
                        val v = s.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, 12) ?: draft.startPeriod
                        onChange(draft.copy(startPeriod = v, endPeriod = maxOf(v, draft.endPeriod)))
                    },
                    label = { Text("开始节") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.endPeriod.toString(),
                    onValueChange = { s ->
                        val v = s.filter(Char::isDigit).toIntOrNull()?.coerceIn(draft.startPeriod, 12) ?: draft.endPeriod
                        onChange(draft.copy(endPeriod = v))
                    },
                    label = { Text("结束节") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.weeksSpec,
                    onValueChange = { onChange(draft.copy(weeksSpec = it)) },
                    label = { Text("周次") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.room,
                    onValueChange = { onChange(draft.copy(room = it)) },
                    label = { Text("教室") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.teacher,
                    onValueChange = { onChange(draft.copy(teacher = it)) },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            OutlinedTextField(
                value = draft.examDate,
                onValueChange = { onChange(draft.copy(examDate = it)) },
                label = { Text("考试日期（yyyy-MM-dd）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.startClock,
                    onValueChange = { onChange(draft.copy(startClock = it)) },
                    label = { Text("开始时间") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.endClock,
                    onValueChange = { onChange(draft.copy(endClock = it)) },
                    label = { Text("结束时间") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.campus,
                    onValueChange = { onChange(draft.copy(campus = it)) },
                    label = { Text("校区") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.room,
                    onValueChange = { onChange(draft.copy(room = it)) },
                    label = { Text("考场地点") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.examType,
                    onValueChange = { onChange(draft.copy(examType = it)) },
                    label = { Text("考试类型") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.seatNo,
                    onValueChange = { onChange(draft.copy(seatNo = it)) },
                    label = { Text("座位号") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = draft.note,
                onValueChange = { onChange(draft.copy(note = it)) },
                label = { Text("备注 / 班级") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 节假日调休 / 放假停课管理弹窗：
 * 支持「某日调休改上另一天课」「连续日期放假停课」「剪贴板识别放假调休通知」以及随时撤销。
 */
@Composable
fun HolidayAdjustmentDialog(
    adjustments: List<ScheduleDayAdjustment>,
    onReadClipboard: () -> String,
    onSaveSwap: (targetDate: String, sourceDate: String, label: String, markSourceAsRest: Boolean) -> Unit,
    onSaveRestRange: (startDate: String, endDate: String, label: String) -> Unit,
    onApplyBatch: (List<ScheduleDayAdjustment>) -> Unit,
    onDelete: (targetDate: String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var mode by remember { mutableIntStateOf(0) } // 0 = 调休补课, 1 = 放假停课
    var targetDate by remember { mutableStateOf(today.toString()) }
    var sourceDate by remember { mutableStateOf(today.plusDays(1).toString()) }
    var swapLabel by remember { mutableStateOf("节假日调休") }
    var markSourceAsRest by remember { mutableStateOf(true) }

    var restStart by remember { mutableStateOf(today.toString()) }
    var restEnd by remember { mutableStateOf(today.toString()) }
    var restLabel by remember { mutableStateOf("节假日放假") }

    var error by remember { mutableStateOf<String?>(null) }
    var pendingNoticeRules by remember { mutableStateOf<List<ScheduleDayAdjustment>>(emptyList()) }
    var noticeInfo by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("节假日调休 / 调课", maxLines = 1, softWrap = false)
                Text(
                    "无损覆盖课表与今日安排，教务同步不丢失、可随时撤销",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 模式切换：调休补课 / 放假停课
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("调休补课（改上某天课）", "放假停课（整天无课）").forEachIndexed { idx, label ->
                        val active = mode == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable { mode = idx; error = null }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // 一键从剪贴板识别学校放假调休通知
                OutlinedButton(
                    onClick = {
                        val raw = onReadClipboard().trim()
                        if (raw.isBlank()) {
                            noticeInfo = "剪贴板为空，请先复制学校放假调休通知"
                            pendingNoticeRules = emptyList()
                        } else {
                            val parsed = ScheduleAdjustments.parseHolidayNotice(raw, today)
                            val combined = (parsed.rests + parsed.swaps).distinctBy { it.targetDate }
                            if (combined.isEmpty()) {
                                noticeInfo = "未识别到标准放假/调休句式，可直接在下方手动填写日期"
                                pendingNoticeRules = emptyList()
                            } else {
                                pendingNoticeRules = combined
                                noticeInfo = "识别出 ${parsed.swaps.size} 天调休补课、${parsed.rests.size} 天放假停课，核对后点下方应用"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("从剪贴板智能识别放假/调休通知")
                }

                if (noticeInfo != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = noticeInfo.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (pendingNoticeRules.isNotEmpty()) {
                            pendingNoticeRules.take(8).forEach { r ->
                                val desc = if (r.type == ScheduleAdjustmentType.SWAP) {
                                    "调休：${ScheduleAdjustments.shortDateWithWeekday(r.targetDate)} → 上 ${ScheduleAdjustments.shortDateWithWeekday(r.sourceDate)} 的课"
                                } else {
                                    "放假：${ScheduleAdjustments.shortDateWithWeekday(r.targetDate)} 停课（${r.label}）"
                                }
                                Text(
                                    text = "• $desc",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (pendingNoticeRules.size > 8) {
                                Text(
                                    text = "… 等共 ${pendingNoticeRules.size} 天",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onApplyBatch(pendingNoticeRules)
                                        pendingNoticeRules = emptyList()
                                        noticeInfo = "已应用全部识别到的调休规则"
                                    }
                                ) {
                                    Text("确认应用 (${pendingNoticeRules.size})")
                                }
                                TextButton(onClick = { pendingNoticeRules = emptyList(); noticeInfo = null }) {
                                    Text("放弃")
                                }
                            }
                        }
                    }
                }

                if (mode == 0) {
                    OutlinedTextField(
                        value = targetDate,
                        onValueChange = { targetDate = it; error = null },
                        label = { Text("补课日期（哪天去上课，yyyy-MM-dd）") },
                        supportingText = {
                            Text("预览：${ScheduleAdjustments.formatDateWithWeek(targetDate)}")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sourceDate,
                        onValueChange = { sourceDate = it; error = null },
                        label = { Text("改上哪天的课（原课程日期，yyyy-MM-dd）") },
                        supportingText = {
                            Text("预览：${ScheduleAdjustments.formatDateWithWeek(sourceDate)}")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = swapLabel,
                        onValueChange = { swapLabel = it },
                        label = { Text("调休备注（如 国庆调休 / 五一调休）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { markSourceAsRest = !markSourceAsRest },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = markSourceAsRest,
                            onCheckedChange = { markSourceAsRest = it },
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "同时将原日期（$sourceDate）设为放假停课",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            val t = runCatching { LocalDate.parse(targetDate.trim()) }.getOrNull()
                            val s = runCatching { LocalDate.parse(sourceDate.trim()) }.getOrNull()
                            if (t == null || s == null) {
                                error = "请输入有效日期，格式如 2026-10-10"
                                return@Button
                            }
                            if (t == s) {
                                error = "补课日期与原课程日期不能相同"
                                return@Button
                            }
                            onSaveSwap(targetDate.trim(), sourceDate.trim(), swapLabel.trim(), markSourceAsRest)
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存此条调休补课")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = restStart,
                            onValueChange = { restStart = it; error = null },
                            label = { Text("开始日期") },
                            supportingText = { Text(ScheduleAdjustments.shortDateWithWeekday(restStart)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = restEnd,
                            onValueChange = { restEnd = it; error = null },
                            label = { Text("结束日期") },
                            supportingText = { Text(ScheduleAdjustments.shortDateWithWeekday(restEnd)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = restLabel,
                        onValueChange = { restLabel = it },
                        label = { Text("放假名称（如 国庆放假 / 清明节）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val s = runCatching { LocalDate.parse(restStart.trim()) }.getOrNull()
                            val e = runCatching { LocalDate.parse(restEnd.trim()) }.getOrNull()
                            if (s == null || e == null) {
                                error = "请输入有效日期，格式如 2026-10-01"
                                return@Button
                            }
                            onSaveRestRange(restStart.trim(), restEnd.trim(), restLabel.trim())
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存放假停课（单日或连休）")
                    }
                }

                if (error != null) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }

                // 已生效列表
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已生效调休（${adjustments.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (adjustments.isNotEmpty()) {
                        TextButton(onClick = onClearAll, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text("清空全部", color = ErrorRed)
                        }
                    }
                }

                if (adjustments.isEmpty()) {
                    Text(
                        text = "暂无节假日调休记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    adjustments.forEach { rule ->
                        val isSwap = rule.type == ScheduleAdjustmentType.SWAP
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSwap) TealPrimary.copy(alpha = 0.16f)
                                        else CourseAmber.copy(alpha = 0.18f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isSwap) "调" else "休",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSwap) TealPrimary else CourseAmber
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = ScheduleAdjustments.formatDateWithWeek(rule.targetDate),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isSwap) {
                                        "改上 ${ScheduleAdjustments.shortDateWithWeekday(rule.sourceDate)} 的课" +
                                            if (rule.label.isNotBlank()) " · ${rule.label}" else ""
                                    } else {
                                        rule.label.ifBlank { "放假停课" }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onDelete(rule.targetDate) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text("撤销", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
