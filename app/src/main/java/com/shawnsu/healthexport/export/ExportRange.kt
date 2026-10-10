package com.shawnsu.healthexport.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 导出时间范围：编码为字符串随预约一并保存，缺席或无法识别时回退为全部历史（startTime=0）。
 * 编码形式：
 * - `all` 或空：全部历史；
 * - 纯数字 N：最近 N 天（含当前时刻）；
 * - `custom:startMs:endMs`：指定起止（endMs 超过当前时刻时收敛到当前时刻）。
 * 纯逻辑实现，无 Android 依赖，便于单元测试；文案为 Markdown 头部使用（正文本身即中文）。
 */
internal object ExportRange {
    const val ALL = "all"
    const val MAX_DAYS = 36500
    const val DAY_MS = 86_400_000L
    private const val CUSTOM_PREFIX = "custom:"
    private val DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 可选预设：全部历史、最近 N 天。顺序即界面顺序。 */
    val presets: List<String> = listOf(ALL, "7", "30", "90", "365")

    fun encodeDays(days: Int): String = days.coerceIn(1, MAX_DAYS).toString()

    fun encodeCustom(startMs: Long, endMs: Long): String = "$CUSTOM_PREFIX$startMs:$endMs"

    /** 解析为 `startMs to endMs`；起点 0 表示全部历史。 */
    fun resolve(encoded: String?, now: Long): Pair<Long, Long> {
        val value = encoded?.trim().orEmpty()
        if (value.isEmpty() || value == ALL) return 0L to now
        if (value.startsWith(CUSTOM_PREFIX)) {
            val parts = value.removePrefix(CUSTOM_PREFIX).split(':')
            val start = parts.getOrNull(0)?.toLongOrNull()
            val end = parts.getOrNull(1)?.toLongOrNull()
            if (parts.size == 2 && start != null && end != null && start >= 0 && start < end) {
                return start to minOf(end, now)
            }
            return 0L to now
        }
        val days = value.toLongOrNull()
        if (days != null && days in 1..MAX_DAYS.toLong()) return (now - days * DAY_MS).coerceAtLeast(0L) to now
        return 0L to now
    }

    /** 是否为可识别的编码（含全部历史）；用于界面回显与校验。 */
    fun isValid(encoded: String?): Boolean {
        val value = encoded?.trim().orEmpty()
        if (value.isEmpty() || value == ALL) return true
        if (value.startsWith(CUSTOM_PREFIX)) {
            val parts = value.removePrefix(CUSTOM_PREFIX).split(':')
            val start = parts.getOrNull(0)?.toLongOrNull()
            val end = parts.getOrNull(1)?.toLongOrNull()
            return parts.size == 2 && start != null && end != null && start >= 0 && start < end
        }
        val days = value.toLongOrNull()
        return days != null && days in 1..MAX_DAYS.toLong()
    }

    /** 是否为全部历史（省略键与显式 `all` 等价）。 */
    fun isAll(encoded: String?): Boolean {
        val value = encoded?.trim().orEmpty()
        return value.isEmpty() || value == ALL
    }

    /** Markdown 头部文案。 */
    fun label(encoded: String?, now: Long): String {
        val value = encoded?.trim().orEmpty()
        // 无法识别的编码与实际读取行为一致地回退为全部历史，避免显示出误导性的日期区间。
        if (isAll(value) || !isValid(value)) return "全部历史（startTime=0）"
        val (start, end) = resolve(value, now)
        val zone = ZoneId.systemDefault()
        val startText = DAY_FORMAT.format(Instant.ofEpochMilli(start).atZone(zone))
        val endText = DAY_FORMAT.format(Instant.ofEpochMilli(end).atZone(zone))
        val days = value.toLongOrNull()
        val prefix = if (days != null && !value.startsWith(CUSTOM_PREFIX)) "最近 $days 天" else "自定义"
        return "$prefix（$startText ~ $endText）"
    }
}
