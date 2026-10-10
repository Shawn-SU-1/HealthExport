/*
 * 本文件修改自「华为运动精简」(nairain233/huawei-health-customization, GPL-3.0)。
 * Modified from huawei-health-customization (nairain233, GPL-3.0).
 * 修改日期 / Modified: 2026-10-10
 * SPDX-FileCopyrightText: 2026 nairain (nairain233)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.shawnsu.healthexport.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import com.shawnsu.healthexport.config.SettingsKeys
import com.shawnsu.healthexport.export.ExportProtocol
import com.shawnsu.healthexport.export.ExportRequests
import com.shawnsu.healthexport.export.ExportRuntime
import com.shawnsu.healthexport.hook.util.ModuleLogger
import io.github.libxposed.api.XposedInterface

/**
 * 安装 Application.attach 与 Activity.onResume 两个钩子，二者共用一个派发入口：
 * 前者覆盖目标进程冷启动，后者覆盖进程存活期间回到前台，
 * 因此预约变更不再需要强制结束目标进程。
 */
internal object HookCoordinator {
    private val installGate = HookInstallGate()

    @Volatile
    private var application: Application? = null

    @SuppressLint("DiscouragedPrivateApi") // Hook 必须在目标应用的 Context 初始化后安装。
    fun installAttachHook(framework: XposedInterface) {
        if (!installGate.claimAttach()) return
        val logger = ModuleLogger(framework)
        val attach = runCatching {
            Application::class.java.getDeclaredMethod("attach", Context::class.java).apply {
                isAccessible = true
            }
        }.getOrElse {
            logger.warn("Application.attach unavailable", it)
            return
        }
        framework.hook(attach)
            .setId("healthexport.application.attach")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val target = chain.thisObject as? Application
                if (target != null && installGate.claimRuntime()) {
                    installAfterAttach(framework, target, logger)
                }
                result
            }
    }

    private fun installAfterAttach(
        framework: XposedInterface,
        target: Application,
        logger: ModuleLogger,
    ) {
        if (target.packageName != HookInstallPolicy.TARGET_PACKAGE) return
        application = target
        installResumeHook(framework, logger)
        dispatch(framework, target, logger)
    }

    /** 回到前台时再读一次预约；钩子只在目标进程内生效。 */
    @SuppressLint("DiscouragedPrivateApi") // 前台恢复钩子必须挂到 Activity 生命周期回调。
    private fun installResumeHook(framework: XposedInterface, logger: ModuleLogger) {
        if (!installGate.claimForeground()) return
        val onResume = runCatching {
            Activity::class.java.getDeclaredMethod("onResume").apply { isAccessible = true }
        }.getOrElse {
            logger.warn("Activity.onResume unavailable", it)
            return
        }
        framework.hook(onResume)
            .setId("healthexport.activity.resume")
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                val target = application
                if (activity != null && target != null &&
                    activity.packageName == HookInstallPolicy.TARGET_PACKAGE
                ) {
                    dispatch(framework, target, logger)
                }
                result
            }
    }

    /** 读取共享预约并派发导出；同一预约在同一进程内只派发一次。 */
    private fun dispatch(framework: XposedInterface, target: Application, logger: ModuleLogger) {
        val preferences = try {
            framework.getRemotePreferences(SettingsKeys.GROUP)
        } catch (error: RuntimeException) {
            logger.warn("Export request unavailable: ${error.javaClass.simpleName}")
            null
        }
        val exportRequest = try {
            preferences?.getString(ExportProtocol.REQUEST, "").orEmpty()
        } catch (error: RuntimeException) {
            logger.warn("Export request unavailable: ${error.javaClass.simpleName}")
            ""
        }
        if (exportRequest.isEmpty() || !installGate.claimRequest(exportRequest)) return
        val exportRange = ExportRequests.range(preferences)
        ExportRuntime.start(target, exportRequest, exportRange, logger)
    }
}
