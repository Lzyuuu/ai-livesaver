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

    @Test
    fun exportedTextContainsOnlyRuntimeSummaryFields() {
        val text = RuntimeDiagnostics.text(
            RuntimeSnapshot(
                appVersion = "0.1.0-test",
                sdk = 36,
                providerConfigured = true,
                providerHost = "api.example.test",
                capabilities = ProviderCapabilities(
                    supported = setOf(ProviderCapability.Chat),
                ),
                worldEnabled = true,
                activity = "natural",
                budgetUsed = 2,
                budgetLimit = 20,
                taskPaused = false,
                lastFailure = "",
                queuedMedia = 1,
                availableStorage = 1234,
                notificationsEnabled = false,
                localDreamStats = LocalDreamRunStats(
                    generationTimeMs = 3210,
                    firstStepTimeMs = 480,
                    width = 512,
                    height = 768,
                    recordedAtMs = 1,
                ),
            ),
        )

        assertTrue(text.contains("provider_host=api.example.test"))
        assertTrue(text.contains("provider_capabilities=Chat"))
        assertTrue(text.contains("local_dream_generation_ms=3210"))
        assertTrue(!text.contains("api_key"))
        assertTrue(!text.contains("prompt"))
    }
}
