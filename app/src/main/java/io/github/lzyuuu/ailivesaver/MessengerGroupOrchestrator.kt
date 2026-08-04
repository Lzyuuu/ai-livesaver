package io.github.lzyuuu.ailivesaver

import android.content.Context

/** Group definition consumed by both the Messenger UI and its shipped orchestration. */
internal data class MessengerGroupSpec(
    val id: Long,
    val memberIds: List<Long>,
    val prompt: String,
)

/** Handles for every in-flight member reply in one group send. */
internal class MessengerGroupDispatch internal constructor(
    val handles: Map<Long, ProviderStreamHandle>,
) {
    fun cancelAll() = handles.values.forEach(ProviderStreamHandle::cancel)
}

/**
 * The production Messenger group-send boundary.
 *
 * It owns the shared user message, one pending reply per member, Provider context assembly,
 * group-prompt delivery, member attribution, and final persistence. Compose only renders state;
 * device tests call this exact boundary rather than duplicating its orchestration.
 */
internal object MessengerGroupOrchestrator {
    fun send(
        context: Context,
        groupCharacterId: Long,
        group: MessengerGroupSpec,
        body: String,
        onHandlesChanged: (Map<Long, ProviderStreamHandle>) -> Unit = {},
        onMemberFinished: (memberId: Long, messageId: Long, result: Result<ProviderResponse>) -> Unit =
            { _, _, _ -> },
    ): Result<MessengerGroupDispatch> = runCatching {
        val appContext = context.applicationContext
        val cleanBody = body.trim()
        require(cleanBody.isNotEmpty()) { "Group message is empty" }

        data class PreparedReply(
            val member: ResidentCharacter,
            val messageId: Long,
            val messages: List<ChatMessage>,
            val memories: List<LongTermMemory>,
            val recap: ConversationRecap?,
            val worldFacts: List<WorldFact>,
            val cognition: List<CharacterCognition>,
            val userContext: MemberWorldContext,
            val characterContext: MemberWorldContext,
            val relationship: RelationshipState,
            val handle: ProviderStreamHandle,
        )

        val config = ProviderStore(appContext).loadFor(ProviderTask.Chat)
        val prepared = WorldStore(appContext).use { store ->
            val resolved = if (group.id > 0) {
                store.loadPersistedMessengerGroup(group.id)
                    ?: error("Messenger group no longer exists")
            } else {
                PersistedMessengerGroup(0L, "legacy", group.prompt, group.memberIds)
            }
            require(resolved.prompt == group.prompt) { "Group prompt is stale" }
            val memberIds = resolved.memberIds.distinct()
            require(memberIds == group.memberIds.distinct()) { "Group membership is stale" }
            require(memberIds.size >= 2) { "Select at least two active group members" }
            val members = store.characters().filter { it.id in memberIds }
            require(members.size == memberIds.size) { "A group member is no longer active" }
            val groupCharacter = store.characters().firstOrNull { it.id == groupCharacterId }
                ?: error("Group conversation no longer exists")
            if (resolved.id > 0) {
                require(groupCharacter.cardJson == "group:${resolved.id}") {
                    "Group conversation does not match its persisted group"
                }
            }

            store.addMessage(groupCharacterId, "user", cleanBody)
            members.map { member ->
                val pending = store.beginAssistantReply(
                    groupCharacterId,
                    config.preset.displayName,
                    config.model,
                )
                PreparedReply(
                    member = member,
                    messageId = pending.id,
                    messages = store.messages(groupCharacterId),
                    memories = store.memories(member.id),
                    recap = store.conversationRecap(member.id),
                    worldFacts = store.worldFacts(),
                    cognition = store.characterCognition(member.id),
                    userContext = store.memberWorldContext("user"),
                    characterContext = store.memberWorldContext("character:${member.id}"),
                    relationship = store.relationship(member.id),
                    handle = ProviderStreamHandle(),
                )
            }
        }

        val activeHandles = prepared.associate { it.member.id to it.handle }.toMutableMap()
        onHandlesChanged(activeHandles.toMap())
        prepared.forEach { reply ->
            ProviderChatClient.stream(
                config = config,
                character = reply.member,
                messages = reply.messages,
                memories = reply.memories,
                recap = reply.recap,
                worldFacts = reply.worldFacts,
                cognition = reply.cognition,
                userContext = reply.userContext,
                characterContext = reply.characterContext,
                relationship = reply.relationship,
                systemPromptAppendix = group.prompt,
                onDelta = {},
                handle = reply.handle,
                callback = { providerResult ->
                    val persistedResult = providerResult.mapCatching { response ->
                        val text = response.text.trim()
                        require(text.isNotEmpty()) { "Provider returned an empty reply" }
                        WorldStore(appContext).use { callbackStore ->
                            callbackStore.completeAssistantReply(
                                reply.messageId,
                                text,
                                response.config.preset.displayName,
                                response.config.model,
                                finalSender = "character:${reply.member.id}",
                            )
                        }
                        response.copy(text = text)
                    }
                    persistedResult.exceptionOrNull()?.let { failure ->
                        WorldStore(appContext).use { callbackStore ->
                            if (failure is ProviderStreamCancelledException || reply.handle.isCancelled()) {
                                callbackStore.interruptAssistantReply(reply.messageId)
                            } else {
                                callbackStore.failAssistantReply(reply.messageId, failure.message.orEmpty())
                            }
                        }
                    }
                    activeHandles.remove(reply.member.id)
                    onHandlesChanged(activeHandles.toMap())
                    onMemberFinished(reply.member.id, reply.messageId, persistedResult)
                },
            )
        }
        MessengerGroupDispatch(prepared.associate { it.member.id to it.handle })
    }
}
