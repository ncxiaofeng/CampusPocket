package com.ecut.campuspocket.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.ecut.campuspocket.ui.theme.BackdropDarkBottom
import com.ecut.campuspocket.ui.theme.BackdropDarkMid
import com.ecut.campuspocket.ui.theme.BackdropDarkTop
import com.ecut.campuspocket.ui.theme.BackdropLightBottom
import com.ecut.campuspocket.ui.theme.BackdropLightMid
import com.ecut.campuspocket.ui.theme.BackdropLightTop
import com.ecut.campuspocket.ui.theme.GlowBlue
import com.ecut.campuspocket.ui.theme.GlowRose
import com.ecut.campuspocket.ui.theme.GlowTeal
import com.ecut.campuspocket.ui.theme.GlowViolet

/** 当前是否为深色玻璃主题。 */
@Composable
fun isGlassDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * 全屏液态玻璃底色：柔和渐变 + 几团失焦光斑。
 *
 * 这里刻意不使用 Modifier.blur（minSdk 26 上不生效），
 * 而是用径向渐变模拟毛玻璃后面的色块，性能也更稳定。
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val dark = isGlassDark()
    val top = if (dark) BackdropDarkTop else BackdropLightTop
    val mid = if (dark) BackdropDarkMid else BackdropLightMid
    val bottom = if (dark) BackdropDarkBottom else BackdropLightBottom
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, mid, bottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val alpha = if (dark) 0.28f else 0.44f

            fun glow(color: Color, cx: Float, cy: Float, radius: Float) {
                val center = Offset(w * cx, h * cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            glow(GlowTeal, 0.12f, 0.10f, w * 0.85f)
            glow(GlowBlue, 0.92f, 0.20f, w * 0.70f)
            glow(GlowViolet, 0.78f, 0.72f, w * 0.90f)
            glow(GlowRose, 0.08f, 0.86f, w * 0.65f)
        }
    }
}

/** 玻璃卡片的底色渐变。 */
@Composable
fun glassBodyBrush(tinted: Boolean = false): Brush {
    val dark = isGlassDark()
    return if (dark) {
        Brush.linearGradient(
            listOf(
                Color(0xFF233835).copy(alpha = 0.82f),
                Color(0xFF111D1C).copy(alpha = 0.68f)
            )
        )
    } else if (tinted) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.46f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.72f),
                Color(0xFFEFFBF8).copy(alpha = 0.46f)
            )
        )
    }
}

/**
 * 玻璃高光描边：左上偏白、右下带一点青/紫的色散，
 * 模拟液态玻璃边缘的折射色边。
 */
@Composable
fun glassBorderBrush(): Brush {
    val dark = isGlassDark()
    return if (dark) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.30f),
                Color.White.copy(alpha = 0.08f),
                GlowTeal.copy(alpha = 0.18f),
                GlowViolet.copy(alpha = 0.22f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.98f),
                Color.White.copy(alpha = 0.42f),
                GlowTeal.copy(alpha = 0.22f),
                GlowViolet.copy(alpha = 0.24f)
            )
        )
    }
}

/**
 * 卡片内部的顶部高光、细长的镜面反射和底部内阴影。
 * 放在内容下方、玻璃底色上方，让平面渐变也有“玻璃厚度”。
 */
@Composable
fun GlassOverlay(modifier: Modifier = Modifier) {
    val dark = isGlassDark()
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.12f else 0.30f),
                0.42f to Color.Transparent,
                1f to Color.Transparent
            )
        )
        // 一条沿上边缘的镜面高光，不横穿文字。
        drawLine(
            color = Color.White.copy(alpha = if (dark) 0.22f else 0.72f),
            start = Offset(size.width * 0.07f, 1.6.dp.toPx()),
            end = Offset(size.width * 0.52f, 1.6.dp.toPx()),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )
        // 底部细微的玻璃厚度。
        drawLine(
            color = Color.Black.copy(alpha = if (dark) 0.24f else 0.045f),
            start = Offset(size.width * 0.18f, size.height - 1.dp.toPx()),
            end = Offset(size.width * 0.94f, size.height - 1.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/** 深色模式下的玻璃阴影颜色。 */
@Composable
fun glassShadowColor(): Color = if (isGlassDark()) {
    Color.Black.copy(alpha = 0.22f)
} else {
    Color(0xFF2A5B55).copy(alpha = 0.10f)
}

/** 选中胶囊等小面积的玻璃底色。 */
@Composable
fun glassPillBrush(selected: Boolean): Brush {
    val dark = isGlassDark()
    val primary = MaterialTheme.colorScheme.primary
    return when {
        selected -> Brush.horizontalGradient(
            listOf(primary.copy(alpha = 0.90f), primary.copy(alpha = 0.74f))
        )
        dark -> Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
        )
        else -> Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.34f))
        )
    }
}
