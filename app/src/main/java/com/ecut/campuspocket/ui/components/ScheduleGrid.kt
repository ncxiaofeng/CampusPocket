package com.ecut.campuspocket.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.data.ClassTimes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

data class CourseBlock(
    val dayOfWeek: Int,          // 0 = 周一
    val startPeriod: Int,        // 1 = 第 1 节
    val endPeriod: Int,
    val title: String,
    val room: String,
    val campus: String = "",
    val teacher: String = "",
    val color: Color,
    /** 空集合表示每周都显示（示例数据）。 */
    val weeks: Set<Int> = emptySet(),
    /** 原始周次文本，例如 4-9周。 */
    val weeksSpec: String = ""
)

private val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun ScheduleGrid(
    blocks: List<CourseBlock>,
    modifier: Modifier = Modifier,
    periodCount: Int = ClassTimes.regularPeriods,
    rowHeight: Dp = 52.dp,
    weekStart: LocalDate = LocalDate.now().minusDays((LocalDate.now().dayOfWeek.value - 1).toLong()),
    highlightToday: Boolean = true,
    onBlockClick: (CourseBlock) -> Unit = {}
) {
    val labelWidth = 46.dp
    val headerHeight = 44.dp
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val strongGridColor = MaterialTheme.colorScheme.outlineVariant
    val todayIndex = if (highlightToday) LocalDate.now().dayOfWeek.value - 1 else -1
    val monday = weekStart
    val nowPosition = if (highlightToday) currentPeriodPosition(LocalTime.now()) else null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(headerHeight)) {
            Box(Modifier.width(labelWidth).height(headerHeight))
            dayNames.forEachIndexed { index, name ->
                val date = monday.plusDays(index.toLong())
                Column(
                    modifier = Modifier.weight(1f).height(headerHeight),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (index == todayIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (index == todayIndex) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Text(
                        text = "${date.monthValue}/${date.dayOfMonth}",
                        fontSize = 8.sp,
                        color = if (index == todayIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * periodCount)
        ) {
            val cellWidth = (maxWidth - labelWidth) / 7

            Canvas(Modifier.fillMaxSize()) {
                val rowPx = rowHeight.toPx()
                val labelPx = labelWidth.toPx()
                val cellPx = cellWidth.toPx()
                for (row in 0..periodCount) {
                    if (row >= periodCount) break
                    val y = rowPx * row
                    val strong = row == 4 || row == 8
                    drawLine(
                        color = if (strong) strongGridColor else gridColor,
                        start = Offset(labelPx, y),
                        end = Offset(size.width, y),
                        strokeWidth = if (strong) 1.6f else 1f
                    )
                }
                for (col in 0..7) {
                    val x = labelPx + cellPx * col
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                }
            }

            // 左侧节次与上下课时间
            Column(Modifier.width(labelWidth).height(rowHeight * periodCount)) {
                for (period in 1..periodCount) {
                    val start = ClassTimes.startTime(period)
                    val end = ClassTimes.endTime(period)
                    Box(
                        Modifier
                            .width(labelWidth)
                            .height(rowHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$period",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (start != null && end != null) {
                                Text(
                                    text = start,
                                    fontSize = 7.sp,
                                    lineHeight = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = end,
                                    fontSize = 7.sp,
                                    lineHeight = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            } else {
                                Text(
                                    text = "夜间",
                                    fontSize = 7.sp,
                                    lineHeight = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            // 当前时间线
            nowPosition?.let { (periodIndex, fraction) ->
                Canvas(Modifier.fillMaxSize()) {
                    val y = (rowHeight * periodIndex + rowHeight * fraction).toPx()
                    val labelPx = labelWidth.toPx()
                    drawLine(
                        color = Color(0xFFE53935),
                        start = Offset(labelPx, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(
                        color = Color(0xFFE53935),
                        radius = 3.dp.toPx(),
                        center = Offset(labelPx, y)
                    )
                }
            }

            // 课程块
            blocks.forEach { block ->
                val startPeriod = block.startPeriod.coerceIn(1, periodCount)
                val endPeriod = block.endPeriod.coerceIn(startPeriod, periodCount)
                val span = endPeriod - startPeriod + 1
                Box(
                    modifier = Modifier
                        .offset(
                            x = labelWidth + cellWidth * block.dayOfWeek + 2.dp,
                            y = rowHeight * (startPeriod - 1) + 2.dp
                        )
                        .width(cellWidth - 4.dp)
                        .height(rowHeight * span - 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(block.color)
                        .clickable { onBlockClick(block) }
                        .padding(horizontal = 3.dp, vertical = 3.dp)
                ) {
                    Column {
                        Text(
                            text = block.title,
                            fontSize = 9.sp,
                            lineHeight = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D3531),
                            maxLines = if (span >= 2) 5 else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (block.campus.isNotBlank() || block.room.isNotBlank()) {
                            Text(
                                text = buildString {
                                    if (block.campus.isNotBlank()) {
                                        append("@")
                                        append(
                                            block.campus
                                                .removeSuffix("校园")
                                                .removeSuffix("校区")
                                        )
                                    }
                                    if (block.room.isNotBlank()) {
                                        if (isNotEmpty()) append(" ")
                                        append(block.room)
                                    }
                                },
                                fontSize = 7.sp,
                                lineHeight = 8.5.sp,
                                color = Color(0xFF1D3531).copy(alpha = 0.72f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (span >= 2 && block.teacher.isNotBlank()) {
                            Text(
                                text = block.teacher,
                                fontSize = 6.5.sp,
                                lineHeight = 8.5.sp,
                                color = Color(0xFF1D3531).copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 返回 (第几节的下标 0..9, 该节内进度 0..1)；不在上课时间返回 null。 */
private fun currentPeriodPosition(now: LocalTime): Pair<Int, Float>? {
    ClassTimes.periods.forEachIndexed { index, (startText, endText) ->
        val start = runCatching { LocalTime.parse(startText) }.getOrNull()
            ?: return@forEachIndexed
        val end = runCatching { LocalTime.parse(endText) }.getOrNull()
            ?: return@forEachIndexed
        if (now >= start && now <= end) {
            val total = Duration.between(start, end).seconds.toFloat().coerceAtLeast(1f)
            val elapsed = Duration.between(start, now).seconds.toFloat()
            return index to (elapsed / total).coerceIn(0f, 1f)
        }
    }
    return null
}
