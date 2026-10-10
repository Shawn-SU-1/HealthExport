/*
 * 本文件修改自「华为运动精简」(nairain233/huawei-health-customization, GPL-3.0)。
 * Modified from huawei-health-customization (nairain233, GPL-3.0).
 * 修改日期 / Modified: 2026-10-10
 * SPDX-FileCopyrightText: 2026 nairain (nairain233)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.shawnsu.healthexport.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/** API 102 入口：这里只做包、主进程和首包守卫。 */
class HookEntry : XposedModule() {
    private var processName = ""

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!HookInstallPolicy.acceptsPackage(param.packageName, processName, param.isFirstPackage)) {
            return
        }
        HookCoordinator.installAttachHook(this)
    }
}
