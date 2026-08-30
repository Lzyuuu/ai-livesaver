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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        voiceModelsSection()
    }
}

/**
 * SO-11 存储：文件式浏览界面（对齐参考 ref-80）。
 * 顶部为返回 + 标题 + 面包屑路径；摘要卡保留数据库/媒体/应用文件/可用空间四行；
 * 下方为应用沙盒内的只读目录浏览（storage-browser + storage-entry-* 结构标签）。
 * 所有文件枚举经 [StorageBrowser] 受限快照并在 IO 调度器异步加载，主线程不做 walk。
 */
@Composable
internal fun StorageScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val sandboxRoot = remember { context.filesDir }
    var currentPath by rememberSaveable { mutableStateOf("") }
    val summary by produceState<StorageSummarySnapshot?>(initialValue = null, revision) {
        value = withContext(Dispatchers.IO) {
            val databaseBytes = File(context.getDatabasePath("world.db").path).length()
            val appBytes = StorageBrowser.boundedSizeBytes(sandboxRoot)
            StorageSummarySnapshot(
                databaseBytes = databaseBytes,
                mediaBytes = store.mediaStorageBytes(),
                appFilesBytes = appBytes.totalBytes,
                appFilesTruncated = appBytes.truncated,
                freeBytes = StatFs(sandboxRoot.path).availableBytes,
            )
        }
    }
    val listing by produceState<StorageDirectoryListing?>(initialValue = null, currentPath, revision) {
        value = withContext(Dispatchers.IO) {
            StorageBrowser.listDirectory(sandboxRoot, currentPath)
        }
    }
    // 越界/失效路径（例如目录被清理）自动回退到根。
    LaunchedEffect(listing) {
        val loaded = listing
        if (loaded != null && currentPath.isNotEmpty() && loaded.relativePath != currentPath) {
            currentPath = loaded.relativePath
        }
    }

    fun navigateUp() {
        if (currentPath.isEmpty()) onBack() else currentPath = StorageBrowser.parentPath(currentPath)
    }

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
            ScreenBackButton(::navigateUp)
            Text(stringResource(R.string.storage_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                "/" + currentPath,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("storage-path"),
            )
            Text(stringResource(R.string.storage_summary), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth().testTag("storage-summary")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val snapshot = summary
                    StorageRow(
                        stringResource(R.string.storage_world_database),
                        snapshot?.databaseBytes,
                        context,
                    )
                    StorageRow(stringResource(R.string.storage_media), snapshot?.mediaBytes, context)
                    StorageRow(
                        stringResource(R.string.storage_app_files),
                        snapshot?.appFilesBytes,
                        context,
                        truncated = snapshot?.appFilesTruncated == true,
                    )
                    StorageRow(stringResource(R.string.storage_available), snapshot?.freeBytes, context)
                }
            }
        }
        item {
            Text(
                stringResource(R.string.storage_browser_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        val current = listing
        if (current == null) {
            item {
                Text(
                    stringResource(R.string.storage_browser_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("storage-browser"),
                )
            }
        } else if (current.entries.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.storage_browser_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("storage-browser"),
                )
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth().testTag("storage-browser")) {
                    Column {
                        current.entries.forEachIndexed { index, entry ->
                            StorageEntryRow(
                                entry = entry,
                                context = context,
                                onOpen = if (entry.isDirectory) {
                                    { currentPath = entry.relativePath }
                                } else {
                                    null
                                },
                            )
                            if (index < current.entries.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                )
                            }
                        }
                    }
                }
            }
            if (current.truncated) {
                item {
                    Text(
                        stringResource(
                            R.string.storage_browser_truncated,
                            StorageBrowser.MAX_ENTRIES_PER_DIRECTORY,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("storage-browser-truncated"),
                    )
                }
            }
        }
    }
}

private data class StorageSummarySnapshot(
    val databaseBytes: Long,
    val mediaBytes: Long,
    val appFilesBytes: Long,
    val appFilesTruncated: Boolean,
    val freeBytes: Long,
)

@Composable
private fun StorageEntryRow(
    entry: StorageEntry,
    context: android.content.Context,
    onOpen: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("storage-entry-${entry.relativePath}")
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (entry.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontWeight = FontWeight.Medium)
            Text(
                if (entry.isDirectory) {
                    stringResource(R.string.storage_entry_items, entry.childCount) +
                        if (entry.childrenTruncated) "+" else ""
                } else {
                    Formatter.formatFileSize(context, entry.sizeBytes)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (onOpen != null) {
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StorageRow(
    label: String,
    bytes: Long?,
    context: android.content.Context,
    truncated: Boolean = false,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(
            if (bytes == null) {
                stringResource(R.string.storage_loading_placeholder)
            } else {
                Formatter.formatFileSize(context, bytes) + if (truncated) "+" else ""
            },
            fontWeight = FontWeight.Bold,
        )
    }
}

private val GENERATION_MAX_TOKEN_STEPS = listOf(128, 256, 512, 1024, 2048, 4096, 8192)
private val GENERATION_PENALTY_WINDOW_STEPS = listOf(0, 32, 64, 128, 256, 512, 1024)

private fun nextDiscrete(current: Int, steps: List<Int>, direction: Int): Int =
    if (direction > 0) {
        steps.firstOrNull { it > current } ?: steps.last()
    } else {
        steps.lastOrNull { it < current } ?: steps.first()
    }

private fun roundToStep(value: Float, step: Float): Float =
    Math.round(value / step) * step

@Composable
internal fun GenerationSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProviderStore(context) }
    var settings by remember { mutableStateOf(store.loadDefaultGeneration()) }
    var expertExpanded by rememberSaveable { mutableStateOf(false) }

    fun persist(next: GenerationSettings) {
        settings = next.normalized()
        store.saveDefaultGeneration(settings)
    }

    fun persistOwned(transform: (GenerationSettings) -> GenerationSettings) {
        persist(transform(settings).copy(preset = GenerationPreset.Custom))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("generation-settings"),
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
            TextButton(
                onClick = { persist(resetGenerationSampling(settings)) },
                modifier = Modifier.testTag("generation-reset"),
            ) {
                Text(stringResource(R.string.generation_reset))
            }
        }
        item {
            Text(
                stringResource(R.string.generation_style_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.generation_preset), fontWeight = FontWeight.Bold)
                    GenerationPresetChips(
                        selected = settings.preset,
                        onPick = { preset -> persist(applyNamedGenerationPreset(settings, preset)) },
                    )
                    Text(
                        stringResource(
                            when (settings.preset) {
                                GenerationPreset.Precise -> R.string.generation_preset_precise_blurb
                                GenerationPreset.Balanced -> R.string.generation_preset_balanced_blurb
                                GenerationPreset.Creative -> R.string.generation_preset_creative_blurb
                                GenerationPreset.Custom -> R.string.generation_preset_custom_blurb
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(R.string.generation_preset_info),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.generation_replies_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GenerationSliderRow(
                        label = stringResource(R.string.generation_temperature),
                        valueLabel = "%.2f".format(settings.temperature),
                        value = settings.temperature,
                        valueRange = 0f..2f,
                        step = 0.05f,
                        note = stringResource(R.string.generation_temperature_note),
                        testTag = "generation-temperature",
                        onChange = { value -> persistOwned { it.copy(temperature = value) } },
                    )
                    GenerationStepperRow(
                        label = stringResource(R.string.generation_max_reply),
                        valueLabel = stringResource(R.string.generation_max_reply_format, settings.maxTokens),
                        note = stringResource(R.string.generation_max_reply_note),
                        testTag = "generation-max-tokens",
                        onDecrement = {
                            persistOwned { it.copy(maxTokens = nextDiscrete(it.maxTokens, GENERATION_MAX_TOKEN_STEPS, -1)) }
                        },
                        onIncrement = {
                            persistOwned { it.copy(maxTokens = nextDiscrete(it.maxTokens, GENERATION_MAX_TOKEN_STEPS, 1)) }
                        },
                    )
                    GenerationSliderRow(
                        label = stringResource(R.string.generation_top_p),
                        valueLabel = "%.2f".format(settings.topP),
                        value = settings.topP,
                        valueRange = 0f..1f,
                        step = 0.01f,
                        note = stringResource(R.string.generation_top_p_note),
                        testTag = "generation-top-p",
                        onChange = { value -> persistOwned { it.copy(topP = value) } },
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.generation_advanced_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generation-expert-toggle")
                            .clickable { expertExpanded = !expertExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.generation_expert_sampling), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.generation_expert_sampling_subtitle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(if (expertExpanded) "∧" else "∨")
                    }
                    if (expertExpanded) {
                        val expert = settings.expertSampling
                        val off = stringResource(R.string.generation_off)
                        GenerationSliderRow(
                            label = stringResource(R.string.generation_min_p),
                            valueLabel = if (expert.minP <= 0f) off else "%.2f".format(expert.minP),
                            value = expert.minP,
                            valueRange = 0f..0.5f,
                            step = 0.01f,
                            note = stringResource(R.string.generation_min_p_note),
                            testTag = "generation-min-p",
                            onChange = { value ->
                                persistOwned { it.copy(expertSampling = it.expertSampling.copy(minP = value)) }
                            },
                        )
                        GenerationSliderRow(
                            label = stringResource(R.string.generation_repetition_penalty),
                            valueLabel = if (expert.repetitionPenalty <= 1f) off else "%.2f".format(expert.repetitionPenalty),
                            value = expert.repetitionPenalty,
                            valueRange = 1f..1.5f,
                            step = 0.01f,
                            note = stringResource(R.string.generation_repetition_penalty_note),
                            testTag = "generation-repetition-penalty",
                            onChange = { value ->
                                persistOwned { it.copy(expertSampling = it.expertSampling.copy(repetitionPenalty = value)) }
                            },
                        )
                        GenerationStepperRow(
                            label = stringResource(R.string.generation_penalty_window),
                            valueLabel = if (expert.penaltyWindow == 0) {
                                off
                            } else {
                                stringResource(R.string.generation_last_tokens_format, expert.penaltyWindow)
                            },
                            note = stringResource(R.string.generation_penalty_window_note),
                            testTag = "generation-penalty-window",
                            onDecrement = {
                                persist(
                                    settings.copy(
                                        expertSampling = settings.expertSampling.copy(
                                            penaltyWindow = nextDiscrete(
                                                settings.expertSampling.penaltyWindow,
                                                GENERATION_PENALTY_WINDOW_STEPS,
                                                -1,
                                            ),
                                        ),
                                    ),
                                )
                            },
                            onIncrement = {
                                persist(
                                    settings.copy(
                                        expertSampling = settings.expertSampling.copy(
                                            penaltyWindow = nextDiscrete(
                                                settings.expertSampling.penaltyWindow,
                                                GENERATION_PENALTY_WINDOW_STEPS,
                                                1,
                                            ),
                                        ),
                                    ),
                                )
                            },
                        )
                        GenerationSliderRow(
                            label = stringResource(R.string.generation_xtc_surprise),
                            valueLabel = if (expert.xtcSurprise <= 0f) {
                                off
                            } else {
                                stringResource(
                                    R.string.generation_percent_words_format,
                                    Math.round(expert.xtcSurprise * 100f),
                                )
                            },
                            value = expert.xtcSurprise,
                            valueRange = 0f..1f,
                            step = 0.05f,
                            note = stringResource(R.string.generation_xtc_surprise_note),
                            testTag = "generation-xtc-surprise",
                            onChange = { value ->
                                persistOwned { it.copy(expertSampling = it.expertSampling.copy(xtcSurprise = value)) }
                            },
                        )
                        GenerationSliderRow(
                            label = stringResource(R.string.generation_xtc_floor),
                            valueLabel = "%.2f".format(expert.xtcFloor),
                            value = expert.xtcFloor,
                            valueRange = 0.05f..0.5f,
                            step = 0.01f,
                            note = stringResource(R.string.generation_xtc_floor_note),
                            testTag = "generation-xtc-floor",
                            onChange = { value ->
                                persist(settings.copy(expertSampling = settings.expertSampling.copy(xtcFloor = value)))
                            },
                        )
                        GenerationSliderRow(
                            label = stringResource(R.string.generation_dry_loop_breaker),
                            valueLabel = if (expert.dryLoopBreaker <= 0f) off else "%.2f".format(expert.dryLoopBreaker),
                            value = expert.dryLoopBreaker,
                            valueRange = 0f..2f,
                            step = 0.05f,
                            note = stringResource(R.string.generation_dry_loop_breaker_note),
                            testTag = "generation-dry-loop",
                            onChange = { value ->
                                persist(settings.copy(expertSampling = settings.expertSampling.copy(dryLoopBreaker = value)))
                            },
                        )
                        GenerationSliderRow(
                            label = stringResource(R.string.generation_dry_steepness),
                            valueLabel = "%.2f".format(expert.drySteepness),
                            value = expert.drySteepness,
                            valueRange = 1f..4f,
                            step = 0.05f,
                            note = stringResource(R.string.generation_dry_steepness_note),
                            testTag = "generation-dry-steepness",
                            onChange = { value ->
                                persist(settings.copy(expertSampling = settings.expertSampling.copy(drySteepness = value)))
                            },
                        )
                        GenerationSliderRow(
                            label = stringResource(R.string.generation_dry_allowed_repeat),
                            valueLabel = stringResource(R.string.generation_max_reply_format, expert.dryAllowedRepeat),
                            value = expert.dryAllowedRepeat.toFloat(),
                            valueRange = 0f..10f,
                            step = 1f,
                            note = stringResource(R.string.generation_dry_allowed_repeat_note),
                            testTag = "generation-dry-allowed",
                            onChange = { value ->
                                persist(settings.copy(expertSampling = settings.expertSampling.copy(dryAllowedRepeat = Math.round(value))))
                            },
                        )
                        GenerationSliderRow(
                            label = stringResource(R.string.generation_dynamic_temperature),
                            valueLabel = if (expert.dynamicTemperature <= 0f) {
                                off
                            } else {
                                stringResource(R.string.generation_dynatemp_format, "%.2f".format(expert.dynamicTemperature))
                            },
                            value = expert.dynamicTemperature,
                            valueRange = 0f..1f,
                            step = 0.05f,
                            note = stringResource(R.string.generation_dynamic_temperature_note),
                            testTag = "generation-dynatemp",
                            onChange = { value ->
                                persist(settings.copy(expertSampling = settings.expertSampling.copy(dynamicTemperature = value)))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    note: String,
    testTag: String,
    onChange: (Float) -> Unit,
) {
    val steps = ((valueRange.endInclusive - valueRange.start) / step).toInt() - 1
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(valueLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = { onChange(roundToStep(it, step).coerceIn(valueRange.start, valueRange.endInclusive)) },
            valueRange = valueRange,
            steps = steps.coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        )
        Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GenerationStepperRow(
    label: String,
    valueLabel: String,
    note: String,
    testTag: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(valueLabel, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onDecrement, modifier = Modifier.testTag("$testTag-dec")) { Text("−") }
            TextButton(onClick = onIncrement, modifier = Modifier.testTag("$testTag-inc")) { Text("+") }
        }
        Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
                                saveAsName = ""
                                showSaveAs = true
                            },
                            modifier = Modifier.testTag("instruction-save-as-new"),
                        ) {
                            Text(stringResource(R.string.instruction_save_as_new))
                        }
                        TextButton(
                            onClick = { persist(restoreFactoryInstructionLibrary(settings)) },
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
                    label = { Text(stringResource(R.string.instruction_name_field)) },
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
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAs = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
