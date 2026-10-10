package com.shawnsu.healthexport

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageResourcesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exposesCoreScreensInSimplifiedChineseAndEnglish() {
        val chinese = context.withLocale("zh-CN")
        val english = context.withLocale("en")

        assertEquals("HealthExport", chinese.getString(R.string.app_name))
        assertEquals("HealthExport", chinese.getString(R.string.settings_title))
        assertEquals("请求 LSPosed 作用域", chinese.getString(R.string.scope_settings_title))
        assertEquals("健康数据导出", chinese.getString(R.string.export_title))
        assertEquals("导出日志", chinese.getString(R.string.export_log_title))
        assertEquals("导出时间范围", chinese.getString(R.string.export_range_title))
        assertEquals("全部历史", chinese.getString(R.string.export_range_all))
        assertEquals("数据分析", chinese.getString(R.string.analysis_title))
        assertEquals("深睡", chinese.getString(R.string.analysis_sleep_deep))
        assertEquals("快速眼动", chinese.getString(R.string.analysis_sleep_rem))
        assertEquals("跑步", chinese.getString(R.string.analysis_sport_258))
        assertEquals("室内单车", chinese.getString(R.string.analysis_sport_265))
        assertEquals("血氧测量（SpO2）", chinese.getString(R.string.analysis_oxygen_2103))
        assertEquals("低血氧提醒（低于 90%）", chinese.getString(R.string.analysis_oxygen_2107))

        assertEquals("HealthExport", english.getString(R.string.app_name))
        assertEquals("HealthExport", english.getString(R.string.settings_title))
        assertEquals("Request LSPosed scope", english.getString(R.string.scope_settings_title))
        assertEquals("Health data export", english.getString(R.string.export_title))
        assertEquals("Export log", english.getString(R.string.export_log_title))
        assertEquals("Export time range", english.getString(R.string.export_range_title))
        assertEquals("All history", english.getString(R.string.export_range_all))
        assertEquals("Data analysis", english.getString(R.string.analysis_title))
        assertEquals("Deep sleep", english.getString(R.string.analysis_sleep_deep))
        assertEquals("REM sleep", english.getString(R.string.analysis_sleep_rem))
        assertEquals("Running", english.getString(R.string.analysis_sport_258))
        assertEquals("Indoor cycling", english.getString(R.string.analysis_sport_265))
        assertEquals("Blood oxygen (SpO2)", english.getString(R.string.analysis_oxygen_2103))
        assertEquals("Low blood oxygen alerts (below 90%)", english.getString(R.string.analysis_oxygen_2107))
    }

    @Test
    fun keepsFormattedResourceArgumentsInBothLanguages() {
        val chinese = context.withLocale("zh-CN")
        val english = context.withLocale("en")

        assertEquals(
            "导出完成，共 1 条",
            chinese.getString(R.string.export_complete_all, 1),
        )
        assertEquals(
            "Export complete. 1 records",
            english.getString(R.string.export_complete_all, 1),
        )
        assertEquals(
            "时间范围：全部历史（startTime=0）",
            chinese.getString(R.string.export_range_current, "全部历史（startTime=0）"),
        )
        assertEquals(
            "Time range: All history",
            english.getString(R.string.export_range_current, "All history"),
        )
        assertEquals(
            "6.5 小时 · 42.0%",
            chinese.getString(R.string.analysis_sleep_stage_value, "6.5", "42.0"),
        )
        assertEquals(
            "6.5 h · 42.0%",
            english.getString(R.string.analysis_sleep_stage_value, "6.5", "42.0"),
        )
    }

    private fun Context.withLocale(languageTag: String): Context {
        val configuration = Configuration(resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        }
        return createConfigurationContext(configuration)
    }
}
