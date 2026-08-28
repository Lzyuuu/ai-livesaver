package io.github.lzyuuu.ailivesaver

import android.app.ActivityManager
import android.content.Context
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 「模型与引擎」页（阶段② #73 开工项 3）：manifest 驱动的本地模型清单，
 * 下载（SHA-256 校验）/删除/本地-云端引擎切换。LiteRT-LM 运行时待接（PLAN）。
 */
@Composable
internal fun ModelEngineSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val entries = remember { LocalModels.load(context) }
    var downloadPercent by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val activeEngine = remember(revision) { LocalModels.activeEngineId(context) }
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
                    "${entry.quant} · ${entry.sizeMb} MB · ${stringResource(R.string.models_engine_min_ram, entry.minRamMb)}",
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
