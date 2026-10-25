package com.ecut.campuspocket.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.BuildConfig
import com.ecut.campuspocket.R
import com.ecut.campuspocket.data.AppLinks
import com.ecut.campuspocket.data.remote.update.AppUpdateInfo
import com.ecut.campuspocket.data.remote.update.UpdateChecker
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.TealPrimary
import kotlinx.coroutines.launch

private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: AppUpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var showReleaseDialog by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun checkUpdate() {
        if (updateState is UpdateState.Checking) return
        updateState = UpdateState.Checking
        scope.launch {
            val result = UpdateChecker(com.ecut.campuspocket.AppContainer.baseHttpClient)
                .check(BuildConfig.VERSION_NAME)
            updateState = result.fold(
                onSuccess = { info ->
                    if (info == null) {
                        UpdateState.UpToDate
                    } else {
                        showReleaseDialog = true
                        UpdateState.Available(info)
                    }
                },
                onFailure = {
                    UpdateState.Failed(it.message ?: "无法连接更新服务器")
                }
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("关于 冻小梨", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { HeroCard() }

            item {
                UpdateCard(
                    state = updateState,
                    onCheck = ::checkUpdate,
                    onShowRelease = { showReleaseDialog = true }
                )
            }

            item { LinksCard(onOpenUrl = ::openUrl) }

            item { PrivacyCard(onOpenPrivacy = onOpenPrivacy) }
        }
    }

    val available = updateState as? UpdateState.Available
    if (showReleaseDialog && available != null) {
        ReleaseDialog(
            info = available.info,
            onDismiss = { showReleaseDialog = false },
            onOpenUrl = ::openUrl
        )
    }
}

@Composable
private fun HeroCard() {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_frozen_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "冻小梨",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "CampusPocket · 东华理工校园服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "纯本地、无后端的校园聚合 App。所有凭据只保存在本机加密数据库，不上传任何个人数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UpdateCard(
    state: UpdateState,
    onCheck: () -> Unit,
    onShowRelease: () -> Unit
) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = "更", color = TealPrimary, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("检查更新", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "GitHub Releases · 最新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onCheck,
                    enabled = state !is UpdateState.Checking,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (state is UpdateState.Checking) "检查中" else "检查",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            when (state) {
                UpdateState.Idle -> Unit
                UpdateState.Checking -> {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }
                UpdateState.UpToDate -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "已是最新版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is UpdateState.Failed -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "检查失败：${state.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "可直接用下方蓝奏云链接下载最新版。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UpdateState.Available -> {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "发现新版本 v${state.info.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onShowRelease,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("查看更新内容") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinksCard(onOpenUrl: (String) -> Unit) {
    Column {
        SectionHeader(title = "项目与下载")
        Spacer(Modifier.height(10.dp))
        AppCard(contentPadding = 0.dp) {
            Column {
                LinkRow(
                    title = "GitHub 项目主页",
                    url = AppLinks.GITHUB_REPO,
                    subtitle = "源码、问题反馈与 Star",
                    onOpenUrl = onOpenUrl
                )
                ThinDivider(Modifier.padding(start = 16.dp))
                LinkRow(
                    title = "GitHub Releases",
                    url = AppLinks.GITHUB_RELEASES,
                    subtitle = "版本历史与 APK 下载",
                    onOpenUrl = onOpenUrl
                )
                ThinDivider(Modifier.padding(start = 16.dp))
                LinkRow(
                    title = "蓝奏云 · APK",
                    url = AppLinks.LANZOU_APK,
                    subtitle = "国内无法访问 GitHub 时使用",
                    onOpenUrl = onOpenUrl
                )
                ThinDivider(Modifier.padding(start = 16.dp))
                LinkRow(
                    title = "蓝奏云 · ZIP",
                    url = AppLinks.LANZOU_ZIP,
                    subtitle = "含 APK、SHA256 与安装说明",
                    onOpenUrl = onOpenUrl
                )
            }
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    url: String,
    subtitle: String,
    onOpenUrl: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(url) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "打开 ›",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PrivacyCard(onOpenPrivacy: () -> Unit) {
    AppCard(onClick = onOpenPrivacy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(text = "隐", color = CourseBlue, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("隐私说明", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "凭据只存本机、不保存账号密码、禁止系统备份",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "›",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReleaseDialog(
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
                        onOpenUrl(AppLinks.LANZOU_APK)
                        onDismiss()
                    }
                ) { Text("蓝奏云下载") }
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        }
    )
}
