package com.shawnsu.healthexport.export

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 单次导出的结构化摘要：由目标进程在写 Markdown 的同时同源计算，随报告链路回传模块 App，
 * 供「数据分析」界面直接渲染，无需再解析 Markdown 正文。
 * 字段单位：距离米、时长毫秒、热量千卡（原始为卡，已 ÷1000）、睡眠分钟。
 * 纯逻辑实现（仅依赖 org.json），无 Android 依赖，便于单元测试。
 */
internal data class ExportSummary(
    val exportedAt: Long,
    val rangeCode: String,
    val rangeLabel: String,
    val startMs: Long,
    val endMs: Long,
    val counts: Map<String, Int>,
    val failed: Set<String>,
    val daily: Daily,
    val sleep: Sleep,
    val heartRate: Distribution,
    val bloodOxygen: Oxygen,
    val pressure: Distribution,
    val weight: List<WeightPoint>,
    val workout: Workout,
) {
    data class Daily(
        val records: Int,
        val days: Int,
        val stepsTotal: Long,
        val distanceM: Long,
        val calories: Long,
        val months: List<MonthActivity>,
    ) {
        val stepsPerDay: Long get() = if (days == 0) 0L else stepsTotal / days
    }

    data class MonthActivity(val month: String, val steps: Long, val days: Int, val distanceM: Long, val calories: Long)

    data class Sleep(
        val records: Int,
        val nights: Int,
        val minutes: Long,
        val months: List<MonthSleep>,
        val detail: SleepDetail,
    ) {
        val minutesPerNight: Long get() = if (nights == 0) 0L else minutes / nights
    }

    data class MonthSleep(val month: String, val minutes: Long, val nights: Int)

    /**
     * 睡眠分期与睡眠质量指标。时长已统一换算为分钟。
     * 分期口径逐夜择一：AASM 新口径（44111-44113，sleep_version=5）→ 旧口径（44101-44103）
     * → 手机侧兜底（44001-44003，原始为秒）；同一夜只取一档，避免重复计入。
     * 账号未采集到分期时 stagedNights 为 0，界面据此提示。
     */
    data class SleepDetail(
        val deepMinutes: Long,
        val lightMinutes: Long,
        val remMinutes: Long,
        val awakeMinutes: Long,
        val stagedNights: Int,
        val wakeCount: Long,
        val scoreSum: Long,
        val scoreNights: Int,
        val latencySum: Long,
        val latencyNights: Int,
        val napMinutes: Long,
        val lastFallAsleep: Long?,
        val lastWakeUp: Long?,
        val lastBedTime: Long?,
        val lastWakeTime: Long?,
        val months: List<MonthSleepDetail>,
    ) {
        /** 有效睡眠时长（不含清醒），用于深睡占比。 */
        val sleepMinutes: Long get() = deepMinutes + lightMinutes + remMinutes

        /** 分期总时长（含清醒），用于占比条与各分期百分比。 */
        val stageMinutes: Long get() = sleepMinutes + awakeMinutes

        val hasStages: Boolean get() = stagedNights > 0

        val deepPerNight: Long get() = if (stagedNights == 0) 0L else deepMinutes / stagedNights

        val deepPercent: Double get() = percentOf(deepMinutes)

        fun percentOf(minutes: Long): Double =
            if (stageMinutes == 0L) 0.0 else minutes * 100.0 / stageMinutes

        /** 未记录睡眠得分时返回 null，界面不显示该行。 */
        val scoreAvg: Double? get() = if (scoreNights == 0) null else scoreSum.toDouble() / scoreNights

        /** 未记录入睡延迟时返回 null。 */
        val latencyAvg: Double? get() = if (latencyNights == 0) null else latencySum.toDouble() / latencyNights

        /** 平均每夜清醒次数；核心记录优先，缺失时取手机侧记录。 */
        val wakePerNight: Double? get() = if (stagedNights == 0) null else wakeCount.toDouble() / stagedNights
    }

    data class MonthSleepDetail(
        val month: String,
        val deepMinutes: Long,
        val lightMinutes: Long,
        val remMinutes: Long,
        val awakeMinutes: Long,
    )

    data class Distribution(val records: Int, val avg: Double, val min: Int, val max: Int, val buckets: List<Bucket>)

    data class Bucket(val from: Int, val count: Int)

    data class Oxygen(val records: Int, val series: List<Series>) {
        data class Series(val type: Int, val records: Int, val avg: Double, val min: Int, val max: Int)
    }

    data class WeightPoint(val time: Long, val weightKg: Double?, val bodyFatPercent: Double?, val heightCm: Double?)

    data class Workout(
        val records: Int,
        val distanceM: Long,
        val durationMs: Long,
        val calories: Long,
        val types: List<WorkoutType>,
    )

    data class WorkoutType(
        val id: Int,
        val records: Int,
        val distanceM: Long,
        val durationMs: Long,
        val calories: Long,
        val avgHeartRate: Double?,
    )

    fun encode(): String = JSONObject().apply {
        put("schema", SCHEMA)
        put("exportedAt", exportedAt)
        put("rangeCode", rangeCode)
        put("rangeLabel", rangeLabel)
        put("startMs", startMs)
        put("endMs", endMs)
        put("counts", JSONObject().apply { counts.toSortedMap().forEach { (id, value) -> put(id, value) } })
        put("failed", JSONArray(failed.sorted()))
        put("daily", daily.toJson())
        put("sleep", sleep.toJson())
        put("heartRate", heartRate.toJson())
        put("bloodOxygen", bloodOxygen.toJson())
        put("pressure", pressure.toJson())
        put("weight", JSONArray().apply { weight.forEach { put(it.toJson()) } })
        put("workout", workout.toJson())
    }.toString()

    companion object {
        const val SCHEMA = 2
        const val MAX_RAW = 256 * 1024

        fun decode(raw: String): ExportSummary {
            require(raw.length <= MAX_RAW)
            val json = JSONObject(raw)
            require(json.getInt("schema") == SCHEMA)
            fun distribution(node: JSONObject): Distribution = Distribution(
                node.getInt("records"), node.getDouble("avg"), node.getInt("min"), node.getInt("max"),
                node.getJSONArray("buckets").let { array ->
                    (0 until array.length()).map { array.getJSONObject(it).let { Bucket(it.getInt("from"), it.getInt("count")) } }
                },
            )
            return ExportSummary(
                exportedAt = json.getLong("exportedAt"),
                rangeCode = json.getString("rangeCode"),
                rangeLabel = json.getString("rangeLabel"),
                startMs = json.getLong("startMs"),
                endMs = json.getLong("endMs"),
                counts = json.getJSONObject("counts").let { node ->
                    buildMap { node.keys().forEach { put(it, node.getInt(it)) } }
                },
                failed = json.getJSONArray("failed").let { array ->
                    (0 until array.length()).map { array.getString(it) }.toSet()
                },
                daily = json.getJSONObject("daily").let { node ->
                    Daily(
                        node.getInt("records"), node.getInt("days"), node.getLong("stepsTotal"),
                        node.getLong("distanceM"), node.getLong("calories"),
                        node.getJSONArray("months").let { array ->
                            (0 until array.length()).map { index ->
                                val item = array.getJSONObject(index)
                                MonthActivity(item.getString("month"), item.getLong("steps"), item.getInt("days"),
                                    item.getLong("distanceM"), item.getLong("calories"))
                            }
                        },
                    )
                },
                sleep = json.getJSONObject("sleep").let { node ->
                    Sleep(
                        node.getInt("records"), node.getInt("nights"), node.getLong("minutes"),
                        node.getJSONArray("months").let { array ->
                            (0 until array.length()).map { index ->
                                val item = array.getJSONObject(index)
                                MonthSleep(item.getString("month"), item.getLong("minutes"), item.getInt("nights"))
                            }
                        },
                        node.getJSONObject("detail").let { detail ->
                            SleepDetail(
                                deepMinutes = detail.getLong("deepMinutes"),
                                lightMinutes = detail.getLong("lightMinutes"),
                                remMinutes = detail.getLong("remMinutes"),
                                awakeMinutes = detail.getLong("awakeMinutes"),
                                stagedNights = detail.getInt("stagedNights"),
                                wakeCount = detail.getLong("wakeCount"),
                                scoreSum = detail.getLong("scoreSum"),
                                scoreNights = detail.getInt("scoreNights"),
                                latencySum = detail.getLong("latencySum"),
                                latencyNights = detail.getInt("latencyNights"),
                                napMinutes = detail.getLong("napMinutes"),
                                lastFallAsleep = detail.optionalLong("lastFallAsleep"),
                                lastWakeUp = detail.optionalLong("lastWakeUp"),
                                lastBedTime = detail.optionalLong("lastBedTime"),
                                lastWakeTime = detail.optionalLong("lastWakeTime"),
                                months = detail.getJSONArray("months").let { array ->
                                    (0 until array.length()).map { index ->
                                        val item = array.getJSONObject(index)
                                        MonthSleepDetail(
                                            item.getString("month"), item.getLong("deepMinutes"),
                                            item.getLong("lightMinutes"), item.getLong("remMinutes"),
                                            item.getLong("awakeMinutes"),
                                        )
                                    }
                                },
                            )
                        },
                    )
                },
                heartRate = distribution(json.getJSONObject("heartRate")),
                bloodOxygen = json.getJSONObject("bloodOxygen").let { node ->
                    Oxygen(
                        node.getInt("records"),
                        node.getJSONArray("series").let { array ->
                            (0 until array.length()).map { index ->
                                val item = array.getJSONObject(index)
                                Oxygen.Series(item.getInt("type"), item.getInt("records"), item.getDouble("avg"),
                                    item.getInt("min"), item.getInt("max"))
                            }
                        },
                    )
                },
                pressure = distribution(json.getJSONObject("pressure")),
                weight = json.getJSONArray("weight").let { array ->
                    (0 until array.length()).map { index ->
                        val item = array.getJSONObject(index)
                        WeightPoint(item.getLong("time"), item.optDouble("weightKg").takeIf { !it.isNaN() },
                            item.optDouble("bodyFatPercent").takeIf { !it.isNaN() },
                            item.optDouble("heightCm").takeIf { !it.isNaN() })
                    }
                },
                workout = json.getJSONObject("workout").let { node ->
                    Workout(
                        node.getInt("records"), node.getLong("distanceM"), node.getLong("durationMs"),
                        node.getLong("calories"),
                        node.getJSONArray("types").let { array ->
                            (0 until array.length()).map { index ->
                                val item = array.getJSONObject(index)
                                val heart = item.optDouble("avgHeartRate", Double.NaN)
                                WorkoutType(item.getInt("id"), item.getInt("records"), item.getLong("distanceM"),
                                    item.getLong("durationMs"), item.getLong("calories"),
                                    if (heart.isNaN()) null else heart)
                            }
                        },
                    )
                },
            )
        }
    }
}

private fun ExportSummary.Daily.toJson(): JSONObject = JSONObject().apply {
    put("records", records)
    put("days", days)
    put("stepsTotal", stepsTotal)
    put("distanceM", distanceM)
    put("calories", calories)
    put("months", JSONArray().apply {
        months.forEach {
            put(JSONObject().apply {
                put("month", it.month); put("steps", it.steps); put("days", it.days)
                put("distanceM", it.distanceM); put("calories", it.calories)
            })
        }
    })
}

private fun ExportSummary.Sleep.toJson(): JSONObject = JSONObject().apply {
    put("records", records)
    put("nights", nights)
    put("minutes", minutes)
    put("months", JSONArray().apply {
        months.forEach {
            put(JSONObject().apply { put("month", it.month); put("minutes", it.minutes); put("nights", it.nights) })
        }
    })
    put("detail", detail.toJson())
}

private fun ExportSummary.SleepDetail.toJson(): JSONObject = JSONObject().apply {
    put("deepMinutes", deepMinutes)
    put("lightMinutes", lightMinutes)
    put("remMinutes", remMinutes)
    put("awakeMinutes", awakeMinutes)
    put("stagedNights", stagedNights)
    put("wakeCount", wakeCount)
    put("scoreSum", scoreSum)
    put("scoreNights", scoreNights)
    put("latencySum", latencySum)
    put("latencyNights", latencyNights)
    put("napMinutes", napMinutes)
    lastFallAsleep?.let { put("lastFallAsleep", it) }
    lastWakeUp?.let { put("lastWakeUp", it) }
    lastBedTime?.let { put("lastBedTime", it) }
    lastWakeTime?.let { put("lastWakeTime", it) }
    put("months", JSONArray().apply {
        months.forEach {
            put(JSONObject().apply {
                put("month", it.month); put("deepMinutes", it.deepMinutes)
                put("lightMinutes", it.lightMinutes); put("remMinutes", it.remMinutes)
                put("awakeMinutes", it.awakeMinutes)
            })
        }
    })
}

private fun JSONObject.optionalLong(key: String): Long? = if (has(key)) getLong(key) else null

private fun ExportSummary.Distribution.toJson(): JSONObject = JSONObject().apply {
    put("records", records)
    put("avg", avg)
    put("min", min)
    put("max", max)
    put("buckets", JSONArray().apply {
        buckets.forEach { put(JSONObject().apply { put("from", it.from); put("count", it.count) }) }
    })
}

private fun ExportSummary.Oxygen.toJson(): JSONObject = JSONObject().apply {
    put("records", records)
    put("series", JSONArray().apply {
        series.forEach {
            put(JSONObject().apply {
                put("type", it.type); put("records", it.records); put("avg", it.avg)
                put("min", it.min); put("max", it.max)
            })
        }
    })
}

private fun ExportSummary.WeightPoint.toJson(): JSONObject = JSONObject().apply {
    put("time", time)
    if (weightKg != null) put("weightKg", weightKg)
    if (bodyFatPercent != null) put("bodyFatPercent", bodyFatPercent)
    if (heightCm != null) put("heightCm", heightCm)
}

private fun ExportSummary.Workout.toJson(): JSONObject = JSONObject().apply {
    put("records", records)
    put("distanceM", distanceM)
    put("durationMs", durationMs)
    put("calories", calories)
    put("types", JSONArray().apply {
        types.forEach {
            put(JSONObject().apply {
                put("id", it.id); put("records", it.records); put("distanceM", it.distanceM)
                put("durationMs", it.durationMs); put("calories", it.calories)
                if (it.avgHeartRate != null) put("avgHeartRate", it.avgHeartRate)
            })
        }
    })
}

/**
 * 逐行累积各数据集的统计量；与 Markdown 写出同源（同一批原始值），不重复读取宿主数据。
 * 输入为 ContentValues 的原始值（未截断、未格式化），因此需要长文本字段（如运动 metadata）时可直接解析。
 */
internal class ExportSummaryBuilder {
    private val daily = Daily()
    private val sleep = Sleep()
    private val heart = Values()
    private val pressure = Values()
    private val oxygen = LinkedHashMap<Int, Values>()
    private var oxygenRecords = 0
    private val weights = LinkedHashMap<Long, MutableMap<Int, Double>>()
    private val workouts = LinkedHashMap<Int, Workout>()
    private var dailyRecords = 0
    private var sleepRecords = 0
    private var weightRecords = 0
    private var workoutRecords = 0

    private class Daily {
        val days = LinkedHashSet<String>()
        var steps = 0L
        var distance = 0L
        var calories = 0L
        val months = LinkedHashMap<String, Month>()
    }

    private class Month {
        var steps = 0L
        var distance = 0L
        var calories = 0L
        val days = LinkedHashSet<String>()
    }

    private class Sleep {
        var minutes = 0L
        val nights = LinkedHashSet<String>()
        val months = LinkedHashMap<String, LongArray>() // [minutes, nights]
        val staged = LinkedHashMap<String, Stages>() // 按夜累积分期分量
        var wakeCount = 0L
        var phoneWakeCount = 0L
        var napMinutes = 0L
        var scoreSum = 0L
        var scoreNights = 0
        var latencySum = 0L
        var latencyNights = 0
        val fallAsleep = Latest()
        val wakeUp = Latest()
        val bedTime = Latest()
        val wakeTime = Latest()

        fun stage(night: String, block: Stages.() -> Unit) {
            staged.getOrPut(night) { Stages() }.block()
        }

        fun toDetail(): ExportSummary.SleepDetail {
            var deep = 0L
            var light = 0L
            var rem = 0L
            var awake = 0L
            var stagedNights = 0
            val months = LinkedHashMap<String, LongArray>() // [deep, light, rem, awake]
            for ((night, stages) in staged) {
                if (!stages.hasStages) continue
                val parts = stages.resolve()
                stagedNights++
                deep += parts[0]
                light += parts[1]
                rem += parts[2]
                awake += parts[3]
                val month = months.getOrPut(night.take(7)) { LongArray(4) }
                for (index in parts.indices) month[index] += parts[index]
            }
            return ExportSummary.SleepDetail(
                deepMinutes = deep,
                lightMinutes = light,
                remMinutes = rem,
                awakeMinutes = awake,
                stagedNights = stagedNights,
                wakeCount = if (wakeCount > 0L) wakeCount else phoneWakeCount,
                scoreSum = scoreSum,
                scoreNights = scoreNights,
                latencySum = latencySum,
                latencyNights = latencyNights,
                napMinutes = napMinutes,
                lastFallAsleep = fallAsleep.value,
                lastWakeUp = wakeUp.value,
                lastBedTime = bedTime.value,
                lastWakeTime = wakeTime.value,
                months = months.map { (month, values) ->
                    ExportSummary.MonthSleepDetail(month, values[0], values[1], values[2], values[3])
                },
            )
        }
    }

    /** 同一夜可能同时存在多套口径的分期记录，逐夜择一取用。 */
    private class Stages {
        var hasNew = false
        var newDeep = 0L
        var newLight = 0L
        var newRem = 0L
        var hasOld = false
        var oldDeep = 0L
        var oldLight = 0L
        var oldRem = 0L
        var hasPhone = false
        var phoneDeep = 0L
        var phoneLight = 0L
        var phoneAwake = 0L
        var awake = 0L

        val hasStages: Boolean get() = hasNew || hasOld || hasPhone

        /** 返回 [深睡, 浅睡, 快速眼动, 清醒]，单位分钟。 */
        fun resolve(): LongArray = when {
            hasNew -> longArrayOf(newDeep, newLight, newRem, awake)
            hasOld -> longArrayOf(oldDeep, oldLight, oldRem, awake)
            else -> longArrayOf(phoneDeep, phoneLight, 0L, phoneAwake)
        }
    }

    /** 取最新一次非空记录；导出顺序不保证有序，故按 start_time 比较。 */
    private class Latest {
        private var at = Long.MIN_VALUE
        var value: Long? = null
            private set

        fun accept(startMs: Long, newValue: Long) {
            if (newValue <= 0L) return
            if (value != null && startMs < at) return
            at = startMs
            value = newValue
        }
    }

    private class Values {
        var records = 0
        var sum = 0.0
        var min = Int.MAX_VALUE
        var max = Int.MIN_VALUE
        val buckets = IntArray(24)

        fun accept(value: Int) {
            records++
            sum += value
            if (value < min) min = value
            if (value > max) max = value
            val index = ((value - BUCKET_ORIGIN) / BUCKET_WIDTH).coerceIn(0, buckets.size - 1)
            buckets[index]++
        }

        companion object {
            const val BUCKET_ORIGIN = 30
            const val BUCKET_WIDTH = 10
        }
    }

    private class Workout {
        var records = 0
        var distance = 0L
        var duration = 0L
        var calories = 0L
        var heartSum = 0.0
        var heartCount = 0
    }

    fun accept(datasetId: String, type: Int, startMs: Long, value: (String) -> Any?) {
        when (datasetId) {
            "daily" -> acceptDaily(type, startMs, value)
            "sleep" -> acceptSleep(type, startMs, value)
            "heart_rate" -> number(value("point_value"))?.toInt()?.let(heart::accept)
            "pressure" -> number(value("point_value"))?.toInt()?.let(pressure::accept)
            "blood_oxygen" -> acceptOxygen(type, value)
            "weight" -> acceptWeight(type, startMs, value)
            "workout" -> acceptWorkout(type, startMs, value)
        }
    }

    private fun acceptDaily(type: Int, startMs: Long, value: (String) -> Any?) {
        val point = number(value("point_value"))?.toLong() ?: return
        val day = dayOf(startMs, value("time_zone"))
        dailyRecords++
        val bucket = daily.months.getOrPut(day.take(7)) { Month() }
        when (type) {
            40002 -> {
                daily.steps += point
                bucket.steps += point
                daily.days += day
                bucket.days += day
            }
            40004 -> {
                daily.distance += point
                bucket.distance += point
            }
            // 40003 原始单位为卡（cal），统一换算为千卡（kcal）。
            40003 -> {
                val kcal = point / CALORIES_PER_KCAL
                daily.calories += kcal
                bucket.calories += kcal
            }
        }
    }

    /**
     * 睡眠同源统计。单位按类型固定（有反编译实证）：441xx 为分钟；
     * 44001/44002/44003 为秒（÷60）；44005 为次数；44201/44202/44205/44216 为毫秒时间戳。
     */
    private fun acceptSleep(type: Int, startMs: Long, value: (String) -> Any?) {
        sleepRecords++
        val night = dayOf(startMs, value("time_zone"))
        val point = number(value("point_value"))?.toLong() ?: 0L
        when (type) {
            44105 -> {
                sleep.minutes += point
                val month = sleep.months.getOrPut(night.take(7)) { longArrayOf(0, 0) }
                if (sleep.nights.add(night)) month[1]++
                month[0] += point
            }
            // 旧口径分期：44101 梦睡/快速眼动、44102 深睡、44103 浅睡、44104 清醒。
            44101 -> sleep.stage(night) { hasOld = true; oldRem += point }
            44102 -> sleep.stage(night) { hasOld = true; oldDeep += point }
            44103 -> sleep.stage(night) { hasOld = true; oldLight += point }
            44104 -> sleep.stage(night) { awake += point }
            // AASM 新口径分期（sleep_version=5）：44111 快速眼动、44112 深睡 N3、44113 浅睡 N1N2。
            44111 -> sleep.stage(night) { hasNew = true; newRem += point }
            44112 -> sleep.stage(night) { hasNew = true; newDeep += point }
            44113 -> sleep.stage(night) { hasNew = true; newLight += point }
            // 手机侧兜底分期，原始单位为秒。
            44001 -> sleep.stage(night) { hasPhone = true; phoneDeep += point / 60 }
            44002 -> sleep.stage(night) { hasPhone = true; phoneLight += point / 60 }
            44003 -> sleep.stage(night) { hasPhone = true; phoneAwake += point / 60 }
            44107 -> sleep.wakeCount += point
            44005 -> sleep.phoneWakeCount += point
            44108 -> sleep.napMinutes += point
            44201 -> sleep.fallAsleep.accept(startMs, point)
            44202 -> sleep.wakeUp.accept(startMs, point)
            44203 -> if (point > 0L) { sleep.scoreSum += point; sleep.scoreNights++ }
            44205 -> sleep.bedTime.accept(startMs, point)
            44216 -> sleep.wakeTime.accept(startMs, point)
            44218 -> if (point > 0L) { sleep.latencySum += point; sleep.latencyNights++ }
        }
    }

    private fun acceptOxygen(type: Int, value: (String) -> Any?) {
        val point = number(value("point_value"))?.toInt() ?: return
        oxygen.getOrPut(type) { Values() }.accept(point)
        oxygenRecords++
    }

    private fun acceptWeight(type: Int, startMs: Long, value: (String) -> Any?) {
        val point = number(value("point_value")) ?: return
        if (type !in WEIGHT_TYPES) return
        weightRecords++
        weights.getOrPut(startMs) { LinkedHashMap() }[type] = point
    }

    private fun acceptWorkout(type: Int, startMs: Long, value: (String) -> Any?) {
        val id = number(value("sub_type_id"))?.toInt() ?: type
        val endMs = number(value("end_time"))?.toLong()
        val metadata = runCatching { JSONObject(value("metadata") as? String ?: "{}") }.getOrNull()
        val duration = metadata?.optLong("totalTime", 0L)?.takeIf { it > 0 }
            ?: ((endMs ?: startMs) - startMs).coerceAtLeast(0L)
        val distance = metadata?.optLong("totalDistance", 0L) ?: 0L
        // metadata.totalCalories 原始单位为卡（cal），统一换算为千卡（kcal）。
        val calories = (metadata?.optLong("totalCalories", 0L) ?: 0L) / CALORIES_PER_KCAL
        val heartRate = metadata?.optDouble("avgHeartRate", 0.0) ?: 0.0
        workoutRecords++
        val item = workouts.getOrPut(id) { Workout() }
        item.records++
        item.distance += distance
        item.duration += duration
        item.calories += calories
        if (heartRate > 0) {
            item.heartSum += heartRate
            item.heartCount++
        }
    }

    fun build(exportedAt: Long, rangeCode: String, rangeLabel: String, startMs: Long, endMs: Long,
              counts: Map<String, Int>, failed: Set<String>): ExportSummary = ExportSummary(
        exportedAt = exportedAt,
        rangeCode = rangeCode,
        rangeLabel = rangeLabel,
        startMs = startMs,
        endMs = endMs,
        counts = counts,
        failed = failed,
        daily = ExportSummary.Daily(
            records = dailyRecords,
            days = daily.days.size,
            stepsTotal = daily.steps,
            distanceM = daily.distance,
            calories = daily.calories,
            months = daily.months.map { (month, v) ->
                ExportSummary.MonthActivity(month, v.steps, v.days.size, v.distance, v.calories)
            },
        ),
        sleep = ExportSummary.Sleep(
            records = sleepRecords,
            nights = sleep.nights.size,
            minutes = sleep.minutes,
            months = sleep.months.map { (month, v) -> ExportSummary.MonthSleep(month, v[0], v[1].toInt()) },
            detail = sleep.toDetail(),
        ),
        heartRate = heart.toDistribution(),
        bloodOxygen = ExportSummary.Oxygen(
            records = oxygenRecords,
            series = oxygen.map { (type, values) -> values.toSeries(type) },
        ),
        pressure = pressure.toDistribution(),
        // 体重记录必须先按时间升序，界面「最新」与图表横轴才正确。
        weight = weights.entries.sortedBy { it.key }.map { (time, values) ->
            ExportSummary.WeightPoint(
                time = time,
                weightKg = values[2004],
                bodyFatPercent = values[2055],
                heightCm = values[2056],
            )
        },
        workout = ExportSummary.Workout(
            records = workoutRecords,
            distanceM = workouts.values.sumOf { it.distance },
            durationMs = workouts.values.sumOf { it.duration },
            calories = workouts.values.sumOf { it.calories },
            types = workouts.map { (id, item) ->
                ExportSummary.WorkoutType(
                    id = id,
                    records = item.records,
                    distanceM = item.distance,
                    durationMs = item.duration,
                    calories = item.calories,
                    avgHeartRate = if (item.heartCount == 0) null else item.heartSum / item.heartCount,
                )
            },
        ),
    )

    private fun Values.toDistribution(): ExportSummary.Distribution = ExportSummary.Distribution(
        records = records,
        avg = if (records == 0) 0.0 else sum / records,
        min = if (records == 0) 0 else min,
        max = if (records == 0) 0 else max,
        buckets = buckets.mapIndexed { index, count ->
            ExportSummary.Bucket(Values.BUCKET_ORIGIN + index * Values.BUCKET_WIDTH, count)
        }.filter { it.count > 0 },
    )

    private fun Values.toSeries(type: Int): ExportSummary.Oxygen.Series = ExportSummary.Oxygen.Series(
        type = type,
        records = records,
        avg = if (records == 0) 0.0 else sum / records,
        min = if (records == 0) 0 else min,
        max = if (records == 0) 0 else max,
    )

    private companion object {
        val WEIGHT_TYPES = setOf(2004, 2054, 2055, 2056)

        /** 热量原始单位为卡（cal），摘要与界面统一使用千卡（kcal）。 */
        const val CALORIES_PER_KCAL = 1000L

        /** 数值可能是 Number 或字符串（ContentValues 上游以两种形式出现过）。 */
        fun number(value: Any?): Double? = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }

        fun dayOf(startMs: Long, zone: Any?): String =
            DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.ofEpochMilli(startMs).atZone(zoneOf(zone)))

        /** 时间戳已是绝对毫秒，仅用于归属本地日期；无法识别时区时用系统默认。 */
        fun zoneOf(zone: Any?): ZoneId {
            val text = (zone as? String)?.trim().orEmpty()
            if (text.length == 5 && (text[0] == '+' || text[0] == '-')) {
                val offset = "GMT${text.take(3)}:${text.substring(3)}"
                runCatching { return ZoneId.of(offset) }
            }
            return ZoneId.systemDefault()
        }
    }
}
