package com.ecut.campuspocket.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.ClassTimes
import com.ecut.campuspocket.data.SemesterConfig
import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.ui.components.CourseBlock
import com.ecut.campuspocket.ui.components.ScheduleGrid
import com.ecut.campuspocket.ui.theme.CourseAmber
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.CourseLavender
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.TealPrimary

private val sampleBlocks = listOf(
    CourseBlock(0, 1, 2, "高等数学", "A101", "广兰校园", "张老师", CourseTeal),
    CourseBlock(0, 5, 6, "大学物理", "B203", "广兰校园", "李老师", CourseBlue),
    CourseBlock(1, 3, 4, "程序设计基础", "实验楼302", "广兰校园", "王老师", CourseLavender),
    CourseBlock(1, 9, 10, "体育", "东操场", "广兰校园", "", CourseAmber),
    CourseBlock(2, 1, 2, "高等数学", "A101", "广兰校园", "张老师", CourseTeal),
    CourseBlock(2, 5, 6, "大学英语", "C105", "广兰校园", "赵老师", CourseRose),
    CourseBlock(3, 3, 4, "线性代数", "A204", "广兰校园", "孙老师", CourseBlue),
    CourseBlock(3, 7, 8, "思政理论", "理论楼", "广兰校园", "周老师", CourseAmber),
    CourseBlock(4, 1, 2, "数据结构", "实验楼201", "广兰校园", "吴老师", CourseLavender),
    CourseBlock(4, 9, 10, "艺术鉴赏", "艺术楼", "广兰校园", "", CourseRose)
)

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    onOpenLogin: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory)
) {
    val currentWeek = SemesterConfig.weekFor()
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
    val periodCount = remember(visibleBlocks) {
        val used = visibleBlocks.maxOfOrNull { it.endPeriod } ?: ClassTimes.regularPeriods
        used.coerceIn(ClassTimes.regularPeriods, 12)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background
                    ),
                    startY = 0f,
                    endY = 900f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "课表",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = if (usingDemo) {
                            "示例课表 · 登录后同步真实数据"
                        } else {
                            "教务系统 · 东华理工大学"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                TextButton(onClick = { viewModel.sync() }, enabled = !viewModel.isSyncing) {
                    if (viewModel.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp
                        )
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
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 6.dp
                            )
                        ) { Text("去登录") }
                    } else {
                        TextButton(
                            onClick = { viewModel.clearMessage() },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 6.dp
                            )
                        ) { Text("知道了") }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            WeekSwitcher(
                week = week,
                isCurrent = week == currentWeek,
                onPrevious = { if (week > 1) week-- },
                onNext = { if (week < SemesterConfig.totalWeeks) week++ },
                onBackToNow = { week = currentWeek }
            )
            Spacer(Modifier.height(10.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    ScheduleGrid(
                        blocks = visibleBlocks,
                        weekStart = weekStart,
                        periodCount = periodCount,
                        rowHeight = 52.dp,
                        highlightToday = week == currentWeek,
                        onBlockClick = { selectedBlock = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(TealPrimary)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "第 $week 周 ${visibleBlocks.size} 个课程块 · 左右切换周次 · 上下滚动看 9-10 节",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (usingDemo) {
                    "点右上角「同步」从教务系统拉取课表"
                } else {
                    "课表已保存在本机，断网也能查看"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }
    }

    selectedBlock?.let { block ->
        CourseDetailDialog(block = block, onDismiss = { selectedBlock = null })
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

@Composable
private fun WeekSwitcher(
    week: Int,
    isCurrent: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackToNow: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
                    .clickable(onClick = onPrevious),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "第 $week 周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isCurrent) {
                    Text(
                        text = "本周",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "点击回到本周",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onBackToNow)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center
            ) {
                Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val blockPalette = listOf(CourseTeal, CourseBlue, CourseAmber, CourseLavender, CourseRose)

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
