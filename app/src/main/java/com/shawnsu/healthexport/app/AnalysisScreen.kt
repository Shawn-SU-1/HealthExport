package com.shawnsu.healthexport.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawnsu.healthexport.R
import com.shawnsu.healthexport.export.ExportSummary
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
        val context = LocalContext.current
        val shots = remember { ChartShots() }
        var style by remember { mutableStateOf(PosterStyle.of(context)) }
        // 二维码只在启用角标时解码一次，避免每张卡片各解一遍。
        val mark = remember(style.branded) {
            if (style.branded) BitmapFactory.decodeResource(context.resources, R.drawable.repo_qr) else null
        }
        CompositionLocalProvider(
            LocalChartShots provides shots,
            LocalPosterStyle provides style,
            LocalPosterMark provides mark,
        ) {
            // 一次性组合全部卡片（非惰性列表），保证未滚动到的图表也已绘制、可被截图导出。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings:analysis")
                    .scrollEndHaptic()
                    .verticalScroll(rememberScrollState())
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(
                        PaddingValues(
                            start = safeInsets.calculateLeftPadding(layoutDirection),
                            top = padding.calculateTopPadding() + 8.dp,
                            end = safeInsets.calculateRightPadding(layoutDirection),
                            bottom = padding.calculateBottomPadding(),
                        ),
                    ),
            ) {
                if (summary == null) {
                    SectionCard(stringResource(R.string.analysis_title), "analysis:empty") {
                        EmptyHint(stringResource(R.string.analysis_empty))
                    }
                } else {
                    ExportShareCard(
                        summary = summary,
                        style = style,
                        onStyleChange = {
                            style = it
                            PosterStyle.store(context, it)
                        },
                    )
                    OverviewCard(summary)
                    StepsCard(summary)
                    SleepCard(summary)
                    HeartRateCard(summary)
                    BloodOxygenCard(summary)
                    PressureCard(summary)
                    WorkoutCard(summary)
                    WeightCard(summary)
                }
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun OverviewCard(summary: ExportSummary) {
    SectionCard(stringResource(R.string.analysis_overview), "analysis:overview", "overview") {
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
    SectionCard(stringResource(R.string.analysis_steps), "analysis:steps", "steps") {
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
    SectionCard(stringResource(R.string.analysis_sleep), "analysis:sleep", "sleep") {
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
    SectionCard(stringResource(R.string.analysis_heart_rate), "analysis:heart", "heart-rate") {
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
    SectionCard(stringResource(R.string.analysis_blood_oxygen), "analysis:oxygen", "blood-oxygen") {
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
    SectionCard(stringResource(R.string.analysis_pressure), "analysis:pressure", "pressure") {
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
    SectionCard(stringResource(R.string.analysis_workout), "analysis:workout", "workout") {
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
    SectionCard(stringResource(R.string.analysis_weight), "analysis:weight", "weight") {
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
private fun SectionCard(
    title: String,
    testTag: String,
    shareSlug: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val titleColors = BasicComponentDefaults.titleColor()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shots = LocalChartShots.current
    val style = LocalPosterStyle.current
    val mark = LocalPosterMark.current
    // 每个带图表标识的卡片各自持有一个绘图图层，用于把卡片内容原样截图后分享。
    val layer = rememberGraphicsLayer()
    DisposableEffect(shareSlug, shots) {
        if (shareSlug != null && shots != null) shots.register(shareSlug, title, layer)
        onDispose { if (shareSlug != null) shots?.unregister(shareSlug) }
    }
    val appName = stringResource(R.string.app_name)
    val chooserTitle = stringResource(R.string.analysis_share_via)
    val savedTemplate = stringResource(R.string.analysis_export_saved)
    val shareOnlyText = stringResource(R.string.analysis_export_share_only)
    val failedText = stringResource(R.string.analysis_export_failed)
    val failedTemplate = stringResource(R.string.analysis_export_failed_reason)
    val shareLabel = stringResource(R.string.analysis_share_chart)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(testTag),
    ) {
        Card(
            // 分享按钮不放进来，避免把自己也截进图片。
            modifier = Modifier
                .fillMaxWidth()
                .then(if (shareSlug == null) Modifier else Modifier.captureLayer(layer)),
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
                    modifier = if (shareSlug == null) Modifier else Modifier.padding(end = 40.dp),
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = titleColors.color,
                )
                content()
            }
        }
        if (shareSlug != null) {
            IconButton(
                onClick = {
                    scope.launch {
                        var failure: Throwable? = null
                        val result = runCatching {
                            val shot = layer.toImageBitmap().asAndroidBitmap()
                            val poster = composePoster(
                                source = shot,
                                accent = ChartPalette.Primary.toArgb(),
                                title = appName,
                                subtitle = title,
                                stamp = fileStamp(System.currentTimeMillis()),
                                theme = style.theme,
                                rounded = style.rounded,
                                mark = mark,
                            )
                            ShareExporter.shareImage(
                                context = context,
                                bitmap = poster,
                                fileName = "HealthExport-$shareSlug-${fileStamp(System.currentTimeMillis())}.png",
                                chooserTitle = chooserTitle,
                            )
                        }.onFailure { failure = it }.getOrNull()
                        Toast.makeText(context, exportMessage(result, failure, savedTemplate, shareOnlyText, failedText, failedTemplate), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Share,
                    contentDescription = shareLabel,
                )
            }
        }
    }
}

/** 导出结果的统一提示语：失败时带上异常类型，便于定位截图/分享环节的问题。 */
private fun exportMessage(
    result: ExportResult?,
    failure: Throwable?,
    savedTemplate: String,
    shareOnlyText: String,
    failedText: String,
    failedTemplate: String,
): String = when {
    result == null && failure != null -> String.format(failedTemplate, failure.javaClass.simpleName)
    result == null -> failedText
    result.savedPath != null -> String.format(savedTemplate, result.savedPath)
    else -> shareOnlyText
}

/**
 * 导出与分享入口：把当前分析的完整数值写成 Markdown 文件并调起系统分享。
 * 图表图片则在每张卡片右上角的分享按钮上导出。
 */
@Composable
private fun ExportShareCard(
    summary: ExportSummary,
    style: PosterStyle,
    onStyleChange: (PosterStyle) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lookup = rememberTextLookup()
    val shots = LocalChartShots.current
    val mark = LocalPosterMark.current
    val appName = stringResource(R.string.app_name)
    val chooserTitle = stringResource(R.string.analysis_share_via)
    val savedTemplate = stringResource(R.string.analysis_export_saved)
    val shareOnlyText = stringResource(R.string.analysis_export_share_only)
    val failedText = stringResource(R.string.analysis_export_failed)
    val failedTemplate = stringResource(R.string.analysis_export_failed_reason)
    val noChartText = stringResource(R.string.analysis_export_no_chart)
    SectionCard(stringResource(R.string.analysis_export_section), "analysis:export") {
        // 1）纯数据报告：只有表格，体积最小。
        ExportActionRow(MiuixIcons.Regular.Notes, stringResource(R.string.analysis_export_report)) {
            scope.launch {
                var failure: Throwable? = null
                val result = runCatching {
                    ShareExporter.shareText(
                        context = context,
                        text = buildAnalysisMarkdown(summary, lookup),
                        fileName = "HealthExport-analysis-${fileStamp(System.currentTimeMillis())}.md",
                        chooserTitle = chooserTitle,
                    )
                }.onFailure { failure = it }.getOrNull()
                Toast.makeText(
                    context,
                    exportMessage(result, failure, savedTemplate, shareOnlyText, failedText, failedTemplate),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        EmptyHint(stringResource(R.string.analysis_export_report_hint))

        // 2）图文报告：报告 md + images/*.png 打成 zip，保持相对路径不出错。
        ExportActionRow(MiuixIcons.Regular.Report, stringResource(R.string.analysis_export_pack)) {
            scope.launch {
                var failure: Throwable? = null
                var empty = false
                val result = runCatching {
                    val posters = capturePosters(shots, appName, style, mark)
                    if (posters.isEmpty()) {
                        empty = true
                        null
                    } else {
                        val entries = ArrayList<Pair<String, ByteArray>>(posters.size + 1)
                        entries += "report.md" to buildAnalysisMarkdown(
                            summary,
                            lookup,
                            posters.associate { (slug, _) -> slug to "images/$slug.png" },
                        ).toByteArray(Charsets.UTF_8)
                        for ((slug, bitmap) in posters) {
                            val bytes = ShareExporter.encodePng(bitmap) ?: continue
                            entries += "images/$slug.png" to bytes
                        }
                        ShareExporter.shareArchive(
                            context = context,
                            entries = entries,
                            fileName = "HealthExport-report-${fileStamp(System.currentTimeMillis())}.zip",
                            chooserTitle = chooserTitle,
                        )
                    }
                }.onFailure { failure = it }.getOrNull()
                Toast.makeText(
                    context,
                    if (empty) {
                        noChartText
                    } else {
                        exportMessage(result, failure, savedTemplate, shareOnlyText, failedText, failedTemplate)
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        EmptyHint(stringResource(R.string.analysis_export_pack_hint))

        // 3）一次分享全部图表图片。
        ExportActionRow(MiuixIcons.Regular.Photos, stringResource(R.string.analysis_share_all)) {
            scope.launch {
                var failure: Throwable? = null
                var empty = false
                val result = runCatching {
                    val posters = capturePosters(shots, appName, style, mark)
                    if (posters.isEmpty()) {
                        empty = true
                        null
                    } else {
                        ShareExporter.shareImages(context, posters, chooserTitle)
                    }
                }.onFailure { failure = it }.getOrNull()
                Toast.makeText(
                    context,
                    if (empty) {
                        noChartText
                    } else {
                        exportMessage(result, failure, savedTemplate, shareOnlyText, failedText, failedTemplate)
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        EmptyHint(stringResource(R.string.analysis_share_all_hint))

        GroupTitle(stringResource(R.string.analysis_export_style))
        RadioButtonPreference(
            title = stringResource(R.string.analysis_export_theme_dark),
            selected = style.theme == PosterTheme.DARK,
            onClick = { onStyleChange(style.copy(theme = PosterTheme.DARK)) },
        )
        RadioButtonPreference(
            title = stringResource(R.string.analysis_export_theme_light),
            selected = style.theme == PosterTheme.LIGHT,
            onClick = { onStyleChange(style.copy(theme = PosterTheme.LIGHT)) },
        )
        SwitchPreference(
            title = stringResource(R.string.analysis_export_rounded),
            checked = style.rounded,
            onCheckedChange = { onStyleChange(style.copy(rounded = it)) },
        )
        SwitchPreference(
            title = stringResource(R.string.analysis_export_branded),
            checked = style.branded,
            onCheckedChange = { onStyleChange(style.copy(branded = it)) },
        )
        EmptyHint(stringResource(R.string.analysis_export_chart_hint))
        EmptyHint(stringResource(R.string.analysis_export_detail_hint))
    }
}

/** 把当前已绘制过的全部图表逐个合成为分享海报，顺序与界面一致。 */
private suspend fun capturePosters(
    shots: ChartShots?,
    appName: String,
    style: PosterStyle,
    mark: Bitmap?,
): List<Pair<String, Bitmap>> = shots?.captureAll().orEmpty().map { shot ->
    shot.slug to composePoster(
        source = shot.bitmap,
        accent = ChartPalette.Primary.toArgb(),
        title = appName,
        subtitle = shot.title,
        stamp = fileStamp(System.currentTimeMillis()),
        theme = style.theme,
        rounded = style.rounded,
        mark = mark,
    )
}

/** 一行可点击的导出 / 分享动作。 */
@Composable
private fun ExportActionRow(imageVector: ImageVector, label: String, onClick: () -> Unit) {
    val titleColors = BasicComponentDefaults.titleColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = imageVector, contentDescription = null)
        Text(
            label,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            fontWeight = FontWeight.Medium,
            color = titleColors.color,
        )
    }
}

/** 报告生成器只依赖资源解析接口，便于在界面外复用与测试。 */
@Composable
private fun rememberTextLookup(): TextLookup {
    val context = LocalContext.current
    return remember(context) {
        TextLookup { id, args ->
            val resources = context.resources
            if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
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

