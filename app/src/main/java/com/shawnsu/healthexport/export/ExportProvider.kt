/*
 * 本文件修改自「华为运动精简」(nairain233/huawei-health-customization, GPL-3.0)。
 * Modified from huawei-health-customization (nairain233, GPL-3.0).
 * 修改日期 / Modified: 2026-10-10
 * SPDX-FileCopyrightText: 2026 nairain (nairain233)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.shawnsu.healthexport.export

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import androidx.core.net.toUri

/** 仅接收目标 UID 的脱敏报告与统计摘要；不暴露配置写入、文件或查询接口。 */
class ExportProvider : ContentProvider() {
    override fun onCreate() = true

    @Synchronized
    @SuppressLint("UseKtx") // 必须检查 commit 返回值，失败不能确认报告已保存。
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = requireNotNull(context)
        val uid = Binder.getCallingUid()
        require(ExportAdmission.allows(uid, Process.myUid(), ctx.packageManager.getPackagesForUid(uid)
            .orEmpty().toList())) { "Caller not allowed" }
        require(extras == null)
        return when (method) {
            ExportProtocol.ACTION_REPORT -> acceptReport(ctx, requireNotNull(arg))
            ExportProtocol.ACTION_SUMMARY -> acceptSummary(ctx, requireNotNull(arg))
            else -> throw IllegalArgumentException("Unsupported method")
        }
    }

    private fun acceptReport(ctx: android.content.Context, arg: String): Bundle {
        val report = ExportReport.decode(arg)
        require(report.time <= System.currentTimeMillis() + 30_000)
        val prefs = ctx.getSharedPreferences(ExportProtocol.STORE, 0)
        val previous = prefs.getString("report", null)?.let { runCatching { ExportReport.decode(it) }.getOrNull() }
        val accepted = report.follows(previous)
        if (accepted) {
            check(prefs.edit().putString("report", report.encode()).commit())
            ctx.contentResolver.notifyChange("content://${ExportProtocol.AUTHORITY}".toUri(), null)
        }
        return Bundle().apply { putBoolean("accepted", accepted) }
    }

    /** 摘要按导出时刻单调接纳；不接受比已存摘要更旧的导出，避免旧结果覆盖新结果。 */
    private fun acceptSummary(ctx: android.content.Context, arg: String): Bundle {
        val summary = ExportSummary.decode(arg)
        require(summary.exportedAt <= System.currentTimeMillis() + 30_000)
        val prefs = ctx.getSharedPreferences(ExportProtocol.SUMMARY_STORE, 0)
        val previous = prefs.getString(ExportProtocol.SUMMARY_KEY, null)
            ?.let { runCatching { ExportSummary.decode(it) }.getOrNull() }
        val accepted = previous == null || summary.exportedAt >= previous.exportedAt
        if (accepted) {
            check(prefs.edit().putString(ExportProtocol.SUMMARY_KEY, summary.encode()).commit())
            ctx.contentResolver.notifyChange("content://${ExportProtocol.AUTHORITY}".toUri(), null)
        }
        return Bundle().apply { putBoolean("accepted", accepted) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
