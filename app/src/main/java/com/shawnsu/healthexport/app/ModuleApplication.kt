/*
 * 本文件修改自「华为运动精简」(nairain233/huawei-health-customization, GPL-3.0)。
 * Modified from huawei-health-customization (nairain233, GPL-3.0).
 * 修改日期 / Modified: 2026-10-10
 * SPDX-FileCopyrightText: 2026 nairain (nairain233)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.shawnsu.healthexport.app

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 模块应用：负责与 LSPosed 服务建立连接并通知界面。
 */
class ModuleApplication : Application(), XposedServiceHelper.OnServiceListener {
    private val listeners = CopyOnWriteArraySet<ServiceStateListener>()
    internal lateinit var scopeSettingsCoordinator: ScopeSettingsCoordinator
        private set
    internal var colorMode by mutableStateOf(AppColorMode.SYSTEM)
        private set
    private lateinit var settingsExecutor: ExecutorService

    override fun onCreate() {
        super.onCreate()
        colorMode = AppAppearancePreferences.read(this)
        settingsExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "settings").apply { isDaemon = true }
        }
        scopeSettingsCoordinator = ScopeSettingsCoordinator(
            workerExecutor = settingsExecutor,
            mainExecutor = mainExecutor,
        )
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(boundService: XposedService) {
        service = boundService
        scopeSettingsCoordinator.bind(XposedScopeService(boundService))
        notifyListeners()
    }

    override fun onServiceDied(deadService: XposedService) {
        if (service === deadService) {
            service = null
            scopeSettingsCoordinator.bind(null)
            notifyListeners()
        }
    }

    fun addServiceStateListener(listener: ServiceStateListener) {
        listeners.add(listener)
        listener.onServiceStateChanged(service)
    }

    fun removeServiceStateListener(listener: ServiceStateListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            try {
                listener.onServiceStateChanged(service)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Service state listener failed", error)
            }
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    companion object {
        private const val TAG = "HealthExport"

        @Volatile
        var service: XposedService? = null
            private set
    }
}
