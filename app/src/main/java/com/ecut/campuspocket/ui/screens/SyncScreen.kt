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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.CampusNetworkStatus
import com.ecut.campuspocket.network.ProxySessionRequiredException
import com.ecut.campuspocket.network.SessionExpiredException
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.SessionState
import com.ecut.campuspocket.ui.components.StatusDot
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SyncScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenPortal: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val syncManager = remember { AppContainer.jwSyncManager }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var needsLogin by rememberSaveable { mutableStateOf(false) }
    var needsPortal by remember { mutableStateOf(false) }
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var awaitingLogin by rememberSaveable { mutableStateOf(false) }
    var lastSyncLabel by rememberSaveable { mutableStateOf<String?>(null) }

    fun runSync(label: String, task: suspend () -> Result<Int>) {
        if (busy != null) return
        lastSyncLabel = label
        scope.launch {
            busy = label
            message = null
            needsLogin = false
            needsPortal = false
            task().fold(
                onSuccess = { count ->
                    message = "${label}已同步，共 $count 条"
                    SessionState.clear()
                },
                onFailure = { error ->
                    needsLogin = error is SessionExpiredException
                    needsPortal = error is ProxySessionRequiredException
                    if (needsLogin) SessionState.reportExpired()
                    if (needsLogin) {
                        scope.launch {
                            val jwHost = java.net.URI(CampusEndpoints.JW_BASE_URL).host
                            val jwCookies = runCatching {
                                AppContainer.authRepository.loadCookiesForHost(jwHost)
                            }.getOrDefault(emptyList())
                            val cas = runCatching {
                                AppContainer.authRepository.getCredential(
                                    CampusEndpoints.SYSTEM_CAS_MASTER
                                )
                            }.getOrNull()
                            diagnostic = buildString {
                                append("error=").append(error.message.orEmpty())
                                append("; network=")
                                append(CampusNetworkStatus.state.value::class.simpleName)
                                append("; jwCookies=")
                                append(jwCookies.joinToString(",") { "${it.name}@${it.path}" })
                                append("; casUpdatedAt=").append(cas?.updatedAt ?: 0L)
                            }
                        }
                    }
                    message = when {
                        needsLogin -> "教务系统登录已过期，请重新登录"
                        needsPortal -> error.message ?: "零信任代理会话已失效"
                        else -> "${label}同步失败：${error.message ?: "请检查校园网连接"}"
                    }
                }
            )
            busy = null
        }
    }

    fun reopenLastSync() {
        when (lastSyncLabel) {
            "课表" -> runSync("课表") { syncManager.syncSchedule() }
            "成绩" -> runSync("成绩") { syncManager.syncGrades() }
            "考试安排" -> runSync("考试安排") { syncManager.syncExams() }
        }
    }

    // 从登录页返回后自动清掉旧的“登录已过期”，并重试刚才那一项。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingLogin || needsLogin) {
            awaitingLogin = false
            message = null
            needsLogin = false
            needsPortal = false
            scope.launch {
                // 给 WebView 的 Cookie 落库留一点时间，避免返回后立刻同步又拿到旧会话。
                delay(700)
                message = null
                reopenLastSync()
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
            Text("数据同步", style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (message != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (needsLogin) ErrorRed else TealPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (needsLogin) {
                        TextButton(onClick = {
                            awaitingLogin = true
                            onOpenLogin()
                        }) { Text("去登录") }
                        diagnostic?.let { info ->
                            TextButton(
                                onClick = { clipboard.setText(AnnotatedString(info)) },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("复制诊断") }
                        }
                    } else if (needsPortal) {
                        TextButton(onClick = onOpenPortal) { Text("去门户") }
                    }
                }
            }

            SyncRow(
                badge = "课",
                badgeColor = CourseTeal,
                title = "课表",
                subtitle = "拉到本周及整个学期的课程块",
                busy = busy == "课表",
                enabled = busy == null,
                onSync = {
                    runSync("课表") { syncManager.syncSchedule() }
                }
            )
            SyncRow(
                badge = "绩",
                badgeColor = CourseBlue,
                title = "成绩",
                subtitle = "拉取全部已出分课程",
                busy = busy == "成绩",
                enabled = busy == null,
                onSync = {
                    runSync("成绩") { syncManager.syncGrades() }
                }
            )
            SyncRow(
                badge = "考",
                badgeColor = CourseRose,
                title = "考试安排",
                subtitle = "本学期考场、座位号与时间",
                busy = busy == "考试安排",
                enabled = busy == null,
                onSync = {
                    runSync("考试安排") { syncManager.syncExams() }
                }
            )

            AppCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(MaterialTheme.colorScheme.primary, size = 6.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "只读同步：不会向教务系统提交任何修改，数据仅缓存在本机。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncRow(
    badge: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    busy: Boolean,
    enabled: Boolean,
    onSync: () -> Unit
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(text = badge, color = badgeColor, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onSync,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("同步", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
