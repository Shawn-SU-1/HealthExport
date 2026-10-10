package com.shawnsu.healthexport.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService

/**
 * 模块主页：作用域授权与导出入口。
 */
class SettingsActivity : AppCompatActivity(), ModuleApplication.ServiceStateListener {
    private var uiState by mutableStateOf(SettingsUiState())
    private var scopeState by mutableStateOf(ScopeUiState())
    private lateinit var scopeCoordinator: ScopeSettingsCoordinator
    private val scopeListener = ScopeSettingsListener { newState -> scopeState = newState }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val moduleApplication = application as ModuleApplication
        scopeCoordinator = moduleApplication.scopeSettingsCoordinator
        setContent {
            HealthExportTheme(
                colorMode = moduleApplication.colorMode,
                window = window,
            ) {
                SettingsScreen(
                    state = uiState,
                    scopeState = scopeState,
                    onScopeChange = { scopeCoordinator.setGranted(it) },
                    onOpenExport = {
                        startActivity(ExportActivity.intent(this))
                    },
                    onOpenAnalysis = {
                        startActivity(AnalysisActivity.intent(this))
                    },
                    onRefreshStatus = ::refreshStatus,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as ModuleApplication).addServiceStateListener(this)
        scopeCoordinator.addListener(scopeListener)
    }

    override fun onStop() {
        scopeCoordinator.removeListener(scopeListener)
        (application as ModuleApplication).removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        uiState = SettingsUiState(isServiceConnected = service != null)
    }

    private fun refreshStatus() {
        scopeCoordinator.refresh()
        onServiceStateChanged(ModuleApplication.service)
    }
}
