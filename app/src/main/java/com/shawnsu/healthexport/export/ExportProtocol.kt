/*
 * 本文件修改自「华为运动精简」(nairain233/huawei-health-customization, GPL-3.0)。
 * Modified from huawei-health-customization (nairain233, GPL-3.0).
 * 修改日期 / Modified: 2026-10-10
 * SPDX-FileCopyrightText: 2026 nairain (nairain233)
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.shawnsu.healthexport.export

import org.json.JSONArray
import org.json.JSONObject

/** 导出协议与数据集清单独立于扫描协议；报告只描述一次导出的进度与结果。 */
object ExportProtocol {
    const val VERSION = 1
    const val REQUEST = "export.request"

    /** 预约时可选的时间范围编码（见 [ExportRange]）；键缺席等价于全部历史，兼容旧预约。 */
    const val RANGE = "export.range"

    /** 目标进程回传动作：进度报告与导出摘要。 */
    const val ACTION_REPORT = "report"
    const val ACTION_SUMMARY = "summary"

    const val AUTHORITY = "com.shawnsu.healthexport.export"

    /** 模块 App 侧报告存储。 */
    const val STORE = "export_reports"

    /** 模块 App 侧摘要存储：与报告分离，便于独立更新。 */
    const val SUMMARY_STORE = "export_summary"
    const val SUMMARY_KEY = "summary"

    /** 摘要文件名后缀：与 Markdown 同目录同名，仅扩展名不同。 */
    const val SUMMARY_SUFFIX = ".summary.json"

    /** 导出进行中超过该时长没有新报告即视为状态待确认（最慢的数据集为全量心率）。 */
    const val STALE_MS = 900_000L

    /**
     * 单个数据集的读取参数。
     * types 为 HiHealthData 类型编码；keys 为 constantsKey（宿主读取时的常量键，可为 null）。
     * 编码在实现时已用宿主真实调用点实证（见《数据读取实证汇总》10.4 表），不在此处再取运行时字典。
     */
    class Dataset(val id: String, val types: IntArray, val keys: Array<String>?)

    /** 用户确认的十类健康数据；顺序即导出文件中的章节顺序。 */
    val datasets: List<Dataset> = listOf(
        Dataset("daily", intArrayOf(40002, 40003, 40004), null),
        // 睡眠：核心分期（44101-44113，分钟）、核心指标（44201-44235）、手机侧兜底（44001-44007，秒）。
        // 逐位对齐实证见《数据读取实证汇总》§3 与 ExportSummary.acceptSleep。
        Dataset(
            "sleep",
            intArrayOf(
                44101, 44102, 44103, 44104, 44105, 44106, 44107, 44108, 44109, 44110,
                44111, 44112, 44113, 44201, 44202, 44203, 44205, 44216, 44217, 44218,
                44235, 44001, 44002, 44003, 44004, 44005, 44006, 44007,
            ),
            null,
        ),
        Dataset("heart_rate", intArrayOf(46019), null),
        // BleConstants.WEIGHT_KEY 的值为 "test"（宿主卡片读取原文如此）。
        Dataset("weight", intArrayOf(10006), arrayOf("test")),
        Dataset("blood_pressure", intArrayOf(10002), null),
        Dataset("workout", intArrayOf(30002), null),
        Dataset("blood_oxygen", intArrayOf(2103, 2107), null),
        Dataset("pressure", intArrayOf(44307), null),
        Dataset("blood_sugar", intArrayOf(10001, 2108), null),
        Dataset("temperature", intArrayOf(2104), null),
    )

    val ids: Set<String> get() = datasets.map { it.id }.toSet()
}

/**
 * 目标进程写回的报告；报告只含进度、计数、输出路径与诊断日志，不含任何健康数据本身。
 * 同一 run 内 done 与 counts 只增不减，sequence 单调递增。
 */
data class ExportReport(
    val request: String,
    val run: String,
    val started: Long,
    val sequence: Int = 0,
    val phase: String = "running",
    val done: Set<String> = emptySet(),
    val failed: Set<String> = emptySet(),
    val counts: Map<String, Int> = emptyMap(),
    val output: String = "",
    val time: Long = System.currentTimeMillis(),
    val error: String = "",
    val trace: String = "",
) {
    val total: Int get() = counts.values.sum()

    fun encode(): String = JSONObject().apply {
        put("protocol", ExportProtocol.VERSION)
        put("request", request)
        put("run", run)
        put("started", started)
        put("sequence", sequence)
        put("phase", phase)
        put("done", JSONArray(done.sorted()))
        put("failed", JSONArray(failed.sorted()))
        put("counts", JSONObject().apply { counts.toSortedMap().forEach { (id, count) -> put(id, count) } })
        put("output", output)
        put("time", time)
        put("error", error)
        put("trace", trace)
    }.toString()

    fun follows(previous: ExportReport?): Boolean = previous == null ||
        (run != previous.run && started > previous.started) ||
        (run == previous.run && started == previous.started && request == previous.request &&
            sequence > previous.sequence && previous.phase == "running" &&
            done.containsAll(previous.done) &&
            previous.counts.all { (id, count) -> (counts[id] ?: 0) >= count })

    companion object {
        fun decode(raw: String): ExportReport {
            require(raw.length <= 32768)
            val json = JSONObject(raw)
            require(json.getInt("protocol") == ExportProtocol.VERSION)
            fun ids(name: String): Set<String> {
                val array = json.getJSONArray(name)
                require(array.length() <= ExportProtocol.ids.size)
                return (0 until array.length()).map { array.getString(it) }.toSet().also {
                    require(ExportProtocol.ids.containsAll(it))
                }
            }
            fun short(name: String, max: Int = 160): String = json.getString(name).also { require(it.length <= max) }
            val countsJson = json.getJSONObject("counts")
            val counts = buildMap {
                val iterator = countsJson.keys()
                while (iterator.hasNext()) {
                    val id = iterator.next()
                    require(ExportProtocol.ids.contains(id))
                    val count = countsJson.getInt(id)
                    require(count >= 0)
                    put(id, count)
                }
            }
            // 旧版本报告没有 trace 键；兼容读取，新报告上限 16KB。
            val trace = json.optString("trace", "").also { require(it.length <= 16_384) }
            return ExportReport(
                short("request"), short("run"), json.getLong("started"), json.getInt("sequence"),
                short("phase"), ids("done"), ids("failed"), counts, short("output", 512),
                json.getLong("time"), short("error"), trace,
            ).also {
                require(it.phase in setOf("running", "complete", "failed"))
                require(it.error in setOf("", "export_error", "write_error"))
                require(it.done.containsAll(it.failed))
                require(it.sequence >= 0 && it.started > 0 && it.time >= it.started)
                require(it.request.matches(Regex("[0-9a-f-]{36}")))
                require(it.run.matches(Regex("[0-9a-f-]{36}")))
                require(it.phase != "running" || (it.error.isEmpty() && it.output.isEmpty()))
                require(it.phase != "complete" || (it.error.isEmpty() && it.done == ExportProtocol.ids && it.output.isNotEmpty()))
                require(it.phase != "failed" || it.error.isNotEmpty())
            }
        }
    }
}
