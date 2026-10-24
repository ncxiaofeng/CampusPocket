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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.BuildConfig
import com.ecut.campuspocket.navigation.Routes
import com.ecut.campuspocket.network.AtrustClient
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.components.StatusDot
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.CourseLavender
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.ErrorRed
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber

private data class MoreItem(
    val badge: String,
    val badgeColor: Color,
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun MoreScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    loggedIn: Boolean,
    vpnLoggedIn: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val atrustPackage = remember { AtrustClient.installedPackage(context) }
    val groups = listOf(
        "账号与同步" to listOf(
            MoreItem(
                badge = "教",
                badgeColor = TealPrimary,
                title = "教务系统",
                subtitle = if (loggedIn) "统一认证会话已加密保存" else "登录后同步课表、成绩、考试与电费",
                trailing = if (loggedIn) "已登录" else "去登录"
            ) { onNavigate(Routes.LOGIN) },
            MoreItem(
                badge = "零",
                badgeColor = Color(0xFF607D8B),
                title = "零信任 VPN",
                subtitle = if (vpnLoggedIn) {
                    "统一认证会话已保存"
                } else {
                    "校外访问时先登录零信任门户"
                },
                trailing = if (vpnLoggedIn) "打开门户" else "去登录"
            ) {
                onNavigate(if (vpnLoggedIn) Routes.VPN_PORTAL else Routes.VPN_LOGIN)
            },
            MoreItem(
                badge = "隧",
                badgeColor = CourseTeal,
                title = "aTrust 隧道",
                subtitle = if (atrustPackage != null) {
                    "官方客户端已安装 · 需要完整内网时打开"
                } else {
                    "需要完整内网（非 WebVPN）时使用"
                },
                trailing = if (atrustPackage != null) "打开" else "去安装"
            ) {
                if (!AtrustClient.launch(context)) {
                    AtrustClient.openStoreOrGuide(context)
                }
            },
            MoreItem("同", CourseBlue, "数据同步", "课表、成绩、考试、电费", "立即同步") { onNavigate(Routes.SYNC) }
        ),
        "学习空间" to listOf(
            MoreItem("绩", CourseLavender, "成绩查询", "学期成绩与绩点", null) { onNavigate(Routes.GRADES) },
            MoreItem("考", CourseRose, "考试安排", "考场与考试时间", null) { onNavigate(Routes.EXAMS) },
            MoreItem("教", CourseTeal, "空闲教室", "按教学楼查询空教室", null) { onNavigate(Routes.CLASSROOMS) },
            MoreItem("告", CourseRose, "通知公告", "教务通知与教学安排", null) { onNavigate(Routes.NOTICES) },
            MoreItem("图", CourseBlue, "图书馆空位", "座位余量与预约入口", null) { onNavigate(Routes.LIBRARY) },
            MoreItem("厅", CourseTeal, "网上办事大厅", "教务 / 校园卡 / 通知等官方入口", null) { onNavigate(Routes.EHALL) }
        ),
        "生活服务" to listOf(
            MoreItem("电", WarningAmber, "电费查询", "照明 / 空调分项用电", null) { onNavigate(Routes.ELECTRICITY) },
            MoreItem("卡", TealPrimary, "校园卡", "余额与消费流水", null) { onNavigate(Routes.CAMPUS_CARD) },
            MoreItem("洗", CourseBlue, "U净洗衣", "查询洗衣机空闲状态", null) { onNavigate(Routes.LAUNDRY) },
            MoreItem("水", CourseTeal, "慧生活798", "饮水机 / 吹风机状态", null) { onNavigate(Routes.WATER) },
            MoreItem("跑", CourseRose, "阳光跑记录", "闪动校园跑步数据（只读）", null) { onNavigate(Routes.RUN) }
        ),
        "其他" to listOf(
            MoreItem("隐", Color(0xFF607D8B), "隐私说明", "凭据如何保存在本机", null) { onNavigate(Routes.PRIVACY) },
            MoreItem(
                "关",
                Color(0xFF607D8B),
                "关于 冻小梨",
                "v${BuildConfig.VERSION_NAME} · 仅限校内网使用",
                null
            ) { onNavigate(Routes.ABOUT) }
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text(
                    text = "更多",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "校园服务与账号设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "同",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (loggedIn) "已登录" else "尚未登录",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (loggedIn) {
                                "会话 Cookie 已加密保存在本机"
                            } else {
                                "登录后自动同步课表、成绩与考试"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onNavigate(Routes.LOGIN) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (loggedIn) "重新授权" else "去登录",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        groups.forEach { (title, items) ->
            item(key = title) {
                MoreGroup(title = title, items = items)
            }
        }

        item {
            AppCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "🔒",
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "密码不落在本机",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "登录在官方网页中完成，App 仅保存加密后的会话 Cookie。退出登录会同时清空本地数据库。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            AppCard(onClick = onLogout, contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "退出登录并清除本地数据",
                        style = MaterialTheme.typography.titleSmall,
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                    Text("›", color = ErrorRed.copy(alpha = 0.7f), fontSize = 20.sp)
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "冻小梨 · 纯本地校园聚合",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), size = 5.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "仅在校园网环境可用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreGroup(title: String, items: List<MoreItem>) {
    Column {
        SectionHeader(title = title)
        Spacer(Modifier.height(10.dp))
        AppCard(contentPadding = 0.dp) {
            Column {
                items.forEachIndexed { index, item ->
                    MoreRow(item)
                    if (index != items.lastIndex) {
                        ThinDivider(Modifier.padding(start = 64.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(text = item.badge, color = item.badgeColor, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (item.danger) ErrorRed else MaterialTheme.colorScheme.onSurface
            )
            if (item.subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (item.trailing != null) {
            Text(
                text = item.trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "›",
            fontSize = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
    }
}
