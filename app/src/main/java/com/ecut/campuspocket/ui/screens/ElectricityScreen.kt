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
import androidx.compose.runtime.mutableIntStateOf
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
import com.ecut.campuspocket.data.repository.ElectricityDay
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.StackedUsageChart
import com.ecut.campuspocket.ui.components.StatusDot
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.components.UsageBar
import com.ecut.campuspocket.ui.components.labelStepFor
import com.ecut.campuspocket.ui.theme.ChartAc
import com.ecut.campuspocket.ui.theme.ChartPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ElectricityScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ElectricityViewModel = viewModel(factory = ElectricityViewModel.Factory)
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableIntStateOf(7) }
    val displayed = days.takeLast(range)
    val latest = days.lastOrNull()
    val yesterdayEpochDay = LocalDate.now().minusDays(1).toEpochDay()
    val yesterday = days.lastOrNull { it.epochDay == yesterdayEpochDay }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
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
            Text("电费", style = MaterialTheme.typography.titleLarge)
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
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(MaterialTheme.colorScheme.primary, size = 5.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "只读",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (viewModel.message != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { BalanceCard(latest = latest, yesterday = yesterday) }

            item {
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "用电趋势",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.weight(1f))
                            RangeSelector(
                                range = range,
                                onRange = { newRange ->
                                    range = newRange
                                    // 旧缓存只有 7 天时，切到 30 天自动补拉一次。
                                    if (newRange == 30 && days.size < 8 &&
                                        !viewModel.isSyncing
                                    ) {
                                        viewModel.sync()
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(ChartPrimary, "用电量")
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (displayed.isEmpty()) "" else {
                                    "近 ${displayed.size} 天 ${"%.1f".format(displayed.sumOf { it.totalKwh })} 度"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (displayed.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            StackedUsageChart(
                                bars = displayed.map { it.toBar() },
                                lightingColor = ChartPrimary,
                                acColor = ChartAc,
                                labelEvery = labelStepFor(displayed.size)
                            )
                        }
                    }
                }
            }

            if (days.isEmpty()) {
                item { EmptyElectricityCard(onOpenLogin = onOpenLogin) }
            } else {
                item {
                    Column {
                        SectionHeader(title = "历史记录", action = "最近 ${displayed.size} 天")
                        Spacer(Modifier.height(10.dp))
                        AppCard(contentPadding = 0.dp) {
                            Column {
                                val history = displayed.asReversed()
                                history.forEachIndexed { index, day ->
                                    DayRow(day)
                                    if (index != history.lastIndex) {
                                        ThinDivider(Modifier.padding(start = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), size = 5.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "数据来自宿舍缴费系统，仅手动同步时更新；只读，不提供充值功能。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(latest: ElectricityDay?, yesterday: ElectricityDay?) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "账户余额",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = latest?.balance?.let { "¥%.2f".format(it) } ?: "¥ --",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        latest == null -> "尚未同步"
                        yesterday == null -> "昨日用电暂无数据"
                        else -> "昨日用电 ${"%.1f".format(yesterday.totalKwh)} 度"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconBadge(text = "电", color = WarningAmber, size = 44.dp)
        }
    }
}

@Composable
private fun RangeSelector(range: Int, onRange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp)
            )
            .padding(2.dp)
    ) {
        listOf(7 to "7 天", 30 to "30 天").forEach { (value, label) ->
            val selected = range == value
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
                    )
                    .clickable { onRange(value) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyElectricityCard(onOpenLogin: () -> Unit) {
    AppCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(text = "电", color = WarningAmber, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "还没有用电记录",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "登录“宿舍缴费”后点“同步”，即可读取电表余额和最近 7 天用量。数据只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onOpenLogin,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("登录宿舍缴费")
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: ElectricityDay) {
    val date = LocalDate.ofEpochDay(day.epochDay)
    val dateText = date.format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = dateText, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                text = "当日用电",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${"%.1f".format(day.totalKwh)} 度",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun ElectricityDay.toBar(): UsageBar {
    val date = LocalDate.ofEpochDay(epochDay)
    return UsageBar(
        label = "${date.monthValue}/${date.dayOfMonth}",
        lightingKwh = lightingKwh.toFloat(),
        acKwh = acKwh.toFloat()
    )
}
