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
import android.webkit.CookieManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.remote.card.CampusCardBill
import com.ecut.campuspocket.data.remote.card.CampusCardOverview
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.liquidGlassBottomBarContentInset
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.TealPrimary

@Composable
fun CampusCardScreen(
    onBack: () -> Unit,
    onOpenOfficial: () -> Unit,
    showBack: Boolean = true,
    viewModel: CampusCardViewModel = viewModel(factory = CampusCardViewModel.Factory)
) {
    val cardCredential by AppContainer.authRepository
        .observeCredential(CampusEndpoints.SYSTEM_CARD)
        .collectAsStateWithLifecycle(initialValue = null)
    val overview = viewModel.overview

    // 从官方登录页返回 / 切回本 Tab 时自动刷新（5 分钟内不重复请求）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.ensureFresh()
    }

    // 首次进入时，把 WebView 里已有的 ehall 会话迁移到加密库，省一次登录。
    LaunchedEffect(cardCredential) {
        if (cardCredential == null) {
            val raw = CookieManager.getInstance()
                .getCookie(CampusEndpoints.EHALL_CARD_URL)
            if (!raw.isNullOrBlank()) {
                AppContainer.authRepository.saveLoginCookies(
                    systemId = CampusEndpoints.SYSTEM_CARD,
                    primaryHost = "ehall.ecut.edu.cn",
                    cookieByHost = mapOf("ehall.ecut.edu.cn" to raw)
                )
                viewModel.load()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (showBack) 8.dp else 16.dp,
                    end = 8.dp,
                    top = if (showBack) 8.dp else 14.dp,
                    bottom = 2.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = "校园卡",
                style = if (showBack) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                }
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenOfficial) {
                Text("官方页", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { viewModel.load() }, enabled = !viewModel.isLoading) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("同步中")
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
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                if (viewModel.needsLogin) {
                    TextButton(
                        onClick = {
                            viewModel.clearMessage()
                            onOpenOfficial()
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

        if (overview == null && viewModel.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "正在读取校园卡…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (overview == null) {
            EmptyCard(
                hasSession = cardCredential != null,
                onOpenOfficial = onOpenOfficial,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 28.dp + liquidGlassBottomBarContentInset()
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "overview") { CardOverviewCard(overview) }

                item(key = "bills_header") {
                    SectionHeader(
                        title = "交易流水",
                        action = "共 ${viewModel.total} 笔"
                    )
                }

                if (viewModel.bills.isEmpty()) {
                    item(key = "empty_bills") {
                        AppCard {
                            Text(
                                text = "暂无流水记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item(key = "bills") {
                        AppCard(contentPadding = 0.dp) {
                            Column {
                                viewModel.bills.forEachIndexed { index, bill ->
                                    BillRow(bill)
                                    if (index != viewModel.bills.lastIndex) {
                                        ThinDivider(Modifier.padding(start = 16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (viewModel.bills.size < viewModel.total) {
                    item(key = "more") {
                        Button(
                            onClick = { viewModel.loadMore() },
                            enabled = !viewModel.isLoadingMore,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (viewModel.isLoadingMore) {
                                CircularProgressIndicator(
                                    Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("加载中")
                            } else {
                                Text("加载更多（已显示 ${viewModel.bills.size}/${viewModel.total}）")
                            }
                        }
                    }
                }

                item(key = "note") {
                    Text(
                        text = "数据来自学校一卡通系统，只读；支付宝随充随付会产生等额的一正一负记录。",
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
private fun CardOverviewCard(overview: CampusCardOverview) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "可用余额",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "¥${"%.2f".format(overview.balance)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            if (overview.cardState.isNotBlank()) append(overview.cardState)
                            if (overview.cardNumber.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(maskCard(overview.cardNumber))
                            }
                        }.ifBlank { "一卡通" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconBadge(text = "卡", color = CourseBlue, size = 44.dp)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthStat(
                    title = "本月消费",
                    value = "¥${"%.2f".format(overview.monthSpend)}",
                    count = "${overview.monthSpendCount} 笔",
                    modifier = Modifier.weight(1f)
                )
                MonthStat(
                    title = "本月充值",
                    value = "¥${"%.2f".format(overview.monthRecharge)}",
                    count = "${overview.monthRechargeCount} 笔",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthStat(
    title: String,
    value: String,
    count: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BillRow(bill: CampusCardBill) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = bill.location.ifBlank { bill.machine.ifBlank { "校园卡消费" } },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(formatBillTime(bill.time))
                    if (bill.isRecharge) append(" · 充值") else append(" · 消费")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = (if (bill.isRecharge) "+" else "-") + "%.2f".format(bill.amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (bill.isRecharge) {
                TealPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun EmptyCard(
    hasSession: Boolean,
    onOpenOfficial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AppCard(modifier = Modifier.padding(horizontal = 24.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                IconBadge(text = "卡", color = CourseBlue, size = 54.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (hasSession) "还没有读到校园卡数据" else "先登录校园卡",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (hasSession) {
                        "点“刷新”重试，或打开官方页确认一卡通状态。"
                    } else {
                        "在官方页面完成统一认证后，余额和流水会加密缓存在本机。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenOfficial) { Text("打开校园卡官方页") }
            }
        }
    }
}

private fun maskCard(number: String): String {
    if (number.length <= 4) return number
    return "****" + number.takeLast(4)
}

private fun formatBillTime(raw: String): String {
    val cleaned = raw.replace(Regex("""^\d{4}-"""), "")
    return cleaned.substringBeforeLast(":").take(16)
}
