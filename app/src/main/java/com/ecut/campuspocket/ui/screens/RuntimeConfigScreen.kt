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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.RuntimeConfig
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber

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

    fun importFromClipboard() {
        val text = clipboard.getText()?.text.orEmpty()
        if (text.isBlank()) {
            message = "剪贴板是空的"
            return
        }
        val values = text.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null
                else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }
            .toMap()
        var found = 0
        values["electricity.operator.pwd"]?.takeIf { it.isNotBlank() }?.let {
            electricity = it
            found++
        }
        values["ujing.hmac.key"]?.takeIf { it.isNotBlank() }?.let {
            ujing = it
            found++
        }
        values["huilife.app.sign.salt"]?.takeIf { it.isNotBlank() }?.let {
            huilife = it
            found++
        }
        message = if (found == 0) {
            "没有识别到 key=value 行；请复制 local.properties 里的三行"
        } else {
            "已从剪贴板导入 $found 项，检查后点保存"
        }
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
                        "在电脑上打开项目的 local.properties，把 " +
                            "electricity.operator.pwd、ujing.hmac.key、huilife.app.sign.salt " +
                            "三行复制到手机剪贴板，再点「从剪贴板导入」即可。",
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
            }
        }
    }
}
