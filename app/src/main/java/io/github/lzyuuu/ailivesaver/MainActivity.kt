package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val APP_PREFERENCES = "app_settings"
private const val THEME_MODE_KEY = "theme_mode"

private enum class ThemeMode {
    System,
    Light,
    Dark,
}

private fun readThemeMode(context: android.content.Context): ThemeMode =
    runCatching {
        ThemeMode.valueOf(
            context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
                .getString(THEME_MODE_KEY, ThemeMode.System.name) ?: ThemeMode.System.name,
        )
    }.getOrDefault(ThemeMode.System)

private fun writeThemeMode(context: android.content.Context, mode: ThemeMode) {
    context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_MODE_KEY, mode.name)
        .apply()
}

internal fun animationsEnabled(
    animatorDurationScale: Float,
    transitionAnimationScale: Float,
    windowAnimationScale: Float,
): Boolean = animatorDurationScale > 0f &&
    transitionAnimationScale > 0f &&
    windowAnimationScale > 0f

internal fun systemAnimationsEnabled(context: android.content.Context): Boolean {
    val resolver = context.contentResolver
    fun scale(name: String) = runCatching {
        Settings.Global.getFloat(resolver, name, 1f)
    }.getOrDefault(1f)
    return animationsEnabled(
        animatorDurationScale = scale(Settings.Global.ANIMATOR_DURATION_SCALE),
        transitionAnimationScale = scale(Settings.Global.TRANSITION_ANIMATION_SCALE),
        windowAnimationScale = scale(Settings.Global.WINDOW_ANIMATION_SCALE),
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeModeName by rememberSaveable { mutableStateOf(readThemeMode(this).name) }
            val themeMode = runCatching { ThemeMode.valueOf(themeModeName) }
                .getOrDefault(ThemeMode.System)
            AiLivesaverTheme(themeMode) {
                AiLivesaverApp(
                    themeMode = themeMode,
                    onThemeModeChanged = { next ->
                        writeThemeMode(this, next)
                        themeModeName = next.name
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        cleanupTemporaryCache(this)
        LocalDreamQueue.resume(this)
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
private fun AiLivesaverTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
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
private fun AiLivesaverApp(
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current
    val worldStore = remember { WorldStore(context) }
    var worldRevision by remember { mutableIntStateOf(0) }
    var destinationName by rememberSaveable { mutableStateOf(Destination.World.name) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    var showProviders by rememberSaveable { mutableStateOf(false) }
    var showWorldSettings by rememberSaveable { mutableStateOf(false) }
    var showWorldKnowledge by rememberSaveable { mutableStateOf(false) }
    var showLocalDream by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showBackups by rememberSaveable { mutableStateOf(false) }
    var showIdentity by rememberSaveable { mutableStateOf(false) }
    var showCharacters by rememberSaveable { mutableStateOf(false) }
    val destination = Destination.valueOf(destinationName)
    val identity = remember(worldRevision) { worldStore.identity() }
    val primaryCharacter = remember(worldRevision) { worldStore.primaryCharacter() }
    val primaryRelationship = remember(worldRevision, primaryCharacter?.id) {
        primaryCharacter?.let { worldStore.relationship(it.id) }
    }
    val characters = remember(worldRevision) { worldStore.characters(includeDeparted = false) }
    val latestMoment = remember(worldRevision) { worldStore.posts("moment").firstOrNull() }
    val latestForum = remember(worldRevision) { worldStore.posts("forum").firstOrNull() }
    val latestForumReplyCount = remember(worldRevision, latestForum?.id) {
        latestForum?.let { worldStore.comments(it.id).size } ?: 0
    }
    val responseEvents = remember(worldRevision) { worldStore.worldEvents(needsResponseOnly = true) }
    val queueCount = remember(worldRevision) { worldStore.queueCount() }
    val budgetExhausted = remember(worldRevision) { WorldEngine.budgetExhausted(context) }

    val activity = context as? ComponentActivity
    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    WorldEngine.onAppOpened(activity) { worldRevision++ }
                }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
    }

    BackHandler(
        enabled = showUpdates || showProviders || showWorldSettings || showWorldKnowledge ||
            showLocalDream ||
            showDiagnostics || showPrivacy || showBackups || showIdentity || showCharacters,
    ) {
        showUpdates = false
        showProviders = false
        showWorldSettings = false
        showWorldKnowledge = false
        showLocalDream = false
        showDiagnostics = false
        showPrivacy = false
        showBackups = false
        showIdentity = false
        showCharacters = false
    }

    Scaffold(
        bottomBar = {
            if (
                !showUpdates && !showProviders && !showWorldSettings && !showWorldKnowledge &&
                !showLocalDream && !showDiagnostics && !showPrivacy && !showBackups &&
                    !showIdentity && !showCharacters
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
                onOpenBackups = {
                    showUpdates = false
                    showBackups = true
                },
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
        } else if (showWorldKnowledge) {
            WorldKnowledgeScreen(
                contentPadding = padding,
                store = worldStore,
                revision = worldRevision,
                onBack = { showWorldKnowledge = false },
                onChanged = { worldRevision++ },
            )
        } else if (showLocalDream) {
            LocalDreamSettingsScreen(
                contentPadding = padding,
                store = worldStore,
                revision = worldRevision,
                onBack = {
                    showLocalDream = false
                    worldRevision++
                },
                onChanged = { worldRevision++ },
            )
        } else if (showDiagnostics) {
            DiagnosticsScreen(
                contentPadding = padding,
                onBack = { showDiagnostics = false },
            )
        } else if (showPrivacy) {
            PrivacyScreen(
                contentPadding = padding,
                onBack = { showPrivacy = false },
            )
        } else if (showBackups) {
            BackupSettingsScreen(
                contentPadding = padding,
                store = worldStore,
                onBack = { showBackups = false },
            )
        } else if (showIdentity) {
            IdentityScreen(
                contentPadding = padding,
                store = worldStore,
                onBack = { showIdentity = false },
                onChanged = { worldRevision++ },
            )
        } else if (showCharacters) {
            CharacterManagerScreen(
                contentPadding = padding,
                store = worldStore,
                revision = worldRevision,
                onBack = { showCharacters = false },
                onChanged = { worldRevision++ },
            )
        } else {
            when (destination) {
                Destination.World -> WorldScreen(
                    contentPadding = padding,
                    character = primaryCharacter,
                    identity = identity,
                    relationship = primaryRelationship,
                    characters = characters,
                    latestMoment = latestMoment,
                    latestForum = latestForum,
                    latestForumReplyCount = latestForumReplyCount,
                    responseEvents = responseEvents,
                    queueCount = queueCount,
                    budgetExhausted = budgetExhausted,
                    onOpenChat = {
                        responseEvents.forEach { worldStore.markWorldEventSeen(it.id) }
                        worldRevision++
                        destinationName = Destination.Chats.name
                    },
                    onOpenMoments = { destinationName = Destination.Moments.name },
                    onOpenCommons = { destinationName = Destination.Commons.name },
                    onOpenQueue = { showLocalDream = true },
                    onManageCircle = { showCharacters = true },
                    onOpenEvent = { event ->
                        worldStore.markWorldEventSeen(event.id)
                        worldRevision++
                        destinationName = when {
                            "message" in event.kind -> Destination.Chats.name
                            "forum" in event.kind || "commons" in event.kind ->
                                Destination.Commons.name
                            else -> Destination.Moments.name
                        }
                    },
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
                    identity = identity,
                    store = worldStore,
                    revision = worldRevision,
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    onOpenIdentity = { showIdentity = true },
                    onOpenCharacters = { showCharacters = true },
                    onOpenUpdates = { showUpdates = true },
                    onOpenProviders = { showProviders = true },
                    onOpenWorldSettings = { showWorldSettings = true },
                    onOpenWorldKnowledge = { showWorldKnowledge = true },
                    onOpenLocalDream = { showLocalDream = true },
                    onOpenDiagnostics = { showDiagnostics = true },
                    onOpenPrivacy = { showPrivacy = true },
                    onOpenBackups = { showBackups = true },
                    onOpenMoments = { destinationName = Destination.Moments.name },
                    onOpenCommons = { destinationName = Destination.Commons.name },
                )
            }
        }
    }
}

@Composable
private fun WorldScreen(
    contentPadding: PaddingValues,
    character: ResidentCharacter?,
    identity: UserIdentity,
    relationship: RelationshipState?,
    characters: List<ResidentCharacter>,
    latestMoment: SocialPost?,
    latestForum: SocialPost?,
    latestForumReplyCount: Int,
    responseEvents: List<WorldEvent>,
    queueCount: Int,
    budgetExhausted: Boolean,
    onOpenChat: () -> Unit,
    onOpenMoments: () -> Unit,
    onOpenCommons: () -> Unit,
    onOpenQueue: () -> Unit,
    onManageCircle: () -> Unit,
    onOpenEvent: (WorldEvent) -> Unit,
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
                        Avatar(identity.name.take(1).uppercase(), 48.dp, identity.avatarPath)
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
            item { RelationshipHero(character, relationship, onOpenChat, onOpenMoments) }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.your_circle),
                action = stringResource(R.string.manage),
                onAction = onManageCircle,
            )
            Spacer(Modifier.height(12.dp))
            CircleStrip(characters, onAdd = onManageCircle)
        }
        item {
            WorldSection(
                title = stringResource(R.string.respond_first),
                action = stringResource(
                    if (responseEvents.any { "message" in it.kind }) {
                        R.string.open_chats
                    } else {
                        R.string.enter
                    },
                ),
                onAction = { responseEvents.firstOrNull()?.let(onOpenEvent) ?: onOpenChat() },
            ) {
                if (responseEvents.isEmpty()) {
                    Text(
                        if (character == null) {
                            stringResource(R.string.no_world_events)
                        } else if (budgetExhausted) {
                            stringResource(R.string.world_quiet_today)
                        } else {
                            stringResource(R.string.waiting_for_first_message)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        responseEvents.take(3).forEach { event ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEvent(event) },
                            ) {
                                PersonMessage(
                                    event.actorName.take(1).uppercase(),
                                    event.actorName,
                                    event.summary,
                                    event.providerName.takeIf { it.isNotBlank() }?.let {
                                        stringResource(
                                            R.string.world_event_source,
                                            it,
                                            event.modelName,
                                        )
                                    }.orEmpty(),
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            WorldSection(
                title = stringResource(R.string.moments_title),
                action = stringResource(R.string.enter),
                onAction = onOpenMoments,
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
                action = stringResource(R.string.replies_count, latestForumReplyCount),
                onAction = onOpenCommons,
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
                onAction = onOpenQueue,
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
    relationship: RelationshipState?,
    onOpenChat: () -> Unit,
    onOpenUpdates: () -> Unit,
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
                    text = "♥  ${relationship?.label ?: stringResource(R.string.root_relationship)}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(18.dp))
            relationship?.let {
                Text(
                    it.summary,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(12.dp))
            }
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
                    onClick = onOpenUpdates,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.view_updates))
                }
            }
        }
    }
}

@Composable
private fun CircleStrip(
    characters: List<ResidentCharacter>,
    onAdd: () -> Unit,
) {
    val addLabel = stringResource(R.string.add)
    val people = buildList {
        characters.forEach { add(it.name to it.name.take(1).uppercase()) }
        add(addLabel to "+")
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        people.forEachIndexed { index, (name, initial) ->
            Column(
                modifier = Modifier.clickable(enabled = index == people.lastIndex, onClick = onAdd),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
    onAction: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionHeader(title, action, onAction)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit = {},
) {
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
        TextButton(onClick = onAction) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PersonMessage(initial: String, name: String, message: String, source: String = "") {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(initial, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (source.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
internal fun Avatar(
    initial: String,
    size: androidx.compose.ui.unit.Dp,
    imagePath: String? = null,
) {
    val bitmap = remember(imagePath) { imagePath?.let(BitmapFactory::decodeFile)?.asImageBitmap() }
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    initial,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
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
    identity: UserIdentity,
    store: WorldStore,
    revision: Int,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onOpenIdentity: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenWorldSettings: () -> Unit,
    onOpenWorldKnowledge: () -> Unit,
    onOpenLocalDream: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenMoments: () -> Unit,
    onOpenCommons: () -> Unit,
) {
    val myPosts = remember(revision) {
        (store.posts("moment") + store.posts("forum"))
            .filter { it.authorKind == "user" }
            .sortedByDescending(SocialPost::createdAt)
    }
    val settings = listOf(
        SettingRow(R.string.user_identity, R.string.user_identity_summary, "identity"),
        SettingRow(R.string.world_members, R.string.world_members_summary, "characters"),
        SettingRow(R.string.provider_settings, R.string.provider_settings_summary, "providers"),
        SettingRow(R.string.local_dream_settings, R.string.local_dream_settings_summary, "dream"),
        SettingRow(R.string.world_settings, R.string.world_settings_summary, "world"),
        SettingRow(R.string.world_knowledge, R.string.world_knowledge_summary, "knowledge"),
        SettingRow(R.string.runtime_status, R.string.runtime_status_summary, "diagnostics"),
        SettingRow(R.string.privacy_settings, R.string.privacy_settings_summary, "privacy"),
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(identity.name.take(1).uppercase(), 56.dp, identity.avatarPath)
                Column {
                    Eyebrow(stringResource(R.string.me_eyebrow))
                    Text(
                        identity.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        identity.bio.ifBlank { stringResource(R.string.me_description) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(stringResource(R.string.appearance_settings), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.appearance_settings_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { option ->
                    FilterChip(
                        selected = themeMode == option,
                        onClick = { onThemeModeChanged(option) },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        ThemeMode.System -> R.string.theme_system
                                        ThemeMode.Light -> R.string.theme_light
                                        ThemeMode.Dark -> R.string.theme_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.my_posts),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.my_posts_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (myPosts.isEmpty()) {
            item { StatusCard(stringResource(R.string.no_my_posts)) }
        } else {
            items(myPosts.take(5), key = { "me-post-${it.id}" }) { post ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
                    ),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (post.kind == "moment") {
                                stringResource(R.string.moments_title)
                            } else {
                                stringResource(R.string.commons_title)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            post.title.ifBlank { post.body },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(
                            onClick = {
                                if (post.kind == "moment") onOpenMoments() else onOpenCommons()
                            },
                        ) {
                            Text(stringResource(R.string.open))
                        }
                    }
                }
            }
        }
        items(settings) { setting ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = setting.destination != null) {
                        when (setting.destination) {
                            "identity" -> onOpenIdentity()
                            "characters" -> onOpenCharacters()
                            "providers" -> onOpenProviders()
                            "dream" -> onOpenLocalDream()
                            "world" -> onOpenWorldSettings()
                            "knowledge" -> onOpenWorldKnowledge()
                            "diagnostics" -> onOpenDiagnostics()
                            "privacy" -> onOpenPrivacy()
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
    var destructiveAction by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf("") }
    val exportSuccess = stringResource(R.string.backup_exported)
    val restoreSuccess = stringResource(R.string.backup_restored)
    val failed = stringResource(R.string.backup_failed)
    val appName = stringResource(R.string.app_name)

    destructiveAction?.let { action ->
        val rebuild = action == "rebuild"
        AlertDialog(
            onDismissRequest = {
                destructiveAction = null
                confirmation = ""
            },
            title = {
                Text(
                    stringResource(
                        if (rebuild) R.string.rebuild_world else R.string.erase_all_data,
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            if (rebuild) {
                                R.string.rebuild_world_confirmation
                            } else {
                                R.string.erase_all_data_confirmation
                            },
                            appName,
                        ),
                    )
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(appName) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        busy = true
                        destructiveAction = null
                        val callback: (Result<Unit>) -> Unit = { result ->
                            busy = false
                            result.onSuccess { (context as? MainActivity)?.recreate() }
                            status = result.exceptionOrNull()?.let {
                                "$failed：${it.message.orEmpty()}"
                            }
                        }
                        if (rebuild) {
                            WorldBackup.rebuildWorld(context, store, callback)
                        } else {
                            WorldBackup.eraseAll(context, store, callback)
                        }
                    },
                    enabled = confirmation == appName,
                ) {
                    Text(
                        stringResource(
                            if (rebuild) R.string.rebuild_world else R.string.erase_all_data,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        destructiveAction = null
                        confirmation = ""
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
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
                onFailure = {
                    (context as? MainActivity)?.recreate()
                    "$failed：${it.message.orEmpty()}"
                },
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
        item {
            Text(
                stringResource(R.string.destructive_data_actions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.destructive_data_actions_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            confirmation = ""
                            destructiveAction = "rebuild"
                        },
                        enabled = !busy,
                    ) {
                        Text(stringResource(R.string.rebuild_world))
                    }
                    Text(stringResource(R.string.rebuild_world_summary))
                    TextButton(
                        onClick = {
                            confirmation = ""
                            destructiveAction = "erase"
                        },
                        enabled = !busy,
                    ) {
                        Text(
                            stringResource(R.string.erase_all_data),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        stringResource(R.string.erase_all_data_summary),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (busy) item { StatusCard(stringResource(R.string.backup_working)) }
        status?.let { item { StatusCard(it) } }
    }
}

@Composable
private fun LocalDreamSettingsScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val jobs = remember(revision) { store.mediaJobs() }
    val storageBytes = remember(revision) { store.mediaStorageBytes() }
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
                    try {
                        val intent = context.packageManager
                            .getLaunchIntentForPackage("io.github.xororz.localdream")
                        if (intent == null) status = unavailable else context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        status = unavailable
                    }
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
                            onSuccess = {
                                LocalDreamQueue.resume(
                                    context,
                                    onProgress = { _, step, total ->
                                        status = "$step / $total"
                                    },
                                    onFinished = { _, generation ->
                                        status = generation.fold(
                                            onSuccess = { generationReady ->
                                                "$connected · ${generationReady.seed}"
                                            },
                                            onFailure = {
                                                "$unavailable：${it.message.orEmpty()}"
                                            },
                                        )
                                        onChanged()
                                    },
                                )
                                "$connected · CLIP $it tokens"
                            },
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
        item {
            Text(
                stringResource(R.string.creation_queue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.media_storage_usage,
                    Formatter.formatFileSize(context, storageBytes),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (jobs.isEmpty()) {
            item { StatusCard(stringResource(R.string.queue_empty)) }
        }
        items(jobs, key = MediaJob::postId) { job ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(job.prompt, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(
                            if (job.status == "failed") {
                                R.string.local_dream_failed
                            } else {
                                R.string.local_dream_pending
                            },
                        ),
                        color = if (job.status == "failed") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    if (job.error.isNotBlank()) {
                        Text(
                            job.error,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    if (job.status == "failed") {
                        TextButton(
                            onClick = {
                                store.retryMediaJob(job.postId)
                                onChanged()
                                LocalDreamQueue.resume(
                                    context,
                                    onFinished = { _, _ -> onChanged() },
                                )
                            },
                        ) {
                            Text(stringResource(R.string.retry_generation))
                        }
                    }
                }
            }
        }
        status?.let { item { StatusCard(it) } }
    }
}

@Composable
private fun WorldKnowledgeScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val facts = remember(revision) { store.worldFacts() }
    val characters = remember(revision) { store.characters(includeDeparted = false) }
    var selectedCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedCharacter = characters.firstOrNull { it.id == selectedCharacterId }
        ?: characters.firstOrNull()
    val cognition = remember(revision, selectedCharacter?.id) {
        selectedCharacter?.let { store.characterCognition(it.id) }.orEmpty()
    }
    var factText by rememberSaveable { mutableStateOf("") }
    var cognitionText by rememberSaveable { mutableStateOf("") }
    var contextMemberKey by rememberSaveable { mutableStateOf("user") }
    val memberContext = remember(revision, contextMemberKey) {
        store.memberWorldContext(contextMemberKey)
    }
    var memberLocation by rememberSaveable(contextMemberKey, memberContext.location) {
        mutableStateOf(memberContext.location)
    }
    var memberTimeZone by rememberSaveable(contextMemberKey, memberContext.timeZone) {
        mutableStateOf(memberContext.timeZone)
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
                stringResource(R.string.world_knowledge),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.world_knowledge_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.member_place_and_time),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.member_place_and_time_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = contextMemberKey == "user",
                    onClick = { contextMemberKey = "user" },
                    label = { Text(stringResource(R.string.you)) },
                )
                characters.forEach { character ->
                    FilterChip(
                        selected = contextMemberKey == "character:${character.id}",
                        onClick = { contextMemberKey = "character:${character.id}" },
                        label = { Text(character.name) },
                    )
                }
            }
            OutlinedTextField(
                value = memberLocation,
                onValueChange = { memberLocation = it },
                label = { Text(stringResource(R.string.member_location)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = memberTimeZone,
                onValueChange = { memberTimeZone = it },
                label = { Text(stringResource(R.string.member_time_zone)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    store.saveMemberWorldContext(
                        contextMemberKey,
                        memberLocation,
                        memberTimeZone,
                    )
                    onChanged()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_member_context))
            }
        }
        item {
            Text(
                stringResource(R.string.shared_world_facts),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.shared_world_facts_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = factText,
                onValueChange = { factText = it },
                label = { Text(stringResource(R.string.fact_or_rule)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    store.addWorldFact(factText)
                    factText = ""
                    onChanged()
                },
                enabled = factText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_world_fact))
            }
        }
        items(facts, key = WorldFact::id) { fact ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(fact.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                store.setWorldFactPinned(fact.id, !fact.pinned)
                                onChanged()
                            },
                        ) {
                            Text(stringResource(if (fact.pinned) R.string.unpin else R.string.pin))
                        }
                        TextButton(
                            onClick = {
                                store.deleteWorldFact(fact.id)
                                onChanged()
                            },
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
        if (characters.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.character_cognition_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.character_cognition_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    characters.forEach { character ->
                        FilterChip(
                            selected = selectedCharacter?.id == character.id,
                            onClick = { selectedCharacterId = character.id },
                            label = { Text(character.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = cognitionText,
                    onValueChange = { cognitionText = it },
                    label = { Text(stringResource(R.string.character_cognition_title)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        selectedCharacter?.let {
                            store.addCharacterCognition(it.id, cognitionText)
                            cognitionText = ""
                            onChanged()
                        }
                    },
                    enabled = cognitionText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.add_cognition))
                }
            }
            items(cognition, key = CharacterCognition::id) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(item.body)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    store.setCharacterCognitionPinned(item.id, !item.pinned)
                                    onChanged()
                                },
                            ) {
                                Text(
                                    stringResource(if (item.pinned) R.string.unpin else R.string.pin),
                                )
                            }
                            TextButton(
                                onClick = {
                                    store.promoteCognitionToWorldFact(item.id)
                                    onChanged()
                                },
                            ) {
                                Text(stringResource(R.string.promote_to_world_fact))
                            }
                            TextButton(
                                onClick = {
                                    store.deleteCharacterCognition(item.id)
                                    onChanged()
                                },
                            ) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
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
    var activity by remember { mutableStateOf(WorldEngine.activity(context)) }
    var globalStyle by remember { mutableStateOf(WorldEngine.globalStyle(context)) }
    var budget by remember { mutableIntStateOf(WorldEngine.dailyBudget(context)) }
    var continuous by remember { mutableStateOf(WorldEngine.continuous(context)) }
    var notifications by remember { mutableStateOf(WorldEngine.notificationsEnabled(context)) }
    var preview by remember { mutableStateOf(WorldEngine.notificationPreview(context)) }
    var doNotDisturb by remember { mutableStateOf(WorldEngine.doNotDisturb(context)) }
    var taskPaused by remember { mutableStateOf(WorldEngine.taskPaused(context)) }
    val characters = remember { WorldStore(context).use { it.characters(includeDeparted = false) } }
    var characterControlRevision by remember { mutableIntStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val generated = stringResource(R.string.world_advanced_once)
    val notReady = stringResource(R.string.world_advance_not_ready)
    val unlimited = stringResource(R.string.unlimited)
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifications = granted
        WorldEngine.setNotificationsEnabled(context, granted)
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
                stringResource(R.string.world_activity),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "quiet" to R.string.activity_quiet,
                    "natural" to R.string.activity_natural,
                    "active" to R.string.activity_active,
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = activity == value,
                        onClick = {
                            activity = value
                            WorldEngine.setActivity(context, value)
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(
                stringResource(R.string.activity_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = globalStyle,
                onValueChange = {
                    globalStyle = it
                    WorldEngine.setGlobalStyle(context, it)
                },
                label = { Text(stringResource(R.string.global_style)) },
                supportingText = { Text(stringResource(R.string.global_style_summary)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                stringResource(R.string.daily_auto_budget),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val choices = listOf(10, 20, 40, 0)
                (if (budget in choices) choices else listOf(budget) + choices).forEach { choice ->
                    FilterChip(
                        selected = budget == choice,
                        onClick = {
                            budget = choice
                            WorldEngine.setDailyBudget(context, choice)
                        },
                        label = {
                            Text(
                                if (choice == 0) {
                                    unlimited
                                } else {
                                    stringResource(R.string.requests_per_day, choice)
                                },
                            )
                        },
                    )
                }
            }
            Text(
                stringResource(
                    R.string.budget_status,
                    WorldEngine.budgetUsed(context),
                    if (budget == 0) unlimited else budget.toString(),
                ),
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.auto_budget_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.continuous_world),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.continuous_world_summary),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = continuous,
                            onCheckedChange = {
                                continuous = it
                                WorldEngine.setContinuous(context, it)
                            },
                        )
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.relationship_notifications),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.relationship_notifications_summary),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = notifications,
                            onCheckedChange = { checked ->
                                if (
                                    checked &&
                                    Build.VERSION.SDK_INT >= 33 &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    notifications = checked
                                    WorldEngine.setNotificationsEnabled(context, checked)
                                }
                            },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.notification_preview))
                            Text(
                                stringResource(R.string.notification_preview_summary),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = preview,
                            onCheckedChange = {
                                preview = it
                                WorldEngine.setNotificationPreview(context, it)
                            },
                            enabled = notifications,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.do_not_disturb))
                            Text(
                                stringResource(R.string.do_not_disturb_summary),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = doNotDisturb,
                            onCheckedChange = {
                                doNotDisturb = it
                                WorldEngine.setDoNotDisturb(context, it)
                            },
                        )
                    }
                }
            }
        }
        if (characters.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.character_activity_controls),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(characters, key = ResidentCharacter::id) { character ->
                val messages = remember(character.id, characterControlRevision) {
                    WorldEngine.proactiveMessages(context, character.id)
                }
                val posts = remember(character.id, characterControlRevision) {
                    WorldEngine.proactivePosts(context, character.id)
                }
                val characterNotifications = remember(character.id, characterControlRevision) {
                    WorldEngine.characterNotifications(context, character)
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(character.name, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = messages,
                                onClick = {
                                    WorldEngine.setProactiveMessages(
                                        context,
                                        character.id,
                                        !messages,
                                    )
                                    characterControlRevision++
                                },
                                label = {
                                    Text(stringResource(R.string.allow_proactive_messages))
                                },
                            )
                            FilterChip(
                                selected = posts,
                                onClick = {
                                    WorldEngine.setProactivePosts(context, character.id, !posts)
                                    characterControlRevision++
                                },
                                label = {
                                    Text(stringResource(R.string.allow_proactive_posts))
                                },
                            )
                            FilterChip(
                                selected = characterNotifications,
                                onClick = {
                                    WorldEngine.setCharacterNotifications(
                                        context,
                                        character.id,
                                        !characterNotifications,
                                    )
                                    characterControlRevision++
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.allow_character_notifications,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        if (taskPaused) {
            item {
                StatusCard(
                    stringResource(
                        R.string.world_task_paused,
                        WorldEngine.lastFailure(context),
                    ),
                )
                Button(
                    onClick = {
                        WorldEngine.resumeTasks(context)
                        taskPaused = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.resume_world_tasks))
                }
            }
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
    var worldConfig by remember { mutableStateOf(store.loadTask(ProviderTask.World)) }
    var memoryConfig by remember { mutableStateOf(store.loadTask(ProviderTask.Memory)) }
    var visionConfig by remember { mutableStateOf(store.loadVision()) }
    var profile by rememberSaveable { mutableStateOf(ProviderTask.Chat.key) }
    val editingFallback = profile.endsWith(":fallback")
    val task = when (profile.substringBefore(':')) {
        "text" -> ProviderTask.Chat
        "vision" -> ProviderTask.Vision
        else -> ProviderTask.entries.firstOrNull { it.key == profile.substringBefore(':') }
            ?: ProviderTask.Chat
    }
    val primaryInitial = when (task) {
        ProviderTask.Chat -> textConfig
        ProviderTask.World -> worldConfig ?: textConfig
        ProviderTask.Memory -> memoryConfig ?: textConfig
        ProviderTask.Vision -> visionConfig ?: textConfig
    }
    val initial = if (editingFallback) {
        store.loadFallback(task) ?: primaryInitial.copy(
            apiKey = "",
            extraHeaders = "",
            capabilities = ProviderCapabilities(),
            fallback = null,
        )
    } else {
        primaryInitial
    }
    var presetName by rememberSaveable { mutableStateOf(initial.preset.name) }
    var baseUrl by rememberSaveable { mutableStateOf(initial.baseUrl) }
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var apiKey by rememberSaveable { mutableStateOf(initial.apiKey) }
    var extraHeaders by rememberSaveable { mutableStateOf(initial.extraHeaders) }
    var capabilities by remember { mutableStateOf(initial.capabilities) }
    var testing by remember { mutableStateOf(false) }
    var testingCapabilities by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val preset = ProviderPreset.valueOf(presetName)
    val requiredFields = stringResource(R.string.provider_required_fields)
    val savedMessage = stringResource(R.string.provider_saved)
    val connectionOk = stringResource(R.string.provider_connection_ok)
    val connectionFailed = stringResource(R.string.provider_connection_failed)
    val visionRemoved = stringResource(R.string.vision_provider_removed)
    val capabilitiesSaved = stringResource(R.string.capabilities_saved)

    fun showProfile(nextTask: ProviderTask, config: ProviderConfig, fallback: Boolean = false) {
        profile = nextTask.key + if (fallback) ":fallback" else ""
        presetName = config.preset.name
        baseUrl = config.baseUrl
        model = config.model
        apiKey = config.apiKey
        extraHeaders = config.extraHeaders
        capabilities = config.capabilities
        status = null
    }

    fun showFallback(nextTask: ProviderTask) {
        val base = store.loadFor(nextTask).copy(
            apiKey = "",
            extraHeaders = "",
            capabilities = ProviderCapabilities(),
            fallback = null,
        )
        showProfile(nextTask, store.loadFallback(nextTask) ?: base, fallback = true)
    }

    fun currentConfig() = ProviderConfig(
        preset = preset,
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        apiKey = apiKey.trim(),
        extraHeaders = extraHeaders.trim(),
        capabilities = capabilities,
    )

    fun rememberSavedConfig(savedTask: ProviderTask, config: ProviderConfig) {
        when (savedTask) {
            ProviderTask.Chat -> textConfig = config
            ProviderTask.World -> worldConfig = config
            ProviderTask.Memory -> memoryConfig = config
            ProviderTask.Vision -> visionConfig = config
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
            TextButton(onClick = onBack) {
                Text("‹  ${stringResource(R.string.back)}")
            }
            Text(
                stringResource(R.string.provider_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (editingFallback) {
                    stringResource(R.string.provider_fallback_summary)
                } else if (task == ProviderTask.Vision) {
                    stringResource(R.string.vision_provider_summary)
                } else {
                    stringResource(R.string.provider_privacy)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = task == ProviderTask.Chat,
                    onClick = { showProfile(ProviderTask.Chat, textConfig) },
                    label = { Text(stringResource(R.string.general_provider)) },
                )
                FilterChip(
                    selected = task == ProviderTask.World,
                    onClick = { showProfile(ProviderTask.World, worldConfig ?: textConfig) },
                    label = { Text(stringResource(R.string.world_provider)) },
                )
                FilterChip(
                    selected = task == ProviderTask.Memory,
                    onClick = { showProfile(ProviderTask.Memory, memoryConfig ?: textConfig) },
                    label = { Text(stringResource(R.string.memory_provider)) },
                )
                FilterChip(
                    selected = task == ProviderTask.Vision,
                    onClick = { showProfile(ProviderTask.Vision, visionConfig ?: textConfig) },
                    label = { Text(stringResource(R.string.vision_provider)) },
                )
                FilterChip(
                    selected = editingFallback,
                    onClick = { showFallback(task) },
                    label = { Text(stringResource(R.string.provider_fallback)) },
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
                            if (editingFallback) {
                                store.saveFallback(task, config)
                            } else {
                                store.saveTask(task, config)
                                rememberSavedConfig(task, config)
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
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.capability_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    ProviderCapability.entries.forEach { capability ->
                        val failure = capabilities.failures[capability]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(capability.labelRes))
                            Text(
                                when {
                                    capability in capabilities.supported ->
                                        stringResource(R.string.capability_passed)
                                    failure != null ->
                                        stringResource(R.string.capability_failed, failure)
                                    else -> stringResource(R.string.capability_unverified)
                                },
                                color = if (capability in capabilities.supported) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val config = currentConfig()
                            if (!config.isValid()) {
                                status = requiredFields
                            } else {
                                val testedTask = task
                                testingCapabilities = true
                                status = null
                                ProviderCapabilityTester.test(config) { result ->
                                    testingCapabilities = false
                                    result.fold(
                                        onSuccess = { results ->
                                            val updated = config.copy(
                                                capabilities = config.capabilities.withResults(results),
                                            )
                                            if (task == testedTask) {
                                                capabilities = updated.capabilities
                                            }
                                            if (editingFallback) {
                                                store.saveFallback(testedTask, updated)
                                            } else {
                                                store.saveTask(testedTask, updated)
                                                rememberSavedConfig(testedTask, updated)
                                            }
                                            status = capabilitiesSaved
                                        },
                                        onFailure = {
                                            status = it.message.orEmpty()
                                        },
                                    )
                                }
                            }
                        },
                        enabled = !testingCapabilities,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (testingCapabilities) {
                                stringResource(R.string.testing_capabilities)
                            } else {
                                stringResource(R.string.test_capabilities)
                            },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.capability_override))
                            Text(
                                stringResource(R.string.capability_override_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = capabilities.manualOverride,
                            onCheckedChange = {
                                capabilities = capabilities.copy(manualOverride = it)
                            },
                        )
                    }
                }
            }
        }
        status?.let { message ->
            item { StatusCard(message) }
        }
        val configured = if (editingFallback) {
            store.loadFallback(task)
        } else {
            when (task) {
                ProviderTask.Chat -> null
                ProviderTask.World -> worldConfig
                ProviderTask.Memory -> memoryConfig
                ProviderTask.Vision -> visionConfig
            }
        }
        if (configured != null) {
            item {
                TextButton(
                    onClick = {
                        if (editingFallback) {
                            store.clearFallback(task)
                        } else {
                            store.clearTask(task)
                            when (task) {
                                ProviderTask.World -> worldConfig = null
                                ProviderTask.Memory -> memoryConfig = null
                                ProviderTask.Vision -> visionConfig = null
                                ProviderTask.Chat -> Unit
                            }
                        }
                        status = if (editingFallback) {
                            savedMessage
                        } else if (task == ProviderTask.Vision) {
                            visionRemoved
                        } else {
                            savedMessage
                        }
                    },
                ) {
                    Text(
                        if (editingFallback) {
                            stringResource(R.string.remove_fallback_provider)
                        } else if (task == ProviderTask.Vision) {
                            stringResource(R.string.remove_vision_provider)
                        } else {
                            stringResource(R.string.remove_task_provider)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenBackups: () -> Unit,
) {
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var showBackupReminder by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val checker = remember { GitHubUpdateChecker() }
    val networkError = stringResource(R.string.network_error)

    fun openDownload(release: GitHubRelease) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, release.downloadUrl.toUri()),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                R.string.open_link_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    if (showBackupReminder) {
        val release = (state as? UpdateState.Available)?.release
        if (release != null) {
            AlertDialog(
                onDismissRequest = { showBackupReminder = false },
                title = { Text(stringResource(R.string.update_backup_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.update_backup_body))
                        Text(
                            stringResource(R.string.backup_privacy_warning),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBackupReminder = false
                            openDownload(release)
                        },
                    ) {
                        Text(stringResource(R.string.update_backup_continue))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showBackupReminder = false
                                onOpenBackups()
                            },
                        ) {
                            Text(stringResource(R.string.update_backup_now))
                        }
                        TextButton(onClick = { showBackupReminder = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                },
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
                            snapshot.release.checksumSha256?.let { checksum ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.apk_checksum, checksum),
                                    style = MaterialTheme.typography.labelSmall,
                                )
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
                        onClick = { showBackupReminder = true },
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
