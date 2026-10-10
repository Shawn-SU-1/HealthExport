package com.shawnsu.healthexport.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            BarChart(
                values = daily.months.map { it.steps.toFloat() },
                labels = daily.months.map { it.month },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SleepCard(summary: ExportSummary) {
    val sleep = summary.sleep
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
            BarChart(
                values = nights.map { it.minutes.toFloat() / it.nights / 60f },
                labels = nights.map { it.month },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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
            BarChart(
                values = heart.buckets.map { it.count.toFloat() },
                labels = heart.buckets.map { it.from.toString() },
                modifier = Modifier.padding(top = 4.dp),
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
        for (series in oxygen.series) {
            GroupTitle(stringResource(R.string.analysis_oxygen_series, series.type))
            StatRow(stringResource(R.string.analysis_records), count(series.records.toLong()))
            StatRow(stringResource(R.string.analysis_average), decimal(series.avg))
            StatRow(stringResource(R.string.analysis_min), series.min.toString())
            StatRow(stringResource(R.string.analysis_max), series.max.toString())
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
            BarChart(
                values = pressure.buckets.map { it.count.toFloat() },
                labels = pressure.buckets.map { it.from.toString() },
                modifier = Modifier.padding(top = 4.dp),
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
        for (type in types) {
            val fraction = if (maxDistance > 0L) {
                type.distanceM.toFloat() / maxDistance
            } else if (maxRecords > 0) {
                type.records.toFloat() / maxRecords
            } else {
                0f
            }
            HorizontalBar(
                label = stringResource(R.string.analysis_workout_type, type.id),
                valueText = stringResource(
                    R.string.analysis_workout_item, type.records, decimal(type.distanceM / 1000.0),
                ),
                fraction = fraction,
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
        val latest = points.last()
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
            LineChart(
                values = measured.map { it.weightKg!!.toFloat() },
                labels = measured.map { DATE_FORMAT.format(Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault())) },
                modifier = Modifier.padding(top = 4.dp),
                zeroBased = false,
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

private fun decimal(value: Double): String = String.format(Locale.US, "%.1f", value)
