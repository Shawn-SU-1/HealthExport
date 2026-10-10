package com.shawnsu.healthexport.export

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.AtomicFile
import android.util.SparseArray
import androidx.core.net.toUri
import com.shawnsu.healthexport.hook.util.ModuleLogger
import java.io.File
import java.io.Writer
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 全部反射读取、Markdown 生成与报告 IPC 均由一次性工作线程执行。
 * 回调语义：应用侧监听器单次回调；以 SparseArray 出现判成败，不信任 int 参数；
 * 数据按 HiHealthData 自身 getType() 分组（详见《回调语义与ExportRuntime设计.md》）。
 * 过程日志（Trace）随每次报告发布上行，供模块 App 展示与分析。
 */
internal object ExportRuntime {
    private const val DIR_NAME = "huawei_health_export"
    private const val DRAIN_MS = 2_000L
    private const val TIMEOUT_FIRST_MS = 120_000L
    private const val TIMEOUT_MS = 90_000L
    private const val CELL_LIMIT = 4000
    private const val MIN_TIME_MS = 946_684_800_000L
    private val TIME_KEYS = setOf("start_time", "end_time", "create_time", "modified_time")
    private val PRIORITY_COLUMNS = listOf("start_time", "end_time", "point_value", "point_unit", "sub_type_id")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val FILE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val datasetNames = mapOf(
        "daily" to "日常活动",
        "sleep" to "睡眠",
        "heart_rate" to "心率",
        "weight" to "体重体脂",
        "blood_pressure" to "血压",
        "workout" to "运动记录",
        "blood_oxygen" to "血氧",
        "pressure" to "压力",
        "blood_sugar" to "血糖",
        "temperature" to "体温",
    )

    fun start(app: Application, request: String, logger: ModuleLogger) {
        Thread({
            try { execute(app, request, logger) }
            catch (error: Throwable) { logger.warn("Export worker unavailable: ${error.javaClass.simpleName}") }
        }, "huawei-health-export").start()
    }

    private fun execute(app: Application, request: String, logger: ModuleLogger) {
        val dir = File(app.filesDir, DIR_NAME)
        val trace = Trace()
        var report = ExportReport(request = request, run = UUID.randomUUID().toString(), started = System.currentTimeMillis())
        val authority = "content://${ExportProtocol.AUTHORITY}".toUri()
        fun publish() {
            runCatching {
                val encoded = report.copy(trace = trace.dump()).encode()
                write(File(dir, "report.json"), encoded)
                app.contentResolver.call(authority, "report", encoded, null)
            }.onFailure { logger.warn("Export report deferred: ${it.javaClass.simpleName}") }
        }
        try {
            check(dir.isDirectory || dir.mkdirs())
            // 重交上一份报告（供模块侧恢复），同 request 已完成则跳过，避免重复导出。
            var previous: ExportReport? = null
            val reportFile = File(dir, "report.json")
            if (reportFile.isFile) runCatching {
                val pending = reportFile.readText()
                previous = ExportReport.decode(pending)
                app.contentResolver.call(authority, "report", pending, null)
            }.onFailure { logger.warn("Previous export report unavailable: ${it.javaClass.simpleName}") }
            if (request.isEmpty()) {
                logger.info("Export idle: no request")
                return
            }
            if (previous?.request == request && previous.phase == "complete") {
                logger.info("Export skipped: already exported")
                return
            }
            trace.add("启动导出，预约 ${request.take(8)}")
            publish()
            val begin = SystemClock.elapsedRealtime()
            val bridge = HiHealthBridge(app)
            trace.add("接口就绪（${SystemClock.elapsedRealtime() - begin}ms）")
            val sections = ArrayList<SectionData>(ExportProtocol.datasets.size)
            var done = report.done
            var failed = report.failed
            var counts = report.counts
            ExportProtocol.datasets.forEachIndexed { index, dataset ->
                val started = SystemClock.elapsedRealtime()
                trace.add("${dataset.id}：读取中")
                val section = try {
                    val outcome = readDataset(bridge, dataset, if (index == 0) TIMEOUT_FIRST_MS else TIMEOUT_MS)
                    trace.add("${dataset.id}：${outcome.items.size} 行（${SystemClock.elapsedRealtime() - started}ms，回调 ${outcome.callbacks} 次）")
                    SectionData(dataset.id, dataset.types, outcome.items, null)
                } catch (error: Throwable) {
                    val reason = reasonOf(error)
                    trace.add("${dataset.id}：失败（${reasonText(reason)}，${SystemClock.elapsedRealtime() - started}ms）")
                    logger.warn("Export ${dataset.id} failed: $reason")
                    SectionData(dataset.id, dataset.types, emptyList(), reason)
                }
                sections += section
                done = done + dataset.id
                if (section.reason == null) counts = counts + (dataset.id to section.items.size)
                else failed = failed + dataset.id
                report = report.copy(done = done, failed = failed, counts = counts, sequence = report.sequence + 1, time = System.currentTimeMillis())
                publish()
                logger.info("Export ${dataset.id}: ${if (section.reason == null) "${section.items.size} rows" else "failed"}, ${SystemClock.elapsedRealtime() - started}ms")
            }
            if (failed.size >= ExportProtocol.datasets.size) {
                trace.add("终止：全部数据集读取失败")
                report = report.copy(phase = "failed", error = "export_error", sequence = report.sequence + 1, time = System.currentTimeMillis())
                publish()
                logger.warn("Export stopped: no dataset available")
                return
            }
            val filename = "huawei-health-export-${FILE_FORMAT.format(Instant.ofEpochMilli(report.started).atZone(ZoneId.systemDefault()))}.md"
            val output = try {
                saveMarkdown(app, filename) { writer -> emitMarkdown(writer, bridge, sections, report.started) }
            } catch (error: Throwable) {
                trace.add("写出失败：${error.javaClass.simpleName}")
                report = report.copy(phase = "failed", error = "write_error", sequence = report.sequence + 1, time = System.currentTimeMillis())
                publish()
                logger.warn("Export write failed: ${error.javaClass.simpleName}")
                return
            }
            trace.add("写出：$output")
            trace.add("完成：共 ${counts.values.sum()} 行，${failed.size} 个数据集失败，耗时 ${SystemClock.elapsedRealtime() - begin}ms")
            report = report.copy(phase = "complete", output = output, sequence = report.sequence + 1, time = System.currentTimeMillis())
            publish()
            logger.info("Export finished: ${counts.values.sum()} rows, ${failed.size} failed, ${SystemClock.elapsedRealtime() - begin}ms -> $output")
        } catch (error: Throwable) {
            trace.add("异常：${error.javaClass.simpleName}")
            report = report.copy(phase = "failed", error = "export_error", sequence = report.sequence + 1, time = System.currentTimeMillis())
            publish()
            logger.warn("Export skipped: ${error.javaClass.simpleName}")
        }
    }

    /** 反射读取一个数据集；成功返回原始 HiHealthData 引用列表（共享宿主对象，不复制）与回调次数。 */
    private fun readDataset(bridge: HiHealthBridge, dataset: ExportProtocol.Dataset, timeoutMs: Long): ReadOutcome {
        val option = bridge.optionConstructor.newInstance()
        // 数组参数显式包一层，避免 Kotlin 对 Java vararg 的展开歧义。
        bridge.setType.invoke(option, *arrayOf<Any?>(dataset.types))
        dataset.keys?.let { bridge.setConstantsKey.invoke(option, *arrayOf<Any?>(it)) }
        bridge.setStartTime.invoke(option, 0L)
        bridge.setEndTime.invoke(option, System.currentTimeMillis())
        bridge.setCount.invoke(option, 0)
        bridge.setSortOrder.invoke(option, 1)

        val items = Collections.synchronizedList(mutableListOf<Any>())
        val sawResult = AtomicBoolean(false)
        val callbacks = AtomicInteger(0)
        val first = CountDownLatch(1)
        fun absorb(value: Any?) {
            callbacks.incrementAndGet()
            if (value is SparseArray<*>) {
                for (index in 0 until value.size()) {
                    val list = value.valueAt(index) as? List<*> ?: continue
                    for (item in list) if (item != null) items.add(item)
                }
                sawResult.set(true)
            }
            first.countDown()
        }
        val listener = Proxy.newProxyInstance(bridge.loader, arrayOf(bridge.listenerClass)) { proxy, method, args ->
            when (method.name) {
                "onResult" -> { runCatching { absorb(args?.getOrNull(0)) }; null }
                "onResultIntent" -> { runCatching { absorb(args?.getOrNull(1)) }; null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "ExportRuntime"
                else -> null
            }
        }
        bridge.readMethod.invoke(bridge.api, option, listener)
        if (!first.await(timeoutMs, TimeUnit.MILLISECONDS)) throw ReadFailure("timeout")
        // 兜底排空窗口：理论模型下单次回调；若宿主分次送达，窗口内合并。
        Thread.sleep(DRAIN_MS)
        if (!sawResult.get()) throw ReadFailure("no_result")
        return ReadOutcome(items, callbacks.get())
    }

    private fun emitMarkdown(writer: Writer, bridge: HiHealthBridge, sections: List<SectionData>, started: Long) {
        val ok = sections.count { it.reason == null }
        writer.appendLine("# 华为运动健康数据导出")
        writer.appendLine()
        writer.appendLine("- 导出时间：${formatTime(started)}")
        writer.appendLine("- 时间范围：全部历史（startTime=0）")
        writer.appendLine("- 数据集：${sections.size} 个，成功 $ok 个，失败 ${sections.size - ok} 个")
        writer.appendLine()
        writer.appendLine("| 数据集 | 类型编码 | 条数 |")
        writer.appendLine("| --- | --- | --- |")
        for (section in sections) {
            val count = if (section.reason == null) section.items.size.toString() else "读取失败"
            writer.appendLine("| ${nameOf(section.id)}（${section.id}） | ${section.types.joinToString(",")} | $count |")
        }
        writer.appendLine()
        for (section in sections) {
            writer.appendLine("## ${nameOf(section.id)}（${section.id}）")
            writer.appendLine()
            writer.appendLine("类型：${section.types.joinToString(",")}")
            writer.appendLine()
            when {
                section.reason != null -> writer.appendLine("读取失败：${section.reason}")
                section.items.isEmpty() -> writer.appendLine("（无数据）")
                else -> emitTable(writer, bridge, section)
            }
            writer.appendLine()
        }
    }

    /** 两遍式流式输出：第一遍收集列集合与排序键，第二遍按 start_time 升序逐行写出。 */
    private fun emitTable(writer: Writer, bridge: HiHealthBridge, section: SectionData) {
        val items = section.items
        val count = items.size
        val starts = LongArray(count)
        val types = IntArray(count)
        val present = LinkedHashSet<String>()
        var hasDevice = false
        var hasOwner = false
        for (index in 0 until count) {
            val item = items[index]
            val holder = runCatching { bridge.getValueHolder.invoke(item) as? ContentValues }.getOrNull()
            if (holder != null) present.addAll(holder.keySet())
            starts[index] = (holder?.get("start_time") as? Number)?.toLong() ?: 0L
            types[index] = runCatching { bridge.getType.invoke(item) as? Int }.getOrNull() ?: 0
            if (!hasDevice) hasDevice = runCatching { !(bridge.getDeviceUuid.invoke(item) as? String).isNullOrEmpty() }.getOrDefault(false)
            if (!hasOwner) hasOwner = runCatching { ((bridge.getOwnerId.invoke(item) as? Int) ?: 0) != 0 }.getOrDefault(false)
        }
        val priority = PRIORITY_COLUMNS.filter { it in present }
        val extras = present.filter { it !in PRIORITY_COLUMNS }.sorted()
        val columns = ArrayList<String>(priority.size + extras.size + 3)
        columns += "type"
        columns += priority
        columns += extras
        if (hasDevice && "device_uuid" !in present) columns += "device_uuid"
        if (hasOwner && "owner_id" !in present) columns += "owner_id"
        writer.appendLine("| " + columns.joinToString(" | ") + " |")
        writer.appendLine("| " + columns.joinToString(" | ") { "---" } + " |")
        val order = (0 until count).sortedBy { starts[it] }
        for (position in order) {
            val item = items[position]
            val cells = runCatching { cellsOf(bridge, item) }.getOrNull() ?: emptyMap<String, String>()
            val line = StringBuilder(160)
            line.append("| ")
            columns.forEachIndexed { columnIndex, column ->
                if (columnIndex > 0) line.append(" | ")
                when (column) {
                    "type" -> line.append(types[position])
                    "device_uuid" -> line.append(runCatching { bridge.getDeviceUuid.invoke(item) as? String }.getOrNull().orEmpty())
                    "owner_id" -> line.append(runCatching { bridge.getOwnerId.invoke(item) as? Int }.getOrNull() ?: 0)
                    else -> line.append(cells[column].orEmpty())
                }
            }
            line.append(" |")
            writer.appendLine(line)
        }
    }

    private fun cellsOf(bridge: HiHealthBridge, item: Any): Map<String, String> {
        val holder = bridge.getValueHolder.invoke(item) as? ContentValues ?: return emptyMap()
        val cells = LinkedHashMap<String, String>(holder.size())
        for (key in holder.keySet()) cells[key] = formatCell(key, holder.get(key))
        return cells
    }

    private fun formatCell(key: String, value: Any?): String {
        val text = when {
            value == null -> ""
            key in TIME_KEYS && value is Number && value.toLong() >= MIN_TIME_MS -> formatTime(value.toLong())
            value is ByteArray -> "[二进制 ${value.size} 字节]"
            else -> value.toString()
        }
        val escaped = text.replace('\n', ' ').replace('\r', ' ').replace("|", "\\|")
        return if (escaped.length > CELL_LIMIT) escaped.take(CELL_LIMIT) + "…（已截断，原长 ${escaped.length}）" else escaped
    }

    private fun formatTime(ms: Long): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())) + " ($ms)"

    /** 写出链：公共 Download 目录 → 应用外部目录 → 内部 filesDir，逐级回退。 */
    private fun saveMarkdown(app: Application, filename: String, body: (Writer) -> Unit): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloads(app, filename, body)?.let { return it }
        }
        val external = runCatching {
            val root = app.getExternalFilesDir(null) ?: throw IllegalStateException("external unavailable")
            val dir = File(root, "export")
            check(dir.isDirectory || dir.mkdirs())
            val file = File(dir, filename)
            file.bufferedWriter().use { body(it) }
            file.absolutePath
        }.getOrNull()
        if (external != null) return external
        val dir = File(app.filesDir, "export")
        check(dir.isDirectory || dir.mkdirs())
        val file = File(dir, filename)
        file.bufferedWriter().use { body(it) }
        return file.absolutePath
    }

    private fun saveToDownloads(app: Application, filename: String, body: (Writer) -> Unit): String? {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/HuaweiHealthExport")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull() ?: return null
        return try {
            val stream = resolver.openOutputStream(uri) ?: throw IllegalStateException("no stream")
            stream.bufferedWriter(Charsets.UTF_8).use { body(it) }
            val finished = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, finished, null, null)
            "Download/HuaweiHealthExport/$filename"
        } catch (_: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun reasonOf(error: Throwable): String = when (error) {
        is ReadFailure -> error.reason
        is InvocationTargetException -> error.cause?.let { reasonOf(it) } ?: "invoke_error"
        else -> error.javaClass.simpleName
    }

    /** 失败原因转诊断日志文案；无法识别的保留原始 token。 */
    private fun reasonText(reason: String): String = when (reason) {
        "timeout" -> "超时"
        "no_result" -> "无数据返回"
        else -> reason
    }

    private fun nameOf(id: String): String = datasetNames[id] ?: id

    private fun write(file: File, content: String) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(content.toByteArray())
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private class SectionData(val id: String, val types: IntArray, val items: List<Any>, val reason: String?)

    private class ReadFailure(val reason: String) : Exception(reason)

    private class ReadOutcome(val items: List<Any>, val callbacks: Int)

    /** 导出过程诊断日志：相对时间戳行；超限时丢弃最旧行，随每次报告发布上行。 */
    private class Trace {
        private val limit = 8_000
        private val lines = ArrayDeque<String>()
        private var chars = 0
        private val start = SystemClock.elapsedRealtime()

        @Synchronized
        fun add(text: String) {
            val tenths = (SystemClock.elapsedRealtime() - start) / 100
            val line = "+${tenths / 10}.${tenths % 10}s $text"
            lines.addLast(line)
            chars += line.length + 1
            while (chars > limit && lines.size > 1) chars -= lines.removeFirst().length + 1
        }

        @Synchronized
        fun dump(): String = lines.joinToString("\n")
    }

    /** 目标进程内的宿主 API 反射句柄；相关类均为明文命名（见逆向报告 §10.3）。 */
    private class HiHealthBridge(app: Application) {
        val loader: ClassLoader = app.classLoader
        val api: Any
        val optionClass: Class<*>
        val optionConstructor: Constructor<*>
        val listenerClass: Class<*>
        val readMethod: Method
        val setType: Method
        val setConstantsKey: Method
        val setStartTime: Method
        val setEndTime: Method
        val setCount: Method
        val setSortOrder: Method
        val getType: Method
        val getDeviceUuid: Method
        val getOwnerId: Method
        val getValueHolder: Method

        init {
            val apiClass = Class.forName("com.huawei.hihealth.api.HiHealthApi", false, loader)
            val managerClass = Class.forName("com.huawei.hihealth.api.HiHealthManager", false, loader)
            api = requireNotNull(managerClass.getMethod("a", Context::class.java).invoke(null, app))
            optionClass = Class.forName("com.huawei.hihealth.HiDataReadOption", false, loader)
            optionConstructor = optionClass.getConstructor()
            listenerClass = Class.forName("com.huawei.hihealth.data.listener.HiDataReadResultListener", false, loader)
            readMethod = apiClass.getMethod("readHiHealthData", optionClass, listenerClass)
            setType = optionClass.getMethod("setType", IntArray::class.java)
            setConstantsKey = optionClass.getMethod("setConstantsKey", Array<String>::class.java)
            setStartTime = optionClass.getMethod("setStartTime", java.lang.Long.TYPE)
            setEndTime = optionClass.getMethod("setEndTime", java.lang.Long.TYPE)
            setCount = optionClass.getMethod("setCount", Integer.TYPE)
            setSortOrder = optionClass.getMethod("setSortOrder", Integer.TYPE)
            val dataClass = Class.forName("com.huawei.hihealth.HiHealthData", false, loader)
            getType = dataClass.getMethod("getType")
            getDeviceUuid = dataClass.getMethod("getDeviceUuid")
            getOwnerId = dataClass.getMethod("getOwnerId")
            getValueHolder = dataClass.getMethod("getValueHolder")
        }
    }
}
