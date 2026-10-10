package com.shawnsu.healthexport.hook

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.shawnsu.healthexport.config.SettingsKeys
import com.shawnsu.healthexport.export.ExportProtocol
import com.shawnsu.healthexport.export.ExportRuntime
import com.shawnsu.healthexport.hook.util.ModuleLogger
import io.github.libxposed.api.XposedInterface

/** 只安装 Application.attach；附加完成后读取导出预约并执行。 */
internal object HookCoordinator {
    private val installGate = HookInstallGate()

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
                val application = chain.thisObject as? Application
                if (application != null && installGate.claimRuntime()) {
                    installAfterAttach(framework, application, logger)
                }
                result
            }
    }

    private fun installAfterAttach(
        framework: XposedInterface,
        application: Application,
        logger: ModuleLogger,
    ) {
        if (application.packageName != HookInstallPolicy.TARGET_PACKAGE) return
        val exportRequest = try {
            framework.getRemotePreferences(SettingsKeys.GROUP).getString(ExportProtocol.REQUEST, "").orEmpty()
        } catch (error: RuntimeException) {
            logger.warn("Export request unavailable: ${error.javaClass.simpleName}")
            ""
        }
        ExportRuntime.start(application, exportRequest, logger)
    }
}
