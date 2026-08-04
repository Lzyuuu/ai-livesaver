package io.github.lzyuuu.ailivesaver

import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
