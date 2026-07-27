package io.github.lzyuuu.ailivesaver

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class LocalDreamHttpSmokeTest {
    @Test
    fun consumesLocalDreamSseAndPersistsRgbImage() {
        val server = ServerSocket(8081)
        val serverFailure = AtomicReference<Throwable?>(null)
        val progressSeen = AtomicBoolean(false)
        val responseReady = CountDownLatch(1)
        val serverThread = thread(start = true, name = "local-dream-test-server") {
            try {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val request = readHttpRequest(input)
                    val contentLength = Regex("(?im)^content-length:\\s*(\\d+)")
                        .find(request)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: 0
                    repeat(contentLength) { input.read() }
                    val rgb = ByteArray(8 * 8 * 3).also {
                        it[0] = 0xFF.toByte()
                        it[1] = 0x20
                        it[2] = 0x80.toByte()
                    }
                    val encoded = Base64.getEncoder().encodeToString(rgb)
                    val events = buildString {
                        append("event: progress\n")
                        append(
                            "data: " + JSONObject()
                                .put("type", "progress")
                                .put("step", 1)
                                .put("total_steps", 1)
                                .toString() + "\n\n",
                        )
                        append("event: complete\n")
                        append(
                            "data: " + JSONObject()
                                .put("type", "complete")
                                .put("width", 8)
                                .put("height", 8)
                                .put("channels", 3)
                                .put("seed", 42)
                                .put("image", encoded)
                                .toString() + "\n\n",
                        )
                        append("data: [DONE]\n\n")
                    }.toByteArray()
                    val output = socket.getOutputStream()
                    output.write(
                        ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Content-Length: ${events.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(),
                    )
                    output.write(events)
                    output.flush()
                    responseReady.countDown()
                }
            } catch (failure: Throwable) {
                serverFailure.set(failure)
                responseReady.countDown()
            }
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = CountDownLatch(1)
        var generatedPath: String? = null
        val postId = WorldStore(context).use { store ->
            store.createMediaPost(
                body = "queue smoke test",
                prompt = "queue smoke test image",
            )
        }
        try {
            LocalDreamQueue.resume(
                context = context,
                onProgress = { activePostId, _, _ ->
                    if (activePostId == postId) progressSeen.set(true)
                },
                onFinished = { activePostId, generation ->
                    if (activePostId == postId) {
                        generatedPath = generation.getOrNull()?.path
                        result.countDown()
                    }
                },
            )
            assertTrue("Local Dream callback timed out", result.await(10, TimeUnit.SECONDS))
            assertTrue("Local Dream server failed", serverFailure.get() == null)
            assertTrue("Local Dream response did not arrive", responseReady.await(2, TimeUnit.SECONDS))
            assertTrue("Progress event was not delivered", progressSeen.get())
            assertTrue("Generation failed: ${generatedPath ?: "no file"}", generatedPath != null)
            assertTrue(File(requireNotNull(generatedPath)).isFile)
            val visiblePost = WorldStore(context).use { store ->
                store.posts("moment").firstOrNull { it.id == postId }
            }
            assertTrue("Generated post was not made visible", visiblePost?.mediaStatus == "ready")
            assertTrue("Generated post has no image path", visiblePost?.mediaPath?.let(::File)?.isFile == true)
        } finally {
            WorldStore(context).use { it.deleteUserPost(postId) }
            generatedPath?.let { File(it).delete() }
            server.close()
            serverThread.join(2_000)
        }
    }

    private fun readHttpRequest(input: java.io.InputStream): String {
        val bytes = ByteArrayOutputStream()
        var previous = 0
        var current: Int
        while (input.read().also { current = it } >= 0) {
            bytes.write(current)
            if (previous == '\r'.code && current == '\n'.code && bytes.toByteArray()
                    .takeLast(4)
                    .toByteArray()
                    .contentEquals(byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte()))
            ) break
            previous = current
        }
        return bytes.toString(Charsets.UTF_8.name())
    }
}
