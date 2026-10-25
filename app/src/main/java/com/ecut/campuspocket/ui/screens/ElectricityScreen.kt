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
    val today = LocalDate.now()
    val dayByEpoch = remember(days) { days.associateBy { it.epochDay } }
    val windowStart = today.minusDays((range - 1).toLong())
    val displayed = remember(days, range, today) {
        days.filter { it.epochDay in windowStart.toEpochDay()..today.toEpochDay() }
    }
    val usageDays = displayed.filter { it.totalKwh > 0.0 }
    val latestBalanceDay = days.lastOrNull { it.balance != null }
    val yesterday = dayByEpoch[today.minusDays(1).toEpochDay()]
    val bars = remember(dayByEpoch, range, today) {
        (0 until range).map { offset ->
            val date = windowStart.plusDays(offset.toLong())
            val day = dayByEpoch[date.toEpochDay()]
            UsageBar(
                label = "${date.monthValue}/${date.dayOfMonth}",
                lightingKwh = day?.lightingKwh?.toFloat() ?: 0f,
                acKwh = day?.acKwh?.toFloat() ?: 0f,
                hasData = day != null && day.totalKwh > 0.0
            )
        }
    }

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
            item {
                BalanceCard(
                    balance = latestBalanceDay?.balance,
                    updatedAt = latestBalanceDay?.updatedAt,
                    yesterday = yesterday
                )
            }

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
                                    // 切到 30 天且本机完全没有记录时自动补拉一次。
                                    if (newRange == 30 && days.isEmpty() &&
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
                                text = if (usageDays.isEmpty()) {
                                    "暂无有效数据"
                                } else {
                                    "合计 ${"%.1f".format(usageDays.sumOf { it.totalKwh })} 度 · " +
                                        "${usageDays.size} 天有数据"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        if (usageDays.isEmpty()) {
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
                                bars = bars,
                                lightingColor = ChartPrimary,
                                acColor = ChartAc,
                                labelEvery = labelStepFor(bars.size)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "空白圆点表示系统未返回该日明细；学校接口只保留近期数据。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            if (days.isEmpty()) {
                item { EmptyElectricityCard(onOpenLogin = onOpenLogin) }
            } else if (usageDays.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            text = "近 $range 天内没有读取到有效用电明细。可能是接口尚未结算，或超出了一卡通系统的明细保留期。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Column {
                        SectionHeader(title = "历史记录", action = "近 $range 天 · ${usageDays.size} 天")
                        Spacer(Modifier.height(10.dp))
                        AppCard(contentPadding = 0.dp) {
                            Column {
                                val history = usageDays.asReversed()
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
private fun BalanceCard(balance: Double?, updatedAt: Long?, yesterday: ElectricityDay?) {
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
                        text = balance?.let { "¥%.2f".format(it) } ?: "¥ --",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        balance == null -> "尚未同步"
                        yesterday == null -> "昨日用电暂无数据"
                        else -> "昨日用电 ${"%.1f".format(yesterday.totalKwh)} 度"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (updatedAt != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "更新于 " + java.time.Instant.ofEpochMilli(updatedAt)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
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
                    text = "完成统一身份认证后点“同步”，即可读取电表余额和最近 30 天用量。数据只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onOpenLogin,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("登录统一身份认证")
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
