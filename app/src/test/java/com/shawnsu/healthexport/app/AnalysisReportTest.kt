package com.shawnsu.healthexport.app

import com.shawnsu.healthexport.R
import com.shawnsu.healthexport.export.ExportSummary
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 报告生成器是纯函数，只需假 [TextLookup] 即可在 JVM 上验证，不需要 Android 运行环境。
 * 只覆盖少量资源 id 的真实文案，其余回退为「#id」，便于定位断言失败的位置。
 */
private val LABELS: Map<Int, String> = mapOf(
    R.string.app_name to "HealthExport",
    R.string.analysis_report_title to "数据分析报告",
    R.string.analysis_report_footer to "本报告由 %1\$s 生成。",
    R.string.analysis_overview to "概览",
    R.string.analysis_steps to "步数趋势",
    R.string.analysis_sleep to "睡眠趋势",
    R.string.analysis_heart_rate to "心率",
    R.string.analysis_blood_oxygen to "血氧",
    R.string.analysis_pressure to "压力",
    R.string.analysis_workout to "运动分布",
    R.string.analysis_weight to "体重与体脂",
    R.string.analysis_no_data to "该范围内没有数据",
    R.string.analysis_report_col_month to "月份",
    R.string.analysis_report_col_steps to "步数",
    R.string.analysis_sleep_deep to "深睡",
    R.string.analysis_sleep_stage_title to "睡眠分期",
    R.string.analysis_sport_258 to "跑步",
    R.string.analysis_km_value to "%1\$s km",
    R.string.analysis_hour_value to "%1\$s 小时",
    R.string.analysis_kcal_value to "%1\$s 千卡",
    R.string.analysis_times_value to "%1\$s 次",
    R.string.analysis_percent_value to "%1\$s%%",
    R.string.analysis_days_value to "%1\$s 天",
    R.string.analysis_sleep_stage_value to "%1\$s 小时（%2\$s%%）",
    R.string.analysis_workout_type to "类型 #%1\$d",
)

private fun testLookup(): TextLookup = TextLookup { id, args ->
    val label = LABELS[id] ?: "#$id"
    if (args.isEmpty()) label else String.format(Locale.US, label, *args)
}

private fun summaryWithData(): ExportSummary = ExportSummary(
    exportedAt = 1_700_000_000_000L,
    rangeCode = "all",
    rangeLabel = "全部历史",
    startMs = 0L,
    endMs = 1_700_000_000_000L,
    counts = mapOf("daily" to 10, "sleep" to 5),
    failed = setOf("blood_sugar"),
    daily = ExportSummary.Daily(
        records = 10,
        days = 10,
        stepsTotal = 1_234_567L,
        distanceM = 12_345L,
        calories = 2_345L,
        months = listOf(
            ExportSummary.MonthActivity("2026-08", 600_000L, 31, 6_000L, 1_000L),
            ExportSummary.MonthActivity("2026-09", 634_567L, 30, 6_345L, 1_345L),
        ),
    ),
    sleep = ExportSummary.Sleep(
        records = 5,
        nights = 5,
        minutes = 2_400L,
        months = listOf(
            ExportSummary.MonthSleep("2026-08", 1_200L, 3),
            ExportSummary.MonthSleep("2026-09", 1_200L, 2),
        ),
        detail = ExportSummary.SleepDetail(
            deepMinutes = 400L,
            lightMinutes = 1_400L,
            remMinutes = 500L,
            awakeMinutes = 100L,
            stagedNights = 5,
            wakeCount = 10L,
            scoreSum = 425L,
            scoreNights = 5,
            latencySum = 60L,
            latencyNights = 5,
            napMinutes = 30L,
            lastFallAsleep = 1_699_990_000_000L,
            lastWakeUp = 1_699_993_600_000L,
            lastBedTime = 1_699_989_000_000L,
            lastWakeTime = 1_699_995_000_000L,
            months = listOf(
                ExportSummary.MonthSleepDetail("2026-08", 200L, 700L, 250L, 50L),
                ExportSummary.MonthSleepDetail("2026-09", 200L, 700L, 250L, 50L),
            ),
        ),
    ),
    heartRate = ExportSummary.Distribution(
        records = 100,
        avg = 72.5,
        min = 45,
        max = 180,
        buckets = listOf(ExportSummary.Bucket(30, 2), ExportSummary.Bucket(40, 8)),
    ),
    bloodOxygen = ExportSummary.Oxygen(
        records = 20,
        series = listOf(ExportSummary.Oxygen.Series(2103, 20, 97.5, 92, 100)),
    ),
    pressure = ExportSummary.Distribution(
        records = 50,
        avg = 45.0,
        min = 20,
        max = 80,
        buckets = listOf(ExportSummary.Bucket(30, 5), ExportSummary.Bucket(40, 45)),
    ),
    weight = listOf(
        ExportSummary.WeightPoint(1_699_000_000_000L, 70.5, 20.1, 175.0),
        ExportSummary.WeightPoint(1_700_000_000_000L, 70.0, 19.8, 175.0),
    ),
    workout = ExportSummary.Workout(
        records = 12,
        distanceM = 210_500L,
        durationMs = 120_000_000L,
        calories = 20_587L,
        types = listOf(
            ExportSummary.WorkoutType(258, 8, 150_000L, 90_000_000L, 15_000L, 150.0),
            ExportSummary.WorkoutType(259, 4, 60_500L, 30_000_000L, 5_587L, null),
        ),
    ),
)

private fun emptySummary(): ExportSummary = ExportSummary(
    exportedAt = 1_700_000_000_000L,
    rangeCode = "all",
    rangeLabel = "全部历史",
    startMs = 0L,
    endMs = 1_700_000_000_000L,
    counts = emptyMap(),
    failed = emptySet(),
    daily = ExportSummary.Daily(0, 0, 0L, 0L, 0L, emptyList()),
    sleep = ExportSummary.Sleep(
        0,
        0,
        0L,
        emptyList(),
        ExportSummary.SleepDetail(0L, 0L, 0L, 0L, 0, 0L, 0L, 0, 0L, 0, 0L, null, null, null, null, emptyList()),
    ),
    heartRate = ExportSummary.Distribution(0, 0.0, 0, 0, emptyList()),
    bloodOxygen = ExportSummary.Oxygen(0, emptyList()),
    pressure = ExportSummary.Distribution(0, 0.0, 0, 0, emptyList()),
    weight = emptyList(),
    workout = ExportSummary.Workout(0, 0L, 0L, 0L, emptyList()),
)

class AnalysisReportTest {

    @Test
    fun `报告包含标题与全部章节`() {
        val markdown = buildAnalysisMarkdown(summaryWithData(), testLookup())
        assertTrue(markdown.startsWith("# 数据分析报告\n"))
        listOf("概览", "步数趋势", "睡眠趋势", "心率", "血氧", "压力", "运动分布", "体重与体脂").forEach {
            assertTrue("缺少章节：$it", markdown.contains("## $it"))
        }
    }

    @Test
    fun `数值口径与界面一致`() {
        val markdown = buildAnalysisMarkdown(summaryWithData(), testLookup())
        // 千分位步数、公里换算、每小时换算、百分比。
        assertTrue(markdown.contains("1,234,567"))
        assertTrue(markdown.contains("12.3"))
        assertTrue(markdown.contains("40.0"))
        assertTrue(markdown.contains("| 2026-08 | 600,000 |"))
        assertTrue(markdown.contains("| 2026-09 | 634,567 |"))
        assertTrue(markdown.contains("20,587"))
    }

    @Test
    fun `睡眠分期与运动类型名称可用`() {
        val markdown = buildAnalysisMarkdown(summaryWithData(), testLookup())
        assertTrue(markdown.contains("## 睡眠分期"))
        assertTrue(markdown.contains("深睡"))
        assertTrue(markdown.contains("跑步"))
        assertTrue(markdown.contains("| 跑步 | 8 次 | 150.0 | 25.0 | 15,000 |"))
        // 未收录的类型回退为带 id 的文案，不丢失数据。
        assertTrue(markdown.contains("#${R.string.analysis_sport_259}"))
        assertTrue(markdown.contains("| 月份 | 步数 |"))
    }

    @Test
    fun `页脚带应用名并结束于换行`() {
        val markdown = buildAnalysisMarkdown(summaryWithData(), testLookup())
        assertTrue(markdown.contains("本报告由 HealthExport 生成。"))
        assertTrue(markdown.trimEnd().endsWith("本报告由 HealthExport 生成。"))
        assertTrue(markdown.contains("\n---\n"))
    }

    @Test
    fun `单元格转义竖线与换行`() {
        val markdown = buildAnalysisMarkdown(
            summaryWithData().copy(rangeLabel = "全部|历史\n第二行"),
            testLookup(),
        )
        assertTrue(markdown.contains("| 全部/历史 第二行 |"))
        assertFalse(markdown.contains("全部|历史"))
    }

    @Test
    fun `无数据时各章节给出明确提示`() {
        val markdown = buildAnalysisMarkdown(emptySummary(), testLookup())
        assertEquals(7, Regex("该范围内没有数据").findAll(markdown).count())
        assertFalse(markdown.contains("## 睡眠分期"))
    }
}

class PosterMathTest {

    @Test
    fun `留白按宽度比例且不低于下限`() {
        assertEquals(50, posterMargin(1_000))
        assertEquals(12, posterMargin(100))
    }

    @Test
    fun `水印条高度按宽度比例且不低于下限`() {
        assertEquals(167, watermarkStripHeight(1_080))
        assertEquals(64, watermarkStripHeight(100))
    }

    @Test
    fun `文件名时间戳不含冒号与空格`() {
        val stamp = fileStamp(1_700_000_000_000L)
        assertFalse(stamp.contains(':'))
        assertFalse(stamp.contains(' '))
    }
}
