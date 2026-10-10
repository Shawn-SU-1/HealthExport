package com.shawnsu.healthexport.app

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawnsu.healthexport.R
import com.shawnsu.healthexport.export.ExportSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 导出数据的图文分析：数据来自导出时同源生成的 JSON 摘要，不解析 Markdown 正文。
 * 所有数值均为原始单位换算而来（米 → 公里、毫秒 → 小时、分钟 → 小时）。
 */
@Composable
internal fun AnalysisScreen(summary: ExportSummary?, onClose: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    Scaffold(
        topBar = {
            TopAppBar(
                color = MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.analysis_title),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings:analysis")
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(
                start = safeInsets.calculateLeftPadding(layoutDirection),
                top = padding.calculateTopPadding() + 8.dp,
                end = safeInsets.calculateRightPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            if (summary == null) {
                item(key = "analysis_empty") {
                    SectionCard(stringResource(R.string.analysis_title), "analysis:empty") {
                        EmptyHint(stringResource(R.string.analysis_empty))
                    }
                }
            } else {
                item(key = "analysis_overview") { OverviewCard(summary) }
                item(key = "analysis_steps") { StepsCard(summary) }
                item(key = "analysis_sleep") { SleepCard(summary) }
                item(key = "analysis_heart") { HeartRateCard(summary) }
                item(key = "analysis_oxygen") { BloodOxygenCard(summary) }
                item(key = "analysis_pressure") { PressureCard(summary) }
                item(key = "analysis_workout") { WorkoutCard(summary) }
                item(key = "analysis_weight") { WeightCard(summary) }
            }
            item(key = "navigation_bar_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun OverviewCard(summary: ExportSummary) {
    SectionCard(stringResource(R.string.analysis_overview), "analysis:overview") {
        StatRow(
            stringResource(R.string.analysis_exported_at),
            STAMP_FORMAT.format(Instant.ofEpochMilli(summary.exportedAt).atZone(ZoneId.systemDefault())),
        )
        StatRow(stringResource(R.string.analysis_range), summary.rangeLabel)
        StatRow(
            stringResource(R.string.analysis_records),
            stringResource(R.string.analysis_records_value, summary.counts.values.sum()),
        )
        StatRow(
            stringResource(R.string.analysis_datasets),
            stringResource(R.string.analysis_dataset_value, summary.counts.size, summary.failed.size),
        )
    }
}

@Composable
private fun StepsCard(summary: ExportSummary) {
    val daily = summary.daily
    SectionCard(stringResource(R.string.analysis_steps), "analysis:steps") {
        if (daily.records == 0) {
            EmptyHint(stringResource(R.string.analysis_no_data))
            return@SectionCard
        }
        StatRow(stringResource(R.string.analysis_steps_total), count(daily.stepsTotal))
        StatRow(stringResource(R.string.analysis_days), stringResource(R.string.analysis_days_value, daily.days))
        StatRow(stringResource(R.string.analysis_steps_per_day), count(daily.stepsPerDay))
        StatRow(
            stringResource(R.string.analysis_distance),
            stringResource(R.string.analysis_km_value, decimal(daily.distanceM / 1000.0)),
        )
        StatRow(
            stringResource(R.string.analysis_calories),
            stringResource(R.string.analysis_kcal_value, count(daily.calories)),
        )
        if (daily.months.size >= 2) {
            ColumnChart(
                values = daily.months.map { it.steps.toFloat() },
                labels = daily.months.map { it.month },
                title = stringResource(R.string.analysis_chart_monthly_steps),
                unit = stringResource(R.string.analysis_unit_steps),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 睡眠是分析的重点：总时长 → 分期构成（深睡 / 浅睡 / 快速眼动 / 清醒）→ 睡眠质量指标 → 深睡月度趋势。
 * 账号未采集到分期时给出明确前提提示，而不是显示 0。
 */
@Composable
private fun SleepCard(summary: ExportSummary) {
    val sleep = summary.sleep
    val detail = sleep.detail
    SectionCard(stringResource(R.string.analysis_sleep), "analysis:sleep") {
        if (sleep.nights == 0) {
            EmptyHint(stringResource(R.string.analysis_no_data))
            return@SectionCard
        }
        StatRow(stringResource(R.string.analysis_sleep_nights), count(sleep.nights.toLong()))
        StatRow(
            stringResource(R.string.analysis_sleep_total),
            stringResource(R.string.analysis_hour_value, decimal(sleep.minutes / 60.0)),
        )
        StatRow(
            stringResource(R.string.analysis_sleep_average),
            stringResource(R.string.analysis_hour_value, decimal(sleep.minutesPerNight / 60.0)),
        )
        val nights = sleep.months.filter { it.nights > 0 }
        if (nights.size >= 2) {
            ColumnChart(
                values = nights.map { it.minutes.toFloat() / it.nights / 60f },
                labels = nights.map { it.month },
                title = stringResource(R.string.analysis_chart_nightly_sleep),
                unit = stringResource(R.string.analysis_unit_hours),
                modifier = Modifier.padding(top = 2.dp),
                format = { decimal(it.toDouble()) },
            )
        }
        if (!detail.hasStages) {
            EmptyHint(stringResource(R.string.analysis_sleep_no_stage))
            return@SectionCard
        }
        GroupTitle(stringResource(R.string.analysis_sleep_stage_title))
        CompositionBar(
            listOf(
                CompositionPart(
                    stringResource(R.string.analysis_sleep_deep),
                    detail.deepMinutes,
                    ChartPalette.StageDeep,
                ),
                CompositionPart(
                    stringResource(R.string.analysis_sleep_light),
                    detail.lightMinutes,
                    ChartPalette.StageLight,
                ),
                CompositionPart(
                    stringResource(R.string.analysis_sleep_rem),
                    detail.remMinutes,
                    ChartPalette.StageRem,
                ),
                CompositionPart(
                    stringResource(R.string.analysis_sleep_awake),
                    detail.awakeMinutes,
                    ChartPalette.StageAwake,
                ),
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
        StageRow(R.string.analysis_sleep_deep, detail.deepMinutes, detail, ChartPalette.StageDeep)
        StageRow(R.string.analysis_sleep_light, detail.lightMinutes, detail, ChartPalette.StageLight)
        StageRow(R.string.analysis_sleep_rem, detail.remMinutes, detail, ChartPalette.StageRem)
        StageRow(R.string.analysis_sleep_awake, detail.awakeMinutes, detail, ChartPalette.StageAwake)
        StatRow(
            stringResource(R.string.analysis_sleep_staged_nights),
            stringResource(R.string.analysis_days_value, detail.stagedNights),
        )
        StatRow(
            stringResource(R.string.analysis_sleep_deep_average),
            stringResource(R.string.analysis_hour_value, decimal(detail.deepPerNight / 60.0)),
        )
        StatRow(
            stringResource(R.string.analysis_sleep_deep_ratio),
            stringResource(R.string.analysis_percent_value, decimal(detail.deepPercent)),
        )
        detail.scoreAvg?.let {
            StatRow(
                stringResource(R.string.analysis_sleep_score),
                stringResource(R.string.analysis_sleep_score_value, decimal(it)),
            )
        }
        detail.latencyAvg?.let {
            StatRow(
                stringResource(R.string.analysis_sleep_latency),
                stringResource(R.string.analysis_sleep_latency_value, decimal(it)),
            )
        }
        detail.wakePerNight?.let {
            StatRow(
                stringResource(R.string.analysis_sleep_wake_count),
                stringResource(R.string.analysis_sleep_wake_count_value, decimal(it)),
            )
        }
        if (detail.napMinutes > 0L) {
            StatRow(
                stringResource(R.string.analysis_sleep_nap),
                stringResource(R.string.analysis_hour_value, decimal(detail.napMinutes / 60.0)),
            )
        }
        detail.lastFallAsleep?.let { StatRow(stringResource(R.string.analysis_sleep_last_fall), stamp(it)) }
        detail.lastWakeUp?.let { StatRow(stringResource(R.string.analysis_sleep_last_wake), stamp(it)) }
        detail.lastBedTime?.let { StatRow(stringResource(R.string.analysis_sleep_last_bed), stamp(it)) }
        detail.lastWakeTime?.let { StatRow(stringResource(R.string.analysis_sleep_last_off_bed), stamp(it)) }
        val stageMonths = detail.months.filter { it.deepMinutes + it.lightMinutes + it.remMinutes > 0L }
        if (stageMonths.size >= 2) {
            GroupTitle(stringResource(R.string.analysis_sleep_stage_trend))
            StackedColumnChart(
                segments = listOf(
                    StackSegment(
                        stringResource(R.string.analysis_sleep_deep),
                        ChartPalette.StageDeep,
                        stageMonths.map { it.deepMinutes.toFloat() / 60f },
                    ),
                    StackSegment(
                        stringResource(R.string.analysis_sleep_light),
                        ChartPalette.StageLight,
                        stageMonths.map { it.lightMinutes.toFloat() / 60f },
                    ),
                    StackSegment(
                        stringResource(R.string.analysis_sleep_rem),
                        ChartPalette.StageRem,
                        stageMonths.map { it.remMinutes.toFloat() / 60f },
                    ),
                ),
                labels = stageMonths.map { it.month },
                title = stringResource(R.string.analysis_chart_stage_stack),
                unit = stringResource(R.string.analysis_unit_hours),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun StageRow(
    @StringRes labelRes: Int,
    minutes: Long,
    detail: ExportSummary.SleepDetail,
    color: Color,
) {
    val titleColors = BasicComponentDefaults.titleColor()
    val summaryColors = BasicComponentDefaults.summaryColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                stringResource(labelRes),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = summaryColors.color,
            )
        }
        Text(
            stringResource(
                R.string.analysis_sleep_stage_value,
                decimal(minutes / 60.0),
                decimal(detail.percentOf(minutes)),
            ),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            fontWeight = FontWeight.Medium,
            color = titleColors.color,
        )
    }
}

@Composable
private fun HeartRateCard(summary: ExportSummary) {
    val heart = summary.heartRate
    SectionCard(stringResource(R.string.analysis_heart_rate), "analysis:heart") {
        if (heart.records == 0) {
            EmptyHint(stringResource(R.string.analysis_no_data))
            return@SectionCard
        }
        DistributionStats(heart)
        if (heart.buckets.size >= 2) {
            BucketChart(
                buckets = heart.buckets.map { BucketBar(bucketLabel(it.from), it.count.toFloat()) },
                title = stringResource(R.string.analysis_chart_hr_distribution),
                unit = stringResource(R.string.analysis_unit_records),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun BloodOxygenCard(summary: ExportSummary) {
    val oxygen = summary.bloodOxygen
    SectionCard(stringResource(R.string.analysis_blood_oxygen), "analysis:oxygen") {
        if (oxygen.records == 0) {
            EmptyHint(stringResource(R.string.analysis_no_data))
            return@SectionCard
        }
        val oxygenLow = (oxygen.series.minOfOrNull { it.min } ?: 90) - 2
        val oxygenHigh = (oxygen.series.maxOfOrNull { it.max } ?: 100) + 2
        for (series in oxygen.series) {
            val nameRes = oxygenNameRes(series.type)
            GroupTitle(
                if (nameRes != null) {
                    stringResource(nameRes)
                } else {
                    stringResource(R.string.analysis_oxygen_series, series.type)
                },
            )
            StatRow(stringResource(R.string.analysis_records), count(series.records.toLong()))
            RangeBar(
                label = stringResource(R.string.analysis_oxygen_range),
                min = series.min,
                avg = series.avg,
                max = series.max,
                scaleLow = oxygenLow.toFloat(),
                scaleHigh = oxygenHigh.toFloat(),
                unit = stringResource(R.string.analysis_unit_percent),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PressureCard(summary: ExportSummary) {
    val pressure = summary.pressure
    SectionCard(stringResource(R.string.analysis_pressure), "analysis:pressure") {
        if (pressure.records == 0) {
            EmptyHint(stringResource(R.string.analysis_no_data))
            return@SectionCard
        }
        DistributionStats(pressure)
        if (pressure.buckets.size >= 2) {
            BucketChart(
                buckets = pressure.buckets.map { BucketBar(bucketLabel(it.from), it.count.toFloat()) },
                title = stringResource(R.string.analysis_chart_pressure_distribution),
                unit = stringResource(R.string.analysis_unit_records),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun WorkoutCard(summary: ExportSummary) {
    val workout = summary.workout
    SectionCard(stringResource(R.string.analysis_workout), "analysis:workout") {
        if (workout.records == 0) {
            EmptyHint(stringResource(R.string.analysis_no_data))
            return@SectionCard
        }
        StatRow(
            stringResource(R.string.analysis_workout_count),
            stringResource(R.string.analysis_times_value, workout.records),
        )
        StatRow(
            stringResource(R.string.analysis_distance),
            stringResource(R.string.analysis_km_value, decimal(workout.distanceM / 1000.0)),
        )
        StatRow(
            stringResource(R.string.analysis_workout_duration),
            stringResource(R.string.analysis_hour_value, decimal(workout.durationMs / 3_600_000.0)),
        )
        StatRow(
            stringResource(R.string.analysis_calories),
            stringResource(R.string.analysis_kcal_value, count(workout.calories)),
        )
        val types = workout.types.sortedByDescending { it.distanceM }.ifEmpty { workout.types }
        val maxDistance = types.maxOfOrNull { it.distanceM } ?: 0L
        val maxRecords = types.maxOfOrNull { it.records } ?: 0
        types.forEachIndexed { index, type ->
            val fraction = if (maxDistance > 0L) {
                type.distanceM.toFloat() / maxDistance
            } else if (maxRecords > 0) {
                type.records.toFloat() / maxRecords
            } else {
                0f
            }
            val nameRes = sportNameRes(type.id)
            RankedBar(
                rank = index + 1,
                label = if (nameRes != null) {
                    stringResource(nameRes)
                } else {
                    stringResource(R.string.analysis_workout_type, type.id)
                },
                valueText = stringResource(
                    R.string.analysis_workout_item, type.records, decimal(type.distanceM / 1000.0),
                ),
                fraction = fraction,
                color = if (index == 0) ChartPalette.Primary else ChartPalette.Secondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun WeightCard(summary: ExportSummary) {
    val points = summary.weight
    val measured = points.filter { it.weightKg != null }
    SectionCard(stringResource(R.string.analysis_weight), "analysis:weight") {
        if (measured.isEmpty()) {
            EmptyHint(stringResource(R.string.analysis_no_data))
            return@SectionCard
        }
        val latest = measured.last()
        latest.weightKg?.let {
            StatRow(stringResource(R.string.analysis_weight_latest), stringResource(R.string.analysis_kg_value, decimal(it)))
        }
        latest.bodyFatPercent?.let {
            StatRow(stringResource(R.string.analysis_body_fat), stringResource(R.string.analysis_percent_value, decimal(it)))
        }
        latest.heightCm?.let {
            StatRow(stringResource(R.string.analysis_height), stringResource(R.string.analysis_cm_value, decimal(it)))
        }
        if (measured.size >= 2) {
            TrendChart(
                values = measured.map { it.weightKg!!.toFloat() },
                labels = measured.map { DATE_FORMAT.format(Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault())) },
                title = stringResource(R.string.analysis_chart_weight_trend),
                unit = stringResource(R.string.analysis_unit_kg),
                color = ChartPalette.Violet,
                zeroBased = false,
                modifier = Modifier.padding(top = 2.dp),
                format = { decimal(it.toDouble()) },
            )
        }
        val fat = measured.filter { it.bodyFatPercent != null }
        if (fat.size >= 2) {
            TrendChart(
                values = fat.map { it.bodyFatPercent!!.toFloat() },
                labels = fat.map { DATE_FORMAT.format(Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault())) },
                title = stringResource(R.string.analysis_chart_body_fat_trend),
                unit = stringResource(R.string.analysis_unit_percent),
                color = ChartPalette.Warm,
                zeroBased = false,
                modifier = Modifier.padding(top = 2.dp),
                format = { decimal(it.toDouble()) },
            )
        }
    }
}

@Composable
private fun DistributionStats(distribution: ExportSummary.Distribution) {
    StatRow(stringResource(R.string.analysis_records), count(distribution.records.toLong()))
    StatRow(stringResource(R.string.analysis_average), decimal(distribution.avg))
    StatRow(stringResource(R.string.analysis_min), distribution.min.toString())
    StatRow(stringResource(R.string.analysis_max), distribution.max.toString())
}

@Composable
private fun SectionCard(title: String, testTag: String, content: @Composable ColumnScope.() -> Unit) {
    val titleColors = BasicComponentDefaults.titleColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(testTag),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = titleColors.color,
            )
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val titleColors = BasicComponentDefaults.titleColor()
    val summaryColors = BasicComponentDefaults.summaryColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = MiuixTheme.textStyles.body2.fontSize, color = summaryColors.color)
        Text(
            value,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            fontWeight = FontWeight.Medium,
            color = titleColors.color,
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    val titleColors = BasicComponentDefaults.titleColor()
    Text(
        text,
        modifier = Modifier.padding(top = 4.dp),
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        fontWeight = FontWeight.Medium,
        color = titleColors.color,
    )
}

@Composable
private fun EmptyHint(text: String) {
    val summaryColors = BasicComponentDefaults.summaryColor()
    Text(text, fontSize = MiuixTheme.textStyles.body2.fontSize, color = summaryColors.color)
}

private fun count(value: Long): String = String.format(Locale.US, "%,d", value)

/** 分桶区间标签：桶宽固定为 10，标出区间上下界。 */
private fun bucketLabel(from: Int): String = "$from-${from + 9}"

/** 运动类型名称；未收录的类型返回 null，界面回退到「运动类型 %d」。 */
@StringRes
private fun sportNameRes(id: Int): Int? = when (id) {
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

/** 血氧类型名称；未收录的类型返回 null，界面回退到「血氧类型 %d」。 */
@StringRes
private fun oxygenNameRes(type: Int): Int? = when (type) {
    2103 -> R.string.analysis_oxygen_2103
    2107 -> R.string.analysis_oxygen_2107
    else -> null
}

private fun stamp(value: Long): String =
    STAMP_FORMAT.format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))

private fun decimal(value: Double): String = String.format(Locale.US, "%.1f", value)
