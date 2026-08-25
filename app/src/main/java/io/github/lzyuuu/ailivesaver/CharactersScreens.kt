package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import android.os.StatFs
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Live v4.47 Characters shell: dark English, not the Fancy OS desktop cream shell. */
private val CharactersBg = Color(0xFF0A0E14)
private val CharactersTopBar = Color(0xFF111622)
private val CharactersInk = Color(0xFFF2F2F2)
private val CharactersMuted = Color(0xFF9A9EA8)
private val CharactersDisabled = Color(0xFF636565)
private val CharactersAccent = FancyGold
private val CharactersField = Color(0xFF121722)
private val CharactersBorder = Color(0xFF27292E)
private val CharactersFab = Color(0xFF1A2130)
private val CharactersDanger = Color(0xFFB44A4A)
private val CharactersPortraitBg = Color(0xFF1C2230)

/** material-icons-core 无 Download，按参考顶栏导入图标本地绘制。 */
private val CharactersDownloadIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CharactersDownload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(5f, 20f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            horizontalLineTo(5f)
            verticalLineToRelative(2f)
            close()
            moveTo(19f, 9f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineToRelative(6f)
            horizontalLineTo(5f)
            lineToRelative(7f, 7f)
            lineToRelative(7f, -7f)
            close()
        }
    }.build()
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
    var viewingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingCard by remember { mutableStateOf<ImportedCharacterCard?>(null) }
    val viewing = characters.firstOrNull { it.id == viewingId }
    val editing = characters.firstOrNull { it.id == editingId }
    val turningPoints = remember(revision, editingId, viewingId) {
        (editingId ?: viewingId)?.let(store::turningPoints).orEmpty()
    }
    val imported = stringResource(R.string.character_card_imported)
    val importFailed = stringResource(R.string.character_card_import_failed)
    val filtered = remember(characters, query) {
        val needle = query.trim()
        if (needle.isEmpty()) characters
        else characters.filter {
            val fields = CharacterCardV2.profileFields(it)
            it.name.contains(needle, ignoreCase = true) ||
                fields.handle.contains(needle, ignoreCase = true) ||
                fields.personality.contains(needle, ignoreCase = true)
        }
    }

    fun persistAvatar(card: ImportedCharacterCard): String {
        val bytes = card.avatarBytes ?: return ""
        val target = File(
            File(context.filesDir, "media").apply { mkdirs() },
            "character-avatar-${UUID.randomUUID()}.png",
        )
        return runCatching {
            target.writeBytes(bytes)
            check(BitmapFactory.decodeFile(target.path) != null)
            target.path
        }.getOrElse {
            target.delete()
            ""
        }
    }

    fun importAsNew(card: ImportedCharacterCard) {
        val avatarPath = persistAvatar(card)
        val fields = CharacterProfileFields(
            handle = card.handle.ifBlank { CharacterCardV2.slugHandle(card.name) },
            description = card.description.ifBlank { card.persona },
            personality = card.personality,
            scenario = card.scenario,
            firstMessage = card.firstMessage,
            relationship = card.relationship,
            avatarPath = avatarPath,
            visualStyle = card.visualStyle,
            gender = card.gender,
            appearancePrompt = card.appearancePrompt,
            clothing = card.clothing,
            negativePrompt = card.negativePrompt,
        )
        val cardJson = CharacterCardV2.buildCardJson(card.name, fields, card.rawJson)
        val id = store.addCharacter(
            card.name,
            CharacterCardV2.composePersona(fields).ifBlank { card.persona },
            "resident",
            card.appearancePrompt,
            card.clothing,
            card.negativePrompt,
            cardJson,
        )
        if (card.firstMessage.isNotBlank()) store.addMessage(id, "assistant", card.firstMessage)
        if (card.relationship.isNotBlank()) {
            store.correctRelationship(id, card.relationship, card.relationship, pinned = false)
        }
        card.lore.forEach { store.addCharacterCognition(id, it) }
        status = "$imported：${card.name}"
        onChanged()
    }

    fun mergeAsTurningPoint(card: ImportedCharacterCard) {
        val existing = characters.firstOrNull { it.name.equals(card.name, ignoreCase = true) }
            ?: return importAsNew(card)
        val current = CharacterCardV2.profileFields(existing)
        val fields = current.copy(
            description = card.description.ifBlank { card.persona }.ifBlank { current.description },
            personality = card.personality.ifBlank { current.personality },
            scenario = card.scenario.ifBlank { current.scenario },
            firstMessage = card.firstMessage.ifBlank { current.firstMessage },
            relationship = card.relationship.ifBlank { current.relationship },
            avatarPath = persistAvatar(card).ifBlank { current.avatarPath },
            visualStyle = card.visualStyle.ifBlank { current.visualStyle },
            gender = card.gender.ifBlank { current.gender },
            appearancePrompt = card.appearancePrompt.ifBlank { current.appearancePrompt },
            clothing = card.clothing.ifBlank { current.clothing },
            negativePrompt = card.negativePrompt.ifBlank { current.negativePrompt },
        )
        store.updateCharacter(
            existing.copy(
                persona = CharacterCardV2.composePersona(fields).ifBlank { card.persona },
                appearance = fields.appearancePrompt.ifBlank { existing.appearance },
                clothing = fields.clothing.ifBlank { existing.clothing },
                negativePrompt = fields.negativePrompt.ifBlank { existing.negativePrompt },
                cardJson = CharacterCardV2.buildCardJson(existing.name, fields, existing.cardJson),
            ),
        )
        if (card.relationship.isNotBlank()) {
            store.correctRelationship(existing.id, card.relationship, card.relationship, pinned = false)
        }
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharactersField),
            ) {
                Column(
                    modifier = Modifier
                        .background(CharactersField)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.same_name_character_found, card.name),
                        color = CharactersInk,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.same_name_character_choice),
                        color = CharactersMuted,
                    )
                    Button(
                        onClick = {
                            importAsNew(card)
                            pendingCard = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CharactersAccent,
                            contentColor = Color.White,
                        ),
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
                        Text(stringResource(R.string.merge_as_turning_point), color = CharactersAccent)
                    }
                }
            }
        }
    }

    BackHandler(adding || editing != null || viewing != null) {
        when {
            adding || editing != null -> {
                adding = false
                editingId = null
            }
            viewing != null -> viewingId = null
        }
    }
    // Editor (New / existing) takes priority over read-only viewer.
    if (adding || editing != null) {
        CharacterEditor(
            contentPadding = contentPadding,
            character = editing,
            turningPoints = turningPoints,
            readOnly = false,
            canLeave = characters.count(ResidentCharacter::active) > 1,
            onBack = {
                adding = false
                editingId = null
            },
            onRequestEdit = null,
            onSave = { name, fields, tier, appearance, clothing, negative ->
                val persona = CharacterCardV2.composePersona(fields).ifBlank { name }
                val cardJson = CharacterCardV2.buildCardJson(name, fields, editing?.cardJson.orEmpty())
                if (editing == null) {
                    val id = store.addCharacter(
                        name,
                        persona,
                        tier,
                        appearance,
                        clothing,
                        negative,
                        cardJson,
                    )
                    if (fields.firstMessage.isNotBlank()) {
                        store.addMessage(id, "assistant", fields.firstMessage)
                    }
                    if (fields.relationship.isNotBlank()) {
                        store.correctRelationship(
                            id,
                            fields.relationship,
                            fields.relationship,
                            pinned = false,
                        )
                    }
                } else {
                    store.updateCharacter(
                        editing.copy(
                            name = name,
                            persona = persona,
                            attentionTier = tier,
                            appearance = appearance,
                            clothing = clothing,
                            negativePrompt = negative,
                            cardJson = cardJson,
                        ),
                    )
                    if (fields.relationship.isNotBlank()) {
                        store.correctRelationship(
                            editing.id,
                            fields.relationship,
                            fields.relationship,
                            pinned = false,
                        )
                    }
                }
                onChanged()
                adding = false
                editingId = null
                viewingId = null
            },
            onActiveChange = editing?.let { character ->
                {
                    store.setCharacterActive(character.id, !character.active)
                    onChanged()
                    editingId = null
                    viewingId = null
                }
            },
            onDelete = editing?.let { character ->
                {
                    if (character.active) {
                        store.setCharacterActive(character.id, false)
                    }
                    store.deleteCharacter(character.id)
                    onChanged()
                    editingId = null
                    viewingId = null
                }
            },
        )
        return
    }
    if (viewing != null) {
        CharacterEditor(
            contentPadding = contentPadding,
            character = viewing,
            turningPoints = turningPoints,
            readOnly = true,
            canLeave = characters.count(ResidentCharacter::active) > 1,
            onBack = { viewingId = null },
            onRequestEdit = {
                editingId = viewing.id
                viewingId = null
            },
            onSave = { _, _, _, _, _, _ -> },
            onActiveChange = null,
            onDelete = null,
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharactersBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("characters-app"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(CharactersTopBar)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回桌面",
                        tint = CharactersInk,
                    )
                }
                Text(
                    stringResource(R.string.characters_app_title),
                    color = CharactersInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/json",
                                "image/png",
                                "text/xml",
                                "application/xml",
                                "text/plain",
                                "*/*",
                            ),
                        )
                    },
                    modifier = Modifier.testTag("characters-import"),
                ) {
                    Icon(
                        CharactersDownloadIcon,
                        contentDescription = stringResource(R.string.import_character_card),
                        tint = CharactersInk,
                    )
                }
                IconButton(
                    onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    },
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.characters_search),
                        tint = CharactersInk,
                    )
                }
            }

            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    placeholder = {
                        Text(
                            stringResource(R.string.characters_search_hint),
                            color = CharactersMuted,
                        )
                    },
                    colors = charactersFieldColors(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            status?.let {
                Text(
                    it,
                    color = CharactersAccent,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.no_characters_yet),
                    color = CharactersMuted,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filtered, key = ResidentCharacter::id) { character ->
                        val fields = CharacterCardV2.profileFields(character)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewingId = character.id }
                                .testTag("character-card-${character.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CharacterPortrait(
                                name = character.name,
                                imagePath = fields.avatarPath,
                                rounded = 24.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                character.name,
                                color = CharactersInk,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "@${fields.handle}",
                                color = CharactersMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!character.active) {
                                Text(
                                    stringResource(R.string.departed),
                                    color = CharactersAccent,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 28.dp)
                .size(56.dp)
                .testTag("characters-new"),
            containerColor = CharactersFab,
            contentColor = CharactersAccent,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp,
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_character_title))
        }
    }
}

@Composable
private fun CharacterEditor(
    contentPadding: PaddingValues,
    character: ResidentCharacter?,
    turningPoints: List<CharacterTurningPoint>,
    readOnly: Boolean,
    canLeave: Boolean,
    onBack: () -> Unit,
    onRequestEdit: (() -> Unit)?,
    onSave: (String, CharacterProfileFields, String, String, String, String) -> Unit,
    onActiveChange: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val initial = remember(character?.id, character?.cardJson) {
        character?.let(CharacterCardV2::profileFields) ?: CharacterProfileFields()
    }
    val isRoot = character?.name.equals("Root", ignoreCase = true) ||
        initial.handle.equals("root", ignoreCase = true) ||
        character?.name.equals(DesktopSeed.ROOT_NAME, ignoreCase = true)

    var name by rememberSaveable(character?.id) { mutableStateOf(character?.name.orEmpty()) }
    var handle by rememberSaveable(character?.id) {
        mutableStateOf(initial.handle.ifBlank { CharacterCardV2.slugHandle(character?.name.orEmpty()) })
    }
    var personality by rememberSaveable(character?.id) { mutableStateOf(initial.personality) }
    var relationship by rememberSaveable(character?.id) { mutableStateOf(initial.relationship) }
    var description by rememberSaveable(character?.id) {
        mutableStateOf(initial.description.ifBlank { character?.persona.orEmpty() })
    }
    var scenario by rememberSaveable(character?.id) { mutableStateOf(initial.scenario) }
    var firstMessage by rememberSaveable(character?.id) { mutableStateOf(initial.firstMessage) }
    var avatarPath by rememberSaveable(character?.id) { mutableStateOf(initial.avatarPath) }
    var tier by rememberSaveable(character?.id) {
        mutableStateOf(character?.attentionTier ?: "resident")
    }
    var appearance by rememberSaveable(character?.id) {
        mutableStateOf(initial.appearancePrompt.ifBlank { character?.appearance.orEmpty() })
    }
    var clothing by rememberSaveable(character?.id) {
        mutableStateOf(initial.clothing.ifBlank { character?.clothing.orEmpty() })
    }
    var negative by rememberSaveable(character?.id) {
        mutableStateOf(initial.negativePrompt.ifBlank { character?.negativePrompt.orEmpty() })
    }
    var visualStyle by rememberSaveable(character?.id) { mutableStateOf(initial.visualStyle) }
    var gender by rememberSaveable(character?.id) { mutableStateOf(initial.gender) }
    var editorTab by rememberSaveable(character?.id) { mutableStateOf(0) }
    var extractingAppearance by remember { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf<String?>(null) }
    var deleteConfirmation by rememberSaveable(character?.id) { mutableStateOf("") }
    var showDeleteConfirmation by rememberSaveable(character?.id) { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    val handleLocked = character != null || isRoot
    val exported = stringResource(R.string.character_card_exported)
    val exportFailed = stringResource(R.string.character_card_export_failed)
    val avatarInvalid = stringResource(R.string.identity_avatar_invalid)
    val avatarTooLarge = stringResource(R.string.identity_avatar_too_large)
    val avatarStorageLow = stringResource(R.string.identity_avatar_storage_low)

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = File(
            File(context.filesDir, "media").apply { mkdirs() },
            "character-avatar-${UUID.randomUUID()}.jpg",
        )
        runCatching {
            check(storageAllowsGeneration(StatFs(context.filesDir.path).availableBytes)) {
                avatarStorageLow
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    var total = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(avatarImportAllowed(total)) { avatarTooLarge }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error(avatarInvalid)
            check(BitmapFactory.decodeFile(target.path) != null) { avatarInvalid }
            avatarPath = target.path
        }.onFailure {
            target.delete()
            transferStatus = it.message ?: avatarInvalid
        }
    }

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
            val cover = fieldsAvatarCover(character, avatarPath)
            val png = CharacterCardV2.embedJson(cover, json)
            context.contentResolver.openOutputStream(uri)?.use { it.write(png) }
                ?: error("无法写入文件")
        }.onSuccess { transferStatus = exported }
            .onFailure { transferStatus = "$exportFailed：${it.message.orEmpty()}" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharactersBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag(if (readOnly) "character-viewer" else "character-editor"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(CharactersTopBar)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = CharactersInk,
                )
            }
            Text(
                if (character == null) {
                    stringResource(R.string.new_character_title)
                } else {
                    character.name
                },
                color = CharactersInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Live viewer / editor chrome: Back + title + Share. Existing editor keeps Delete for maintenance.
            if (character != null) {
                val editingCharacter = character
                Box {
                    IconButton(onClick = { exportMenuOpen = true }) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = CharactersInk)
                    }
                    DropdownMenu(
                        expanded = exportMenuOpen,
                        onDismissRequest = { exportMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_json)) },
                            onClick = {
                                exportMenuOpen = false
                                jsonExporter.launch("${editingCharacter.safeFileName()}.json")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_png)) },
                            onClick = {
                                exportMenuOpen = false
                                pngExporter.launch("${editingCharacter.safeFileName()}.png")
                            },
                        )
                    }
                }
            }
            if (!readOnly && character != null && onDelete != null && !isRoot) {
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = CharactersDanger)
                }
            }
            if (!readOnly) {
                TextButton(
                    onClick = {
                        val fields = CharacterProfileFields(
                            handle = if (handleLocked) initial.handle else handle,
                            description = if (isRoot) initial.description.ifBlank { DesktopSeed.ROOT_PERSONA } else description,
                            personality = if (isRoot) initial.personality else personality,
                            scenario = if (isRoot) initial.scenario else scenario,
                            firstMessage = if (isRoot) initial.firstMessage else firstMessage,
                            relationship = if (isRoot) initial.relationship else relationship,
                            avatarPath = avatarPath,
                            visualStyle = visualStyle,
                            gender = gender,
                            appearancePrompt = appearance,
                            clothing = clothing,
                            negativePrompt = negative,
                        )
                        val targetName = if (isRoot) (character?.name ?: "Root") else name.trim()
                        onSave(
                            targetName,
                            fields,
                            tier,
                            appearance,
                            clothing,
                            negative,
                        )
                    },
                    enabled = isRoot || name.isNotBlank(),
                ) {
                    Text(
                        if (character == null) {
                            stringResource(R.string.new_character_create)
                        } else {
                            stringResource(R.string.character_save)
                        },
                        color = if (isRoot || name.isNotBlank()) CharactersAccent else CharactersMuted,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = if (readOnly) 16.dp else 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (readOnly) 8.dp else 12.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CharacterPortrait(
                    name = name.trim().ifBlank { if (isRoot) "R" else "?" },
                    imagePath = avatarPath,
                    circular = true,
                    modifier = Modifier
                        .size(100.dp)
                        .border(1.dp, CharactersBorder, CircleShape)
                        .clickable {
                            if (readOnly) {
                                onRequestEdit?.invoke()
                            } else {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                        },
                )
                Text(
                    stringResource(R.string.character_avatar_hint),
                    color = CharactersMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf("角色", "外貌").forEachIndexed { index, label ->
                    FilterChip(
                        selected = editorTab == index,
                        onClick = { editorTab = index },
                        label = {
                            Text(
                                label,
                                fontWeight = if (editorTab == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        modifier = Modifier.testTag("character-tab-$index"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CharactersAccent.copy(alpha = 0.22f),
                            selectedLabelColor = CharactersAccent,
                            labelColor = CharactersMuted,
                        ),
                    )
                }
            }

            if (editorTab == 0) {
                if (isRoot) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = CharactersField),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            "Root 为系统内置角色，基础人设已锁定保护。您可以在“外貌”页签中自定义外观与视觉风格。",
                            color = CharactersAccent,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                SectionLabel(stringResource(R.string.character_section_identity), readOnly)
                CharactersField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (!handleLocked) handle = CharacterCardV2.slugHandle(it)
                    },
                    label = stringResource(R.string.character_name_label),
                    enabled = !readOnly && !isRoot,
                    onClickWhenDisabled = if (isRoot) null else onRequestEdit,
                )
                CharactersField(
                    value = if (handle.startsWith("@")) handle else "@$handle",
                    onValueChange = {
                        if (!handleLocked) handle = CharacterCardV2.normalizeHandle(it)
                    },
                    label = stringResource(R.string.character_handle_label),
                    enabled = !readOnly && !handleLocked,
                    supporting = stringResource(R.string.character_handle_hint),
                    onClickWhenDisabled = if (isRoot) null else onRequestEdit,
                )

                SectionLabel(stringResource(R.string.character_section_personality), readOnly)
                CharactersField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = stringResource(R.string.character_personality_label),
                    enabled = !readOnly && !isRoot,
                    minLines = 3,
                    fixedHeight = if (readOnly) 130.dp else null,
                    onClickWhenDisabled = if (isRoot) null else onRequestEdit,
                )
                CharactersField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = stringResource(R.string.character_relationship_label),
                    enabled = !readOnly && !isRoot,
                    supporting = if (character == null) {
                        stringResource(R.string.character_relationship_hint)
                    } else {
                        null
                    },
                    fixedHeight = if (readOnly) 64.2.dp else null,
                    onClickWhenDisabled = if (isRoot) null else onRequestEdit,
                )
                CharactersField(
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.character_description_label),
                    enabled = !readOnly && !isRoot,
                    minLines = 8,
                    fixedHeight = if (readOnly) 280.2.dp else null,
                    onClickWhenDisabled = if (isRoot) null else onRequestEdit,
                )

                SectionLabel(
                    stringResource(R.string.character_section_chat),
                    viewer = readOnly,
                    extraTop = if (readOnly) 10.dp else 0.dp,
                )
                CharactersField(
                    value = scenario,
                    onValueChange = { scenario = it },
                    label = stringResource(R.string.character_scenario_label),
                    enabled = !readOnly && !isRoot,
                    minLines = 3,
                    fixedHeight = if (readOnly) 88.2.dp else null,
                    onClickWhenDisabled = if (isRoot) null else onRequestEdit,
                )
                CharactersField(
                    value = firstMessage,
                    onValueChange = { firstMessage = it },
                    label = stringResource(R.string.character_first_message_label),
                    enabled = !readOnly && !isRoot,
                    minLines = 3,
                    fixedHeight = if (readOnly) 88.2.dp else null,
                    onClickWhenDisabled = if (isRoot) null else onRequestEdit,
                )
            }

            if (editorTab == 1) {
                if (!readOnly) {
                    SectionLabel(stringResource(R.string.character_attention_tier))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tier == "special_focus",
                            onClick = { tier = "special_focus" },
                            label = { Text(stringResource(R.string.character_special_focus)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CharactersAccent.copy(alpha = 0.22f),
                                selectedLabelColor = CharactersAccent,
                                labelColor = CharactersMuted,
                            ),
                        )
                        FilterChip(
                            selected = tier == "resident",
                            onClick = { tier = "resident" },
                            label = { Text(stringResource(R.string.character_resident)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CharactersAccent.copy(alpha = 0.22f),
                                selectedLabelColor = CharactersAccent,
                                labelColor = CharactersMuted,
                            ),
                        )
                    }
                }

                SectionLabel(stringResource(R.string.character_visual_identity), readOnly)
                Text(
                    stringResource(R.string.character_visual_identity_summary),
                    color = CharactersMuted,
                    fontSize = 12.sp,
                )

                Text(
                    "艺术风格预设",
                    color = CharactersInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Photoreal", "Film", "Anime", "Painted").forEach { style ->
                        FilterChip(
                            selected = visualStyle.equals(style, ignoreCase = true),
                            onClick = {
                                if (!readOnly) {
                                    visualStyle = if (visualStyle.equals(style, ignoreCase = true)) "" else style
                                }
                            },
                            label = { Text(style) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CharactersAccent.copy(alpha = 0.22f),
                                selectedLabelColor = CharactersAccent,
                                labelColor = CharactersMuted,
                            ),
                        )
                    }
                }

                Text(
                    "性别预设",
                    color = CharactersInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Woman", "Man", "Non-binary", "Unspecified").forEach { value ->
                        FilterChip(
                            selected = gender.equals(value, ignoreCase = true),
                            onClick = {
                                if (!readOnly) {
                                    gender = if (gender.equals(value, ignoreCase = true)) "" else value
                                }
                            },
                            label = { Text(value) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CharactersAccent.copy(alpha = 0.22f),
                                selectedLabelColor = CharactersAccent,
                                labelColor = CharactersMuted,
                            ),
                        )
                    }
                }

                if (!readOnly) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "固定外观特征",
                            color = CharactersInk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        TextButton(
                            onClick = {
                                extractingAppearance = true
                                transferStatus = "正在提取外观关键词..."
                                AppearanceExtractor.extract(context, description, personality) { extracted, isLlm ->
                                    extractingAppearance = false
                                    if (extracted.appearancePrompt.isNotBlank()) {
                                        appearance = extracted.appearancePrompt
                                    }
                                    if (extracted.clothing.isNotBlank()) {
                                        clothing = extracted.clothing
                                    }
                                    if (extracted.visualStyle.isNotBlank() && visualStyle.isBlank()) {
                                        visualStyle = extracted.visualStyle
                                    }
                                    if (extracted.gender.isNotBlank() && gender.isBlank()) {
                                        gender = extracted.gender
                                    }
                                    if (extracted.negativePrompt.isNotBlank() && negative.isBlank()) {
                                        negative = extracted.negativePrompt
                                    }
                                    transferStatus = if (isLlm) {
                                        "已根据人设智能提取外观关键词"
                                    } else {
                                        "已根据本地规则填充（配置 Provider 后效果更好）"
                                    }
                                }
                            },
                            enabled = !extractingAppearance && (description.isNotBlank() || personality.isNotBlank()),
                        ) {
                            Text(
                                if (extractingAppearance) "正在提取..." else "从人设自动提取外观",
                                color = CharactersAccent,
                            )
                        }
                    }
                }

                CharactersField(
                    value = appearance,
                    onValueChange = { appearance = it },
                    label = stringResource(R.string.character_fixed_appearance),
                    enabled = !readOnly,
                    minLines = 2,
                    supporting = "发色、瞳色、体态特征等（如 short silver hair, blue eyes）",
                    fixedHeight = if (readOnly) 88.dp else null,
                    onClickWhenDisabled = onRequestEdit,
                )
                CharactersField(
                    value = clothing,
                    onValueChange = { clothing = it },
                    label = stringResource(R.string.character_usual_clothing),
                    enabled = !readOnly,
                    minLines = 2,
                    supporting = "常用服装搭配（如 black trench coat, white shirt）",
                    fixedHeight = if (readOnly) 88.dp else null,
                    onClickWhenDisabled = onRequestEdit,
                )
                CharactersField(
                    value = negative,
                    onValueChange = { negative = it },
                    label = stringResource(R.string.character_negative_prompt),
                    enabled = !readOnly,
                    minLines = 2,
                    supporting = "过滤畸变与低质量标签（如 blurry, bad anatomy, deformed）",
                    fixedHeight = if (readOnly) 88.dp else null,
                    onClickWhenDisabled = onRequestEdit,
                )
            }

            transferStatus?.let { statusText ->
                Text(statusText, color = CharactersAccent, fontSize = 13.sp)
            }

            if (!readOnly && character != null) {
                if (turningPoints.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.character_turning_points))
                    Text(
                        stringResource(R.string.character_turning_points_summary),
                        color = CharactersMuted,
                        fontSize = 12.sp,
                    )
                    turningPoints.forEach { point ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CharactersField)
                                .border(1.dp, CharactersBorder, RoundedCornerShape(10.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(point.createdAt)),
                                color = CharactersAccent,
                            )
                            Text(
                                "${point.previousPersona}  →  ${point.newPersona}",
                                color = CharactersInk.copy(alpha = 0.85f),
                                maxLines = 5,
                            )
                        }
                    }
                }
                if (onActiveChange != null && !isRoot) {
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
                            color = CharactersAccent,
                        )
                    }
                    if (character.active && !canLeave) {
                        Text(
                            stringResource(R.string.last_active_character),
                            color = CharactersMuted,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirmation && onDelete != null && !isRoot) {
        Dialog(onDismissRequest = { showDeleteConfirmation = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .background(CharactersField)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.delete_character_permanently),
                        color = CharactersInk,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.delete_character_warning), color = CharactersMuted)
                    CharactersField(
                        value = deleteConfirmation,
                        onValueChange = { deleteConfirmation = it },
                        label = stringResource(R.string.type_app_name_to_confirm),
                    )
                    Button(
                        onClick = onDelete,
                        enabled = deleteConfirmation == stringResource(R.string.app_name),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CharactersDanger,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(stringResource(R.string.confirm_permanent_delete))
                    }
                    TextButton(
                        onClick = { showDeleteConfirmation = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.cancel), color = CharactersAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, viewer: Boolean = false, extraTop: Dp = 0.dp) {
    Text(
        text,
        color = CharactersAccent,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = if (viewer) 19.sp else 24.sp,
        modifier = Modifier.padding(
            start = if (viewer) 4.dp else 0.dp,
            top = 6.dp + extraTop,
        ),
    )
}

@Composable
private fun CharactersField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    supporting: String? = null,
    minLines: Int = 1,
    /** When set (viewer), match Fancy live EditText outer height; text scrolls inside. */
    fixedHeight: Dp? = null,
    onClickWhenDisabled: (() -> Unit)? = null,
) {
    val fieldModifier = Modifier
        .fillMaxWidth()
        .then(
            if (fixedHeight != null) {
                Modifier.height(fixedHeight)
            } else {
                Modifier.heightIn(min = if (minLines == 1) 64.dp else (24.dp * minLines + 40.dp))
            },
        )
        .then(
            if (!enabled && onClickWhenDisabled != null) {
                Modifier.clickable(onClick = onClickWhenDisabled)
            } else {
                Modifier
            },
        )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it, color = CharactersMuted) } },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            letterSpacing = if (fixedHeight != null) 0.3.sp else MaterialTheme.typography.bodyLarge.letterSpacing,
        ),
        enabled = enabled,
        singleLine = minLines == 1,
        minLines = if (fixedHeight != null) 1 else minLines,
        maxLines = when {
            fixedHeight != null -> Int.MAX_VALUE
            minLines == 1 -> 1
            else -> minLines + 8
        },
        modifier = fieldModifier,
        shape = RoundedCornerShape(10.dp),
        colors = charactersFieldColors(),
    )
}

@Composable
private fun charactersFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CharactersAccent,
    unfocusedBorderColor = CharactersBorder,
    disabledBorderColor = CharactersBorder,
    focusedTextColor = CharactersInk,
    unfocusedTextColor = CharactersInk,
    disabledTextColor = CharactersDisabled,
    cursorColor = CharactersAccent,
    focusedContainerColor = CharactersField,
    unfocusedContainerColor = CharactersField,
    // Viewer fields are disabled; Fancy fill matches page bg (#0A0E14), not alpha-blended Field.
    disabledContainerColor = CharactersBg,
    focusedLabelColor = CharactersAccent,
    unfocusedLabelColor = CharactersMuted,
    disabledLabelColor = CharactersDisabled,
    focusedSupportingTextColor = CharactersMuted,
    unfocusedSupportingTextColor = CharactersMuted,
)

@Composable
private fun CharacterPortrait(
    name: String,
    imagePath: String,
    modifier: Modifier = Modifier,
    rounded: Dp = 18.dp,
    circular: Boolean = false,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(rounded)
    val bitmap = remember(imagePath) {
        imagePath.takeIf(String::isNotBlank)?.let(BitmapFactory::decodeFile)?.asImageBitmap()
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(CharactersPortraitBg),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.take(1).uppercase(),
                color = CharactersAccent,
                fontSize = if (circular) 36.sp else 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun ResidentCharacter.safeFileName() =
    name.replace(Regex("""[^\p{L}\p{N}._-]"""), "_").ifBlank { "character" }

private fun fieldsAvatarCover(character: ResidentCharacter, avatarPath: String): ByteArray {
    if (avatarPath.isNotBlank()) {
        val decoded = runCatching { File(avatarPath).readBytes() }.getOrNull()
        if (decoded != null && decoded.size >= 8 &&
            decoded[0] == (-119).toByte() && decoded[1] == 80.toByte()
        ) {
            return decoded
        }
    }
    return CharacterCardV2.coverPng(character.name)
}
