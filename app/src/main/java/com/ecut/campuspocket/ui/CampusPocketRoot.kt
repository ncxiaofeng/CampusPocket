package com.ecut.campuspocket.ui

import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    val icon: ImageVector
)

private val tabs = listOf(
    TabItem(Routes.TODAY, "首页", Icons.Filled.Home),
    TabItem(Routes.SCHEDULE, "课表", Icons.Filled.DateRange),
    TabItem(Routes.MORE, "更多", Icons.Filled.Menu)
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

    // 原生教务接口与官方兜底页共用同一套地址选择：校内直连，校外走 WebVPN 代理。
    fun jwBaseUrl(): String = when {
        networkState is CampusNetworkState.OnCampus -> CampusEndpoints.JW_BASE_URL
        proxyCredential != null -> CampusEndpoints.JW_PROXY_BASE_URL
        else -> CampusEndpoints.JW_BASE_URL
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != null && currentRoute in topLevelRoutes) {
                CampusBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (networkState is CampusNetworkState.OffCampus &&
                currentRoute != Routes.VPN_LOGIN &&
                currentRoute != Routes.VPN_PORTAL
            ) {
                CampusNetworkBanner(
                    vpnAuthenticated = vpnCredential != null,
                    proxyReady = proxyCredential != null,
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
                    onOpenCampusCard = { navController.navigate(Routes.CAMPUS_CARD) },
                    onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                    vpnAuthenticated = vpnCredential != null
                )
            }
            composable(Routes.SCHEDULE) {
                ScheduleScreen(onOpenLogin = { navController.navigate(Routes.LOGIN) })
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onNavigate = { route -> navController.navigate(route) },
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
                // 所有模块的“去登录”都走同一个统一身份认证入口。
                LoginWebViewScreen(
                    target = CampusEndpoints.CAS_LOGIN,
                    onBack = {
                        SessionState.clear()
                        navController.popBackStack()
                    }
                )
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
                    onOpenLogin = { navController.navigate(Routes.LOGIN) }
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
                    onBack = { navController.popBackStack() },
                    onOpenOfficial = { navController.navigate(Routes.CARD_WEB) }
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
                    badge = "跑",
                    description = "只读展示闪动校园跑步记录，不提供任何自动打卡能力。",
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PRIVACY) {
                PlaceholderScreen(
                    title = "隐私说明",
                    badge = "隐",
                    description = "所有凭据仅保存在本机加密数据库中，App 不上传任何个人数据，也不保存账号密码。",
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

@Composable
private fun CampusBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    androidx.compose.foundation.layout.Column {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            thickness = 1.dp
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
                    onClick = { onSelect(tab.route) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                    colors = colors
                )
            }
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
