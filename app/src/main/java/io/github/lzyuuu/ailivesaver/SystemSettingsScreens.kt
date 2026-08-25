package io.github.lzyuuu.ailivesaver

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File

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
                            GenerationSettings(
                                preset.temperature,
                                preset.maxTokens,
                                preset.topP,
                                preset,
                                current.instructionTemplate,
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
            Text(
                stringResource(R.string.generation_section_template),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            InstructionTemplateChips(
                selected = settings.instructionTemplate,
                onSelect = { template -> update { it.copy(instructionTemplate = template) } },
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
    selected: InstructionTemplate,
    onSelect: (InstructionTemplate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == InstructionTemplate.Roleplay,
            onClick = { onSelect(InstructionTemplate.Roleplay) },
            label = { Text(stringResource(R.string.generation_template_roleplay)) },
        )
        FilterChip(
            selected = selected == InstructionTemplate.Direct,
            onClick = { onSelect(InstructionTemplate.Direct) },
            label = { Text(stringResource(R.string.generation_template_direct)) },
        )
        FilterChip(
            selected = selected == InstructionTemplate.StraightAnswers,
            onClick = { onSelect(InstructionTemplate.StraightAnswers) },
            label = { Text(stringResource(R.string.generation_template_straight)) },
        )
    }
}
