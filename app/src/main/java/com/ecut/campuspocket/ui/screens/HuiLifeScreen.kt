package com.ecut.campuspocket.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDiagnostics
import com.ecut.campuspocket.data.remote.huilife.HuiLifePlatform
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.ChartPrimary
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val HUI_ALIPAY_APP_ID = "2019061465519660"

/**
 * 慧生活798：短信登录后读取钱包/设备/消费。
 *
 * 启动设备属于控制类操作，必须用户二次确认；不自动接水、不充值。
 */
@Composable
fun HuiLifeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HuiLifeViewModel = viewModel(factory = HuiLifeViewModel.Factory)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dashboard = viewModel.dashboard
    val loggedIn = viewModel.accountLoggedIn || viewModel.appLoggedIn
    val showLogin = viewModel.loginFormVisible || !loggedIn

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", fontSize = 28.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "慧生活798",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "饮水 / 热水 / 淋浴 · 只读 + 手动启动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (viewModel.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (!showLogin) {
                TextButton(onClick = { viewModel.refresh() }) { Text("刷新") }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            TextButton(
                                onClick = {
                                    viewModel.message?.let {
                                        clipboard.setText(
                                            AnnotatedString(
                                                it + "\n" + HuiLifeDiagnostics.summary(
                                                    "app=${viewModel.appLoggedIn} " +
                                                        "account=${viewModel.accountLoggedIn}"
                                                )
                                            )
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("复制") }
                            TextButton(onClick = { viewModel.clearMessage() }) {
                                Text("知道了")
                            }
                        }
                    }
                }
            }

            if (showLogin) {
                item { HuiLifeLoginCard(viewModel) }
            } else {
                item { SessionCard(viewModel) }
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(text = "钱", color = TealPrimary, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dashboard?.wallet?.name ?: "校园钱包",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    dashboard?.wallet?.let { "¥%.2f".format(it.balance) }
                                        ?: "未读取",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                if (dashboard?.usingAppToken == true) "设备端读取" else "查询端读取",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!dashboard?.otherErrors.isNullOrEmpty()) {
                    item {
                        AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                "部分数据读取失败：${dashboard?.otherErrors.orEmpty().joinToString("；")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                item {
                    AppCard(contentPadding = 0.dp) {
                        Column {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("我的设备", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${dashboard?.devices?.size ?: 0} 台",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val devices = dashboard?.devices.orEmpty()
                            if (devices.isEmpty()) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    Text(
                                        when {
                                            !viewModel.appLoggedIn ->
                                                "设备列表需要「设备控制」登录。查询登录只负责余额/消费；" +
                                                    "请切换设备控制再做一次短信验证。"
                                            dashboard?.deviceError != null ->
                                                "设备读取失败：${dashboard?.deviceError.orEmpty()}"
                                            else ->
                                                "接口返回 0 台收藏设备。请先在支付宝小程序里收藏要用的饮水机，再刷新。"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (dashboard?.deviceError != null && viewModel.appLoggedIn) {
                                            ErrorRed
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (dashboard?.deviceError != null) {
                                        TextButton(
                                            onClick = {
                                                clipboard.setText(
                                                    AnnotatedString(
                                                        "设备错误：" +
                                                            dashboard?.deviceError.orEmpty() +
                                                            "\n" + HuiLifeDiagnostics.summary(
                                                                "app=${viewModel.appLoggedIn} " +
                                                                    "account=${viewModel.accountLoggedIn}"
                                                            )
                                                    )
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 0.dp)
                                        ) { Text("复制错误") }
                                        Text(
                                            text = HuiLifeDiagnostics.summary(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!viewModel.appLoggedIn) {
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = { viewModel.openLogin(HuiLifePlatform.APP) },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(
                                                horizontal = 12.dp,
                                                vertical = 7.dp
                                            )
                                        ) { Text("登录设备控制") }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            } else {
                                devices.forEachIndexed { index, device ->
                                    ThinDivider(Modifier.padding(start = 16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconBadge(text = "水", color = ChartPrimary, size = 34.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(device.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                device.detail.ifBlank { device.id },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (viewModel.appLoggedIn) {
                                            TextButton(onClick = { viewModel.stop(device) }) {
                                                Text("停止", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Button(
                                                onClick = { viewModel.askStart(device) },
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(
                                                    horizontal = 12.dp,
                                                    vertical = 6.dp
                                                )
                                            ) { Text("启动") }
                                        } else {
                                            Text(
                                                "需设备登录",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = WarningAmber
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                item {
                    AppCard(contentPadding = 0.dp) {
                        Column {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("最近消费", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${dashboard?.bills?.size ?: 0} 笔",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val bills = dashboard?.bills.orEmpty().take(6)
                            if (bills.isEmpty()) {
                                Text(
                                    "暂无消费记录",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                            } else {
                                bills.forEach { bill ->
                                    ThinDivider(Modifier.padding(start = 16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(bill.title, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                formatBillTime(bill.time),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "¥%.2f".format(bill.amount),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ChartPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { openHuiLifeAlipay(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("在支付宝打开慧生活798")
                    }
                }

                item {
                    Text(
                        "启动/停止只会在你点击并确认后发送；不自动接水、不充值。余额与账单来自慧生活接口，以官方小程序为准。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    viewModel.confirmDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelStart() },
            title = { Text("启动 ${device.name}？") },
            text = {
                Text(
                    "设备会开始出水，费用按实际用量从校园钱包扣除。请确认已经放好容器。",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmStart() }) { Text("确认启动") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStart() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HuiLifeLoginCard(viewModel: HuiLifeViewModel) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = "水", color = ChartPrimary, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("登录慧生活798", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "手机号 + 短信验证码，不需要密码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformChip(
                    label = "查询登录",
                    selected = viewModel.platform == HuiLifePlatform.ACCOUNT,
                    modifier = Modifier.weight(1f)
                ) { viewModel.choosePlatform(HuiLifePlatform.ACCOUNT) }
                PlatformChip(
                    label = "设备控制",
                    selected = viewModel.platform == HuiLifePlatform.APP,
                    modifier = Modifier.weight(1f)
                ) { viewModel.choosePlatform(HuiLifePlatform.APP) }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = viewModel.phone,
                onValueChange = { viewModel.phone = it.filter(Char::isDigit).take(11) },
                label = { Text("手机号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.graphCode,
                    onValueChange = { viewModel.graphCode = it.take(8) },
                    label = { Text("图形验证码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 56.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.loadCaptcha() },
                    contentAlignment = Alignment.Center
                ) {
                    val captcha = viewModel.captcha
                    if (captcha != null) {
                        Image(
                            bitmap = captcha,
                            contentDescription = "图形验证码",
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        )
                    } else {
                        Text(
                            "获取验证码",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "图形验证码看不清时点图片刷新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = viewModel.smsCode,
                    onValueChange = { viewModel.smsCode = it.filter(Char::isDigit).take(8) },
                    label = { Text("短信验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { viewModel.sendSms() },
                    enabled = !viewModel.loading,
                    shape = RoundedCornerShape(10.dp)
                ) { Text(if (viewModel.smsSent) "重新发送" else "发送短信") }
            }
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { viewModel.login() },
                enabled = !viewModel.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (viewModel.platform == HuiLifePlatform.APP) {
                        "登录设备控制"
                    } else {
                        "登录查询"
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "查询端可看余额/设备/消费；设备控制端才能一键启动。两边的短信验证码不能共用，需要分别获取。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (viewModel.accountLoggedIn || viewModel.appLoggedIn) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { viewModel.closeLogin() },
                    contentPadding = PaddingValues(0.dp)
                ) { Text("返回已登录页面") }
            }
        }
    }
}

@Composable
private fun PlatformChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SessionCard(viewModel: HuiLifeViewModel) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = "账", color = CourseBlue, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("登录状态", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "查询端：${if (viewModel.accountLoggedIn) "已登录" else "未登录"} · " +
                            "设备端：${if (viewModel.appLoggedIn) "已登录" else "未登录"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!viewModel.accountLoggedIn) {
                    TextButton(
                        onClick = { viewModel.openLogin(HuiLifePlatform.ACCOUNT) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("登录查询端") }
                } else {
                    TextButton(
                        onClick = { viewModel.logout(HuiLifePlatform.ACCOUNT) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("退出查询端", color = ErrorRed) }
                }
                if (!viewModel.appLoggedIn) {
                    TextButton(
                        onClick = { viewModel.openLogin(HuiLifePlatform.APP) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("登录设备控制") }
                } else {
                    TextButton(
                        onClick = { viewModel.logout(HuiLifePlatform.APP) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("退出设备控制", color = ErrorRed) }
                }
            }
        }
    }
}

private fun formatBillTime(timestamp: Long): String {
    if (timestamp <= 0) return "--"
    return runCatching {
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    }.getOrDefault("--")
}

private fun openHuiLifeAlipay(context: Context) {
    val uri = Uri.parse(
        "alipays://platformapi/startapp?appId=$HUI_ALIPAY_APP_ID&page=pages/index/index"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "未安装支付宝，无法打开慧生活798", Toast.LENGTH_SHORT).show()
        }
}
