package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.DateFormat
import java.util.Date
import java.io.File

@Composable
internal fun IdentityScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val identity = remember { store.identity() }
    var name by rememberSaveable { mutableStateOf(identity.name.takeUnless { it == "你" }.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(identity.addressPreference) }
    var bio by rememberSaveable { mutableStateOf(identity.bio) }
    var avatarPath by rememberSaveable { mutableStateOf(identity.avatarPath) }
    var status by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.identity_saved)
    val incomplete = stringResource(R.string.identity_name_required)
    val avatarInvalid = stringResource(R.string.identity_avatar_invalid)
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val directory = File(context.filesDir, "media").apply { mkdirs() }
            val target = File(directory, "user-avatar-${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error(avatarInvalid)
            check(BitmapFactory.decodeFile(target.path) != null) { avatarInvalid }
            avatarPath = target.path
        }.onFailure { status = it.message ?: avatarInvalid }
    }

    SettingsList(contentPadding) {
        item {
            ScreenHeading(onBack, R.string.user_identity, R.string.user_identity_summary)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Avatar(name.trim().ifBlank { "你" }.take(1).uppercase(), 88.dp, avatarPath)
                Text(
                    stringResource(R.string.identity_avatar_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) { Text(stringResource(R.string.choose_avatar)) }
                    if (avatarPath.isNotBlank()) {
                        TextButton(onClick = { avatarPath = "" }) {
                            Text(stringResource(R.string.remove_avatar))
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.current_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.address_preference)) },
                supportingText = { Text(stringResource(R.string.address_preference_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text(stringResource(R.string.identity_bio)) },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        status = incomplete
                    } else {
                        store.updateIdentity(name, address, bio, avatarPath)
                        onChanged()
                        status = saved
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_identity))
            }
        }
        status?.let { item { StatusCard(it) } }
    }
}

@Composable
internal fun CharacterManagerScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val characters = remember(revision) { store.characters() }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingCard by remember { mutableStateOf<ImportedCharacterCard?>(null) }
    val editing = characters.firstOrNull { it.id == editingId }
    val turningPoints = remember(revision, editingId) {
        editingId?.let(store::turningPoints).orEmpty()
    }
    val imported = stringResource(R.string.character_card_imported)
    val importFailed = stringResource(R.string.character_card_import_failed)
    fun importAsNew(card: ImportedCharacterCard) {
        val id = store.addCharacter(
            card.name,
            card.persona,
            "resident",
            "",
            "",
            "",
            card.rawJson,
        )
        if (card.firstMessage.isNotBlank()) store.addMessage(id, "assistant", card.firstMessage)
        card.lore.forEach { store.addCharacterCognition(id, it) }
        status = "$imported：${card.name}"
        onChanged()
    }

    fun mergeAsTurningPoint(card: ImportedCharacterCard) {
        val existing = characters.firstOrNull { it.name.equals(card.name, ignoreCase = true) }
            ?: return importAsNew(card)
        store.updateCharacter(existing.copy(persona = card.persona, cardJson = card.rawJson))
        card.lore.forEach { store.addCharacterCognition(existing.id, it) }
        status = "$imported：${card.name}"
        onChanged()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use {
                CharacterCardV2.read(it)
            } ?: error("无法读取文件")
            val card = CharacterCardV2.parse(bytes)
            if (characters.any { it.name.equals(card.name, ignoreCase = true) }) {
                pendingCard = card
            } else {
                importAsNew(card)
            }
        }.onFailure {
            status = "$importFailed：${it.message.orEmpty()}"
        }
    }

    pendingCard?.let { card ->
        Dialog(onDismissRequest = { pendingCard = null }) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.same_name_character_found, card.name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.same_name_character_choice))
                    Button(
                        onClick = {
                            importAsNew(card)
                            pendingCard = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_as_new_character))
                    }
                    TextButton(
                        onClick = {
                            mergeAsTurningPoint(card)
                            pendingCard = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.merge_as_turning_point))
                    }
                }
            }
        }
    }

    BackHandler(adding || editing != null) {
        adding = false
        editingId = null
    }
    if (adding || editing != null) {
        CharacterEditor(
            contentPadding = contentPadding,
            character = editing,
            turningPoints = turningPoints,
            canLeave = characters.count(ResidentCharacter::active) > 1,
            onBack = {
                adding = false
                editingId = null
            },
            onSave = { name, persona, tier, appearance, clothing, negative ->
                if (editing == null) {
                    store.addCharacter(name, persona, tier, appearance, clothing, negative)
                } else {
                    store.updateCharacter(
                        editing.copy(
                            name = name,
                            persona = persona,
                            attentionTier = tier,
                            appearance = appearance,
                            clothing = clothing,
                            negativePrompt = negative,
                        ),
                    )
                }
                onChanged()
                adding = false
                editingId = null
            },
            onActiveChange = editing?.let { character ->
                {
                    store.setCharacterActive(character.id, !character.active)
                    onChanged()
                    editingId = null
                }
            },
            onDelete = editing?.takeUnless(ResidentCharacter::active)?.let { character ->
                {
                    store.deleteCharacter(character.id)
                    onChanged()
                    editingId = null
                }
            },
        )
        return
    }

    SettingsList(contentPadding) {
        item {
            ScreenHeading(onBack, R.string.world_members, R.string.world_members_summary)
            Button(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_resident_character))
            }
            TextButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "image/png")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_character_card))
            }
        }
        status?.let { item { StatusCard(it) } }
        if (characters.isEmpty()) {
            item { StatusCard(stringResource(R.string.no_characters_yet)) }
        }
        items(characters, key = ResidentCharacter::id) { character ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingId = character.id },
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(character.name, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                when {
                                    !character.active -> R.string.departed
                                    character.attentionTier == "special_focus" ->
                                        R.string.special_focus
                                    else -> R.string.resident
                                },
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        character.persona,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterEditor(
    contentPadding: PaddingValues,
    character: ResidentCharacter?,
    turningPoints: List<CharacterTurningPoint>,
    canLeave: Boolean,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onActiveChange: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var name by rememberSaveable(character?.id) { mutableStateOf(character?.name.orEmpty()) }
    var persona by rememberSaveable(character?.id) { mutableStateOf(character?.persona.orEmpty()) }
    var tier by rememberSaveable(character?.id) {
        mutableStateOf(character?.attentionTier ?: "resident")
    }
    var appearance by rememberSaveable(character?.id) {
        mutableStateOf(character?.appearance.orEmpty())
    }
    var clothing by rememberSaveable(character?.id) {
        mutableStateOf(character?.clothing.orEmpty())
    }
    var negative by rememberSaveable(character?.id) {
        mutableStateOf(character?.negativePrompt.orEmpty())
    }
    var transferStatus by remember { mutableStateOf<String?>(null) }
    var deleteConfirmation by rememberSaveable(character?.id) { mutableStateOf("") }
    var showDeleteConfirmation by rememberSaveable(character?.id) { mutableStateOf(false) }
    val exported = stringResource(R.string.character_card_exported)
    val exportFailed = stringResource(R.string.character_card_export_failed)
    val jsonExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null || character == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(CharacterCardV2.export(character).toByteArray())
            } ?: error("无法写入文件")
        }.onSuccess { transferStatus = exported }
            .onFailure { transferStatus = "$exportFailed：${it.message.orEmpty()}" }
    }
    val pngExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri == null || character == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = CharacterCardV2.export(character)
            val png = CharacterCardV2.embedJson(CharacterCardV2.coverPng(character.name), json)
            context.contentResolver.openOutputStream(uri)?.use { it.write(png) }
                ?: error("无法写入文件")
        }.onSuccess { transferStatus = exported }
            .onFailure { transferStatus = "$exportFailed：${it.message.orEmpty()}" }
    }

    SettingsList(contentPadding) {
        item {
            ScreenHeading(
                onBack,
                if (character == null) R.string.add_resident_character else R.string.edit_character,
                R.string.character_history_preserved,
            )
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
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
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(stringResource(R.string.attention_tier), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tier == "special_focus",
                    onClick = { tier = "special_focus" },
                    label = { Text(stringResource(R.string.special_focus)) },
                )
                FilterChip(
                    selected = tier == "resident",
                    onClick = { tier = "resident" },
                    label = { Text(stringResource(R.string.resident)) },
                )
            }
        }
        item {
            Text(stringResource(R.string.visual_identity), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.visual_identity_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = appearance,
                onValueChange = { appearance = it },
                label = { Text(stringResource(R.string.fixed_appearance)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = clothing,
                onValueChange = { clothing = it },
                label = { Text(stringResource(R.string.usual_clothing)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = negative,
                onValueChange = { negative = it },
                label = { Text(stringResource(R.string.negative_prompt)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    onSave(name.trim(), persona.trim(), tier, appearance, clothing, negative)
                },
                enabled = name.isNotBlank() && persona.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save))
            }
        }
        if (character != null && onActiveChange != null) {
            item {
                Text(stringResource(R.string.character_card), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            jsonExporter.launch("${character.safeFileName()}.json")
                        },
                    ) {
                        Text(stringResource(R.string.export_json))
                    }
                    TextButton(
                        onClick = {
                            pngExporter.launch("${character.safeFileName()}.png")
                        },
                    ) {
                        Text(stringResource(R.string.export_png))
                    }
                }
                transferStatus?.let { StatusCard(it) }
            }
            if (turningPoints.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.character_turning_points), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.character_turning_points_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(turningPoints, key = CharacterTurningPoint::id) { point ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(point.createdAt)),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "${point.previousPersona}  →  ${point.newPersona}",
                                maxLines = 5,
                            )
                        }
                    }
                }
            }
            item {
                TextButton(
                    onClick = onActiveChange,
                    enabled = !character.active || canLeave,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (character.active) R.string.let_character_leave
                            else R.string.bring_character_back,
                        ),
                    )
                }
                if (character.active && !canLeave) {
                    Text(
                        stringResource(R.string.last_active_character),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onDelete != null) {
                item {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.delete_character_permanently),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation && onDelete != null) {
        Dialog(onDismissRequest = { showDeleteConfirmation = false }) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.delete_character_permanently),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.delete_character_warning))
                    OutlinedTextField(
                        value = deleteConfirmation,
                        onValueChange = { deleteConfirmation = it },
                        label = { Text(stringResource(R.string.type_app_name_to_confirm)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onDelete,
                        enabled = deleteConfirmation == stringResource(R.string.app_name),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.confirm_permanent_delete))
                    }
                    TextButton(
                        onClick = { showDeleteConfirmation = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }
}

private fun ResidentCharacter.safeFileName() =
    name.replace(Regex("""[^\p{L}\p{N}._-]"""), "_").ifBlank { "character" }

@Composable
private fun SettingsList(
    contentPadding: PaddingValues,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun ScreenHeading(onBack: () -> Unit, title: Int, summary: Int) {
    TextButton(onClick = onBack) { Text("‹  ${stringResource(R.string.back)}") }
    Text(
        stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Text(
        stringResource(summary),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
