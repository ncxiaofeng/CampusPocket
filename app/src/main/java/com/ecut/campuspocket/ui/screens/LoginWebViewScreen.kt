package com.ecut.campuspocket.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.LoginTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 官方网页登录容器。
 *
 * 用户只在学校官方页面输入账号密码，App 不读取 DOM、不拦截按键，
 * 点击“完成登录”时才通过 CookieManager 取会话 Cookie 并加密保存。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginWebViewScreen(
    onBack: () -> Unit,
    target: LoginTarget = CampusEndpoints.JW_LOGIN
) {
    val repo = AppContainer.authRepository
    val scope = rememberCoroutineScope()
    val primaryHost = remember(target) { target.primaryHost }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var saved by remember { mutableStateOf(false) }
    var autoCompleting by remember { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun cookieNamesOf(raw: String): Set<String> = raw.split(";")
        .mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index).trim().lowercase()
        }
        .toSet()

    fun captureCookies(): Map<String, String> {
        val urls = linkedSetOf<String>()
        webViewRef.value?.url?.let(urls::add)
        urls.addAll(target.extraCookieUrls)
        // 无论从哪个系统登录，都顺手把 CAS 主凭据（CASTGC）带回去。
        urls.add(CampusEndpoints.AUTH_SERVER_LOGIN_URL)

        val manager = CookieManager.getInstance()
        val result = LinkedHashMap<String, String>()
        urls.forEach { url ->
            val host = Uri.parse(url).host ?: return@forEach
            val raw = manager.getCookie(url)
            if (!raw.isNullOrBlank()) {
                val existing = result[host]
                result[host] = if (existing.isNullOrBlank()) raw else "$existing; $raw"
            }
        }
        return result
    }

    fun completeLogin() {
        if (saved) return
        val cookieByHost = captureCookies()
        val primaryNames = cookieNamesOf(cookieByHost[primaryHost].orEmpty())
        val allNames = cookieByHost.values.flatMap { cookieNamesOf(it) }.toSet()
        val requiredPrimaryCookie = target.requiredPrimaryCookie
        val currentUrl = webViewRef.value?.url.orEmpty()
        val currentUri = runCatching { Uri.parse(currentUrl) }.getOrNull()
        val requiredPath = target.primaryPathPrefix.orEmpty()
        val onPrimaryPage = requiredPrimaryCookie == null || (
            currentUri != null &&
                currentUri.host == primaryHost &&
                (requiredPath.isEmpty() || currentUri.path.orEmpty().startsWith(requiredPath)) &&
                !currentUrl.contains("login_slogin") &&
                !currentUrl.contains("/authserver/")
            )
        val looksLoggedIn = when {
            requiredPrimaryCookie != null ->
                primaryNames.contains(requiredPrimaryCookie) && onPrimaryPage
            target.requiredAnyCookieNames.isNotEmpty() ->
                allNames.any { it in target.requiredAnyCookieNames }
            else ->
                allNames.any { it in setOf("castgc", "tgc", "jsessionid", "mod_auth_cas") }
        }
        if (cookieByHost.isEmpty() || !looksLoggedIn) {
            statusIsError = true
            statusMessage = if (requiredPrimaryCookie != null) {
                "尚未检测到教务系统会话。请先完成登录并进入课表页面，再点击“完成登录”。"
            } else {
                "未检测到登录凭证。请先在页面中完成登录，再点击“完成登录”。"
            }
            return
        }
        statusIsError = false
        statusMessage = "正在加密保存…"
        scope.launch {
            val result = runCatching {
                repo.saveLoginCookies(
                    systemId = target.systemId,
                    primaryHost = primaryHost,
                    cookieByHost = cookieByHost
                )
            }
            if (result.isSuccess) {
                // 登录任意系统都可能经过 CAS；只要出现 CASTGC，就更新一次主凭据。
                runCatching {
                    repo.saveCasMasterCookies(cookieByHost[CampusEndpoints.CAS_HOST].orEmpty())
                }
                if (primaryHost != CampusEndpoints.CAS_HOST) {
                    // 零信任代理域名（authserver-...atrust...）下拿到的 CASTGC 也镜像到 CAS 主域，
                    // 让“先登零信任”同样能一次性解锁电费 / 校园卡。
                    runCatching {
                        repo.saveCasMasterCookies(cookieByHost[primaryHost].orEmpty())
                    }
                }
                if (target.clearWebViewCookiesAfterSave) {
                    // 凭证已写入 SQLCipher，清除 WebView 的明文 Cookie 副本。
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                }
                saved = true
                statusMessage = "登录凭证已加密保存到本机"
                delay(900)
                onBack()
            } else {
                statusIsError = true
                statusMessage = "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            }
        }
    }

    fun restartLogin() {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies(null)
        manager.flush()
        val hosts = linkedSetOf(primaryHost)
        target.extraCookieUrls.mapNotNullTo(hosts) { Uri.parse(it).host }
        webViewRef.value?.url?.let { Uri.parse(it).host }?.let(hosts::add)
        scope.launch {
            runCatching { repo.clearSystem(target.systemId, hosts.filter { it.isNotBlank() }) }
        }
        saved = false
        statusMessage = null
        webViewRef.value?.loadUrl(target.startUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = target.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { restartLogin() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                Text("重新登录", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { completeLogin() },
                enabled = !saved,
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 6.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (saved) "已保存" else "完成登录",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔒", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "密码不在本地保存",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "登录在官方网页完成，App 仅保存加密后的会话 Cookie。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (target.helperText != null) {
            Text(
                text = target.helperText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
            )
        }

        if (statusMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (statusIsError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Text(
                    text = statusMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusIsError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
        }

        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val webView = WebView(context)
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportMultipleWindows(false)
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = CampusEndpoints.DESKTOP_USER_AGENT
                    }
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            val currentHost = Uri.parse(url.orEmpty()).host.orEmpty()
                            if (currentHost in target.autoCompleteHosts &&
                                !saved &&
                                !autoCompleting
                            ) {
                                autoCompleting = true
                                statusIsError = false
                                statusMessage = "认证已完成，正在保存会话…"
                                view?.postDelayed({ completeLogin() }, 800)
                            }
                            // 统一认证：已经离开 CAS 登录页且 CASTGC 出现，自动保存，
                            // 不必再让用户点“完成登录”。停在 CAS 页时不动，避免把失效凭据存回去。
                            if (target.systemId == CampusEndpoints.SYSTEM_CAS_MASTER &&
                                currentHost.isNotBlank() &&
                                currentHost != CampusEndpoints.CAS_HOST &&
                                !saved &&
                                !autoCompleting
                            ) {
                                val casRaw = CookieManager.getInstance()
                                    .getCookie(CampusEndpoints.AUTH_SERVER_LOGIN_URL)
                                    .orEmpty()
                                val hasTicket = cookieNamesOf(casRaw)
                                    .any { it in target.requiredAnyCookieNames }
                                if (hasTicket) {
                                    autoCompleting = true
                                    statusIsError = false
                                    statusMessage = "统一认证已完成，正在保存…"
                                    view?.postDelayed({ completeLogin() }, 500)
                                }
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                statusIsError = true
                                statusMessage =
                                    "网页加载失败：${error?.description ?: "网络错误"}"
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            if (request?.isForMainFrame == true) {
                                val code = errorResponse?.statusCode ?: 0
                                if (code >= 400) {
                                    isLoading = false
                                    statusIsError = true
                                    statusMessage = "网页返回 HTTP $code，请稍后重试。"
                                }
                            }
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            handler?.cancel()
                            isLoading = false
                            statusIsError = true
                            statusMessage = "HTTPS 证书校验失败，已停止加载。"
                        }
                    }
                    webViewRef.value = webView
                    if (target.preloadStoredCookies) {
                        scope.launch {
                            val manager = CookieManager.getInstance()
                            manager.setAcceptCookie(true)
                            val hosts = linkedSetOf(primaryHost, CampusEndpoints.CAS_HOST)
                            target.extraCookieUrls.mapNotNullTo(hosts) { Uri.parse(it).host }
                            hosts.filter { it.isNotBlank() }.forEach { host ->
                                val rows = runCatching {
                                    repo.loadCookiesForHost(host)
                                }.getOrDefault(emptyList())
                                val scheme = if (host.contains("authserver")) "https" else "http"
                                rows.forEach { row ->
                                    val path = row.path.ifBlank { "/" }
                                    manager.setCookie(
                                        "$scheme://$host$path",
                                        "${row.name}=${row.value}; path=$path"
                                    )
                                }
                            }
                            manager.flush()
                            webView.loadUrl(target.startUrl)
                        }
                    } else {
                        webView.loadUrl(target.startUrl)
                    }
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
