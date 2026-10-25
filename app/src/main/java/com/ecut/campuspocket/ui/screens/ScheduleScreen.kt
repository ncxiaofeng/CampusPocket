package com.ecut.campuspocket.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.ClassTimes
import com.ecut.campuspocket.data.SemesterConfig
import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.ui.components.CourseBlock
import com.ecut.campuspocket.ui.components.ScheduleGrid
import com.ecut.campuspocket.ui.components.liquidGlassBottomBarContentInset
import com.ecut.campuspocket.ui.theme.ScheduleBlue
import com.ecut.campuspocket.ui.theme.ScheduleGreen
import com.ecut.campuspocket.ui.theme.ScheduleOrange
import com.ecut.campuspocket.ui.theme.SchedulePink
import com.ecut.campuspocket.ui.theme.SchedulePurple
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val today = LocalDate.now()
    val currentWeek = SemesterConfig.weekFor(today)
    var week by rememberSaveable { mutableIntStateOf(currentWeek) }
    var selectedBlock by remember { mutableStateOf<CourseBlock?>(null) }
    val entities by viewModel.courses.collectAsStateWithLifecycle()
    val usingDemo = entities.isEmpty()
    val blocks = remember(entities) {
        if (entities.isEmpty()) sampleBlocks else entities.map { it.toBlock() }
    }
    val visibleBlocks = remember(blocks, week) {
        blocks.filter { it.weeks.isEmpty() || week in it.weeks }
    }
    val weekStart = SemesterConfig.weekStart(week)
    val weekEnd = weekStart.plusDays(6)
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
                        append(if (usingDemo) "示例课表" else "${visibleBlocks.size} 个课程块")
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
                    .size(40.dp)
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
                        modifier = Modifier.size(21.dp)
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
                    onBlockClick = { selectedBlock = it }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    selectedBlock?.let { block ->
        CourseDetailDialog(block = block, onDismiss = { selectedBlock = null })
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
            .size(34.dp)
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
private fun CourseDetailDialog(block: CourseBlock, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        title = { Text(block.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
            modifier = Modifier.width(40.dp)
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

private fun CourseEntity.toBlock(): CourseBlock {
    val size = blockPalette.size
    val color = blockPalette[((colorSeed % size) + size) % size]
    val weeks = weeksCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    val start = startPeriod.takeIf { it > 0 } ?: (startSlot * 2 + 1)
    val end = endPeriod.takeIf { it >= start } ?: ((startSlot + slotSpan) * 2)
    return CourseBlock(
        dayOfWeek = dayOfWeek,
        startPeriod = start,
        endPeriod = end,
        title = title,
        room = room,
        campus = campus,
        teacher = teacher,
        color = color,
        weeks = weeks,
        weeksSpec = weeksSpec
    )
}
