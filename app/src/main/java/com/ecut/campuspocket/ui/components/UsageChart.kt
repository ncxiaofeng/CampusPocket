package com.ecut.campuspocket.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecut.campuspocket.ui.theme.ChartGrid
import kotlin.math.ceil

data class UsageBar(
    val label: String,
    val lightingKwh: Float,
    val acKwh: Float
)

/**
 * 堆叠柱状图：底部照明、顶部空调。
 */
@Composable
fun StackedUsageChart(
    bars: List<UsageBar>,
    lightingColor: Color,
    acColor: Color,
    modifier: Modifier = Modifier,
    labelEvery: Int = 1
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            if (bars.isEmpty()) return@Canvas
            val maxTotal = bars.maxOf { it.lightingKwh + it.acKwh }.coerceAtLeast(0.1f)
            val slot = size.width / bars.size
            val barWidth = (slot * 0.52f).coerceAtMost(22.dp.toPx())
            val corner = CornerRadius(barWidth * 0.28f, barWidth * 0.28f)

            // 网格线
            for (step in 0..3) {
                val y = size.height * step / 3f
                drawLine(
                    color = ChartGrid.copy(alpha = 0.7f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            bars.forEachIndexed { index, bar ->
                val total = bar.lightingKwh + bar.acKwh
                if (total <= 0f) return@forEachIndexed
                val left = slot * index + (slot - barWidth) / 2f
                val barHeight = size.height * (total / maxTotal)
                val lightingHeight = barHeight * (bar.lightingKwh / total)
                val acHeight = barHeight - lightingHeight

                if (acHeight > 0f) {
                    drawRoundRect(
                        color = acColor,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, acHeight),
                        cornerRadius = corner
                    )
                }
                if (lightingHeight > 0f) {
                    drawRoundRect(
                        color = lightingColor,
                        topLeft = Offset(left, size.height - lightingHeight),
                        size = Size(barWidth, lightingHeight),
                        cornerRadius = corner
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // 30 天视图每根柱子只有十几 dp，按柱分格会把 “9/25” 裁成 “9/”；
        // 这里只为实际显示标签的柱子均分宽度。
        val labelIndices = bars.indices.filter { index ->
            labelEvery <= 1 || index % labelEvery == 0 || index == bars.lastIndex
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labelIndices.forEach { index ->
                Text(
                    text = bars[index].label,
                    textAlign = TextAlign.Center,
                    fontSize = 8.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun labelStepFor(count: Int): Int = if (count <= 8) 1 else ceil(count / 7.0).toInt()
