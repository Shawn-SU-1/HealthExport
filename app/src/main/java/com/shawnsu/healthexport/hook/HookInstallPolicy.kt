/*
 * 本文件修改自「华为运动精简」(nairain233/huawei-health-customization, GPL-3.0)。
 * Modified from huawei-health-customization (nairain233, GPL-3.0).
 * 修改日期 / Modified: 2026-10-10
 * SPDX-FileCopyrightText: 2026 nairain (nairain233)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.shawnsu.healthexport.hook

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 安装与派发守卫：每个钩子只安装一次；同一预约在同一进程内只派发一次，
 * 避免导出进行中反复回到前台时并发启动第二次导出。
 */
class HookInstallGate {
    private val attachInstalled = AtomicBoolean()
    private val runtimeInstalled = AtomicBoolean()
    private val foregroundInstalled = AtomicBoolean()
    private val dispatched = AtomicReference<String?>(null)

    fun claimAttach(): Boolean = attachInstalled.compareAndSet(false, true)

    fun claimRuntime(): Boolean = runtimeInstalled.compareAndSet(false, true)

    /** 前台恢复钩子只安装一次。 */
    fun claimForeground(): Boolean = foregroundInstalled.compareAndSet(false, true)

    /** 同一预约只派发一次；已完成预约由导出侧兜底去重。 */
    fun claimRequest(request: String): Boolean {
        while (true) {
            val current = dispatched.get()
            if (current == request) return false
            if (dispatched.compareAndSet(current, request)) return true
        }
    }
}

object HookInstallPolicy {
    const val TARGET_PACKAGE = "com.huawei.health"

    fun acceptsPackage(
        packageName: String?,
        processName: String?,
        isFirstPackage: Boolean,
    ): Boolean = packageName == TARGET_PACKAGE &&
        processName == TARGET_PACKAGE &&
        isFirstPackage

    @Suppress("UNUSED_PARAMETER") // 保留调用接口；版本只用于扫描与缓存身份，不作为守卫。
    fun canInstallRuntime(
        attached: Boolean,
        packageName: String?,
        processName: String?,
        isFirstPackage: Boolean,
        versionName: String?,
        versionCode: Long,
    ): Boolean = attached && acceptsPackage(packageName, processName, isFirstPackage)
}
