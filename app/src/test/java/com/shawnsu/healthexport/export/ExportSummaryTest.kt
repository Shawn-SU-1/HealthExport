package com.shawnsu.healthexport.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSummaryTest {
    private val day1 = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private val day2 = day1 + 86_400_000L
    private val day3 = day2 + 86_400_000L
    private val zone = "+0000"

    private class Recorder {
        val builder = ExportSummaryBuilder()
        var failed = emptySet<String>()
        var counts = mapOf<String, Int>()

        fun feed(dataset: String, type: Int, startMs: Long, vararg pairs: Pair<String, Any?>): Recorder {
            builder.accept(dataset, type, startMs) { key -> pairs.firstOrNull { it.first == key }?.second }
            return this
        }

        fun build(): ExportSummary = builder.build(
            exportedAt = 1_700_000_500_000L,
            rangeCode = ExportRange.ALL,
            rangeLabel = "全部历史（startTime=0）",
            startMs = 0L,
            endMs = 1_700_000_500_000L,
            counts = counts,
            failed = failed,
        )
    }

    @Test
    fun roundTripsThroughJson() {
        val summary = Recorder()
            .feed("daily", 40002, day1, "point_value" to 1000, "time_zone" to zone)
            .feed("daily", 40004, day1, "point_value" to 1200, "time_zone" to zone)
            .feed("sleep", 44105, day1, "point_value" to 420, "time_zone" to zone)
            .feed("heart_rate", 0, day1, "point_value" to 72)
            .feed("blood_oxygen", 2103, day1, "point_value" to 98)
            .feed("pressure", 0, day1, "point_value" to 55)
            .feed("weight", 2004, day1, "point_value" to 57.95)
            .feed(
                "workout", 30001, day1,
                "sub_type_id" to 258, "end_time" to day1 + 1_500_000L,
                "metadata" to """{"totalTime":1500000,"totalDistance":5000,"totalCalories":300,"avgHeartRate":146}""",
            )
            .build()

        assertEquals(summary, ExportSummary.decode(summary.encode()))
    }

    @Test
    fun aggregatesDailyActivityByDayAndMonth() {
        val summary = Recorder()
            .feed("daily", 40002, day1, "point_value" to 1000, "time_zone" to zone)
            .feed("daily", 40002, day1 + 3_600_000L, "point_value" to 500, "time_zone" to zone)
            .feed("daily", 40002, day2, "point_value" to 800, "time_zone" to zone)
            .feed("daily", 40004, day1, "point_value" to 1200, "time_zone" to zone)
            .feed("daily", 40003, day1, "point_value" to 300000, "time_zone" to zone)
            .build()

        assertEquals(5, summary.daily.records)
        assertEquals(2, summary.daily.days)
        assertEquals(2300L, summary.daily.stepsTotal)
        assertEquals(1200L, summary.daily.distanceM)
        assertEquals(300L, summary.daily.calories)
        assertEquals(1150L, summary.daily.stepsPerDay)
        assertEquals(1, summary.daily.months.size)
        assertEquals("2023-11", summary.daily.months[0].month)
        assertEquals(2300L, summary.daily.months[0].steps)
        assertEquals(2, summary.daily.months[0].days)
    }

    @Test
    fun countsEachSleepNightOnceAndIgnoresUnknownSleepTypes() {
        val summary = Recorder()
            .feed("sleep", 44105, day1, "point_value" to 400, "time_zone" to zone)
            .feed("sleep", 44105, day1 + 3_600_000L, "point_value" to 20, "time_zone" to zone)
            .feed("sleep", 44110, day1, "point_value" to 109, "time_zone" to zone)
            .feed("sleep", 44105, day2, "point_value" to 300, "time_zone" to zone)
            .build()

        assertEquals(4, summary.sleep.records)
        assertEquals(2, summary.sleep.nights)
        assertEquals(720L, summary.sleep.minutes)
        assertEquals(360L, summary.sleep.minutesPerNight)
        assertEquals(1, summary.sleep.months.size)
        assertEquals(720L, summary.sleep.months[0].minutes)
        assertEquals(2, summary.sleep.months[0].nights)
    }

    @Test
    fun bucketsHeartRateAndPressureWithSharedWidth() {
        val summary = Recorder()
            .feed("heart_rate", 0, day1, "point_value" to 30)
            .feed("heart_rate", 0, day1, "point_value" to 45)
            .feed("heart_rate", 0, day1, "point_value" to 45)
            .feed("heart_rate", 0, day1, "point_value" to 200)
            .feed("pressure", 0, day1, "point_value" to 55)
            .build()

        val heart = summary.heartRate
        assertEquals(4, heart.records)
        assertEquals(80.0, heart.avg, 0.0001)
        assertEquals(30, heart.min)
        assertEquals(200, heart.max)
        assertEquals(listOf(30 to 1, 40 to 2, 200 to 1), heart.buckets.map { it.from to it.count })

        assertEquals(1, summary.pressure.records)
        assertEquals(55.0, summary.pressure.avg, 0.0001)
        assertEquals(listOf(50 to 1), summary.pressure.buckets.map { it.from to it.count })
    }

    @Test
    fun clampsOutOfRangeValuesIntoFirstAndLastBucket() {
        val summary = Recorder()
            .feed("heart_rate", 0, day1, "point_value" to 5)
            .feed("heart_rate", 0, day1, "point_value" to 900)
            .build()

        val buckets = summary.heartRate.buckets.map { it.from to it.count }
        assertEquals(listOf(30 to 1, 260 to 1), buckets)
        assertEquals(5, summary.heartRate.min)
        assertEquals(900, summary.heartRate.max)
    }

    @Test
    fun keepsBloodOxygenSeriesSeparateByType() {
        val summary = Recorder()
            .feed("blood_oxygen", 2103, day1, "point_value" to 98)
            .feed("blood_oxygen", 2103, day1, "point_value" to 97)
            .feed("blood_oxygen", 2107, day1, "point_value" to 80)
            .build()

        assertEquals(3, summary.bloodOxygen.records)
        assertEquals(listOf(2103, 2107), summary.bloodOxygen.series.map { it.type })
        val normal = summary.bloodOxygen.series[0]
        assertEquals(2, normal.records)
        assertEquals(97.5, normal.avg, 0.0001)
        assertEquals(97, normal.min)
        assertEquals(98, normal.max)
        assertEquals(80.0, summary.bloodOxygen.series[1].avg, 0.0001)
    }

    @Test
    fun mergesWeightMeasurementsByTimestamp() {
        val summary = Recorder()
            .feed("weight", 2004, day1, "point_value" to 57.95)
            .feed("weight", 2055, day1, "point_value" to 21.0)
            .feed("weight", 2056, day1, "point_value" to 167.0)
            .feed("weight", 2054, day1, "point_value" to 1234.0)
            .feed("weight", 2004, day2, "point_value" to 58.4)
            .build()

        assertEquals(2, summary.weight.size)
        assertEquals(day1, summary.weight[0].time)
        assertEquals(57.95, summary.weight[0].weightKg!!, 0.0001)
        assertEquals(21.0, summary.weight[0].bodyFatPercent!!, 0.0001)
        assertEquals(167.0, summary.weight[0].heightCm!!, 0.0001)
        assertEquals(day2, summary.weight[1].time)
        assertNull(summary.weight[1].bodyFatPercent)
        assertNull(summary.weight[1].heightCm)
    }

    @Test
    fun readsWorkoutTotalsFromMetadataAndFallsBackToTimestamps() {
        val summary = Recorder()
            .feed(
                "workout", 30001, day1,
                "sub_type_id" to 258, "end_time" to day1 + 1_500_000L,
                "metadata" to """{"totalTime":1500000,"totalDistance":5000,"totalCalories":300000,"avgHeartRate":146}""",
            )
            .feed(
                "workout", 30001, day2,
                "sub_type_id" to 258, "end_time" to day2 + 600_000L,
                "metadata" to """{"totalTime":0,"totalDistance":2000,"totalCalories":100000}""",
            )
            .feed("workout", 30001, day3, "sub_type_id" to 279, "end_time" to day3 + 900_000L, "metadata" to "{}")
            .build()

        assertEquals(3, summary.workout.records)
        assertEquals(7000L, summary.workout.distanceM)
        assertEquals(3_000_000L, summary.workout.durationMs)
        assertEquals(400L, summary.workout.calories)
        assertEquals(listOf(258, 279), summary.workout.types.map { it.id })

        val cycling = summary.workout.types[0]
        assertEquals(2, cycling.records)
        assertEquals(146.0, cycling.avgHeartRate!!, 0.0001)
        assertNull(summary.workout.types[1].avgHeartRate)
    }

    @Test
    fun convertsCaloriesFromCaloriesToKilocalories() {
        val summary = Recorder()
            .feed("daily", 40003, day1, "point_value" to 380_588_187, "time_zone" to zone)
            .build()

        assertEquals(380_588L, summary.daily.calories)
        assertEquals(380_588L, summary.daily.months[0].calories)
    }

    @Test
    fun prefersAasmSleepStagesOverLegacyOnesPerNight() {
        val summary = Recorder()
            // 第 1 夜只有旧口径。
            .feed("sleep", 44105, day1, "point_value" to 420, "time_zone" to zone)
            .feed("sleep", 44102, day1, "point_value" to 60, "time_zone" to zone)
            .feed("sleep", 44103, day1, "point_value" to 300, "time_zone" to zone)
            .feed("sleep", 44101, day1, "point_value" to 40, "time_zone" to zone)
            .feed("sleep", 44104, day1, "point_value" to 20, "time_zone" to zone)
            // 第 2 夜新旧口径并存，新口径优先且不重复累计。
            .feed("sleep", 44105, day2, "point_value" to 480, "time_zone" to zone)
            .feed("sleep", 44112, day2, "point_value" to 90, "time_zone" to zone)
            .feed("sleep", 44113, day2, "point_value" to 330, "time_zone" to zone)
            .feed("sleep", 44111, day2, "point_value" to 50, "time_zone" to zone)
            .feed("sleep", 44102, day2, "point_value" to 999, "time_zone" to zone)
            .feed("sleep", 44104, day2, "point_value" to 10, "time_zone" to zone)
            .build()

        val detail = summary.sleep.detail
        assertTrue(detail.hasStages)
        assertEquals(2, detail.stagedNights)
        assertEquals(150L, detail.deepMinutes)
        assertEquals(630L, detail.lightMinutes)
        assertEquals(90L, detail.remMinutes)
        assertEquals(30L, detail.awakeMinutes)
        assertEquals(900L, detail.stageMinutes)
        assertEquals(75L, detail.deepPerNight)
        assertEquals(1, detail.months.size)
        assertEquals("2023-11", detail.months[0].month)
        assertEquals(150L, detail.months[0].deepMinutes)
    }

    @Test
    fun fallsBackToPhoneSideSleepStagesInSeconds() {
        val summary = Recorder()
            .feed("sleep", 44105, day1, "point_value" to 400, "time_zone" to zone)
            .feed("sleep", 44001, day1, "point_value" to 6000, "time_zone" to zone)
            .feed("sleep", 44002, day1, "point_value" to 18000, "time_zone" to zone)
            .feed("sleep", 44003, day1, "point_value" to 1200, "time_zone" to zone)
            .build()

        val detail = summary.sleep.detail
        assertTrue(detail.hasStages)
        assertEquals(100L, detail.deepMinutes)
        assertEquals(300L, detail.lightMinutes)
        assertEquals(0L, detail.remMinutes)
        assertEquals(20L, detail.awakeMinutes)
    }

    @Test
    fun collectsSleepQualityMetricsAndLatestTimestamps() {
        val summary = Recorder()
            .feed("sleep", 44105, day1, "point_value" to 400, "time_zone" to zone)
            .feed("sleep", 44102, day1, "point_value" to 60, "time_zone" to zone)
            .feed("sleep", 44107, day1, "point_value" to 2, "time_zone" to zone)
            .feed("sleep", 44108, day1, "point_value" to 30, "time_zone" to zone)
            .feed("sleep", 44203, day1, "point_value" to 80, "time_zone" to zone)
            .feed("sleep", 44218, day1, "point_value" to 12, "time_zone" to zone)
            .feed("sleep", 44201, day1, "point_value" to day1, "time_zone" to zone)
            .feed("sleep", 44105, day2, "point_value" to 420, "time_zone" to zone)
            .feed("sleep", 44102, day2, "point_value" to 70, "time_zone" to zone)
            .feed("sleep", 44107, day2, "point_value" to 4, "time_zone" to zone)
            .feed("sleep", 44203, day2, "point_value" to 90, "time_zone" to zone)
            .feed("sleep", 44218, day2, "point_value" to 18, "time_zone" to zone)
            .feed("sleep", 44201, day2, "point_value" to day2, "time_zone" to zone)
            // 乱序到达的时间戳不应覆盖更新的记录。
            .feed("sleep", 44201, day1, "point_value" to day1, "time_zone" to zone)
            .build()

        val detail = summary.sleep.detail
        assertEquals(6L, detail.wakeCount)
        assertEquals(30L, detail.napMinutes)
        assertEquals(85.0, detail.scoreAvg!!, 0.0001)
        assertEquals(15.0, detail.latencyAvg!!, 0.0001)
        assertEquals(3.0, detail.wakePerNight!!, 0.0001)
        assertEquals(day2, detail.lastFallAsleep)
        assertNull(detail.lastWakeUp)
    }

    @Test
    fun skipsSleepDetailWhenOnlyTotalDurationIsAvailable() {
        val summary = Recorder()
            .feed("sleep", 44105, day1, "point_value" to 420, "time_zone" to zone)
            .feed("sleep", 44110, day1, "point_value" to 109, "time_zone" to zone)
            .build()

        assertTrue(!summary.sleep.detail.hasStages)
        assertEquals(0L, summary.sleep.detail.deepMinutes)
    }

    @Test
    fun sortsWeightPointsByTime() {
        val summary = Recorder()
            .feed("weight", 2004, day3, "point_value" to 58.4)
            .feed("weight", 2004, day1, "point_value" to 57.95)
            .feed("weight", 2055, day1, "point_value" to 21.0)
            .feed("weight", 2004, day2, "point_value" to 58.1)
            .build()

        assertEquals(listOf(day1, day2, day3), summary.weight.map { it.time })
        assertEquals(58.4, summary.weight.last().weightKg!!, 0.0001)
    }

    @Test
    fun roundTripsSleepDetailThroughJson() {
        val summary = Recorder()
            .feed("sleep", 44105, day1, "point_value" to 420, "time_zone" to zone)
            .feed("sleep", 44112, day1, "point_value" to 80, "time_zone" to zone)
            .feed("sleep", 44113, day1, "point_value" to 300, "time_zone" to zone)
            .feed("sleep", 44111, day1, "point_value" to 30, "time_zone" to zone)
            .feed("sleep", 44104, day1, "point_value" to 10, "time_zone" to zone)
            .feed("sleep", 44203, day1, "point_value" to 88, "time_zone" to zone)
            .feed("sleep", 44202, day1, "point_value" to day1 + 1000L, "time_zone" to zone)
            .build()

        assertEquals(summary, ExportSummary.decode(summary.encode()))
    }

    @Test
    fun rejectsForeignOrOversizedPayloads() {
        val summary = Recorder()
            .feed("daily", 40002, day1, "point_value" to 1000, "time_zone" to zone)
            .build()

        val foreign = summary.encode().replace("\"schema\":2", "\"schema\":3")
        assertThrows(IllegalArgumentException::class.java) { ExportSummary.decode(foreign) }
        assertThrows(IllegalArgumentException::class.java) {
            ExportSummary.decode(" ".repeat(ExportSummary.MAX_RAW + 1))
        }
    }

    @Test
    fun ignoresMalformedValuesInsteadOfCrashing() {
        val summary = Recorder()
            .feed("daily", 40002, day1, "point_value" to "abc", "time_zone" to zone)
            .feed("heart_rate", 0, day1, "point_value" to null)
            .feed("blood_oxygen", 2103, day1, "point_value" to "")
            .feed("weight", 2004, day1, "point_value" to "not-a-number")
            .feed("workout", 30001, day1, "sub_type_id" to 258, "metadata" to "not-json")
            .build()

        assertEquals(0, summary.daily.records)
        assertEquals(0, summary.heartRate.records)
        assertEquals(0, summary.bloodOxygen.records)
        assertTrue(summary.weight.isEmpty())
        assertEquals(1, summary.workout.records)
        assertEquals(0L, summary.workout.distanceM)
        assertEquals(0L, summary.workout.durationMs)
    }
}
