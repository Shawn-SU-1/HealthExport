package com.shawnsu.healthexport.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 图表统一高度与最多显示的轴标签数量。 */
private val CHART_HEIGHT = 132.dp
private const val MAX_LABELS = 5

/** 从标签序列中均匀抽取最多 [MAX_LABELS] 个，避免长区间标签重叠。 */
private fun sampleLabels(labels: List<String>): List<String> {
    if (labels.size <= MAX_LABELS) return labels
    val step = (labels.size - 1).toFloat() / (MAX_LABELS - 1)
    return (0 until MAX_LABELS).map { labels[(it * step).toInt().coerceIn(0, labels.lastIndex)] }
}

@Composable
private fun ChartLabels(labels: List<String>, modifier: Modifier = Modifier) {
    val colors = BasicComponentDefaults.summaryColor()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (label in sampleLabels(labels)) {
            Text(
                label,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = colors.color,
            )
        }
    }
}

/**
 * 折线图（含渐变填充）；数据不足两个点时不出图。
 * [zeroBased] 为 false 时按序列上下界映射，用于体重这类绝对值序列，避免波动被压平。
 */
@Composable
internal fun LineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
    zeroBased: Boolean = true,
) {
    if (values.size < 2) return
    val gridColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            val maxValue = values.maxOrNull() ?: 0f
            val minValue = if (zeroBased) 0f else values.minOrNull() ?: 0f
            if (maxValue <= minValue) return@Canvas
            val stepX = size.width / (values.size - 1)
            val padding = 6.dp.toPx()
            val usable = size.height - padding
            fun x(index: Int) = stepX * index
            fun y(value: Float) = padding + usable * (1f - (value - minValue) / (maxValue - minValue))

            drawLine(gridColor, Offset(0f, padding), Offset(size.width, padding))
            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height))

            val area = Path().apply {
                values.forEachIndexed { index, value ->
                    if (index == 0) moveTo(x(index), y(value)) else lineTo(x(index), y(value))
                }
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.02f))))

            val line = Path().apply {
                values.forEachIndexed { index, value ->
                    if (index == 0) moveTo(x(index), y(value)) else lineTo(x(index), y(value))
                }
            }
            drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
            values.forEachIndexed { index, value ->
                drawCircle(color, 2.5.dp.toPx(), Offset(x(index), y(value)))
            }
        }
        ChartLabels(labels, Modifier.padding(top = 4.dp))
    }
}

/** 柱状图；单条数据仅绘制一根柱。 */
@Composable
internal fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
) {
    if (values.isEmpty()) return
    val axisColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            val maxValue = values.maxOrNull() ?: 0f
            if (maxValue <= 0f) return@Canvas
            val slot = size.width / values.size
            val barWidth = (slot * 0.62f).coerceAtLeast(1.dp.toPx())
            val minHeight = 1.dp.toPx()

            drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height))
            values.forEachIndexed { index, value ->
                if (value <= 0f) return@forEachIndexed
                val barHeight = (size.height * (value / maxValue)).coerceAtLeast(minHeight)
                drawRect(
                    color = color,
                    topLeft = Offset(slot * index + (slot - barWidth) / 2f, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
        ChartLabels(labels, Modifier.padding(top = 4.dp))
    }
}

/** 横向条形：用于类别对比（运动类型等），无需坐标轴。 */
@Composable
internal fun HorizontalBar(
    label: String,
    valueText: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.primary,
) {
    val titleColors = BasicComponentDefaults.titleColor()
    val colors = BasicComponentDefaults.summaryColor()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                color = titleColors.color,
            )
            Text(
                valueText,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                fontWeight = FontWeight.Medium,
                color = colors.color,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}
