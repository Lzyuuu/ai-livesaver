package io.github.lzyuuu.ailivesaver

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.UUID

@Composable
internal fun VoiceCallsSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
    var enabled by rememberSaveable { mutableStateOf(preferences.getBoolean(VOICE_ENABLED_KEY, false)) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("voice-calls-settings"),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenBackButton(onBack)
            Text(stringResource(R.string.voice_calls_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.voice_calls_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.voice_calls_enable), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.voice_calls_enable_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                preferences.edit().putBoolean(VOICE_ENABLED_KEY, it).apply()
                            },
                            modifier = Modifier.testTag("voice-enabled-switch"),
                        )
                    }
                    Text(stringResource(R.string.voice_calls_skeleton), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun StorageScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val databaseBytes = File(context.getDatabasePath("world.db").path).length()
    val mediaBytes = rememberStorageBytes(revision) { store.mediaStorageBytes() }
    val appBytes = context.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val freeBytes = StatFs(context.filesDir.path).availableBytes
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("storage-settings-screen"),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenBackButton(onBack)
            Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.storage_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StorageRow(stringResource(R.string.storage_world_database), databaseBytes, context)
                    StorageRow(stringResource(R.string.storage_media), mediaBytes, context)
                    StorageRow(stringResource(R.string.storage_app_files), appBytes, context)
                    StorageRow(stringResource(R.string.storage_available), freeBytes, context)
                }
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, context: android.content.Context) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(Formatter.formatFileSize(context, bytes), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun <T> rememberStorageBytes(key: Any?, calculation: () -> T): T =
    androidx.compose.runtime.remember(key) { calculation() }

@Composable
internal fun GenerationSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProviderStore(context) }
    var settings by remember { mutableStateOf(store.loadDefaultGeneration()) }
    var maxTokensText by remember { mutableStateOf(settings.maxTokens.toString()) }
    var status by remember { mutableStateOf<String?>(null) }
    val savedMessage = stringResource(R.string.generation_saved)

    fun update(transform: (GenerationSettings) -> GenerationSettings) {
        settings = transform(settings).normalized()
        maxTokensText = settings.maxTokens.toString()
        status = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenBackButton(onBack)
            Text(
                stringResource(R.string.generation_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.generation_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.generation_preset),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            GenerationPresetChips(
                selected = settings.preset,
                onPick = { preset ->
                    update { current ->
                        if (preset == GenerationPreset.Custom) {
                            current.copy(preset = GenerationPreset.Custom)
                        } else {
                            current.copy(
                                temperature = preset.temperature,
                                maxTokens = preset.maxTokens,
                                topP = preset.topP,
                                preset = preset,
                            )
                        }
                    }
                },
            )
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.generation_temperature))
                Slider(
                    value = settings.temperature,
                    onValueChange = { value ->
                        update { it.copy(temperature = Math.round(value * 10f) / 10f, preset = GenerationPreset.Custom) }
                    },
                    valueRange = 0f..2f,
                    steps = 19,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generation-temperature"),
                )
                Text(
                    "%.1f".format(settings.temperature),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.generation_top_p))
                Slider(
                    value = settings.topP,
                    onValueChange = { value ->
                        update { it.copy(topP = Math.round(value * 100f) / 100f, preset = GenerationPreset.Custom) }
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generation-top-p"),
                )
                Text(
                    "%.2f".format(settings.topP),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            OutlinedTextField(
                value = maxTokensText,
                onValueChange = { value ->
                    val digits = value.filter(Char::isDigit).take(4)
                    maxTokensText = digits
                    digits.toIntOrNull()?.let { tokens -> update { it.copy(maxTokens = tokens, preset = GenerationPreset.Custom) } }
                },
                label = { Text(stringResource(R.string.generation_max_tokens)) },
                supportingText = { Text(stringResource(R.string.generation_max_tokens_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generation-max-tokens"),
            )
        }
        item {
            Button(
                onClick = {
                    settings = settings.normalized()
                    store.saveDefaultGeneration(settings)
                    maxTokensText = settings.maxTokens.toString()
                    status = savedMessage
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generation-save"),
            ) {
                Text(stringResource(R.string.save))
            }
        }
        status?.let { message ->
            item { StatusCard(message) }
        }
    }
}

/** 采样预设单选：精准/均衡/创意直接套用预设值，手调数值后自动落到自定义。 */
@Composable
internal fun GenerationPresetChips(
    selected: GenerationPreset,
    onPick: (GenerationPreset) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == GenerationPreset.Precise,
            onClick = { onPick(GenerationPreset.Precise) },
            label = { Text(stringResource(R.string.generation_preset_precise)) },
        )
        FilterChip(
            selected = selected == GenerationPreset.Balanced,
            onClick = { onPick(GenerationPreset.Balanced) },
            label = { Text(stringResource(R.string.generation_preset_balanced)) },
        )
        FilterChip(
            selected = selected == GenerationPreset.Creative,
            onClick = { onPick(GenerationPreset.Creative) },
            label = { Text(stringResource(R.string.generation_preset_creative)) },
        )
        FilterChip(
            selected = selected == GenerationPreset.Custom,
            onClick = { onPick(GenerationPreset.Custom) },
            label = { Text(stringResource(R.string.generation_preset_custom)) },
        )
    }
}

@Composable
internal fun InstructionTemplateChips(
    library: List<InstructionTemplateEntry>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        library.forEach { entry ->
            FilterChip(
                selected = selectedId == entry.id,
                onClick = { onSelect(entry.id) },
                label = { Text(entry.name) },
            )
        }
    }
}

@Composable
internal fun InstructionSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProviderStore(context) }
    var settings by remember {
        val loaded = store.loadDefaultGeneration()
        val resolved = if (loaded.instructionLibrary.any { it.id == loaded.instructionTemplateId }) {
            loaded
        } else {
            loaded.copy(instructionTemplateId = FACTORY_INSTRUCTION_ROLEPLAY_ID)
        }
        mutableStateOf(resolved)
    }
    var saveAsName by remember { mutableStateOf("") }
    var showSaveAs by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    val selected = settings.selectedInstructionEntry()

    fun persist(next: GenerationSettings) {
        settings = next
        store.saveDefaultGeneration(next)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("instruction-settings"),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 72.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenBackButton(onBack)
            Text(
                stringResource(R.string.instruction_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(
                stringResource(R.string.instruction_saved_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    settings.instructionLibrary.forEach { entry ->
                        val selectedRow = entry.id == settings.instructionTemplateId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("instruction-template-${entry.id}")
                                .clickable { persist(settings.copy(instructionTemplateId = entry.id)) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedRow,
                                onClick = { persist(settings.copy(instructionTemplateId = entry.id)) },
                            )
                            Text(
                                entry.name,
                                modifier = Modifier.weight(1f),
                                color = if (selectedRow) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (selectedRow) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (!selectedRow) {
                                TextButton(
                                    onClick = {
                                        persist(
                                            settings.copy(
                                                instructionLibrary = deleteInstructionTemplate(
                                                    settings.instructionLibrary,
                                                    settings.instructionTemplateId,
                                                    entry.id,
                                                ),
                                            ),
                                        )
                                    },
                                    modifier = Modifier.testTag("instruction-delete-${entry.id}"),
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.instruction_selection_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.instruction_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = selected.body,
                        onValueChange = { value ->
                            persist(
                                settings.copy(
                                    instructionLibrary = settings.instructionLibrary.map { entry ->
                                        if (entry.id == settings.instructionTemplateId) {
                                            entry.copy(body = value)
                                        } else {
                                            entry
                                        }
                                    },
                                ),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("instruction-body"),
                        label = { Text(selected.name) },
                        minLines = 4,
                        maxLines = 8,
                    )
                    Text(
                        stringResource(R.string.instruction_token_hint, instructionTokenHint(selected.body)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.instruction_write_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.instruction_image_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.imagePromptTemplate,
                        onValueChange = { value -> persist(settings.copy(imagePromptTemplate = value)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 168.dp)
                            .testTag("instruction-image-prompt"),
                        label = { Text(stringResource(R.string.instruction_image_template_label)) },
                        minLines = 4,
                        maxLines = 6,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.instruction_template_actions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = {
                                saveAsName = selected.name
                                showSaveAs = true
                            },
                            modifier = Modifier.testTag("instruction-save-as-new"),
                        ) {
                            Text(stringResource(R.string.instruction_save_as_new))
                        }
                        TextButton(
                            onClick = { showRestore = true },
                            modifier = Modifier.testTag("instruction-restore-factory"),
                        ) {
                            Text(stringResource(R.string.instruction_restore_factory))
                        }
                    }
                    Text(
                        stringResource(R.string.instruction_restore_warning),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showSaveAs) {
        AlertDialog(
            onDismissRequest = { showSaveAs = false },
            title = { Text(stringResource(R.string.instruction_name_template)) },
            text = {
                OutlinedTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("instruction-save-as-name"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val (library, newId) = saveInstructionTemplateAsNew(
                            settings.instructionLibrary,
                            selected.body,
                            saveAsName,
                            newId = "user-${UUID.randomUUID()}",
                        )
                        persist(settings.copy(instructionLibrary = library, instructionTemplateId = newId))
                        showSaveAs = false
                    },
                ) {
                    Text(stringResource(R.string.instruction_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAs = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showRestore) {
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text(stringResource(R.string.instruction_restore_factory)) },
            text = { Text(stringResource(R.string.instruction_restore_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        persist(restoreFactoryInstructionLibrary(settings))
                        showRestore = false
                    },
                    modifier = Modifier.testTag("instruction-restore-confirm"),
                ) {
                    Text(stringResource(R.string.instruction_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
