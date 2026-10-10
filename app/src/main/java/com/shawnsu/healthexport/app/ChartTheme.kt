package com.shawnsu.healthexport.app

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 图表的暗色数据面板配色。
 * 数据色显式固定、不跟随应用主题：同一序列在明暗主题下必须保持同一语义，
 * 否则同一个数值在两套主题里会被读成不同含义。
 */
internal object ChartPalette {
    /** 面板底色（自上而下略微提亮，避免大面积死黑）。 */
    val PanelTop = Color(0xFF111823)
    val PanelBottom = Color(0xFF0B1016)
    val PanelBorder = Color(0x1FFFFFFF)

    /** 网格、坐标轴与刻度文字。 */
    val Grid = Color(0x14FFFFFF)
    val GridStrong = Color(0x26FFFFFF)
    val Axis = Color(0x38FFFFFF)
    val Tick = Color(0xFF6E7E90)
    val Text = Color(0xFFE6EDF5)
    val TextMuted = Color(0xFF8FA0B3)

    /** 数据序列语义色。 */
    val Primary = Color(0xFF38BDF8)
    val Secondary = Color(0xFF34D399)
    val Warm = Color(0xFFF0A94A)
    val Violet = Color(0xFFA78BFA)
    val Danger = Color(0xFFF87171)

    /** 睡眠分期四档（暗底提亮，保证相邻分期可区分）。 */
    val StageDeep = Color(0xFF5B7CFA)
    val StageLight = Color(0xFF9DB4FF)
    val StageRem = Color(0xFFF0A94A)
    val StageAwake = Color(0xFFA8B2C1)
}

/** 图表的纯计算部分，与 Compose 无关，便于单元测试。 */
internal object ChartMath {

    /**
     * 把 [min]–[max] 扩张为一组「整齐」刻度（步长为 1/2/2.5/5 × 10^n），
     * 返回值同时用于绘制网格线与 Y 轴标签，首尾即图表的取值上下界。
     */
    fun niceTicks(min: Float, max: Float, count: Int = 4): List<Float> {
        if (!min.isFinite() || !max.isFinite()) return emptyList()
        if (max <= min) {
            val pad = if (abs(max) < 1f) 1f else abs(max) * 0.1f
            return niceTicks(max - pad, max + pad, count)
        }
        val rawStep = (max - min) / count.coerceAtLeast(1)
        val magnitude = 10f.pow(floor(log10(rawStep.toDouble())).toFloat())
        val normalized = rawStep / magnitude
        val step = when {
            normalized <= 1f -> 1f
            normalized <= 2f -> 2f
            normalized <= 2.5f -> 2.5f
            normalized <= 5f -> 5f
            else -> 10f
        } * magnitude
        val start = floor(min / step) * step
        val end = ceil(max / step) * step
        val ticks = ArrayList<Float>()
        var value = start
        var guard = 0
        while (value <= end + step * 1e-4f && guard < 64) {
            ticks += value
            value += step
            guard++
        }
        return ticks
    }

    /** 轴标签用的紧凑数值：1200 → 1.2k，3400000 → 3.4M。 */
    fun compact(value: Float): String {
        val size = abs(value)
        return when {
            size >= 1_000_000f -> trim(value / 1_000_000f) + "M"
            size >= 1_000f -> trim(value / 1_000f) + "k"
            size >= 100f -> String.format(Locale.US, "%.0f", value)
            size >= 10f -> String.format(Locale.US, "%.1f", value)
            else -> String.format(Locale.US, "%.2f", value)
        }
    }

    private fun trim(value: Float): String {
        val text = String.format(Locale.US, "%.1f", value)
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }

    /**
     * 从 [size] 个数据点中均匀抽取至多 [max] 个下标，首尾必取，
     * 用于避免长区间下的 X 轴标签互相重叠。
     */
    fun sampleIndexes(size: Int, max: Int): List<Int> {
        if (size <= 0 || max <= 0) return emptyList()
        if (size <= max) return (0 until size).toList()
        if (max == 1) return listOf(0)
        val step = (size - 1).toFloat() / (max - 1)
        return (0 until max).map { (it * step).toInt().coerceIn(0, size - 1) }.distinct()
    }
}
