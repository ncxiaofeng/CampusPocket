package com.ecut.campuspocket.capture.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.capture.CaptureEntry
import com.ecut.campuspocket.capture.CaptureKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter(val label: String) {
    ALL("全部"), REQUEST("请求"), RESPONSE("响应"), ERROR("错误")
}

@Composable
fun LogPanel(
    entries: List<CaptureEntry>,
    modifier: Modifier = Modifier,
    showActions: Boolean = true
) {
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL.name) }
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activeFilter = LogFilter.valueOf(filter)

    val filtered = remember(entries, activeFilter) {
        entries.asReversed().filter { entry ->
            when (activeFilter) {
                LogFilter.ALL -> true
                LogFilter.REQUEST -> entry.kind == CaptureKind.REQUEST
                LogFilter.RESPONSE -> entry.kind == CaptureKind.RESPONSE
                LogFilter.ERROR -> entry.kind == CaptureKind.ERROR
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogFilter.entries.forEach { item ->
                FilterChip(
                    selected = activeFilter == item,
                    onClick = { filter = item.name },
                    label = { Text(item.label, fontSize = 11.sp) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (showActions) {
                TextButton(
                    onClick = { exportAndShare(context) },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) { Text("导出") }
                TextButton(
                    onClick = { confirmClear = true },
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) { Text("清空") }
            }
        }

        Text(
            text = "共 ${entries.size} 条 · ${filtered.size} 条显示（新→旧）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "还没有日志。打开页面并操作后，请求会出现在这里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.entryId }) { entry ->
                    LogEntryCard(entry)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空日志？") },
            text = { Text("内存和当前日志文件都会被清空，已导出的文件不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    com.ecut.campuspocket.capture.CaptureStore.clear()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LogEntryCard(entry: CaptureEntry) {
    var expanded by remember(entry.entryId) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindBadge(entry)
            Spacer(Modifier.width(8.dp))
            Text(
                text = entry.method.ifBlank { kindLabel(entry.kind) },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (entry.status != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = entry.status.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if ((entry.status ?: 0) in 200..399) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.url.ifBlank { entry.error.ifBlank { "(页面事件)" } },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) 6 else 2,
            overflow = TextOverflow.Ellipsis
        )

        if (expanded) {
            if (entry.headers.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Headers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        text = entry.headers.take(4_000),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (entry.body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Body",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SelectionContainer {
                    Text(
                        text = entry.body.take(4_000),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (entry.error.isNotBlank() && entry.url.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = entry.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        val text = buildString {
                            append(entry.method).append(' ').append(entry.url).append('\n')
                            if (entry.headers.isNotBlank()) append(entry.headers).append('\n')
                            if (entry.body.isNotBlank()) append(entry.body)
                        }
                        clipboard.setText(AnnotatedString(text))
                        android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("复制这条") }
            }
        }
    }
}

@Composable
private fun KindBadge(entry: CaptureEntry) {
    val (label, color) = when (entry.kind) {
        CaptureKind.REQUEST -> "REQ" to MaterialTheme.colorScheme.primary
        CaptureKind.RESPONSE -> "RES" to if ((entry.status ?: 0) in 200..399) {
            Color(0xFF2E7D32)
        } else {
            MaterialTheme.colorScheme.error
        }
        CaptureKind.ERROR -> "ERR" to MaterialTheme.colorScheme.error
        CaptureKind.PAGE -> "PAGE" to MaterialTheme.colorScheme.onSurfaceVariant
        CaptureKind.CONSOLE -> "CONSOLE" to Warning
        CaptureKind.WS -> "WS" to Color(0xFF1565C0)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun kindLabel(kind: CaptureKind): String = when (kind) {
    CaptureKind.REQUEST -> "请求"
    CaptureKind.RESPONSE -> "响应"
    CaptureKind.ERROR -> "错误"
    CaptureKind.PAGE -> "页面"
    CaptureKind.CONSOLE -> "控制台"
    CaptureKind.WS -> "WebSocket"
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
