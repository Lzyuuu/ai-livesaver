package io.github.lzyuuu.ailivesaver

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private object ActiveChatReplies {
    private val characterIds = mutableSetOf<Long>()

    fun contains(characterId: Long) = characterId in characterIds
    fun add(characterId: Long) = characterIds.add(characterId)
    fun remove(characterId: Long) = characterIds.remove(characterId)
}

private fun captureLongTermMemory(
    context: android.content.Context,
    store: WorldStore,
    character: ResidentCharacter,
    message: ChatMessage,
    onChanged: () -> Unit,
) {
    MemoryExtractor.fromUserMessage(message.body)?.let { explicit ->
        store.rememberIfCurrent(character.id, message.id, explicit)
        return
    }
    if (!MemoryExtractor.shouldInspect(message.body)) return
    val config = ProviderStore(context).loadFor(ProviderTask.Memory)
    if (!config.supports(ProviderCapability.Structured)) return
    ProviderTextClient.completeStructured(
        config,
        "Extract only durable facts the user explicitly shares for future conversation context. " +
            "Do not infer preferences, identity or plans that are not clearly stated. " +
            "Return NONE when this message contains no durable fact.",
        "User message:\n${message.body}\nReturn one concise fact in body, or NONE.",
    ) { result ->
        val memory = result.getOrNull()?.text?.let(MemoryExtractor::fromProvider)
            ?: return@completeStructured
        val saved = runCatching {
            WorldStore(context.applicationContext).use { callbackStore ->
                if (!callbackStore.rememberIfCurrent(character.id, message.id, memory)) {
                    false
                } else {
                    callbackStore.recordConversationRelationship(
                        character.id,
                        message.id,
                        sharedPersonalFact = true,
                    )
                    true
                }
            }
        }.getOrDefault(false)
        if (saved) {
            onChanged()
        }
    }
}

@Composable
internal fun ChatsScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    initialCharacterId: Long?,
    onInitialCharacterConsumed: () -> Unit,
    onChanged: () -> Unit,
    onConfigureProvider: () -> Unit,
    onManageCharacters: () -> Unit,
) {
    val characters = remember(revision) { store.characters(includeDeparted = false) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(initialCharacterId, characters) {
        val requestedId = initialCharacterId ?: return@LaunchedEffect
        if (characters.any { it.id == requestedId }) selectedId = requestedId
        onInitialCharacterConsumed()
    }
    val character = selectedId?.let { id -> characters.firstOrNull { it.id == id } }
    when {
        character != null -> {
        key(character.id) {
            ConversationScreen(
                contentPadding,
                store,
                characters,
                character,
                revision,
                onChanged,
                onCharacterSelected = { selectedId = it },
                onBack = { selectedId = null },
            )
        }
        }
        characters.isEmpty() -> {
            FirstRelationshipScreen(
                contentPadding,
                store,
                revision,
                onChanged,
                onConfigureProvider,
            )
        }
        else -> {
            ChatListScreen(
                contentPadding = contentPadding,
                store = store,
                characters = characters,
                revision = revision,
                onCharacterSelected = { selectedId = it },
                onManageCharacters = onManageCharacters,
            )
        }
    }
}

@Composable
private fun ChatListScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    characters: List<ResidentCharacter>,
    revision: Int,
    onCharacterSelected: (Long) -> Unit,
    onManageCharacters: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val latestMessages = remember(revision, characters) {
        characters.associate { it.id to store.messages(it.id).lastOrNull() }
    }
    val visibleCharacters = characters
        .filter { character ->
            query.isBlank() ||
                character.name.contains(query, ignoreCase = true) ||
                latestMessages[character.id]?.body.orEmpty().contains(query, ignoreCase = true)
        }
        .sortedWith(
            compareByDescending<ResidentCharacter> { latestMessages[it.id]?.createdAt ?: 0L }
                .thenByDescending { it.attentionTier == "special_focus" },
        )
    val timeFormatter = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.chats_eyebrow),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chats_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onManageCharacters) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.manage_characters))
                }
            }
            Text(
                stringResource(R.string.chats_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_conversations)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (visibleCharacters.isEmpty()) {
            item { StatusCard(stringResource(R.string.no_matching_conversations)) }
        } else {
            items(visibleCharacters, key = ResidentCharacter::id) { resident ->
                val latestMessage = latestMessages[resident.id]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chat-character-${resident.id}")
                        .clickable { onCharacterSelected(resident.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(resident.name.take(1).uppercase(), 56.dp)
                        Column(Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    resident.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                latestMessage?.let {
                                    Text(
                                        timeFormatter.format(Date(it.createdAt)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            Text(
                                latestMessage?.body?.ifBlank { latestMessage.draftBody }
                                    ?: stringResource(R.string.no_messages_yet),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (resident.attentionTier == "special_focus") {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.special_focus),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FirstRelationshipScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onChanged: () -> Unit,
    onConfigureProvider: () -> Unit,
) {
    val context = LocalContext.current
    val providerReady = remember(revision) { ProviderStore(context).load().isValid() }
    var userName by rememberSaveable { mutableStateOf("") }
    var characterName by rememberSaveable { mutableStateOf("") }
    var persona by rememberSaveable { mutableStateOf("") }
    var importedCard by remember { mutableStateOf<ImportedCharacterCard?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val completeFields = stringResource(R.string.complete_world_fields)
    val importFailed = stringResource(R.string.character_card_import_failed)
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use(CharacterCardV2::read)
                ?: kotlin.error("无法读取文件")
            CharacterCardV2.parse(bytes)
        }.onSuccess { card ->
            importedCard = card
            characterName = card.name
            persona = card.persona
            error = null
        }.onFailure {
            error = "$importFailed：${it.message.orEmpty()}"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.chats_eyebrow),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.chats_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.first_relationship_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.first_relationship_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!providerReady) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.world_creation_path),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        FirstRelationshipStep(
                            number = "1",
                            title = stringResource(R.string.connect_inference),
                            summary = stringResource(R.string.connect_inference_summary),
                        )
                        FirstRelationshipStep(
                            number = "2",
                            title = stringResource(R.string.choose_first_character),
                            summary = stringResource(R.string.choose_first_character_summary),
                        )
                        FirstRelationshipStep(
                            number = "3",
                            title = stringResource(R.string.begin_shared_life),
                            summary = stringResource(R.string.begin_shared_life_summary),
                        )
                        Text(
                            stringResource(R.string.provider_required_for_world),
                            fontWeight = FontWeight.Bold,
                        )
                        Button(onClick = onConfigureProvider) {
                            Text(stringResource(R.string.configure_provider))
                        }
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text(stringResource(R.string.your_world_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = characterName,
                    onValueChange = { characterName = it },
                    label = { Text(stringResource(R.string.character_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    label = { Text(stringResource(R.string.character_persona)) },
                    supportingText = { Text(stringResource(R.string.character_persona_hint)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                TextButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "image/png")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_character_card))
                }
                importedCard?.let { card ->
                    Text(
                        stringResource(R.string.first_relationship_card_loaded, card.name),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        if (userName.isBlank() || characterName.isBlank() || persona.isBlank()) {
                            error = completeFields
                        } else {
                            val character = store.createWorld(
                                userName,
                                characterName,
                                persona,
                                importedCard?.rawJson.orEmpty(),
                            )
                            importedCard?.let { card ->
                                if (card.firstMessage.isNotBlank()) {
                                    store.addMessage(character.id, "assistant", card.firstMessage)
                                }
                                card.lore.forEach { lore ->
                                    store.addCharacterCognition(character.id, lore)
                                }
                            }
                            onChanged()
                            WorldEngine.generate(context) { if (it) onChanged() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.create_world))
                }
            }
            error?.let { message -> item { StatusCard(message) } }
        }
    }
}

@Composable
private fun FirstRelationshipStep(
    number: String,
    title: String,
    summary: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConversationScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    characters: List<ResidentCharacter>,
    character: ResidentCharacter,
    revision: Int,
    onChanged: () -> Unit,
    onCharacterSelected: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val provider = remember { ProviderStore(context) }
    val messages = remember(revision) { store.messages(character.id) }
    val retiredMessages = remember(revision) { store.retiredMessages(character.id) }
    val memories = remember(revision) { store.memories(character.id) }
    val recap = remember(revision) { store.conversationRecap(character.id) }
    val relationship = remember(revision) { store.relationship(character.id) }
    val relationshipEvents = remember(revision) { store.relationshipEvents(character.id) }
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showContext by rememberSaveable { mutableStateOf(false) }
    var streamingMessageId by remember { mutableStateOf<Long?>(null) }
    var streamingText by remember { mutableStateOf("") }
    var rewritingMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var rewriteText by rememberSaveable { mutableStateOf("") }
    var expandedVersionsId by rememberSaveable { mutableStateOf<Long?>(null) }
    val formatter = remember {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    }
    val sending = messages.any { it.status == "streaming" } ||
        ActiveChatReplies.contains(character.id)

    fun requestReply(existingMessageId: Long? = null) {
        if (ActiveChatReplies.contains(character.id)) return
        val config = provider.loadFor(ProviderTask.Chat)
        error = null
        streamingText = ""
        val reply = runCatching {
            store.beginAssistantReply(
                character.id,
                config.preset.displayName,
                config.model,
                existingMessageId,
            )
        }.getOrElse {
            error = it.message.orEmpty()
            return
        }
        ActiveChatReplies.add(character.id)
        streamingMessageId = reply.id
        onChanged()
        var lastPersistedAt = 0L
        ProviderChatClient.stream(
            config = config,
            character = character,
            messages = store.messages(character.id),
            memories = store.memories(character.id),
            recap = store.conversationRecap(character.id),
            worldFacts = store.worldFacts(),
            cognition = store.characterCognition(character.id),
            userContext = store.memberWorldContext("user"),
            characterContext = store.memberWorldContext("character:${character.id}"),
            relationship = relationship,
            onDelta = { body ->
                streamingText = body
                val now = System.currentTimeMillis()
                if (now - lastPersistedAt >= 500) {
                    runCatching {
                        WorldStore(context.applicationContext).use {
                            it.updateAssistantDraft(reply.id, body)
                        }
                    }
                    lastPersistedAt = now
                }
            },
        ) { result ->
            try {
                result.fold(
                    onSuccess = { response ->
                        runCatching {
                            WorldStore(context.applicationContext).use {
                                it.completeAssistantReply(
                                    reply.id,
                                    response.text,
                                    response.config.preset.displayName,
                                    response.config.model,
                                )
                            }
                        }.onFailure {
                            runCatching {
                                WorldStore(context.applicationContext).use { callbackStore ->
                                    callbackStore.failAssistantReply(reply.id, it.message.orEmpty())
                                }
                            }
                            error = it.message.orEmpty()
                        }
                    },
                    onFailure = {
                        runCatching {
                            WorldStore(context.applicationContext).use { callbackStore ->
                                callbackStore.failAssistantReply(reply.id, it.message.orEmpty())
                            }
                        }
                        error = it.message.orEmpty()
                    },
                )
            } finally {
                ActiveChatReplies.remove(character.id)
                streamingMessageId = null
                streamingText = ""
                onChanged()
            }
        }
    }

    LaunchedEffect(character.id) {
        if (
            !ActiveChatReplies.contains(character.id) &&
            store.recoverInterruptedReplies(character.id) > 0
        ) {
            onChanged()
        }
    }

    rewritingMessageId?.let { messageId ->
        val source = messages.firstOrNull { it.id == messageId && it.sender == "user" }
        if (source == null) {
            rewritingMessageId = null
        } else {
            val pinnedCount = store.pinnedMemoriesFrom(character.id, messageId)
            AlertDialog(
                onDismissRequest = { rewritingMessageId = null },
                title = { Text(stringResource(R.string.rewrite_from_here)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.rewrite_timeline_warning))
                        if (pinnedCount > 0) {
                            Text(
                                stringResource(R.string.rewrite_pinned_memories, pinnedCount),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedTextField(
                            value = rewriteText,
                            onValueChange = { rewriteText = it },
                            minLines = 3,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val body = rewriteText.trim()
                            if (body.isEmpty()) return@TextButton
                            val newMessage = store.rewriteFromMessage(
                                character.id,
                                messageId,
                                body,
                            )
                            val extractedMemory = MemoryExtractor.fromUserMessage(body)
                            extractedMemory?.let {
                                store.rememberIfCurrent(character.id, newMessage.id, it)
                            }
                            store.recordConversationRelationship(
                                character.id,
                                newMessage.id,
                                extractedMemory != null,
                                messageBody = body,
                            )
                            captureLongTermMemory(context, store, character, newMessage, onChanged)
                            rewritingMessageId = null
                            expandedVersionsId = null
                            onChanged()
                            requestReply()
                        },
                        enabled = rewriteText.isNotBlank() && !sending,
                    ) {
                        Text(stringResource(R.string.confirm_rewrite))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { rewritingMessageId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    BackHandler(enabled = !showContext) { onBack() }
    BackHandler(showContext) { showContext = false }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastMessageIndex = messages.lastIndex + if (characters.size > 1) 2 else 1
            if (systemAnimationsEnabled(context)) {
                listState.animateScrollToItem(lastMessageIndex)
            } else {
                listState.scrollToItem(lastMessageIndex)
            }
        }
    }

    if (showContext) {
        ConversationContextScreen(
            contentPadding = contentPadding,
            character = character,
            messages = messages.filter { it.status == "complete" },
            retiredMessages = retiredMessages,
            recap = recap,
            memories = memories,
            relationship = relationship,
            relationshipEvents = relationshipEvents,
            onBack = { showContext = false },
            onSaveRecap = { body, throughMessageId ->
                runCatching {
                    WorldStore(context.applicationContext).use {
                        it.saveConversationRecapIfCurrent(character.id, body, throughMessageId)
                    }
                }.getOrDefault(false).also { saved ->
                    if (saved) onChanged()
                }
            },
            onUpdateRecap = { body ->
                store.updateConversationRecap(character.id, body)
                onChanged()
            },
            onPinRecap = { pinned ->
                store.setConversationRecapPinned(character.id, pinned)
                onChanged()
            },
            onUpdate = { id, body ->
                store.updateMemory(id, body)
                onChanged()
            },
            onPin = { id, pinned ->
                store.setMemoryPinned(id, pinned)
                onChanged()
            },
            onDelete = {
                store.deleteMemory(it)
                onChanged()
            },
            onCorrectRelationship = { label, summary, pinned ->
                store.correctRelationship(character.id, label, summary, pinned)
                onChanged()
            },
            onPinRelationship = { pinned ->
                store.setRelationshipPinned(character.id, pinned)
                onChanged()
            },
        )
        return
    }

    fun sendMessage() {
        val body = input.trim()
        if (body.isEmpty() || sending) return
        input = ""
        error = null
        val userMessage = store.addMessage(character.id, "user", body)
        val extractedMemory = MemoryExtractor.fromUserMessage(body)
        extractedMemory?.let {
            store.rememberIfCurrent(character.id, userMessage.id, it)
        }
        store.recordConversationRelationship(
            character.id,
            userMessage.id,
            extractedMemory != null,
            messageBody = body,
        )
        captureLongTermMemory(context, store, character, userMessage, onChanged)
        onChanged()
        requestReply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (characters.size > 1) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    characters.forEach { resident ->
                        FilterChip(
                            selected = resident.id == character.id,
                            onClick = { onCharacterSelected(resident.id) },
                            label = { Text(resident.name) },
                        )
                    }
                }
            }
            }
            item {
                ScreenBackButton(onBack)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            character.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                        )
                        Text(
                            "${relationship.label} · ${stringResource(R.string.private_conversation)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showContext = true }) {
                        Text(stringResource(R.string.conversation_context))
                    }
                }
            }
            if (messages.isEmpty()) {
                item {
                    StatusCard(stringResource(R.string.start_conversation))
                }
            }
            items(messages, key = ChatMessage::id) { message ->
                val versions = remember(revision, message.id) {
                    if (message.sender == "assistant") store.messageVersions(message.id)
                    else emptyList()
                }
                val visibleBody = when {
                    message.id == streamingMessageId && streamingText.isNotEmpty() -> streamingText
                    message.status != "complete" && message.body.isEmpty() -> message.draftBody
                    else -> message.body
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.sender == "user") {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    },
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.86f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.sender == "user") {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                visibleBody.ifBlank {
                                    stringResource(R.string.character_thinking)
                                },
                            )
                            if (message.sender == "assistant") {
                            when (message.status) {
                                "streaming" -> Text(
                                    stringResource(R.string.streaming_reply),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                "failed" -> Text(
                                    stringResource(R.string.chat_failed, message.error),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                "interrupted" -> Text(
                                    stringResource(R.string.reply_interrupted),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (
                                message == messages.lastOrNull() &&
                                message.status in setOf("failed", "interrupted") &&
                                !sending
                            ) {
                                TextButton(onClick = { requestReply(message.id) }) {
                                    Text(stringResource(R.string.retry_reply))
                                }
                            }
                            if (
                                message == messages.lastOrNull() &&
                                message.status == "complete" &&
                                !sending
                            ) {
                                TextButton(onClick = { requestReply(message.id) }) {
                                    Text(stringResource(R.string.regenerate_reply))
                                }
                            }
                            if (versions.isNotEmpty() && message.status != "streaming") {
                                TextButton(
                                    onClick = {
                                        expandedVersionsId =
                                            message.id.takeUnless { expandedVersionsId == it }
                                    },
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.reply_versions,
                                            versions.size,
                                        ),
                                    )
                                }
                            }
                            if (expandedVersionsId == message.id) {
                                versions.forEach { version ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor =
                                                MaterialTheme.colorScheme.surface,
                                        ),
                                    ) {
                                        Column(
                                            Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(version.body, maxLines = 5)
                                            Text(
                                                stringResource(
                                                    R.string.generation_provenance,
                                                    version.providerName.ifBlank { "—" },
                                                    version.modelName.ifBlank { "—" },
                                                    formatter.format(Date(version.createdAt)),
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color =
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (
                                                version.body != message.body ||
                                                version.providerName != message.providerName ||
                                                version.modelName != message.modelName
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        store.restoreMessageVersion(
                                                            message.id,
                                                            version.id,
                                                        )
                                                        expandedVersionsId = null
                                                        onChanged()
                                                    },
                                                ) {
                                                    Text(
                                                        stringResource(
                                                            R.string.restore_version,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            } else if (!sending) {
                                TextButton(
                                    onClick = {
                                        rewritingMessageId = message.id
                                        rewriteText = message.body
                                    },
                                ) {
                                    Text(stringResource(R.string.rewrite_from_here))
                                }
                            }
                        }
                    }
                }
            }
            error?.let { message ->
                item { StatusCard(stringResource(R.string.chat_failed, message)) }
            }
            if (messages.lastOrNull()?.sender == "user" && !sending) {
                item {
                    StatusCard(stringResource(R.string.reply_waiting_for_network))
                    TextButton(onClick = { requestReply() }) {
                        Text(stringResource(R.string.continue_pending_reply))
                    }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.message_character, character.name)) },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = ::sendMessage,
                    enabled = input.isNotBlank() && !sending,
                    modifier = Modifier.size(52.dp),
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationContextScreen(
    contentPadding: PaddingValues,
    character: ResidentCharacter,
    messages: List<ChatMessage>,
    retiredMessages: List<ChatMessage>,
    recap: ConversationRecap?,
    memories: List<LongTermMemory>,
    relationship: RelationshipState,
    relationshipEvents: List<RelationshipEvent>,
    onBack: () -> Unit,
    onSaveRecap: (String, Long) -> Boolean,
    onUpdateRecap: (String) -> Unit,
    onPinRecap: (Boolean) -> Unit,
    onUpdate: (Long, String) -> Unit,
    onPin: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onCorrectRelationship: (String, String, Boolean) -> Unit,
    onPinRelationship: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val provider = remember { ProviderStore(context) }
    val recapRequiresMessages = stringResource(R.string.recap_requires_messages)
    val recapFailedPrefix = stringResource(R.string.recap_failed, "")
    val recapOutdated = stringResource(R.string.recap_outdated)
    var generating by remember { mutableStateOf(false) }
    var recapError by remember { mutableStateOf<String?>(null) }
    var editingRecap by rememberSaveable { mutableStateOf(false) }
    var recapText by rememberSaveable(recap?.createdAt) { mutableStateOf(recap?.body.orEmpty()) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editingRelationship by rememberSaveable { mutableStateOf(false) }
    var relationshipLabel by rememberSaveable(relationship.createdAt) { mutableStateOf(relationship.label) }
    var relationshipSummary by rememberSaveable(relationship.createdAt) {
        mutableStateOf(relationship.summary)
    }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenBackButton(onBack)
            Text(
                stringResource(R.string.conversation_context),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.conversation_recap_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.relationship_state),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (editingRelationship) {
                        OutlinedTextField(
                            value = relationshipLabel,
                            onValueChange = { relationshipLabel = it },
                            label = { Text(stringResource(R.string.relationship_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = relationshipSummary,
                            onValueChange = { relationshipSummary = it },
                            label = { Text(stringResource(R.string.relationship_summary)) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = {
                                    if (
                                        relationshipLabel.isNotBlank() &&
                                        relationshipSummary.isNotBlank()
                                    ) {
                                        onCorrectRelationship(
                                            relationshipLabel,
                                            relationshipSummary,
                                            relationship.pinned,
                                        )
                                        editingRelationship = false
                                    }
                                },
                                enabled = relationshipLabel.isNotBlank() &&
                                    relationshipSummary.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.save))
                            }
                            TextButton(onClick = { editingRelationship = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    } else {
                        Text(relationship.label, style = MaterialTheme.typography.titleMedium)
                        Text(relationship.summary)
                    }
                    relationship.sourceMessageId?.let { sourceMessageId ->
                        Text(
                            stringResource(
                                R.string.relationship_source_message,
                                sourceMessageId,
                                formatter.format(Date(relationship.createdAt)),
                                character.name,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } ?: Text(
                        stringResource(
                            if (relationship.source == "user_correction") {
                                R.string.relationship_source_user
                            } else {
                                R.string.relationship_source_automatic
                            },
                            formatter.format(Date(relationship.createdAt)),
                            character.name,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onPinRelationship(!relationship.pinned) }) {
                            Text(stringResource(if (relationship.pinned) R.string.unpin else R.string.pin))
                        }
                        TextButton(
                            onClick = {
                                relationshipLabel = relationship.label
                                relationshipSummary = relationship.summary
                                editingRelationship = true
                            },
                        ) {
                            Text(stringResource(R.string.edit))
                        }
                    }
                    Text(
                        stringResource(R.string.relationship_state_summary),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (relationshipEvents.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.relationship_history),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.relationship_history_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(relationshipEvents, key = { "relationship-${it.id}" }) { event ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (event.active) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (event.active) {
                            Text(
                                stringResource(R.string.relationship_current_event),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(event.label, fontWeight = FontWeight.Bold)
                        Text(event.summary)
                        val source = if (event.source == "user_correction") {
                            stringResource(R.string.relationship_user_source)
                        } else {
                            stringResource(R.string.relationship_automatic_source)
                        }
                        Text(
                            stringResource(
                                R.string.relationship_event_source,
                                source,
                                formatter.format(Date(event.createdAt)),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.conversation_recap),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (editingRecap) {
                        OutlinedTextField(
                            value = recapText,
                            onValueChange = { recapText = it },
                            minLines = 5,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            recap?.body ?: stringResource(R.string.no_conversation_recap),
                            color = if (recap == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    recap?.let {
                        Text(
                            stringResource(
                                R.string.recap_source,
                                it.throughMessageId,
                                formatter.format(Date(it.createdAt)),
                                character.name,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    recapError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = {
                                if (messages.size < 2) {
                                    recapError = recapRequiresMessages
                                    return@Button
                                }
                                generating = true
                                recapError = null
                                ProviderTextClient.complete(
                                    provider.loadFor(ProviderTask.Memory),
                                    "Summarize a private conversation for future context. " +
                                        "Preserve concrete events, promises, feelings and unresolved topics. " +
                                        "Do not invent facts. Write concise natural-language Chinese.",
                                    messages.takeLast(100).joinToString("\n") {
                                        "${if (it.sender == "user") "User" else character.name}: ${it.body}"
                                    },
                                ) { result ->
                                    generating = false
                                    result.onSuccess {
                                        if (!onSaveRecap(it.text, messages.last().id)) {
                                            recapError = recapOutdated
                                        }
                                    }.onFailure {
                                        recapError = "$recapFailedPrefix ${it.message.orEmpty()}"
                                    }
                                }
                            },
                            enabled = !generating,
                        ) {
                            Text(
                                stringResource(
                                    when {
                                        generating -> R.string.generating_recap
                                        recap == null -> R.string.generate_recap
                                        else -> R.string.regenerate_recap
                                    },
                                ),
                            )
                        }
                        recap?.let {
                            TextButton(onClick = { onPinRecap(!it.pinned) }) {
                                Text(stringResource(if (it.pinned) R.string.unpin else R.string.pin))
                            }
                            TextButton(
                                onClick = {
                                    if (editingRecap) {
                                        if (recapText.isNotBlank()) onUpdateRecap(recapText)
                                        editingRecap = false
                                    } else {
                                        recapText = it.body
                                        editingRecap = true
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (editingRecap) R.string.save else R.string.edit,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.long_term_memories),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.memory_transparency),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (memories.isEmpty()) {
            item { StatusCard(stringResource(R.string.no_memories)) }
        }
        items(memories, key = LongTermMemory::id) { memory ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (editingId == memory.id) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(memory.body, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        stringResource(
                            R.string.memory_source,
                            memory.sourceMessageId,
                            formatter.format(Date(memory.createdAt)),
                            memory.characterName,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { onPin(memory.id, !memory.pinned) },
                        ) {
                            Text(
                                stringResource(
                                    if (memory.pinned) R.string.unpin else R.string.pin,
                                ),
                            )
                        }
                        TextButton(
                            onClick = {
                                if (editingId == memory.id) {
                                    if (editText.isNotBlank()) onUpdate(memory.id, editText)
                                    editingId = null
                                } else {
                                    editingId = memory.id
                                    editText = memory.body
                                }
                            },
                        ) {
                            Text(
                                stringResource(
                                    if (editingId == memory.id) R.string.save else R.string.edit,
                                ),
                            )
                        }
                        TextButton(onClick = { onDelete(memory.id) }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
        if (retiredMessages.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.retired_timeline),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.retired_timeline_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(retiredMessages, key = { "retired-${it.id}" }) { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (message.sender == "user") {
                                stringResource(R.string.you)
                            } else {
                                character.name
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(message.body.ifBlank { message.draftBody })
                        Text(
                            formatter.format(Date(message.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
