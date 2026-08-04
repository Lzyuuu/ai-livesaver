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
            ).also { groupId ->
                store.addMessage(groupId, "user", "Follow the group scene instruction now.")
            }
        }

        memberIds.forEach { memberId ->
            val pending = WorldStore(context).use {
                it.beginAssistantReply(
                    groupCharacterId,
                    config.preset.displayName,
                    config.model,
                )
            }
            val response = stream(
                config = config,
                context = context,
                characterId = memberId,
                messagesCharacterId = groupCharacterId,
                replyId = pending.id,
                appendix = prompt,
                finalSender = "character:$memberId",
            )
            assertTrue("group prompt marker was not followed", response.text.contains("GROUP_MARKER_804"))
        }

        val reopenedGroup = WorldStore(context).use { it.loadPersistedMessengerGroup(groupRecordId) }
        assertNotNull(reopenedGroup)
        assertEquals(prompt, reopenedGroup!!.prompt)
        assertEquals(memberIds.toSet(), reopenedGroup.memberIds.toSet())
        WorldStore(context).use { store ->
            val replies = store.messages(groupCharacterId).filter {
                it.status == "complete" && it.sender.startsWith("character:")
            }
            assertEquals(2, replies.size)
            assertEquals(memberIds.map { "character:$it" }.toSet(), replies.map { it.sender }.toSet())
            assertTrue(replies.all { it.body.contains("GROUP_MARKER_804") })
            assertTrue(replies.all { it.providerName.isNotBlank() && it.modelName == config.model })
            store.deleteCharacter(groupCharacterId)
        }
    }

    private fun verifyBinder(context: Context, config: ProviderConfig) {
        val response = awaitResult<ProviderResponse> { done ->
            ProviderTextClient.completeStructured(
                config,
                "Generate fictional companion candidates. The body string itself must be JSON matching " +
                    "{candidates:[{name,persona,relationship,reasons:[string]}]}.",
                "Create exactly two concise, distinct candidates. Keep every field short.",
                done,
            )
        }
        val candidates = validateBinderCandidateList(response.text)
        assertTrue(candidates.size >= 2)
        val draftId = "real-binder-${System.nanoTime()}"
        val candidateId = "$draftId-0"
        WorldStore(context).use { store ->
            store.putBinderDraft(
                BinderDraft(
                    id = draftId,
                    step = 5,
                    payload = BinderAnswers(relationship = "real Provider acceptance").toJson(),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val first = store.confirmBinderCandidateIdempotently(
                candidateId,
                draftId,
                candidates.first(),
            )
            val second = store.confirmBinderCandidateIdempotently(
                candidateId,
                draftId,
                candidates.first(),
            )
            assertTrue(first > 0)
            assertEquals(first, second)
            assertEquals(candidateId, store.getConfirmedBinderCandidate(draftId)?.id)
            assertTrue(store.characters().any { it.id == first })
            store.deleteCharacter(first)
        }
    }

    private fun verifySocialGeneration(context: Context, config: ProviderConfig) {
        seedDesktopShellForSmoke(context)
        val worldPreferences = context.getSharedPreferences("world_engine", Context.MODE_PRIVATE)
        val previousEnabled = WorldEngine.isEnabled(context)
        val previousBudget = WorldEngine.dailyBudget(context)
        // Avoid racing the direct acceptance call with the scheduled world job that
        // setEnabled(true) would enqueue. Configure the same persisted runtime state directly.
        WorldEngine.setEnabled(context, false)
        worldPreferences.edit()
            .putBoolean("enabled", true)
            .putBoolean("task_paused", false)
            .putInt("daily_budget", 0)
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
                    done,
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
        worldPreferences.edit()
            .putBoolean("enabled", previousEnabled)
            .putInt("daily_budget", previousBudget)
            .apply()
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
}
