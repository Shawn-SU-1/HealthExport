package com.shawnsu.healthexport.export

import com.shawnsu.healthexport.export.ExportLogAnalyzer.Hint
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportLogAnalyzerTest {
    private val request = "11111111-1111-1111-1111-111111111111"

    private fun report() = ExportReport(
        request = "22222222-2222-2222-2222-222222222222",
        run = "33333333-3333-3333-3333-333333333333",
        started = 100, time = 100,
    )

    @Test fun fallsBackWithoutRequestOrReport() {
        assertEquals(listOf(Hint.NoRecord), ExportLogAnalyzer.hints(null, "", false))
        assertEquals(listOf(Hint.PreviousLog("22222222")), ExportLogAnalyzer.hints(report(), "", false))
    }

    @Test fun waitsForHealthOnFreshOrReplacedRequest() {
        assertEquals(listOf(Hint.WaitingFirst("11111111")), ExportLogAnalyzer.hints(null, request, false))
        assertEquals(listOf(Hint.WaitingNext("11111111", "22222222")), ExportLogAnalyzer.hints(report(), request, false))
    }

    @Test fun summarizesRunningCompleteAndFailedStates() {
        val running = report().copy(request = request, phase = "running",
            done = setOf("daily"), counts = mapOf("daily" to 5))
        assertEquals(listOf(Hint.Running(1, 10)), ExportLogAnalyzer.hints(running, request, false))
        assertEquals(listOf(Hint.Stale), ExportLogAnalyzer.hints(running, request, true))
        val complete = running.copy(phase = "complete", done = ExportProtocol.ids,
            counts = ExportProtocol.ids.associateWith { 1 }, output = "out.md")
        assertEquals(listOf(Hint.Complete(10)), ExportLogAnalyzer.hints(complete, request, false))
        assertEquals(
            listOf(Hint.CompletePartial(10, setOf("sleep", "weight"))),
            ExportLogAnalyzer.hints(complete.copy(failed = setOf("sleep", "weight")), request, false),
        )
        assertEquals(
            listOf(Hint.Failed("write_error")),
            ExportLogAnalyzer.hints(running.copy(phase = "failed", error = "write_error"), request, false),
        )
    }
}
