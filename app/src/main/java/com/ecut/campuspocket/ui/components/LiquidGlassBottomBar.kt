package com.ecut.campuspocket.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.ui.theme.ActionGreen
import com.ecut.campuspocket.ui.theme.ActionGreenDeep
import com.ecut.campuspocket.ui.theme.GlowTeal
import com.ecut.campuspocket.ui.theme.GlowViolet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
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
private val RowHorizontalPadding = 6.dp

/** 底栏布局占用的高度（不含系统导航栏安全区）。 */
val LiquidGlassBottomBarHeight: Dp = BarHeight + BarBottomGap

/**
 * 页面滚动内容需要预留的底部空间。
 */
@Composable
fun liquidGlassBottomBarContentInset(): Dp {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return LiquidGlassBottomBarHeight + navBar + 16.dp
}

/**
 * 真实背景采样状态。
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
 * 液态玻璃悬浮底栏（含自由滑动液态玻璃透镜滑块，移除默认深色点击阴影）。
 *
 * 交互：
 * - 无 Material 灰色 Ripple 阴影；
 * - 触摸按下时立刻浮起液态玻璃滑块，并可左右自由拖动（支持平滑跨越中央 `+` 按钮）；
 * - 滑块所到之处实时高亮下方 Tab 图标与文字并伴随细腻触觉反馈；
 * - 松手后弹簧吸附至目标 Tab 并切换页面。
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
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val glassTint = remember(dark) { glassTintBrush(dark) }
    val borderBrush = remember(dark) { glassEdgeBrush(dark) }

    // 避免 pointerInput 闭包长期捕获冷启动时的旧 selectedIndex（会导致切去别页后点不回首页）。
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(selectedIndex) }
    var sliderInitialized by remember { mutableStateOf(false) }

    val padPx = with(density) { RowHorizontalPadding.toPx() }
    val centerGapPx = with(density) { CenterGap.toPx() }

    val layoutMetrics = remember(barWidthPx, tabs.size, padPx, centerGapPx) {
        computeTabMetrics(
            totalWidth = barWidthPx,
            tabCount = tabs.size,
            horizontalPadding = padPx,
            centerGap = centerGapPx
        )
    }

    // 滑块位置与拉伸仅在 graphicsLayer 绘制阶段读取，拖动与动画期间 0 次重组、满帧跟手。
    val sliderCenterX = remember { Animatable(0f) }
    val stretchAnim = remember { Animatable(0f) }
    val dragPositionPx = remember { mutableFloatStateOf(0f) }
    val dragStretchRaw = remember { mutableFloatStateOf(0f) }
    val isDraggingRaw = remember { mutableStateOf(false) }

    // 外部 selectedIndex 变化（或首次量出宽度）时，平滑滑移到对应 Tab 中心。
    LaunchedEffect(selectedIndex, layoutMetrics) {
        if (!isTouching && layoutMetrics != null && selectedIndex in tabs.indices) {
            hoveredIndex = selectedIndex
            val targetX = layoutMetrics.centerOf(selectedIndex)
            if (!sliderInitialized) {
                sliderCenterX.snapTo(targetX)
                sliderInitialized = true
            } else {
                sliderCenterX.animateTo(
                    targetValue = targetX,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = 260f
                    )
                )
            }
        }
    }

    // 用 State 对象传入 graphicsLayer，避免动画每帧触发底栏重组。
    val pillScaleState = animateFloatAsState(
        targetValue = if (isTouching) 1.11f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = 280f
        ),
        label = "liquidPillScale"
    )
    val pillAlphaState = animateFloatAsState(
        targetValue = if (isTouching) 1f else 0.62f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 260f
        ),
        label = "liquidPillAlpha"
    )

    val activeIndex = if (isTouching) hoveredIndex else selectedIndex

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
                .onSizeChanged { size ->
                    barWidthPx = size.width.toFloat()
                }
                .pointerInput(tabs.size, layoutMetrics) {
                    val metrics = layoutMetrics ?: return@pointerInput
                    val dragThresholdPx = with(density) { 4.dp.toPx() }
                    var activeAnimJob: Job? = null
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        // 如果起点落在中央 `+` 按钮正中，交由 FAB 自己处理点击。
                        if (metrics.isInCenterGap(downX)) {
                            return@awaitEachGesture
                        }

                        activeAnimJob?.cancel()
                        val clampedStart = metrics.clampSliderX(downX)
                        val initialIdx = metrics.nearestTabIndex(clampedStart)
                        hoveredIndex = initialIdx
                        isTouching = true
                        isDraggingRaw.value = false
                        dragPositionPx.floatValue = clampedStart
                        dragStretchRaw.floatValue = 0f
                        if (!sliderInitialized) {
                            sliderInitialized = true
                            activeAnimJob = scope.launch { sliderCenterX.snapTo(clampedStart) }
                        } else {
                            // 按下其他 Tab 时先柔和滑向按压点，避免突兀瞬移。
                            activeAnimJob = scope.launch {
                                sliderCenterX.animateTo(
                                    targetValue = clampedStart,
                                    animationSpec = spring(
                                        dampingRatio = 0.82f,
                                        stiffness = 320f
                                    )
                                )
                            }
                        }

                        var targetIdx = initialIdx
                        var lastX = downX
                        var dragging = false
                        var smoothStretch = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (!change.pressed) {
                                break
                            }
                            val currentX = change.position.x
                            val deltaX = currentX - lastX
                            lastX = currentX
                            if (!dragging && abs(currentX - downX) >= dragThresholdPx) {
                                dragging = true
                                activeAnimJob?.cancel()
                                isDraggingRaw.value = true
                            }
                            if (dragging) {
                                val clamped = metrics.clampSliderX(currentX)
                                // 拖动时直接写绘制层状态，不启协程、不触发重组，1:1 零延迟紧跟手指。
                                dragPositionPx.floatValue = clamped
                                val instantStretch = (abs(deltaX) / 24f).coerceIn(0f, 0.14f)
                                smoothStretch = smoothStretch * 0.72f + instantStretch * 0.28f
                                dragStretchRaw.floatValue = smoothStretch

                                val nextIdx = metrics.nearestTabIndex(clamped)
                                if (nextIdx != targetIdx) {
                                    targetIdx = nextIdx
                                    hoveredIndex = nextIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            change.consume()
                        }

                        isTouching = false
                        activeAnimJob?.cancel()
                        val finalCenter = metrics.centerOf(targetIdx)
                        val releaseX = if (dragging) dragPositionPx.floatValue else sliderCenterX.value
                        val releaseStretch = if (dragging) dragStretchRaw.floatValue else stretchAnim.value
                        activeAnimJob = scope.launch {
                            if (dragging) {
                                sliderCenterX.snapTo(releaseX)
                                isDraggingRaw.value = false
                            }
                            sliderCenterX.animateTo(
                                targetValue = finalCenter,
                                animationSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = 260f
                                )
                            )
                        }
                        scope.launch {
                            if (dragging) {
                                stretchAnim.snapTo(releaseStretch)
                            }
                            stretchAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.72f,
                                    stiffness = 280f
                                )
                            )
                        }
                        if (targetIdx != currentSelectedIndex) {
                            currentOnTabSelected(targetIdx)
                        }
                    }
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

            // 3. 顶部镜面高光 + 底部内阴影（深色模式下外层 borderBrush 已提供细腻边缘高光，避免额外直线形成灰白横线）。
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                if (!dark) {
                    val topY = 1.4.dp.toPx()
                    drawLine(
                        color = Color.White.copy(alpha = 0.72f),
                        start = Offset(w * 0.06f, topY),
                        end = Offset(w * 0.94f, topY),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = Color.Black.copy(alpha = if (dark) 0.22f else 0.045f),
                    start = Offset(w * 0.12f, h - 1.dp.toPx()),
                    end = Offset(w * 0.88f, h - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 4. 液态玻璃可滑动透镜滑块（GPU 图层平移，0 重组满帧跟手）。
            if (layoutMetrics != null && sliderInitialized) {
                val pillWidthPx = (layoutMetrics.tabWidth - with(density) { 8.dp.toPx() })
                    .coerceAtLeast(with(density) { 52.dp.toPx() })
                val pillHeightDp = BarHeight - 14.dp
                val pillWidthDp = with(density) { pillWidthPx.toDp() }

                LiquidGlassLensPill(
                    dark = dark,
                    isTouching = isTouching,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(pillWidthDp)
                        .height(pillHeightDp)
                        .graphicsLayer {
                            val scale = pillScaleState.value
                            val centerX = if (isDraggingRaw.value) {
                                dragPositionPx.floatValue
                            } else {
                                sliderCenterX.value
                            }
                            val stretch = if (isDraggingRaw.value) {
                                dragStretchRaw.floatValue
                            } else {
                                stretchAnim.value
                            }
                            translationX = centerX - pillWidthPx / 2f
                            alpha = pillAlphaState.value
                            scaleX = scale * (1f + stretch)
                            scaleY = scale * (1f - stretch * 0.45f)
                        }
                )
            }

            // 5. Tab：中间空出 FAB 的位置；无 Ripple 阴影，状态实时响应滑块位置。
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = RowHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    if (index == 2) {
                        Spacer(Modifier.width(CenterGap))
                    }
                    LiquidGlassTab(
                        item = tab,
                        selected = index == activeIndex,
                        emphasized = isTouching && index == activeIndex,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 绿色加号：放在底栏内部居中，和底栏融为一体。
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
                    color = Color.White.copy(alpha = if (dark) 0.34f else 0.70f),
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

/**
 * 触摸/选中时的液态玻璃透镜滑块：
 * 通透高光体 + 顶部折射弧线 + 青/紫色散边缘 + 极轻内部立体阴影。
 */
@Composable
private fun LiquidGlassLensPill(
    dark: Boolean,
    isTouching: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(h / 2f, h / 2f)

        // 柔和底光晕（拖动/触摸时更通透明亮）
        val bodyBrush = if (dark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isTouching) 0.22f else 0.14f),
                    Color(0xFF7BE8D2).copy(alpha = if (isTouching) 0.14f else 0.08f),
                    Color.White.copy(alpha = if (isTouching) 0.10f else 0.05f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (isTouching) 0.92f else 0.72f),
                    Color(0xFFE6FAFF).copy(alpha = if (isTouching) 0.68f else 0.42f),
                    Color.White.copy(alpha = if (isTouching) 0.76f else 0.52f)
                )
            )
        }
        drawRoundRect(brush = bodyBrush, cornerRadius = radius)

        // 顶部镜面折射高光：仅在浅色亮玻璃上绘制，深色下避免顶部出现生硬的灰白色横线
        if (!dark) {
            val specularBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.96f),
                    Color.Transparent
                )
            )
            drawLine(
                brush = specularBrush,
                start = Offset(w * 0.22f, 2.2.dp.toPx()),
                end = Offset(w * 0.78f, 2.2.dp.toPx()),
                strokeWidth = (if (isTouching) 2.dp else 1.4.dp).toPx(),
                cap = StrokeCap.Round
            )
        }

        // 液态玻璃色散描边（白 → 青 → 紫）
        val strokeBrush = Brush.linearGradient(
            colors = if (dark) {
                listOf(
                    Color.White.copy(alpha = if (isTouching) 0.36f else 0.22f),
                    GlowTeal.copy(alpha = if (isTouching) 0.34f else 0.18f),
                    GlowViolet.copy(alpha = if (isTouching) 0.30f else 0.16f),
                    Color.White.copy(alpha = if (isTouching) 0.24f else 0.14f)
                )
            } else {
                listOf(
                    Color.White.copy(alpha = if (isTouching) 0.98f else 0.85f),
                    GlowTeal.copy(alpha = if (isTouching) 0.48f else 0.26f),
                    GlowViolet.copy(alpha = if (isTouching) 0.42f else 0.22f),
                    Color.White.copy(alpha = if (isTouching) 0.92f else 0.72f)
                )
            },
            start = Offset.Zero,
            end = Offset(w, h)
        )
        drawRoundRect(
            brush = strokeBrush,
            cornerRadius = radius,
            style = Stroke(width = (if (isTouching) 1.35.dp else 1.dp).toPx())
        )
    }
}

@Composable
private fun LiquidGlassTab(
    item: LiquidGlassTabItem,
    selected: Boolean,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val dark = isGlassDark()
    val selectedColor = if (dark) Color(0xFF5BE39B) else ActionGreenDeep
    val unselectedColor = if (dark) Color(0xFFD0D9E2) else Color(0xFF5A6878)
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        label = "liquidGlassTabColor"
    )
    val contentScale by animateFloatAsState(
        targetValue = when {
            emphasized -> 1.08f
            selected -> 1.03f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquidGlassTabScale"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                this.role = Role.Tab
                this.selected = selected
                this.contentDescription = item.label
            }
            .graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = item.label,
            tint = iconColor,
            modifier = Modifier.size(23.dp)
        )
        Spacer(Modifier.height(3.dp))
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
 * 底部 4 Tab + 中央 FAB 空隙的几何计算（纯函数，便于单测验证滑动落点）。
 */
internal data class TabBarMetrics(
    val totalWidth: Float,
    val horizontalPadding: Float,
    val centerGap: Float,
    val tabWidth: Float,
    val tabCount: Int
) {
    fun centerOf(index: Int): Float {
        val clamped = index.coerceIn(0, (tabCount - 1).coerceAtLeast(0))
        val base = horizontalPadding + clamped * tabWidth + tabWidth / 2f
        return if (clamped >= 2) base + centerGap else base
    }

    fun isInCenterGap(x: Float): Boolean {
        val gapLeft = horizontalPadding + 2 * tabWidth
        val gapRight = gapLeft + centerGap
        return x in gapLeft..gapRight
    }

    fun clampSliderX(x: Float): Float {
        if (tabCount <= 0) return x
        val minX = centerOf(0)
        val maxX = centerOf(tabCount - 1)
        return x.coerceIn(minX, maxX)
    }

    fun nearestTabIndex(x: Float): Int {
        if (tabCount <= 0) return 0
        var bestIdx = 0
        var bestDist = Float.MAX_VALUE
        for (i in 0 until tabCount) {
            val dist = abs(x - centerOf(i))
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }
}

internal fun computeTabMetrics(
    totalWidth: Float,
    tabCount: Int,
    horizontalPadding: Float,
    centerGap: Float
): TabBarMetrics? {
    if (totalWidth <= 0f || tabCount <= 0) return null
    val usable = totalWidth - horizontalPadding * 2f - if (tabCount > 2) centerGap else 0f
    if (usable <= 0f) return null
    return TabBarMetrics(
        totalWidth = totalWidth,
        horizontalPadding = horizontalPadding,
        centerGap = if (tabCount > 2) centerGap else 0f,
        tabWidth = usable / tabCount,
        tabCount = tabCount
    )
}

/**
 * 底栏着色层：低透明度，让下面的真实模糊采样透出来。
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
