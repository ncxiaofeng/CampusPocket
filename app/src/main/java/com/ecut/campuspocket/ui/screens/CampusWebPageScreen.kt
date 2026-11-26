package com.ecut.campuspocket.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.BuildConfig
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.CasAutoLogin
import com.ecut.campuspocket.network.CasSsoManager
import com.ecut.campuspocket.network.SessionRecoveryResult
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.isGlassDark
import com.ecut.campuspocket.ui.theme.ErrorRed
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 内嵌官方页的无感自动登录模式。 */
enum class WebAutoLoginMode {
    NONE,
    /** 直连统一认证（办事大厅 / 校园卡 / 校内教务）。 */
    CAS,
    /** 零信任门户（atrust1.ecut.edu.cn）。 */
    PORTAL
}

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
    /** 只有当前页面路径以该前缀开头时才保存，避免把 /sso 的中间会话当成教务会话。 */
    saveSessionPathPrefix: String? = null,
    /** Cookie 在库里保存的 path，教务为 /jwglxt。 */
    saveSessionPath: String = "/",
    userAgentSwitch: Boolean = false,
    rememberCasLogin: Boolean = false,
    autoLoginMode: WebAutoLoginMode = WebAutoLoginMode.NONE,
    /** 为学习通等旧移动端页面修正异常 viewport / rem 放缩，并支持页内打开新窗口链接。 */
    mobileViewportFix: Boolean = false,
    /** 提供「清爽阅读 / 原网页」原生前端壳子切换（把页面正文/题目提取成液态玻璃卡片）。 */
    readerModeAvailable: Boolean = false
) {
    val context = LocalContext.current
    val repo = AppContainer.authRepository
    val recoveryManager = AppContainer.sessionRecoveryManager
    val scope = rememberCoroutineScope()
    val targetUrl = url
    val host = remember(targetUrl) { Uri.parse(targetUrl).host.orEmpty() }
    val cookieKey = remember(targetUrl) { targetUrl }
    var cookiesReady by remember(cookieKey) { mutableStateOf(false) }
    var isLoading by remember(cookieKey) { mutableStateOf(true) }
    var sessionLost by remember(cookieKey) { mutableStateOf(false) }
    var autoLoginBanner by remember(cookieKey) { mutableStateOf<String?>(null) }
    var autoRecoveryAttempted by remember(cookieKey) { mutableStateOf(false) }
    var lastCasTicket by remember(cookieKey) { mutableStateOf<String?>(null) }
    var readerMode by remember(cookieKey) { mutableStateOf(readerModeAvailable) }
    var readerSnapshot by remember(cookieKey) { mutableStateOf<WebReaderSnapshot?>(null) }
    var desktopMode by remember(cookieKey) {
        mutableStateOf(userAgent == null && desktopUserAgent)
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun extractReaderContent(view: WebView?) {
        if (!readerModeAvailable || view == null) return
        view.evaluateJavascript(READER_EXTRACT_SCRIPT) { raw ->
            parseReaderSnapshot(raw)?.let { readerSnapshot = it }
        }
    }

    val cookieHosts = remember(host, extraCookieHosts, autoLoginMode) {
        buildList {
            if (host.isNotBlank()) add(host)
            add(CampusEndpoints.CAS_HOST)
            if (autoLoginMode == WebAutoLoginMode.PORTAL) {
                add(CasAutoLogin.ATRUST_PORTAL_HOST)
                add(CasAutoLogin.ATRUST_CAS_HOST)
                add(CampusEndpoints.JW_PROXY_HOST)
            }
            addAll(extraCookieHosts)
        }.filter { it.isNotBlank() }.distinct()
    }

    suspend fun syncStoredCookiesToWebView() {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        cookieHosts.forEach { cookieHost ->
            val rows = runCatching {
                repo.loadCookiesForHost(cookieHost)
            }.getOrDefault(emptyList())
            val scheme = when {
                cookieHost.contains("authserver") ||
                    cookieHost.contains("atrust") ||
                    cookieHost.contains("libspace") ||
                    cookieHost.contains("ehall") -> "https"
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
    }

    LaunchedEffect(cookieKey) {
        if (autoLoginMode != WebAutoLoginMode.NONE && recoveryManager.isAutoLoginReady()) {
            val needsPreRecover = withContext(Dispatchers.IO) {
                when (autoLoginMode) {
                    WebAutoLoginMode.PORTAL ->
                        repo.loadCookiesForHost(CasAutoLogin.ATRUST_PORTAL_HOST).isEmpty()
                    WebAutoLoginMode.CAS ->
                        !AppContainer.casSsoManager.hasMasterSession()
                    WebAutoLoginMode.NONE -> false
                }
            }
            if (needsPreRecover) {
                autoLoginBanner = "正在后台无感登录${sourceName}…"
                val result = runCatching {
                    when (autoLoginMode) {
                        WebAutoLoginMode.PORTAL -> recoveryManager.recoverPortalSession()
                        WebAutoLoginMode.CAS -> recoveryManager.recoverCasSession()
                        WebAutoLoginMode.NONE -> SessionRecoveryResult.NoCredentials
                    }
                }.getOrElse { SessionRecoveryResult.Failed(it.message ?: "自动登录失败") }
                autoLoginBanner = when (result) {
                    is SessionRecoveryResult.AlreadyValid,
                    is SessionRecoveryResult.AutoLoggedIn -> null
                    is SessionRecoveryResult.Failed -> "自动登录暂停：${result.message}，可手动登录"
                    is SessionRecoveryResult.NoCredentials -> null
                }
            }
        }
        syncStoredCookiesToWebView()
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val compactPadding = PaddingValues(horizontal = 6.dp)
            if (readerModeAvailable) {
                TextButton(
                    onClick = {
                        readerMode = !readerMode
                        if (readerMode) extractReaderContent(webViewRef.value)
                    },
                    contentPadding = compactPadding
                ) { Text(if (readerMode) "原网页" else "阅读版") }
            }
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
                    },
                    contentPadding = compactPadding
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
                    },
                    contentPadding = compactPadding
                ) { Text("浏览器") }
            }
            TextButton(
                onClick = { webViewRef.value?.reload() },
                contentPadding = compactPadding
            ) { Text("刷新") }
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

        if (autoLoginBanner != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = autoLoginBanner.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
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
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            textZoom = 100
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            if (userAgent != null) {
                                userAgentString = userAgent
                            } else if (desktopUserAgent) {
                                userAgentString = CampusEndpoints.DESKTOP_USER_AGENT
                            }
                        }
                        val manager = CookieManager.getInstance()
                        manager.setAcceptCookie(true)
                        manager.setAcceptThirdPartyCookies(webView, true)
                        webView.webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (BuildConfig.DEBUG && consoleMessage != null) {
                                    Log.d(
                                        "CxWebDiag",
                                        "console ${consoleMessage.messageLevel()}: ${consoleMessage.message()}"
                                    )
                                }
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }
                        webView.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val nextUrl = request?.url?.toString().orEmpty()
                                if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
                                    return false
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                // 从任意学校官方页登录后，顺手把 CASTGC 存成统一主凭据。
                                // 只在 ticket 值变化时写库：首次加载可能还是注入的旧值，
                                // 用户登录后 CAS 会换新值，这里会再存一次。
                                val casRaw = CookieManager.getInstance()
                                    .getCookie(CampusEndpoints.AUTH_SERVER_LOGIN_URL)
                                    .orEmpty()
                                val ticket = CasSsoManager.casTicketValue(casRaw)
                                if (ticket != null && ticket != lastCasTicket) {
                                    lastCasTicket = ticket
                                    AppContainer.appScope.launch {
                                        runCatching { repo.saveCasMasterCookies(casRaw) }
                                    }
                                }
                                if (saveSessionHost != null) {
                                    val currentUrl = url.orEmpty()
                                    val currentUri = Uri.parse(currentUrl)
                                    val currentHost = currentUri.host.orEmpty()
                                    val currentPath = currentUri.path.orEmpty()
                                    val pathMatches = saveSessionPathPrefix == null ||
                                        currentPath.startsWith(saveSessionPathPrefix)
                                    Log.d(
                                        "JwDiag",
                                        "pageFinished host=$currentHost path=$currentPath " +
                                            "saveHost=$saveSessionHost prefix=$saveSessionPathPrefix " +
                                            "match=$pathMatches"
                                    )
                                    if (currentHost == saveSessionHost && pathMatches) {
                                        val manager = CookieManager.getInstance()
                                        val raw = scopedCookieHeader(
                                            manager = manager,
                                            url = currentUrl,
                                            host = currentHost,
                                            pathPrefix = saveSessionPathPrefix
                                        )
                                        if (raw.isNotBlank()) {
                                            AppContainer.appScope.launch {
                                                runCatching {
                                                    repo.saveLoginCookies(
                                                        systemId = saveSessionSystemId,
                                                        primaryHost = saveSessionHost,
                                                        cookieByHost = mapOf(
                                                            saveSessionHost to raw
                                                        ),
                                                        pathByHost = mapOf(
                                                            saveSessionHost to saveSessionPath
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                val finishedUrl = url.orEmpty()
                                val finishedHost = Uri.parse(finishedUrl).host.orEmpty()
                                val onCasLoginPage = finishedUrl.contains("/authserver/login")
                                if (onCasLoginPage &&
                                    autoLoginMode != WebAutoLoginMode.NONE &&
                                    !autoRecoveryAttempted &&
                                    recoveryManager.isAutoLoginReady()
                                ) {
                                    // 门户或官方页会话失效并跳回 CAS 登录页：后台用保存的账号密码无感重登，
                                    // 再把新会话 Cookie 灌进 WebView 并自动回到原目标页。
                                    autoRecoveryAttempted = true
                                    val isPortalCas = autoLoginMode == WebAutoLoginMode.PORTAL ||
                                        finishedHost.contains("atrust", ignoreCase = true)
                                    autoLoginBanner = "会话已失效，正在后台无感登录…"
                                    scope.launch {
                                        val res = runCatching {
                                            if (isPortalCas) {
                                                recoveryManager.recoverPortalSession(forceRetry = true)
                                            } else {
                                                recoveryManager.recoverCasSession(forceRetry = true)
                                            }
                                        }.getOrElse {
                                            SessionRecoveryResult.Failed(it.message ?: "自动登录失败")
                                        }
                                        when (res) {
                                            is SessionRecoveryResult.AlreadyValid,
                                            is SessionRecoveryResult.AutoLoggedIn -> {
                                                autoLoginBanner = "已自动登录，正在进入${sourceName}…"
                                                syncStoredCookiesToWebView()
                                                view?.loadUrl(targetUrl)
                                            }
                                            is SessionRecoveryResult.Failed -> {
                                                autoLoginBanner = "自动登录暂停：${res.message}，请手动登录"
                                            }
                                            is SessionRecoveryResult.NoCredentials -> {
                                                autoLoginBanner = null
                                            }
                                        }
                                    }
                                } else if (!onCasLoginPage &&
                                    autoLoginBanner?.startsWith("已自动登录") == true
                                ) {
                                    autoLoginBanner = null
                                }
                                if (rememberCasLogin && onCasLoginPage) {
                                    view?.evaluateJavascript(REMEMBER_ME_SCRIPT, null)
                                }
                                if (saveProxySession) {
                                    val currentUrl = url.orEmpty()
                                    val currentHost = Uri.parse(currentUrl).host.orEmpty()
                                    // 只有真正映射到教务的代理域名才持有 JW 代理会话。
                                    // 门户主页（atrust1.ecut.edu.cn）的 Cookie 存下来会误判“代理已就绪”，
                                    // 之后原生同步会一直拿不到 /jwglxt 会话。
                                    if (currentHost == CampusEndpoints.JW_PROXY_HOST) {
                                        val manager = CookieManager.getInstance()
                                        val raw = scopedCookieHeader(
                                            manager = manager,
                                            url = currentUrl,
                                            host = currentHost,
                                            pathPrefix = saveSessionPathPrefix
                                        )
                                        if (raw.isNotBlank()) {
                                            AppContainer.appScope.launch {
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
                                if (mobileViewportFix) {
                                    view?.evaluateJavascript(MOBILE_VIEWPORT_FIX_SCRIPT) { diag ->
                                        if (BuildConfig.DEBUG) {
                                            Log.d("CxWebDiag", "fix=$diag")
                                        }
                                    }
                                }
                                if (readerModeAvailable) {
                                    extractReaderContent(view)
                                    view?.postDelayed({ extractReaderContent(view) }, 450)
                                    view?.postDelayed({ extractReaderContent(view) }, 1400)
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
                    },
                    update = { webView ->
                        webView.visibility = if (readerModeAvailable && readerMode) {
                            View.INVISIBLE
                        } else {
                            View.VISIBLE
                        }
                    }
                )
                if (readerModeAvailable && readerMode) {
                    WebReaderOverlay(
                        snapshot = readerSnapshot,
                        isLoading = isLoading,
                        onOpenOriginal = { readerMode = false },
                        onRefresh = {
                            extractReaderContent(webViewRef.value)
                        },
                        onActionClick = { action ->
                            webViewRef.value?.evaluateJavascript(
                                "(function(){var el=document.querySelector('[data-cp-act=\"${action.index}\"]');if(el){el.click();}})()",
                                null
                            )
                            val interactive = action.label.contains("做作业") ||
                                action.label.contains("签到") ||
                                action.label.contains("开始")
                            if (interactive) {
                                readerMode = false
                            } else {
                                webViewRef.value?.postDelayed({ extractReaderContent(webViewRef.value) }, 800)
                                webViewRef.value?.postDelayed({ extractReaderContent(webViewRef.value) }, 1800)
                            }
                        }
                    )
                }
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
/**
 * CookieManager.getCookie 会把同一 host 下不同 path 的同名 Cookie 混在一起
 * （教务同时有 /sso 与 /jwglxt 的 JSESSIONID）。这里用根路径和 /sso 路径的
 * Cookie 做差集，只留下目标 path 专属的那一个，避免把 /sso 的会话写进 /jwglxt。
 */
private fun scopedCookieHeader(
    manager: CookieManager,
    url: String,
    host: String,
    pathPrefix: String?
): String {
    val all = manager.getCookie(url).orEmpty()
    if (all.isBlank()) return all
    val jsessionCount = Regex("""(?:^|;\s*)JSESSIONID=""", RegexOption.IGNORE_CASE)
        .findAll(all).count()
    if (jsessionCount <= 1) return all
    val scheme = Uri.parse(url).scheme ?: "http"
    val excludedPairs = buildSet {
        addAll(parseCookiePairs(manager.getCookie("$scheme://$host/").orEmpty()))
        if (pathPrefix == "/jwglxt") {
            addAll(parseCookiePairs(manager.getCookie("$scheme://$host/sso/").orEmpty()))
        }
    }
    return parseCookiePairs(all)
        .filter { pair -> !pair.first.equals("JSESSIONID", true) || pair !in excludedPairs }
        .joinToString("; ") { (name, value) -> "$name=$value" }
}

private fun parseCookiePairs(raw: String): List<Pair<String, String>> =
    raw.split(";").mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) return@mapNotNull null
        part.substring(0, index).trim() to part.substring(index + 1).trim()
    }

private const val REMEMBER_ME_SCRIPT = """
(function(){
  function tick(){
    try {
      var box = document.getElementById('rememberMe');
      if (!box) return;
      if (window.jQuery) {
        var jq = window.jQuery('#rememberMe');
        if (jq.length && typeof jq.iCheck === 'function') { jq.iCheck('check'); }
      }
      if (!box.checked) {
        box.checked = true;
        box.dispatchEvent(new Event('change', {bubbles: true}));
        box.dispatchEvent(new Event('ifChecked', {bubbles: true}));
      }
    } catch (e) {}
  }
  tick();
  setTimeout(tick, 500);
  setTimeout(tick, 1500);
})()
"""

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

private data class WebReaderAction(val index: Int, val label: String)

private data class WebReaderSnapshot(
    val title: String,
    val blocks: List<String>,
    val actions: List<WebReaderAction>
)

private fun parseReaderSnapshot(rawJsResult: String?): WebReaderSnapshot? {
    if (rawJsResult.isNullOrBlank() || rawJsResult == "null") return null
    return runCatching {
        val unquoted = if (rawJsResult.startsWith("\"")) {
            org.json.JSONTokener(rawJsResult).nextValue().toString()
        } else {
            rawJsResult
        }
        val obj = JSONObject(unquoted)
        val title = obj.optString("title", "").trim()
        val blocksArray = obj.optJSONArray("blocks")
        val blocks = buildList {
            if (blocksArray != null) {
                for (i in 0 until blocksArray.length()) {
                    val line = blocksArray.optString(i, "").trim()
                    if (line.isNotBlank()) add(line)
                }
            }
        }
        val actionsArray = obj.optJSONArray("actions")
        val actions = buildList {
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val a = actionsArray.optJSONObject(i) ?: continue
                    val label = a.optString("label", "").trim()
                    val idx = a.optInt("index", -1)
                    if (label.isNotBlank() && idx >= 0) add(WebReaderAction(idx, label))
                }
            }
        }
        WebReaderSnapshot(title = title, blocks = blocks, actions = actions)
    }.getOrNull()
}

@Composable
private fun WebReaderOverlay(
    snapshot: WebReaderSnapshot?,
    isLoading: Boolean,
    onOpenOriginal: () -> Unit,
    onRefresh: () -> Unit,
    onActionClick: (WebReaderAction) -> Unit
) {
    val readerBg = if (isGlassDark()) Color(0xFF0E1716) else Color(0xFFF3F8F7)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(readerBg),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (snapshot == null || (snapshot.blocks.isEmpty() && snapshot.title.isBlank())) {
            item {
                AppCard {
                    Column {
                        Text(
                            if (isLoading) "正在整理页面内容…" else "暂未提取到正文内容",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "部分互动页面（如手势签到、上传附件）请切回「原网页」操作。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onOpenOriginal) { Text("查看原网页") }
                            TextButton(onClick = onRefresh) { Text("重新提取") }
                        }
                    }
                }
            }
        } else {
            if (snapshot.title.isNotBlank()) {
                item {
                    AppCard {
                        Column {
                            Text(
                                snapshot.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "清爽阅读前端 · 仅提取可见文本；作答或提交请点右上角「原网页」",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(snapshot.blocks) { block ->
                AppCard(contentPadding = 14.dp) {
                    Text(
                        text = block,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                }
            }
            if (snapshot.actions.isNotEmpty()) {
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "页面操作",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            snapshot.actions.take(4).forEach { action ->
                                Button(
                                    onClick = { onActionClick(action) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(action.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 修正学习通等移动端页面在 WebView 中因 flexible.js / width=750 导致的异常巨大缩放，
 * 并把 window.open / target="_blank" 统一收敛到当前页打开。
 */
private const val MOBILE_VIEWPORT_FIX_SCRIPT = """
(function(){
  function fix(){
    try{
      var meta = document.querySelector('meta[name="viewport"]');
      var origVp = meta ? (meta.getAttribute('content') || '') : 'none';
      var w = window.innerWidth || document.documentElement.clientWidth || 375;
      var rootFs = parseFloat(window.getComputedStyle(document.documentElement).fontSize || '16');
      var extraRoot = '';
      // 仅当页面的 rem 基准被错误放大（例如按物理像素计算成 100px+）时，按 750 设计稿比例折算回合理 rem（约 innerWidth/7.5）。
      // 绝不把 rem 强行压成 16px，否则会压扁学习通所有 rem 按钮与顶栏。
      var maxNormalRem = Math.max(58, w / 5.5);
      if (rootFs > maxNormalRem) {
        var idealRem = Math.max(36, Math.min(54, Math.round(w / 7.5)));
        extraRoot = 'html{font-size:' + idealRem + 'px!important;}';
      }
      var s = document.getElementById('cp-mobile-fix');
      if (!s && document.head) {
        s = document.createElement('style');
        s.id = 'cp-mobile-fix';
        document.head.appendChild(s);
      }
      if (s) {
        s.textContent = extraRoot +
          'html,body{max-width:100vw!important;overflow-x:hidden!important;-webkit-text-size-adjust:100%!important;}' +
          'img,video,table,iframe,pre{max-width:100%!important;height:auto!important;}';
      }
      // 把 target=_blank 改为本页打开，支持页内跳转。
      var links = document.querySelectorAll('a[target="_blank"]');
      for (var i = 0; i < links.length; i++) {
        links[i].setAttribute('target', '_self');
      }
      if (!window.__cpOpenHooked) {
        window.__cpOpenHooked = true;
        window.open = function(u){ if (u) { window.location.href = u; } return window; };
      }
      return JSON.stringify({vp:origVp, w:w, sw:document.documentElement.scrollWidth, fs:rootFs});
    }catch(e){ return 'err:' + e.message; }
  }
  var res = fix();
  setTimeout(fix, 500);
  setTimeout(fix, 1300);
  return res;
})()
"""

private const val READER_EXTRACT_SCRIPT = """
(function(){
  try{
    var title = (document.title || '').trim();
    var h = document.querySelector('h1,h2,.mark_title,.tit,.title');
    if (h && h.innerText && h.innerText.trim().length > 1) { title = h.innerText.trim(); }
    var blocks = [];
    var seen = {};
    var selectors = '.questionLi,.TiMu,.mark_item,.singleQuesId,.stem,.Py_answer,.analysis,p,h3,li';
    var nodes = document.querySelectorAll(selectors);
    for (var i = 0; i < nodes.length && blocks.length < 60; i++) {
      var el = nodes[i];
      if (el.offsetParent === null) continue;
      var txt = (el.innerText || '').replace(/\n{3,}/g, '\n\n').trim();
      if (txt.length < 2 || txt === title) continue;
      if (seen[txt]) continue;
      seen[txt] = true;
      blocks.push(txt.slice(0, 1200));
    }
    if (blocks.length === 0 && document.body && document.body.innerText) {
      var raw = document.body.innerText.split('\n');
      for (var j = 0; j < raw.length && blocks.length < 40; j++) {
        var line = raw[j].trim();
        if (line.length >= 2 && !seen[line]) { seen[line] = true; blocks.push(line); }
      }
    }
    var actions = [];
    var actionSeen = {};
    var btns = document.querySelectorAll('a,button,input[type=button],div[onclick],span[onclick],.btn,.blueBtn,.jb_btn,.bottomBtn,.detailBtn');
    for (var k = 0; k < btns.length && actions.length < 4; k++) {
      var b = btns[k];
      if (b.offsetParent === null) continue;
      var label = (b.innerText || b.value || '').trim();
      if (label.length >= 2 && label.length <= 14 && !actionSeen[label] &&
          (label.indexOf('详情') >= 0 || label.indexOf('查看') >= 0 || label.indexOf('做作业') >= 0 ||
           label.indexOf('签到') >= 0 || label.indexOf('进入') >= 0 || label.indexOf('开始') >= 0)) {
        var actIdx = actions.length;
        b.setAttribute('data-cp-act', String(actIdx));
        actionSeen[label] = true;
        actions.push({index: actIdx, label: label});
      }
    }
    return JSON.stringify({title: title, blocks: blocks, actions: actions});
  }catch(e){ return null; }
})()
"""

