package com.shawnsu.healthexport.export

import android.content.SharedPreferences
import com.shawnsu.healthexport.InMemoryPreferences
import com.shawnsu.healthexport.app.ExportUiState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExportProtocolTest {
    private fun report() = ExportReport(
        "11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", 100, time = 100,
    )

    @Test fun keepsTenDatasetsAndRoundTrips() {
        assertEquals(10, ExportProtocol.datasets.size)
        assertEquals(
            setOf("daily", "sleep", "heart_rate", "weight", "blood_pressure", "workout",
                "blood_oxygen", "pressure", "blood_sugar", "temperature"),
            ExportProtocol.ids,
        )
        ExportProtocol.datasets.forEach { assertTrue(it.id, it.types.isNotEmpty()) }
        val weight = ExportProtocol.datasets.first { it.id == "weight" }
        assertArrayEquals(intArrayOf(10006), weight.types)
        assertEquals(listOf("test"), weight.keys?.toList())
        val complete = report().copy(phase = "complete", done = ExportProtocol.ids,
            counts = ExportProtocol.ids.associateWith { 1 }, output = "Download/HuaweiHealthExport/export.md")
        assertEquals(complete, ExportReport.decode(complete.encode()))
    }

    @Test fun carriesBoundedDiagnosticTrace() {
        val complete = report().copy(phase = "complete", done = ExportProtocol.ids,
            counts = ExportProtocol.ids.associateWith { 1 }, output = "out.md",
            trace = "+0.0s 启动导出，预约 11111111\n+1.2s daily：6544 行（3120ms，回调 1 次）")
        assertEquals(complete, ExportReport.decode(complete.encode()))
        assertEquals("", ExportReport.decode(report().encode()).trace)
        assertTrue(runCatching { ExportReport.decode(report().copy(trace = "x".repeat(16_385)).encode()) }.isFailure)
        // 升级前的旧报告没有 trace 键，仍可被解码。
        val legacy = JSONObject(report().encode()).also { it.remove("trace") }.toString()
        assertEquals("", ExportReport.decode(legacy).trace)
    }

    @Test fun acceptsNewRunsButRejectsStaleOrTerminalUpdates() {
        val running = report()
        val progressed = running.copy(sequence = 1, done = setOf("daily"), counts = mapOf("daily" to 5))
        assertTrue(progressed.follows(running))
        assertFalse(running.follows(progressed))
        assertFalse(progressed.copy(sequence = 2, done = emptySet()).follows(progressed))
        assertFalse(progressed.copy(sequence = 2, counts = mapOf("daily" to 3)).follows(progressed))
        val complete = progressed.copy(sequence = 2, phase = "complete", done = ExportProtocol.ids, output = "out.md")
        assertTrue(complete.follows(progressed))
        assertFalse(complete.copy(sequence = 3).follows(complete))
        assertFalse(progressed.copy(run = "33333333-3333-3333-3333-333333333333", started = 99).follows(complete))
        assertTrue(progressed.copy(run = "33333333-3333-3333-3333-333333333333", started = 101).follows(complete))
    }

    @Test fun rejectsInvalidPayloadsAndFalseCompletion() {
        val complete = report().copy(phase = "complete", done = ExportProtocol.ids,
            counts = ExportProtocol.ids.associateWith { 1 }, output = "out.md")
        listOf(
            report().copy(phase = "complete"),
            report().copy(done = setOf("unknown")),
            report().copy(counts = mapOf("daily" to -1)),
            report().copy(phase = "failed"),
            report().copy(error = "write_error"),
            report().copy(output = "out.md"),
            report().copy(request = "not-a-uuid"),
            report().copy(time = 50),
            complete.copy(error = "export_error"),
            complete.copy(output = ""),
        ).forEach { invalid -> assertTrue(invalid.toString(), runCatching { ExportReport.decode(invalid.encode()) }.isFailure) }
        assertTrue(runCatching { ExportReport.decode("x".repeat(32769)) }.isFailure)
        assertTrue(runCatching { ExportReport.decode("{") }.isFailure)
    }

    @Test fun admitsOnlyOwnOrHostUid() {
        assertTrue(ExportAdmission.allows(10, 10, emptyList()))
        assertTrue(ExportAdmission.allows(11, 10, listOf("com.huawei.health")))
        assertFalse(ExportAdmission.allows(11, 10, emptyList()))
        assertFalse(ExportAdmission.allows(11, 10, listOf("com.huawei.health.fake")))
    }

    @Test fun exportFailureRestoresOptimisticLocalWrite() {
        val base = InMemoryPreferences(mapOf(ExportProtocol.REQUEST to "old"))
        val failing = object : SharedPreferences by base {
            override fun edit(): SharedPreferences.Editor {
                val editor = base.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun putString(key: String, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                    override fun commit(): Boolean { editor.commit(); return false }
                }
            }
        }
        assertEquals(ExportRequestOutcome.UNCERTAIN, ExportRequests.save(failing, "new"))
        assertEquals("old", base.getString(ExportProtocol.REQUEST, ""))
        assertEquals(ExportRequestOutcome.SUCCESS, ExportRequests.save(base, "new"))
        assertEquals("new", base.getString(ExportProtocol.REQUEST, ""))
    }

    @Test fun onlyActionableStatesAllowRequest() {
        val ready = ExportUiState(writable = true)
        assertTrue(ready.canRequest)
        assertFalse(ready.copy(pending = true).canRequest)
        assertFalse(ready.copy(saving = true).canRequest)
        assertFalse(ready.copy(report = report()).canRequest)
        assertTrue(ready.copy(report = report(), uncertain = true).canRequest)
        assertFalse(ready.copy(writable = false).canRequest)
        assertFalse(ready.copy(requestUncertain = true).canRequest)
        assertTrue(ready.copy(
            report = report().copy(phase = "complete", done = ExportProtocol.ids, output = "out.md"),
        ).canRequest)
    }
}
