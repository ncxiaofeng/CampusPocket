package com.ecut.campuspocket.capture.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecut.campuspocket.capture.CaptureStore
import java.io.File
import java.text.DecimalFormat

private data class CaptureTarget(
    val badge: String,
    val name: String,
    val url: String,
    val description: String,
    val color: Color
)

private val targets = listOf(
    CaptureTarget(
        badge = "洗",
        name = "U净 · 洗衣 H5",
        url = "https://wx.zhinengxiyifang.cn/static/washer-h5/ujing/latest/index.html",
        description = "美的洗衣房 H5，登录后可查看设备与订单",
        color = Color(0xFF1565C0)
    ),
    CaptureTarget(
        badge = "水",
        name = "U净 · 饮水 H5",
        url = "https://static.ujing.com.cn/h5/water-h5/ujing/latest/index.html",
        description = "饮水机设备页，扫码 / 登录后操作",
        color = Color(0xFF00897B)
    ),
    CaptureTarget(
        badge = "浴",
        name = "U净 · 淋浴 H5",
        url = "https://static.ujing.com.cn/h5/shower-h5/ujing/latest/index.html",
        description = "淋浴设备页",
        color = Color(0xFF6A1B9A)
    ),
    CaptureTarget(
        badge = "慧",
        name = "慧生活798 · H5",
        url = "https://cloud.hnkzy.com/h5/",
        description = "慧生活 H5 主入口，洗衣 / 吹风机等",
        color = Color(0xFFEF6C00)
    ),
    CaptureTarget(
        badge = "跑",
        name = "闪动校园 · H5",
        url = "https://mh5.huachenjie.com/run-mobile-web",
        description = "闪动校园移动网页版，登录后查看跑步记录",
        color = Color(0xFFC62828)
    )
)

@Composable
fun HomeScreen(
    onOpenUrl: (String) -> Unit,
    onOpenLogs: () -> Unit
) {
    val context = LocalContext.current
    val entries by CaptureStore.entries.collectAsStateWithLifecycle()
    var customDialog by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf("") }
    val files = CaptureStore.listLogFiles().take(6)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "校园抓包助手",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "在 App 内打开 H5 服务，自动记录请求与响应",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "记录范围",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "本工具用自带 WebView 打开 H5 页面，并通过 JS 注入记录 fetch / XHR 的 URL、请求体、响应体和状态码。Cookie、Authorization、密码、验证码、token 会自动打码。\n\n原生 App 的 HTTPS 抓包需要 root + 系统证书；Android 7 以上默认不信任用户证书，证书固定还会进一步拦截，本工具不尝试绕过。若页面提示“请在微信中打开”，说明该 H5 只支持微信内置浏览器，普通 WebView 看不到完整流程。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("H5 抓包入口", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "接口通常与原生 App 共用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(targets.size) { index ->
            val target = targets[index]
            TargetCard(target) { onOpenUrl(target.url) }
        }

        item {
            TargetCard(
                badge = "+",
                name = "自定义网址",
                url = "",
                description = "输入任意 H5 / 网页地址",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { customDialog = true }
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("本次会话", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "已记录 ${entries.size} 条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenLogs) { Text("查看日志") }
                        TextButton(onClick = { exportAndShare(context) }) { Text("导出") }
                    }
                }
            }
        }

        if (files.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "历史抓包文件",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column {
                            files.forEach { file ->
                                FileRow(file, onShare = { shareCaptureFile(context, file) })
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "日志只保存在本机应用私有目录；点击导出后才会通过系统分享发出。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (customDialog) {
        AlertDialog(
            onDismissRequest = { customDialog = false },
            title = { Text("打开自定义网址") },
            text = {
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    label = { Text("https://…") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = customUrl.isNotBlank(),
                    onClick = {
                        val normalized = customUrl.trim().let {
                            if (it.startsWith("http://") || it.startsWith("https://")) it
                            else "https://" + it
                        }
                        customDialog = false
                        customUrl = ""
                        onOpenUrl(normalized)
                    }
                ) { Text("打开") }
            },
            dismissButton = {
                TextButton(onClick = { customDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TargetCard(target: CaptureTarget, onClick: () -> Unit) {
    TargetCard(
        badge = target.badge,
        name = target.name,
        url = target.url,
        description = target.description,
        color = target.color,
        onClick = onClick
    )
}

@Composable
private fun TargetCard(
    badge: String,
    name: String,
    url: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(color.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    color = color,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FileRow(file: File, onShare: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.labelMedium)
            Text(
                text = DecimalFormat("0.0").format(file.length() / 1024.0) + " KB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onShare) { Text("分享") }
    }
}
