package com.ecut.campuspocket.ui.theme

import androidx.compose.ui.graphics.Color

// 品牌青绿（液态玻璃主题）
val TealPrimary = Color(0xFF0B7C74)
val TealOnPrimary = Color(0xFFFFFFFF)
val TealContainer = Color(0xFFA7F3E8)
val TealOnContainer = Color(0xFF053B36)
val TealDark = Color(0xFF5EEAD4)

val SlateSecondary = Color(0xFF486A87)
val SlateContainer = Color(0xFFD7E8F7)
val SlateOnContainer = Color(0xFF10293B)

val BlueTertiary = Color(0xFF5B5BD6)
val BlueContainer = Color(0xFFE0E0FF)

// 背景由 GlassBackdrop 绘制，colorScheme.background 保持透明，
// 这样各个页面的 .background(background) 不会盖住全局玻璃底色。
val AppBackground = Color(0x00000000)
val AppBackgroundDark = Color(0x00000000)
val SurfaceLight = Color(0xFFF9FEFD)
val SurfaceDark = Color(0xFF121D1C)

val TextPrimary = Color(0xFF10201E)
val TextSecondary = Color(0xFF4F615D)
val OutlineSoft = Color(0xFFD4E3DF)

val ErrorRed = Color(0xFFB3261E)
val WarningAmber = Color(0xFFA85B00)

// 液态玻璃背景与光斑
val BackdropLightTop = Color(0xFFE7F7F3)
val BackdropLightMid = Color(0xFFEFF3FF)
val BackdropLightBottom = Color(0xFFF8F4FF)
val BackdropDarkTop = Color(0xFF061412)
val BackdropDarkMid = Color(0xFF0A1420)
val BackdropDarkBottom = Color(0xFF101022)

val GlowTeal = Color(0xFF2DD4BF)
val GlowBlue = Color(0xFF60A5FA)
val GlowViolet = Color(0xFFA78BFA)
val GlowRose = Color(0xFFF9A8D4)

// 非课表区域使用的服务点缀色（粉彩）
val CourseTeal = Color(0xFFA7F3D9)
val CourseBlue = Color(0xFFBFDBFE)
val CourseAmber = Color(0xFFFDE68A)
val CourseLavender = Color(0xFFDDD6FE)
val CourseRose = Color(0xFFFBCFE8)
val CourseInk = Color(0xFF123B36)

// 课表课程块：高饱和底色 + 白色文字，参考主流课程表 App 的可读性
val SchedulePink = Color(0xFFD94F7E)
val ScheduleBlue = Color(0xFF3B7BBF)
val ScheduleGreen = Color(0xFF1F8F82)
val ScheduleOrange = Color(0xFFB86A0A)
val SchedulePurple = Color(0xFF7C5CC4)

// 底栏中心快捷按钮 / 选中态
val ActionGreen = Color(0xFF0FB36B)
val ActionGreenBright = Color(0xFF19C97A)
val ActionGreenDeep = Color(0xFF087A4B)

// 图表配色
val ChartPrimary = Color(0xFF0D9488)
val ChartSecondary = Color(0xFF5EEAD4)
val ChartAc = Color(0xFF60A5FA)
val ChartGrid = Color(0xFFD7E7E3)

// 天气 / 消费点缀色
val WeatherSky = Color(0xFF2D7FF9)
val WeatherSun = Color(0xFFF59E0B)
val WeatherRain = Color(0xFF3B82F6)
val SpendRose = Color(0xFFD14D72)
