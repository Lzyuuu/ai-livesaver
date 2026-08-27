package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessengerRetryHttpSmokeTest {
    @Test
    fun shippedStreamFailureThenRetryUsesRealHttpAndOneTimelineVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = RetryServer()
        val characterId = WorldStore(context).use { it.addCharacter("retry-${System.nanoTime()}", "test persona", "resident", "", "", "") }
        try {
            val config = ProviderConfig(ProviderPreset.Custom, server.baseUrl, "test-model", "test-key")
            val first = WorldStore(context).use { it.beginAssistantReply(characterId, "Custom", "test-model") }
            server.fail()
            stream(context, config, characterId, first.id).assertFailure()
            assertEquals("failed", WorldStore(context).use { it.messages(characterId).single().status })

            val retry = WorldStore(context).use { it.beginAssistantReply(characterId, "Custom", "test-model", first.id) }
            assertEquals(first.id, retry.id)
            server.succeed()
            stream(context, config, characterId, retry.id).assertSuccess()
            WorldStore(context).use {
                val messages = it.messages(characterId)
                assertEquals(1, messages.size)
                assertEquals("complete", messages.single().status)
                assertEquals("retry succeeded", messages.single().body)
                assertEquals(1, it.messageVersions(first.id).size)
            }
            assertEquals(2, server.requests.get())
        } finally {
            WorldStore(context).use { it.deleteCharacter(characterId) }
            server.close()
        }
    }

    @Test
    fun groupMemberFailureCanRetryWithoutDroppingSuccessfulMember() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = RetryServer()
        val ids = WorldStore(context).use {
            listOf(
                it.addCharacter("group-a-${System.nanoTime()}", "a", "resident", "", "", ""),
                it.addCharacter("group-b-${System.nanoTime()}", "b", "resident", "", "", ""),
            )
        }
        val groupId = WorldStore(context).use { it.addCharacter("group-${System.nanoTime()}", "group", "resident", "", "", "") }
        try {
            val config = ProviderConfig(ProviderPreset.Custom, server.baseUrl, "test-model", "test-key")
            val first = ids.map { id -> WorldStore(context).use { it.beginAssistantReply(groupId, "Custom", "test-model") to id } }
            server.fail()
            val failed = stream(context, config, first[0].second, first[0].first.id)
            server.succeed()
            val successful = stream(context, config, first[1].second, first[1].first.id)
            failed.assertFailure()
            successful.assertSuccess()
            val failedId = first[0].first.id
            val retry = WorldStore(context).use { it.beginAssistantReply(groupId, "Custom", "test-model", failedId) }
            stream(context, config, ids[0], retry.id).assertSuccess()
            WorldStore(context).use {
                val messages = it.messages(groupId)
                assertEquals(2, messages.size)
                assertTrue(messages.all { message -> message.status == "complete" })
                assertEquals(2, messages.count { message -> message.body == "retry succeeded" })
                assertEquals(1, it.messageVersions(failedId).size)
            }
            // HTTP stacks may reconnect internally; the shipped contract is the timeline.
            assertTrue(server.requests.get() >= 3)
        } finally {
            WorldStore(context).use { ids.plus(groupId).forEach(it::deleteCharacter) }
            server.close()
        }
    }

    private fun stream(context: Context, config: ProviderConfig, characterId: Long, replyId: Long): Result<ProviderResponse> {
        val done = CountDownLatch(1)
        var result: Result<ProviderResponse>? = null
        val character = WorldStore(context).use { it.characters().first { c -> c.id == characterId } }
        ProviderChatClient.stream(
            config = config,
            character = character,
            messages = emptyList(),
            memories = emptyList(),
            recap = null,
            worldFacts = emptyList(),
            cognition = emptyList(),
            userContext = MemberWorldContext("user", "", ""),
            characterContext = MemberWorldContext("character:${character.id}", "", ""),
            relationship = RelationshipState("new", "", null, 0),
            defaults = GenerationSettings(),
            onDelta = {},
            callback = { response ->
                result = response
                done.countDown()
            },
        )
        assertTrue(done.await(10, TimeUnit.SECONDS))
        result!!.onSuccess { response -> WorldStore(context).use { it.completeAssistantReply(replyId, response.text, response.config.preset.displayName, response.config.model) } }
            .onFailure { failure -> WorldStore(context).use { it.failAssistantReply(replyId, failure.message.orEmpty()) } }
        return result!!
    }

    private fun Result<ProviderResponse>.assertFailure() =
        assertTrue("expected failure but completed with ${getOrNull()?.text}", isFailure)

    private fun Result<ProviderResponse>.assertSuccess() =
        assertTrue(
            "expected success but got ${exceptionOrNull()?.javaClass?.simpleName}: " +
                exceptionOrNull()?.message.orEmpty(),
            isSuccess,
        )

    private class RetryServer : AutoCloseable {
        val socket = ServerSocket(0)
        val requests = AtomicInteger()
        private val failing = java.util.concurrent.atomic.AtomicBoolean(true)
        val baseUrl = "http://127.0.0.1:${socket.localPort}/v1"
        fun fail() = failing.set(true)
        fun succeed() = failing.set(false)
        private val worker = thread(name = "messenger-retry-http") {
            while (!socket.isClosed) runCatching {
                socket.accept().use { client ->
                    readRequest(client.getInputStream())
                    requests.incrementAndGet()
                    if (failing.get()) {
                        writeUnauthorized(client)
                    } else {
                        write(client, "data: ${delta("retry succeeded")}\n\n" + "data: [DONE]\n\n")
                    }
                }
            }
        }
        private fun delta(text: String) = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("delta", JSONObject().put("content", text)))).toString()
        private fun readRequest(input: InputStream) {
            val headers = ByteArrayOutputStream()
            var tail = ""
            while (true) {
                val next = input.read()
                if (next < 0) return
                headers.write(next)
                tail = (tail + next.toChar()).takeLast(4)
                if (tail == "\r\n\r\n") break
            }
            val contentLength = Regex("(?im)^content-length:\\s*(\\d+)")
                .find(headers.toString(Charsets.UTF_8.name()))
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: 0
            var remaining = contentLength
            val buffer = ByteArray(1_024)
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read < 0) return
                remaining -= read
            }
        }
        private fun write(client: java.net.Socket, body: String) { val bytes = body.toByteArray(); client.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() } }
        private fun writeUnauthorized(client: java.net.Socket) {
            client.getOutputStream().apply {
                write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                flush()
            }
        }
        override fun close() { socket.close(); worker.join(2_000) }
    }
}
