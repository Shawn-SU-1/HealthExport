/*
 * 本文件修改自「华为运动精简」(nairain233/huawei-health-customization, GPL-3.0)。
 * Modified from huawei-health-customization (nairain233, GPL-3.0).
 * 修改日期 / Modified: 2026-10-10
 * SPDX-FileCopyrightText: 2026 nairain (nairain233)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.shawnsu.healthexport.hook.util

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * 统一脱敏日志入口，不记录业务或隐私数据。
 */
class ModuleLogger(
    private val framework: XposedInterface,
) {
    private val tag: String = "HealthExport"

    fun info(message: String) {
        framework.log(Log.INFO, tag, message)
    }

    fun warn(message: String) {
        framework.log(Log.WARN, tag, message)
    }

    fun warn(message: String, error: Throwable) {
        framework.log(Log.WARN, tag, message, error)
    }
}
