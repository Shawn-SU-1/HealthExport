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

        assertEquals("HealthExport", english.getString(R.string.app_name))
        assertEquals("HealthExport", english.getString(R.string.settings_title))
        assertEquals("Request LSPosed scope", english.getString(R.string.scope_settings_title))
        assertEquals("Health data export", english.getString(R.string.export_title))
        assertEquals("Export log", english.getString(R.string.export_log_title))
        assertEquals("Export time range", english.getString(R.string.export_range_title))
        assertEquals("All history", english.getString(R.string.export_range_all))
        assertEquals("Data analysis", english.getString(R.string.analysis_title))
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
    }

    private fun Context.withLocale(languageTag: String): Context {
        val configuration = Configuration(resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        }
        return createConfigurationContext(configuration)
    }
}
