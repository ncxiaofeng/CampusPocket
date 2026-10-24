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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.remote.jw.JwClassroom
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary

private val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
fun ClassroomsScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenOfficial: () -> Unit,
    viewModel: ClassroomsViewModel = viewModel(factory = ClassroomsViewModel.Factory)
) {
    val filters = viewModel.filters
    val grouped = remember(viewModel.classrooms) {
        viewModel.classrooms.groupBy { it.building.ifBlank { "未标注楼号" } }
    }
    val periodLabel = remember(viewModel.selectedPeriods) {
        formatPeriods(viewModel.selectedPeriods)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
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
            Text("空闲教室", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenOfficial) {
                Text("官方页面", style = MaterialTheme.typography.labelMedium)
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

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "filters") {
                FilterCard(viewModel = viewModel, periodLabel = periodLabel)
            }

            if (viewModel.isQuerying && !viewModel.hasQueried) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "正在查询空教室…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (viewModel.hasQueried && viewModel.classrooms.isEmpty()) {
                item(key = "empty") {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconBadge(text = "教", color = CourseTeal, size = 48.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("没有符合条件的空教室", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "试试换个节次、星期或楼号。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (viewModel.classrooms.isNotEmpty()) {
                item(key = "summary") {
                    Column {
                        Text(
                            text = buildString {
                                append("第 ${viewModel.selectedWeek} 周 · ")
                                append(weekdays[viewModel.selectedWeekday - 1])
                                append(" · 第 $periodLabel 节")
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = buildString {
                                if (filters != null) {
                                    append(filters.campusName)
                                    append(" · ")
                                }
                                append("共 ${viewModel.classrooms.size} 间空闲")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                grouped.forEach { (building, rooms) ->
                    item(key = "building_$building") {
                        Column {
                            SectionHeader(title = building, action = "${rooms.size} 间")
                            Spacer(Modifier.height(10.dp))
                            AppCard(contentPadding = 0.dp) {
                                Column {
                                    rooms.forEachIndexed { index, room ->
                                        RoomRow(room)
                                        if (index != rooms.lastIndex) {
                                            ThinDivider(Modifier.padding(start = 16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "footer") {
                Text(
                    text = "数据来自教务空闲教室查询，只读；教室临时借用或调课可能未及时更新。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FilterCard(viewModel: ClassroomsViewModel, periodLabel: String) {
    val weekState = rememberLazyListState(
        initialFirstVisibleItemIndex = (viewModel.selectedWeek - 3).coerceAtLeast(0)
    )
    val weekdayState = rememberLazyListState(
        initialFirstVisibleItemIndex = (viewModel.selectedWeekday - 2).coerceAtLeast(0)
    )
    val periodState = rememberLazyListState(
        initialFirstVisibleItemIndex = (viewModel.selectedPeriods.min() - 2).coerceAtLeast(0)
    )
    var campusMenuExpanded by remember { mutableStateOf(false) }
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = "教", color = CourseTeal, size = 30.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "查询条件",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "第 $periodLabel 节",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val campuses = viewModel.filters?.campuses.orEmpty()
            if (campuses.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FilterRow(label = "校区") {
                    Box {
                        Row(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { campusMenuExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.filters?.campusName ?: "选择校区",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "▾",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = campusMenuExpanded,
                            onDismissRequest = { campusMenuExpanded = false }
                        ) {
                            campuses.forEach { campus ->
                                val selected = viewModel.filters?.campusId == campus.id
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = campus.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    },
                                    onClick = {
                                        campusMenuExpanded = false
                                        viewModel.selectCampus(campus.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            FilterRow(label = "周次") {
                LazyRow(
                    state = weekState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items((1..20).toList()) { week ->
                        SelectableChip(
                            text = "$week",
                            selected = viewModel.selectedWeek == week,
                            onClick = { viewModel.selectWeek(week) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            FilterRow(label = "星期") {
                LazyRow(
                    state = weekdayState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(weekdays.size) { index ->
                        SelectableChip(
                            text = weekdays[index],
                            selected = viewModel.selectedWeekday == index + 1,
                            onClick = { viewModel.selectWeekday(index + 1) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            FilterRow(label = "节次") {
                LazyRow(
                    state = periodState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items((1..12).toList()) { period ->
                        SelectableChip(
                            text = "$period",
                            selected = period in viewModel.selectedPeriods,
                            onClick = { viewModel.togglePeriod(period) }
                        )
                    }
                }
            }

            val buildings = viewModel.filters?.buildings.orEmpty()
            if (buildings.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FilterRow(label = "楼号") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(buildings) { building ->
                            SelectableChip(
                                text = building.name.ifBlank { "全部" },
                                selected = viewModel.selectedBuildingId == building.id,
                                onClick = { viewModel.selectBuilding(building.id) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { viewModel.query() },
                enabled = viewModel.filters != null && !viewModel.isQuerying,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (viewModel.isQuerying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("查询中")
                } else {
                    Text("查询空闲教室")
                }
            }
        }
    }
}

@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(38.dp)
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun RoomRow(room: JwClassroom) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    if (room.type.isNotBlank()) append(room.type)
                    if (room.seats > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("${room.seats} 座")
                    }
                    if (room.examSeats > 0 && room.examSeats != room.seats) {
                        append(" · 考试 ${room.examSeats} 座")
                    }
                }.ifBlank { "教室" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (room.borrowable) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "可借用",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

private fun formatPeriods(periods: Set<Int>): String {
    val sorted = periods.sorted()
    if (sorted.isEmpty()) return "-"
    val contiguous = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (contiguous && sorted.size > 1) {
        "${sorted.first()}-${sorted.last()}"
    } else {
        sorted.joinToString(",")
    }
}
