package com.ecut.campuspocket.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingSign
import com.ecut.campuspocket.data.remote.chaoxing.ChaoxingTask
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.CourseLavender
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ChaoxingTab(val label: String) {
    SIGNS("签到"),
    WORKS("作业"),
    EXAMS("考试")
}

private val taskComparator =
    compareBy<ChaoxingTask> { it.termRank }
        .thenByDescending { it.needsAction }
        .thenByDescending { it.detail }

@Composable
fun ChaoxingScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChaoxingViewModel = viewModel(factory = ChaoxingViewModel.Factory)
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snapshot = viewModel.snapshot
    val signs = remember(snapshot) {
        snapshot?.signs
            ?.filter { it.isOngoing }
            ?.sortedWith(compareBy<ChaoxingSign> { it.termRank }.thenByDescending { it.startTime })
            .orEmpty()
    }
    val works = remember(snapshot) {
        snapshot?.works.orEmpty().sortedWith(taskComparator)
    }
    val exams = remember(snapshot) {
        snapshot?.exams.orEmpty().sortedWith(taskComparator)
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不阻塞数据刷新，只是收不到通知 */ }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // 首次进入或从登录页返回时强制刷新，其余情况 30 秒节流。
        viewModel.refresh(force = !viewModel.loggedIn)
    }

    LaunchedEffect(viewModel.loggedIn) {
        if (viewModel.loggedIn &&
            viewModel.remindersEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
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
            Column(Modifier.weight(1f)) {
                Text("学习通", style = MaterialTheme.typography.titleLarge)
                Text(
                    "作业 / 考试 / 签到提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (viewModel.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { viewModel.refresh(force = true) }) {
                    Text("刷新")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!viewModel.loggedIn) {
                item { ChaoxingLoginCard(onOpenLogin) }
            } else {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(text = "学", color = CourseBlue, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "已登录",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "只读：不会代签、代交作业",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.logout() }) {
                                Text("退出", color = ErrorRed)
                            }
                        }
                    }
                }

                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(text = "铃", color = WarningAmber, size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("后台提醒", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "每 15 分钟检查一次签到；作业/考试每小时刷新",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = viewModel.remindersEnabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.setReminders(enabled)
                                        if (enabled &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermission.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        }
                                    }
                                )
                            }
                            if (viewModel.remindersEnabled) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "系统省电策略可能把检查推迟几分钟；签到窗口很短时请以学习通 App 为准。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChaoxingTab.entries.forEachIndexed { index, entry ->
                            TabChip(
                                label = entry.label,
                                count = when (entry) {
                                    ChaoxingTab.SIGNS -> signs.size
                                    ChaoxingTab.WORKS -> works.size
                                    ChaoxingTab.EXAMS -> exams.size
                                },
                                selected = tab == index,
                                modifier = Modifier.weight(1f),
                                onClick = { tab = index }
                            )
                        }
                    }
                }

                if (signs.any { it.termRank == 0 } || works.any { it.termRank == 0 } ||
                    exams.any { it.termRank == 0 }
                ) {
                    item {
                        Text(
                            "本学期课程优先显示；往期课程保留在列表后面。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (viewModel.error != null) {
                    item {
                        AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                viewModel.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                if (viewModel.message != null) {
                    item {
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    viewModel.message.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearMessage() }) {
                                    Text("知道了")
                                }
                            }
                        }
                    }
                }

                when (ChaoxingTab.entries[tab]) {
                    ChaoxingTab.SIGNS -> {
                        if (signs.isEmpty()) {
                            item { EmptyCard("当前没有进行中的签到", "下拉或点右上角刷新后再看看。") }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    signs.forEach { sign ->
                                        SignCard(sign) { onOpenUrl(sign.url) }
                                    }
                                }
                            }
                        }
                    }
                    ChaoxingTab.WORKS -> {
                        if (works.isEmpty()) {
                            item { EmptyCard("没有读取到作业", "可能是本学期暂无作业，或课程页面需要重新登录。") }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    works.forEach { task ->
                                        TaskCard(task, CourseLavender) { onOpenUrl(task.url) }
                                    }
                                }
                            }
                        }
                    }
                    ChaoxingTab.EXAMS -> {
                        if (exams.isEmpty()) {
                            item { EmptyCard("没有读取到考试", "考试安排以学习通和教务通知为准。") }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    exams.forEach { task ->
                                        TaskCard(task, CourseRose) { onOpenUrl(task.url) }
                                    }
                                }
                            }
                        }
                    }
                }

                if (snapshot?.courses?.isNotEmpty() == true) {
                    item {
                        Text(
                            "已读取 ${snapshot.courses.size} 门课程 · " +
                                "更新于 ${formatTime(snapshot.fetchedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChaoxingLoginCard(onOpenLogin: () -> Unit) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = "学", color = CourseBlue, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("连接学习通", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "只读取作业、考试与签到活动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "在学习通官方网页完成登录，App 只保存加密后的会话 Cookie。不会自动签到，也不会代交作业。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenLogin, modifier = Modifier.fillMaxWidth()) {
                Text("登录学习通")
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$label $count",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SignCard(sign: ChaoxingSign, onOpen: () -> Unit) {
    AppCard(containerColor = WarningAmber.copy(alpha = 0.09f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(text = "签", color = WarningAmber, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(sign.courseName, sign.termLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    sign.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (sign.startTime > 0) {
                    Text(
                        "发布于 ${formatTime(sign.startTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = onOpen, shape = RoundedCornerShape(10.dp)) {
                Text("去签到")
            }
        }
    }
}

@Composable
private fun TaskCard(task: ChaoxingTask, color: Color, onOpen: () -> Unit) {
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                text = if (task.type == "exam") "考" else "作",
                color = if (task.needsAction) color else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 36.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOf(task.courseName, task.termLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                if (task.detail.isNotBlank()) {
                    Text(
                        task.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StatusChip(
                label = task.statusLabel,
                needsAction = task.needsAction,
                done = task.isDone
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, needsAction: Boolean, done: Boolean) {
    val container = when {
        needsAction -> WarningAmber.copy(alpha = 0.18f)
        done -> CourseTeal.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    AppCard {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return "--"
    return runCatching {
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    }.getOrDefault("--")
}
