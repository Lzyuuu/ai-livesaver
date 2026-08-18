package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.UUID

private const val MAX_AVATAR_BYTES = 10L * 1024 * 1024

internal fun avatarImportAllowed(totalBytes: Long): Boolean =
    totalBytes in 1..MAX_AVATAR_BYTES

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
    val avatarTooLarge = stringResource(R.string.identity_avatar_too_large)
    val avatarStorageLow = stringResource(R.string.identity_avatar_storage_low)
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = File(
            File(context.filesDir, "media").apply { mkdirs() },
            "user-avatar-${UUID.randomUUID()}.jpg",
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
            status = it.message ?: avatarInvalid
        }
    }

    SettingsList(
        contentPadding = contentPadding,
        modifier = Modifier.testTag("identity-screen"),
    ) {
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
private fun SettingsList(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
    ScreenBackButton(onBack)
    Text(
        stringResource(title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        stringResource(summary),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
