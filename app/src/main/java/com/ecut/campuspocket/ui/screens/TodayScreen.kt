package com.ecut.campuspocket.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecut.campuspocket.data.ClassTimes
import com.ecut.campuspocket.data.local.CourseEntity
import com.ecut.campuspocket.data.remote.card.CampusCardBill
import com.ecut.campuspocket.data.remote.card.CampusCardOverview
import com.ecut.campuspocket.data.remote.weather.WeatherDaily
import com.ecut.campuspocket.data.remote.weather.WeatherHourly
import com.ecut.campuspocket.data.remote.weather.WeatherSnapshot
import com.ecut.campuspocket.data.remote.weather.alignHour
import com.ecut.campuspocket.data.remote.weather.weatherDescription
import com.ecut.campuspocket.network.CampusNetworkState
import com.ecut.campuspocket.network.CampusNetworkStatus
import com.ecut.campuspocket.ui.components.AppCard
import com.ecut.campuspocket.ui.components.IconBadge
import com.ecut.campuspocket.ui.components.MiniBarChart
import com.ecut.campuspocket.ui.components.liquidGlassBottomBarContentInset
import com.ecut.campuspocket.ui.components.SectionHeader
import com.ecut.campuspocket.ui.components.StatusDot
import com.ecut.campuspocket.ui.components.ThinDivider
import com.ecut.campuspocket.ui.components.glassBodyBrush
import com.ecut.campuspocket.ui.components.glassBorderBrush
import com.ecut.campuspocket.ui.theme.CourseAmber
import com.ecut.campuspocket.ui.theme.CourseBlue
import com.ecut.campuspocket.ui.theme.CourseLavender
import com.ecut.campuspocket.ui.theme.CourseRose
import com.ecut.campuspocket.ui.theme.CourseTeal
import com.ecut.campuspocket.ui.theme.SpendRose
import com.ecut.campuspocket.ui.theme.TealPrimary
import com.ecut.campuspocket.ui.theme.WarningAmber
import com.ecut.campuspocket.ui.theme.WeatherRain
import com.ecut.campuspocket.ui.theme.WeatherSky
import androidx.core.content.ContextCompat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class TodayCourse(
    val start: String,
    val end: String,
    val name: String,
    val room: String,
    val color: Color
)

private val coursePalette = listOf(CourseTeal, CourseBlue, CourseAmber, CourseLavender, CourseRose)

@Composable
fun TodayScreen(
    onOpenElectricity: () -> Unit,
    onOpenCampusCard: () -> Unit,
    onOpenSchedule: () -> Unit,
    modifier: Modifier = Modifier,
    vpnAuthenticated: Boolean = false,
    scheduleViewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.Factory),
    electricityViewModel: ElectricityViewModel = viewModel(factory = ElectricityViewModel.Factory),
    campusCardViewModel: CampusCardViewModel = viewModel(factory = CampusCardViewModel.Factory),
    weatherViewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory)
) {
    val today = remember { LocalDate.now() }
    val dateText = remember {
        today.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    }
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..18 -> "下午好"
            else -> "晚上好"
        }
    }
    val courses by scheduleViewModel.courses.collectAsStateWithLifecycle()
    val electricityDays by electricityViewModel.days.collectAsStateWithLifecycle()
    val todayIndex = today.dayOfWeek.value - 1
    val currentWeek = com.ecut.campuspocket.data.SemesterConfig.weekFor(today)
    val todayCourses = remember(courses, todayIndex, currentWeek) {
        courses.filter { it.dayOfWeek == todayIndex && it.isActiveInWeek(currentWeek) }
            .sortedBy { if (it.startPeriod > 0) it.startPeriod else it.startSlot * 2 + 1 }
            .map { it.toTodayCourse() }
    }
    val latestDay = electricityDays.lastOrNull()
    val latestBalanceDay = remember(electricityDays) {
        electricityDays.lastOrNull { it.balance != null }
    }
    val networkState by CampusNetworkStatus.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            weatherViewModel.refreshWithDeviceLocation()
        }
    }

    LaunchedEffect(Unit) {
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (coarseGranted || fineGranted) {
            weatherViewModel.refreshWithDeviceLocation()
        } else if (!locationPermissionRequested) {
            locationPermissionRequested = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        weatherViewModel.refreshIfStale()
        campusCardViewModel.ensureFresh()
    }

    val dayByEpoch = remember(electricityDays) {
        electricityDays.associateBy { it.epochDay }
    }
    val yesterdayDay = dayByEpoch[today.minusDays(1).toEpochDay()]
    val chartDays = remember(today, dayByEpoch) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            date to dayByEpoch[date.toEpochDay()]
        }
    }
    val chartLabels = chartDays.map { "${it.first.monthValue}/${it.first.dayOfMonth}" }
    val chartValues = chartDays.map { it.second?.totalKwh?.toFloat() ?: 0f }
    val hasChartData = chartValues.any { it > 0f }
    val listBottomPadding = 28.dp + liquidGlassBottomBarContentInset()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 14.dp,
            bottom = listBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { TodayHeader(greeting, dateText, networkState, vpnAuthenticated) }

        item {
            TodayCoursesCard(
                courses = todayCourses,
                hasAnyCourses = courses.isNotEmpty(),
                week = currentWeek,
                todayIndex = todayIndex,
                onClick = onOpenSchedule
            )
        }

        item {
            WeatherCard(
                snapshot = weatherViewModel.snapshot,
                isLoading = weatherViewModel.isLoading,
                message = weatherViewModel.message,
                courses = todayCourses,
                onRefresh = { weatherViewModel.refresh() }
            )
        }

        item {
            ElectricitySection(
                yesterday = yesterdayDay,
                latestBalance = latestBalanceDay?.balance,
                latestBalanceAt = latestBalanceDay?.updatedAt,
                chartLabels = chartLabels,
                chartValues = chartValues,
                hasChartData = hasChartData,
                onOpenElectricity = onOpenElectricity
            )
        }

        item {
            CampusCardSection(
                overview = campusCardViewModel.overview,
                bills = campusCardViewModel.bills,
                isLoading = campusCardViewModel.isLoading,
                onOpenCampusCard = onOpenCampusCard
            )
        }
    }
}

@Composable
private fun TodayHeader(
    greeting: String,
    dateText: String,
    networkState: CampusNetworkState,
    vpnAuthenticated: Boolean
) {
    val (networkLabel, networkColor) = when {
        networkState is CampusNetworkState.OnCampus ->
            "校园网" to MaterialTheme.colorScheme.primary
        networkState is CampusNetworkState.OffCampus && vpnAuthenticated ->
            "零信任已认证" to MaterialTheme.colorScheme.primary
        networkState is CampusNetworkState.OffCampus ->
            "未连接校园网" to MaterialTheme.colorScheme.error
        networkState is CampusNetworkState.Checking ->
            "检测中" to MaterialTheme.colorScheme.onSurfaceVariant
        else ->
            "校园网检测" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            text = "$greeting，同学",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .background(
                        networkColor.copy(alpha = 0.08f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, glassBorderBrush(), RoundedCornerShape(10.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(networkColor, size = 5.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = networkLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = networkColor
                )
            }
        }
    }
}

@Composable
private fun TodayCoursesCard(
    courses: List<TodayCourse>,
    hasAnyCourses: Boolean,
    week: Int,
    todayIndex: Int,
    onClick: () -> Unit
) {
    AppCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = "今日课程",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "第 $week 周 · 周${
                            listOf("一", "二", "三", "四", "五", "六", "日")[todayIndex]
                        }",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                if (courses.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "${courses.size} 门",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (courses.isEmpty()) {
                Text(
                    text = "今天暂无课程",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (hasAnyCourses) {
                        "今天没有排课，点此查看本周完整课表"
                    } else {
                        "登录教务系统并同步后，课表会显示在这里"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                courses.forEachIndexed { index, course ->
                    if (index > 0) {
                        ThinDivider(Modifier.padding(vertical = 10.dp))
                    }
                    CourseRow(course)
                }
            }
        }
    }
}

@Composable
private fun CourseRow(course: TodayCourse) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(52.dp)) {
            Text(
                text = course.start,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = course.end,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .background(course.color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = course.room,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 天气
// ---------------------------------------------------------------------------

private fun weatherIcon(code: Int): ImageVector = when (code) {
    0, 1 -> Icons.Outlined.WbSunny
    2 -> Icons.Outlined.WbCloudy
    3, 45, 48 -> Icons.Outlined.Cloud
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 ->
        Icons.Outlined.Grain
    71, 73, 75, 77, 85, 86 -> Icons.Outlined.AcUnit
    95, 96, 99 -> Icons.Outlined.Thunderstorm
    else -> Icons.Outlined.Cloud
}

private fun weatherTint(code: Int): Color = when (code) {
    0, 1 -> com.ecut.campuspocket.ui.theme.WeatherSun
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherRain
    71, 73, 75, 77, 85, 86 -> Color(0xFF6AA9E9)
    95, 96, 99 -> Color(0xFF7C5CD6)
    else -> WeatherSky
}

private fun currentHour(snapshot: WeatherSnapshot): WeatherHourly? {
    val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    return snapshot.hourly.firstOrNull { it.time == now }
        ?: snapshot.hourly.minByOrNull {
            abs(Duration.between(now, it.time).toMinutes())
        }
}

private fun forecastForCourse(
    course: TodayCourse,
    date: LocalDate,
    hourly: List<WeatherHourly>
): WeatherHourly? {
    val start = alignHour(course.start) ?: return null
    val target = LocalDateTime.of(date, start)
    return hourly.firstOrNull { it.time == target }
        ?: hourly.filter { it.time.toLocalDate() == date }
            .minByOrNull { abs(Duration.between(target, it.time).toMinutes()) }
}

@Composable
private fun WeatherCard(
    snapshot: WeatherSnapshot?,
    isLoading: Boolean,
    message: String?,
    courses: List<TodayCourse>,
    onRefresh: () -> Unit
) {
    val currentCode = snapshot?.now?.weatherCode
        ?: snapshot?.let { currentHour(it)?.weatherCode }
        ?: 2
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = weatherIcon(currentCode),
                    color = weatherTint(currentCode),
                    size = 38.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "校园天气",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${snapshot?.location?.name ?: "南昌 · 广兰校区"} · 逐小时预报",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新天气",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            when {
                snapshot != null -> WeatherContent(snapshot = snapshot, courses = courses)
                isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "正在获取校园天气…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message ?: "天气服务暂不可用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onRefresh,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("重试", style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "天气数据来自 Open-Meteo 公开接口，仅发送设备定位或广兰校区坐标，可离线忽略。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun WeatherContent(snapshot: WeatherSnapshot, courses: List<TodayCourse>) {
    val hour = currentHour(snapshot)
    val temperature = snapshot.now?.temperature ?: hour?.temperature
    val code = snapshot.now?.weatherCode ?: hour?.weatherCode ?: 2
    val description = weatherDescription(code)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = temperature?.let { "${it.roundToInt()}°" } ?: "--",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.titleMedium,
                    color = weatherTint(code),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    snapshot.now?.let {
                        append("体感 ${it.apparentTemperature.roundToInt()}° · 湿度 ${it.humidity}%")
                        append(" · 风速 ${it.windSpeed.roundToInt()} km/h")
                    }
                }.ifBlank { "逐小时预报" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = weatherIcon(code),
            contentDescription = null,
            tint = weatherTint(code),
            modifier = Modifier.size(54.dp)
        )
    }

    val today = LocalDate.now()
    val todayDaily = snapshot.daily.firstOrNull { it.date == today }
    val tomorrowDaily = snapshot.daily.firstOrNull { it.date == today.plusDays(1) }
    if (todayDaily != null || tomorrowDaily != null) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            todayDaily?.let {
                WeatherSummaryPill(
                    label = "今天",
                    value = "${it.minTemperature.roundToInt()}° ~ ${it.maxTemperature.roundToInt()}°",
                    detail = "降水 ${it.precipitationProbabilityMax}%",
                    modifier = Modifier.weight(1f)
                )
            }
            tomorrowDaily?.let {
                WeatherSummaryPill(
                    label = "明天",
                    value = "${it.minTemperature.roundToInt()}° ~ ${it.maxTemperature.roundToInt()}°",
                    detail = "降水 ${it.precipitationProbabilityMax}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    ThinDivider()
    Spacer(Modifier.height(12.dp))

    if (courses.isNotEmpty()) {
        Text(
            text = "上课时段天气",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        courses.forEach { course ->
            val forecast = forecastForCourse(course, today, snapshot.hourly)
            val ended = runCatching {
                LocalTime.now().isAfter(LocalTime.parse(course.end))
            }.getOrDefault(false)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(44.dp)) {
                    Text(
                        text = course.start,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = course.end,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = weatherIcon(forecast?.weatherCode ?: code),
                    contentDescription = null,
                    tint = (weatherTint(forecast?.weatherCode ?: code))
                        .copy(alpha = if (ended) 0.45f else 1f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (ended) 0.5f else 1f
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (forecast != null) {
                            "${forecast.temperature.roundToInt()}° · ${weatherDescription(forecast.weatherCode)}" +
                                if (forecast.precipitationProbability > 0) {
                                    " · 降水 ${forecast.precipitationProbability}%"
                                } else {
                                    " · 无降水"
                                }
                        } else {
                            "暂无该时段逐小时数据"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (ended) 0.55f else 1f
                        )
                    )
                }
            }
        }
    } else {
        Text(
            text = "接下来几小时",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        val nowHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val nextHours = snapshot.hourly.filter { !it.time.isBefore(nowHour) }.take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nextHours.forEach { item ->
                WeatherHourPill(item = item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeatherSummaryPill(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(glassBodyBrush(tinted = true))
            .border(1.dp, glassBorderBrush(), RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeatherHourPill(item: WeatherHourly, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(glassBodyBrush(tinted = true))
            .border(1.dp, glassBorderBrush(), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = item.time.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Icon(
            imageVector = weatherIcon(item.weatherCode),
            contentDescription = null,
            tint = weatherTint(item.weatherCode),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "${item.temperature.roundToInt()}°",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (item.precipitationProbability > 0) {
                "${item.precipitationProbability}%"
            } else {
                "无降水"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// 电费
// ---------------------------------------------------------------------------

@Composable
private fun ElectricitySection(
    yesterday: com.ecut.campuspocket.data.repository.ElectricityDay?,
    latestBalance: Double?,
    latestBalanceAt: Long?,
    chartLabels: List<String>,
    chartValues: List<Float>,
    hasChartData: Boolean,
    onOpenElectricity: () -> Unit
) {
    Column {
        SectionHeader(
            title = "电费",
            action = "详情 ›",
            onAction = onOpenElectricity
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElectricityMiniCard(
                badge = "电",
                badgeColor = WarningAmber,
                title = "昨日用电",
                value = yesterday?.totalKwh?.let { "%.1f".format(it) } ?: "--",
                valueUnit = "度",
                footer = "昨日",
                modifier = Modifier.weight(1f),
                onClick = onOpenElectricity
            )
            ElectricityMiniCard(
                badge = "余",
                badgeColor = TealPrimary,
                title = "电表余额",
                value = latestBalance?.let { "%.2f".format(it) } ?: "--",
                valueUnit = "元",
                footer = latestBalanceAt?.let { "更新于 ${formatClock(it)}" } ?: "尚未同步",
                modifier = Modifier.weight(1f),
                onClick = onOpenElectricity
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (latestBalance != null) {
                    "电表余额 ¥${"%.2f".format(latestBalance)} · ${latestBalanceAt?.let { formatTime(it) } ?: "--"}"
                } else {
                    "尚未同步电费数据"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        AppCard(onClick = onOpenElectricity) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "近 7 天用电",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (hasChartData) {
                            "合计 ${"%.1f".format(chartValues.sum())} 度"
                        } else {
                            "暂无记录"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (hasChartData) {
                    MiniBarChart(values = chartValues, labels = chartLabels)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "同步后这里会显示每天的用电柱状图",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ElectricityMiniCard(
    badge: String,
    badgeColor: Color,
    title: String,
    value: String,
    valueUnit: String,
    footer: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppCard(modifier = modifier, onClick = onClick, contentPadding = 16.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(text = badge, color = badgeColor, size = 32.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = valueUnit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 校园卡
// ---------------------------------------------------------------------------

@Composable
private fun CampusCardSection(
    overview: CampusCardOverview?,
    bills: List<CampusCardBill>,
    isLoading: Boolean,
    onOpenCampusCard: () -> Unit
) {
    Column {
        SectionHeader(
            title = "校园卡",
            action = "详情 ›",
            onAction = onOpenCampusCard
        )
        Spacer(Modifier.height(10.dp))
        if (overview == null) {
            AppCard(onClick = onOpenCampusCard) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Outlined.CreditCard,
                        color = TealPrimary,
                        size = 42.dp
                    )
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (isLoading) "正在读取校园卡…" else "校园卡余额与消费",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (isLoading) {
                                "正在从学校一卡通系统读取余额与流水"
                            } else {
                                "打开校园卡页完成统一认证后自动读取；数据只保存在本机"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "›",
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            val recentSpend = bills.filter { !it.isRecharge }.take(3)
            AppCard(onClick = onOpenCampusCard) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "校园卡余额",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "¥${"%.2f".format(overview.balance)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = buildString {
                                    if (overview.cardState.isNotBlank()) append(overview.cardState)
                                    if (overview.cardNumber.isNotBlank()) {
                                        if (isNotEmpty()) append(" · ")
                                        append(maskCardNumber(overview.cardNumber))
                                    }
                                }.ifBlank { "一卡通" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconBadge(
                            icon = Icons.Outlined.CreditCard,
                            color = TealPrimary,
                            size = 42.dp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampusStat(
                            title = "本月消费",
                            value = "¥${"%.2f".format(overview.monthSpend)}",
                            count = "${overview.monthSpendCount} 笔",
                            accent = SpendRose,
                            modifier = Modifier.weight(1f)
                        )
                        CampusStat(
                            title = "本月充值",
                            value = "¥${"%.2f".format(overview.monthRecharge)}",
                            count = "${overview.monthRechargeCount} 笔",
                            accent = TealPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ThinDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "最近消费",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "查看全部 ›",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    if (recentSpend.isEmpty()) {
                        Text(
                            text = if (isLoading) "正在加载流水…" else "最近暂无消费记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentSpend.forEach { bill ->
                            RecentSpendRow(bill)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CampusStat(
    title: String,
    value: String,
    count: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = count,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentSpendRow(bill: CampusCardBill) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = bill.location.ifBlank { bill.machine.ifBlank { "校园卡消费" } },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatBillTime(bill.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "-${"%.2f".format(bill.amount)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = SpendRose
        )
    }
}

// ---------------------------------------------------------------------------
// 工具
// ---------------------------------------------------------------------------

private fun CourseEntity.isActiveInWeek(week: Int): Boolean {
    if (weeksCsv.isBlank()) return true
    return weeksCsv.split(",").any { it.trim().toIntOrNull() == week }
}

private fun CourseEntity.toTodayCourse(): TodayCourse {
    val start = startPeriod.takeIf { it > 0 } ?: (startSlot * 2 + 1)
    val end = endPeriod.takeIf { it >= start } ?: ((startSlot + slotSpan) * 2)
    val size = coursePalette.size
    return TodayCourse(
        start = ClassTimes.startTime(start) ?: "--:--",
        end = ClassTimes.endTime(end) ?: "--:--",
        name = title,
        room = listOf(campus, room)
            .filter { it.isNotBlank() }
            .joinToString(" "),
        color = coursePalette[((colorSeed % size) + size) % size]
    )
}

private fun formatTime(millis: Long): String {
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatClock(millis: Long): String {
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}

private fun formatBillTime(raw: String): String {
    val cleaned = raw.replace(Regex("""^\d{4}-"""), "")
    return cleaned.substringBeforeLast(":").take(16)
}

private fun maskCardNumber(number: String): String {
    if (number.length <= 4) return number
    return "****" + number.takeLast(4)
}
