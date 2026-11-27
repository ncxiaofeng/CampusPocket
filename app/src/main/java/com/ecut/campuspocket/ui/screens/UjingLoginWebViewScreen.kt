package com.ecut.campuspocket.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.data.remote.ujing.UjingTokenExtractor
import com.ecut.campuspocket.network.CampusEndpoints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * U净免配置登录容器：
 * 1. 上方加载 U净 官方 H5 页面，由官方页面完成发送短信验证码（无需在 APK 内嵌 HMAC 密钥）；
 * 2. 收到短信后，直接在下方原生栏调用 `POST /api/v1/login`（该接口无需 HMAC 密钥与微信 tempToken），
 *    登录成功后将个人 JWT 加密保存并自动返回原生 U净 页面。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UjingLoginWebViewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var mobile by rememberSaveable { mutableStateOf("") }
    var captcha by rememberSaveable { mutableStateOf("") }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun cleanupWebStorage() {
        webViewRef.value?.evaluateJavascript(CLEAR_TOKEN_JS, null)
        runCatching {
            WebStorage.getInstance().deleteOrigin(UJING_ORIGIN)
            CookieManager.getInstance().flush()
        }
    }

    fun handleCapturedToken(jwt: String) {
        if (captured) return
        captured = true
        statusIsError = false
        statusMessage = "登录成功，正在保存 U净 会话…"
        scope.launch {
            runCatching {
                AppContainer.ujingRepository.saveToken(jwt)
            }.onSuccess {
                cleanupWebStorage()
                statusMessage = "已保存 U净 登录态，正在返回…"
                delay(300)
                onBack()
            }.onFailure { error ->
                captured = false
                statusIsError = true
                statusMessage = "保存失败：${error.message ?: "请重试"}"
            }
        }
    }

    fun submitNativeLogin() {
        if (isSubmitting || captured) return
        if (mobile.length != 11 || !mobile.all { it.isDigit() }) {
            statusIsError = true
            statusMessage = "请输入 11 位手机号"
            return
        }
        if (captcha.length < 4) {
            statusIsError = true
            statusMessage = "请输入收到的短信验证码"
            return
        }
        isSubmitting = true
        statusIsError = false
        statusMessage = "正在登录 U净…"
        scope.launch {
            runCatching {
                AppContainer.ujingRepository.login(mobile, captcha)
            }.onSuccess {
                captured = true
                cleanupWebStorage()
                statusIsError = false
                statusMessage = "登录成功，正在返回原生页面…"
                delay(300)
                onBack()
            }.onFailure { error ->
                statusIsError = true
                statusMessage = error.message ?: "登录失败，请检查验证码后重试"
            }
            isSubmitting = false
        }
    }

    fun probeToken(webView: WebView?) {
        if (captured || webView == null) return
        webView.evaluateJavascript(READ_TOKEN_JS) { raw ->
            val jwt = UjingTokenExtractor.extractJwtFromJsResult(raw)
            if (jwt != null) {
                handleCapturedToken(jwt)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (!captured) {
            delay(1000)
            probeToken(webViewRef.value)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.let { view ->
                view.stopLoading()
                view.destroy()
            }
            webViewRef.value = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
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
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "U净免配置登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "上方官方页发短信 · 下方填码直接登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            color = if (statusIsError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = statusMessage
                    ?: "第 1 步：在下方 U净 官方页输入手机号点「获取验证码」（无需点网页上的登录）。",
                style = MaterialTheme.typography.labelSmall,
                color = if (statusIsError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                runCatching { WebStorage.getInstance().deleteOrigin(UJING_ORIGIN) }
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.userAgentString = CampusEndpoints.MOBILE_BROWSER_USER_AGENT
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val scheme = request?.url?.scheme.orEmpty()
                            return scheme != "http" && scheme != "https"
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            probeToken(view)
                        }
                    }
                    webViewRef.value = this
                    loadUrl(CampusEndpoints.UJING_H5_LOGIN_URL)
                }
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "第 2 步：收到短信后，在这里直接登录冻小梨",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { value ->
                            mobile = value.filter { it.isDigit() }.take(11)
                        },
                        label = { Text("手机号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1.35f)
                    )
                    OutlinedTextField(
                        value = captcha,
                        onValueChange = { value ->
                            captcha = value.filter { it.isDigit() }.take(6)
                        },
                        label = { Text("验证码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.9f)
                    )
                    Button(
                        onClick = { submitNativeLogin() },
                        enabled = !isSubmitting && !captured,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("登录")
                        }
                    }
                }
            }
        }
    }
}

private const val UJING_ORIGIN = "https://wx.zhinengxiyifang.cn"

private const val READ_TOKEN_JS = """
(function() {
  try {
    return window.localStorage.getItem('token')
      || window.localStorage.getItem('wechatWorkH5-washer-token')
      || '';
  } catch (e) {
    return '';
  }
})();
"""

private const val CLEAR_TOKEN_JS = """
(function() {
  try {
    window.localStorage.removeItem('token');
    window.localStorage.removeItem('wechatWorkH5-washer-token');
    window.localStorage.removeItem('userInfo');
  } catch (e) {}
})();
"""
