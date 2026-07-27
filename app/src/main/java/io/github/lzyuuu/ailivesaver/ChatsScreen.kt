package io.github.lzyuuu.ailivesaver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ChatsScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onChanged: () -> Unit,
    onConfigureProvider: () -> Unit,
) {
    val character = remember(revision) { store.primaryCharacter() }
    if (character == null) {
        FirstRelationshipScreen(contentPadding, store, onChanged, onConfigureProvider)
    } else {
        ConversationScreen(contentPadding, store, character, revision, onChanged)
    }
}

@Composable
private fun FirstRelationshipScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onChanged: () -> Unit,
    onConfigureProvider: () -> Unit,
) {
    val context = LocalContext.current
    val providerReady = remember { ProviderStore(context).load().isValid() }
    var userName by rememberSaveable { mutableStateOf("") }
    var characterName by rememberSaveable { mutableStateOf("") }
    var persona by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val completeFields = stringResource(R.string.complete_world_fields)

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
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            stringResource(R.string.provider_required_for_world),
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
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
                Button(
                    onClick = {
                        if (userName.isBlank() || characterName.isBlank() || persona.isBlank()) {
                            error = completeFields
                        } else {
                            store.createWorld(userName, characterName, persona)
                            onChanged()
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
private fun ConversationScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    character: ResidentCharacter,
    revision: Int,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val provider = remember { ProviderStore(context) }
    val messages = remember(revision) { store.messages(character.id) }
    val memories = remember(revision) { store.memories(character.id) }
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMemories by rememberSaveable { mutableStateOf(false) }

    BackHandler(showMemories) { showMemories = false }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + 1)
    }

    if (showMemories) {
        MemoriesScreen(
            contentPadding = contentPadding,
            memories = memories,
            onBack = { showMemories = false },
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
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
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
                    )
                    Text(
                        stringResource(R.string.private_conversation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showMemories = true }) {
                    Text(stringResource(R.string.memories_count, memories.size))
                }
            }
        }
        if (messages.isEmpty()) {
            item {
                StatusCard(stringResource(R.string.start_conversation))
            }
        }
        items(messages, key = ChatMessage::id) { message ->
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
                    Text(message.body, modifier = Modifier.padding(14.dp))
                }
            }
        }
        error?.let { message ->
            item { StatusCard(stringResource(R.string.chat_failed, message)) }
        }
        item {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.message_character, character.name)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val body = input.trim()
                    if (body.isEmpty()) return@Button
                    input = ""
                    error = null
                    val userMessage = store.addMessage(character.id, "user", body)
                    MemoryExtractor.fromUserMessage(body)?.let {
                        store.remember(character.id, userMessage.id, it)
                    }
                    sending = true
                    onChanged()
                    ProviderChatClient.complete(
                        config = provider.load(),
                        character = character,
                        messages = store.messages(character.id),
                        memories = store.memories(character.id),
                    ) { result ->
                        sending = false
                        result.onSuccess {
                            store.addMessage(character.id, "assistant", it)
                            onChanged()
                        }.onFailure {
                            error = it.message.orEmpty()
                        }
                    }
                },
                enabled = input.isNotBlank() && !sending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (sending) {
                        stringResource(R.string.character_thinking)
                    } else {
                        stringResource(R.string.send)
                    },
                )
            }
        }
    }
}

@Composable
private fun MemoriesScreen(
    contentPadding: PaddingValues,
    memories: List<LongTermMemory>,
    onBack: () -> Unit,
    onUpdate: (Long, String) -> Unit,
    onPin: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
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
            TextButton(onClick = onBack) { Text("‹  ${stringResource(R.string.back)}") }
            Text(
                stringResource(R.string.long_term_memories),
                style = MaterialTheme.typography.headlineMedium,
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
    }
}
