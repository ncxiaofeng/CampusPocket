package com.ecut.campuspocket.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局液态玻璃卡片：半透明渐变底、高光描边、柔和投影。
 *
 * containerColor 传入默认 surface 时使用玻璃质感；
 * 业务传了自定义色（提醒卡 / 品牌卡）时保留该色调并叠加透明度。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    val dark = isGlassDark()
    val usesDefault = containerColor == MaterialTheme.colorScheme.surface
    val tint = if (usesDefault) Color.Transparent else containerColor
    val border = BorderStroke(1.dp, glassBorderBrush())
    val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val shadowed = modifier.shadow(
        elevation = if (dark) 8.dp else 14.dp,
        shape = shape,
        clip = false,
        ambientColor = glassShadowColor(),
        spotColor = glassShadowColor()
    )
    val inner: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxWidth()
                .background(glassBodyBrush())
                .background(tint)
        ) {
            GlassOverlay(Modifier.matchParentSize())
            Box(Modifier.padding(contentPadding)) { content() }
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = shadowed,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border
        ) {
            inner()
        }
    } else {
        Card(
            modifier = shadowed,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border
        ) {
            inner()
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .then(
                        if (onAction != null) Modifier.clickable(onClick = onAction)
                        else Modifier
                    )
                    .padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * 服务图标映射。任何未知标识都会回退到通用图标，
 * 不再把单个汉字当作图标绘制。
 */
private fun serviceIcon(text: String): ImageVector = when (text) {
    "洗" -> Icons.Outlined.LocalLaundryService
    "水" -> Icons.Outlined.WaterDrop
    "电" -> Icons.Outlined.Bolt
    "卡" -> Icons.Outlined.CreditCard
    "图" -> Icons.Outlined.LocalLibrary
    "教" -> Icons.Outlined.School
    "告" -> Icons.Outlined.Campaign
    "厅" -> Icons.Outlined.Business
    "绩" -> Icons.Outlined.Assessment
    "考" -> Icons.AutoMirrored.Outlined.EventNote
    "课" -> Icons.Outlined.CalendarMonth
    "零" -> Icons.Outlined.Shield
    "隧" -> Icons.Outlined.VpnKey
    "同" -> Icons.Outlined.Sync
    "跑" -> Icons.AutoMirrored.Outlined.DirectionsRun
    "隐" -> Icons.Outlined.PrivacyTip
    "余" -> Icons.Outlined.AccountBalanceWallet
    "更" -> Icons.Outlined.SystemUpdateAlt
    "座" -> Icons.Outlined.Chair
    "关" -> Icons.Outlined.Info
    "C" -> Icons.Outlined.Info
    else -> Icons.Outlined.Apps
}

@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    // 粉彩色直接当图标色在浅色玻璃上太淡；浅色主题下把前景压深一档。
    val iconTint = if (!isGlassDark() && color.luminance() > 0.62f) {
        lerp(color, Color(0xFF123B36), 0.58f)
    } else {
        color
    }
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.linearGradient(
                    listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.10f))
                ),
                shape = RoundedCornerShape(size * 0.32f)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f))
                ),
                shape = RoundedCornerShape(size * 0.32f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

@Composable
fun IconBadge(
    text: String,
    color: Color,
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    IconBadge(
        icon = serviceIcon(text),
        color = color,
        size = size,
        modifier = modifier
    )
}

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 7.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
    )
}

/**
 * 7 天微型柱状图。
 */
@Composable
fun MiniBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlightLast: Boolean = true
) {
    val primary = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val baseline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
        ) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barWidth = slot * 0.42f
            val maxValue = values.maxOrNull()?.coerceAtLeast(0.1f) ?: 1f
            // 基线
            drawLine(
                color = baseline,
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1.5f
            )
            values.forEachIndexed { index, value ->
                val ratio = (value / maxValue).coerceIn(0.04f, 1f)
                val barHeight = size.height * 0.82f * ratio
                val left = slot * index + (slot - barWidth) / 2f
                val isLast = index == values.lastIndex
                val color = if (isLast && highlightLast) primary else base
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
                if (isLast && highlightLast) {
                    drawCircle(
                        color = lerp(primary, Color.White, 0.55f),
                        radius = barWidth * 0.16f,
                        center = Offset(left + barWidth / 2f, size.height - barHeight - 8.dp.toPx())
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == labels.lastIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 可选中的胶囊标签，用于日期 / 校区 / 节次等筛选。
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .background(glassPillBrush(selected), shape)
            .border(1.dp, glassBorderBrush(), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
fun ValueWithUnit(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = valueColor
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
