package com.ecut.campuspocket.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.remote.huilife.HuiLifeDeviceKind
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
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import kotlinx.coroutines.delay
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
    onOpenConfig: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HuiLifeViewModel = viewModel(factory = HuiLifeViewModel.Factory)
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dashboard = viewModel.dashboard
    val loggedIn = viewModel.accountLoggedIn || viewModel.appLoggedIn
    val showLogin = viewModel.loginFormVisible || !loggedIn
    val configVersion by AppContainer.runtimeConfig.version.collectAsStateWithLifecycle()
    val hasAppControlConfig = remember(configVersion) {
        AppContainer.runtimeConfig.get().hasHuiLife
    }

    val scanner = rememberLauncherForActivityResult(ScanCustomCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val raw = result.content.rawValue.orEmpty()
                if (raw.isBlank()) {
                    Toast.makeText(context, "二维码内容为空", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addScannedDevice(raw)
                }
            }

            is QRResult.QRError -> Toast.makeText(
                context,
                "扫码失败：${result.exception.localizedMessage ?: "请调整距离后重试"}",
                Toast.LENGTH_LONG
            ).show()

            QRResult.QRMissingPermission -> Toast.makeText(
                context,
                "需要相机权限才能扫码",
                Toast.LENGTH_LONG
            ).show()

            QRResult.QRUserCanceled -> Unit
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanner.launch(scannerConfig())
        } else {
            Toast.makeText(context, "需要相机权限才能扫码", Toast.LENGTH_LONG).show()
        }
    }

    fun launchScan() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanner.launch(scannerConfig())
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // 状态轮询：页面处于前台时每 10 秒刷新一次；退到后台/离开页面自动暂停。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(
        lifecycleOwner,
        loggedIn,
        viewModel.dashboard?.devices?.size,
        viewModel.scannedDevices.size
    ) {
        if (!loggedIn) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refreshStatuses()
                delay(10_000)
            }
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
                    text = if (viewModel.appLoggedIn || hasAppControlConfig) {
                        "饮水 / 吹风 / 淋浴 · 扫码启动 · 仅支持停止自己启动的设备"
                    } else {
                        "饮水 / 吹风 / 淋浴 · 钱包余额与设备实时状态"
                    },
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
                item {
                    HuiLifeLoginCard(
                        viewModel = viewModel,
                        hasAppControlConfig = hasAppControlConfig,
                        onOpenConfig = onOpenConfig
                    )
                }
            } else {
                item {
                    SessionCard(
                        viewModel = viewModel,
                        hasAppControlConfig = hasAppControlConfig,
                        onOpenConfig = onOpenConfig
                    )
                }
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
                                    "${viewModel.allDevices.size} 台",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { launchScan() },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("扫码")
                                }
                            }
                            val devices = viewModel.allDevices
                            if (devices.isEmpty()) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    Text(
                                        when {
                                            !viewModel.appLoggedIn && hasAppControlConfig ->
                                                "收藏设备列表需要「设备控制」登录；或等有消费记录后自动显示。"
                                            !viewModel.appLoggedIn ->
                                                "暂无常用设备记录；产生接水/吹风消费或扫码添加设备后会显示实时状态，也可直接点下方跳转支付宝小程序使用。"
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
                                    if (!viewModel.appLoggedIn && hasAppControlConfig) {
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
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { launchScan() },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 7.dp
                                        )
                                    ) {
                                        Icon(
                                            Icons.Filled.QrCodeScanner,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("扫码添加设备")
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            } else {
                                devices.forEachIndexed { index, device ->
                                    val status = viewModel.statusFor(device.id)
                                    val statusColor = when (status?.label) {
                                        "使用中", "启动中" -> WarningAmber
                                        "离线", "禁用" -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> ChartPrimary
                                    }
                                    ThinDivider(Modifier.padding(start = 16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val (deviceIcon, deviceIconColor) = when (device.kind) {
                                            HuiLifeDeviceKind.HAIR_DRYER -> Icons.Outlined.Air to WarningAmber
                                            HuiLifeDeviceKind.SHOWER -> Icons.Outlined.Shower to CourseBlue
                                            HuiLifeDeviceKind.LAUNDRY -> Icons.Outlined.LocalLaundryService to CourseTeal
                                            HuiLifeDeviceKind.WATER -> Icons.Outlined.WaterDrop to ChartPrimary
                                            HuiLifeDeviceKind.OTHER -> Icons.Outlined.QrCode2 to CourseBlue
                                        }
                                        IconBadge(icon = deviceIcon, color = deviceIconColor, size = 34.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { viewModel.openRename(device) }
                                        ) {
                                            Text(device.name, style = MaterialTheme.typography.titleSmall)
                                            val isUnnamed = device.name.startsWith("未命名设备") ||
                                                device.name.startsWith("扫码设备")
                                            Text(
                                                buildString {
                                                    append(device.detail.ifBlank { device.id })
                                                    if (status != null) {
                                                        append(" · ").append(status.label)
                                                    }
                                                    if (isUnnamed) {
                                                        append(" · 点此备注")
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (status != null) {
                                                    statusColor
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        if (viewModel.appLoggedIn) {
                                            if (viewModel.canStop(device.id)) {
                                                Button(
                                                    onClick = { viewModel.askStop(device) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = ErrorRed
                                                    ),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                ) { Text("停止") }
                                            } else if (viewModel.isScanned(device.id)) {
                                                TextButton(
                                                    onClick = { viewModel.removeScannedDevice(device) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                                ) {
                                                    Text(
                                                        "取消",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (viewModel.canStart(device.id)) {
                                                    Button(
                                                        onClick = { viewModel.askStart(device) },
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(
                                                            horizontal = 12.dp,
                                                            vertical = 6.dp
                                                        )
                                                    ) { Text("启动") }
                                                } else if (status != null) {
                                                    Text(
                                                        status.label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = statusColor
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    "仅查看 · 扫码后可启动",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        } else {
                                            Text(
                                                if (hasAppControlConfig) {
                                                    "需设备登录"
                                                } else {
                                                    status?.label ?: "仅查看"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (hasAppControlConfig) WarningAmber else statusColor
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
                        "只有你扫过二维码的设备才显示「启动」；仅支持停止确认由你本人启动的运行中设备，避免误停他人设备；不自动接水、不充值。" +
                            "余额与账单来自慧生活接口，以官方小程序为准。",
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
                val startHint = when (device.kind) {
                    HuiLifeDeviceKind.HAIR_DRYER ->
                        "吹风机将会通电启动，费用按实际使用时长从校园钱包扣除。请确认你就在设备旁边。"
                    HuiLifeDeviceKind.SHOWER ->
                        "淋浴设备将会启动，费用按实际用量从校园钱包扣除。请确认你就在设备旁边。"
                    HuiLifeDeviceKind.LAUNDRY ->
                        "洗衣设备将会启动，费用按实际程序从校园钱包扣除。请确认已经放好衣物。"
                    else ->
                        "设备将会启动，费用按实际用量从校园钱包扣除。请确认已经做好准备。"
                }
                Text(startHint, style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmStart() }) { Text("确认启动") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStart() }) { Text("取消") }
            }
        )
    }

    viewModel.confirmStopDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelStop() },
            title = { Text("停止 ${device.name}？") },
            text = {
                Text(
                    "确认向该设备发送停止指令并结束本次使用？仅会停止由你本人启动的设备，账单将在结束后自动结算。",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmStop() },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("确认停止") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelStop() }) { Text("取消") }
            }
        )
    }

    viewModel.renameTarget?.let { device ->
        var draft by remember(device.id) {
            mutableStateOf(viewModel.customNameFor(device.id).ifBlank { device.name })
        }
        AlertDialog(
            onDismissRequest = { viewModel.dismissRename() },
            title = { Text("备注设备名称") },
            text = {
                Column {
                    Text(
                        "设备编号：${device.id}。留空保存可恢复接口默认名称。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(32) },
                        label = { Text("设备名称 / 备注") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.saveCustomName(device.id, draft) }) {
                    Text("保存")
                }
            },
            dismissButton = {
                Row {
                    if (viewModel.customNameFor(device.id).isNotBlank()) {
                        TextButton(onClick = { viewModel.saveCustomName(device.id, "") }) {
                            Text("恢复默认")
                        }
                    }
                    TextButton(onClick = { viewModel.dismissRename() }) { Text("取消") }
                }
            }
        )
    }
}

@Composable
private fun HuiLifeLoginCard(
    viewModel: HuiLifeViewModel,
    hasAppControlConfig: Boolean,
    onOpenConfig: () -> Unit
) {
    val appControlBlocked = viewModel.platform == HuiLifePlatform.APP && !hasAppControlConfig
    val context = androidx.compose.ui.platform.LocalContext.current
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = "水", color = ChartPrimary, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("登录慧生活798", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (hasAppControlConfig) {
                            "手机号 + 短信验证码即可查余额、设备状态、账单并一键启动"
                        } else {
                            "需在高级配置导入 798 配置码后登录，也可一键跳转支付宝使用"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformChip(
                    label = if (hasAppControlConfig) "设备控制（推荐）" else "设备控制（需配置）",
                    selected = viewModel.platform == HuiLifePlatform.APP,
                    modifier = Modifier.weight(1f)
                ) { viewModel.choosePlatform(HuiLifePlatform.APP) }
                PlatformChip(
                    label = "查询端（旧通道）",
                    selected = viewModel.platform == HuiLifePlatform.ACCOUNT,
                    modifier = Modifier.weight(1f)
                ) { viewModel.choosePlatform(HuiLifePlatform.ACCOUNT) }
            }
            Spacer(Modifier.height(12.dp))

            if (appControlBlocked) {
                Text(
                    "慧生活798 服务端已停用免配置旧登录通道，目前需使用官方客户端签名协议（3.1.9）登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "公开版默认不内置签名参数。请在「高级配置」导入 798 配置码即可恢复完整余额/状态查询与一键启停，或直接跳转支付宝小程序使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onOpenConfig,
                        modifier = Modifier.weight(1f)
                    ) { Text("去高级配置") }
                    TextButton(onClick = { openHuiLifeAlipay(context) }) { Text("打开支付宝") }
                }
            } else {
                if (viewModel.platform == HuiLifePlatform.ACCOUNT) {
                    Text(
                        "提示：旧查询通道近期已被慧生活798 服务端限制（可能报「请升级最新版应用」），建议优先使用「设备控制」登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningAmber
                    )
                    Spacer(Modifier.height(10.dp))
                }
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
                            "尝试登录旧查询通道"
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "「设备控制」一次登录即可查看余额、设备实时状态、消费记录并支持扫码启停。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
private fun SessionCard(
    viewModel: HuiLifeViewModel,
    hasAppControlConfig: Boolean,
    onOpenConfig: () -> Unit
) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = "账", color = CourseBlue, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("登录状态", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (hasAppControlConfig || viewModel.appLoggedIn) {
                            "查询端：${if (viewModel.accountLoggedIn) "已登录" else "未登录"} · " +
                                "设备端：${if (viewModel.appLoggedIn) "已登录" else "未登录"}"
                        } else {
                            "查询端：${if (viewModel.accountLoggedIn) "已登录（余额 / 设备状态 / 账单）" else "未登录"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (viewModel.appLoggedIn) {
                    TextButton(
                        onClick = { viewModel.logout(HuiLifePlatform.APP) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("退出设备控制", color = ErrorRed) }
                } else if (hasAppControlConfig) {
                    TextButton(
                        onClick = { viewModel.openLogin(HuiLifePlatform.APP) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("登录设备控制") }
                } else {
                    TextButton(
                        onClick = onOpenConfig,
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("去高级配置") }
                }
                if (viewModel.accountLoggedIn) {
                    TextButton(
                        onClick = { viewModel.logout(HuiLifePlatform.ACCOUNT) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("退出查询端", color = ErrorRed) }
                } else if (!viewModel.appLoggedIn) {
                    TextButton(
                        onClick = { viewModel.openLogin(HuiLifePlatform.ACCOUNT) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) { Text("旧查询通道") }
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

private fun scannerConfig(): ScannerConfig = ScannerConfig.build {
    // 只识别二维码，减少无关格式分析；图片只在本地处理。
    setBarcodeFormats(listOf(BarcodeFormat.QR_CODE))
    setHapticSuccessFeedback(true)
    setShowTorchToggle(true)
    setShowCloseButton(true)
    setKeepScreenOn(true)
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
