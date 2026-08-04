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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in device tests for a real Provider. The host stages credentials in the app-private
 * files directory before selecting one test method. Missing credentials skip during the normal
 * connected suite; staged credentials make every capability or request failure a hard failure.
 */
@RunWith(AndroidJUnit4::class)
class RealProviderInstrumentationTest {
    private val timeoutSeconds = 90L

    @Test
    fun capabilityAndMessengerOrchestrated() {
        val context = target()
        val config = configureAndVerify(context)
        verifyMessenger(context, config)
    }

    @Test
    fun structuredBinderAndSocialOrchestrated() {
        val context = target()
        val config = configureAndVerify(context)
        verifyBinder(context, config)
        verifySocialGeneration(context, config)
    }

    private fun configureAndVerify(context: Context): ProviderConfig {
        val config = readStagedConfig(context)
        val results = awaitResult<List<CapabilityResult>> { done ->
            ProviderCapabilityTester.test(config, done)
        }
        val required = setOf(
            ProviderCapability.Chat,
            ProviderCapability.Streaming,
            ProviderCapability.Structured,
        )
        val passed = results.filter(CapabilityResult::passed).mapTo(mutableSetOf()) {
            it.capability
        }
        assertTrue("required Provider capabilities did not pass", passed.containsAll(required))

        val qualified = config.copy(capabilities = ProviderCapabilities().withResults(results))
        ProviderStore(context).apply {
            saveTask(ProviderTask.Chat, qualified)
            saveTask(ProviderTask.World, qualified)
        }
        val reloadedChat = ProviderStore(context).loadTask(ProviderTask.Chat)
        val reloadedWorld = ProviderStore(context).loadTask(ProviderTask.World)
        assertNotNull(reloadedChat)
        assertNotNull(reloadedWorld)
        assertTrue(reloadedChat!!.isValid())
        assertTrue(reloadedWorld!!.isValid())
        assertTrue(reloadedChat.capabilities.supported.containsAll(required))
        assertTrue(reloadedWorld.capabilities.supported.containsAll(required))
        assertTrue(reloadedChat.capabilities.checkedAt > 0)
        assertTrue(reloadedWorld.capabilities.checkedAt > 0)
        return qualified
    }

    private fun verifyMessenger(context: Context, config: ProviderConfig) {
        val stamp = System.nanoTime()
        val memberIds = WorldStore(context).use {
            listOf(
                it.addCharacter("RealAlpha-$stamp", "Concise and precise.", "resident", "", "", ""),
                it.addCharacter("RealBeta-$stamp", "Warm and thoughtful.", "resident", "", "", ""),
            )
        }
        try {
            verifyCancelAndRetry(context, config, memberIds.first())
            verifySharedGroupTimeline(context, config, memberIds, stamp)
        } finally {
            WorldStore(context).use { store -> memberIds.forEach(store::deleteCharacter) }
        }
    }

    private fun verifyCancelAndRetry(context: Context, config: ProviderConfig, characterId: Long) {
        val pending = WorldStore(context).use { store ->
            store.addMessage(characterId, "user", "Give one short live reply.")
            store.beginAssistantReply(characterId, config.preset.displayName, config.model)
        }
        val handle = ProviderStreamHandle()
        val cancelled = awaitRawResult<ProviderResponse> { done ->
            ProviderChatClient.stream(
                config = config,
                character = character(context, characterId),
                messages = WorldStore(context).use { it.messages(characterId) },
                memories = emptyList(),
                recap = null,
                worldFacts = emptyList(),
                cognition = emptyList(),
                userContext = MemberWorldContext("user", "", ""),
                characterContext = MemberWorldContext("character:$characterId", "", ""),
                relationship = RelationshipState("new", "", null, 0),
                onDelta = { handle.cancel() },
                callback = done,
                handle = handle,
            )
        }
        assertTrue("cancelled stream unexpectedly completed", cancelled.isFailure)
        WorldStore(context).use { store ->
            store.interruptAssistantReply(pending.id)
            assertEquals("interrupted", store.messages(characterId).last().status)
        }

        val retry = WorldStore(context).use {
            it.beginAssistantReply(
                characterId,
                config.preset.displayName,
                config.model,
                pending.id,
            )
        }
        val response = stream(
            config = config,
            context = context,
            characterId = characterId,
            messagesCharacterId = characterId,
            replyId = retry.id,
        )
        assertTrue(response.text.isNotBlank())
        WorldStore(context).use { store ->
            val persisted = store.messages(characterId).last()
            assertEquals(pending.id, persisted.id)
            assertEquals("complete", persisted.status)
            assertEquals(config.preset.displayName, persisted.providerName)
            assertEquals(config.model, persisted.modelName)
            assertEquals(1, store.messageVersions(pending.id).size)
        }
        // Reopen the shipped database and prove the completed reply survives the store lifecycle.
        assertEquals(
            "complete",
            WorldStore(context).use { it.messages(characterId).last().status },
        )
    }

    private fun verifySharedGroupTimeline(
        context: Context,
        config: ProviderConfig,
        memberIds: List<Long>,
        stamp: Long,
    ) {
        val prompt = "Your entire reply must be exactly GROUP_MARKER_804."
        val groupRecordId = WorldStore(context).use {
            it.savePersistedMessengerGroup(
                PersistedMessengerGroup(0L, "Real Group $stamp", prompt, memberIds),
            )
        }
        val groupCharacterId = WorldStore(context).use { store ->
            store.addCharacter(
                "Real Group $stamp",
                "Real Provider group acceptance timeline.",
                "resident",
                "",
                "",
                "",
                "group:$groupRecordId",
            )
        }

        val finished = CountDownLatch(memberIds.size)
        val results = mutableMapOf<Long, Result<ProviderResponse>>()
        val dispatch = MessengerGroupOrchestrator.send(
            context = context,
            groupCharacterId = groupCharacterId,
            group = MessengerGroupSpec(groupRecordId, memberIds, prompt),
            body = "Follow the group scene instruction now.",
            onMemberFinished = { memberId, _, result ->
                synchronized(results) { results[memberId] = result }
                finished.countDown()
            },
        ).getOrThrow()
        assertEquals(memberIds.toSet(), dispatch.handles.keys)
        assertTrue(
            "production group orchestration timed out",
            finished.await(timeoutSeconds, TimeUnit.SECONDS),
        )
        val completed = synchronized(results) { results.toMap() }
        assertEquals(memberIds.toSet(), completed.keys)
        completed.values.forEach { result ->
            val response = result.getOrThrow()
            assertTrue("group prompt marker was not followed", response.text.contains("GROUP_MARKER_804"))
        }

        val reopenedGroup = WorldStore(context).use { it.loadPersistedMessengerGroup(groupRecordId) }
        assertNotNull(reopenedGroup)
        assertEquals(prompt, reopenedGroup!!.prompt)
        assertEquals(memberIds.toSet(), reopenedGroup.memberIds.toSet())
        WorldStore(context).use { store ->
            val timeline = store.messages(groupCharacterId)
            val userMessages = timeline.filter { it.sender == "user" }
            val replies = timeline.filter {
                it.status == "complete" && it.sender.startsWith("character:")
            }
            assertEquals(1, userMessages.size)
            assertEquals("Follow the group scene instruction now.", userMessages.single().body)
            assertEquals(2, replies.size)
            assertEquals(memberIds.map { "character:$it" }.toSet(), replies.map { it.sender }.toSet())
            assertTrue(replies.all { it.body.contains("GROUP_MARKER_804") })
            assertTrue(replies.all { it.providerName.isNotBlank() && it.modelName == config.model })
            store.deleteCharacter(groupCharacterId)
        }
    }

    private fun verifyBinder(context: Context, config: ProviderConfig) {
        val draftId = "real-binder-${System.nanoTime()}"
        val answers = BinderAnswers(
            relationship = "real Provider acceptance",
            preferences = "Create two concise and distinct candidates.",
            personality = "Keep every field short.",
        )
        val generation = awaitResult<BinderGeneration> { done ->
            BinderOrchestrator.generate(context, draftId, answers, done)
        }
        assertTrue(
            "production Binder orchestration accepted fewer than two candidates",
            generation.candidates.size >= 2,
        )
        assertEquals(config.preset.displayName, generation.providerName)
        assertEquals(config.model, generation.modelName)

        val candidateId = "$draftId-0"
        val first = BinderOrchestrator.confirm(context, draftId, 0, generation.candidates.first())
        val second = BinderOrchestrator.confirm(context, draftId, 0, generation.candidates.first())
        assertTrue(first > 0)
        assertEquals(first, second)
        WorldStore(context).use { store ->
            assertEquals(5, store.getBinderDraft(draftId)?.step)
            assertEquals(candidateId, store.getConfirmedBinderCandidate(draftId)?.id)
            assertTrue(store.characters().any { it.id == first })
            store.deleteCharacter(first)
        }
    }

    private fun verifySocialGeneration(context: Context, config: ProviderConfig) {
        seedDesktopShellForSmoke(context)
        val worldPreferences = context.getSharedPreferences("world_engine", Context.MODE_PRIVATE)
        val previousWorldPreferences = worldPreferences.all.toMap()
        // Avoid racing the direct acceptance call with a scheduled world job and make
        // Ustagram persistence deterministic: event_count=0 never queues attached media.
        WorldEngine.setEnabled(context, false)
        worldPreferences.edit()
            .putBoolean("enabled", true)
            .putBoolean("task_paused", false)
            .putInt("daily_budget", 0)
            .putInt("event_count", 0)
            .putInt("failure_count", 0)
            .remove("last_failure")
            .commit()

        val momentIds = WorldStore(context).use { store ->
            store.posts("moment").mapTo(mutableSetOf(), SocialPost::id)
        }
        val momentCreated = awaitBoolean { done ->
            assertTrue(WorldEngine.generateMomentPost(context, done))
        }
        assertTrue("Ustagram real generation failed", momentCreated)
        val moment = WorldStore(context).use { store ->
            store.posts("moment").first { it.id !in momentIds }
        }
        assertRealProviderPost(moment, config, "moment")

        val forumIds = WorldStore(context).use { store ->
            store.posts("forum").mapTo(mutableSetOf(), SocialPost::id)
        }
        val forumCreated = awaitBoolean { done ->
            assertTrue(
                WorldEngine.generateRebbitPost(
                    context,
                    "Write one original safe forum post for this real integration test.",
                    done,
                ),
            )
        }
        assertTrue("Rebbit real generation failed", forumCreated)
        val forum = WorldStore(context).use { store ->
            store.posts("forum").first { it.id !in forumIds }
        }
        assertRealProviderPost(forum, config, "forum")

        val yIds = WorldStore(context).use { store ->
            store.posts(Y_POST_KIND).mapTo(mutableSetOf(), SocialPost::id)
        }
        val yCreated = awaitBoolean { done ->
            assertTrue(
                generateYPost(
                    context,
                    "Write one original short public update for this real integration test.",
                    callback = done,
                ),
            )
        }
        assertTrue("Y real generation failed", yCreated)
        val yPost = WorldStore(context).use { store ->
            store.posts(Y_POST_KIND).first { it.id !in yIds }
        }
        assertRealProviderPost(yPost, config, Y_POST_KIND)

        WorldStore(context).use { store ->
            store.deleteAiPost(moment.id)
            store.deleteAiPost(forum.id)
            store.deleteAiPost(yPost.id)
        }
        restorePreferences(worldPreferences, previousWorldPreferences)
    }

    private fun restorePreferences(
        preferences: android.content.SharedPreferences,
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

    private fun assertRealProviderPost(post: SocialPost, config: ProviderConfig, kind: String) {
        assertEquals(kind, post.kind)
        assertEquals("resident", post.authorKind)
        assertTrue(post.body.isNotBlank())
        assertFalse(post.body == "...")
        assertFalse(post.body == "✨")
        assertEquals(config.preset.displayName, post.providerName)
        assertEquals(config.model, post.modelName)
    }

    private fun stream(
        config: ProviderConfig,
        context: Context,
        characterId: Long,
        messagesCharacterId: Long,
        replyId: Long,
        appendix: String = "",
        finalSender: String = "assistant",
    ): ProviderResponse {
        val response = awaitResult<ProviderResponse> { done ->
            ProviderChatClient.stream(
                config = config,
                character = character(context, characterId),
                messages = WorldStore(context).use { it.messages(messagesCharacterId) },
                memories = emptyList(),
                recap = null,
                worldFacts = emptyList(),
                cognition = emptyList(),
                userContext = MemberWorldContext("user", "", ""),
                characterContext = MemberWorldContext("character:$characterId", "", ""),
                relationship = RelationshipState("new", "", null, 0),
                systemPromptAppendix = appendix,
                onDelta = {},
                callback = done,
            )
        }
        WorldStore(context).use {
            it.completeAssistantReply(
                replyId,
                response.text,
                response.config.preset.displayName,
                response.config.model,
                finalSender,
            )
        }
        return response
    }

    private fun character(context: Context, id: Long) = WorldStore(context).use {
        it.characters().first { character -> character.id == id }
    }

    private fun target() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun readStagedConfig(context: Context): ProviderConfig {
        stagedConfig?.let { return it }
        synchronized(stagedConfigLock) {
            stagedConfig?.let { return it }
            val file = File(context.filesDir, ".real-provider-test.json")
            assumeTrue("real Provider credentials were not staged", file.isFile)
            val raw = try {
                file.readText()
            } finally {
                assertTrue("staged Provider credentials were not deleted", file.delete() || !file.exists())
            }
            val json = JSONObject(raw)
            return ProviderConfig(
                preset = ProviderPreset.Custom,
                baseUrl = json.getString("baseUrl"),
                model = json.getString("model"),
                apiKey = json.getString("apiKey"),
            ).also {
                assertTrue("staged Provider configuration is invalid", it.isValid())
                stagedConfig = it
            }
        }
    }

    private fun awaitBoolean(block: ((Boolean) -> Unit) -> Unit): Boolean {
        val latch = CountDownLatch(1)
        var result = false
        block {
            result = it
            latch.countDown()
        }
        assertTrue("Provider callback timeout", latch.await(timeoutSeconds, TimeUnit.SECONDS))
        return result
    }

    private fun <T> awaitRawResult(block: ((Result<T>) -> Unit) -> Unit): Result<T> {
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        block {
            result = it
            latch.countDown()
        }
        assertTrue("Provider callback timeout", latch.await(timeoutSeconds, TimeUnit.SECONDS))
        return checkNotNull(result) { "Provider callback returned no result" }
    }

    private fun <T> awaitResult(block: ((Result<T>) -> Unit) -> Unit): T =
        awaitRawResult(block).getOrThrow()

    private companion object {
        val stagedConfigLock = Any()

        @Volatile
        var stagedConfig: ProviderConfig? = null
    }
}
