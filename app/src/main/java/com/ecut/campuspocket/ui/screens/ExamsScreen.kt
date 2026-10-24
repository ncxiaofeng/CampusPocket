package com.ecut.campuspocket.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.local.ExamEntity
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
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
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val todayIso = remember(today) { today.toString() }
    val upcoming = remember(exams, todayIso) {
        exams.filter { it.examDate.isBlank() || it.examDate >= todayIso }
    }
    val past = remember(exams, todayIso) {
        exams.filter { it.examDate.isNotBlank() && it.examDate < todayIso }.reversed()
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
                        ExamGroup(title = "即将考试", exams = upcoming, today = today)
                    }
                }
                if (past.isNotEmpty()) {
                    item(key = "past") {
                        ExamGroup(title = "已结束", exams = past, today = today)
                    }
                }

                item {
                    Text(
                        text = "考试安排来自教务系统，只读缓存在本机；考场与座位若有调整，请以教务页面为准。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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
private fun ExamGroup(title: String, exams: List<ExamEntity>, today: LocalDate) {
    Column {
        SectionHeader(title = title, action = "${exams.size} 场")
        Spacer(Modifier.height(10.dp))
        AppCard(contentPadding = 0.dp) {
            Column {
                exams.forEachIndexed { index, exam ->
                    ExamRow(exam = exam, today = today)
                    if (index != exams.lastIndex) {
                        ThinDivider(Modifier.padding(start = 78.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamRow(exam: ExamEntity, today: LocalDate) {
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
                Spacer(Modifier.width(8.dp))
                StatusChip(daysUntil = daysUntil, isUpcoming = isUpcoming)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (exam.examName.isNotBlank()) append(exam.examName)
                    if (exam.examType.isNotBlank()) {
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
                        "本学期暂无考试记录。教务发布后点“同步”即可更新。"
                    } else {
                        "登录教务系统后点“同步”，本学期考场与座位号会加密缓存在本机。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSync) { Text("同步考试安排") }
            }
        }
    }
}

private fun formatDateLabel(isoDate: String): String {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    return date.format(DateTimeFormatter.ofPattern("MM月dd日 E", Locale.CHINA))
}
