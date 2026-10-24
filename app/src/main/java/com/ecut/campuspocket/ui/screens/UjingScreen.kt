package com.ecut.campuspocket.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.remote.ujing.UjingCampus
import com.ecut.campuspocket.data.remote.ujing.UjingDeviceTag
import com.ecut.campuspocket.data.remote.ujing.UjingOrder
import com.ecut.campuspocket.data.remote.ujing.UjingProgram
import com.ecut.campuspocket.data.remote.ujing.UjingStore
import com.ecut.campuspocket.data.remote.ujing.UjingStoreRef
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.StatusDot
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.theme.ChartPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UjingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UjingViewModel = viewModel(factory = UjingViewModel.Factory)
) {
    val loggedIn = viewModel.hasSession && !viewModel.needsLogin

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        UjingTopBar(
            onBack = onBack,
            loggedIn = loggedIn,
            isBusy = viewModel.isBusy,
            campusName = viewModel.selectedCampus.name,
            onRefresh = viewModel::refresh
        )

        if (!loggedIn) {
            UjingLoginContent(
                isBusy = viewModel.isBusy,
                message = viewModel.message,
                onSendCaptcha = viewModel::sendCaptcha,
                onLogin = viewModel::login
            )
        } else {
            UjingContent(
                stores = viewModel.stores,
                selectedStore = viewModel.selectedStore,
                storeInfo = viewModel.storeInfo,
                selectedDeviceTypeId = viewModel.programInfo?.deviceTypeId,
                programs = viewModel.programInfo?.programs.orEmpty(),
                waitMinutes = viewModel.waitMinutes,
                runningOrders = viewModel.runningOrders,
                historyOrders = viewModel.historyOrders,
                recentStores = viewModel.recentStores,
                campuses = viewModel.campuses,
                selectedCampus = viewModel.selectedCampus,
                isBusy = viewModel.isBusy,
                isStoreLoading = viewModel.isStoreLoading,
                message = viewModel.message,
                onSelectCampus = viewModel::selectCampus,
                onSelectRecentStore = viewModel::selectRecentStore,
                onSelectStore = viewModel::selectStore,
                onSelectDeviceType = viewModel::loadPrograms,
                onRefresh = viewModel::refresh,
                onLogout = viewModel::logout,
                onClearMessage = viewModel::clearMessage
            )
        }
    }
}

@Composable
private fun UjingTopBar(
    onBack: () -> Unit,
    loggedIn: Boolean,
    isBusy: Boolean,
    campusName: String,
    onRefresh: () -> Unit
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
        Column(Modifier.weight(1f)) {
            Text(
                text = "U净洗衣",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$campusName · 只读查询",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (loggedIn) {
            TextButton(onClick = onRefresh, enabled = !isBusy) {
                Text(if (isBusy) "同步中" else "刷新")
            }
        }
    }
}

@Composable
private fun UjingLoginContent(
    isBusy: Boolean,
    message: String?,
    onSendCaptcha: (String) -> Unit,
    onLogin: (String, String) -> Unit
) {
    var mobile by rememberSaveable { mutableStateOf("") }
    var captcha by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AppCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge("洗", ChartPrimary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("登录 U净", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "手机号 + 短信验证码；只用于查询洗衣房状态",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { value ->
                            mobile = value.filter { it.isDigit() }.take(11)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("手机号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = captcha,
                        onValueChange = { value ->
                            captcha = value.filter { it.isDigit() }.take(6)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("验证码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    if (message != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSendCaptcha(mobile) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("获取验证码")
                        }
                        Button(
                            onClick = { onLogin(mobile, captcha) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("登录")
                        }
                    }
                }
            }
        }
        item {
            Text(
                text = "U净 的洗衣房分布在广兰 / 青岚校区，登录后可在顶部切换校区；本页只展示空闲数量、" +
                    "等待时间与洗护价格，不会下单、不会支付。登录态保存在本机加密数据库，可随时退出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UjingContent(
    stores: List<UjingStore>,
    selectedStore: UjingStore?,
    storeInfo: com.ecut.campuspocket.data.remote.ujing.UjingStoreInfo?,
    selectedDeviceTypeId: Int?,
    programs: List<UjingProgram>,
    waitMinutes: Int?,
    runningOrders: List<UjingOrder>,
    historyOrders: List<UjingOrder>,
    recentStores: List<UjingStoreRef>,
    campuses: List<UjingCampus>,
    selectedCampus: UjingCampus,
    isBusy: Boolean,
    isStoreLoading: Boolean,
    message: String?,
    onSelectCampus: (UjingCampus) -> Unit,
    onSelectRecentStore: (UjingStoreRef) -> Unit,
    onSelectStore: (UjingStore) -> Unit,
    onSelectDeviceType: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onClearMessage: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                campuses.forEach { campus ->
                    CampusChip(
                        name = campus.name,
                        selected = campus.name == selectedCampus.name,
                        onClick = { onSelectCampus(campus) }
                    )
                }
            }
        }

        if (recentStores.isNotEmpty()) {
            item { SectionHeader("最近使用") }
            items(
                recentStores,
                key = { "recent-${it.storeId}" },
                contentType = { "recent" }
            ) { ref ->
                RecentStoreCard(
                    ref = ref,
                    selected = ref.storeId == selectedStore?.id,
                    onClick = { onSelectRecentStore(ref) }
                )
                if (ref.storeId == selectedStore?.id && storeInfo != null) {
                    Spacer(Modifier.height(10.dp))
                    StoreDetailCard(
                        storeInfo = storeInfo,
                        selectedDeviceTypeId = selectedDeviceTypeId,
                        programs = programs,
                        waitMinutes = waitMinutes,
                        isStoreLoading = isStoreLoading,
                        onSelectDeviceType = onSelectDeviceType
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "附近洗衣房",
                action = "刷新",
                onAction = onRefresh
            )
        }

        if (isBusy && stores.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        items(
            stores,
            key = { it.id },
            contentType = { "store" }
        ) { store ->
            StoreCard(
                store = store,
                selected = store.id == selectedStore?.id,
                onClick = { onSelectStore(store) }
            )
            if (store.id == selectedStore?.id && storeInfo != null) {
                Spacer(Modifier.height(10.dp))
                StoreDetailCard(
                    storeInfo = storeInfo,
                    selectedDeviceTypeId = selectedDeviceTypeId,
                    programs = programs,
                    waitMinutes = waitMinutes,
                    isStoreLoading = isStoreLoading,
                    onSelectDeviceType = onSelectDeviceType
                )
            }
        }

        if (stores.isEmpty() && !isBusy) {
            item {
                Text(
                    text = "附近没有查到洗衣房。可以稍后刷新，或到官方 U净 App 查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (runningOrders.isNotEmpty()) {
            item {
                SectionHeader("正在使用")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    runningOrders.forEach { order ->
                        OrderCard(order = order, running = true)
                    }
                }
            }
        }

        if (historyOrders.isNotEmpty()) {
            item {
                SectionHeader("最近订单")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    historyOrders.take(6).forEach { order ->
                        OrderCard(order = order, running = false)
                    }
                }
            }
        }

        item {
            TextButton(
                onClick = {
                    openUjingAlipay(
                        context,
                        "pages/order-list/order-list?orderType=2"
                    )
                }
            ) {
                Text("在支付宝查看 U净 订单")
            }
        }

        if (message != null) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onClearMessage) { Text("知道了") }
                    }
                }
            }
        }

        item {
            TextButton(onClick = onLogout) {
                Text("退出 U净 登录", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RecentStoreCard(
    ref: UjingStoreRef,
    selected: Boolean,
    onClick: () -> Unit
) {
    AppCard(
        onClick = onClick,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge("洗", ChartPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = ref.storeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "最近使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StoreCard(
    store: UjingStore,
    selected: Boolean,
    onClick: () -> Unit
) {
    AppCard(
        onClick = onClick,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge("洗", ChartPrimary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = store.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = distanceText(store.distanceKm),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = store.serviceSubjectName.ifBlank { "洗衣房" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (store.washerCount > 0) {
                        Text(
                            "洗衣 ${store.washerCount} 台",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (store.dryerCount > 0) {
                        Text(
                            "烘干 ${store.dryerCount} 台",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StoreDetailCard(
    storeInfo: com.ecut.campuspocket.data.remote.ujing.UjingStoreInfo,
    selectedDeviceTypeId: Int?,
    programs: List<UjingProgram>,
    waitMinutes: Int?,
    isStoreLoading: Boolean,
    onSelectDeviceType: (Int) -> Unit
) {
    val context = LocalContext.current
    AppCard {
        Column {
            Text(
                text = storeInfo.storeName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = storeInfo.serviceSubjectName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                storeInfo.devices.forEach { tag ->
                    DeviceChip(
                        tag = tag,
                        selected = tag.deviceTypeId == selectedDeviceTypeId,
                        onClick = { onSelectDeviceType(tag.deviceTypeId) }
                    )
                }
            }
            if (waitMinutes != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (waitMinutes <= 0) "现在有空闲机器" else "预计等待 $waitMinutes 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (waitMinutes <= 0) ChartPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isStoreLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (programs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ThinDivider()
                Spacer(Modifier.height(8.dp))
                programs.forEach { program ->
                    ProgramRow(program)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { openUjingAlipay(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("在支付宝打开 U净")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "小程序里点“扫一扫”扫描机器上的二维码，再选方案并支付；" +
                    "筒自洁、启动洗衣等操作也在小程序内完成。本页只做查询，不创建订单。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CampusChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = name,
        modifier = Modifier
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun DeviceChip(
    tag: UjingDeviceTag,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            color = if (tag.idle > 0) ChartPrimary else MaterialTheme.colorScheme.error,
            size = 6.dp
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${tag.name} ${tag.idle}/${tag.total}",
            style = MaterialTheme.typography.labelSmall,
            color = content
        )
    }
}

@Composable
private fun ProgramRow(program: UjingProgram) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = program.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (program.tags.isNotEmpty()) {
                Text(
                    text = program.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatPrice(program.priceCents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${program.minutes} 分钟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OrderCard(order: UjingOrder, running: Boolean) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${order.storeName} · ${order.deviceTypeName}${order.deviceNo}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = order.createAt.formatOrderTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (running) "进行中" else order.statusText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (running) ChartPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (running && order.remainTime > 0) {
                    Text(
                        text = "剩余 ${order.remainTime} 分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private const val UJING_ALIPAY_APP_ID = "2021002174638299"

/**
 * 拉起 U净 的支付宝小程序。
 *
 * U净 的官方下单链路是“小程序里扫机器二维码 → 选方案 → 支付”，
 * App 只负责跳到小程序首页，不创建订单、不参与支付。
 */
private fun openUjingAlipay(
    context: Context,
    page: String = "pages/home/home"
) {
    val uri = Uri.parse(
        "alipays://platformapi/startapp?appId=$UJING_ALIPAY_APP_ID&page=$page"
    )
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "未安装支付宝，无法打开 U净小程序", Toast.LENGTH_SHORT).show()
        }
}

private fun UjingOrder.statusText(): String = when (status) {
    "50" -> "已完成"
    "10" -> "进行中"
    "20" -> "待开始"
    "40" -> "已完成"
    else -> statusRemark.ifBlank { "状态 $status" }
}

private val UJING_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())

private fun formatPrice(cents: Int): String {
    val value = cents.coerceAtLeast(0)
    return "¥${value / 100}.${(value % 100).toString().padStart(2, '0')}"
}

private fun String.formatOrderTime(): String = runCatching {
    UJING_TIME_FORMATTER.format(Instant.parse(this))
}.getOrDefault(replace('T', ' ').take(16))

private fun distanceText(km: Double): String = when {
    km <= 0.0 -> "附近"
    km < 1.0 -> "${(km * 1000).toInt()} m"
    else -> "%.1f km".format(Locale.CHINA, km)
}
