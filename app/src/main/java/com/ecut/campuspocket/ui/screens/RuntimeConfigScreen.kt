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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.AppLinks
import com.ecut.campuspocket.data.ElectricityLinkParser
import com.ecut.campuspocket.data.RemoteClaimMode
import com.ecut.campuspocket.data.RemoteConfigClaim
import com.ecut.campuspocket.data.RemoteConfigClaimParser
import com.ecut.campuspocket.data.RuntimeConfig
import com.ecut.campuspocket.data.RuntimeConfigTextParser
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val initial = remember { store.get() }
    var electricity by remember { mutableStateOf(initial.electricityOperatorPwd) }
    var ujing by remember { mutableStateOf(initial.ujingHmacKey) }
    var huilife by remember { mutableStateOf(initial.huilifeAppSignSalt) }
    var sealedFields by remember { mutableStateOf(initial.sealedFields) }
    var reveal by remember { mutableStateOf(false) }
    var claiming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun claimRemoteConfig(claim: RemoteConfigClaim, source: String) {
        if (claiming) return
        claiming = true
        message = "正在安全拉取配置码…"
        scope.launch {
            try {
                AppContainer.remoteConfigClaimClient.claim(claim).fold(
                    onSuccess = { outcome ->
                        val current = store.get()
                        var nextElectricity = current.electricityOperatorPwd
                        var nextUjing = current.ujingHmacKey
                        var nextHuilife = current.huilifeAppSignSalt
                        val nextSealed = current.sealedFields.toMutableSet()
                        val importedLabels = mutableListOf<String>()

                        outcome.values[RuntimeConfigTextParser.KEY_ELECTRICITY]?.let {
                            nextElectricity = it
                            nextSealed.add("e")
                            importedLabels.add("电费")
                        }
                        outcome.values[RuntimeConfigTextParser.KEY_UJING]?.let {
                            nextUjing = it
                            nextSealed.add("u")
                            importedLabels.add("U净")
                        }
                        outcome.values[RuntimeConfigTextParser.KEY_HUILIFE]?.let {
                            nextHuilife = it
                            nextSealed.add("h")
                            importedLabels.add("798设备控制")
                        }

                        val merged = RuntimeConfig(
                            electricityOperatorPwd = nextElectricity,
                            ujingHmacKey = nextUjing,
                            huilifeAppSignSalt = nextHuilife,
                            sealedFields = nextSealed
                        )
                        runCatching { store.update(merged) }.fold(
                            onSuccess = {
                                electricity = merged.electricityOperatorPwd
                                ujing = merged.ujingHmacKey
                                huilife = merged.huilifeAppSignSalt
                                sealedFields = store.get().sealedFields
                                val items = importedLabels.joinToString("、")
                                message = when {
                                    outcome.mode == RemoteClaimMode.ONCE && outcome.burned ->
                                        "已从${source}安全导入（${items}）并销毁云端一次性凭证，返回即可生效"
                                    outcome.mode == RemoteClaimMode.ONCE ->
                                        "已从${source}安全导入（${items}），本机已加密锁定，返回即可生效"
                                    else ->
                                        "已从${source}安全导入（${items}，限时配置码），本机已加密锁定，返回即可生效"
                                }
                            },
                            onFailure = { err ->
                                message = "配置拉取成功，但本机加密保存失败：${err.message ?: "Keystore 不可用"}"
                            }
                        )
                    },
                    onFailure = { err ->
                        message = err.message ?: "配置码拉取失败，请检查网络后重试"
                    }
                )
            } finally {
                claiming = false
            }
        }
    }

    fun applyConfigText(text: String, source: String) {
        if (text.isBlank()) {
            message = "${source}内容为空"
            return
        }
        val remoteClaim = RemoteConfigClaimParser.parse(text)
        if (remoteClaim != null) {
            claimRemoteConfig(remoteClaim, source)
            return
        }
        val values = RuntimeConfigTextParser.parse(text)
        var found = 0
        val nextSealed = sealedFields.toMutableSet()
        values[RuntimeConfigTextParser.KEY_ELECTRICITY]?.let {
            electricity = it
            nextSealed.remove("e")
            found++
        }
        values[RuntimeConfigTextParser.KEY_UJING]?.let {
            ujing = it
            nextSealed.remove("u")
            found++
        }
        values[RuntimeConfigTextParser.KEY_HUILIFE]?.let {
            huilife = it
            nextSealed.remove("h")
            found++
        }
        sealedFields = nextSealed
        message = if (found == 0) {
            "没有识别到配置内容；支持安全配置码、key=value 或 JSON"
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
        sealedFields = sealedFields - "e"
        message = "已从链接提取 operatorPwd，检查后点保存"
    }

    fun save() {
        runCatching {
            val updated = RuntimeConfig(
                electricityOperatorPwd = electricity.trim(),
                ujingHmacKey = ujing.trim(),
                huilifeAppSignSalt = huilife.trim(),
                sealedFields = sealedFields
            )
            store.update(updated)
            sealedFields = store.get().sealedFields
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
                            "U净洗衣（官方网页免参登录）与慧生活798 查询端已默认可用。" +
                                "此处仅用于电费（operatorPwd）与 798 App 内一键启停设备（sign.salt），" +
                                "不编进 APK，只保存在本机加密存储。",
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
                        if ("e" in sealedFields && electricity.isNotBlank()) {
                            SealedConfigBadge(
                                onClear = {
                                    electricity = ""
                                    sealedFields = sealedFields - "e"
                                    message = "已清除电费配置，点「保存配置」生效或重新扫码导入"
                                }
                            )
                        } else {
                            ConfigField(
                                value = electricity,
                                onValueChange = { electricity = it },
                                label = "electricity.operator.pwd",
                                reveal = reveal
                            )
                        }
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
                            enabled = !claiming,
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) { Text("从剪贴板链接导入（需含 operatorPwd）") }
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text(
                            "U净洗衣 · HMAC key（公开版已免配置，可选）",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        if ("u" in sealedFields && ujing.isNotBlank()) {
                            SealedConfigBadge(
                                onClear = {
                                    ujing = ""
                                    sealedFields = sealedFields - "u"
                                    message = "已清除 U净配置，点「保存配置」生效"
                                }
                            )
                        } else {
                            ConfigField(
                                value = ujing,
                                onValueChange = { ujing = it },
                                label = "ujing.hmac.key",
                                reveal = reveal
                            )
                        }
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text("慧生活798 · 设备登录盐", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        if ("h" in sealedFields && huilife.isNotBlank()) {
                            SealedConfigBadge(
                                onClear = {
                                    huilife = ""
                                    sealedFields = sealedFields - "h"
                                    message = "已清除 798 设备盐配置，点「保存配置」生效"
                                }
                            )
                        } else {
                            ConfigField(
                                value = huilife,
                                onValueChange = { huilife = it },
                                label = "huilife.app.sign.salt",
                                reveal = reveal
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { save() },
                        enabled = !claiming,
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                    TextButton(
                        onClick = { importFromClipboard() },
                        enabled = !claiming,
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) { Text("从剪贴板导入") }
                }
            }

            item {
                OutlinedButton(
                    onClick = { launchConfigScan() },
                    enabled = !claiming,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (claiming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在安全拉取配置码…")
                    } else {
                        Text("扫码导入配置（支持一次性 / 限时安全码）")
                    }
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
                    Column {
                        Text(
                            "支持扫描分享者生成的一次性（阅后即焚）或限时安全配置二维码；" +
                                "扫码后将自动加密保存到本机并锁定防明文查看，无法二次转发。" +
                                "如需获取配置码或反馈问题，可加入 QQ 交流群 ${AppLinks.QQ_GROUP_NUMBER}。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(AppLinks.QQ_GROUP_NUMBER))
                                Toast.makeText(
                                    context,
                                    "已复制 QQ 群号 ${AppLinks.QQ_GROUP_NUMBER}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) { Text("复制交流群号（${AppLinks.QQ_GROUP_NUMBER}）") }
                    }
                }
            }

            item {
                TextButton(
                    onClick = {
                        runCatching { store.clear() }
                        electricity = ""
                        ujing = ""
                        huilife = ""
                        sealedFields = emptySet()
                        message = "已清空本机保存的三项配置"
                    },
                    enabled = !claiming,
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
private fun SealedConfigBadge(
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = TealPrimary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "已通过安全配置码加密导入",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = TealPrimary
            )
            Text(
                "已锁定不展示明文（本机加密生效中）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onClear,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("清除重填", color = ErrorRed, style = MaterialTheme.typography.labelMedium)
        }
    }
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
