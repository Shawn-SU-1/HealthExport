package com.shawnsu.healthexport.app

import androidx.annotation.StringRes
import com.shawnsu.healthexport.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 解析资源字符串的抽象：界面传 `stringResource` 结果，测试传真实现。
 * 借此让报告生成器（[buildAnalysisMarkdown]）保持纯函数，可在 JVM 单测中直接验证。
 */
internal fun interface TextLookup {
    fun text(@StringRes id: Int, vararg args: Any): String
}

internal val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 千分位整数，与导出正文的写法保持一致。 */
internal fun count(value: Long): String = String.format(Locale.US, "%,d", value)

/** 一位小数；所有比率与均值统一口径，避免同一数值在界面与报告里位数不同。 */
internal fun decimal(value: Double): String = String.format(Locale.US, "%.1f", value)

/** 分桶区间标签：桶宽固定为 10，标出区间上下界。 */
internal fun bucketLabel(from: Int): String = "$from-${from + 9}"

internal fun stamp(value: Long): String =
    STAMP_FORMAT.format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))

/** 运动类型名称；未收录的类型返回 null，界面与报告回退到「运动类型 %d」。 */
@StringRes
internal fun sportNameRes(id: Int): Int? = when (id) {
    128 -> R.string.analysis_sport_128
    129 -> R.string.analysis_sport_129
    130 -> R.string.analysis_sport_130
    133 -> R.string.analysis_sport_133
    137 -> R.string.analysis_sport_137
    138 -> R.string.analysis_sport_138
    139 -> R.string.analysis_sport_139
    257 -> R.string.analysis_sport_257
    258 -> R.string.analysis_sport_258
    259 -> R.string.analysis_sport_259
    260 -> R.string.analysis_sport_260
    261 -> R.string.analysis_sport_261
    262 -> R.string.analysis_sport_262
    263 -> R.string.analysis_sport_263
    264 -> R.string.analysis_sport_264
    265 -> R.string.analysis_sport_265
    266 -> R.string.analysis_sport_266
    267 -> R.string.analysis_sport_267
    270 -> R.string.analysis_sport_270
    271 -> R.string.analysis_sport_271
    273 -> R.string.analysis_sport_273
    274 -> R.string.analysis_sport_274
    275 -> R.string.analysis_sport_275
    278 -> R.string.analysis_sport_278
    279 -> R.string.analysis_sport_279
    280 -> R.string.analysis_sport_280
    281 -> R.string.analysis_sport_281
    283 -> R.string.analysis_sport_283
    else -> null
}

/** 血氧类型名称；未收录的类型返回 null，界面与报告回退到「血氧类型 %d」。 */
@StringRes
internal fun oxygenNameRes(type: Int): Int? = when (type) {
    2103 -> R.string.analysis_oxygen_2103
    2107 -> R.string.analysis_oxygen_2107
    else -> null
}
