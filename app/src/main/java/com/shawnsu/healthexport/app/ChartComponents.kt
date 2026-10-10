package com.shawnsu.healthexport.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text

/** 图表统一尺寸：读数行、绘图区、轴标签三层构成固定的视觉节拍。 */
private val PLOT_HEIGHT = 132.dp
private val PLOT_HEIGHT_TALL = 156.dp
private val AXIS_WIDTH = 36.dp
private val AXIS_PAD = 8.dp
private const val AXIS_MAX_LABELS = 6
private const val TICK_FONT = 9

/**
 * 暗色数据面板：所有图表都嵌在这一层里，形成「深色数据窗口」的统一语言。
 * 面板底色固定，不随应用明暗主题变化，保证数据色的语义稳定。
 */
@Composable
internal fun DataPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ChartPalette.PanelTop, ChartPalette.PanelBottom)))
            .border(1.dp, ChartPalette.PanelBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 面板顶部的标题行：左侧说明、右侧口径或读数。 */
@Composable
internal fun PanelHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = ChartPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Text(
                trailing,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ChartPalette.Text,
                maxLines = 1,
            )
        }
    }
}

/** 图例：数据序列多于一条时必须给出颜色对应关系。 */
@Composable
internal fun ChartLegend(entries: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for ((color, label) in entries) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(label, fontSize = 10.sp, color = ChartPalette.TextMuted, maxLines = 1)
            }
        }
    }
}

/** Y 轴刻度列，与 Canvas 的取值上下界逐条对齐。 */
@Composable
private fun ValueAxis(ticks: List<Float>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(AXIS_WIDTH)
            .fillMaxHeight()
            .padding(vertical = AXIS_PAD),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        ticks.asReversed().forEach { tick ->
            Text(
                ChartMath.compact(tick),
                fontSize = TICK_FONT.sp,
                color = ChartPalette.Tick,
                maxLines = 1,
            )
        }
    }
}

/** X 轴标签行，按需抽样，首尾必取。 */
@Composable
private fun LabelAxis(labels: List<String>, modifier: Modifier = Modifier) {
    val indexes = ChartMath.sampleIndexes(labels.size, AXIS_MAX_LABELS)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AXIS_WIDTH + 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (index in indexes) {
            Text(
                labels[index],
                fontSize = TICK_FONT.sp,
                color = ChartPalette.Tick,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 绘图区上下留白，使刻度线不与面板边缘相贴。 */
private fun DrawScope.plotTop() = AXIS_PAD.toPx()

private fun DrawScope.plotBottom() = size.height - AXIS_PAD.toPx()

private fun DrawScope.plotSpan() = plotBottom() - plotTop()

/** 画一条水平网格线，并回传该刻度对应的 y。 */
private fun DrawScope.gridLine(value: Float, lower: Float, upper: Float, strong: Boolean) {
    if (upper <= lower) return
    val ratio = (value - lower) / (upper - lower)
    val y = plotBottom() - ratio * plotSpan()
    drawLine(
        color = if (strong) ChartPalette.GridStrong else ChartPalette.Grid,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1f,
    )
}

private fun DrawScope.dashedLine(y: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
    )
}

/** 读数行：标题 + 数值，交互或自动高亮时给出对应标签。 */
@Composable
private fun Readout(label: String, value: String, hint: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = ChartPalette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ChartPalette.Text)
            if (hint != null) {
                Text(hint, fontSize = 10.sp, color = ChartPalette.Tick, maxLines = 1)
            }
        }
    }
}

/**
 * 折线趋势图：网格 + 刻度 + 均值参考线 + 峰值标记 + 渐变面积。
 * 点击任意位置可锁定该点读数，再点一次取消。
 */
@Composable
internal fun TrendChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
    unit: String = "",
    color: Color = ChartPalette.Primary,
    zeroBased: Boolean = true,
    format: (Float) -> String = { ChartMath.compact(it) },
) {
    if (values.size < 2) return
    val low = if (zeroBased) minOf(0f, values.min()) else values.min()
    val ticks = ChartMath.niceTicks(low, values.max(), 4)
    if (ticks.size < 2) return
    val lower = ticks.first()
    val upper = ticks.last()
    val mean = values.average().toFloat()
    val peakIndex = values.indices.maxByOrNull { values[it] } ?: 0
    var selected by remember(values) { mutableIntStateOf(-1) }
    val active = if (selected in values.indices) selected else -1

    DataPanel(modifier) {
        if (title != null) PanelHeader(title, ChartMath.compact(values[peakIndex]) + unit)
        Readout(
            label = if (active >= 0) labels.getOrElse(active) { "" } else labels.lastOrNull().orEmpty(),
            value = format(values[if (active >= 0) active else peakIndex]) + unit,
            hint = if (active >= 0) null else "MAX",
        )
        Row(modifier = Modifier.fillMaxWidth().height(PLOT_HEIGHT)) {
            ValueAxis(ticks)
            Spacer(Modifier.width(6.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(values) {
                        detectTapGestures { offset ->
                            val step = if (values.size > 1) size.width.toFloat() / (values.size - 1) else 1f
                            val index = (offset.x / step).toInt().coerceIn(0, values.lastIndex)
                            selected = if (selected == index) -1 else index
                        }
                    },
            ) {
                val span = plotSpan()
                ticks.forEach { gridLine(it, lower, upper, it == lower || it == upper) }
                val stepX = size.width / (values.size - 1)
                fun pointX(index: Int) = stepX * index
                fun pointY(value: Float) = plotBottom() - ((value - lower) / (upper - lower)) * span

                if (mean in lower..upper) dashedLine(pointY(mean), ChartPalette.GridStrong)

                val area = Path().apply {
                    values.forEachIndexed { index, value ->
                        if (index == 0) moveTo(pointX(index), pointY(value)) else lineTo(pointX(index), pointY(value))
                    }
                    lineTo(size.width, plotBottom())
                    lineTo(0f, plotBottom())
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.02f))))

                val line = Path().apply {
                    values.forEachIndexed { index, value ->
                        if (index == 0) moveTo(pointX(index), pointY(value)) else lineTo(pointX(index), pointY(value))
                    }
                }
                drawPath(line, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))

                val markerIndex = if (active >= 0) active else peakIndex
                drawLine(
                    color = color.copy(alpha = 0.45f),
                    start = Offset(pointX(markerIndex), plotTop()),
                    end = Offset(pointX(markerIndex), plotBottom()),
                    strokeWidth = 1f,
                )
                drawCircle(color.copy(alpha = 0.22f), 5.dp.toPx(), Offset(pointX(markerIndex), pointY(values[markerIndex])))
                drawCircle(color, 2.6.dp.toPx(), Offset(pointX(markerIndex), pointY(values[markerIndex])))
            }
        }
        LabelAxis(labels)
    }
}

/**
 * 柱状图：网格 + 刻度 + 峰值柱高亮。点击某一根柱可锁定其读数。
 */
@Composable
internal fun ColumnChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
    unit: String = "",
    color: Color = ChartPalette.Primary,
    format: (Float) -> String = { ChartMath.compact(it) },
) {
    if (values.isEmpty()) return
    val ticks = ChartMath.niceTicks(0f, values.max(), 4)
    if (ticks.size < 2) return
    val lower = ticks.first()
    val upper = ticks.last()
    val peakIndex = values.indices.maxByOrNull { values[it] } ?: 0
    var selected by remember(values) { mutableIntStateOf(-1) }
    val active = if (selected in values.indices) selected else -1

    DataPanel(modifier) {
        if (title != null) PanelHeader(title, ChartMath.compact(values.sum()) + unit)
        Readout(
            label = if (active >= 0) labels.getOrElse(active) { "" } else labels.getOrElse(peakIndex) { "" },
            value = format(values[if (active >= 0) active else peakIndex]) + unit,
            hint = if (active >= 0) null else "MAX",
        )
        Row(modifier = Modifier.fillMaxWidth().height(PLOT_HEIGHT)) {
            ValueAxis(ticks)
            Spacer(Modifier.width(6.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(values) {
                        detectTapGestures { offset ->
                            val slot = size.width.toFloat() / values.size
                            val index = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                            selected = if (selected == index) -1 else index
                        }
                    },
            ) {
                val span = plotSpan()
                ticks.forEach { gridLine(it, lower, upper, it == lower || it == upper) }
                val slot = size.width / values.size
                val barWidth = (slot * 0.58f).coerceAtLeast(1.dp.toPx())
                values.forEachIndexed { index, value ->
                    if (value <= lower) return@forEachIndexed
                    val barHeight = ((value - lower) / (upper - lower)) * span
                    val highlighted = index == peakIndex || index == active
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                (if (highlighted) color else color.copy(alpha = 0.55f)),
                                (if (highlighted) color.copy(alpha = 0.75f) else color.copy(alpha = 0.22f)),
                            ),
                            startY = plotBottom() - barHeight,
                            endY = plotBottom(),
                        ),
                        topLeft = Offset(slot * index + (slot - barWidth) / 2f, plotBottom() - barHeight),
                        size = Size(barWidth, barHeight),
                    )
                }
                drawLine(ChartPalette.Axis, Offset(0f, plotBottom()), Offset(size.width, plotBottom()), strokeWidth = 1f)
            }
        }
        LabelAxis(labels)
    }
}

/** 堆叠柱的一个分段。 */
internal data class StackSegment(val label: String, val color: Color, val values: List<Float>)

/**
 * 堆叠柱状图：用于睡眠分期这类「总量 + 构成」双信息场景。
 */
@Composable
internal fun StackedColumnChart(
    segments: List<StackSegment>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
    unit: String = "",
) {
    if (segments.isEmpty() || labels.isEmpty()) return
    val totals = labels.indices.map { index -> segments.sumOf { it.values.getOrElse(index) { 0f }.toDouble() }.toFloat() }
    if (totals.all { it <= 0f }) return
    val ticks = ChartMath.niceTicks(0f, totals.max(), 4)
    if (ticks.size < 2) return
    val lower = ticks.first()
    val upper = ticks.last()

    DataPanel(modifier) {
        if (title != null) PanelHeader(title, ChartMath.compact(totals.average().toFloat()) + unit)
        ChartLegend(segments.map { it.color to it.label })
        Row(modifier = Modifier.fillMaxWidth().height(PLOT_HEIGHT_TALL)) {
            ValueAxis(ticks)
            Spacer(Modifier.width(6.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val span = plotSpan()
                ticks.forEach { gridLine(it, lower, upper, it == lower || it == upper) }
                val slot = size.width / labels.size
                val barWidth = (slot * 0.6f).coerceAtLeast(1.dp.toPx())
                labels.indices.forEach { index ->
                    var stackTop = plotBottom()
                    for (segment in segments) {
                        val value = segment.values.getOrElse(index) { 0f }
                        if (value <= 0f) continue
                        val barHeight = (value / (upper - lower)) * span
                        drawRect(
                            color = segment.color,
                            topLeft = Offset(slot * index + (slot - barWidth) / 2f, stackTop - barHeight),
                            size = Size(barWidth, barHeight),
                        )
                        stackTop -= barHeight
                    }
                }
                drawLine(ChartPalette.Axis, Offset(0f, plotBottom()), Offset(size.width, plotBottom()), strokeWidth = 1f)
            }
        }
        LabelAxis(labels)
    }
}

/** 分桶统计的一项：区间起点、区间宽度与计数。 */
internal data class BucketBar(val label: String, val count: Float)

/**
 * 分布直方图：用于心率、压力这类分桶数据；峰值桶高亮，区间标签置于柱下。
 */
@Composable
internal fun BucketChart(
    buckets: List<BucketBar>,
    modifier: Modifier = Modifier,
    title: String? = null,
    unit: String = "",
    color: Color = ChartPalette.Primary,
) {
    if (buckets.size < 2) return
    val ticks = ChartMath.niceTicks(0f, buckets.maxOf { it.count }, 4)
    if (ticks.size < 2) return
    val lower = ticks.first()
    val upper = ticks.last()
    val peakIndex = buckets.indices.maxByOrNull { buckets[it].count } ?: 0

    DataPanel(modifier) {
        if (title != null) {
            PanelHeader(title, ChartMath.compact(buckets.sumOf { it.count.toDouble() }.toFloat()) + unit)
        }
        Readout(
            label = buckets[peakIndex].label,
            value = ChartMath.compact(buckets[peakIndex].count) + unit,
            hint = "MAX",
        )
        Row(modifier = Modifier.fillMaxWidth().height(PLOT_HEIGHT)) {
            ValueAxis(ticks)
            Spacer(Modifier.width(6.dp))
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val span = plotSpan()
                ticks.forEach { gridLine(it, lower, upper, it == lower || it == upper) }
                val slot = size.width / buckets.size
                buckets.forEachIndexed { index, bucket ->
                    if (bucket.count <= lower) return@forEachIndexed
                    val barHeight = ((bucket.count - lower) / (upper - lower)) * span
                    val highlighted = index == peakIndex
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                if (highlighted) color else color.copy(alpha = 0.5f),
                                color.copy(alpha = 0.18f),
                            ),
                            startY = plotBottom() - barHeight,
                            endY = plotBottom(),
                        ),
                        topLeft = Offset(slot * index, plotBottom() - barHeight),
                        size = Size(slot, barHeight),
                    )
                }
                drawLine(ChartPalette.Axis, Offset(0f, plotBottom()), Offset(size.width, plotBottom()), strokeWidth = 1f)
            }
        }
        LabelAxis(buckets.map { it.label })
    }
}

/**
 * 范围条：min–avg–max 三段式，用于血氧这类「看波动幅度」的指标。
 */
@Composable
internal fun RangeBar(
    label: String,
    min: Int,
    avg: Double,
    max: Int,
    modifier: Modifier = Modifier,
    scaleLow: Float,
    scaleHigh: Float,
    unit: String = "",
    color: Color = ChartPalette.Secondary,
) {
    val low = scaleLow
    val high = if (scaleHigh <= scaleLow) scaleLow + 1f else scaleHigh
    fun fraction(value: Float) = ((value - low) / (high - low)).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                label,
                fontSize = 11.sp,
                color = ChartPalette.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                "AVG " + ChartMath.compact(avg.toFloat()) + unit + "  ·  " + min + "–" + max + unit,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ChartPalette.Text,
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            val radius = size.height / 2f
            drawRoundRect(color = Color(0x14FFFFFF), cornerRadius = CornerRadius(radius, radius))
            val startX = size.width * fraction(min.toFloat())
            val endX = size.width * fraction(max.toFloat())
            drawRoundRect(
                color = color.copy(alpha = 0.38f),
                topLeft = Offset(startX, 0f),
                size = Size((endX - startX).coerceAtLeast(1.dp.toPx()), size.height),
                cornerRadius = CornerRadius(radius, radius),
            )
            val avgX = size.width * fraction(avg.toFloat())
            drawLine(
                color = color,
                start = Offset(avgX, 0f),
                end = Offset(avgX, size.height),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * 排行条：名次 + 名称 + 占比条 + 数值，用于运动类型对比。
 */
@Composable
internal fun RankedBar(
    rank: Int,
    label: String,
    valueText: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = ChartPalette.Primary,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rank.toString().padStart(2, '0'),
                fontSize = 10.sp,
                color = ChartPalette.Tick,
            )
            Text(
                label,
                fontSize = 11.sp,
                color = ChartPalette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Text(valueText, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ChartPalette.TextMuted)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x14FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.55f)))),
            )
        }
    }
}

/** 构成条的一个分段。 */
internal data class CompositionPart(val label: String, val minutes: Long, val color: Color)

/**
 * 睡眠分期构成条：按比例铺满整行，并在下方给出各自的时长与占比。
 */
@Composable
internal fun CompositionBar(parts: List<CompositionPart>, modifier: Modifier = Modifier) {
    val total = parts.sumOf { it.minutes }.toFloat()
    if (total <= 0f) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            parts.forEach { part ->
                if (part.minutes > 0L) {
                    Box(
                        modifier = Modifier
                            .weight(part.minutes.toFloat())
                            .fillMaxHeight()
                            .background(part.color),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            parts.forEach { part ->
                Column(horizontalAlignment = Alignment.Start) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(part.color),
                        )
                        Text(part.label, fontSize = 10.sp, color = ChartPalette.TextMuted, maxLines = 1)
                    }
                    Text(
                        "${Math.round(part.minutes / 60.0 * 10) / 10.0}h",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ChartPalette.Text,
                    )
                    Text(
                        "${Math.round(part.minutes / total * 1000) / 10.0}%",
                        fontSize = 10.sp,
                        color = ChartPalette.Tick,
                    )
                }
            }
        }
    }
}
