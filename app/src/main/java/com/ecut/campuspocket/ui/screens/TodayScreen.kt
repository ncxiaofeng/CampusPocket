package com.ecut.campuspocket.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.ClassTimes
import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.network.CampusNetworkState
import com.ecut.campuspocket.network.CampusNetworkStatus
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.MiniBarChart
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.StatusDot
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseAmber
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.CourseLavender
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TodayCourse(
    val start: String,
    val end: String,
    val name: String,
    val room: String,
    val color: Color
)

private val coursePalette = listOf(CourseTeal, CourseBlue, CourseAmber, CourseLavender, CourseRose)

@Composable
fun TodayScreen(
    onOpenElectricity: () -> Unit,
    onOpenCampusCard: () -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    vpnAuthenticated: Boolean = false,
    scheduleViewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory),
    electricityViewModel: ElectricityViewModel = viewModel(factory = ElectricityViewModel.Factory)
) {
    val today = remember { LocalDate.now() }
    val dateText = remember {
        today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    }
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..18 -> "下午好"
            else -> "晚上好"
        }
    }
    val courses by scheduleViewModel.courses.collectAsStateWithLifecycle()
    val electricityDays by electricityViewModel.days.collectAsStateWithLifecycle()
    val todayIndex = today.dayOfWeek.value - 1
    val currentWeek = com.ecut.campuspocket.data.SemesterConfig.weekFor(today)
    val todayCourses = remember(courses, todayIndex, currentWeek) {
        courses.filter { it.dayOfWeek == todayIndex && it.isActiveInWeek(currentWeek) }
            .sortedBy { if (it.startPeriod > 0) it.startPeriod else it.startSlot * 2 + 1 }
            .map { it.toTodayCourse() }
    }
    val latestDay = electricityDays.lastOrNull()
    val networkState by CampusNetworkStatus.state.collectAsStateWithLifecycle()

    val dayByEpoch = remember(electricityDays) {
        electricityDays.associateBy { it.epochDay }
    }
    val yesterdayDay = dayByEpoch[today.minusDays(1).toEpochDay()]
    val chartDays = remember(today, dayByEpoch) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to dayByEpoch[date.toEpochDay()]
        }
    }
    val chartLabels = chartDays.map { "${it.first.monthValue}/${it.first.dayOfMonth}" }
    val chartValues = chartDays.map { it.second?.totalKwh?.toFloat() ?: 0f }
    val hasChartData = chartValues.any { it > 0f }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { TodayHeader(greeting, dateText, networkState, vpnAuthenticated) }

        item {
            AppCard(onClick = onOpenSchedule) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(
                                text = "今日课程",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "第 ${com.ecut.campuspocket.data.SemesterConfig.weekFor(today)} 周 · 周${
                                    listOf("一", "二", "三", "四", "五", "六", "日")[todayIndex]
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (todayCourses.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${todayCourses.size} 门",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (todayCourses.isEmpty()) {
                        Text(
                            text = "今天暂无课程",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "登录教务系统并同步后，课表会显示在这里",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        todayCourses.forEachIndexed { index, course ->
                            if (index > 0) {
                                ThinDivider(Modifier.padding(vertical = 10.dp))
                            }
                            CourseRow(course)
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionHeader(
                    title = "电费",
                    action = "详情 ›",
                    onAction = onOpenElectricity
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElectricityMiniCard(
                        badge = "电",
                        badgeColor = WarningAmber,
                        title = "昨日用电",
                        value = yesterdayDay?.totalKwh?.let { "%.1f".format(it) } ?: "--",
                        unit = "度 · 昨日",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenElectricity
                    )
                    ElectricityMiniCard(
                        badge = "余",
                        badgeColor = TealPrimary,
                        title = "电表余额",
                        value = latestDay?.balance?.let { "%.2f".format(it) } ?: "--",
                        valueUnit = "元",
                        unit = "元 · 余额",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenElectricity
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (latestDay?.balance != null) {
                            "电表余额 ¥${"%.2f".format(latestDay.balance)} · 更新于 ${formatTime(latestDay.updatedAt)}"
                        } else {
                            "尚未同步电费数据"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            AppCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "近 7 天用电",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (hasChartData) {
                                "合计 ${"%.1f".format(chartValues.sum())} 度"
                            } else {
                                "暂无记录"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    if (hasChartData) {
                        MiniBarChart(values = chartValues, labels = chartLabels)
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "接入电费查询后，这里会显示每天的用电柱状图",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            AppCard(onClick = onOpenCampusCard, containerColor = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "卡",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "校园卡",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        Text(
                            text = "余额与消费流水",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Text(
                        text = "›",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseRow(course: TodayCourse) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(52.dp)) {
            Text(
                text = course.start,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = course.end,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .background(course.color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = course.room,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun CourseEntity.isActiveInWeek(week: Int): Boolean {
    if (weeksCsv.isBlank()) return true
    return weeksCsv.split(",").any { it.trim().toIntOrNull() == week }
}

private fun CourseEntity.toTodayCourse(): TodayCourse {
    val start = startPeriod.takeIf { it > 0 } ?: (startSlot * 2 + 1)
    val end = endPeriod.takeIf { it >= start } ?: ((startSlot + slotSpan) * 2)
    val size = coursePalette.size
    return TodayCourse(
        start = ClassTimes.startTime(start) ?: "--:--",
        end = ClassTimes.endTime(end) ?: "--:--",
        name = title,
        room = listOf(campus, room)
            .filter { it.isNotBlank() }
            .joinToString(" "),
        color = coursePalette[((colorSeed % size) + size) % size]
    )
}

private fun formatTime(millis: Long): String {
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

@Composable
private fun TodayHeader(
    greeting: String,
    dateText: String,
    networkState: CampusNetworkState,
    vpnAuthenticated: Boolean
) {
    val (networkLabel, networkColor) = when {
        networkState is CampusNetworkState.OnCampus ->
            "校园网" to MaterialTheme.colorScheme.primary
        networkState is CampusNetworkState.OffCampus && vpnAuthenticated ->
            "零信任已认证" to MaterialTheme.colorScheme.primary
        networkState is CampusNetworkState.OffCampus ->
            "未连接校园网" to MaterialTheme.colorScheme.error
        networkState is CampusNetworkState.Checking ->
            "检测中" to MaterialTheme.colorScheme.onSurfaceVariant
        else ->
            "校园网检测" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            text = "$greeting，同学",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .background(
                        networkColor.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(networkColor, size = 5.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = networkLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = networkColor
                )
            }
        }
    }
}

@Composable
private fun ElectricityMiniCard(
    badge: String,
    badgeColor: Color,
    title: String,
    value: String,
    unit: String,
    valueUnit: String = "度",
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppCard(modifier = modifier, onClick = onClick, contentPadding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = badge, color = badgeColor, size = 32.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = valueUnit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}
