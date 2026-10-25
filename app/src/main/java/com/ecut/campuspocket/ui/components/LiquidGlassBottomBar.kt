package com.ecut.campuspocket.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.ui.theme.ActionGreen
import com.ecut.campuspocket.ui.theme.ActionGreenDeep
import com.ecut.campuspocket.ui.theme.GlowTeal
import com.ecut.campuspocket.ui.theme.GlowViolet
import kotlin.math.roundToInt

/**
 * 可复用的底部 Tab 数据。
 *
 * 组件不关心路由；调用方根据 index 切页即可。
 */
data class LiquidGlassTabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

// 底栏本身的高度。FAB 放在底栏内部，不再单独向外探出。
private val BarHeight = 72.dp
private val FabSize = 56.dp
private val CenterGap = 68.dp
private val BarHorizontalMargin = 16.dp
private val BarBottomGap = 10.dp
private val BarCorner = 36.dp

/** 底栏布局占用的高度（不含系统导航栏安全区）。 */
val LiquidGlassBottomBarHeight: Dp = BarHeight + BarBottomGap

/**
 * 页面滚动内容需要预留的底部空间。
 *
 * 现在底栏是“悬浮覆盖”在内容上的，所以内容可以滑到玻璃后面；
 * 但列表末尾必须留出足够空间，避免最后一张卡片被底栏永久盖住。
 */
@Composable
fun liquidGlassBottomBarContentInset(): Dp {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return LiquidGlassBottomBarHeight + navBar + 16.dp
}

/**
 * 真实背景采样状态。
 *
 * Compose 没有 Flutter 的 BackdropFilter，但可以用 GraphicsLayer 实现同样的事：
 * 1. 把页面内容记录进一个 GraphicsLayer（source）；
 * 2. 在底栏里按坐标偏移绘制这个 Layer；
 * 3. 给底栏内的绘制层加 RenderEffect 高斯模糊。
 *
 * 这样底栏看到的是“页面此刻真实的像素”，滚动、卡片、文字都会被实时模糊，
 * 而不是一块假的半透明白色背景。
 */
class LiquidGlassBackdropState internal constructor(
    internal val layer: GraphicsLayer
) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
    internal var barOrigin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberLiquidGlassBackdropState(): LiquidGlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { LiquidGlassBackdropState(layer) }
}

/**
 * 给页面内容加上这个 Modifier，内容就会被记录到 [state]。
 * 注意：要放在 Scaffold 的 body 内、bottomBar 之外，避免把底栏自己采样进去。
 */
fun Modifier.liquidGlassBackdropSource(state: LiquidGlassBackdropState): Modifier =
    this
        .onGloballyPositioned { coordinates ->
            state.sourceOrigin = coordinates.positionInRoot()
        }
        .drawWithContent {
            val layerSize = IntSize(
                size.width.roundToInt(),
                size.height.roundToInt()
            )
            state.layer.record(
                density = this,
                layoutDirection = layoutDirection,
                size = layerSize
            ) {
                this@drawWithContent.drawContent()
            }
            drawContent()
        }

/**
 * 液态玻璃悬浮底栏。
 *
 * 布局：
 * - 底栏是独立悬浮的完整圆角玻璃片，固定在 Scaffold.bottomBar；
 * - 绿色加号放在底栏内部居中，不再和底栏分离；
 * - 内容滚动到玻璃后面时，底栏会实时采样并模糊背后的页面内容；
 * - 底部通过 navigationBarsPadding + 10dp 间距避开系统手势条。
 */
@Composable
fun LiquidGlassBottomBar(
    tabs: List<LiquidGlassTabItem>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LiquidGlassBackdropState? = null
) {
    val shape = RoundedCornerShape(BarCorner)
    val dark = isGlassDark()
    val glassTint = remember(dark) { glassTintBrush(dark) }
    val borderBrush = remember(dark) { glassEdgeBrush(dark) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = BarHorizontalMargin,
                end = BarHorizontalMargin,
                bottom = BarBottomGap
            )
            .height(BarHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 22.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color(0xFF6FA8FF).copy(alpha = if (dark) 0.18f else 0.16f),
                    spotColor = Color(0xFF9B7BE0).copy(alpha = if (dark) 0.22f else 0.20f)
                )
                .clip(shape)
                .border(1.dp, borderBrush, shape)
                .onGloballyPositioned { coordinates ->
                    backdrop?.barOrigin = coordinates.positionInRoot()
                }
        ) {
            // 1. 真实背景采样：把页面内容按坐标偏移绘制进来，再用 RenderEffect 模糊。
            if (backdrop != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                renderEffect = BlurEffect(
                                    radiusX = 22.dp.toPx(),
                                    radiusY = 22.dp.toPx(),
                                    edgeTreatment = TileMode.Clamp
                                )
                            }
                        }
                        .drawBehind {
                            val dx = backdrop.sourceOrigin.x - backdrop.barOrigin.x
                            val dy = backdrop.sourceOrigin.y - backdrop.barOrigin.y
                            translate(left = dx, top = dy) {
                                drawLayer(backdrop.layer)
                            }
                        }
                )
            }

            // 2. 很薄的玻璃着色：只负责轻微提亮/调色，不能盖住真实采样。
            Box(
                Modifier
                    .matchParentSize()
                    .background(glassTint)
            )

            // 3. 顶部镜面高光 + 底部内阴影。
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val highlight = Color.White.copy(alpha = if (dark) 0.26f else 0.72f)
                val topY = 1.4.dp.toPx()
                drawLine(
                    color = highlight,
                    start = Offset(w * 0.06f, topY),
                    end = Offset(w * 0.94f, topY),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.Black.copy(alpha = if (dark) 0.22f else 0.045f),
                    start = Offset(w * 0.12f, h - 1.dp.toPx()),
                    end = Offset(w * 0.88f, h - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. Tab：中间空出 FAB 的位置。
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    if (index == 2) {
                        Spacer(Modifier.width(CenterGap))
                    }
                    LiquidGlassTab(
                        item = tab,
                        selected = index == selectedIndex,
                        onClick = { onTabSelected(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 绿色加号：放在底栏内部居中，和底栏融为一体，而不是悬空剥离。
        FloatingActionButton(
            onClick = onCenterClick,
            modifier = Modifier
                .align(Alignment.Center)
                .size(FabSize)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = ActionGreen.copy(alpha = 0.38f),
                    spotColor = ActionGreen.copy(alpha = 0.48f)
                )
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.70f),
                    shape = CircleShape
                ),
            shape = CircleShape,
            containerColor = ActionGreen,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "快捷操作",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun LiquidGlassTab(
    item: LiquidGlassTabItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isGlassDark()
    val selectedColor = if (dark) Color(0xFF5BE39B) else ActionGreenDeep
    val unselectedColor = if (dark) Color(0xFFD0D9E2) else Color(0xFF5A6878)
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        label = "liquidGlassTabColor"
    )
    val indicatorColor by animateColorAsState(
        targetValue = when {
            !selected -> Color.Transparent
            dark -> Color.White.copy(alpha = 0.12f)
            else -> Color.White.copy(alpha = 0.45f)
        },
        label = "liquidGlassTabIndicator"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            color = iconColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * 底栏着色层：低透明度，让下面的真实模糊采样透出来。
 * 深色模式单独调低亮度，避免发灰。
 */
private fun glassTintBrush(dark: Boolean): Brush = if (dark) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF0E1716).copy(alpha = 0.46f),
            Color(0xFF152233).copy(alpha = 0.58f),
            Color(0xFF0B1413).copy(alpha = 0.70f)
        )
    )
} else {
    // 上半部分保留真实采样，下半部分逐渐加重，保证 Tab 文字压在真实内容上也可读。
    Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.52f),
            Color(0xFFDCEFFF).copy(alpha = 0.78f),
            Color.White.copy(alpha = 0.92f)
        )
    )
}

private fun glassEdgeBrush(dark: Boolean): Brush = if (dark) {
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
            GlowTeal.copy(alpha = 0.24f),
            GlowViolet.copy(alpha = 0.26f)
        )
    )
}
