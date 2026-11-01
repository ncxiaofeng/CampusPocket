package com.ecut.campuspocket.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.ElectricityLinkParser
import com.ecut.campuspocket.data.RuntimeConfig
import com.ecut.campuspocket.data.RuntimeConfigTextParser
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig

/**
 * 运行期第三方参数。APK 不再内嵌这些值，升级覆盖安装也不会丢。
 */
@Composable
fun RuntimeConfigScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val store = AppContainer.runtimeConfig
    val clipboard = LocalClipboardManager.current
    val initial = remember { store.get() }
    var electricity by remember { mutableStateOf(initial.electricityOperatorPwd) }
    var ujing by remember { mutableStateOf(initial.ujingHmacKey) }
    var huilife by remember { mutableStateOf(initial.huilifeAppSignSalt) }
    var reveal by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun applyConfigText(text: String, source: String) {
        if (text.isBlank()) {
            message = "${source}内容为空"
            return
        }
        val values = RuntimeConfigTextParser.parse(text)
        var found = 0
        values[RuntimeConfigTextParser.KEY_ELECTRICITY]?.let {
            electricity = it
            found++
        }
        values[RuntimeConfigTextParser.KEY_UJING]?.let {
            ujing = it
            found++
        }
        values[RuntimeConfigTextParser.KEY_HUILIFE]?.let {
            huilife = it
            found++
        }
        message = if (found == 0) {
            "没有识别到三项参数；需要 key=value 三行或 JSON"
        } else {
            "已从${source}导入 $found 项，检查后点保存"
        }
    }

    fun importFromClipboard() {
        applyConfigText(clipboard.getText()?.text.orEmpty(), "剪贴板")
    }

    val scanner = rememberLauncherForActivityResult(ScanCustomCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val raw = result.content.rawValue.orEmpty()
                applyConfigText(raw, "二维码")
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
            scanner.launch(configScanner())
        } else {
            Toast.makeText(context, "需要相机权限才能扫码", Toast.LENGTH_LONG).show()
        }
    }

    fun launchConfigScan() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanner.launch(configScanner())
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun importElectricityLink() {
        val text = clipboard.getText()?.text.orEmpty()
        val pwd = ElectricityLinkParser.extractOperatorPwd(text)
        if (pwd.isNullOrBlank()) {
            message = "没找到 operatorPwd；请确认复制的是宿舍缴费入口链接"
            return
        }
        electricity = pwd
        message = "已从链接提取 operatorPwd，检查后点保存"
    }

    fun save() {
        runCatching {
            store.update(
                RuntimeConfig(
                    electricityOperatorPwd = electricity.trim(),
                    ujingHmacKey = ujing.trim(),
                    huilifeAppSignSalt = huilife.trim()
                )
            )
        }.fold(
            onSuccess = { message = "已加密保存，返回后重新同步即可" },
            onFailure = { message = "保存失败：${it.message ?: "Keystore 不可用"}" }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
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
            Text("高级配置", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(text = "配", color = WarningAmber, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "第三方系统参数",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "不是你的账号密码；只保存在本机加密存储",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = reveal, onCheckedChange = { reveal = it })
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "这些参数来自学校 H5 / 第三方客户端，不再编进 APK，避免公开仓库泄露。" +
                                "换手机或重装后重新填写即可。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text("电费 · operatorPwd", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        ConfigField(
                            value = electricity,
                            onValueChange = { electricity = it },
                            label = "electricity.operator.pwd",
                            reveal = reveal
                        )
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text("电费官方入口", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "今日校园内的缴费页不能直接导出链接。如果你手上有带 operatorPwd 的" +
                                "完整链接（例如别人私下给的），可以粘贴导入；否则请直接用今日校园查电费。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { importElectricityLink() },
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) { Text("从剪贴板链接导入（需含 operatorPwd）") }
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text("U净洗衣 · HMAC key", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        ConfigField(
                            value = ujing,
                            onValueChange = { ujing = it },
                            label = "ujing.hmac.key",
                            reveal = reveal
                        )
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text("慧生活798 · 设备登录盐", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        ConfigField(
                            value = huilife,
                            onValueChange = { huilife = it },
                            label = "huilife.app.sign.salt",
                            reveal = reveal
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { save() },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                    TextButton(
                        onClick = { importFromClipboard() },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("从剪贴板导入") }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { launchConfigScan() },
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) { Text("扫码导入配置") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "二维码内容为三项 key=value",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (message != null) {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                message.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { message = null }) { Text("知道了") }
                        }
                    }
                }
            }

            item {
                AppCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
                    Text(
                        "电费可粘贴带 operatorPwd 的完整链接自动提取；也可以让熟人扫你生成的" +
                            "配置二维码导入。二维码包含共享参数，请只私下发给信任的人。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                TextButton(
                    onClick = {
                        runCatching { store.clear() }
                        electricity = ""
                        ujing = ""
                        huilife = ""
                        message = "已清空本机保存的三项配置"
                    },
                    contentPadding = PaddingValues(0.dp)
                ) { Text("清空已保存配置", color = ErrorRed) }
            }
        }
    }
}

private fun configScanner(): ScannerConfig = ScannerConfig.build {
    setBarcodeFormats(listOf(BarcodeFormat.QR_CODE))
    setHapticSuccessFeedback(true)
    setShowTorchToggle(true)
    setShowCloseButton(true)
    setKeepScreenOn(true)
}

@Composable
private fun ConfigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    reveal: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None
        else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun MissingConfigScreen(
    title: String,
    message: String,
    onOpenConfig: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 2.dp),
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
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(text = "配", color = WarningAmber, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("需要先配置第三方参数", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onOpenConfig, modifier = Modifier.fillMaxWidth()) {
                    Text("去「高级配置」填写")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { CampusTodayLauncher.open(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开今日校园查电费")
                }
            }
        }
    }
}
