package com.shawnsu.healthexport.export

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 单次导出的结构化摘要：由目标进程在写 Markdown 的同时同源计算，随报告链路回传模块 App，
 * 供「数据分析」界面直接渲染，无需再解析 Markdown 正文。
 * 全部字段为原始单位：距离米、时长毫秒、热量卡、睡眠分钟。
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

    data class Sleep(val records: Int, val nights: Int, val minutes: Long, val months: List<MonthSleep>) {
        val minutesPerNight: Long get() = if (nights == 0) 0L else minutes / nights
    }

    data class MonthSleep(val month: String, val minutes: Long, val nights: Int)

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
        const val SCHEMA = 1
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
}

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
            40003 -> {
                daily.calories += point
                bucket.calories += point
            }
        }
    }

    private fun acceptSleep(type: Int, startMs: Long, value: (String) -> Any?) {
        sleepRecords++
        if (type != 44105) return
        val minutes = number(value("point_value"))?.toLong() ?: return
        sleep.minutes += minutes
        val night = dayOf(startMs, value("time_zone"))
        val month = sleep.months.getOrPut(night.take(7)) { longArrayOf(0, 0) }
        if (sleep.nights.add(night)) month[1]++
        month[0] += minutes
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
        val calories = metadata?.optLong("totalCalories", 0L) ?: 0L
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
        ),
        heartRate = heart.toDistribution(),
        bloodOxygen = ExportSummary.Oxygen(
            records = oxygenRecords,
            series = oxygen.map { (type, values) -> values.toSeries(type) },
        ),
        pressure = pressure.toDistribution(),
        weight = weights.map { (time, values) ->
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
