package com.shawnsu.healthexport.app

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shawnsu.healthexport.export.ExportProtocol
import com.shawnsu.healthexport.export.ExportSummary

/** 展示最近一次导出的图文分析；摘要由导出过程同源生成并回传，界面只读取模块自己的存储。 */
class AnalysisActivity : AppCompatActivity() {
    private var summary by mutableStateOf<ExportSummary?>(null)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            read()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        setContent {
            HealthExportTheme(
                colorMode = moduleApplication.colorMode,
                window = window,
            ) {
                AnalysisScreen(summary = summary, onClose = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(
            "content://${ExportProtocol.AUTHORITY}".toUri(),
            true,
            observer,
        )
        read()
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(observer)
        super.onStop()
    }

    /** 摘要只在导出完成后写入；读取失败按「暂无数据」处理，等待下一次导出刷新。 */
    private fun read() {
        summary = runCatching {
            getSharedPreferences(ExportProtocol.SUMMARY_STORE, Context.MODE_PRIVATE)
                .getString(ExportProtocol.SUMMARY_KEY, null)
                ?.let(ExportSummary::decode)
        }.getOrNull()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, AnalysisActivity::class.java)
    }
}
