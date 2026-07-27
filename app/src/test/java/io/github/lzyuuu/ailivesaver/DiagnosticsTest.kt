package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun failureSummaryIsSingleLineAndBounded() {
        val input = "HTTP 401\nsecret prompt\r\n" + "x".repeat(200)
        val output = RuntimeDiagnostics.sanitizeFailure(input)

        assertEquals(160, output.length)
        assertEquals(-1, output.indexOf('\n'))
        assertEquals(-1, output.indexOf('\r'))
        assertTrue(output.startsWith("HTTP 401 secret prompt "))
    }
}
