package com.shawnsu.healthexport.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportRangeTest {
    private val now = 1_700_000_000_000L

    @Test
    fun treatsMissingAndAllAsFullHistory() {
        assertEquals(0L to now, ExportRange.resolve(null, now))
        assertEquals(0L to now, ExportRange.resolve("", now))
        assertEquals(0L to now, ExportRange.resolve("   ", now))
        assertEquals(0L to now, ExportRange.resolve(ExportRange.ALL, now))
        assertTrue(ExportRange.isAll(null))
        assertTrue(ExportRange.isAll(ExportRange.ALL))
        assertFalse(ExportRange.isAll("7"))
        assertTrue(ExportRange.isValid(null))
        assertTrue(ExportRange.isValid(ExportRange.ALL))
    }

    @Test
    fun resolvesRecentDaysRelativeToNow() {
        assertEquals(now - 7 * ExportRange.DAY_MS to now, ExportRange.resolve("7", now))
        assertEquals(now - 365 * ExportRange.DAY_MS to now, ExportRange.resolve("365", now))
        assertTrue(ExportRange.isValid("30"))
        assertEquals("30", ExportRange.encodeDays(30))
        // 起点不早于纪元，避免负时间戳传给宿主读取接口。
        assertEquals(0L to 1L, ExportRange.resolve(ExportRange.MAX_DAYS.toString(), 1L))
    }

    @Test
    fun fallsBackToFullHistoryOnInvalidEncoding() {
        val invalid = listOf("0", "-1", "abc", "40000", "7.5", "custom:1", "custom:2:1", "custom:1:2:3", "custom:a:b", "custom:-1:5")
        for (value in invalid) {
            assertFalse(value, ExportRange.isValid(value))
            assertEquals(value, 0L to now, ExportRange.resolve(value, now))
        }
    }

    @Test
    fun clampsCustomRangeToNow() {
        val start = now - 10 * ExportRange.DAY_MS
        val encoded = ExportRange.encodeCustom(start, now + ExportRange.DAY_MS)
        assertTrue(ExportRange.isValid(encoded))
        assertEquals(start to now, ExportRange.resolve(encoded, now))
    }

    @Test
    fun labelsDescribeTheSelectedRange() {
        assertEquals("全部历史（startTime=0）", ExportRange.label(ExportRange.ALL, now))
        assertTrue(ExportRange.label("7", now).startsWith("最近 7 天（"))
        assertTrue(ExportRange.label(ExportRange.encodeCustom(now - ExportRange.DAY_MS, now), now).startsWith("自定义（"))
        assertEquals("全部历史（startTime=0）", ExportRange.label("abc", now))
        assertEquals("全部历史（startTime=0）", ExportRange.label("40000", now))
    }

    @Test
    fun keepsPresetsValidWithAllHistoryFirst() {
        assertEquals(ExportRange.ALL, ExportRange.presets.first())
        assertTrue(ExportRange.presets.all { ExportRange.isValid(it) })
    }

    @Test
    fun boundsEncodedDays() {
        assertEquals("1", ExportRange.encodeDays(0))
        assertEquals("1", ExportRange.encodeDays(-5))
        assertEquals(ExportRange.MAX_DAYS.toString(), ExportRange.encodeDays(Int.MAX_VALUE))
    }

    @Test
    fun rangeIsPersistedWithTheRequest() {
        val preferences = com.shawnsu.healthexport.InMemoryPreferences()
        val request = "11111111-1111-1111-1111-111111111111"
        assertEquals(ExportRequestOutcome.SUCCESS, ExportRequests.save(preferences, request, "30"))
        assertEquals(request, preferences.getString(ExportProtocol.REQUEST, null))
        assertEquals("30", ExportRequests.range(preferences))

        // 非法范围退化为全部历史，不写入脏值。
        assertEquals(ExportRequestOutcome.SUCCESS, ExportRequests.save(preferences, request, "bogus"))
        assertEquals(ExportRange.ALL, ExportRequests.range(preferences))
        assertEquals(ExportRange.ALL, ExportRequests.range(null))
    }
}
