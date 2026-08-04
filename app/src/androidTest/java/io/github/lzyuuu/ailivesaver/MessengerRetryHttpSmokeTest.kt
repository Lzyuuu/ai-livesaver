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
            stream(context, config, characterId, first.id).assertFailure()
            assertEquals("failed", WorldStore(context).use { it.messages(characterId).single().status })

            val retry = WorldStore(context).use { it.beginAssistantReply(characterId, "Custom", "test-model", first.id) }
            assertEquals(first.id, retry.id)
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
            val results = first.map { (reply, member) -> stream(context, config, member, reply.id) }
            results[0].assertFailure()
            results[1].assertSuccess()
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
            assertEquals(3, server.requests.get())
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

    private fun Result<ProviderResponse>.assertFailure() = assertTrue(isFailure)
    private fun Result<ProviderResponse>.assertSuccess() = assertTrue(isSuccess)

    private class RetryServer : AutoCloseable {
        val socket = ServerSocket(0)
        val requests = AtomicInteger()
        val baseUrl = "http://127.0.0.1:${socket.localPort}/v1"
        private val worker = thread(name = "messenger-retry-http") {
            while (!socket.isClosed) runCatching {
                socket.accept().use { client ->
                    val input = client.getInputStream()
                    readHeaders(input)
                    val n = requests.incrementAndGet()
                    if (n == 1) {
                        write(client, "data: ${delta("partial")}\n\n")
                    } else {
                        write(client, "data: ${delta("retry succeeded")}\n\n" + "data: [DONE]\n\n")
                    }
                }
            }
        }
        private fun delta(text: String) = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("delta", JSONObject().put("content", text)))).toString()
        private fun readHeaders(input: InputStream) { val b = ByteArrayOutputStream(); var tail = ""; while (true) { val c = input.read(); if (c < 0) break; b.write(c); tail = (tail + c.toChar()).takeLast(4); if (tail == "\r\n\r\n") break } }
        private fun write(client: java.net.Socket, body: String) { val bytes = body.toByteArray(); client.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() } }
        override fun close() { socket.close(); worker.join(2_000) }
    }
}
