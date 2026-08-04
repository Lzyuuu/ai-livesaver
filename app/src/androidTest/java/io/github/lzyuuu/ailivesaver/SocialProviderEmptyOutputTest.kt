package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.content.SharedPreferences
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SocialProviderEmptyOutputTest {
    private val timeoutSeconds = 10L

    @Test
    fun ustagramRejectsBlankAndEllipsisWithoutPostBudgetOrProvenance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedDesktopShellForSmoke(context)
        withWorldStateRestored(context) {
            listOf("", "...").forEach { providerBody ->
                EmptyStructuredServer(providerBody).use { server ->
                    saveStructuredWorldProvider(context, server.baseUrl)
                    val beforeIds = WorldStore(context).use { store ->
                        store.posts("moment").mapTo(mutableSetOf(), SocialPost::id)
                    }
                    val budgetBefore = WorldEngine.budgetUsed(context)

                    val created = awaitBoolean { done ->
                        assertTrue(WorldEngine.generateMomentPost(context, done))
                    }

                    assertFalse("Ustagram accepted invalid Provider body '$providerBody'", created)
                    assertEquals(budgetBefore, WorldEngine.budgetUsed(context))
                    assertNoNewProviderPost(context, "moment", beforeIds)
                    assertEquals(1, server.requests)
                }
            }
        }
    }

    @Test
    fun rebbitRejectsBlankAndEllipsisWithoutPostBudgetOrProvenance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seedDesktopShellForSmoke(context)
        WorldStore(context).use { store ->
            store.ensureDefaultRebbitSubreddits()
            store.setRebbitSubredditEnabled("general", true)
        }
        withWorldStateRestored(context) {
            listOf("", "...").forEach { providerBody ->
                EmptyStructuredServer(providerBody).use { server ->
                    saveStructuredWorldProvider(context, server.baseUrl)
                    val beforeIds = WorldStore(context).use { store ->
                        store.posts("forum").mapTo(mutableSetOf(), SocialPost::id)
                    }
                    val budgetBefore = WorldEngine.budgetUsed(context)

                    val created = awaitBoolean { done ->
                        assertTrue(
                            WorldEngine.generateRebbitPost(
                                context,
                                "Write one forum post for the invalid-output regression test.",
                                done,
                            ),
                        )
                    }

                    assertFalse("Rebbit accepted invalid Provider body '$providerBody'", created)
                    assertEquals(budgetBefore, WorldEngine.budgetUsed(context))
                    assertNoNewProviderPost(context, "forum", beforeIds)
                    assertEquals(1, server.requests)
                }
            }
        }
    }

    private fun saveStructuredWorldProvider(context: Context, baseUrl: String) {
        val capabilities = ProviderCapabilities(
            supported = setOf(ProviderCapability.Structured),
            checkedAt = System.currentTimeMillis(),
        )
        ProviderStore(context).saveTask(
            ProviderTask.World,
            ProviderConfig(
                preset = ProviderPreset.Custom,
                baseUrl = baseUrl,
                model = "empty-output-model",
                apiKey = "test-key",
                capabilities = capabilities,
            ),
        )
    }

    private fun assertNoNewProviderPost(
        context: Context,
        kind: String,
        beforeIds: Set<Long>,
    ) {
        val after = WorldStore(context).use { store -> store.posts(kind) }
        assertEquals(beforeIds, after.mapTo(mutableSetOf(), SocialPost::id))
        assertTrue(
            "invalid output created a post carrying Provider provenance",
            after.none { post ->
                post.id !in beforeIds &&
                    (post.providerName.isNotBlank() || post.modelName.isNotBlank())
            },
        )
    }

    private fun withWorldStateRestored(context: Context, block: () -> Unit) {
        val providerStore = ProviderStore(context)
        val previousWorldProvider = providerStore.loadTask(ProviderTask.World)
        val worldPreferences = context.getSharedPreferences("world_engine", Context.MODE_PRIVATE)
        val previousWorldPreferences = worldPreferences.all.toMap()
        worldPreferences.edit()
            .putBoolean("enabled", true)
            .putBoolean("task_paused", false)
            .putInt("daily_budget", 100)
            .putInt("budget_used", 7)
            .remove("budget_day")
            .putInt("failure_count", 0)
            .remove("last_failure")
            .commit()
        try {
            block()
        } finally {
            if (previousWorldProvider == null) {
                providerStore.clearTask(ProviderTask.World)
            } else {
                providerStore.saveTask(ProviderTask.World, previousWorldProvider)
            }
            restorePreferences(worldPreferences, previousWorldPreferences)
        }
    }

    private fun restorePreferences(
        preferences: SharedPreferences,
        values: Map<String, *>,
    ) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }

    private fun awaitBoolean(block: (((Boolean) -> Unit)) -> Unit): Boolean {
        val latch = CountDownLatch(1)
        var result = false
        block {
            result = it
            latch.countDown()
        }
        assertTrue("Provider callback timeout", latch.await(timeoutSeconds, TimeUnit.SECONDS))
        return result
    }

    private class EmptyStructuredServer(private val generatedBody: String) : AutoCloseable {
        private val socket = ServerSocket(0)
        @Volatile var requests: Int = 0
            private set
        val baseUrl = "http://127.0.0.1:${socket.localPort}/v1"
        private val worker = thread(name = "social-empty-output-http") {
            runCatching {
                socket.accept().use { client ->
                    readRequest(client.getInputStream())
                    requests += 1
                    val structuredContent = JSONObject().put("body", generatedBody).toString()
                    val responseBody = JSONObject()
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
                                    "Content-Length: ${responseBody.size}\r\n" +
                                    "Connection: close\r\n\r\n"
                                ).toByteArray(),
                        )
                        write(responseBody)
                        flush()
                    }
                }
            }
        }

        private fun readRequest(input: InputStream) {
            val headerBytes = ByteArrayOutputStream()
            var tail = ""
            while (true) {
                val next = input.read()
                if (next < 0) return
                headerBytes.write(next)
                tail = (tail + next.toChar()).takeLast(4)
                if (tail == "\r\n\r\n") break
            }
            val headers = headerBytes.toString(Charsets.UTF_8.name())
            val contentLength = Regex("(?im)^content-length:\\s*(\\d+)")
                .find(headers)
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

        override fun close() {
            socket.close()
            worker.join(2_000)
        }
    }
}
