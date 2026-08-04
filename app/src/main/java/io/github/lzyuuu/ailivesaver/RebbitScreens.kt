package io.github.lzyuuu.ailivesaver

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import java.io.File

/** Live v4.47 Rebbit palette — ImageMagick-sampled on emulator-5556. */
internal val RebbitStatusBar = Color(0xFF6750A4)
internal val RebbitNavBar = Color.Black
internal val RebbitChrome = Color(0xFFFAF7F2)
internal val RebbitFeed = Color(0xFFFAF7F2)
internal val RebbitCard = Color(0xFFFBF8F2)
internal val RebbitCardBorder = Color(0xFFE0DDD6)
internal val RebbitDisclaimerBg = Color(0xFFFAF7F2)
internal val RebbitInk = Color(0xFF1A1A1A)
internal val RebbitToolbarAction = Color(0xFF5F5B52)
internal val RebbitMuted = Color(0xFF8E8E93)
internal val RebbitReplyIdle = Color(0xFFD1D5DB)
internal val RebbitField = Color(0xFFFBF8F2)
internal val RebbitDestructive = Color(0xFFFF6B6B)
private val RebbitBg = RebbitChrome

internal const val DEFAULT_REBBIT_PROMPT =
    "Write one short public Rebbit forum post under 120 characters. " +
        "It should feel like a simulated community discussion starter."

internal data class RebbitSystemBarConfig(
    val statusBarColor: Color,
    val navigationBarColor: Color,
    val statusBarUsesDarkIcons: Boolean,
    val navigationBarUsesDarkIcons: Boolean,
)

internal fun rebbitSystemBarConfig(sdkInt: Int): RebbitSystemBarConfig =
    if (sdkInt >= 35) {
        RebbitSystemBarConfig(
            statusBarColor = RebbitChrome,
            navigationBarColor = RebbitChrome,
            statusBarUsesDarkIcons = true,
            navigationBarUsesDarkIcons = true,
        )
    } else {
        RebbitSystemBarConfig(
            statusBarColor = RebbitStatusBar,
            navigationBarColor = RebbitNavBar,
            statusBarUsesDarkIcons = false,
            navigationBarUsesDarkIcons = false,
        )
    }

internal fun forumPostsOrderClause(sort: String, voteScoreSubquery: String): String = when (sort) {
    "top" -> "$voteScoreSubquery DESC, social_posts.created_at DESC"
    "best" ->
        "$voteScoreSubquery DESC, " +
            "(SELECT COUNT(*) FROM social_comments WHERE post_id = social_posts.id) DESC, " +
            "social_posts.created_at DESC"
    "hot" ->
        "($voteScoreSubquery * 1.0 / " +
            "((strftime('%s', 'now') * 1000 - social_posts.created_at) / 3600000.0 + 2)) DESC, " +
            "social_posts.created_at DESC"
    else -> "social_posts.created_at DESC"
}

internal fun simulatedRebbitPostBody(
    characterName: String,
    community: String,
    seed: Long = characterName.hashCode().toLong() xor community.hashCode().toLong(),
): String {
    val starters = listOf(
        "Quick check-in from r/$community — what should we discuss next?",
        "$characterName here. Anyone else following r/$community today?",
        "Posting in r/$community: small thought, big conversation starter?",
        "r/$community feels lively. $characterName dropping a discussion opener.",
    )
    val index = (seed and 0x7FFF_FFFFL).toInt() % starters.size
    return starters[index].take(120)
}

internal fun isValidRebbitGeneratedBody(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.isNotEmpty() && trimmed != "..."
}

internal fun sanitizeRebbitGeneratedBody(
    text: String,
    characterName: String,
    community: String,
): String {
    val trimmed = text.trim()
    return if (isValidRebbitGeneratedBody(trimmed)) {
        trimmed
    } else {
        simulatedRebbitPostBody(characterName, community)
    }
}

/** Fancy-style sparkles: large 4-point left + two smaller stacked right. No extra dependency. */
internal val RebbitSparklesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RebbitSparkles",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        fun sparkle(cx: Float, cy: Float, outer: Float, waist: Float) {
            path(fill = SolidColor(Color.Black)) {
                moveTo(cx, cy - outer)
                lineTo(cx + waist, cy - waist)
                lineTo(cx + outer, cy)
                lineTo(cx + waist, cy + waist)
                lineTo(cx, cy + outer)
                lineTo(cx - waist, cy + waist)
                lineTo(cx - outer, cy)
                lineTo(cx - waist, cy - waist)
                close()
            }
        }
        sparkle(9.2f, 12f, 7.2f, 1.55f)
        sparkle(17.4f, 7.2f, 3.4f, 0.75f)
        sparkle(17.8f, 16.6f, 2.8f, 0.65f)
    }.build()
}

/** Report/flag glyph matching Fancy Rebbit reply actions. */
private val RebbitFlagIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RebbitFlag",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 3f)
            lineTo(8f, 3f)
            lineTo(8f, 21f)
            lineTo(6f, 21f)
            close()
            moveTo(8f, 4.5f)
            lineTo(18.5f, 4.5f)
            lineTo(16.2f, 8.2f)
            lineTo(18.5f, 11.9f)
            lineTo(8f, 11.9f)
            close()
        }
    }.build()
}

/** Camera-with-plus for gallery pick row. */
private val RebbitCameraAddIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RebbitCameraAdd",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 7f)
            lineTo(7.2f, 7f)
            lineTo(8.4f, 5.2f)
            lineTo(13.2f, 5.2f)
            lineTo(14.4f, 7f)
            lineTo(18f, 7f)
            quadTo(19.6f, 7f, 19.6f, 8.6f)
            lineTo(19.6f, 17f)
            quadTo(19.6f, 18.6f, 18f, 18.6f)
            lineTo(4f, 18.6f)
            quadTo(2.4f, 18.6f, 2.4f, 17f)
            lineTo(2.4f, 8.6f)
            quadTo(2.4f, 7f, 4f, 7f)
            close()
            moveTo(11f, 9.2f)
            quadTo(13.6f, 9.2f, 13.6f, 11.8f)
            quadTo(13.6f, 14.4f, 11f, 14.4f)
            quadTo(8.4f, 14.4f, 8.4f, 11.8f)
            quadTo(8.4f, 9.2f, 11f, 9.2f)
            close()
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(18.2f, 3.2f)
            lineTo(19.4f, 3.2f)
            lineTo(19.4f, 5f)
            lineTo(21.2f, 5f)
            lineTo(21.2f, 6.2f)
            lineTo(19.4f, 6.2f)
            lineTo(19.4f, 8f)
            lineTo(18.2f, 8f)
            lineTo(18.2f, 6.2f)
            lineTo(16.4f, 6.2f)
            lineTo(16.4f, 5f)
            lineTo(18.2f, 5f)
            close()
        }
    }.build()
}

internal fun rebbitGeneratePrompt(context: android.content.Context): String =
    context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
        .getString("rebbit_generate_prompt", DEFAULT_REBBIT_PROMPT)
        ?: DEFAULT_REBBIT_PROMPT

internal fun saveRebbitGeneratePrompt(context: android.content.Context, prompt: String) {
    context.getSharedPreferences(APP_PREFERENCES, android.content.Context.MODE_PRIVATE)
        .edit()
        .putString("rebbit_generate_prompt", prompt.trim().ifBlank { DEFAULT_REBBIT_PROMPT })
        .apply()
}

internal fun rebbitRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - timestamp).coerceAtLeast(0) / 60_000L)
    return when {
        minutes < 1L -> "0 minutes ago"
        minutes < 60L -> "$minutes minutes ago"
        else -> {
            val hours = minutes / 60L
            if (hours < 24L) "$hours hours ago" else "${hours / 24L} days ago"
        }
    }
}

internal fun rebbitHandle(name: String): String =
    name.removeSuffix(" · NPC").lowercase().replace(Regex("[^a-z0-9_]"), "")

internal fun normalizeRebbitAuthorName(authorName: String): String =
    authorName
        .removeSuffix(" · NPC")
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '_' }

internal fun rebbitPostAuthorAvatarPath(
    post: SocialPost,
    userAvatarPath: String?,
    characters: List<ResidentCharacter>,
): String? {
    if (post.authorKind == "user") {
        return userAvatarPath?.takeIf { it.isNotBlank() }
    }
    val character = post.authorCharacterId?.let { characterId ->
        characters.firstOrNull { it.id == characterId }
    } ?: characters.firstOrNull { resident ->
        normalizeRebbitAuthorName(resident.name) ==
            normalizeRebbitAuthorName(post.authorName)
    }
    return character
        ?.let { CharacterCardV2.profileFields(it).avatarPath }
        ?.takeIf { it.isNotBlank() }
}

@Composable
private fun RebbitLightSystemBars() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val config = rebbitSystemBarConfig(Build.VERSION.SDK_INT)
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        val previousStatusColor = window?.statusBarColor
        val previousNavColor = window?.navigationBarColor
        window?.statusBarColor = config.statusBarColor.toArgb()
        window?.navigationBarColor = config.navigationBarColor.toArgb()
        controller?.isAppearanceLightStatusBars = config.statusBarUsesDarkIcons
        controller?.isAppearanceLightNavigationBars = config.navigationBarUsesDarkIcons
        onDispose {
            previousStatus?.let { controller?.isAppearanceLightStatusBars = it }
            previousNav?.let { controller?.isAppearanceLightNavigationBars = it }
            previousStatusColor?.let { window?.statusBarColor = it }
            previousNavColor?.let { window?.navigationBarColor = it }
        }
    }
}

@Composable
internal fun RebbitScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    openPostId: Long?,
    onOpenPostConsumed: () -> Unit,
    onChanged: () -> Unit,
    onResumeQueuedResponses: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val socialRequiresWorld = stringResource(R.string.social_requires_world)
    val rebbitGenerating = stringResource(R.string.rebbit_generating)
    val rebbitGenerateFailed = stringResource(R.string.rebbit_generate_failed)
    val identity = remember(revision) { store.identity() }
    val character = remember(revision) { store.primaryCharacter() }
    val characters = remember(revision) { store.characters(includeDeparted = true) }
    var forumSort by rememberSaveable { mutableStateOf("latest") }
    val posts = remember(revision, forumSort) { store.posts("forum", forumSort) }
    val queuedResponseCount = remember(revision) { store.socialResponseQueueCount() }
    var selectedPostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var subredditsOpen by rememberSaveable { mutableStateOf(false) }
    var editPromptOpen by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    val selectedPost = posts.firstOrNull { it.id == selectedPostId }

    RebbitLightSystemBars()

    LaunchedEffect(Unit) { store.ensureDefaultRebbitSubreddits() }
    LaunchedEffect(openPostId, posts) {
        val postId = openPostId ?: return@LaunchedEffect
        if (posts.any { it.id == postId }) selectedPostId = postId
        onOpenPostConsumed()
    }

    BackHandler(selectedPost != null) { selectedPostId = null }
    BackHandler(selectedPost == null && !composerOpen && !subredditsOpen && !editPromptOpen) {
        onBack()
    }
    BackHandler(composerOpen) { composerOpen = false }
    BackHandler(subredditsOpen) { subredditsOpen = false }
    BackHandler(editPromptOpen) { editPromptOpen = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RebbitBg)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .testTag("rebbit-screen"),
    ) {
        RebbitTopBar(
            onBack = {
                when {
                    selectedPost != null -> selectedPostId = null
                    else -> onBack()
                }
            },
            onCompose = { composerOpen = true },
            onGenerate = {
                if (generating) return@RebbitTopBar
                if (character == null) {
                    status = socialRequiresWorld
                    return@RebbitTopBar
                }
                generating = true
                status = rebbitGenerating
                WorldEngine.generateRebbitPost(
                    context,
                    rebbitGeneratePrompt(context),
                ) { ok ->
                    generating = false
                    status = if (ok) null else rebbitGenerateFailed
                    onChanged()
                }
            },
            generating = generating,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onEditPrompt = {
                menuOpen = false
                editPromptOpen = true
            },
            onManageSubreddits = {
                menuOpen = false
                store.ensureDefaultRebbitSubreddits()
                subredditsOpen = true
                onChanged()
            },
            onDeleteAll = {
                menuOpen = false
                store.deleteAllForumPosts()
                selectedPostId = null
                onChanged()
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RebbitDisclaimerBg)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("rebbit-disclaimer"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.rebbit_ai_banner),
                color = RebbitInk,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("latest" to "New", "best" to "Best", "hot" to "Hot", "top" to "Top").forEach { (value, label) ->
                FilterChip(selected = forumSort == value, onClick = { forumSort = value }, label = { Text(label) }, shape = RoundedCornerShape(50))
            }
        }

        if (selectedPost != null) {
            RebbitDetail(
                post = selectedPost,
                authorAvatarPath = rebbitPostAuthorAvatarPath(
                    post = selectedPost,
                    userAvatarPath = identity.avatarPath,
                    characters = characters,
                ),
                comments = remember(revision, selectedPost.id) { store.comments(selectedPost.id) },
                status = status,
                onComment = { body ->
                    store.addComment(selectedPost.id, body, parentId = null)
                    onChanged()
                    WorldEngine.respondToPost(
                        context,
                        selectedPost.id,
                        "forum",
                        "${selectedPost.title}\n${selectedPost.body}\nReply: $body",
                        selectedPost.audience,
                        selectedPost.audienceCharacterIds,
                    ) { onChanged() }
                },
                onVote = {
                    store.voteOnPost(selectedPost.id, it)
                    onChanged()
                },
                onDelete = {
                    if (selectedPost.authorKind == "user") {
                        store.deleteUserPost(selectedPost.id)
                    } else {
                        store.deleteAiPost(selectedPost.id)
                    }
                    selectedPostId = null
                    onChanged()
                },
                onDeleteComment = { commentId ->
                    store.deleteComment(commentId)
                    onChanged()
                },
            )
        } else if (posts.isEmpty() && queuedResponseCount == 0 && status == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RebbitFeed)
                    .testTag("rebbit-empty"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.rebbit_empty),
                    color = RebbitInk,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RebbitFeed)
                    .testTag("rebbit-feed"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (queuedResponseCount > 0) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, RebbitCardBorder, RoundedCornerShape(16.dp))
                                .background(RebbitCard)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.social_responses_waiting, queuedResponseCount),
                                color = RebbitInk,
                            )
                            TextButton(onClick = onResumeQueuedResponses) {
                                Text(
                                    stringResource(R.string.continue_social_responses),
                                    color = FancyGold,
                                )
                            }
                        }
                    }
                }
                status?.let {
                    item {
                        Text(
                            it,
                            color = FancyGold,
                            modifier = Modifier
                                .padding(8.dp)
                                .testTag("rebbit-status"),
                        )
                    }
                }
                items(posts, key = SocialPost::id) { post ->
                    RebbitFeedCard(
                        post = post,
                        authorAvatarPath = rebbitPostAuthorAvatarPath(
                            post = post,
                            userAvatarPath = identity.avatarPath,
                            characters = characters,
                        ),
                        comments = remember(revision, post.id) { store.comments(post.id) },
                        onOpen = { selectedPostId = post.id },
                        onDelete = {
                            if (post.authorKind == "user") {
                                store.deleteUserPost(post.id)
                            } else {
                                store.deleteAiPost(post.id)
                            }
                            onChanged()
                        },
                        onDeleteComment = { commentId ->
                            store.deleteComment(commentId)
                            onChanged()
                        },
                        onComment = { body ->
                            store.addComment(post.id, body)
                            onChanged()
                            WorldEngine.respondToPost(
                                context,
                                post.id,
                                "forum",
                                "${post.title}\n${post.body}\nReply: $body",
                                post.audience,
                                post.audienceCharacterIds,
                            ) { onChanged() }
                        },
                    )
                }
            }
        }
    }

    if (composerOpen) {
        RebbitComposerDialog(
            onDismiss = { composerOpen = false },
            onPost = { body, attachAiImage, imagePath ->
                val subreddit = store.resolveRebbitPublishSubreddit()
                val title = body.lineSequence().firstOrNull()?.take(80).orEmpty()
                when {
                    imagePath != null -> {
                        runCatching { persistImportedImage(context, imagePath) }
                            .onSuccess { path ->
                                val postId = runCatching {
                                    store.createPost(
                                        kind = "forum",
                                        authorName = store.userName(),
                                        title = title,
                                        body = body,
                                        authorKind = "user",
                                        subreddit = subreddit,
                                        mediaPath = path,
                                    )
                                }.getOrElse { error ->
                                    store.discardUnreferencedMedia(path)
                                    throw error
                                }
                                WorldEngine.respondToPost(
                                    context,
                                    postId,
                                    "forum",
                                    body,
                                    "world",
                                    "",
                                ) { onChanged() }
                            }
                    }
                    attachAiImage -> {
                        store.createPost(
                            kind = "forum",
                            authorName = store.userName(),
                            title = title,
                            body = body,
                            authorKind = "user",
                            subreddit = subreddit,
                            mediaPrompt = body,
                        )
                        LocalDreamQueue.resume(context, onFinished = { _, _ -> onChanged() })
                    }
                    else -> {
                        val postId = store.createPost(
                            kind = "forum",
                            authorName = store.userName(),
                            title = title,
                            body = body,
                            authorKind = "user",
                            subreddit = subreddit,
                        )
                        WorldEngine.respondToPost(
                            context,
                            postId,
                            "forum",
                            body,
                            "world",
                            "",
                        ) { onChanged() }
                    }
                }
                composerOpen = false
                onChanged()
            },
        )
    }
    if (subredditsOpen) {
        RebbitSubredditsDialog(
            subreddits = store.rebbitSubreddits(),
            onAdd = {
                store.addRebbitSubreddit(it)
                onChanged()
            },
            onToggle = { name, enabled ->
                store.setRebbitSubredditEnabled(name, enabled)
                onChanged()
            },
            onAll = {
                store.setAllRebbitSubredditsEnabled(true)
                onChanged()
            },
            onNone = {
                store.setAllRebbitSubredditsEnabled(false)
                onChanged()
            },
            onDone = { subredditsOpen = false },
        )
    }
    if (editPromptOpen) {
        RebbitEditPromptDialog(
            initial = rebbitGeneratePrompt(context),
            onDismiss = { editPromptOpen = false },
            onSave = {
                saveRebbitGeneratePrompt(context, it)
                editPromptOpen = false
            },
        )
    }
}

@Composable
private fun RebbitTopBar(
    onBack: () -> Unit,
    onCompose: () -> Unit,
    onGenerate: () -> Unit,
    generating: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onEditPrompt: () -> Unit,
    onManageSubreddits: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(RebbitBg)
            .padding(horizontal = 2.dp)
            .testTag("rebbit-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("rebbit-back")) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = RebbitInk,
            )
        }
        Text(
            "Rebbit",
            color = RebbitInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCompose, modifier = Modifier.testTag("rebbit-compose")) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rebbit_compose), tint = RebbitToolbarAction)
        }
        IconButton(
            onClick = onGenerate,
            enabled = !generating,
            modifier = Modifier.testTag("rebbit-generate"),
        ) {
            Icon(
                RebbitSparklesIcon,
                contentDescription = stringResource(R.string.rebbit_generate),
                tint = RebbitToolbarAction,
            )
        }
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }, modifier = Modifier.testTag("rebbit-more")) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more), tint = RebbitToolbarAction)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
                modifier = Modifier.testTag("rebbit-more-menu"),
                containerColor = RebbitCard,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rebbit_edit_prompt), color = RebbitInk) },
                    onClick = onEditPrompt,
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = RebbitInk) },
                    colors = MenuDefaults.itemColors(
                        textColor = RebbitInk,
                        leadingIconColor = RebbitInk,
                    ),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rebbit_manage_subreddits), color = RebbitInk) },
                    onClick = onManageSubreddits,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = RebbitInk)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = RebbitInk,
                        leadingIconColor = RebbitInk,
                    ),
                )
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.rebbit_delete_all), color = RebbitDestructive)
                    },
                    onClick = onDeleteAll,
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = RebbitDestructive)
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = RebbitDestructive,
                        leadingIconColor = RebbitDestructive,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RebbitFeedCard(
    post: SocialPost,
    authorAvatarPath: String?,
    comments: List<SocialComment>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDeleteComment: (Long) -> Unit = {},
    onComment: (String) -> Unit,
) {
    var reply by rememberSaveable(post.id) { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val handle = rebbitHandle(post.authorName)
    val community = post.subreddit.ifBlank { "general" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, RebbitCardBorder, RoundedCornerShape(18.dp))
            .background(RebbitCard)
            .clickable(onClick = onOpen)
            .padding(14.dp)
            .testTag("rebbit-post-${post.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.authorName.take(1).uppercase(), 40.dp, authorAvatarPath)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    post.authorName.removeSuffix(" · NPC"),
                    color = RebbitInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Text(
                    "@$handle · r/$community · ${rebbitRelativeTime(post.createdAt)}",
                    color = RebbitMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = RebbitMuted)
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = RebbitMuted)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = RebbitCard,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_discussion), color = RebbitInk) },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                }
            }
        }
        if (post.title.isNotBlank()) {
            Text(post.title, color = RebbitInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (post.body.isNotBlank()) {
            Text(post.body, color = RebbitInk.copy(alpha = 0.92f), fontSize = 15.sp)
        }
        RebbitMedia(post)
        comments.takeLast(3).forEach { comment ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "@${rebbitHandle(comment.authorName)}: ${comment.body}",
                    color = RebbitInk.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = RebbitMuted,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDeleteComment(comment.id) },
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    RebbitFlagIcon,
                    contentDescription = null,
                    tint = RebbitMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = reply,
                onValueChange = { reply = it },
                placeholder = {
                    Text(stringResource(R.string.rebbit_add_reply), color = RebbitMuted)
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("rebbit-reply-${post.id}"),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = rebbitFieldColors(),
            )
            IconButton(
                onClick = {
                    val body = reply.trim()
                    if (body.isNotBlank()) {
                        onComment(body)
                        reply = ""
                    }
                },
                enabled = reply.isNotBlank(),
                modifier = Modifier.testTag("rebbit-send-${post.id}"),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.reply),
                    tint = FancyGold,
                )
            }
        }
    }
}

@Composable
private fun RebbitDetail(
    post: SocialPost,
    authorAvatarPath: String?,
    comments: List<SocialComment>,
    status: String?,
    onComment: (String) -> Unit,
    onVote: (Int) -> Unit,
    onDelete: () -> Unit,
    onDeleteComment: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("rebbit-detail"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RebbitFeedCard(
                post = post,
                authorAvatarPath = authorAvatarPath,
                comments = emptyList(),
                onOpen = {},
                onDelete = onDelete,
                onDeleteComment = onDeleteComment,
                onComment = onComment,
            )
        }
        item {
            VotePill(
                score = post.voteScore,
                userVote = post.userVote,
                onVote = onVote,
                modifier = Modifier.testTag("rebbit-vote"),
            )
        }
        status?.let { item { Text(it, color = FancyGold) } }
        items(threadedComments(comments), key = { it.first.id }) { (comment, depth) ->
            Row(
                modifier = Modifier
                    .padding(start = (12 + depth * 14).dp)
                    .testTag("rebbit-comment-${comment.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "@${rebbitHandle(comment.authorName)}: ${comment.body}",
                    color = RebbitInk,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = RebbitMuted,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDeleteComment(comment.id) },
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    RebbitFlagIcon,
                    contentDescription = null,
                    tint = RebbitMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun RebbitMedia(post: SocialPost) {
    val path = post.mediaPath ?: return
    val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
    )
    if (post.mediaPrompt.orEmpty().isNotBlank() || post.mediaDescription.isNotBlank()) {
        Text(
            stringResource(R.string.rebbit_image_prompt),
            color = FancyGold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun RebbitComposerDialog(
    onDismiss: () -> Unit,
    onPost: (String, Boolean, String?) -> Unit,
) {
    val context = LocalContext.current
    var body by rememberSaveable { mutableStateOf("") }
    var attachAi by rememberSaveable { mutableStateOf(false) }
    var cachedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Thread {
            val imported = runCatching { importUserImageToCache(context, uri) }
            Handler(Looper.getMainLooper()).post {
                imported.onSuccess {
                    cachedImagePath?.let(::File)?.takeIf { file ->
                        file.parentFile == context.cacheDir
                    }?.delete()
                    cachedImagePath = it
                    attachAi = false
                }
            }
        }.start()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(RebbitCard)
                .padding(18.dp)
                .testTag("rebbit-composer"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.rebbit_new_post),
                color = RebbitInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = {
                    Text(stringResource(R.string.rebbit_whats_on_mind), color = RebbitMuted)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .testTag("rebbit-composer-body"),
                colors = rebbitFieldColors(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = attachAi,
                    onCheckedChange = {
                        attachAi = it
                        if (it) cachedImagePath = null
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = FancyGold,
                        uncheckedTrackColor = RebbitMuted.copy(alpha = 0.35f),
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.rebbit_attach_ai_image), color = RebbitMuted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    .testTag("rebbit-pick-gallery"),
            ) {
                Icon(RebbitCameraAddIcon, contentDescription = null, tint = RebbitMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.rebbit_pick_gallery), color = RebbitMuted)
            }
            cachedImagePath?.let {
                Text(stringResource(R.string.image_ready_to_post), color = FancyGold, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = FancyGold)
                }
                TextButton(
                    onClick = { onPost(body.trim(), attachAi, cachedImagePath) },
                    enabled = body.isNotBlank(),
                    modifier = Modifier.testTag("rebbit-composer-post"),
                ) {
                    Text(
                        stringResource(R.string.rebbit_post),
                        color = if (body.isNotBlank()) FancyGold else RebbitMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun RebbitSubredditsDialog(
    subreddits: List<RebbitSubreddit>,
    onAdd: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onAll: () -> Unit,
    onNone: () -> Unit,
    onDone: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    val visible = subreddits.filter {
        filter.isBlank() || it.name.contains(filter, ignoreCase = true)
    }
    Dialog(onDismissRequest = onDone) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(RebbitCard)
                .padding(18.dp)
                .testTag("rebbit-subreddits"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.rebbit_subreddits_title),
                color = RebbitInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Text(
                stringResource(R.string.rebbit_subreddits_summary),
                color = RebbitMuted,
                fontSize = 13.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("r/", color = RebbitMuted)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("new_community", color = RebbitMuted) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rebbit-subreddit-add"),
                    singleLine = true,
                    colors = rebbitFieldColors(),
                )
                IconButton(onClick = {
                    onAdd(draft)
                    draft = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = FancyGold)
                }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(stringResource(R.string.rebbit_filter), color = RebbitMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rebbit-subreddit-filter"),
                singleLine = true,
                colors = rebbitFieldColors(),
            )
            Row {
                TextButton(onClick = onAll) { Text("All", color = FancyGold) }
                TextButton(onClick = onNone) { Text("None", color = FancyGold) }
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                visible.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(item.name, !item.enabled) }
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            if (item.enabled) "✓  r/${item.name}" else "○  r/${item.name}",
                            color = if (item.enabled) RebbitInk else RebbitMuted,
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone, modifier = Modifier.testTag("rebbit-subreddits-done")) {
                    Text(stringResource(R.string.done), color = FancyGold)
                }
            }
        }
    }
}

@Composable
private fun RebbitEditPromptDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(RebbitCard)
                .padding(18.dp)
                .testTag("rebbit-edit-prompt"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.rebbit_edit_prompt),
                color = RebbitInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                colors = rebbitFieldColors(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = FancyGold)
                }
                TextButton(onClick = { onSave(prompt) }) {
                    Text(stringResource(R.string.save), color = FancyGold)
                }
            }
        }
    }
}

@Composable
private fun rebbitFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RebbitInk,
    unfocusedTextColor = RebbitInk,
    focusedBorderColor = RebbitReplyIdle,
    unfocusedBorderColor = RebbitCardBorder,
    cursorColor = FancyGold,
    focusedContainerColor = RebbitField,
    unfocusedContainerColor = RebbitField,
    focusedPlaceholderColor = RebbitMuted,
    unfocusedPlaceholderColor = RebbitMuted,
)
