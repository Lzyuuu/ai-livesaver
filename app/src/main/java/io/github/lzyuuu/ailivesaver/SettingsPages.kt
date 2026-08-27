package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import java.io.File
import java.util.UUID

/** 常规/记忆/清理/模型与引擎 页面（批次 A，一比一对齐参考 V4.51）。 */

internal const val UI_PREFS = "ui_prefs"
internal const val PREF_FONT_SCALE = "font_scale"
internal const val PREF_LANGUAGE = "language"
internal const val PREF_AUTO_POST = "auto_post"
internal const val PREF_AUTO_POST_INTERVAL = "auto_post_interval"
internal const val PREF_MEMORY_LOCAL_AUTO = "memory_local_auto"
internal const val PREF_MEMORY_CLOUD_AUTO = "memory_cloud_auto"
internal const val PREF_MEMORY_RECALL_LEVEL = "memory_recall_level"
internal const val PREF_HISTORY_WINDOW_MESSAGES = "history_window_messages"
internal const val DEFAULT_HISTORY_WINDOW_MESSAGES = 48
internal const val MIN_HISTORY_WINDOW_MESSAGES = 8
internal const val MAX_HISTORY_WINDOW_MESSAGES = 512

/** 记忆页「聊天历史窗口」：限制流式聊天每次携带的最近消息条数（参考语义）。 */
internal fun readHistoryWindowMessages(context: android.content.Context): Int =
    context.getSharedPreferences(UI_PREFS, android.content.Context.MODE_PRIVATE)
        .getInt(PREF_HISTORY_WINDOW_MESSAGES, DEFAULT_HISTORY_WINDOW_MESSAGES)

/** 文本大小档位 → fontScale 倍率（参考：小/默认/大/特大）。 */
val TEXT_SIZE_SCALES = linkedMapOf("small" to 0.85f, "default" to 1.0f, "large" to 1.15f, "xlarge" to 1.3f)

@Composable
private fun SettingsPageScaffold(
    titleRes: Int,
    testTag: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    pageContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag),
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
                stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        pageContent()
    }
}

@Composable
private fun PageCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun GeneralSettingsScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(UI_PREFS, android.content.Context.MODE_PRIVATE) }
    val identity = remember { store.identity() }
    var name by rememberSaveable { mutableStateOf(identity.name.takeUnless { it == "你" }.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(identity.username) }
    var bio by rememberSaveable { mutableStateOf(identity.bio) }
    var avatarPath by rememberSaveable { mutableStateOf(identity.avatarPath) }
    var profileStatus by remember { mutableStateOf<String?>(null) }
    val savedText = stringResource(R.string.identity_saved)
    val invalidAvatar = stringResource(R.string.identity_avatar_invalid)
    val tooLargeAvatar = stringResource(R.string.identity_avatar_too_large)
    val lowStorage = stringResource(R.string.identity_avatar_storage_low)
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val target = File(
            File(context.filesDir, "media").apply { mkdirs() },
            "user-avatar-${UUID.randomUUID()}.jpg",
        )
        runCatching {
            check(storageAllowsGeneration(StatFs(context.filesDir.path).availableBytes)) { lowStorage }
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    var total = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(avatarImportAllowed(total)) { tooLargeAvatar }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error(invalidAvatar)
            check(BitmapFactory.decodeFile(target.path) != null) { invalidAvatar }
            avatarPath = target.path
        }.onFailure {
            target.delete()
            profileStatus = it.message ?: invalidAvatar
        }
    }

    var fontScale by rememberSaveable { mutableStateOf(prefs.getString(PREF_FONT_SCALE, "default") ?: "default") }
    var language by rememberSaveable { mutableStateOf(prefs.getString(PREF_LANGUAGE, "system") ?: "system") }
    var autoPost by rememberSaveable { mutableStateOf(prefs.getBoolean(PREF_AUTO_POST, false)) }
    var autoPostInterval by rememberSaveable { mutableStateOf(prefs.getInt(PREF_AUTO_POST_INTERVAL, 240).toString()) }

    SettingsPageScaffold(R.string.settings_general_title, "general-settings-screen", contentPadding, onBack) {
        item {
            Text(
                stringResource(R.string.general_profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                Text(
                    stringResource(R.string.general_profile_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(name.trim().ifBlank { "你" }.take(1).uppercase(), 88.dp, avatarPath)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            avatarPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }) { Text(stringResource(R.string.general_choose_photo)) }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.general_profile_name_label)) },
                    supportingText = { Text(stringResource(R.string.general_profile_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.general_profile_username_label)) },
                    supportingText = { Text(stringResource(R.string.general_profile_username_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(stringResource(R.string.general_bio_label)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        store.updateIdentity(name.ifBlank { "你" }, identity.addressPreference, bio, avatarPath, username)
                        onChanged()
                        profileStatus = savedText
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.save)) }
                profileStatus?.let { StatusCard(it) }
            }
        }
        item {
            Text(
                stringResource(R.string.general_text_size_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                Text(
                    stringResource(R.string.general_text_size_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TEXT_SIZE_SCALES.forEach { (key, _) ->
                        FilterChip(
                            selected = fontScale == key,
                            onClick = {
                                fontScale = key
                                prefs.edit().putString(PREF_FONT_SCALE, key).apply()
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (key) {
                                            "small" -> R.string.general_text_size_small
                                            "large" -> R.string.general_text_size_large
                                            "xlarge" -> R.string.general_text_size_xlarge
                                            else -> R.string.general_text_size_default
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.general_text_size_preview),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * (TEXT_SIZE_SCALES[fontScale] ?: 1f)),
                )
            }
        }
        item {
            Text(
                stringResource(R.string.general_language_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                Text(
                    stringResource(R.string.general_language_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = language == "system",
                        onClick = {
                            language = "system"
                            prefs.edit().putString(PREF_LANGUAGE, "system").apply()
                        },
                        label = { Text(stringResource(R.string.general_language_system)) },
                    )
                    FilterChip(
                        selected = language == "zh",
                        onClick = {
                            language = "zh"
                            prefs.edit().putString(PREF_LANGUAGE, "zh").apply()
                        },
                        label = { Text(stringResource(R.string.general_language_chinese)) },
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.general_social_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                Text(
                    stringResource(R.string.general_social_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.general_social_autopost))
                    Switch(
                        checked = autoPost,
                        onCheckedChange = {
                            autoPost = it
                            prefs.edit().putBoolean(PREF_AUTO_POST, it).apply()
                        },
                    )
                }
                if (autoPost) {
                    OutlinedTextField(
                        value = autoPostInterval,
                        onValueChange = { value -> autoPostInterval = value.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.general_social_interval_label)) },
                        suffix = { Text(stringResource(R.string.general_social_interval_suffix)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.general_social_interval_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        val minutes = autoPostInterval.toIntOrNull()?.coerceAtLeast(1) ?: 240
                        prefs.edit().putInt(PREF_AUTO_POST_INTERVAL, minutes).apply()
                        autoPostInterval = minutes.toString()
                    }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

@Composable
internal fun MemorySettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(UI_PREFS, android.content.Context.MODE_PRIVATE) }
    // 参考语义：聊天历史窗口按消息条数计（默认 48 条，±8 步进）。
    var historyWindow by remember { mutableIntStateOf(prefs.getInt(PREF_HISTORY_WINDOW_MESSAGES, DEFAULT_HISTORY_WINDOW_MESSAGES)) }
    var localAuto by rememberSaveable { mutableStateOf(prefs.getBoolean(PREF_MEMORY_LOCAL_AUTO, true)) }
    var cloudAuto by rememberSaveable { mutableStateOf(prefs.getBoolean(PREF_MEMORY_CLOUD_AUTO, true)) }
    var recallLevel by rememberSaveable { mutableStateOf(prefs.getString(PREF_MEMORY_RECALL_LEVEL, "balanced") ?: "balanced") }

    fun persistHistoryWindow(value: Int) {
        val clamped = value.coerceIn(MIN_HISTORY_WINDOW_MESSAGES, MAX_HISTORY_WINDOW_MESSAGES)
        historyWindow = clamped
        prefs.edit().putInt(PREF_HISTORY_WINDOW_MESSAGES, clamped).apply()
    }

    SettingsPageScaffold(R.string.settings_memory_title, "memory-settings-screen", contentPadding, onBack) {
        item {
            Text(
                stringResource(R.string.memory_recall_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.memory_history_window_title),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.memory_history_window_value, historyWindow),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { persistHistoryWindow(historyWindow - 8) },
                            modifier = Modifier.size(52.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("−") }
                        Button(
                            onClick = { persistHistoryWindow(historyWindow + 8) },
                            modifier = Modifier.size(52.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("+") }
                    }
                }
                Text(
                    stringResource(R.string.memory_history_window_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.memory_write_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.memory_local_auto_title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = localAuto,
                        onCheckedChange = {
                            localAuto = it
                            prefs.edit().putBoolean(PREF_MEMORY_LOCAL_AUTO, it).apply()
                        },
                    )
                }
                Text(
                    stringResource(R.string.memory_local_auto_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.memory_cloud_auto_title), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(
                        checked = cloudAuto,
                        onCheckedChange = {
                            cloudAuto = it
                            prefs.edit().putBoolean(PREF_MEMORY_CLOUD_AUTO, it).apply()
                        },
                    )
                }
                Text(
                    stringResource(R.string.memory_cloud_auto_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PageCard {
                Text(stringResource(R.string.memory_recall_tune_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.memory_recall_tune_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("low", "balanced", "rich").forEach { level ->
                        FilterChip(
                            selected = recallLevel == level,
                            onClick = {
                                recallLevel = level
                                prefs.edit().putString(PREF_MEMORY_RECALL_LEVEL, level).apply()
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (level) {
                                            "low" -> R.string.generation_style_precise
                                            "rich" -> R.string.generation_style_creative
                                            else -> R.string.generation_style_balanced
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CleanupSettingsScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var cacheBytes by remember { mutableStateOf(directoryBytes(context.cacheDir)) }
    var orphanRows by remember { mutableIntStateOf(store.countOrphanMessages()) }
    var driedMemories by remember { mutableIntStateOf(store.countDriedMemories()) }
    var duplicatePosts by remember { mutableIntStateOf(store.countDuplicatePosts()) }
    var lastRemoved by remember { mutableStateOf<String?>(null) }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1 shl 20 -> "${bytes / (1 shl 20)} MB"
        bytes >= 1 shl 10 -> "${bytes / (1 shl 10)} kB"
        else -> "$bytes B"
    }

    SettingsPageScaffold(R.string.settings_cleanup_title, "cleanup-settings-screen", contentPadding, onBack) {
        item {
            Text(
                stringResource(R.string.cleanup_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                Text(stringResource(R.string.cleanup_cache_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.cleanup_cache_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatSize(cacheBytes), style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = {
                            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                            context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                            cacheBytes = directoryBytes(context.cacheDir)
                            lastRemoved = context.getString(R.string.cleanup_removed_toast)
                        },
                        colors = ButtonDefaults.buttonColors(),
                    ) { Text(stringResource(R.string.cleanup_remove)) }
                }
            }
        }
        // 孤立行 / 枯竭的记忆 / 重复的帖子：仅在有残留时显示（与参考一致，干净态只有缓存卡）。
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CleanupRowCard(
                    titleRes = R.string.cleanup_orphan_title,
                    noteRes = R.string.cleanup_orphan_note,
                    count = orphanRows,
                    onRemove = {
                        store.deleteOrphanMessages().also {
                            orphanRows = store.countOrphanMessages()
                            onChanged()
                        }
                    },
                )
                CleanupRowCard(
                    titleRes = R.string.cleanup_dried_title,
                    noteRes = R.string.cleanup_dried_note,
                    count = driedMemories,
                    onRemove = {
                        store.deleteDriedMemories().also {
                            driedMemories = store.countDriedMemories()
                            onChanged()
                        }
                    },
                )
                CleanupRowCard(
                    titleRes = R.string.cleanup_duplicate_title,
                    noteRes = R.string.cleanup_duplicate_note,
                    count = duplicatePosts,
                    onRemove = {
                        store.deleteDuplicatePosts().also {
                            duplicatePosts = store.countDuplicatePosts()
                            onChanged()
                        }
                    },
                )
            }
        }
        lastRemoved?.let { removed -> item { StatusCard(removed) } }
    }
}

internal fun directoryBytes(dir: File?): Long =
    dir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

@Composable
private fun CleanupRowCard(
    titleRes: Int,
    noteRes: Int,
    count: Int,
    onRemove: () -> Int,
) {
    val context = LocalContext.current
    if (count <= 0) return
    var lastRemoved by remember { mutableStateOf<String?>(null) }
    PageCard {
        Text(stringResource(titleRes), fontWeight = FontWeight.Bold)
        Text(
            stringResource(noteRes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$count", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = {
                    val removed = onRemove()
                    if (removed > 0) {
                        lastRemoved = context.getString(R.string.cleanup_removed_rows, removed)
                    }
                },
                colors = ButtonDefaults.buttonColors(),
            ) { Text(stringResource(R.string.cleanup_remove)) }
        }
        lastRemoved?.let { StatusCard(it) }
    }
}

@Composable
internal fun ModelEngineSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(R.string.settings_models_engine_title, "models-engine-screen", contentPadding, onBack) {
        item {
            PageCard {
                Text(
                    stringResource(R.string.settings_models_engine_summary),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.models_engine_stub_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DeveloperSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenWelcome: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }
    val packageInfo = remember { pm.getPackageInfo(context.packageName, 0) }
    val versionText = remember {
        "${packageInfo.versionName} (${packageInfo.longVersionCode})"
    }
    val deviceText = remember {
        "${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
    }
    val hardwareText = remember {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        (context.getSystemService(android.app.Activity.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.let { it.getMemoryInfo(memoryInfo) }
        "${android.os.Build.HARDWARE} · $abi · ${memoryInfo.totalMem / (1L shl 20)} MB RAM"
    }
    val appStorageText = remember {
        val bytes = directoryBytes(context.filesDir) + directoryBytes(context.cacheDir) + directoryBytes(context.getDatabasePath("world.db"))
        val mb = bytes / (1L shl 20)
        if (mb >= 1) "$mb MB" else "${bytes / (1L shl 10)} kB"
    }

    @Composable
    fun InfoRow(label: String, value: String) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.3f),
            )
            Text(value)
        }
    }

    SettingsPageScaffold(R.string.settings_developer_title, "developer-settings-screen", contentPadding, onBack) {
        item {
            Text(
                stringResource(R.string.developer_section_this_version),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                InfoRow("版本", versionText)
                InfoRow(stringResource(R.string.developer_device_label), deviceText)
                InfoRow(stringResource(R.string.developer_hardware_label), hardwareText)
                InfoRow(stringResource(R.string.developer_app_storage_label), appStorageText)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.developer_storage_note),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.developer_about_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                TextButton(onClick = onOpenUpdates) { Text(stringResource(R.string.developer_whats_new)) }
                TextButton(onClick = onOpenWelcome) { Text(stringResource(R.string.developer_replay_intro)) }
                TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.developer_privacy_policy)) }
                TextButton(onClick = onOpenStorage) { Text(stringResource(R.string.developer_storage_entry)) }
                TextButton(onClick = onOpenHelp) { Text(stringResource(R.string.developer_help_entry)) }
            }
        }
        item {
            Text(
                stringResource(R.string.developer_diagnostics_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            PageCard {
                TextButton(onClick = onOpenDiagnostics) { Text(stringResource(R.string.developer_runtime_entry)) }
            }
        }
    }
}
