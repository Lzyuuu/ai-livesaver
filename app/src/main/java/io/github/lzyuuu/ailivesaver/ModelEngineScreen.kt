package io.github.lzyuuu.ailivesaver

import android.app.ActivityManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp

/**
 * 「模型与引擎」页：manifest 驱动的本地模型清单，下载（SHA-256 校验）/删除/本地-云端引擎切换。
 * LiteRT-LM 运行时已编译接入；真实模型加载与硬件 delegate 验证受设备和模型条件限制。
 */
@Composable
internal fun ModelEngineSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val entries = remember(revision) { LocalModels.load(context) }
    var downloadPercent by remember { mutableStateOf<String?>(null) }
    var internalsRevision by remember { mutableIntStateOf(0) }
    val cores = remember { Runtime.getRuntime().availableProcessors() }
    val internals = remember(internalsRevision) { EngineInternalsStore.read(context) }
    val resolvedPrefill = resolveThreads(internals.prefillThreads, prefillAutoThreads(cores), cores)
    val resolvedDecode = resolveThreads(internals.decodeThreads, decodeAutoThreads(cores), cores)
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importMessage = context.getString(R.string.models_engine_importing)
        LocalModels.importFromUri(context, uri) { result ->
            importMessage = result.fold(
                onSuccess = { null },
                onFailure = { it.message ?: context.getString(R.string.models_engine_import_failed) },
            )
            revision++
        }
    }
    val activeEngine = remember(revision) { LocalModels.activeEngineId(context) }
    var backendRevision by remember { mutableIntStateOf(0) }
    val backend = remember(backendRevision) { selectedInferenceBackend(context) }
    // 能力检测 + 选择/回退（纯逻辑，见 InferenceBackend.kt）。
    val supportedBackends = remember { LlamaNative.supportedBackends }
    val effective = remember(backendRevision) { effectiveInferenceBackend(backend, supportedBackends) }
    val deviceRamMb = remember {
        val info = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let {
            it.getMemoryInfo(info)
        }
        (info.totalMem / (1L shl 20)).toInt()
    }

    SettingsPageScaffold(R.string.settings_models_engine_title, "models-engine-screen", contentPadding, onBack) {
        item {
            Text(
                stringResource(R.string.settings_models_engine_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                Text(
                    stringResource(R.string.models_engine_ram_line, deviceRamMb),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.models_engine_local_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.models_engine_import))
                }
                importMessage?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(entries.size) { index ->
            val entry = entries[index]
            val downloaded = remember(revision, entry.id) { LocalModels.isDownloaded(context, entry) }
            val isActive = activeEngine == entry.id
            val ramShort = deviceRamMb < entry.minRamMb
            Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PageCard {
                Text(
                    if (entry.minRamMb > 0) {
                        "${entry.quant} · ${entry.sizeMb} MB · ${stringResource(R.string.models_engine_min_ram, entry.minRamMb)}"
                    } else {
                        "${entry.quant} · ${entry.sizeMb} MB"
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    entry.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ramShort) {
                    Text(
                        stringResource(R.string.models_engine_ram_warning),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isActive,
                            onClick = {
                                LocalModels.setActiveEngine(context, if (isActive) null else entry.id)
                                revision++
                            },
                            enabled = downloaded,
                            label = { Text(stringResource(if (isActive) R.string.models_engine_active else R.string.models_engine_set_active)) },
                        )
                    }
                    when {
                        downloaded -> TextButton(onClick = {
                            LocalModels.remove(context, entry)
                            revision++
                        }) { Text(stringResource(R.string.cleanup_remove)) }
                        downloadPercent != null -> Text(
                            downloadPercent!!,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> Button(
                            onClick = {
                                downloadPercent = "0%"
                                LocalModels.download(context, entry) { result ->
                                    downloadPercent = result.fold(
                                        onSuccess = { null },
                                        onFailure = { "下载失败" },
                                    )
                                    revision++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(),
                        ) { Text(stringResource(R.string.models_engine_download)) }
                    }
                }
            }
        }
        item {
            PageCard {
                Text(stringResource(R.string.models_engine_runs_on), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.models_engine_backend_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InferenceBackend.entries.forEach { option ->
                        FilterChip(
                            selected = backend == option,
                            onClick = {
                                setInferenceBackend(context, option)
                                backendRevision++
                            },
                            // 无 native backend 的选项禁用（本机构建仅 CPU），避免选中后伪造加速。
                            enabled = option in supportedBackends,
                            label = { Text(option.label) },
                        )
                    }
                }
                Text(
                    when {
                        effective == null -> "本地推理不可用：llama 运行时未编入本机"
                        effective != backend -> "所选 ${backend.label} 后端本机不可用，已回退 ${effective.label}（本机构建仅支持 CPU）"
                        else -> "当前生效：${effective.label}"
                    },
                    color = when {
                        effective == null -> MaterialTheme.colorScheme.error
                        effective != backend -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        item {
            // 上下文（参考 ref-30c）：对话长度步进，下次加载模型时生效。
            PageCard {
                Text(stringResource(R.string.models_engine_context_title), fontWeight = FontWeight.Bold)
                StepperRow(
                    label = stringResource(R.string.models_engine_context_length),
                    value = stringResource(R.string.models_engine_context_value, internals.contextTokens),
                    onDec = {
                        EngineInternalsStore.write(
                            context,
                            internals.copy(
                                contextTokens = internals.contextTokens - EngineInternals.CONTEXT_STEP,
                            ).normalized(),
                        )
                        internalsRevision++
                    },
                    onInc = {
                        EngineInternalsStore.write(
                            context,
                            internals.copy(
                                contextTokens = internals.contextTokens + EngineInternals.CONTEXT_STEP,
                            ).normalized(),
                        )
                        internalsRevision++
                    },
                    decEnabled = internals.contextTokens > EngineInternals.MIN_CONTEXT_TOKENS,
                    incEnabled = internals.contextTokens < EngineInternals.MAX_CONTEXT_TOKENS,
                    tagPrefix = "engine-context",
                )
                Text(
                    stringResource(R.string.models_engine_context_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            // 高级 · 引擎内部（参考 ref-30c）：预填充/解码线程、批大小。
            PageCard {
                Text(stringResource(R.string.models_engine_internals_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.models_engine_internals_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StepperRow(
                    label = stringResource(R.string.models_engine_prefill_threads),
                    value = stringResource(
                        if (internals.prefillThreads == 0) R.string.models_engine_threads_auto else R.string.models_engine_threads_count,
                        resolvedPrefill,
                    ),
                    onDec = {
                        EngineInternalsStore.write(
                            context,
                            internals.copy(prefillThreads = (internals.prefillThreads - 1).coerceAtLeast(0)).normalized(),
                        )
                        internalsRevision++
                    },
                    onInc = {
                        EngineInternalsStore.write(
                            context,
                            internals.copy(prefillThreads = internals.prefillThreads + 1).normalized(),
                        )
                        internalsRevision++
                    },
                    decEnabled = internals.prefillThreads > 0,
                    incEnabled = internals.prefillThreads < EngineInternals.MAX_THREADS,
                    tagPrefix = "engine-prefill",
                )
                Text(
                    stringResource(R.string.models_engine_prefill_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StepperRow(
                    label = stringResource(R.string.models_engine_decode_threads),
                    value = stringResource(
                        if (internals.decodeThreads == 0) R.string.models_engine_threads_auto else R.string.models_engine_threads_count,
                        resolvedDecode,
                    ),
                    onDec = {
                        EngineInternalsStore.write(
                            context,
                            internals.copy(decodeThreads = (internals.decodeThreads - 1).coerceAtLeast(0)).normalized(),
                        )
                        internalsRevision++
                    },
                    onInc = {
                        EngineInternalsStore.write(
                            context,
                            internals.copy(decodeThreads = internals.decodeThreads + 1).normalized(),
                        )
                        internalsRevision++
                    },
                    decEnabled = internals.decodeThreads > 0,
                    incEnabled = internals.decodeThreads < EngineInternals.MAX_THREADS,
                    tagPrefix = "engine-decode",
                )
                Text(
                    stringResource(R.string.models_engine_decode_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StepperRow(
                    label = stringResource(R.string.models_engine_batch),
                    value = stringResource(R.string.models_engine_batch_value, internals.batchSize),
                    onDec = {
                        EngineInternalsStore.write(
                            context,
                            internals.copy(batchSize = internals.batchSize - EngineInternals.BATCH_STEP).normalized(),
                        )
                        internalsRevision++
                    },
                    onInc = {
                        EngineInternalsStore.write(
                            context,
                            internals.copy(batchSize = internals.batchSize + EngineInternals.BATCH_STEP).normalized(),
                        )
                        internalsRevision++
                    },
                    decEnabled = internals.batchSize > EngineInternals.MIN_BATCH,
                    incEnabled = internals.batchSize < EngineInternals.MAX_BATCH,
                    tagPrefix = "engine-batch",
                )
                Text(
                    stringResource(R.string.models_engine_batch_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            PageCard {
                Text(stringResource(R.string.models_engine_cloud_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.models_engine_cloud_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = activeEngine == null,
                        onClick = {
                            LocalModels.setActiveEngine(context, null)
                            revision++
                        },
                        label = { Text(stringResource(R.string.models_engine_cloud_active)) },
                    )
                }
            }
        }
    }
}

/** 参考样式步进行（ref-30c）：标签 + 值 + −/+ 按钮。 */
@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDec: () -> Unit,
    onInc: () -> Unit,
    decEnabled: Boolean,
    incEnabled: Boolean,
    tagPrefix: String,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(value, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDec, enabled = decEnabled, modifier = Modifier.testTag("$tagPrefix-dec")) {
                Text("−")
            }
            TextButton(onClick = onInc, enabled = incEnabled, modifier = Modifier.testTag("$tagPrefix-inc")) {
                Text("+")
            }
        }
    }
}
