package io.github.lzyuuu.ailivesaver

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private data class MessengerGroup(val id: Long, val memberIds: List<Long>, val prompt: String)

private fun groupFor(store: WorldStore, character: ResidentCharacter): MessengerGroup? {
    if (!character.cardJson.startsWith("group:")) return null
    val token = character.cardJson.removePrefix("group:")
    val persistedId = token.toLongOrNull()
    if (persistedId != null) {
        return store.loadPersistedMessengerGroup(persistedId)?.let {
            MessengerGroup(it.id, it.memberIds, it.prompt)
        }
    }
    // Compatibility with pre-contract group rows.
    val ids = token.split(',').mapNotNull(String::toLongOrNull)
    return ids.takeIf { it.size >= 2 }?.let { MessengerGroup(0L, it, "") }
}

internal object ActiveChatReplies {
    private val characterIds = mutableSetOf<Long>()
    private val streamHandles = mutableMapOf<Long, ProviderStreamHandle>()

    fun contains(characterId: Long) = characterId in characterIds
    fun add(characterId: Long) = characterIds.add(characterId)
    fun remove(characterId: Long) {
        characterIds.remove(characterId)
        streamHandles.remove(characterId)
    }

    fun attachStream(characterId: Long, handle: ProviderStreamHandle) {
        streamHandles[characterId] = handle
    }

    fun cancelStream(characterId: Long) {
        streamHandles[characterId]?.cancel()
    }
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
    onOpenBackup: () -> Unit = {},
    onBackToDesktop: (() -> Unit)? = null,
) {
    val characters = remember(revision) { store.characters(includeDeparted = false) }
    // 不用 rememberSaveable：从桌面 Dock 再进 Messenger 应回到列表，而不是恢复上次会话。
    var selectedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(initialCharacterId, characters) {
        val requestedId = initialCharacterId ?: return@LaunchedEffect
        if (characters.any { it.id == requestedId }) selectedId = requestedId
        onInitialCharacterConsumed()
    }
    val character = selectedId?.let { id -> characters.firstOrNull { it.id == id } }
    when {
        character != null -> {
        key(character.id) {
            val group = groupFor(store, character)
            if (group == null) {
                ConversationScreen(contentPadding, store, character, revision, onChanged, { selectedId = null }, onManageCharacters)
            } else {
                GroupConversationScreen(contentPadding, store, character, group, revision, onChanged, { selectedId = null })
            }
        }
        }
        else -> {
            // Fancy OS Messenger 列表即使尚无会话也保持参考 IA（Recent/New/Import/Browse），
            // 不再回落到旧五 Tab 的 FirstRelationship 建世页。
            ChatListScreen(
                contentPadding = contentPadding,
                store = store,
                characters = characters,
                revision = revision,
                onCharacterSelected = { selectedId = it },
                onManageCharacters = onManageCharacters,
                onOpenBackup = onOpenBackup,
                onBackToDesktop = onBackToDesktop,
                onChanged = onChanged,
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
    onOpenBackup: () -> Unit,
    onBackToDesktop: (() -> Unit)?,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var showNewSheet by rememberSaveable { mutableStateOf(false) }
    var showNewGroupSheet by rememberSaveable { mutableStateOf(false) }
    var listMenuExpanded by remember { mutableStateOf(false) }
    var showRootPrompt by rememberSaveable { mutableStateOf(false) }
    var rootPrompt by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newPersona by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("") }
    var groupPrompt by rememberSaveable { mutableStateOf("") }
    var selectedGroupMemberIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var createError by remember { mutableStateOf<String?>(null) }
    val groupNameFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showNewGroupSheet) {
        if (showNewGroupSheet) groupNameFocusRequester.requestFocus()
    }
    val latestMessages = remember(revision, characters) {
        characters.associate { it.id to store.messages(it.id).lastOrNull() }
    }
    val unreadCounts = remember(revision, characters) { store.unreadMessageCounts() }
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
    val importFailed = stringResource(R.string.character_card_import_failed)
    val newGroupNeedName = stringResource(R.string.messenger_new_group_need_name)
    val newGroupNeedMembers = stringResource(R.string.messenger_new_group_need_members)
    val completeCharacterFields = stringResource(R.string.messenger_complete_character_fields)
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use(CharacterCardV2::read)
                ?: error("无法读取文件")
            CharacterCardV2.parse(bytes)
        }.onSuccess { card ->
            val id = store.addCharacter(
                name = card.name,
                persona = card.persona.ifBlank { card.name },
                attentionTier = "resident",
                appearance = "",
                clothing = "",
                negativePrompt = "",
                cardJson = card.rawJson,
            )
            if (card.firstMessage.isNotBlank()) {
                store.addMessage(id, "assistant", card.firstMessage)
            }
            card.lore.forEach { lore -> store.addCharacterCognition(id, lore) }
            onChanged()
            onCharacterSelected(id)
        }.onFailure {
            createError = "$importFailed：${it.message.orEmpty()}"
        }
    }

    if (showRootPrompt) {
        AlertDialog(
            onDismissRequest = { showRootPrompt = false },
            title = { Text("Edit prompt", color = FancyCream) },
            text = { OutlinedTextField(rootPrompt, { rootPrompt = it }, label = { Text("Root/system prompt") }, minLines = 5, colors = messengerFieldColors()) },
            confirmButton = {
                TextButton(onClick = {
                    characters.firstOrNull { it.name.equals("Root", true) }?.let { root ->
                        store.updateCharacter(root.copy(persona = rootPrompt.trim().ifBlank { DesktopSeed.ROOT_PERSONA }))
                        onChanged()
                    }
                    showRootPrompt = false
                }, modifier = Modifier.testTag("messenger-edit-prompt-save")) { Text("Save", color = FancyGold) }
            },
            dismissButton = { TextButton(onClick = { showRootPrompt = false }) { Text("Cancel", color = FancyCream) } },
            containerColor = FancyNavyMid,
        )
    }

    if (showNewGroupSheet) {
        AlertDialog(
            onDismissRequest = {
                showNewGroupSheet = false
                groupName = ""
                selectedGroupMemberIds = emptySet()
                groupPrompt = ""
                createError = null
            },
            title = { Text(stringResource(R.string.messenger_new_group_title), color = FancyCream) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.messenger_new_group_summary),
                        color = FancyCream.copy(alpha = 0.75f),
                    )
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text(stringResource(R.string.messenger_new_group_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(groupNameFocusRequester)
                            .testTag("messenger-new-group-name"),

                        colors = messengerFieldColors(),
                    )
                    OutlinedTextField(
                        value = groupPrompt,
                        onValueChange = { groupPrompt = it },
                        label = { Text("Group prompt / scene") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("messenger-new-group-prompt"),
                        colors = messengerFieldColors(),
                    )
                    characters.forEach { resident ->
                        val selected = resident.id in selectedGroupMemberIds
                        TextButton(
                            onClick = {
                                selectedGroupMemberIds = if (selected) {
                                    selectedGroupMemberIds - resident.id
                                } else {
                                    selectedGroupMemberIds + resident.id
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${if (selected) "✓ " else ""}${resident.name}",
                                color = if (selected) FancyGold else FancyCream,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    createError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when {
                            groupName.isBlank() -> createError = newGroupNeedName
                            selectedGroupMemberIds.size < 2 -> createError = newGroupNeedMembers
                            else -> {
                                val memberNames = characters
                                    .filter { it.id in selectedGroupMemberIds }
                                    .joinToString(", ") { it.name }
                                val persistedId = store.savePersistedMessengerGroup(
                                    PersistedMessengerGroup(0L, groupName.trim(), groupPrompt.trim(), selectedGroupMemberIds.toList()),
                                )
                                val id = store.addCharacter(
                                    name = groupName.trim(),
                                    persona = "Group chat with $memberNames.",
                                    attentionTier = "resident",
                                    appearance = "",
                                    clothing = "",
                                    negativePrompt = "",
                                    cardJson = "group:$persistedId",
                                )
                                selectedGroupMemberIds.forEach { memberId ->
                                    store.addMessage(id, "character:$memberId", "我加入了这个群组。")
                                }
                                showNewGroupSheet = false
                                groupName = ""
                                selectedGroupMemberIds = emptySet()
                                createError = null
                                onChanged()
                                onCharacterSelected(id)
                            }
                        }
                    },
                    modifier = Modifier.testTag("messenger-new-group-confirm"),
                ) {
                    Text(stringResource(R.string.messenger_new_group_create), color = FancyGold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewGroupSheet = false
                        groupName = ""
                        selectedGroupMemberIds = emptySet()
                        createError = null
                    },
                ) {
                    Text(stringResource(R.string.cancel), color = FancyCream.copy(alpha = 0.7f))
                }
            },
            containerColor = FancyNavyMid,
        )
    }

    if (showNewSheet) {
        AlertDialog(
            onDismissRequest = {
                showNewSheet = false
                creating = false
                createError = null
            },
            title = {
                Text(
                    if (creating) {
                        stringResource(R.string.messenger_new_character)
                    } else {
                        stringResource(R.string.messenger_new_conversation)
                    },
                    color = FancyCream,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!creating) {
                        Text(
                            stringResource(R.string.messenger_new_conversation_summary),
                            color = FancyCream.copy(alpha = 0.75f),
                        )
                        characters.take(8).forEach { resident ->
                            TextButton(
                                onClick = {
                                    showNewSheet = false
                                    onCharacterSelected(resident.id)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(resident.name, color = FancyGold, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        TextButton(
                            onClick = { creating = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("+ ${stringResource(R.string.messenger_new_character)}", color = FancyGold)
                        }
                    } else {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(stringResource(R.string.character_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = messengerFieldColors(),
                        )
                        OutlinedTextField(
                            value = newPersona,
                            onValueChange = { newPersona = it },
                            label = { Text(stringResource(R.string.character_persona)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            colors = messengerFieldColors(),
                        )
                        createError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                if (creating) {
                    TextButton(
                        onClick = {
                            if (newName.isBlank() || newPersona.isBlank()) {
                                createError = completeCharacterFields
                                return@TextButton
                            }
                            val id = store.addCharacter(
                                name = newName.trim(),
                                persona = newPersona.trim(),
                                attentionTier = "resident",
                                appearance = "",
                                clothing = "",
                                negativePrompt = "",
                            )
                            showNewSheet = false
                            creating = false
                            newName = ""
                            newPersona = ""
                            onChanged()
                            onCharacterSelected(id)
                        },
                    ) {
                        Text(stringResource(R.string.messenger_create_and_open), color = FancyGold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (creating) {
                            creating = false
                            createError = null
                        } else {
                            showNewSheet = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.cancel), color = FancyCream.copy(alpha = 0.7f))
                }
            },
            containerColor = FancyNavyMid,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FancyInk)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("messenger-list"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBackToDesktop != null) {
                IconButton(
                    onClick = onBackToDesktop,
                    modifier = Modifier.testTag("messenger-back-desktop"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.messenger_back_to_desktop),
                        tint = FancyCream,
                    )
                }
            }
            Text(
                stringResource(R.string.messenger_title),
                color = FancyCream,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(
                    onClick = { listMenuExpanded = true },
                    modifier = Modifier.testTag("messenger-list-settings"),
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = stringResource(R.string.messenger_list_settings),
                        tint = FancyCream.copy(alpha = 0.55f),
                    )
                }
                DropdownMenu(
                    expanded = listMenuExpanded,
                    onDismissRequest = { listMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit prompt") },
                        onClick = {
                            listMenuExpanded = false
                            rootPrompt = characters.firstOrNull { it.name.equals("Root", true) }?.persona.orEmpty()
                            showRootPrompt = true
                        },
                        modifier = Modifier.testTag("messenger-edit-prompt"),
                    )
                    DropdownMenuItem(
                        text = { Text("Back up") },
                        onClick = { listMenuExpanded = false; onOpenBackup() },
                        modifier = Modifier.testTag("messenger-back-up"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.messenger_import)) },
                        onClick = {
                            listMenuExpanded = false
                            importLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "image/png",
                                    "application/xml",
                                    "text/xml",
                                    "*/*",
                                ),
                            )
                        },
                        modifier = Modifier.testTag("messenger-import"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.messenger_browse)) },
                        onClick = {
                            listMenuExpanded = false
                            onManageCharacters()
                        },
                        modifier = Modifier.testTag("messenger-browse"),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.messenger_recent_chats),
                color = FancyCream.copy(alpha = 0.62f),
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                onClick = { showNewSheet = true },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.testTag("messenger-new"),
            ) {
                Text(
                    stringResource(R.string.messenger_new_character_action),
                    color = FancyGold,
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text(
                    stringResource(R.string.messenger_search_placeholder),
                    color = FancyCream.copy(alpha = 0.42f),
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = FancyGold.copy(alpha = 0.85f))
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("messenger-search"),
            colors = messengerSearchFieldColors(),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.messenger_groups), color = FancyCream, fontWeight = FontWeight.SemiBold)
                    TextButton(
                        onClick = {
                            showNewGroupSheet = true
                            createError = null
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.testTag("messenger-new-group"),
                    ) {
                        Text(stringResource(R.string.messenger_new_group), color = FancyGold)
                    }
                }
            }
            if (visibleCharacters.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_matching_conversations),
                        color = FancyCream.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            } else {
                itemsIndexed(visibleCharacters, key = { _, it -> it.id }) { index, resident ->
                    val latestMessage = latestMessages[resident.id]
                    val unread = unreadCounts[resident.id] ?: 0
                    if (index > 0) {
                        HorizontalDivider(
                            color = FancyCream.copy(alpha = 0.12f),
                            thickness = 1.dp,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat-character-${resident.id}")
                            .clickable { onCharacterSelected(resident.id) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MessengerCharacterAvatar(resident)
                        Column(Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    resident.name,
                                    color = FancyCream,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.weight(1f))
                                latestMessage?.let {
                                    Text(
                                        chatListTimeLabel(it.createdAt),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (unread > 0) FancyGold else FancyCream.copy(alpha = 0.45f),
                                    )
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val streaming = latestMessage?.status == "streaming"
                                Text(
                                    when {
                                        streaming -> stringResource(R.string.streaming_reply)
                                        else -> latestMessage?.body
                                            ?.ifBlank { latestMessage.draftBody }
                                            ?: stringResource(R.string.messenger_no_messages_yet)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (streaming) {
                                        FancyGold
                                    } else {
                                        FancyCream.copy(alpha = 0.55f)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (unread > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = FancyGold,
                                    ) {
                                        Text(
                                            if (unread > 99) "99+" else "$unread",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = FancyInk,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessengerCharacterAvatar(
    character: ResidentCharacter,
    size: androidx.compose.ui.unit.Dp = 52.dp,
) {
    val avatarPath = remember(character.id, character.cardJson) {
        CharacterCardV2.profileFields(character).avatarPath
    }
    Avatar(character.name.take(1).uppercase(), size, avatarPath)
}

@Composable
private fun messengerSearchFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = FancyCream,
    unfocusedTextColor = FancyCream,
    focusedBorderColor = FancyCream.copy(alpha = 0.35f),
    unfocusedBorderColor = FancyCream.copy(alpha = 0.28f),
    cursorColor = FancyGold,
    focusedContainerColor = FancyInk,
    unfocusedContainerColor = FancyInk,
    focusedLeadingIconColor = FancyGold,
    unfocusedLeadingIconColor = FancyGold.copy(alpha = 0.85f),
)

@Composable
private fun messengerFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = FancyCream,
    unfocusedTextColor = FancyCream,
    focusedBorderColor = FancyGold,
    unfocusedBorderColor = FancyGoldDim,
    cursorColor = FancyGold,
    focusedContainerColor = FancyNavyMid,
    unfocusedContainerColor = FancyNavyMid,
    focusedLabelColor = FancyGold,
    unfocusedLabelColor = FancyGoldDim,
)

@Composable
private fun chatListTimeLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return if (isSameChatDay(timestamp, now)) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
    } else {
        chatDayLabel(timestamp, now)
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
private fun GroupConversationScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    groupCharacter: ResidentCharacter,
    group: MessengerGroup,
    revision: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val provider = remember { ProviderStore(context) }
    val allCharacters = remember(revision) { store.characters() }
    var memberIds by remember(group.id, revision) { mutableStateOf(group.memberIds.toSet()) }
    var prompt by remember(group.id, revision) { mutableStateOf(group.prompt) }
    val members = allCharacters.filter { it.id in memberIds }
    val messages = remember(revision) { store.messages(groupCharacter.id) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var groupHandles by remember { mutableStateOf<Map<Long, ProviderStreamHandle>>(emptyMap()) }
    BackHandler { onBack() }
    fun persistDetails() {
        if (memberIds.size < 2) { error = "Select at least two members"; return }
        store.savePersistedMessengerGroup(PersistedMessengerGroup(group.id, groupCharacter.name, prompt.trim(), memberIds.toList()))
        showDetails = false
        onChanged()
    }
    fun stopAll() { groupHandles.values.forEach { it.cancel() }; groupHandles = emptyMap() }
    fun send() {
        if (groupHandles.isNotEmpty()) return
        error = null
        val body = input.trim()
        if (body.isEmpty()) return
        input = ""
        store.addMessage(groupCharacter.id, "user", body)
        val config = provider.loadFor(ProviderTask.Chat)
        members.forEach { member ->
            val pendingReply = runCatching {
                store.beginAssistantReply(
                    groupCharacter.id,
                    config.preset.displayName,
                    config.model,
                )
            }.getOrElse { failure ->
                error = failure.message.orEmpty()
                return@forEach
            }
            val handle = ProviderStreamHandle()
            groupHandles = groupHandles + (member.id to handle)
            ProviderChatClient.stream(
                config = config,
                character = member,
                messages = store.messages(groupCharacter.id),
                memories = store.memories(member.id),
                recap = store.conversationRecap(member.id),
                worldFacts = store.worldFacts(),
                cognition = store.characterCognition(member.id),
                userContext = store.memberWorldContext("user"),
                characterContext = store.memberWorldContext("character:${member.id}"),
                relationship = store.relationship(member.id),
                systemPromptAppendix = group.prompt,
                onDelta = {},
                handle = handle,
                callback = { result ->
                    groupHandles = groupHandles - member.id
                    runCatching {
                        WorldStore(context.applicationContext).use { callbackStore ->
                            result.fold(
                                onSuccess = { response ->
                                    val reply = response.text.trim()
                                    if (reply.isEmpty()) {
                                        callbackStore.failAssistantReply(pendingReply.id, "Provider returned an empty reply")
                                    } else {
                                        callbackStore.completeAssistantReply(
                                            pendingReply.id,
                                            reply,
                                            response.config.preset.displayName,
                                            response.config.model,
                                            finalSender = "character:${member.id}",
                                        )
                                    }
                                },
                                onFailure = { failure ->
                                    callbackStore.failAssistantReply(pendingReply.id, failure.message.orEmpty())
                                },
                            )
                        }
                    }
                    onChanged()
                },
            )
        }
        onChanged()
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Group details", color = FancyCream) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(prompt, { prompt = it }, label = { Text("Group prompt / scene") }, minLines = 3, colors = messengerFieldColors(), modifier = Modifier.testTag("messenger-group-prompt"))
                    allCharacters.forEach { member ->
                        TextButton(onClick = { memberIds = if (member.id in memberIds) memberIds - member.id else memberIds + member.id }, modifier = Modifier.fillMaxWidth()) {
                            Text("${if (member.id in memberIds) "✓ " else ""}${member.name}", color = if (member.id in memberIds) FancyGold else FancyCream)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = ::persistDetails, modifier = Modifier.testTag("messenger-group-details-save")) { Text("Save", color = FancyGold) } },
            dismissButton = { TextButton(onClick = { showDetails = false }) { Text("Cancel", color = FancyCream) } },
            containerColor = FancyNavyMid,
        )
    }
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear group") }, text = { Text("Clear this group conversation?") }, confirmButton = { TextButton(onClick = { stopAll(); store.clearConversation(groupCharacter.id); confirmClear = false; onChanged() }, modifier = Modifier.testTag("messenger-group-clear-confirm")) { Text("Clear") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
    }
    Column(
        modifier = Modifier.fillMaxSize().background(FancyInk).padding(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ).testTag("messenger-group-conversation"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = FancyCream)
            }
            IconButton(onClick = { showDetails = true }, modifier = Modifier.testTag("messenger-group-details")) {
                Icon(Icons.Default.Tune, contentDescription = "Group details", tint = FancyGold)
            }
            IconButton(onClick = ::stopAll, enabled = groupHandles.isNotEmpty(), modifier = Modifier.testTag("messenger-group-stop")) {
                Icon(Icons.Default.Close, contentDescription = "Stop", tint = FancyGold)
            }
            IconButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("messenger-group-clear")) {
                Icon(Icons.Default.Refresh, contentDescription = "Clear", tint = FancyGold)
            }
            Column(Modifier.weight(1f)) {
                Text(groupCharacter.name, color = FancyCream, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Text(members.joinToString(" · ") { it.name }, color = FancyCream.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message ->
                val sender = when {
                    message.sender == "user" -> "我"
                    message.sender.startsWith("character:") -> members.firstOrNull { it.id == message.sender.removePrefix("character:").toLongOrNull() }?.name ?: "成员"
                    else -> message.sender
                }
                Column(Modifier.fillMaxWidth().testTag("messenger-group-message-${message.id}")) {
                    Text(sender, color = if (message.sender == "user") FancyGold else FancyCream.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    if (message.status == "failed") {
                        Text(
                            stringResource(R.string.messenger_reply_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (message.error.isNotBlank()) {
                            Text(message.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(message.body, color = FancyCream, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.messenger_message_placeholder)) }, singleLine = true, colors = messengerSearchFieldColors())
            IconButton(onClick = ::send, enabled = input.isNotBlank(), modifier = Modifier.testTag("messenger-group-send")) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send), tint = FancyGold)
            }
        }
    }
}

@Composable
private fun ConversationScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    character: ResidentCharacter,
    revision: Int,
    onChanged: () -> Unit,
    onBack: () -> Unit,
    onManageCharacters: () -> Unit,
) {
    val context = LocalContext.current
    val animationsEnabled = remember { systemAnimationsEnabled(context) }
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
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var selectMessages by rememberSaveable { mutableStateOf(false) }
    var rewritingMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var rewriteText by rememberSaveable { mutableStateOf("") }
    var expandedVersionsId by rememberSaveable { mutableStateOf<Long?>(null) }
    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        store.addMessage(character.id, "user", "[image]")
        onChanged()
    }
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
        val handle = ProviderStreamHandle()
        ActiveChatReplies.attachStream(character.id, handle)
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
            callback = { result ->
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
                        onFailure = { failure ->
                            runCatching {
                                WorldStore(context.applicationContext).use { callbackStore ->
                                    if (failure is ProviderStreamCancelledException || handle.isCancelled()) {
                                        callbackStore.interruptAssistantReply(reply.id)
                                    } else {
                                        callbackStore.failAssistantReply(reply.id, failure.message.orEmpty())
                                        error = failure.message.orEmpty()
                                    }
                                }
                            }
                        },
                    )
                } finally {
                    ActiveChatReplies.remove(character.id)
                    streamingMessageId = null
                    streamingText = ""
                    onChanged()
                }
            },
            handle = handle,
        )
    }

    fun stopGeneration() {
        ActiveChatReplies.cancelStream(character.id)
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
    // 回复完成（streaming→complete）也触发已读标记，避免会话内读完最新回复后
    // 返回列表时未读角标残留。
    LaunchedEffect(messages.size, messages.lastOrNull()?.status) {
        if (messages.isNotEmpty()) {
            val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            if (animationsEnabled) {
                listState.animateScrollToItem(target)
            } else {
                listState.scrollToItem(target)
            }
        }
        store.markChatRead(character.id)
        onChanged()
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
            .background(FancyInk)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("messenger-conversation"),
    ) {
        Surface(color = FancyInk) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = FancyCream,
                    )
                }
                MessengerCharacterAvatar(character, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = FancyCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("messenger-open-chats"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = stringResource(R.string.messenger_chats),
                        tint = FancyCream.copy(alpha = 0.55f),
                    )
                }
                IconButton(
                    onClick = { showContext = true },
                    modifier = Modifier.testTag("messenger-open-context"),
                ) {
                    Icon(
                        Icons.Default.Dashboard,
                        contentDescription = stringResource(R.string.messenger_open_console),
                        tint = FancyCream.copy(alpha = 0.55f),
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("messenger-chat-options"),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.messenger_chat_options),
                            tint = FancyCream.copy(alpha = 0.55f),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.messenger_view_profile)) },
                            onClick = {
                                menuExpanded = false
                                onManageCharacters()
                            },
                            modifier = Modifier.testTag("messenger-view-profile"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.messenger_select_messages)) },
                            onClick = {
                                menuExpanded = false
                                selectMessages = !selectMessages
                            },
                            modifier = Modifier.testTag("messenger-select-messages"),
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.messenger_clear_chat)) },
                            onClick = {
                                menuExpanded = false
                                confirmClear = true
                            },
                            modifier = Modifier.testTag("messenger-clear-conversation"),
                        )
                    }
                }
            }
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.messenger_clear_conversation)) },
                text = { Text(stringResource(R.string.messenger_clear_conversation_summary)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            stopGeneration()
                            store.clearConversation(character.id)
                            confirmClear = false
                            onChanged()
                        },
                        modifier = Modifier.testTag("messenger-clear-confirm"),
                    ) {
                        Text(stringResource(R.string.messenger_clear))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(messages, key = { _, it -> it.id }) { index, message ->
                val previous = messages.getOrNull(index - 1)
                val next = messages.getOrNull(index + 1)
                val versions = remember(revision, message.id) {
                    if (message.sender == "assistant") store.messageVersions(message.id)
                    else emptyList()
                }
                MessageBubble(
                    message = message,
                    characterName = character.name,
                    modifier = if (animationsEnabled) Modifier.animateItem() else Modifier,
                    newDay = previous == null ||
                        !isSameChatDay(previous.createdAt, message.createdAt),
                    lastOfGroup = next == null ||
                        next.sender != message.sender ||
                        !isSameChatDay(message.createdAt, next.createdAt),
                    isLastMessage = index == messages.lastIndex,
                    streamingText = if (message.id == streamingMessageId) streamingText else "",
                    sending = sending,
                    versions = versions,
                    versionsExpanded = expandedVersionsId == message.id,
                    formatter = formatter,
                    onRetry = { requestReply(message.id) },
                    onToggleVersions = {
                        expandedVersionsId = message.id.takeUnless { expandedVersionsId == it }
                    },
                    onRestoreVersion = { versionId ->
                        store.restoreMessageVersion(message.id, versionId)
                        expandedVersionsId = null
                        onChanged()
                    },
                    onRewrite = {
                        rewritingMessageId = message.id
                        rewriteText = message.body
                    },
                )
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
        Surface(color = FancyInk) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        attachLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("messenger-attach-image"),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.messenger_attach_image),
                        tint = FancyGold,
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.messenger_message_placeholder),
                            color = FancyCream.copy(alpha = 0.45f),
                        )
                    },
                    minLines = 1,
                    maxLines = 4,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.weight(1f),
                    colors = messengerSearchFieldColors(),
                )
                if (sending) {
                    IconButton(
                        onClick = ::stopGeneration,
                        modifier = Modifier.testTag("messenger-stop-generation"),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.messenger_stop_generation),
                            tint = FancyGold,
                        )
                    }
                } else {
                    IconButton(
                        onClick = ::sendMessage,
                        enabled = input.isNotBlank(),
                        modifier = Modifier.testTag("messenger-send"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = FancyGold,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    characterName: String,
    modifier: Modifier = Modifier,
    newDay: Boolean,
    lastOfGroup: Boolean,
    isLastMessage: Boolean,
    streamingText: String,
    sending: Boolean,
    versions: List<MessageVersion>,
    versionsExpanded: Boolean,
    formatter: DateFormat,
    onRetry: () -> Unit,
    onToggleVersions: () -> Unit,
    onRestoreVersion: (Long) -> Unit,
    onRewrite: () -> Unit,
) {
    val isUser = message.sender == "user"
    val clipboardManager = LocalClipboardManager.current
    var menuOpen by remember(message.id) { mutableStateOf(false) }
    val visibleBody = when {
        streamingText.isNotEmpty() -> streamingText
        message.status != "complete" && message.body.isEmpty() -> message.draftBody
        else -> message.body
    }
    val streaming = message.status == "streaming"
    val context = LocalContext.current
    val animationsEnabled = remember { systemAnimationsEnabled(context) }
    val cursorAlpha = if (streaming && animationsEnabled) {
        val cursorTransition = rememberInfiniteTransition(label = "streamCursor")
        cursorTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "streamCursorAlpha",
        ).value
    } else {
        1f
    }
    val timeText = remember(message.createdAt) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.createdAt))
    }
    Column(modifier.fillMaxWidth()) {
        if (newDay) {
            DayDividerLabel(
                chatDayLabel(message.createdAt),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!isUser) {
                if (lastOfGroup) {
                    Avatar(characterName.take(1).uppercase(), 28.dp)
                } else {
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            Box {
                Surface(
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomEnd = if (isUser && lastOfGroup) 5.dp else 18.dp,
                        bottomStart = if (!isUser && lastOfGroup) 5.dp else 18.dp,
                    ),
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menuOpen = true },
                        ),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (visibleBody.isBlank() && message.status == "streaming") {
                            TypingIndicator(
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        } else {
                            val textColor = if (isUser) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                text = buildAnnotatedString {
                                    append(visibleBody)
                                    if (streaming) {
                                        withStyle(
                                            SpanStyle(
                                                color = textColor.copy(alpha = cursorAlpha),
                                            ),
                                        ) {
                                            append("▍")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                            )
                        }
                        if (!isUser) {
                            when (message.status) {
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
                        }
                        if (lastOfGroup) {
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    timeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.62f)
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                )
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    if (visibleBody.isNotBlank() && message.status != "streaming") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_message)) },
                            onClick = {
                                menuOpen = false
                                clipboardManager.setText(AnnotatedString(visibleBody))
                            },
                        )
                    }
                    if (
                        !isUser && isLastMessage &&
                        message.status in setOf("failed", "interrupted") && !sending
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.retry_reply)) },
                            onClick = {
                                menuOpen = false
                                onRetry()
                            },
                        )
                    }
                    if (!isUser && isLastMessage && message.status == "complete" && !sending) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.regenerate_reply)) },
                            onClick = {
                                menuOpen = false
                                onRetry()
                            },
                        )
                    }
                    if (!isUser && versions.isNotEmpty() && message.status != "streaming") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reply_versions, versions.size)) },
                            onClick = {
                                menuOpen = false
                                onToggleVersions()
                            },
                        )
                    }
                    if (isUser && !sending) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rewrite_from_here)) },
                            onClick = {
                                menuOpen = false
                                onRewrite()
                            },
                        )
                    }
                }
            }
        }
        if (!isUser && isLastMessage && !sending && message.status == "complete") {
            Row(
                modifier = Modifier.padding(start = 34.dp, top = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.regenerate_reply),
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onRetry)
                        .padding(4.dp),
                )
            }
        }
        if (
            !isUser && isLastMessage && !sending &&
            message.status in setOf("failed", "interrupted")
        ) {
            Row(Modifier.padding(start = 34.dp, top = 1.dp)) {
                Text(
                    stringResource(R.string.retry_reply),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        if (versionsExpanded) {
            Column(
                modifier = Modifier
                    .padding(start = 34.dp, top = 6.dp)
                    .widthIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                versions.forEach { version ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (
                                version.body != message.body ||
                                version.providerName != message.providerName ||
                                version.modelName != message.modelName
                            ) {
                                TextButton(
                                    onClick = { onRestoreVersion(version.id) },
                                ) {
                                    Text(stringResource(R.string.restore_version))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (lastOfGroup) {
            Spacer(Modifier.height(8.dp))
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
