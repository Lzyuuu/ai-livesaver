package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun failureSummaryNeverKeepsProviderResponseText() {
        assertEquals(
            "Provider HTTP 401",
            RuntimeDiagnostics.sanitizeFailure("HTTP 401\nsecret prompt\napi-key"),
        )
        assertEquals(
            "Provider request failed",
            RuntimeDiagnostics.sanitizeFailure("connection failed: private prompt"),
        )
        assertEquals("", RuntimeDiagnostics.sanitizeFailure(""))
    }

    @Test
    fun exportedTextContainsOnlyRuntimeSummaryFields() {
        val text = RuntimeDiagnostics.text(
            RuntimeSnapshot(
                appVersion = "0.1.0-test",
                deviceSummary = "iQOO / test / qcom / arm64-v8a",
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
                pendingMedia = 1,
                failedMedia = 0,
                localDreamQueueRunning = true,
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
        assertTrue(text.contains("device=iQOO / test / qcom / arm64-v8a"))
        assertTrue(text.contains("provider_capabilities=Chat"))
        assertTrue(text.contains("local_dream_generation_ms=3210"))
        assertTrue(text.contains("local_dream_queue_running=true"))
        assertTrue(!text.contains("api_key"))
        assertTrue(!text.contains("prompt"))
    }
}
