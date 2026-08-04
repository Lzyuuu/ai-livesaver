package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only contract tests. Credentials are read once from filesDir and never logged. */
@RunWith(AndroidJUnit4::class)
class RealProviderInstrumentationTest {
    private val timeout = 90L

    @Test
    fun capabilityAndMessengerOrchestrated() {
        val context = target()
        val config = configureAndVerify(context)
        realStreamCancelInterruptedThenRetrySameMessageAndGroupAppendix(context, config)
    }

    @Test
    fun structuredBinderAndSocialOrchestrated() {
        val context = target()
        val config = configureAndVerify(context)
        realStructuredBinderCandidateValidatesAndCanBeCreatedIdempotently(context, config)
        realSocialGeneration(context)
    }

    private fun configureAndVerify(context: Context): ProviderConfig {
        val config = readConfig(context)
        val results = awaitResult<List<CapabilityResult>> { done -> ProviderCapabilityTester.test(config, done) }
        val required = setOf(ProviderCapability.Chat, ProviderCapability.Streaming, ProviderCapability.Structured)
        assertTrue(results.filter { it.capability in required }.all(CapabilityResult::passed))
        val capabilities = ProviderCapabilities().withResults(results)
        ProviderStore(context).apply { saveTask(ProviderTask.Chat, config.copy(capabilities = capabilities)); saveTask(ProviderTask.World, config.copy(capabilities = capabilities)) }
        assertEquals(required, ProviderStore(context).loadFor(ProviderTask.Chat).capabilities.supported.intersect(required))
        return config.copy(capabilities = capabilities)
    }

    private fun realProviderCapabilitiesPersistForChatAndWorld() {
        val context = target()
        val config = readConfig(context)
        val results = awaitResult<List<CapabilityResult>> { done ->
            ProviderCapabilityTester.test(config, done)
        }
        val required = setOf(ProviderCapability.Chat, ProviderCapability.Streaming, ProviderCapability.Structured)
        assertTrue(results.filter { it.capability in required }.all(CapabilityResult::passed))
        val capabilities = ProviderCapabilities().withResults(results)
        ProviderStore(context).apply {
            saveTask(ProviderTask.Chat, config.copy(capabilities = capabilities))
            saveTask(ProviderTask.World, config.copy(capabilities = capabilities))
        }
        assertEquals(required, ProviderStore(context).loadFor(ProviderTask.Chat).capabilities.supported.intersect(required))
        assertEquals(required, ProviderStore(context).loadFor(ProviderTask.World).capabilities.supported.intersect(required))
    }

    private fun realStreamCancelInterruptedThenRetrySameMessageAndGroupAppendix(context: Context, config: ProviderConfig) {
        require(config.isValid())
        val ids = WorldStore(context).use {
            listOf(
                it.addCharacter("real-${System.nanoTime()}", "A concise helpful resident.", "resident", "", "", ""),
                it.addCharacter("real-${System.nanoTime()}", "A concise thoughtful resident.", "resident", "", "", ""),
            )
        }
        try {
            val first = WorldStore(context).use { it.beginAssistantReply(ids[0], config.preset.displayName, config.model) }
            val cancelled = ProviderStreamHandle()
            val interrupted = awaitRawResult<ProviderResponse> { done ->
                ProviderChatClient.stream(config, character(context, ids[0]), emptyList(), emptyList(), null, emptyList(), emptyList(),
                    MemberWorldContext("user", "", ""), MemberWorldContext("character:${ids[0]}", "", ""), RelationshipState("new", "", null, 0),
                    onDelta = { cancelled.cancel() }, callback = done, handle = cancelled)
            }
            assertTrue(interrupted.isFailure)
            WorldStore(context).use {
                it.interruptAssistantReply(first.id)
                assertEquals("interrupted", it.messages(ids[0], includeRetired = true).single().status)
            }
            val retry = WorldStore(context).use { it.beginAssistantReply(ids[0], config.preset.displayName, config.model, first.id) }
            val response = stream(config, context, ids[0], retry.id)
            assertTrue(response.text.isNotBlank())
            assertEquals(config.model, response.config.model)
            WorldStore(context).use {
                val message = it.messages(ids[0]).single()
                assertEquals("complete", message.status)
                assertEquals(config.preset.displayName, message.providerName)
                assertEquals(config.model, message.modelName)
                assertEquals(1, it.messageVersions(first.id).size)
            }
            ids.forEach { id ->
                val pending = WorldStore(context).use { it.beginAssistantReply(id, config.preset.displayName, config.model) }
                val groupResponse = stream(config, context, id, pending.id, "SECURE_GROUP_MARKER: follow the group safety rule")
                assertTrue(groupResponse.text.isNotBlank())
            }
        } finally {
            WorldStore(context).use { ids.forEach(it::deleteCharacter) }
        }
    }

    private fun realStructuredBinderCandidateValidatesAndCanBeCreatedIdempotently(context: Context, config: ProviderConfig) {
        val response = awaitResult<ProviderResponse> { done ->
            ProviderTextClient.completeStructured(config, "Return JSON only: candidates array with name, persona, relationship, reasons.", "Create one fictional companion candidate.", done)
        }
        val candidates = validateBinderCandidateList(response.text)
        assertTrue(candidates.isNotEmpty())
        val candidate = candidates.first()
        val store = WorldStore(context)
        try {
            val draftId = "real-binder-${System.nanoTime()}"
            val candidateId = "real-candidate-${System.nanoTime()}"
            val first = store.confirmBinderCandidateIdempotently(candidateId, draftId, candidate)
            val second = store.confirmBinderCandidateIdempotently(candidateId, draftId, candidate)
            assertTrue(first > 0)
            assertEquals(first, second)
            assertTrue(store.getConfirmedBinderCandidate(draftId)?.confirmed == true)
            store.deleteCharacter(first)
        } finally { store.close() }
    }

    private fun realSocialGeneration(context: Context) {
        val beforeMoment = WorldStore(context).use { it.posts("moment").size }
        val momentDone = CountDownLatch(1)
        assertTrue(WorldEngine.generateMomentPost(context) { momentDone.countDown() })
        assertTrue(momentDone.await(timeout, TimeUnit.SECONDS))
        val beforeRebbit = WorldStore(context).use { it.posts("forum").size }
        val rebbitDone = CountDownLatch(1)
        assertTrue(WorldEngine.generateRebbitPost(context, "Write one original safe forum post.") { rebbitDone.countDown() })
        assertTrue(rebbitDone.await(timeout, TimeUnit.SECONDS))
        WorldStore(context).use {
            assertTrue(it.posts("moment").size > beforeMoment)
            assertTrue(it.posts("forum").size > beforeRebbit)
            assertTrue(it.posts("moment").first().body.isNotBlank())
            assertTrue(it.posts("forum").first().body.isNotBlank())
        }
    }

    private fun stream(config: ProviderConfig, context: Context, id: Long, replyId: Long, appendix: String = ""): ProviderResponse {
        val response = awaitResult<ProviderResponse> { done ->
            ProviderChatClient.stream(config, character(context, id), emptyList(), emptyList(), null, emptyList(), emptyList(),
                MemberWorldContext("user", "", ""), MemberWorldContext("character:$id", "", ""), RelationshipState("new", "", null, 0), appendix, {}, done)
        }
        WorldStore(context).use { it.completeAssistantReply(replyId, response.text, response.config.preset.displayName, response.config.model) }
        return response
    }

    private fun character(context: Context, id: Long) = WorldStore(context).use { it.characters().first { c -> c.id == id } }
    private fun target() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun readConfig(context: Context): ProviderConfig {
        val file = File(context.filesDir, ".real-provider-test.json")
        val json = JSONObject(file.readText())
        file.delete()
        return ProviderConfig(ProviderPreset.Custom, json.getString("baseUrl"), json.getString("model"), json.getString("apiKey"))
    }

    private fun <T> awaitRawResult(block: ((Result<T>) -> Unit) -> Unit): Result<T> {
        val latch = CountDownLatch(1); var result: Result<T>? = null
        block { result = it; latch.countDown() }
        assertTrue("provider callback timeout", latch.await(timeout, TimeUnit.SECONDS))
        return result!!
    }

    private fun <T> awaitResult(block: ((Result<T>) -> Unit) -> Unit): T = awaitRawResult(block).getOrThrow()
}
