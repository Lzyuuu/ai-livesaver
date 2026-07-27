package io.github.lzyuuu.ailivesaver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiLivesaverTheme {
                AiLivesaverApp()
            }
        }
    }
}

private enum class Destination(
    val labelRes: Int,
    val glyph: String,
) {
    World(R.string.nav_world, "◉"),
    Chats(R.string.nav_chats, "✦"),
    Moments(R.string.nav_moments, "◎"),
    Commons(R.string.nav_commons, "#"),
    Me(R.string.nav_me, "◇"),
}

@Composable
private fun AiLivesaverTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFEAB8D9),
            secondary = Color(0xFFD6B9CF),
            surface = Color(0xFF171217),
            background = Color(0xFF171217),
        )
        else -> lightColorScheme(
            primary = Color(0xFF805372),
            secondary = Color(0xFF755766),
            surface = Color(0xFFFFF8FB),
            background = Color(0xFFFFF8FB),
            surfaceVariant = Color(0xFFF3E8EF),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun AiLivesaverApp() {
    val context = LocalContext.current
    val worldStore = remember { WorldStore(context) }
    var worldRevision by remember { mutableIntStateOf(0) }
    var destinationName by rememberSaveable { mutableStateOf(Destination.World.name) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    var showProviders by rememberSaveable { mutableStateOf(false) }
    var showWorldSettings by rememberSaveable { mutableStateOf(false) }
    var showLocalDream by rememberSaveable { mutableStateOf(false) }
    var showBackups by rememberSaveable { mutableStateOf(false) }
    val destination = Destination.valueOf(destinationName)
    val primaryCharacter = remember(worldRevision) { worldStore.primaryCharacter() }
    val latestMoment = remember(worldRevision) { worldStore.posts("moment").firstOrNull() }
    val latestForum = remember(worldRevision) { worldStore.posts("forum").firstOrNull() }
    val queueCount = remember(worldRevision) { worldStore.queueCount() }

    LaunchedEffect(Unit) {
        WorldEngine.onAppOpened(context) { worldRevision++ }
    }

    BackHandler(
        enabled = showUpdates || showProviders || showWorldSettings || showLocalDream || showBackups,
    ) {
        showUpdates = false
        showProviders = false
        showWorldSettings = false
        showLocalDream = false
        showBackups = false
    }

    Scaffold(
        bottomBar = {
            if (
                !showUpdates && !showProviders && !showWorldSettings &&
                !showLocalDream && !showBackups
            ) {
                NavigationBar {
                    Destination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == destination,
                            onClick = { destinationName = item.name },
                            icon = {
                                Text(
                                    text = item.glyph,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (showUpdates) {
            UpdateScreen(
                contentPadding = padding,
                onBack = { showUpdates = false },
            )
        } else if (showProviders) {
            ProviderScreen(
                contentPadding = padding,
                onBack = { showProviders = false },
            )
        } else if (showWorldSettings) {
            WorldSettingsScreen(
                contentPadding = padding,
                onBack = { showWorldSettings = false },
                onWorldChanged = { worldRevision++ },
            )
        } else if (showLocalDream) {
            LocalDreamSettingsScreen(
                contentPadding = padding,
                onBack = { showLocalDream = false },
            )
        } else if (showBackups) {
            BackupSettingsScreen(
                contentPadding = padding,
                store = worldStore,
                onBack = { showBackups = false },
            )
        } else {
            when (destination) {
                Destination.World -> WorldScreen(
                    contentPadding = padding,
                    character = primaryCharacter,
                    latestMoment = latestMoment,
                    latestForum = latestForum,
                    queueCount = queueCount,
                    onOpenChat = { destinationName = Destination.Chats.name },
                )
                Destination.Chats -> ChatsScreen(
                    contentPadding = padding,
                    store = worldStore,
                    revision = worldRevision,
                    onChanged = { worldRevision++ },
                    onConfigureProvider = { showProviders = true },
                )
                Destination.Moments -> SocialScreen(
                    kind = "moment",
                    contentPadding = padding,
                    store = worldStore,
                    revision = worldRevision,
                    onChanged = { worldRevision++ },
                    onStartWorld = { destinationName = Destination.Chats.name },
                )
                Destination.Commons -> SocialScreen(
                    kind = "forum",
                    contentPadding = padding,
                    store = worldStore,
                    revision = worldRevision,
                    onChanged = { worldRevision++ },
                    onStartWorld = { destinationName = Destination.Chats.name },
                )
                Destination.Me -> MeScreen(
                    contentPadding = padding,
                    onOpenUpdates = { showUpdates = true },
                    onOpenProviders = { showProviders = true },
                    onOpenWorldSettings = { showWorldSettings = true },
                    onOpenLocalDream = { showLocalDream = true },
                    onOpenBackups = { showBackups = true },
                )
            }
        }
    }
}

@Composable
private fun WorldScreen(
    contentPadding: PaddingValues,
    character: ResidentCharacter?,
    latestMoment: SocialPost?,
    latestForum: SocialPost?,
    queueCount: Int,
    onOpenChat: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Eyebrow(stringResource(R.string.world_eyebrow))
                    Text(
                        text = stringResource(R.string.world_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("♢", fontSize = 22.sp)
                    }
                }
            }
        }
        if (character == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.world_not_created),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.world_not_created_summary))
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = onOpenChat) {
                            Text(stringResource(R.string.begin_world_building))
                        }
                    }
                }
            }
        } else {
            item { RelationshipHero(character, onOpenChat) }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.your_circle),
                action = stringResource(R.string.manage),
            )
            Spacer(Modifier.height(12.dp))
            CircleStrip(character)
        }
        item {
            WorldSection(
                title = stringResource(R.string.respond_first),
                action = stringResource(R.string.open_chats),
            ) {
                if (character == null) {
                    Text(
                        stringResource(R.string.no_world_events),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PersonMessage(
                        character.name.take(1).uppercase(),
                        character.name,
                        stringResource(R.string.waiting_for_first_message),
                    )
                }
            }
        }
        item {
            WorldSection(
                title = stringResource(R.string.moments_title),
                action = stringResource(R.string.enter),
            ) {
                if (latestMoment == null) {
                    Text(
                        stringResource(R.string.no_moments_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PersonMessage(
                        latestMoment.authorName.take(1).uppercase(),
                        latestMoment.authorName,
                        latestMoment.body,
                    )
                }
            }
        }
        item {
            WorldSection(
                title = stringResource(R.string.commons_title),
                action = stringResource(R.string.commons_replies),
            ) {
                if (latestForum == null) {
                    Text(
                        stringResource(R.string.no_commons_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        latestForum.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        latestForum.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            WorldSection(
                title = stringResource(R.string.creation_queue),
                action = stringResource(R.string.queue_count, queueCount),
            ) {
                Text(
                    if (queueCount == 0) {
                        stringResource(R.string.queue_empty)
                    } else {
                        stringResource(R.string.queue_summary)
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RelationshipHero(
    character: ResidentCharacter,
    onOpenChat: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(character.name.take(1).uppercase(), 72.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                character.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.primary_character_status),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = RoundedCornerShape(99.dp),
            ) {
                Text(
                    text = "♥  ${stringResource(R.string.root_relationship)}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.continue_chat))
                }
                TextButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.view_updates))
                }
            }
        }
    }
}

@Composable
private fun CircleStrip(character: ResidentCharacter?) {
    val addLabel = stringResource(R.string.add)
    val people = buildList {
        character?.let { add(it.name to it.name.take(1).uppercase()) }
        add(addLabel to "+")
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        people.forEach { (name, initial) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(initial, 58.dp)
                Spacer(Modifier.height(6.dp))
                Text(name, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun WorldSection(
    title: String,
    action: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionHeader(title, action)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PersonMessage(initial: String, name: String, message: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(initial, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Avatar(initial: String, size: androidx.compose.ui.unit.Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initial,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private data class SettingRow(
    val titleRes: Int,
    val summaryRes: Int,
    val destination: String? = null,
)

@Composable
private fun MeScreen(
    contentPadding: PaddingValues,
    onOpenUpdates: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenWorldSettings: () -> Unit,
    onOpenLocalDream: () -> Unit,
    onOpenBackups: () -> Unit,
) {
    val settings = listOf(
        SettingRow(R.string.provider_settings, R.string.provider_settings_summary, "providers"),
        SettingRow(R.string.local_dream_settings, R.string.local_dream_settings_summary, "dream"),
        SettingRow(R.string.world_settings, R.string.world_settings_summary, "world"),
        SettingRow(R.string.backup_settings, R.string.backup_settings_summary, "backups"),
        SettingRow(R.string.about_updates, R.string.about_updates_summary, "updates"),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = contentPadding.calculateTopPadding() + 24.dp,
            end = 20.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Eyebrow(stringResource(R.string.me_eyebrow))
            Text(
                stringResource(R.string.me_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.me_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        items(settings) { setting ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = setting.destination != null) {
                        when (setting.destination) {
                            "providers" -> onOpenProviders()
                            "dream" -> onOpenLocalDream()
                            "world" -> onOpenWorldSettings()
                            "backups" -> onOpenBackups()
                            "updates" -> onOpenUpdates()
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(setting.titleRes), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(setting.summaryRes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (setting.destination != null) {
                        Spacer(Modifier.width(12.dp))
                        Text("›", fontSize = 28.sp)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tagline),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BackupSettingsScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var includeMedia by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val exportSuccess = stringResource(R.string.backup_exported)
    val restoreSuccess = stringResource(R.string.backup_restored)
    val failed = stringResource(R.string.backup_failed)
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        WorldBackup.export(context, store, uri, includeMedia) { result ->
            busy = false
            status = result.fold(
                onSuccess = { exportSuccess },
                onFailure = { "$failed：${it.message.orEmpty()}" },
            )
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        WorldBackup.restore(context, store, uri) { result ->
            busy = false
            status = result.fold(
                onSuccess = {
                    (context as? MainActivity)?.recreate()
                    restoreSuccess
                },
                onFailure = { "$failed：${it.message.orEmpty()}" },
            )
        }
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
            TextButton(onClick = onBack) { Text("‹  ${stringResource(R.string.back)}") }
            Text(
                stringResource(R.string.backup_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.backup_privacy_warning),
                color = MaterialTheme.colorScheme.error,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.include_generated_media), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.include_generated_media_summary),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = includeMedia, onCheckedChange = { includeMedia = it })
                }
            }
        }
        item {
            Button(
                onClick = {
                    val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    createBackup.launch("ai-livesaver-$date.zip")
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.export_world_backup))
            }
        }
        item {
            Button(
                onClick = { restoreBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.restore_world_backup))
            }
            Text(
                stringResource(R.string.restore_replaces_world),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) item { StatusCard(stringResource(R.string.backup_working)) }
        status?.let { item { StatusCard(it) } }
    }
}

@Composable
private fun LocalDreamSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val connected = stringResource(R.string.local_dream_connected)
    val unavailable = stringResource(R.string.local_dream_unavailable)

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
            TextButton(onClick = onBack) { Text("‹  ${stringResource(R.string.back)}") }
            Text(
                stringResource(R.string.local_dream_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.local_dream_api_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(
                onClick = {
                    context.packageManager
                        .getLaunchIntentForPackage("io.github.xororz.localdream")
                        ?.let(context::startActivity)
                        ?: run { status = unavailable }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_local_dream))
            }
        }
        item {
            Button(
                onClick = {
                    checking = true
                    status = null
                    LocalDreamClient.probe { result ->
                        checking = false
                        status = result.fold(
                            onSuccess = { "$connected · CLIP $it tokens" },
                            onFailure = { "$unavailable：${it.message.orEmpty()}" },
                        )
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (checking) {
                        stringResource(R.string.checking_local_dream)
                    } else {
                        stringResource(R.string.check_local_dream)
                    },
                )
            }
        }
        status?.let { item { StatusCard(it) } }
    }
}

@Composable
private fun WorldSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onWorldChanged: () -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(WorldEngine.isEnabled(context)) }
    var budget by remember { mutableIntStateOf(WorldEngine.dailyBudget(context)) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val generated = stringResource(R.string.world_advanced_once)
    val notReady = stringResource(R.string.world_advance_not_ready)

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
            TextButton(onClick = onBack) { Text("‹  ${stringResource(R.string.back)}") }
            Text(
                stringResource(R.string.world_settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.world_engine_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.auto_world_events),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.auto_world_events_summary),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            WorldEngine.setEnabled(context, it)
                        },
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.daily_auto_budget),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 12, 24).forEach { choice ->
                    FilterChip(
                        selected = budget == choice,
                        onClick = {
                            budget = choice
                            WorldEngine.setDailyBudget(context, choice)
                        },
                        label = { Text(stringResource(R.string.requests_per_day, choice)) },
                    )
                }
            }
            Text(
                stringResource(R.string.auto_budget_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Button(
                onClick = {
                    generating = true
                    status = null
                    if (!WorldEngine.generate(context) { success ->
                            generating = false
                            status = if (success) generated else notReady
                            if (success) onWorldChanged()
                        }
                    ) {
                        generating = false
                        status = notReady
                    }
                },
                enabled = enabled && !generating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (generating) {
                        stringResource(R.string.advancing_world)
                    } else {
                        stringResource(R.string.advance_world_once)
                    },
                )
            }
        }
        status?.let { item { StatusCard(it) } }
    }
}

@Composable
private fun ProviderScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProviderStore(context) }
    var textConfig by remember { mutableStateOf(store.load()) }
    var visionConfig by remember { mutableStateOf(store.loadVision()) }
    var profile by rememberSaveable { mutableStateOf("text") }
    val initial = if (profile == "text") textConfig else visionConfig ?: ProviderConfig()
    var presetName by rememberSaveable { mutableStateOf(initial.preset.name) }
    var baseUrl by rememberSaveable { mutableStateOf(initial.baseUrl) }
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var apiKey by rememberSaveable { mutableStateOf(initial.apiKey) }
    var extraHeaders by rememberSaveable { mutableStateOf(initial.extraHeaders) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val preset = ProviderPreset.valueOf(presetName)
    val requiredFields = stringResource(R.string.provider_required_fields)
    val savedMessage = stringResource(R.string.provider_saved)
    val connectionOk = stringResource(R.string.provider_connection_ok)
    val connectionFailed = stringResource(R.string.provider_connection_failed)
    val visionRemoved = stringResource(R.string.vision_provider_removed)

    fun showProfile(name: String, config: ProviderConfig) {
        profile = name
        presetName = config.preset.name
        baseUrl = config.baseUrl
        model = config.model
        apiKey = config.apiKey
        extraHeaders = config.extraHeaders
        status = null
    }

    fun currentConfig() = ProviderConfig(
        preset = preset,
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        apiKey = apiKey.trim(),
        extraHeaders = extraHeaders.trim(),
    )

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
            TextButton(onClick = onBack) {
                Text("‹  ${stringResource(R.string.back)}")
            }
            Text(
                stringResource(R.string.provider_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (profile == "text") {
                    stringResource(R.string.provider_privacy)
                } else {
                    stringResource(R.string.vision_provider_summary)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = profile == "text",
                    onClick = { showProfile("text", textConfig) },
                    label = { Text(stringResource(R.string.general_provider)) },
                )
                FilterChip(
                    selected = profile == "vision",
                    onClick = { showProfile("vision", visionConfig ?: ProviderConfig()) },
                    label = { Text(stringResource(R.string.vision_provider)) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProviderPreset.entries.forEach { choice ->
                    FilterChip(
                        selected = preset == choice,
                        onClick = {
                            presetName = choice.name
                            baseUrl = choice.defaultBaseUrl
                            model = choice.defaultModel
                            status = null
                        },
                        label = { Text(choice.displayName) },
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.provider_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.provider_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.provider_api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = extraHeaders,
                onValueChange = { extraHeaders = it },
                label = { Text(stringResource(R.string.provider_extra_headers)) },
                supportingText = { Text(stringResource(R.string.provider_extra_headers_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val config = currentConfig()
                        status = if (config.isValid()) {
                            if (profile == "text") {
                                store.save(config)
                                textConfig = config
                            } else {
                                store.saveVision(config)
                                visionConfig = config
                            }
                            savedMessage
                        } else {
                            requiredFields
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.save))
                }
                Button(
                    onClick = {
                        val config = currentConfig()
                        if (!config.isValid()) {
                            status = requiredFields
                        } else {
                            testing = true
                            status = null
                            ProviderConnectionTester.test(config) { result ->
                                testing = false
                                status = result.fold(
                                    onSuccess = { connectionOk },
                                    onFailure = {
                                        "$connectionFailed：${it.message.orEmpty()}"
                                    },
                                )
                            }
                        }
                    },
                    enabled = !testing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (testing) {
                            stringResource(R.string.testing_connection)
                        } else {
                            stringResource(R.string.test_connection)
                        },
                    )
                }
            }
        }
        status?.let { message ->
            item { StatusCard(message) }
        }
        if (profile == "vision" && visionConfig != null) {
            item {
                TextButton(
                    onClick = {
                        store.clearVision()
                        visionConfig = null
                        status = visionRemoved
                    },
                ) {
                    Text(stringResource(R.string.remove_vision_provider))
                }
            }
        }
    }
}

@Composable
private fun UpdateScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val context = LocalContext.current
    val checker = remember { GitHubUpdateChecker() }
    val networkError = stringResource(R.string.network_error)

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
            TextButton(onClick = onBack) {
                Text("‹  ${stringResource(R.string.back)}")
            }
            Text(
                stringResource(R.string.updates_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.update_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.testing_channel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${stringResource(R.string.current_version)}  ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    state = UpdateState.Checking
                    checker.checkAsync(BuildConfig.VERSION_NAME) { result ->
                        state = result.fold(
                            onSuccess = { it },
                            onFailure = {
                                UpdateState.Failed(
                                    it.message ?: networkError,
                                )
                            },
                        )
                    }
                },
                enabled = state !is UpdateState.Checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state is UpdateState.Checking) {
                        stringResource(R.string.checking_updates)
                    } else {
                        stringResource(R.string.check_updates)
                    },
                )
            }
        }
        when (val snapshot = state) {
            UpdateState.Idle -> Unit
            UpdateState.Checking -> Unit
            UpdateState.UpToDate -> item {
                StatusCard(stringResource(R.string.up_to_date))
            }
            is UpdateState.Failed -> item {
                StatusCard(stringResource(R.string.update_failed, snapshot.message))
            }
            is UpdateState.Available -> {
                item {
                    StatusCard(stringResource(R.string.update_available, snapshot.release.tagName))
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.release_notes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                snapshot.release.notes.ifBlank {
                                    stringResource(R.string.no_release_notes)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            snapshot.release.apkSizeBytes?.let { size ->
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.apk_size, humanFileSize(size)))
                            }
                            Text(
                                if (snapshot.release.checksumUrl != null) {
                                    stringResource(R.string.checksum_available)
                                } else {
                                    stringResource(R.string.checksum_missing)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        snapshot.release.downloadUrl.toUri(),
                                    ),
                                )
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    context,
                                    R.string.open_link_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.download_update))
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Text(message, modifier = Modifier.padding(18.dp))
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp,
    )
}

private fun humanFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return "%.1f KB".format(kib)
    return "%.1f MB".format(kib / 1024.0)
}
