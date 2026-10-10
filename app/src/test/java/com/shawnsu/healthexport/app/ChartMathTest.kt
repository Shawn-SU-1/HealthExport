package com.shawnsu.healthexport.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test
    fun niceTicksCoversRequestedRange() {
        val cases = listOf(
            Triple(0f, 100f, 4),
            Triple(3f, 97f, 4),
            Triple(0f, 1f, 4),
            Triple(-12.5f, 41.2f, 4),
            Triple(125_000f, 480_000f, 4),
            Triple(0.03f, 0.08f, 3),
        )
        for ((min, max, count) in cases) {
            val ticks = ChartMath.niceTicks(min, max, count)
            assertTrue("ticks empty for $min..$max", ticks.size >= 2)
            assertTrue("low bound $min not covered by ${ticks.first()}", ticks.first() <= min + 1e-3f)
            assertTrue("high bound $max not covered by ${ticks.last()}", ticks.last() >= max - 1e-3f)
            assertTrue("ticks not ascending", ticks.zipWithNext().all { (a, b) -> b > a })
        }
    }

    @Test
    fun niceTicksProducesUniformSteps() {
        for ((min, max) in listOf(0f to 100f, 3f to 97f, 0f to 1f, 125_000f to 480_000f)) {
            val ticks = ChartMath.niceTicks(min, max, 4)
            val step = ticks[1] - ticks[0]
            for (i in 1 until ticks.size - 1) {
                assertEquals(
                    "irregular step at $i for $min..$max",
                    step,
                    ticks[i + 1] - ticks[i],
                    step * 1e-3f,
                )
            }
        }
    }

    @Test
    fun niceTicksHandlesEqualBoundsAndNonFiniteInput() {
        val equal = ChartMath.niceTicks(0f, 0f, 4)
        assertTrue(equal.size >= 2)
        assertTrue(equal.first() < 0f && equal.last() > 0f)

        val flat = ChartMath.niceTicks(72.5f, 72.5f, 4)
        assertTrue(flat.size >= 2)
        assertTrue(flat.first() < 72.5f && flat.last() > 72.5f)

        assertTrue(ChartMath.niceTicks(Float.NaN, 10f, 4).isEmpty())
        assertTrue(ChartMath.niceTicks(0f, Float.POSITIVE_INFINITY, 4).isEmpty())
    }

    @Test
    fun compactFormatsMagnitudes() {
        assertEquals("1.2k", ChartMath.compact(1200f))
        assertEquals("1k", ChartMath.compact(1000f))
        assertEquals("3.4M", ChartMath.compact(3_400_000f))
        assertEquals("2M", ChartMath.compact(2_000_000f))
        assertEquals("1.2M", ChartMath.compact(1_234_567f))
        assertEquals("999", ChartMath.compact(999f))
        assertEquals("125", ChartMath.compact(125f))
        assertEquals("12.3", ChartMath.compact(12.34f))
        assertEquals("3.14", ChartMath.compact(3.14159f))
    }

    @Test
    fun sampleIndexesKeepsIdentityWhenSeriesIsShort() {
        assertEquals(listOf(0, 1, 2), ChartMath.sampleIndexes(3, 6))
        assertEquals(listOf(0, 1, 2, 3, 4), ChartMath.sampleIndexes(5, 5))
    }

    @Test
    fun sampleIndexesSpreadsEvenlyAcrossLongSeries() {
        assertEquals(listOf(0, 3, 6, 9), ChartMath.sampleIndexes(10, 4))
        assertEquals(listOf(0, 2, 5, 8, 11), ChartMath.sampleIndexes(12, 5))
    }

    @Test
    fun sampleIndexesHandlesDegenerateInput() {
        assertTrue(ChartMath.sampleIndexes(0, 6).isEmpty())
        assertTrue(ChartMath.sampleIndexes(10, 0).isEmpty())
        assertEquals(listOf(0), ChartMath.sampleIndexes(10, 1))
    }

    @Test
    fun sampleIndexesNeverRepeatsAndStaysInRange() {
        for (size in 1..40) {
            for (max in 1..8) {
                val indexes = ChartMath.sampleIndexes(size, max)
                assertEquals("duplicates for $size/$max", indexes.size, indexes.distinct().size)
                assertTrue("count over max for $size/$max", indexes.size <= max)
                assertTrue(
                    "out of range for $size/$max: $indexes",
                    indexes.all { it in 0 until size },
                )
                assertEquals("first not zero for $size/$max", 0, indexes.first())
            }
        }
    }
}
