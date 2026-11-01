package com.ecut.campuspocket.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecut.campuspocket.data.AppLinks
import com.ecut.campuspocket.data.remote.update.AppUpdateInfo

@Composable
fun AppUpdateDialog(
    info: AppUpdateInfo,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.version}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = info.releaseNotes.ifBlank { "本次更新没有附带说明。" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "蓝奏云提取码：${AppLinks.LANZOU_FOLDER_PWD}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onOpenUrl(info.apkDownloadUrl ?: info.releasePageUrl)
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (info.apkDownloadUrl != null) "GitHub 下载" else "打开发布页")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onOpenUrl(AppLinks.LANZOU_FOLDER)
                        onDismiss()
                    }
                ) { Text("蓝奏云下载") }
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        }
    )
}
