package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import android.text.format.Formatter
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import java.net.URI
import java.util.Locale

internal data class RuntimeSnapshot(
    val appVersion: String,
    val sdk: Int,
    val providerConfigured: Boolean,
    val providerHost: String,
    val capabilities: ProviderCapabilities,
    val worldEnabled: Boolean,
    val activity: String,
    val budgetUsed: Int,
    val budgetLimit: Int,
    val taskPaused: Boolean,
    val lastFailure: String,
    val queuedMedia: Int,
    val pendingMedia: Int,
    val failedMedia: Int,
    val localDreamQueueRunning: Boolean,
    val availableStorage: Long,
    val notificationsEnabled: Boolean,
    val localDreamStats: LocalDreamRunStats?,
)

internal object RuntimeDiagnostics {
    fun snapshot(context: Context): RuntimeSnapshot {
        val provider = ProviderStore(context).load()
        val mediaJobs = WorldStore(context).use { it.mediaJobs() }
        val notifications = WorldEngine.notificationsEnabled(context) &&
            (Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)
        return RuntimeSnapshot(
            appVersion = BuildConfig.VERSION_NAME,
            sdk = Build.VERSION.SDK_INT,
            providerConfigured = provider.isValid(),
            providerHost = runCatching { URI(provider.baseUrl).host.orEmpty() }
                .getOrDefault("")
                .ifBlank { "—" },
            capabilities = provider.capabilities,
            worldEnabled = WorldEngine.isEnabled(context),
            activity = WorldEngine.activity(context),
            budgetUsed = WorldEngine.budgetUsed(context),
            budgetLimit = WorldEngine.dailyBudget(context),
            taskPaused = WorldEngine.taskPaused(context),
            lastFailure = sanitizeFailure(WorldEngine.lastFailure(context)),
            queuedMedia = mediaJobs.size,
            pendingMedia = mediaJobs.count { it.status == "pending" },
            failedMedia = mediaJobs.count { it.status == "failed" },
            localDreamQueueRunning = LocalDreamQueue.isRunning(),
            availableStorage = StatFs(context.filesDir.path).availableBytes,
            notificationsEnabled = notifications,
            localDreamStats = LocalDreamStatsStore.load(context),
        )
    }

    fun share(context: Context, snapshot: RuntimeSnapshot) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text(snapshot))
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_diagnostics)))
    }

    internal fun text(snapshot: RuntimeSnapshot): String = buildString {
        appendLine("AI Livesaver diagnostics")
        appendLine("version=${snapshot.appVersion}")
        appendLine("sdk=${snapshot.sdk}")
        appendLine("provider_configured=${snapshot.providerConfigured}")
        appendLine("provider_host=${snapshot.providerHost}")
        appendLine("provider_capabilities=${snapshot.capabilities.supported.joinToString { it.name }}")
        appendLine("world_enabled=${snapshot.worldEnabled}")
        appendLine("world_activity=${snapshot.activity}")
        appendLine("budget=${snapshot.budgetUsed}/${snapshot.budgetLimit}")
        appendLine("task_paused=${snapshot.taskPaused}")
        appendLine("queued_media=${snapshot.queuedMedia}")
        appendLine("queued_media_pending=${snapshot.pendingMedia}")
        appendLine("queued_media_failed=${snapshot.failedMedia}")
        appendLine("local_dream_queue_running=${snapshot.localDreamQueueRunning}")
        appendLine("available_storage_bytes=${snapshot.availableStorage}")
        appendLine("notifications_enabled=${snapshot.notificationsEnabled}")
        snapshot.localDreamStats?.let { stats ->
            appendLine("local_dream_generation_ms=${stats.generationTimeMs}")
            appendLine("local_dream_first_step_ms=${stats.firstStepTimeMs ?: "unknown"}")
            appendLine("local_dream_image=${stats.width}x${stats.height}")
        } ?: appendLine("local_dream_last_run=none")
        if (snapshot.lastFailure.isNotBlank()) appendLine("last_failure=${snapshot.lastFailure}")
    }

    internal fun sanitizeFailure(providerFailure: String): String = providerFailure
        .replace(Regex("[\\r\\n]+"), " ")
        .take(160)
}

@Composable
internal fun DiagnosticsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val snapshot = remember(revision) { RuntimeDiagnostics.snapshot(context) }
    var localDreamState by remember { mutableStateOf<String?>(null) }
    var probing by remember { mutableStateOf(false) }
    val localDreamProbeOk = stringResource(R.string.local_dream_probe_ok)
    val localDreamProbeFailed = stringResource(R.string.local_dream_probe_failed)
    var previewOpen by rememberSaveable { mutableStateOf(false) }

    if (previewOpen) {
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            title = { Text(stringResource(R.string.diagnostics_preview_title)) },
            text = {
                Text(
                    RuntimeDiagnostics.text(snapshot),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        previewOpen = false
                        RuntimeDiagnostics.share(context, snapshot)
                    },
                ) {
                    Text(stringResource(R.string.share_diagnostics))
                }
            },
            dismissButton = {
                TextButton(onClick = { previewOpen = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("‹  ${stringResource(R.string.back)}") }
            Text(
                stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.diagnostics_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.runtime_status), fontWeight = FontWeight.Bold)
                    DiagnosticRow(
                        stringResource(R.string.runtime_app_version),
                        snapshot.appVersion,
                    )
                    DiagnosticRow(
                        stringResource(R.string.runtime_provider),
                        if (snapshot.providerConfigured) snapshot.providerHost
                        else stringResource(R.string.runtime_not_configured),
                    )
                    DiagnosticRow(
                        stringResource(R.string.runtime_world),
                        if (snapshot.worldEnabled) {
                            stringResource(R.string.runtime_enabled)
                        } else {
                            stringResource(R.string.runtime_disabled)
                        },
                    )
                    DiagnosticRow(
                        stringResource(R.string.runtime_budget),
                        "${snapshot.budgetUsed} / " + if (snapshot.budgetLimit == 0) {
                            stringResource(R.string.unlimited)
                        } else {
                            snapshot.budgetLimit.toString()
                        },
                    )
                    DiagnosticRow(
                        stringResource(R.string.runtime_queue),
                        stringResource(
                            R.string.runtime_queue_summary,
                            snapshot.pendingMedia,
                            snapshot.failedMedia,
                            stringResource(
                                if (snapshot.localDreamQueueRunning) {
                                    R.string.runtime_queue_running
                                } else {
                                    R.string.runtime_queue_idle
                                },
                            ),
                        ),
                    )
                    DiagnosticRow(
                        stringResource(R.string.runtime_storage),
                        Formatter.formatFileSize(context, snapshot.availableStorage),
                    )
                    DiagnosticRow(
                        stringResource(R.string.runtime_notifications),
                        if (snapshot.notificationsEnabled) {
                            stringResource(R.string.runtime_enabled)
                        } else {
                            stringResource(R.string.runtime_disabled)
                        },
                    )
                    if (snapshot.taskPaused) {
                        Text(
                            stringResource(R.string.runtime_paused, snapshot.lastFailure),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.runtime_capabilities), fontWeight = FontWeight.Bold)
                    ProviderCapability.entries.forEach { capability ->
                        DiagnosticRow(
                            stringResource(capability.labelRes),
                            when {
                                capability in snapshot.capabilities.supported ->
                                    stringResource(R.string.capability_passed)
                                snapshot.capabilities.failures.containsKey(capability) ->
                                    stringResource(R.string.capability_failed, "—")
                                else -> stringResource(R.string.capability_unverified)
                            },
                        )
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.runtime_local_dream), fontWeight = FontWeight.Bold)
                    val stats = snapshot.localDreamStats
                    val firstStep = stats?.firstStepTimeMs?.let { "$it ms" }
                        ?: stringResource(R.string.runtime_local_dream_first_step_unknown)
                    DiagnosticRow(
                        stringResource(R.string.runtime_local_dream_last_run),
                        stats?.let {
                            stringResource(
                                R.string.runtime_local_dream_stats,
                                it.generationTimeMs,
                                firstStep,
                                it.width,
                                it.height,
                            )
                        } ?: stringResource(R.string.runtime_local_dream_no_run),
                    )
                    Text(
                        stringResource(R.string.runtime_local_dream_stats_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    localDreamState?.let { Text(it) }
                    Button(
                        onClick = {
                            probing = true
                            localDreamState = null
                            LocalDreamClient.probe { result ->
                                probing = false
                                localDreamState = result.fold(
                                    onSuccess = { value ->
                                        String.format(Locale.getDefault(), localDreamProbeOk, value)
                                    },
                                    onFailure = { localDreamProbeFailed },
                                )
                                revision++
                            }
                        },
                        enabled = !probing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (probing) {
                                stringResource(R.string.checking_local_dream)
                            } else {
                                stringResource(R.string.check_local_dream)
                            },
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = { previewOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.preview_diagnostics))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.diagnostics_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
