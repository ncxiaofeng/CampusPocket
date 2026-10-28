package com.ecut.campuspocket.ui

import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.GlassBackdrop
import com.ecut.campuspocket.ui.components.GlassOverlay
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.LiquidGlassBottomBar
import com.ecut.campuspocket.ui.components.LiquidGlassTabItem
import com.ecut.campuspocket.ui.components.liquidGlassBackdropSource
import com.ecut.campuspocket.ui.components.rememberLiquidGlassBackdropState
import com.ecut.campuspocket.ui.components.glassBodyBrush
import com.ecut.campuspocket.ui.components.glassBorderBrush
import com.ecut.campuspocket.ui.components.isGlassDark
import com.ecut.campuspocket.ui.theme.ActionGreen
import com.ecut.campuspocket.ui.theme.ActionGreenBright
import com.ecut.campuspocket.ui.theme.ActionGreenDeep
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ecut.campuspocket.AppContainer
import com.ecut.campuspocket.navigation.Routes
import com.ecut.campuspocket.network.CampusEndpoints
import com.ecut.campuspocket.network.CampusNetworkState
import com.ecut.campuspocket.network.CampusNetworkStatus
import com.ecut.campuspocket.ui.screens.AboutScreen
import com.ecut.campuspocket.ui.screens.CampusCardScreen
import com.ecut.campuspocket.ui.screens.CampusWebPageScreen
import com.ecut.campuspocket.ui.screens.ClassroomsScreen
import com.ecut.campuspocket.ui.screens.ElectricityScreen
import com.ecut.campuspocket.ui.screens.ExamsScreen
import com.ecut.campuspocket.ui.screens.GradesScreen
import com.ecut.campuspocket.ui.screens.HuiLifeScreen
import com.ecut.campuspocket.ui.screens.LibrarySeatsScreen
import com.ecut.campuspocket.ui.screens.LoginWebViewScreen
import com.ecut.campuspocket.ui.screens.MoreScreen
import com.ecut.campuspocket.ui.screens.NoticesScreen
import com.ecut.campuspocket.ui.screens.PlaceholderScreen
import com.ecut.campuspocket.ui.screens.ScheduleScreen
import com.ecut.campuspocket.ui.screens.SyncScreen
import com.ecut.campuspocket.ui.screens.TodayScreen
import com.ecut.campuspocket.ui.screens.UjingScreen
import kotlinx.coroutines.launch

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

private val tabs = listOf(
    TabItem(Routes.TODAY, "首页", Icons.Outlined.Home, Icons.Filled.Home),
    TabItem(Routes.SCHEDULE, "课表", Icons.Outlined.DateRange, Icons.Filled.DateRange),
    TabItem(Routes.CAMPUS_CARD, "校园卡", Icons.Outlined.CreditCard, Icons.Filled.CreditCard),
    TabItem(Routes.MORE, "更多", Icons.Outlined.Menu, Icons.Filled.Menu)
)

private val topLevelRoutes = tabs.map { it.route }.toSet()

@Composable
fun CampusPocketRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 统一身份认证主凭据（CASTGC）：真正决定“是否已登录”的那一个。
    val casMasterCredential by AppContainer.authRepository
        .observeCredential(CampusEndpoints.SYSTEM_CAS_MASTER)
        .collectAsStateWithLifecycle(initialValue = null)
    val vpnCredential by AppContainer.authRepository
        .observeCredential(CampusEndpoints.SYSTEM_CAS)
        .collectAsStateWithLifecycle(initialValue = null)
    val proxyCredential by AppContainer.authRepository
        .observeCredential(CampusEndpoints.SYSTEM_JW_PROXY)
        .collectAsStateWithLifecycle(initialValue = null)
    val sessionExpired by SessionState.expired.collectAsStateWithLifecycle()
    val networkState by CampusNetworkStatus.state.collectAsStateWithLifecycle()
    var autoJumpHandled by rememberSaveable { mutableStateOf(false) }
    var showQuickActions by rememberSaveable { mutableStateOf(false) }
    val backdropState = rememberLiquidGlassBackdropState()
    val layoutDirection = LocalLayoutDirection.current
    val showBottomBar = currentRoute != null && currentRoute in topLevelRoutes

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { CampusNetworkStatus.refresh() }
    }

    LaunchedEffect(networkState, vpnCredential) {
        when (networkState) {
            is CampusNetworkState.OffCampus -> {
                // 已经完成零信任认证时不再反复拉登录页；门户入口在顶部横幅和“更多”里。
                if (vpnCredential != null) {
                    autoJumpHandled = true
                } else if (!autoJumpHandled) {
                    autoJumpHandled = true
                    navController.navigate(Routes.VPN_LOGIN) {
                        launchSingleTop = true
                    }
                }
            }
            is CampusNetworkState.OnCampus -> autoJumpHandled = true
            else -> Unit
        }
    }

    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 原生教务接口与官方兜底页共用同一套地址选择：校内直连，校外走 WebVPN 代理。
    val proxyReady = proxyCredential?.domain == CampusEndpoints.JW_PROXY_HOST
    fun jwBaseUrl(): String = when {
        networkState is CampusNetworkState.OnCampus -> CampusEndpoints.JW_BASE_URL
        proxyReady -> CampusEndpoints.JW_PROXY_BASE_URL
        else -> CampusEndpoints.JW_BASE_URL
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                LiquidGlassBottomBar(
                    tabs = tabs.map {
                        LiquidGlassTabItem(
                            label = it.label,
                            icon = it.icon,
                            selectedIcon = it.selectedIcon
                        )
                    },
                    selectedIndex = tabs
                        .indexOfFirst { it.route == currentRoute }
                        .coerceAtLeast(0),
                    onTabSelected = { index -> openTab(tabs[index].route) },
                    onCenterClick = { showQuickActions = true },
                    backdrop = backdropState
                )
            }
        }
    ) { innerPadding ->
        // 背景和页面内容放在同一个 Box 内，并记录到 GraphicsLayer 供底栏真实采样。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBackdropSource(backdropState)
        ) {
            GlassBackdrop()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = innerPadding.calculateStartPadding(layoutDirection),
                        top = innerPadding.calculateTopPadding(),
                        end = innerPadding.calculateEndPadding(layoutDirection),
                        // 顶层页面让内容延伸到悬浮底栏后面，由页面自己预留底部空间；
                        // 二级页面（底栏隐藏）仍按 Scaffold 的安全区正常收缩。
                        bottom = if (showBottomBar) 0.dp
                        else innerPadding.calculateBottomPadding()
                    )
            ) {
            if (networkState is CampusNetworkState.OffCampus &&
                currentRoute != Routes.VPN_LOGIN &&
                currentRoute != Routes.VPN_PORTAL
            ) {
                CampusNetworkBanner(
                    vpnAuthenticated = vpnCredential != null,
                    proxyReady = proxyReady,
                    onVpnLogin = { navController.navigate(Routes.VPN_LOGIN) },
                    onOpenPortal = { navController.navigate(Routes.VPN_PORTAL) },
                    onRetry = { scope.launch { CampusNetworkStatus.refresh() } }
                )
            }
            if (sessionExpired) {
                SessionExpiredBanner(
                    onRelogin = {
                        SessionState.clear()
                        navController.navigate(Routes.LOGIN)
                    },
                    onDismiss = { SessionState.clear() }
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
                modifier = Modifier.weight(1f)
            ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenElectricity = { navController.navigate(Routes.ELECTRICITY) },
                    onOpenCampusCard = { openTab(Routes.CAMPUS_CARD) },
                    onOpenSchedule = { openTab(Routes.SCHEDULE) },
                    vpnAuthenticated = vpnCredential != null
                )
            }
            composable(Routes.SCHEDULE) {
                ScheduleScreen(onOpenLogin = { navController.navigate(Routes.LOGIN) })
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onNavigate = { route ->
                        if (route == Routes.CAMPUS_CARD) openTab(route)
                        else navController.navigate(route)
                    },
                    onLogout = {
                        scope.launch {
                            runCatching { AppContainer.authRepository.clearAll() }
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.removeAllCookies(null)
                            cookieManager.flush()
                            SessionState.clear()
                            Toast.makeText(context, "本地登录数据已清除", Toast.LENGTH_SHORT).show()
                        }
                    },
                    loggedIn = casMasterCredential != null,
                    vpnLoggedIn = vpnCredential != null
                )
            }

            composable(Routes.LOGIN) {
                // 校内：直接打开教务 SSO 页。WebView 会执行完整重定向，
                // 一次同时拿到 CASTGC 和教务 JSESSIONID，避免再走一遍
                // 对集群粘性 Cookie 很敏感的 OkHttp 静默换票。
                if (networkState is CampusNetworkState.OnCampus) {
                    CampusWebPageScreen(
                        title = "统一身份认证 · 教务",
                        url = CampusEndpoints.JW_SSO_URL,
                        onBack = {
                            SessionState.clear()
                            navController.popBackStack()
                        },
                        helperText = "登录成功后会落在课表页；返回 App 点“同步”即可。",
                        extraCookieHosts = listOf(CampusEndpoints.CAS_HOST),
                        saveSessionHost = java.net.URI(CampusEndpoints.JW_BASE_URL).host,
                        saveSessionSystemId = CampusEndpoints.SYSTEM_JW,
                        detectSessionLost = false,
                        sourceName = "教务系统"
                    )
                } else {
                    // 校外：教务内网地址不可达，先完成统一认证（落到 ehall）。
                    // 课表同步仍需在零信任门户里建立 WebVPN 代理会话。
                    LoginWebViewScreen(
                        target = CampusEndpoints.CAS_LOGIN,
                        onBack = {
                            SessionState.clear()
                            navController.popBackStack()
                        }
                    )
                }
            }
            composable(Routes.VPN_LOGIN) {
                LoginWebViewScreen(
                    target = CampusEndpoints.ATRUST_LOGIN,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.VPN_PORTAL) {
                CampusWebPageScreen(
                    title = "零信任门户",
                    url = CampusEndpoints.ATRUST_PORTAL_URL,
                    onBack = { navController.popBackStack() },
                    helperText = "学校零信任门户；可切 PC 版。进入“新教务管理系统”任一功能一次，之后 App 就能校外同步课表/成绩。",
                    extraCookieHosts = listOf("atrust1.ecut.edu.cn"),
                    userAgent = CampusEndpoints.MOBILE_BROWSER_USER_AGENT,
                    detectSessionLost = false,
                    sourceName = "零信任门户",
                    openInBrowser = true,
                    saveProxySession = true,
                    userAgentSwitch = true
                )
            }
            composable(Routes.ELECTRICITY_LOGIN) {
                // 电费不再单独登录：统一身份认证一次即可，电费会话由 CAS 静默换票。
                LoginWebViewScreen(
                    target = CampusEndpoints.CAS_LOGIN,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SYNC) {
                SyncScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenPortal = { navController.navigate(Routes.VPN_PORTAL) }
                )
            }
            composable(Routes.GRADES) {
                GradesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) }
                )
            }
            composable(Routes.EXAMS) {
                ExamsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) }
                )
            }
            composable(Routes.CLASSROOMS) {
                ClassroomsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenOfficial = { navController.navigate(Routes.CLASSROOMS_OFFICIAL) }
                )
            }
            composable(Routes.CLASSROOMS_OFFICIAL) {
                CampusWebPageScreen(
                    title = "空闲教室 · 官方页",
                    url = CampusEndpoints.JW_CLASSROOM_URL,
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    helperText = "教务官方空闲教室页面（只读），可用更多筛选条件。",
                    extraCookieHosts = listOf("authserver.ecut.edu.cn")
                )
            }
            composable(Routes.NOTICES) {
                NoticesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenOfficial = { navController.navigate(Routes.NOTICES_OFFICIAL) },
                    onOpenNoticeOfficial = { notice ->
                        navController.navigate(
                            Routes.NOTICE_OFFICIAL_DETAIL +
                                "?xwbh=${Uri.encode(notice.id)}"
                        )
                    }
                )
            }
            composable(Routes.NOTICES_OFFICIAL) {
                CampusWebPageScreen(
                    title = "通知公告 · 官方页",
                    url = jwBaseUrl() + CampusEndpoints.JW_NOTICE_PATH,
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    helperText = "教务官方通知公告页面（只读），可查看图片、表格等完整排版。",
                    extraCookieHosts = listOf("authserver.ecut.edu.cn"),
                    desktopUserAgent = false,
                    sourceName = "通知公告"
                )
            }
            composable(
                route = Routes.NOTICE_OFFICIAL_DETAIL + "?xwbh={xwbh}",
                arguments = listOf(
                    navArgument("xwbh") { defaultValue = "" }
                )
            ) { entry ->
                val noticeId = entry.arguments?.getString("xwbh").orEmpty()
                val url = if (noticeId.isBlank()) {
                    jwBaseUrl() + CampusEndpoints.JW_NOTICE_PATH
                } else {
                    jwBaseUrl() +
                        "/jwglxt/xtgl/xwglMobile_cxXwDetail.html?xwbh=" +
                        Uri.encode(noticeId) +
                        "&hasHeader="
                }
                CampusWebPageScreen(
                    title = "公告详情 · 官方页",
                    url = url,
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    helperText = "教务官方公告详情（只读），图片、表格等完整排版以此页为准。",
                    extraCookieHosts = listOf("authserver.ecut.edu.cn"),
                    desktopUserAgent = false,
                    sourceName = "通知公告"
                )
            }
            composable(Routes.ELECTRICITY) {
                ElectricityScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLogin = { navController.navigate(Routes.ELECTRICITY_LOGIN) }
                )
            }
            composable(Routes.EHALL) {
                CampusWebPageScreen(
                    title = "网上办事大厅",
                    url = CampusEndpoints.EHALL_URL,
                    onBack = { navController.popBackStack() },
                    helperText = "学校网上办事大厅（PC 版）；校外建议先在零信任门户认证。",
                    extraCookieHosts = listOf("authserver.ecut.edu.cn"),
                    desktopUserAgent = true,
                    detectSessionLost = false,
                    sourceName = "网上办事大厅",
                    openInBrowser = true,
                    userAgentSwitch = true
                )
            }
            composable(Routes.LIBRARY) {
                LibrarySeatsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBooking = { navController.navigate(Routes.LIBRARY_BOOKING) }
                )
            }
            composable(Routes.LIBRARY_BOOKING) {
                CampusWebPageScreen(
                    title = "图书馆预约",
                    url = CampusEndpoints.LIBRARY_H5_URL,
                    onBack = { navController.popBackStack() },
                    helperText = "图书馆空间预约官方页面；登录、预约与签到均在此页面完成。",
                    desktopUserAgent = false,
                    detectSessionLost = false,
                    sourceName = "图书馆"
                )
            }
            composable(Routes.CAMPUS_CARD) {
                CampusCardScreen(
                    onBack = { openTab(Routes.TODAY) },
                    onOpenOfficial = { navController.navigate(Routes.CARD_WEB) },
                    showBack = false
                )
            }
            composable(Routes.CARD_WEB) {
                CampusWebPageScreen(
                    title = "校园卡 · 官方页",
                    url = CampusEndpoints.EHALL_CARD_URL,
                    onBack = { navController.popBackStack() },
                    helperText = "学校一卡通官方页面；登录后返回即可读取余额与流水。",
                    extraCookieHosts = listOf("authserver.ecut.edu.cn"),
                    desktopUserAgent = true,
                    detectSessionLost = false,
                    sourceName = "一卡通",
                    openInBrowser = true,
                    saveSessionHost = "ehall.ecut.edu.cn",
                    saveSessionSystemId = CampusEndpoints.SYSTEM_CARD,
                    userAgentSwitch = true
                )
            }
            composable(Routes.LAUNDRY) {
                UjingScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.WATER) {
                HuiLifeScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.RUN) {
                PlaceholderScreen(
                    title = "阳光跑记录",
                    icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                    description = "只读展示闪动校园跑步记录，不提供任何自动打卡能力。",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PRIVACY) {
                PlaceholderScreen(
                    title = "隐私说明",
                    icon = Icons.Outlined.PrivacyTip,
                    description = "所有凭据仅保存在本机加密数据库中，App 不上传任何个人数据，也不保存账号密码；天气接口只发送设备定位或校区坐标。",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) }
                )
            }
            }
        }
        }
        }
        if (showQuickActions) {
            QuickActionsSheet(
                onDismiss = { showQuickActions = false },
                onAction = { route, asTab ->
                    showQuickActions = false
                    if (asTab) openTab(route) else navController.navigate(route)
                }
            )
        }
    }
}

private data class QuickActionItem(
    val label: String,
    val route: String,
    val icon: ImageVector,
    val color: Color,
    val asTab: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsSheet(
    onDismiss: () -> Unit,
    onAction: (route: String, asTab: Boolean) -> Unit
) {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val items = listOf(
        QuickActionItem("数据同步", Routes.SYNC, Icons.Outlined.Sync, ActionGreen),
        QuickActionItem("电费查询", Routes.ELECTRICITY, Icons.Outlined.Bolt, WarningAmber),
        QuickActionItem(
            "校园卡",
            Routes.CAMPUS_CARD,
            Icons.Outlined.CreditCard,
            TealPrimary,
            asTab = true
        ),
        QuickActionItem("空闲教室", Routes.CLASSROOMS, Icons.Outlined.School, Color(0xFF3B7BBF)),
        QuickActionItem("图书馆", Routes.LIBRARY, Icons.Outlined.LocalLibrary, Color(0xFF7C5CC4)),
        QuickActionItem(
            "U净洗衣",
            Routes.LAUNDRY,
            Icons.Outlined.LocalLaundryService,
            Color(0xFFD94F7E)
        )
    )
    val dark = isGlassDark()
    val sheetBrush = if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF1B2B29), Color(0xFF0D1817)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFEFC), Color(0xFFEFFBF8)))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(sheetBrush)
                .border(1.dp, glassBorderBrush(), shape)
                .navigationBarsPadding()
        ) {
            GlassOverlay(Modifier.matchParentSize())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 30.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(38.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "快捷操作",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "常用的校园服务，一步直达",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                items.chunked(3).forEachIndexed { rowIndex, rowItems ->
                    if (rowIndex > 0) Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { item ->
                            QuickActionTile(
                                item = item,
                                modifier = Modifier.weight(1f),
                                onClick = { onAction(item.route, item.asTab) }
                            )
                        }
                        repeat(3 - rowItems.size) {
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    item: QuickActionItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = 12.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(icon = item.icon, color = item.color, size = 36.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SessionExpiredBanner(
    onRelogin: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "登录已过期",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "重新登录一次即可恢复课表/电费/校园卡",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onRelogin,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    text = "重新登录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun CampusNetworkBanner(
    vpnAuthenticated: Boolean,
    proxyReady: Boolean,
    onVpnLogin: () -> Unit,
    onOpenPortal: () -> Unit,
    onRetry: () -> Unit
) {
    val container = if (vpnAuthenticated) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (vpnAuthenticated) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (vpnAuthenticated) "零信任已认证" else "未连接校园网",
                style = MaterialTheme.typography.labelLarge,
                color = content
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    vpnAuthenticated && proxyReady -> "教务走 WebVPN 代理"
                    vpnAuthenticated -> "打开门户进入教务即可同步"
                    else -> "内网服务不可用"
                },
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.75f),
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text(
                    text = "重试",
                    style = MaterialTheme.typography.labelMedium,
                    color = content
                )
            }
            TextButton(
                onClick = if (vpnAuthenticated) onOpenPortal else onVpnLogin,
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text(
                    text = if (vpnAuthenticated) "零信任门户" else "零信任登录",
                    style = MaterialTheme.typography.labelMedium,
                    color = content
                )
            }
        }
    }
}
