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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.SavedLoginCredentials
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AutoLoginScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preferences = AppContainer.preferences
    val vault = AppContainer.credentialVault
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(preferences.autoLoginEnabled) }
    var hasSaved by remember { mutableStateOf(vault.hasCredentials) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            vault.load()?.let { username = it.username }
        }
    }

    fun saveAndTest() {
        if (username.isBlank() || password.isBlank()) {
            message = "请填写学号和密码"
            return
        }
        val testPassword = password
        val testUsername = username.trim()
        val saved = runCatching {
            vault.save(SavedLoginCredentials(testUsername, testPassword))
        }
        if (saved.isFailure) {
            message = "加密保存失败：${saved.exceptionOrNull()?.message ?: "Keystore 不可用"}"
            return
        }
        hasSaved = true
        password = ""
        testing = true
        enabled = false
        preferences.autoLoginEnabled = false
        preferences.autoLoginBlockedUntil = 0L
        message = "已加密保存，正在测试登录…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                AppContainer.casAutoLogin.login(testUsername, testPassword)
            }
            testing = false
            result.fold(
                onSuccess = {
                    enabled = true
                    preferences.autoLoginEnabled = true
                    preferences.autoLoginBlockedUntil = 0L
                    message = "保存成功，统一认证测试登录通过，自动登录已开启"
                },
                onFailure = {
                    enabled = false
                    preferences.autoLoginEnabled = false
                    message = "已保存，但测试登录未通过：${it.message ?: "未知错误"}。" +
                        "自动登录暂未开启；可以稍后重试，或手动打开开关。"
                }
            )
        }
    }

    fun clearSaved() {
        runCatching { vault.clear() }
        preferences.autoLoginEnabled = false
        enabled = false
        hasSaved = false
        username = ""
        password = ""
        message = "已删除本机保存的账号密码"
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
            Text("自动登录", style = MaterialTheme.typography.titleLarge)
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
                            IconBadge(text = "锁", color = TealPrimary, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "保存账号密码并自动登录",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "用 Android Keystore 加密，仅本机可用",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { value ->
                                    if (!value) {
                                        clearSaved()
                                    } else if (hasSaved) {
                                        enabled = true
                                        preferences.autoLoginEnabled = true
                                        message = "自动登录已开启"
                                    } else {
                                        enabled = true
                                        message = "请填写账号密码并点「保存并测试」"
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "统一认证会话失效时，App 会用这里的账号密码在后台完成一次官方网站登录。" +
                                "密码不会上传，也不会写入日志；关闭开关会立即删除密文和密钥。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                AppCard {
                    Column {
                        Text("统一身份认证账号", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.trim() },
                            label = { Text("学号 / 用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { saveAndTest() },
                                enabled = !testing,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (testing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("测试中")
                                } else {
                                    Text("保存并测试")
                                }
                            }
                            if (hasSaved) {
                                TextButton(
                                    onClick = { clearSaved() },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("删除", color = ErrorRed)
                                }
                            }
                        }
                        if (message != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                message.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                AppCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        IconBadge(text = "注", color = CourseBlue, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "学校登录页出现图形验证码、二次验证或密码变更时，自动登录会停止并回退到手动登录。" +
                                "自动登录只在本机、App 打开或同步电费时触发，不会在后台偷偷上传密码。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
