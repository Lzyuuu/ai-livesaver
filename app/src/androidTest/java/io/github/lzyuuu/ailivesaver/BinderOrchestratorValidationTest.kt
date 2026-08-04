package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BinderOrchestratorValidationTest {
    @Test
    fun shippedGenerationRejectsSingleCandidateWithoutAdvancingDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providerStore = ProviderStore(context)
        val previousWorldProvider = providerStore.loadTask(ProviderTask.World)
        SingleCandidateServer().use { server ->
            providerStore.saveTask(
                ProviderTask.World,
                ProviderConfig(
                    preset = ProviderPreset.Custom,
                    baseUrl = server.baseUrl,
                    model = "single-candidate-model",
                    apiKey = "test-key",
                    capabilities = ProviderCapabilities(
                        supported = setOf(ProviderCapability.Structured),
                        checkedAt = System.currentTimeMillis(),
                    ),
                ),
            )
            val draftId = "single-candidate-${System.nanoTime()}"
            try {
                val latch = CountDownLatch(1)
                var result: Result<BinderGeneration>? = null
                BinderOrchestrator.generate(
                    context,
                    draftId,
                    BinderAnswers(relationship = "two distinct companions"),
                ) {
                    result = it
                    latch.countDown()
                }

                assertTrue("Binder callback timeout", latch.await(10, TimeUnit.SECONDS))
                val failure = checkNotNull(result).exceptionOrNull()
                assertTrue(failure is IllegalArgumentException)
                assertTrue(failure!!.message.orEmpty().contains("at least two"))
                assertNull(WorldStore(context).use { it.getBinderDraft(draftId) })
                assertEquals(1, server.requests)
            } finally {
                if (previousWorldProvider == null) {
                    providerStore.clearTask(ProviderTask.World)
                } else {
                    providerStore.saveTask(ProviderTask.World, previousWorldProvider)
                }
            }
        }
    }

    @Test
    fun shippedGenerationRetriesMalformedEnvelopeAndPersistsTwoCandidates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providerStore = ProviderStore(context)
        val previousWorldProvider = providerStore.loadTask(ProviderTask.World)
        MalformedThenValidServer().use { server ->
            providerStore.saveTask(
                ProviderTask.World,
                ProviderConfig(
                    preset = ProviderPreset.Custom,
                    baseUrl = server.baseUrl,
                    model = "format-retry-model",
                    apiKey = "test-key",
                    capabilities = ProviderCapabilities(
                        supported = setOf(ProviderCapability.Structured),
                        checkedAt = System.currentTimeMillis(),
                    ),
                ),
            )
            val draftId = "format-retry-${System.nanoTime()}"
            try {
                val latch = CountDownLatch(1)
                var result: Result<BinderGeneration>? = null
                BinderOrchestrator.generate(
                    context,
                    draftId,
                    BinderAnswers(relationship = "two companions"),
                ) {
                    result = it
                    latch.countDown()
                }

                assertTrue("Binder callback timeout", latch.await(10, TimeUnit.SECONDS))
                assertEquals(2, checkNotNull(result).getOrThrow().candidates.size)
                assertEquals(5, WorldStore(context).use { it.getBinderDraft(draftId)?.step })
                assertEquals(2, server.requests)
            } finally {
                if (previousWorldProvider == null) {
                    providerStore.clearTask(ProviderTask.World)
                } else {
                    providerStore.saveTask(ProviderTask.World, previousWorldProvider)
                }
            }
        }
    }

    @Test
    fun shippedGenerationRetriesTruncatedCandidateBodyAndPersistsTwoCandidates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providerStore = ProviderStore(context)
        val previousWorldProvider = providerStore.loadTask(ProviderTask.World)
        TruncatedCandidateThenValidServer().use { server ->
            providerStore.saveTask(
                ProviderTask.World,
                ProviderConfig(
                    preset = ProviderPreset.Custom,
                    baseUrl = server.baseUrl,
                    model = "candidate-body-retry-model",
                    apiKey = "test-key",
                    capabilities = ProviderCapabilities(
                        supported = setOf(ProviderCapability.Structured),
                        checkedAt = System.currentTimeMillis(),
                    ),
                ),
            )
            val draftId = "candidate-body-retry-${System.nanoTime()}"
            try {
                val latch = CountDownLatch(1)
                var result: Result<BinderGeneration>? = null
                BinderOrchestrator.generate(
                    context,
                    draftId,
                    BinderAnswers(relationship = "two companions"),
                ) {
                    result = it
                    latch.countDown()
                }

                assertTrue("Binder callback timeout", latch.await(10, TimeUnit.SECONDS))
                assertEquals(2, checkNotNull(result).getOrThrow().candidates.size)
                assertEquals(5, WorldStore(context).use { it.getBinderDraft(draftId)?.step })
                assertEquals(2, server.requests)
            } finally {
                if (previousWorldProvider == null) {
                    providerStore.clearTask(ProviderTask.World)
                } else {
                    providerStore.saveTask(ProviderTask.World, previousWorldProvider)
                }
            }
        }
    }

    private class TruncatedCandidateThenValidServer : AutoCloseable {
        private val socket = ServerSocket(0)
        @Volatile var requests = 0
            private set
        val baseUrl = "http://127.0.0.1:${socket.localPort}/v1"
        private val worker = thread(name = "binder-candidate-body-retry-http") {
            repeat(2) { attempt ->
                runCatching {
                    socket.accept().use { client ->
                        readHttpRequest(client.getInputStream())
                        requests += 1
                        val validCandidates = JSONObject()
                            .put(
                                "candidates",
                                JSONArray()
                                    .put(candidate("First"))
                                    .put(candidate("Second")),
                            )
                            .toString()
                        val candidateBody = if (attempt == 0) {
                            validCandidates.dropLast(2)
                        } else {
                            validCandidates
                        }
                        writeJsonResponse(
                            client,
                            JSONObject().put("body", candidateBody).toString(),
                        )
                    }
                }
            }
        }

        private fun candidate(name: String) = JSONObject()
            .put("name", name)
            .put("persona", "$name persona")
            .put("relationship", "friend")
            .put("reasons", JSONArray().put("distinct"))

        override fun close() {
            socket.close()
            worker.join(2_000)
        }
    }

    private class MalformedThenValidServer : AutoCloseable {
        private val socket = ServerSocket(0)
        @Volatile var requests = 0
            private set
        val baseUrl = "http://127.0.0.1:${socket.localPort}/v1"
        private val worker = thread(name = "binder-format-retry-http") {
            repeat(2) { attempt ->
                runCatching {
                    socket.accept().use { client ->
                        readHttpRequest(client.getInputStream())
                        requests += 1
                        val candidates = JSONObject()
                            .put(
                                "candidates",
                                JSONArray()
                                    .put(candidate("First"))
                                    .put(candidate("Second")),
                            )
                            .toString()
                        val envelope = if (attempt == 0) {
                            JSONObject().put("body", candidates).put("extra", true)
                        } else {
                            JSONObject().put("body", candidates)
                        }
                        writeJsonResponse(client, envelope.toString())
                    }
                }
            }
        }

        private fun candidate(name: String) = JSONObject()
            .put("name", name)
            .put("persona", "$name persona")
            .put("relationship", "friend")
            .put("reasons", JSONArray().put("distinct"))

        override fun close() {
            socket.close()
            worker.join(2_000)
        }
    }

    private class SingleCandidateServer : AutoCloseable {
        private val socket = ServerSocket(0)
        @Volatile var requests = 0
            private set
        val baseUrl = "http://127.0.0.1:${socket.localPort}/v1"
        private val worker = thread(name = "binder-single-candidate-http") {
            runCatching {
                socket.accept().use { client ->
                    readRequest(client.getInputStream())
                    requests += 1
                    val candidateBody = JSONObject()
                        .put(
                            "candidates",
                            JSONArray().put(
                                JSONObject()
                                    .put("name", "Only")
                                    .put("persona", "One candidate only")
                                    .put("relationship", "friend")
                                    .put("reasons", JSONArray().put("single")),
                            ),
                        )
                        .toString()
                    val response = JSONObject()
                        .put(
                            "choices",
                            JSONArray().put(
                                JSONObject().put(
                                    "message",
                                    JSONObject().put(
                                        "content",
                                        JSONObject().put("body", candidateBody).toString(),
                                    ),
                                ),
                            ),
                        )
                        .toString()
                        .toByteArray()
                    client.getOutputStream().apply {
                        write(
                            (
                                "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/json\r\n" +
                                    "Content-Length: ${response.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(),
                        )
                        write(response)
                        flush()
                    }
                }
            }
        }

        private fun readRequest(input: InputStream) = readHttpRequest(input)

        override fun close() {
            socket.close()
            worker.join(2_000)
        }
    }

    private companion object {
        fun readHttpRequest(input: InputStream) {
            val headers = ByteArrayOutputStream()
            var tail = ""
            while (true) {
                val next = input.read()
                if (next < 0) return
                headers.write(next)
                tail = (tail + next.toChar()).takeLast(4)
                if (tail == "\r\n\r\n") break
            }
            val length = Regex("(?im)^content-length:\\s*(\\d+)")
                .find(headers.toString(Charsets.UTF_8.name()))
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: 0
            var remaining = length
            val buffer = ByteArray(1_024)
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) return
                remaining -= count
            }
        }

        fun writeJsonResponse(client: java.net.Socket, structuredContent: String) {
            val response = JSONObject()
                .put(
                    "choices",
                    JSONArray().put(
                        JSONObject().put(
                            "message",
                            JSONObject().put("content", structuredContent),
                        ),
                    ),
                )
                .toString()
                .toByteArray()
            client.getOutputStream().apply {
                write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${response.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(),
                )
                write(response)
                flush()
            }
        }
    }
}
