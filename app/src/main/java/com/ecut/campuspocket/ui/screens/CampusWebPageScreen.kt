package com.ecut.campuspocket.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.BuildConfig
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.ui.theme.ErrorRed
import kotlinx.coroutines.launch

/**
 * 内嵌学校官方网页（只读）。
 *
 * 用于尚未逆向出稳定接口的模块：把加密库里的会话 Cookie 注入 WebView，
 * 直接展示官方页面，不读取表单、不提交任何业务操作。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CampusWebPageScreen(
    title: String,
    url: String,
    onBack: () -> Unit,
    onOpenLogin: (() -> Unit)? = null,
    helperText: String? = null,
    extraCookieHosts: List<String> = emptyList(),
    desktopUserAgent: Boolean = true,
    userAgent: String? = null,
    detectSessionLost: Boolean = true,
    sourceName: String = "教务系统",
    openInBrowser: Boolean = false,
    saveProxySession: Boolean = false,
    saveSessionHost: String? = null,
    saveSessionSystemId: String = "ecut_session",
    userAgentSwitch: Boolean = false
) {
    val context = LocalContext.current
    val repo = AppContainer.authRepository
    val scope = rememberCoroutineScope()
    val host = remember(url) { Uri.parse(url).host.orEmpty() }
    val cookieKey = remember(url) { url }
    var cookiesReady by remember(cookieKey) { mutableStateOf(false) }
    var isLoading by remember(cookieKey) { mutableStateOf(true) }
    var sessionLost by remember(cookieKey) { mutableStateOf(false) }
    var desktopMode by remember(cookieKey) {
        mutableStateOf(userAgent == null && desktopUserAgent)
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(cookieKey) {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        (listOf(host) + extraCookieHosts).filter { it.isNotBlank() }.forEach { cookieHost ->
            val rows = runCatching {
                AppContainer.authRepository.loadCookiesForHost(cookieHost)
            }.getOrDefault(emptyList())
            val scheme = when {
                cookieHost.contains("authserver") ||
                    cookieHost.contains("atrust") ||
                    cookieHost.contains("libspace") -> "https"
                else -> if (url.startsWith("https://")) "https" else "http"
            }
            rows.forEach { row ->
                val path = row.path.ifBlank { "/" }
                manager.setCookie(
                    "$scheme://$cookieHost$path",
                    "${row.name}=${row.value}; path=$path"
                )
            }
        }
        manager.flush()
        cookiesReady = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 2.dp),
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
            Spacer(Modifier.weight(1f))
            if (userAgentSwitch) {
                TextButton(
                    onClick = {
                        desktopMode = !desktopMode
                        webViewRef.value?.settings?.userAgentString = if (desktopMode) {
                            CampusEndpoints.DESKTOP_USER_AGENT
                        } else {
                            CampusEndpoints.MOBILE_BROWSER_USER_AGENT
                        }
                        webViewRef.value?.reload()
                    }
                ) { Text(if (desktopMode) "手机" else "PC") }
            }
            if (openInBrowser) {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                ) { Text("浏览器") }
            }
            TextButton(onClick = { webViewRef.value?.reload() }) { Text("刷新") }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Text(
                text = helperText ?: "教务官方页面 · 只读展示，App 不会在此页面提交任何操作。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (sessionLost) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "教务登录已过期，页面要求重新登录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (onOpenLogin != null) {
                        TextButton(onClick = onOpenLogin) {
                            Text("去登录", color = ErrorRed)
                        }
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (!cookiesReady) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        if (BuildConfig.DEBUG) {
                            // 调试包允许通过 chrome://inspect / adb forward 观察页面请求，
                            // 便于后续把这些模块改成原生接口。
                            WebView.setWebContentsDebuggingEnabled(true)
                        }
                        val webView = WebView(context)
                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            setSupportMultipleWindows(false)
                            cacheMode = WebSettings.LOAD_DEFAULT
                            if (userAgent != null) {
                                userAgentString = userAgent
                            } else if (desktopUserAgent) {
                                userAgentString = CampusEndpoints.DESKTOP_USER_AGENT
                            }
                        }
                        val manager = CookieManager.getInstance()
                        manager.setAcceptCookie(true)
                        manager.setAcceptThirdPartyCookies(webView, true)
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                if (saveSessionHost != null) {
                                    val currentUrl = url.orEmpty()
                                    val currentHost = Uri.parse(currentUrl).host.orEmpty()
                                    if (currentHost == saveSessionHost) {
                                        val raw = CookieManager.getInstance()
                                            .getCookie(currentUrl)
                                        if (!raw.isNullOrBlank()) {
                                            scope.launch {
                                                runCatching {
                                                    repo.saveLoginCookies(
                                                        systemId = saveSessionSystemId,
                                                        primaryHost = saveSessionHost,
                                                        cookieByHost = mapOf(
                                                            saveSessionHost to raw
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (saveProxySession) {
                                    val currentUrl = url.orEmpty()
                                    val currentHost = Uri.parse(currentUrl).host.orEmpty()
                                    if (currentHost.endsWith(".atrust.ecut.edu.cn")) {
                                        val raw = CookieManager.getInstance()
                                            .getCookie(currentUrl)
                                        if (!raw.isNullOrBlank()) {
                                            scope.launch {
                                                runCatching {
                                                    repo.saveLoginCookies(
                                                        systemId = CampusEndpoints.SYSTEM_JW_PROXY,
                                                        primaryHost = currentHost,
                                                        cookieByHost = mapOf(
                                                            currentHost to raw
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (url.orEmpty().contains("atrust1.ecut.edu.cn/portal")) {
                                    // aTrust 门户在部分 WebView 里根容器高度塌成 0，补一层最小布局。
                                    view?.evaluateJavascript(PORTAL_HEIGHT_FIX_SCRIPT, null)
                                }
                                if (detectSessionLost) {
                                    val current = url.orEmpty()
                                    sessionLost = current.contains("login_slogin") ||
                                        current.contains("/authserver/") ||
                                        current.contains("login_sso")
                                }
                            }
                        }
                        webView.loadUrl(url)
                        webViewRef.value = webView
                        webView
                    }
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .height(2.dp)
                    )
                }
            }
        }

        Text(
            text = "页面内容由学校${sourceName}提供，加载失败时请先确认网络连接。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.apply {
                stopLoading()
                destroy()
            }
            webViewRef.value = null
        }
    }
}

/**
 * aTrust 门户在 App WebView 里 `100vh` 会算成 0（Chromium 内嵌视图的初始包含块高度为 0），
 * 导致整页高度塌陷。这里用 window.innerHeight 的像素值覆盖关键容器，并持续一段时间防路由重渲染。
 */
private const val PORTAL_HEIGHT_FIX_SCRIPT = """
(function(){
  function fix(){
    try{
      var H=Math.round(window.innerHeight||600);
      var s=document.getElementById('cp-portal-fix');
      if(!s){s=document.createElement('style');s.id='cp-portal-fix';document.head.appendChild(s);}
      s.textContent='html,body{height:'+H+'px!important;min-height:'+H+'px!important}'+
        '#app,.h-screen,.for-main-window,.main-page-wrapper{height:'+H+'px!important;'+
        'min-height:'+H+'px!important;width:100%!important}';
      document.documentElement.style.setProperty('height',H+'px','important');
      document.body.style.setProperty('height',H+'px','important');
      var app=document.querySelector('#app');
      if(app){app.style.setProperty('height',H+'px','important');}
    }catch(e){}
  }
  fix();
  var n=0;
  var timer=setInterval(function(){fix();n++;if(n>=12){clearInterval(timer);}},800);
})()
"""

