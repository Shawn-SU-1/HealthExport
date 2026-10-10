package com.shawnsu.healthexport.app

import com.shawnsu.healthexport.R
import com.shawnsu.healthexport.export.ExportSummary

/**
 * 由导出的 JSON 统计摘要生成 Markdown 分析报告，数值口径与「数据分析」界面完全一致。
 * 纯函数：所有文案经 [TextLookup] 取得，可在 JVM 单测中直接断言，无需 Android 环境。
 */
internal fun buildAnalysisMarkdown(summary: ExportSummary, lookup: TextLookup): String {
    val builder = StringBuilder(4096)
    builder.append("# ").append(lookup.text(R.string.analysis_report_title)).append("\n\n")
    appendOverview(builder, summary, lookup)
    appendSteps(builder, summary, lookup)
    appendSleep(builder, summary, lookup)
    appendHeartRate(builder, summary, lookup)
    appendBloodOxygen(builder, summary, lookup)
    appendPressure(builder, summary, lookup)
    appendWorkout(builder, summary, lookup)
    appendWeight(builder, summary, lookup)
    builder.append("---\n\n")
    builder.append(lookup.text(R.string.analysis_report_footer, lookup.text(R.string.app_name)))
    builder.append('\n')
    return builder.toString()
}

private fun appendOverview(builder: StringBuilder, summary: ExportSummary, lookup: TextLookup) {
    heading(builder, lookup.text(R.string.analysis_overview))
    tableStart(builder, lookup)
    row(builder, lookup.text(R.string.analysis_exported_at), stamp(summary.exportedAt))
    row(builder, lookup.text(R.string.analysis_range), summary.rangeLabel)
    row(builder, lookup.text(R.string.analysis_records), lookup.text(R.string.analysis_records_value, summary.counts.values.sum()))
    row(
        builder,
        lookup.text(R.string.analysis_datasets),
        lookup.text(R.string.analysis_dataset_value, summary.counts.size, summary.failed.size),
    )
    tableEnd(builder)
}

private fun appendSteps(builder: StringBuilder, summary: ExportSummary, lookup: TextLookup) {
    val daily = summary.daily
    heading(builder, lookup.text(R.string.analysis_steps))
    if (daily.records == 0) {
        note(builder, lookup.text(R.string.analysis_no_data))
        return
    }
    tableStart(builder, lookup)
    row(builder, lookup.text(R.string.analysis_steps_total), count(daily.stepsTotal))
    row(builder, lookup.text(R.string.analysis_days), lookup.text(R.string.analysis_days_value, daily.days))
    row(builder, lookup.text(R.string.analysis_steps_per_day), count(daily.stepsPerDay))
    row(builder, lookup.text(R.string.analysis_distance), lookup.text(R.string.analysis_km_value, decimal(daily.distanceM / 1000.0)))
    row(builder, lookup.text(R.string.analysis_calories), lookup.text(R.string.analysis_kcal_value, count(daily.calories)))
    tableEnd(builder)
    if (daily.months.size >= 2) {
        heading(builder, lookup.text(R.string.analysis_chart_monthly_steps))
        tableStart(builder, lookup, lookup.text(R.string.analysis_report_col_month), lookup.text(R.string.analysis_report_col_steps))
        daily.months.forEach { row(builder, it.month, count(it.steps)) }
        tableEnd(builder)
    }
}

private fun appendSleep(builder: StringBuilder, summary: ExportSummary, lookup: TextLookup) {
    val sleep = summary.sleep
    val detail = sleep.detail
    heading(builder, lookup.text(R.string.analysis_sleep))
    if (sleep.nights == 0) {
        note(builder, lookup.text(R.string.analysis_no_data))
        return
    }
    tableStart(builder, lookup)
    row(builder, lookup.text(R.string.analysis_sleep_nights), count(sleep.nights.toLong()))
    row(builder, lookup.text(R.string.analysis_sleep_total), lookup.text(R.string.analysis_hour_value, decimal(sleep.minutes / 60.0)))
    row(builder, lookup.text(R.string.analysis_sleep_average), lookup.text(R.string.analysis_hour_value, decimal(sleep.minutesPerNight / 60.0)))
    tableEnd(builder)
    val nights = sleep.months.filter { it.nights > 0 }
    if (nights.size >= 2) {
        heading(builder, lookup.text(R.string.analysis_chart_nightly_sleep))
        tableStart(builder, lookup, lookup.text(R.string.analysis_report_col_month), lookup.text(R.string.analysis_report_col_duration))
        nights.forEach { row(builder, it.month, decimal(it.minutes.toDouble() / it.nights / 60.0)) }
        tableEnd(builder)
    }
    if (!detail.hasStages) {
        note(builder, lookup.text(R.string.analysis_sleep_no_stage))
        return
    }
    heading(builder, lookup.text(R.string.analysis_sleep_stage_title))
    tableStart(builder, lookup)
    stageRow(builder, lookup, R.string.analysis_sleep_deep, detail.deepMinutes, detail)
    stageRow(builder, lookup, R.string.analysis_sleep_light, detail.lightMinutes, detail)
    stageRow(builder, lookup, R.string.analysis_sleep_rem, detail.remMinutes, detail)
    stageRow(builder, lookup, R.string.analysis_sleep_awake, detail.awakeMinutes, detail)
    row(builder, lookup.text(R.string.analysis_sleep_staged_nights), lookup.text(R.string.analysis_days_value, detail.stagedNights))
    row(builder, lookup.text(R.string.analysis_sleep_deep_average), lookup.text(R.string.analysis_hour_value, decimal(detail.deepPerNight / 60.0)))
    row(builder, lookup.text(R.string.analysis_sleep_deep_ratio), lookup.text(R.string.analysis_percent_value, decimal(detail.deepPercent)))
    detail.scoreAvg?.let {
        row(builder, lookup.text(R.string.analysis_sleep_score), lookup.text(R.string.analysis_sleep_score_value, decimal(it)))
    }
    detail.latencyAvg?.let {
        row(builder, lookup.text(R.string.analysis_sleep_latency), lookup.text(R.string.analysis_sleep_latency_value, decimal(it)))
    }
    detail.wakePerNight?.let {
        row(builder, lookup.text(R.string.analysis_sleep_wake_count), lookup.text(R.string.analysis_sleep_wake_count_value, decimal(it)))
    }
    if (detail.napMinutes > 0L) {
        row(builder, lookup.text(R.string.analysis_sleep_nap), lookup.text(R.string.analysis_hour_value, decimal(detail.napMinutes / 60.0)))
    }
    detail.lastFallAsleep?.let { row(builder, lookup.text(R.string.analysis_sleep_last_fall), stamp(it)) }
    detail.lastWakeUp?.let { row(builder, lookup.text(R.string.analysis_sleep_last_wake), stamp(it)) }
    detail.lastBedTime?.let { row(builder, lookup.text(R.string.analysis_sleep_last_bed), stamp(it)) }
    detail.lastWakeTime?.let { row(builder, lookup.text(R.string.analysis_sleep_last_off_bed), stamp(it)) }
    tableEnd(builder)
    val stageMonths = detail.months.filter { it.deepMinutes + it.lightMinutes + it.remMinutes > 0L }
    if (stageMonths.size >= 2) {
        heading(builder, lookup.text(R.string.analysis_chart_stage_stack))
        tableStart(
            builder,
            lookup,
            lookup.text(R.string.analysis_report_col_month),
            lookup.text(R.string.analysis_sleep_deep),
            lookup.text(R.string.analysis_sleep_light),
            lookup.text(R.string.analysis_sleep_rem),
        )
        stageMonths.forEach {
            tableRow(
                builder,
                it.month,
                decimal(it.deepMinutes / 60.0),
                decimal(it.lightMinutes / 60.0),
                decimal(it.remMinutes / 60.0),
            )
        }
        tableEnd(builder)
    }
}

private fun stageRow(
    builder: StringBuilder,
    lookup: TextLookup,
    labelRes: Int,
    minutes: Long,
    detail: ExportSummary.SleepDetail,
) {
    row(
        builder,
        lookup.text(labelRes),
        lookup.text(R.string.analysis_sleep_stage_value, decimal(minutes / 60.0), decimal(detail.percentOf(minutes))),
    )
}

private fun appendHeartRate(builder: StringBuilder, summary: ExportSummary, lookup: TextLookup) {
    val heart = summary.heartRate
    heading(builder, lookup.text(R.string.analysis_heart_rate))
    if (heart.records == 0) {
        note(builder, lookup.text(R.string.analysis_no_data))
        return
    }
    distributionTable(builder, heart, lookup)
    bucketTable(builder, lookup, heart, R.string.analysis_chart_hr_distribution)
}

private fun appendPressure(builder: StringBuilder, summary: ExportSummary, lookup: TextLookup) {
    val pressure = summary.pressure
    heading(builder, lookup.text(R.string.analysis_pressure))
    if (pressure.records == 0) {
        note(builder, lookup.text(R.string.analysis_no_data))
        return
    }
    distributionTable(builder, pressure, lookup)
    bucketTable(builder, lookup, pressure, R.string.analysis_chart_pressure_distribution)
}

private fun distributionTable(builder: StringBuilder, distribution: ExportSummary.Distribution, lookup: TextLookup) {
    tableStart(builder, lookup)
    row(builder, lookup.text(R.string.analysis_records), count(distribution.records.toLong()))
    row(builder, lookup.text(R.string.analysis_average), decimal(distribution.avg))
    row(builder, lookup.text(R.string.analysis_min), distribution.min.toString())
    row(builder, lookup.text(R.string.analysis_max), distribution.max.toString())
    tableEnd(builder)
}

private fun bucketTable(
    builder: StringBuilder,
    lookup: TextLookup,
    distribution: ExportSummary.Distribution,
    titleRes: Int,
) {
    if (distribution.buckets.size < 2) return
    heading(builder, lookup.text(titleRes))
    tableStart(builder, lookup, lookup.text(R.string.analysis_report_col_range), lookup.text(R.string.analysis_report_col_count))
    distribution.buckets.forEach { row(builder, bucketLabel(it.from), count(it.count.toLong())) }
    tableEnd(builder)
}

private fun appendBloodOxygen(builder: StringBuilder, summary: ExportSummary, lookup: TextLookup) {
    val oxygen = summary.bloodOxygen
    heading(builder, lookup.text(R.string.analysis_blood_oxygen))
    if (oxygen.records == 0) {
        note(builder, lookup.text(R.string.analysis_no_data))
        return
    }
    for (series in oxygen.series) {
        val nameRes = oxygenNameRes(series.type)
        builder.append("**").append(
            if (nameRes != null) lookup.text(nameRes) else lookup.text(R.string.analysis_oxygen_series, series.type),
        ).append("**\n\n")
        tableStart(builder, lookup)
        row(builder, lookup.text(R.string.analysis_records), count(series.records.toLong()))
        row(builder, lookup.text(R.string.analysis_min), lookup.text(R.string.analysis_percent_value, decimal(series.min.toDouble())))
        row(builder, lookup.text(R.string.analysis_average), lookup.text(R.string.analysis_percent_value, decimal(series.avg)))
        row(builder, lookup.text(R.string.analysis_max), lookup.text(R.string.analysis_percent_value, decimal(series.max.toDouble())))
        tableEnd(builder)
    }
}

private fun appendWorkout(builder: StringBuilder, summary: ExportSummary, lookup: TextLookup) {
    val workout = summary.workout
    heading(builder, lookup.text(R.string.analysis_workout))
    if (workout.records == 0) {
        note(builder, lookup.text(R.string.analysis_no_data))
        return
    }
    tableStart(builder, lookup)
    row(builder, lookup.text(R.string.analysis_workout_count), lookup.text(R.string.analysis_times_value, workout.records))
    row(builder, lookup.text(R.string.analysis_distance), lookup.text(R.string.analysis_km_value, decimal(workout.distanceM / 1000.0)))
    row(builder, lookup.text(R.string.analysis_workout_duration), lookup.text(R.string.analysis_hour_value, decimal(workout.durationMs / 3_600_000.0)))
    row(builder, lookup.text(R.string.analysis_calories), lookup.text(R.string.analysis_kcal_value, count(workout.calories)))
    tableEnd(builder)
    val types = workout.types.sortedByDescending { it.distanceM }
    if (types.isEmpty()) return
    tableStart(
        builder,
        lookup,
        lookup.text(R.string.analysis_report_col_type),
        lookup.text(R.string.analysis_report_col_count),
        lookup.text(R.string.analysis_report_col_distance),
        lookup.text(R.string.analysis_report_col_duration),
        lookup.text(R.string.analysis_report_col_calories),
    )
    types.forEach {
        val nameRes = sportNameRes(it.id)
        tableRow(
            builder,
            if (nameRes != null) lookup.text(nameRes) else lookup.text(R.string.analysis_workout_type, it.id),
            lookup.text(R.string.analysis_times_value, it.records),
            decimal(it.distanceM / 1000.0),
            decimal(it.durationMs / 3_600_000.0),
            count(it.calories),
        )
    }
    tableEnd(builder)
}

private fun appendWeight(builder: StringBuilder, summary: ExportSummary, lookup: TextLookup) {
    val measured = summary.weight.filter { it.weightKg != null }
    heading(builder, lookup.text(R.string.analysis_weight))
    if (measured.isEmpty()) {
        note(builder, lookup.text(R.string.analysis_no_data))
        return
    }
    val latest = measured.last()
    tableStart(builder, lookup)
    row(builder, lookup.text(R.string.analysis_records), count(measured.size.toLong()))
    latest.weightKg?.let {
        row(builder, lookup.text(R.string.analysis_weight_latest), lookup.text(R.string.analysis_kg_value, decimal(it)))
    }
    latest.bodyFatPercent?.let {
        row(builder, lookup.text(R.string.analysis_body_fat), lookup.text(R.string.analysis_percent_value, decimal(it)))
    }
    latest.heightCm?.let {
        row(builder, lookup.text(R.string.analysis_height), lookup.text(R.string.analysis_cm_value, decimal(it)))
    }
    tableEnd(builder)
}

private fun heading(builder: StringBuilder, title: String) {
    builder.append("## ").append(title).append("\n\n")
}

private fun note(builder: StringBuilder, text: String) {
    builder.append(text).append("\n\n")
}

private fun tableStart(builder: StringBuilder, lookup: TextLookup) {
    tableStart(builder, lookup, lookup.text(R.string.analysis_report_col_item), lookup.text(R.string.analysis_report_col_value))
}

private fun tableStart(builder: StringBuilder, lookup: TextLookup, vararg headers: String) {
    tableRow(builder, *headers)
    builder.append("|")
    headers.forEach { builder.append(" --- |") }
    builder.append('\n')
}

private fun row(builder: StringBuilder, label: String, value: String) {
    tableRow(builder, label, value)
}

private fun tableEnd(builder: StringBuilder) {
    builder.append('\n')
}

private fun tableRow(builder: StringBuilder, vararg cells: String) {
    builder.append("|")
    cells.forEach { builder.append(' ').append(cell(it)).append(" |") }
    builder.append('\n')
}

/** 表格单元格转义：竖线会破坏表格结构，换行会造成行错位。 */
private fun cell(text: String): String =
    text.replace('|', '/').replace('\n', ' ').replace('\r', ' ')
