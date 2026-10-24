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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.remote.library.LibrarySeatArea
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.ChoiceChip
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LibrarySeatsScreen(
    onBack: () -> Unit,
    onOpenBooking: () -> Unit,
    viewModel: LibrarySeatsViewModel = viewModel(factory = LibrarySeatsViewModel.Factory)
) {
    val snapshot = viewModel.snapshot
    val selectedCampus = snapshot?.campuses?.firstOrNull { it.id == viewModel.selectedCampusId }
    val floors = remember(selectedCampus) {
        selectedCampus?.children.orEmpty().sortedBy { it.sort }
    }
    val dates = snapshot?.dates.orEmpty().ifEmpty { listOf(LocalDate.now().toString()) }

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
            Text("图书馆空位", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { viewModel.load() }, enabled = !viewModel.isLoading) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("刷新中")
                } else {
                    Text("刷新")
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
                    color = ErrorRed,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { viewModel.load() },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) { Text("重试") }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "filters") {
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(text = "图", color = CourseTeal, size = 30.dp)
                            Spacer(Modifier.width(9.dp))
                            Text("查询条件", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = when (viewModel.mode) {
                                    SeatTimeMode.NOW -> "现在 → 22:00"
                                    SeatTimeMode.ALL_DAY -> "全天"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "日期",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(dates) { date ->
                                ChoiceChip(
                                    text = dateLabel(date),
                                    selected = date == viewModel.selectedDate,
                                    onClick = { viewModel.selectDate(date) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChoiceChip(
                                text = "现在",
                                selected = viewModel.mode == SeatTimeMode.NOW,
                                onClick = { viewModel.selectMode(SeatTimeMode.NOW) }
                            )
                            ChoiceChip(
                                text = "全天",
                                selected = viewModel.mode == SeatTimeMode.ALL_DAY,
                                onClick = { viewModel.selectMode(SeatTimeMode.ALL_DAY) }
                            )
                        }
                    }
                }
            }

            val campuses = snapshot?.campuses.orEmpty()
            if (campuses.isNotEmpty()) {
                item(key = "campuses") {
                    Column {
                        SectionHeader(title = "校区")
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(campuses) { campus ->
                                ChoiceChip(
                                    text = campus.name,
                                    selected = campus.id == viewModel.selectedCampusId,
                                    onClick = { viewModel.selectCampus(campus.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (selectedCampus != null) {
                item(key = "summary") {
                    CampusSummaryCard(selectedCampus)
                }
                item(key = "floors") {
                    Column {
                        SectionHeader(
                            title = "${selectedCampus.name} · 楼层/区域",
                            action = "${floors.size} 个"
                        )
                        Spacer(Modifier.height(10.dp))
                        if (floors.isEmpty()) {
                            AppCard {
                                Text(
                                    text = "这个校区暂无区域数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            AppCard(contentPadding = 0.dp) {
                                Column {
                                    floors.forEachIndexed { index, floor ->
                                        FloorRow(floor)
                                        if (index != floors.lastIndex) {
                                            ThinDivider(Modifier.padding(start = 16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (viewModel.isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "正在查询空位…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "booking") {
                Column {
                    Button(
                        onClick = onOpenBooking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("去官方页面预约")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "空位数据来自图书馆空间预约系统，免登录查询；预约、签到与取消在官方页面完成，App 不保存图书馆密码。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CampusSummaryCard(campus: LibrarySeatArea) {
    val rate = if (campus.total > 0) campus.free.toFloat() / campus.total else 0f
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = campus.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "空闲 ${campus.free} / 共 ${campus.total}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${campus.free}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "个空位",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
                IconBadge(text = "座", color = TealPrimary, size = 42.dp)
            }
            Spacer(Modifier.height(14.dp))
            RatioBar(free = campus.free, total = campus.total)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "空闲率 ${"%.0f".format(rate * 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FloorRow(floor: LibrarySeatArea) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = floor.name,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "空闲 ${floor.free} / 共 ${floor.total}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            RatioBar(free = floor.free, total = floor.total)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = "${floor.free}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RatioBar(free: Int, total: Int) {
    val fraction = if (total > 0) (free.toFloat() / total).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                RoundedCornerShape(3.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    RoundedCornerShape(3.dp)
                )
        )
    }
}

private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun dateLabel(isoDate: String): String {
    val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return isoDate
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> weekdayNames[date.dayOfWeek.value - 1]
    }
    return date.format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA)) + " " + prefix
}
