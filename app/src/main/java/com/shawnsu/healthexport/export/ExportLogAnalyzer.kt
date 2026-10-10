/*
 * 本文件修改自「华为运动精简」(nairain233/huawei-health-customization, GPL-3.0)。
 * Modified from huawei-health-customization (nairain233, GPL-3.0).
 * 修改日期 / Modified: 2026-10-10
 * SPDX-FileCopyrightText: 2026 nairain (nairain233)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.shawnsu.healthexport.export

/**
 * 导出日志分析：把「当前预约 + 最近报告」组合翻译为用户可读的结论与建议。
 * 纯逻辑（无 Android 依赖），由模块 App 渲染文案，便于单元测试。
 */
internal object ExportLogAnalyzer {
    /** 一条分析结论；字段仅为 UI 拼接文案所需的参数。 */
    sealed interface Hint {
        /** 从未发起过导出（无预约、无报告）。 */
        data object NoRecord : Hint

        /** 已预约但运动健康尚未执行。 */
        data class WaitingFirst(val request: String) : Hint

        /** 新预约尚未执行；展示的是上一次导出的日志。 */
        data class WaitingNext(val request: String, val previous: String) : Hint

        /** 该预约正在导出。 */
        data class Running(val done: Int, val total: Int) : Hint

        /** 该预约长时间无进度，可能已中断。 */
        data object Stale : Hint

        /** 该预约已完成。 */
        data class Complete(val total: Int) : Hint

        /** 该预约已完成但部分数据集读取失败。 */
        data class CompletePartial(val total: Int, val failed: Set<String>) : Hint

        /** 该预约导出失败；error 为协议错误码。 */
        data class Failed(val error: String) : Hint

        /** 无当前预约；展示的是上一次导出的日志。 */
        data class PreviousLog(val previous: String) : Hint
    }

    fun hints(report: ExportReport?, request: String, uncertain: Boolean): List<Hint> = buildList {
        if (request.isEmpty()) {
            add(if (report == null) Hint.NoRecord else Hint.PreviousLog(short(report.request)))
            return@buildList
        }
        if (report == null) {
            add(Hint.WaitingFirst(short(request)))
            return@buildList
        }
        if (report.request != request) {
            add(Hint.WaitingNext(short(request), short(report.request)))
            return@buildList
        }
        when (report.phase) {
            "running" -> add(if (uncertain) Hint.Stale else Hint.Running(report.done.size, ExportProtocol.datasets.size))
            "failed" -> add(Hint.Failed(report.error))
            // 解码已保证 phase 属于 running/complete/failed，其余按完成处理。
            else -> add(
                if (report.failed.isEmpty()) Hint.Complete(report.total)
                else Hint.CompletePartial(report.total, report.failed)
            )
        }
    }

    private fun short(id: String): String = id.take(8)
}
