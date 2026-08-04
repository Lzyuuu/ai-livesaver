package io.github.lzyuuu.ailivesaver

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

internal const val Y_POST_KIND = "y"

internal fun yHandle(name: String): String {
    val slug = name.removeSuffix(" · NPC").trim().lowercase()
        .replace(Regex("[^a-z0-9_]+"), "")
    return "@" + slug.ifBlank { "user" }
}

/** Deterministic offline short post when Provider is missing or returns empty output. */
internal fun ySimulatedGeneratedPost(actorName: String): String {
    val display = actorName.removeSuffix(" · NPC").trim().ifBlank { "Root" }
    val templates = listOf(
        "$display checks in — still running Fancy OS.",
        "$display has thoughts. More later.",
        "Quick note from $display: the feed is alive.",
        "$display: keeping the signal short today.",
    )
    val index = display.lowercase().fold(0) { acc, c -> acc + c.code } % templates.size
    return templates[index]
}

internal fun yResolvedGenerateBody(providerText: String?, actorName: String): String {
    val trimmed = providerText?.trim().orEmpty()
    if (trimmed.isNotEmpty() && trimmed != "...") return trimmed
    return ySimulatedGeneratedPost(actorName)
}

/** LazyColumn item identity must include revision so comment writes recompose cards. */
internal fun yFeedItemKey(revision: Int, postId: Long): Pair<Int, Long> = revision to postId

internal fun yRelativeTimeLabel(
    timestamp: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val minutes = ((now - timestamp).coerceAtLeast(0) / 60_000L).toInt()
    return if (minutes == 1) "1 minute ago" else "$minutes minutes ago"
}

/** Light social chrome aligned with validated Ustagram / live v4.47 Y. */
internal object YFeedPalette {
    val pageBackground = FancyCream
    val chromeBackground = FancyCream
    val cardBackground = Color(0xFFFFFCF5)
    val cardBorder = Color(0xFFD6D2C6)
    val bodyText = Color(0xFF1C1C1C)
    val toolbarIcon = Color(0xFF141414)
    val title = Color.Black
    val metadata = Color(0xFF6F7468)
    val emptyState = Color(0xFF8A8F82)
    val generateAccent = FancyGold
    val menuSurface = Color(0xFFFFFCF5)
    val statusBarColor = FancyCream
    const val usesLightSystemBarIcons = true
}

private val YPageBg = YFeedPalette.pageBackground
private val YChromeBg = YFeedPalette.chromeBackground
private val YCardBg = YFeedPalette.cardBackground
private val YCardBorder = YFeedPalette.cardBorder
private val YBodyText = YFeedPalette.bodyText
private val YMuted = YFeedPalette.metadata
private val YEmpty = YFeedPalette.emptyState
private val YDanger = Color(0xFFE5738A)
private val YIcon = YFeedPalette.toolbarIcon
private val YTitleColor = YFeedPalette.title
private val YToolbarHeight = 56.dp
private val YToolbarIcon = 22.dp
private val YTitleSize = 28.sp

/** Four-point sparkles cluster matching fancy-ai Generate glyph (no new dependency). */
internal val YSparklesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "YSparkles",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        fun fourPoint(cx: Float, cy: Float, outer: Float, inner: Float) {
            path(fill = SolidColor(Color.Black)) {
                moveTo(cx, cy - outer)
                lineTo(cx + inner, cy - inner)
                lineTo(cx + outer, cy)
                lineTo(cx + inner, cy + inner)
                lineTo(cx, cy + outer)
                lineTo(cx - inner, cy + inner)
                lineTo(cx - outer, cy)
                lineTo(cx - inner, cy - inner)
                close()
            }
        }
        // AutoAwesome-like cluster: large primary + two satellites
        fourPoint(9.8f, 12.2f, 7.4f, 2.55f)
        fourPoint(17.8f, 6.2f, 3.4f, 1.15f)
        fourPoint(18.2f, 16.6f, 2.7f, 0.95f)
    }.build()
}

internal val YFlagIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "YFlag",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 2f)
            verticalLineTo(22f)
            horizontalLineTo(8f)
            verticalLineTo(14f)
            horizontalLineTo(17f)
            lineTo(15f, 10f)
            lineTo(17f, 6f)
            horizontalLineTo(8f)
            verticalLineTo(2f)
            close()
        }
    }.build()
}

/** Sliders / equalizer glyph for Edit prompt (matches reference, no extended icons dep). */
internal val YSlidersIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "YSliders",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // left track + knob
            moveTo(5f, 4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(16f)
            horizontalLineToRelative(-2f)
            close()
            moveTo(3f, 9f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-6f)
            close()
            // middle
            moveTo(11f, 4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(16f)
            horizontalLineToRelative(-2f)
            close()
            moveTo(9f, 14f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-6f)
            close()
            // right
            moveTo(17f, 4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(16f)
            horizontalLineToRelative(-2f)
            close()
            moveTo(15f, 7f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-6f)
            close()
        }
    }.build()
}

@Composable
private fun YLightSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val prevLightStatus = controller?.isAppearanceLightStatusBars
        val prevLightNav = controller?.isAppearanceLightNavigationBars
        val prevStatusColor = window?.statusBarColor
        val prevNavColor = window?.navigationBarColor
        val prevNavContrast = if (Build.VERSION.SDK_INT >= 29) {
            window?.isNavigationBarContrastEnforced
        } else {
            null
        }
        val prevSystemBarsBehavior = controller?.systemBarsBehavior
        if (window != null && controller != null) {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.statusBarColor = AndroidColor.parseColor("#E8E4D9")
            window.navigationBarColor = AndroidColor.parseColor("#E8E4D9")
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
            }
        }
        onDispose {
            prevLightStatus?.let { controller?.isAppearanceLightStatusBars = it }
            prevLightNav?.let { controller?.isAppearanceLightNavigationBars = it }
            prevStatusColor?.let { window?.statusBarColor = it }
            prevNavColor?.let { window?.navigationBarColor = it }
            prevSystemBarsBehavior?.let { controller?.systemBarsBehavior = it }
            if (Build.VERSION.SDK_INT >= 29 && prevNavContrast != null) {
                window?.isNavigationBarContrastEnforced = prevNavContrast
            }
        }
    }
}

@Composable
internal fun YScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    openPostId: Long?,
    onOpenPostConsumed: () -> Unit,
    onChanged: () -> Unit,
    onResumeQueuedResponses: () -> Unit,
    onStartWorld: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val yGenerating = stringResource(R.string.y_generating)
    val yGenerateFailed = stringResource(R.string.y_generate_failed)
    val identity = remember(revision) { store.identity() }
    val character = remember(revision) { store.primaryCharacter() }
    val posts = remember(revision) { store.posts(Y_POST_KIND) }
    val characters = remember(revision) { store.characters(includeDeparted = true) }
    val queuedResponseCount = remember(revision) { store.socialResponseQueueCount() }
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    var promptEditorOpen by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var audience by rememberSaveable { mutableStateOf("world") }
    var selectedCharacterIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val promptPrefs = remember(context) { context.getSharedPreferences("social_y", Context.MODE_PRIVATE) }
    var generatePrompt by rememberSaveable {
        mutableStateOf(promptPrefs.getString("generate_prompt", "Write one short public broadcast under 60 Chinese characters. Sound like a microblog.").orEmpty())
    }
    var menuOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var highlightPostId by rememberSaveable { mutableStateOf<Long?>(null) }

    YLightSystemBars()

    LaunchedEffect(openPostId, posts) {
        val postId = openPostId ?: return@LaunchedEffect
        if (posts.any { it.id == postId }) highlightPostId = postId
        onOpenPostConsumed()
    }

    BackHandler(composerOpen || promptEditorOpen) {
        when {
            composerOpen -> composerOpen = false
            promptEditorOpen -> promptEditorOpen = false
        }
    }

    fun publishDraft() {
        val body = draft.trim()
        if (body.isBlank() || character == null) return
        val postId = store.createPost(
            kind = Y_POST_KIND,
            authorName = store.userName(),
            title = "",
            body = body,
            authorKind = "user",
            audience = audience,
            audienceCharacterIds = selectedCharacterIds.joinToString(","),
            worldEventKind = Y_POST_KIND,
        )
        draft = ""
        composerOpen = false
        highlightPostId = postId
        onChanged()
        WorldEngine.respondToPost(
            context,
            postId,
            Y_POST_KIND,
            body,
            "world",
            "",
        ) { onChanged() }
    }

    fun generatePost() {
        if (character == null || generating) return
        generating = true
        status = yGenerating
        val actor = store.characters(includeDeparted = false).firstOrNull() ?: character
        val config = ProviderStore(context).loadFor(ProviderTask.World)
        ProviderTextClient.completeStructured(
            config,
            "You are ${actor.name}. ${actor.persona}",
            generatePrompt.trim().ifBlank {
                "Write one short public broadcast under 60 Chinese characters."
            },
        ) { result ->
            result.onSuccess { response ->
                val body = yResolvedGenerateBody(response.text, actor.name)
                store.createPost(
                    kind = Y_POST_KIND,
                    authorName = actor.name,
                    title = "",
                    body = body,
                    authorKind = "resident",
                    authorCharacterId = actor.id,
                    providerName = response.config.preset.displayName,
                    modelName = response.config.model,
                    worldEventKind = Y_POST_KIND,
                )
                status = null
            }.onFailure {
                // Provider 失败不能产生未标识的 AI 帖子；保留离线历史并展示失败状态。
                status = yGenerateFailed
            }
            generating = false
            onChanged()
        }
    }

    fun deleteAllPosts() {
        posts.forEach { post ->
            if (post.authorKind == "user") store.deleteUserPost(post.id)
            else store.deleteAiPost(post.id)
        }
        highlightPostId = null
        onChanged()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YPageBg)
            .testTag("y-screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(YChromeBg)
                    .statusBarsPadding(),
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(YToolbarHeight)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("y-back"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = YIcon,
                        modifier = Modifier.size(YToolbarIcon),
                    )
                }
                Text(
                    "Y",
                    color = YTitleColor,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = YTitleSize,
                    lineHeight = YTitleSize,
                    modifier = Modifier.padding(start = 0.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { composerOpen = true },
                    enabled = character != null,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("y-compose"),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.compose_y),
                        tint = YIcon,
                        modifier = Modifier.size(YToolbarIcon),
                    )
                }
                IconButton(
                    onClick = { generatePost() },
                    enabled = character != null && !generating,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("y-generate"),
                ) {
                    Icon(
                        YSparklesIcon,
                        contentDescription = stringResource(R.string.y_generate),
                        tint = if (character != null && !generating) {
                            YFeedPalette.generateAccent
                        } else {
                            YMuted.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(YToolbarIcon),
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("y-more"),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_actions),
                            tint = YIcon,
                            modifier = Modifier.size(YToolbarIcon),
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = YFeedPalette.menuSurface,
                        modifier = Modifier.testTag("y-more-menu"),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.y_edit_prompt), color = YBodyText)
                            },
                            leadingIcon = {
                                Icon(YSlidersIcon, contentDescription = null, tint = YBodyText)
                            },
                            onClick = {
                                menuOpen = false
                                promptEditorOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.y_delete_all), color = YDanger)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = YDanger)
                            },
                            onClick = {
                                menuOpen = false
                                deleteAllPosts()
                            },
                        )
                    }
                }
            }
            }

            Text(
                stringResource(R.string.y_ai_disclaimer),
                color = FancyGold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(YPageBg)
                    .padding(horizontal = 28.dp, vertical = 16.dp)
                    .testTag("y-disclaimer"),
            )

            if (queuedResponseCount > 0) {
                TextButton(
                    onClick = onResumeQueuedResponses,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.social_responses_waiting, queuedResponseCount),
                        color = FancyGold,
                    )
                }
            }
            status?.let {
                Text(
                    it,
                    color = FancyGoldDim,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("y-status"),
                )
            }

            when {
                character == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.social_requires_world), color = YBodyText)
                        TextButton(onClick = onStartWorld) {
                            Text(stringResource(R.string.begin_world_building), color = FancyGold)
                        }
                    }
                }
                posts.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp)
                            .testTag("y-empty"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.no_y_yet),
                            color = YEmpty,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            fontFamily = FontFamily.SansSerif,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("y-feed"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(posts, key = { post -> yFeedItemKey(revision, post.id) }) { post ->
                            YPostCard(
                                post = post,
                                revision = revision,
                                authorAvatarPath = when (post.authorKind) {
                                    "user" -> identity.avatarPath
                                    else -> post.authorCharacterId?.let { characterId ->
                                        characters.firstOrNull { it.id == characterId }
                                    }?.let { CharacterCardV2.profileFields(it).avatarPath }
                                        ?.takeIf { it.isNotBlank() }
                                },
                                userName = store.userName(),
                                comments = store.comments(post.id),
                                highlighted = post.id == highlightPostId,
                                onReply = { body, parentId, replyToName ->
                                    store.addComment(
                                        post.id,
                                        body,
                                        parentId = parentId,
                                        replyToName = replyToName,
                                    )
                                    onChanged()
                                    if (post.aiResponsesEnabled) {
                                        WorldEngine.respondToPost(
                                            context,
                                            post.id,
                                            Y_POST_KIND,
                                            body,
                                            post.audience,
                                            post.audienceCharacterIds,
                                        ) { onChanged() }
                                    }
                                },
                                onDeleteComment = { commentId ->
                                    store.deleteComment(commentId)
                                    onChanged()
                                },
                                onDeletePost = {
                                    if (post.authorKind == "user") store.deleteUserPost(post.id)
                                    else store.deleteAiPost(post.id)
                                    if (highlightPostId == post.id) highlightPostId = null
                                    onChanged()
                                },
                                onHidePost = {
                                    store.hideAiPost(post.id)
                                    onChanged()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Keep Scaffold insets available for callers that still pass padding; unused for chrome.
    @Suppress("UNUSED_VARIABLE")
    val unusedPadding = contentPadding

    if (composerOpen) {
        YComposeDialog(
            draft = draft,
            audience = audience,
            selectedCharacterIds = selectedCharacterIds,
            characters = characters,
            onDraftChange = { draft = it },
            onAudienceChange = { audience = it },
            onSelectedCharacterIdsChange = { selectedCharacterIds = it },
            onCancel = { composerOpen = false },
            onPost = { publishDraft() },
        )
    }
    if (promptEditorOpen) {
        YPromptDialog(
            prompt = generatePrompt,
            onPromptChange = { generatePrompt = it },
            onDismiss = {
                promptPrefs.edit().putString("generate_prompt", generatePrompt.trim()).apply()
                promptEditorOpen = false
            },
        )
    }
}

@Composable
private fun YComposeDialog(
    draft: String,
    audience: String,
    selectedCharacterIds: List<Long>,
    characters: List<ResidentCharacter>,
    onAudienceChange: (String) -> Unit,
    onSelectedCharacterIdsChange: (List<Long>) -> Unit,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onPost: () -> Unit,
) {
    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(YFeedPalette.cardBackground)
                .border(1.dp, YCardBorder, RoundedCornerShape(26.dp))
                .padding(20.dp)
                .testTag("y-compose-dialog"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.compose_y),
                color = YBodyText,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Text(stringResource(R.string.audience), color = YBodyText)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("world" to R.string.audience_world, "selected" to R.string.audience_selected).forEach { (value, label) ->
                    FilterChip(selected = audience == value, onClick = { onAudienceChange(value) }, label = { Text(stringResource(label)) })
                }
            }
            if (audience == "selected") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                characters.forEach { c -> FilterChip(selected = c.id in selectedCharacterIds, onClick = { onSelectedCharacterIdsChange(if (c.id in selectedCharacterIds) selectedCharacterIds - c.id else selectedCharacterIds + c.id) }, label = { Text(c.name) }) }
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = TextStyle(color = YBodyText, fontSize = 16.sp),
                cursorBrush = SolidColor(FancyGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp)
                    .border(1.dp, YCardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
                    .testTag("y-composer-input"),
                decorationBox = { inner ->
                    if (draft.isBlank()) {
                        Text(stringResource(R.string.y_compose_hint), color = YMuted)
                    }
                    inner()
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.y_cancel), color = FancyGold)
                }
                TextButton(
                    onClick = onPost,
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("y-publish"),
                ) {
                    Text(
                        stringResource(R.string.y_post_action),
                        color = if (draft.isNotBlank()) YBodyText else YMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun YPromptDialog(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(YFeedPalette.cardBackground)
                .border(1.dp, YCardBorder, RoundedCornerShape(24.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.y_edit_prompt),
                color = YBodyText,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
            )
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                textStyle = TextStyle(color = YBodyText, fontSize = 15.sp),
                cursorBrush = SolidColor(FancyGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .border(1.dp, FancyGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.save_changes), color = FancyGold)
            }
        }
    }
}

@Composable
private fun YPostCard(
    post: SocialPost,
    revision: Int,
    authorAvatarPath: String?,
    userName: String,
    comments: List<SocialComment>,
    highlighted: Boolean,
    onReply: (String, Long?, String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onDeletePost: () -> Unit,
    onHidePost: () -> Unit,
) {
    var reply by rememberSaveable(post.id) { mutableStateOf("") }
    var replyTo by remember(post.id) { mutableStateOf<SocialComment?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val displayName = post.authorName.removeSuffix(" · NPC")
    val threaded = remember(revision, post.id, comments) { threadedComments(comments) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(YCardBg)
            .border(1.dp, YCardBorder, RoundedCornerShape(20.dp))
            .then(
                if (highlighted) {
                    Modifier.border(1.dp, FancyGold.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .padding(14.dp)
            .testTag("y-feed-card-${post.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(displayName.take(1).uppercase(), 40.dp, authorAvatarPath)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, color = YBodyText, fontWeight = FontWeight.Bold)
                Text(
                    "${yHandle(displayName)} · ${yRelativeTimeLabel(post.createdAt)}",
                    color = YMuted,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = onDeletePost, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_post),
                    tint = YMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        tint = YMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (post.authorKind != "user") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hide_ai_post)) },
                            onClick = {
                                menuOpen = false
                                onHidePost()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_post)) },
                        onClick = {
                            menuOpen = false
                            onDeletePost()
                        },
                    )
                }
            }
        }

        Text(post.body, color = YBodyText, fontSize = 16.sp, lineHeight = 22.sp)

        threaded.forEach { (comment, depth) ->
            YInlineReply(
                comment = comment,
                depth = depth,
                highlightHandle = comment.authorName.removeSuffix(" · NPC")
                    .equals(userName, ignoreCase = true),
                onDelete = { onDeleteComment(comment.id) },
                onTap = { replyTo = comment },
            )
        }

        replyTo?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reply_to_member, it.authorName.removeSuffix(" · NPC")),
                    color = FancyGold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cancel_reply),
                    tint = YMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { replyTo = null },
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicTextField(
                value = reply,
                onValueChange = { reply = it },
                textStyle = TextStyle(color = YBodyText, fontSize = 15.sp),
                cursorBrush = SolidColor(FancyGold),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .background(YCardBg, RoundedCornerShape(24.dp))
                    .border(1.dp, FancyGold.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
                    .testTag("y-reply-input-${post.id}"),
                decorationBox = { inner ->
                    if (reply.isBlank()) {
                        Text(stringResource(R.string.y_reply_hint), color = YMuted)
                    }
                    inner()
                },
            )
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.reply),
                tint = FancyGold,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(enabled = reply.isNotBlank()) {
                        val body = reply.trim()
                        if (body.isBlank()) return@clickable
                        onReply(
                            body,
                            replyTo?.id,
                            replyTo?.authorName.orEmpty().removeSuffix(" · NPC"),
                        )
                        reply = ""
                        replyTo = null
                    }
                    .testTag("y-reply-send-${post.id}"),
            )
        }
    }
}

@Composable
private fun YInlineReply(
    comment: SocialComment,
    depth: Int,
    highlightHandle: Boolean,
    onDelete: () -> Unit,
    onTap: () -> Unit,
) {
    val displayName = comment.authorName.removeSuffix(" · NPC")
    val handle = yHandle(displayName)
    val handleColor = if (highlightHandle) FancyGold else YBodyText
    val labeled = buildAnnotatedString {
        withStyle(SpanStyle(color = handleColor, fontWeight = FontWeight.SemiBold)) {
            append(handle)
        }
        withStyle(SpanStyle(color = YBodyText)) {
            append(": ")
            append(comment.body)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth.coerceAtMost(3) * 10).dp)
            .clickable(onClick = onTap)
            .testTag("y-inline-reply-${comment.id}"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(labeled, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.delete),
            tint = YMuted,
            modifier = Modifier
                .size(15.dp)
                .clickable(onClick = onDelete),
        )
        Icon(
            YFlagIcon,
            contentDescription = null,
            tint = YMuted,
            modifier = Modifier.size(15.dp),
        )
    }
}
