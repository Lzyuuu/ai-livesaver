package io.github.lzyuuu.ailivesaver

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
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
import androidx.compose.ui.window.DialogProperties
import android.app.Activity
import androidx.core.view.WindowCompat
import java.io.File

/** Live v4.47 Ustagram light chrome (cream/off-white canvas). */
internal val UstagramChrome = Color(0xFFF5F3EE)
internal val UstagramFeed = Color(0xFFF5F3EE)
internal val UstagramCard = Color(0xFFFFFCF7)
internal val UstagramCardBorder = Color(0xFFE0DDD6)
internal val UstagramDisclaimerBand = Color(0xFFF5F3EE)
internal val UstagramInk = Color(0xFF1A1A1A)
internal val UstagramMuted = Color(0xFF8E8E93)
internal val UstagramReplyIdle = Color(0xFFD1D5DB)
internal val UstagramEmptyText = Color(0xFF8E8E93)
internal val UstagramComposerSurface = Color(0xFFF3F0F8)
internal val UstagramComposerScrim = Color(0x99A8A29E)

internal fun normalizeUstagramAuthorName(authorName: String): String =
    authorName
        .removeSuffix(" · NPC")
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '_' }

internal fun socialHandle(authorName: String): String {
    val cleaned = normalizeUstagramAuthorName(authorName)
    return "@${cleaned.ifBlank { "user" }}"
}

internal fun ustagramPostAuthorAvatarPath(
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
        normalizeUstagramAuthorName(resident.name) ==
            normalizeUstagramAuthorName(post.authorName)
    }
    return character
        ?.let { CharacterCardV2.profileFields(it).avatarPath }
        ?.takeIf { it.isNotBlank() }
}

/** English relative time matching Fancy AI Ustagram reference chrome. */
internal sealed class UstagramRelativeTime {
    data object ZeroMinutes : UstagramRelativeTime()
    data class Minutes(val value: Int) : UstagramRelativeTime()
    data class Hours(val value: Int) : UstagramRelativeTime()
    data class Days(val value: Int) : UstagramRelativeTime()
}

internal fun ustagramRelativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): UstagramRelativeTime {
    val minutes = ((now - timestamp).coerceAtLeast(0) / 60_000)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> UstagramRelativeTime.ZeroMinutes
        minutes < 60 -> UstagramRelativeTime.Minutes(minutes.toInt())
        hours < 24 -> UstagramRelativeTime.Hours(hours.toInt())
        else -> UstagramRelativeTime.Days(days.coerceAtLeast(1).toInt())
    }
}

@Composable
internal fun ustagramRelativeTimeLabel(
    timestamp: Long,
    now: Long = System.currentTimeMillis(),
): String = when (val bucket = ustagramRelativeTime(timestamp, now)) {
    UstagramRelativeTime.ZeroMinutes -> stringResource(R.string.ustagram_time_zero_minutes)
    is UstagramRelativeTime.Minutes -> stringResource(R.string.ustagram_time_minutes_ago, bucket.value)
    is UstagramRelativeTime.Hours -> stringResource(R.string.ustagram_time_hours_ago, bucket.value)
    is UstagramRelativeTime.Days -> stringResource(R.string.ustagram_time_days_ago, bucket.value)
}

internal val SparklesIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Sparkles",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 2f)
        lineTo(13.2f, 7.5f)
        lineTo(18f, 6f)
        lineTo(14.5f, 10f)
        lineTo(19f, 13f)
        lineTo(13.5f, 12.2f)
        lineTo(12f, 18f)
        lineTo(10.5f, 12.2f)
        lineTo(5f, 13f)
        lineTo(9.5f, 10f)
        lineTo(6f, 6f)
        lineTo(10.8f, 7.5f)
        close()
        moveTo(18.5f, 15.5f)
        lineTo(19f, 17.5f)
        lineTo(21f, 18f)
        lineTo(19f, 18.5f)
        lineTo(18.5f, 20.5f)
        lineTo(18f, 18.5f)
        lineTo(16f, 18f)
        lineTo(18f, 17.5f)
        close()
        moveTo(5.5f, 16.5f)
        lineTo(6f, 18f)
        lineTo(7.5f, 18.5f)
        lineTo(6f, 19f)
        lineTo(5.5f, 20.5f)
        lineTo(5f, 19f)
        lineTo(3.5f, 18.5f)
        lineTo(5f, 18f)
        close()
    }.build()
}

@Composable
internal fun UstagramAppScreen(
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
    val ustagramGenerateFailed = stringResource(R.string.ustagram_generate_failed)
    val imageReadyToPost = stringResource(R.string.image_ready_to_post)
    val identity = remember(revision) { store.identity() }
    val characters = remember(revision) { store.characters(includeDeparted = true) }
    val posts = remember(revision) { store.posts("moment") }
    val queuedResponseCount = remember(revision) { store.socialResponseQueueCount() }
    var composerOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editPromptOpen by remember { mutableStateOf(false) }
    var promptDraft by rememberSaveable {
        mutableStateOf(WorldEngine.globalStyle(context))
    }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        val previousStatusColor = window?.statusBarColor
        val previousNavColor = window?.navigationBarColor
        window?.statusBarColor = UstagramChrome.toArgb()
        window?.navigationBarColor = UstagramChrome.toArgb()
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            previousStatus?.let { controller?.isAppearanceLightStatusBars = it }
            previousNav?.let { controller?.isAppearanceLightNavigationBars = it }
            previousStatusColor?.let { window?.statusBarColor = it }
            previousNavColor?.let { window?.navigationBarColor = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UstagramChrome)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .semantics { testTag = "ustagram-screen" },
    ) {
        UstagramTopBar(
            onBack = onBack,
            onCompose = { composerOpen = true },
            onGenerate = {
                if (generating) return@UstagramTopBar
                generating = true
                status = null
                if (!WorldEngine.generateMomentPost(context) { success ->
                        generating = false
                        status = if (success) {
                            null
                        } else {
                            ustagramGenerateFailed
                        }
                        if (success) onChanged()
                    }
                ) {
                    generating = false
                    status = ustagramGenerateFailed
                }
            },
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onEditPrompt = {
                menuOpen = false
                promptDraft = WorldEngine.globalStyle(context)
                editPromptOpen = true
            },
            onDeleteAll = {
                menuOpen = false
                confirmClear = true
            },
        )
        Text(
            text = stringResource(R.string.ustagram_disclaimer),
            color = FancyGold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(UstagramDisclaimerBand)
                .padding(horizontal = 28.dp, vertical = 10.dp),
        )
        if (queuedResponseCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.social_responses_waiting, queuedResponseCount),
                    color = UstagramMuted,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onResumeQueuedResponses) {
                    Text(stringResource(R.string.continue_social_responses), color = FancyGold)
                }
            }
        }
        status?.let {
            Text(
                it,
                color = Color(0xFFFF8A80),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (posts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(UstagramFeed),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.ustagram_empty_prefix))
                        withStyle(SpanStyle(color = FancyGold)) { append("✨") }
                        append(stringResource(R.string.ustagram_empty_suffix))
                    },
                    color = UstagramEmptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(UstagramFeed),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(posts, key = SocialPost::id) { post ->
                    UstagramPostCard(
                        post = post,
                        comments = remember(revision, post.id) { store.comments(post.id) },
                        authorAvatarPath = ustagramPostAuthorAvatarPath(
                            post = post,
                            userAvatarPath = identity.avatarPath,
                            characters = characters,
                        ),
                        userHandle = socialHandle(identity.name),
                        highlighted = openPostId == post.id,
                        onOpenConsumed = {
                            if (openPostId == post.id) onOpenPostConsumed()
                        },
                        onToggleLike = {
                            store.toggleReaction(post.id)
                            onChanged()
                        },
                        onReply = { body ->
                            store.addComment(post.id, body)
                            onChanged()
                            WorldEngine.respondToPost(
                                context,
                                post.id,
                                "moment",
                                body,
                                post.audience,
                                post.audienceCharacterIds,
                            ) { onChanged() }
                        },
                        onDeletePost = {
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
                        onHidePost = {
                            store.hideAiPost(post.id)
                            onChanged()
                        },
                    )
                }
            }
        }
    }

    if (composerOpen) {
        UstagramComposerDialog(
            onDismiss = { composerOpen = false },
            onPublish = { caption, attachAiImage, cachedImagePath ->
                when {
                    cachedImagePath != null -> {
                        runCatching { persistImportedImage(context, cachedImagePath) }
                            .onSuccess { path ->
                                val postId = store.createImportedMediaPost(
                                    caption,
                                    path,
                                    description = caption,
                                    audience = "world",
                                    audienceCharacterIds = "",
                                    aiResponsesEnabled = true,
                                )
                                onChanged()
                                composerOpen = false
                                WorldEngine.respondToPost(
                                    context,
                                    postId,
                                    "moment",
                                    caption,
                                    "world",
                                    "",
                                ) { onChanged() }
                            }
                            .onFailure {
                                status = it.message.orEmpty()
                            }
                    }
                    attachAiImage && caption.isNotBlank() -> {
                        val postId = store.createMediaPost(
                            body = caption,
                            prompt = caption,
                            aiResponsesEnabled = true,
                        )
                        onChanged()
                        composerOpen = false
                        LocalDreamQueue.resume(context) { _, _ -> onChanged() }
                        WorldEngine.respondToPost(
                            context,
                            postId,
                            "moment",
                            caption,
                            "world",
                            "",
                        ) { onChanged() }
                    }
                    else -> {
                        val postId = store.createPost(
                            "moment",
                            store.userName(),
                            "",
                            caption,
                            authorKind = "user",
                            aiResponsesEnabled = true,
                        )
                        onChanged()
                        composerOpen = false
                        WorldEngine.respondToPost(
                            context,
                            postId,
                            "moment",
                            caption,
                            "world",
                            "",
                        ) { onChanged() }
                    }
                }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.ustagram_delete_all_posts), color = UstagramInk) },
            text = { Text(stringResource(R.string.ustagram_delete_all_confirm), color = UstagramMuted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.clearPosts("moment")
                        confirmClear = false
                        onChanged()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.ustagram_cancel), color = FancyGold)
                }
            },
            containerColor = UstagramCard,
        )
    }

    if (editPromptOpen) {
        AlertDialog(
            onDismissRequest = { editPromptOpen = false },
            title = { Text(stringResource(R.string.ustagram_edit_prompt), color = UstagramInk) },
            text = {
                BasicTextField(
                    value = promptDraft,
                    onValueChange = { promptDraft = it },
                    textStyle = TextStyle(color = UstagramInk, fontSize = 15.sp),
                    cursorBrush = SolidColor(FancyGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .border(1.dp, UstagramReplyIdle, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        WorldEngine.setGlobalStyle(context, promptDraft)
                        editPromptOpen = false
                    },
                ) {
                    Text(stringResource(R.string.save), color = FancyGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editPromptOpen = false }) {
                    Text(stringResource(R.string.ustagram_cancel), color = FancyGold)
                }
            },
            containerColor = UstagramCard,
        )
    }
}

@Composable
private fun UstagramTopBar(
    onBack: () -> Unit,
    onCompose: () -> Unit,
    onGenerate: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onEditPrompt: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UstagramChrome)
            .height(56.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.semantics { testTag = "ustagram-back" },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = UstagramInk,
            )
        }
        Text(
            text = "Ustagram",
            color = UstagramInk,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onCompose,
            modifier = Modifier.semantics { testTag = "ustagram-compose" },
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.ustagram_compose),
                tint = UstagramInk,
            )
        }
        IconButton(
            onClick = onGenerate,
            modifier = Modifier.semantics { testTag = "ustagram-generate" },
        ) {
            Icon(
                SparklesIcon,
                contentDescription = stringResource(R.string.ustagram_generate),
                tint = FancyGold,
            )
        }
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_actions),
                    tint = UstagramInk,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
                containerColor = UstagramCard,
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.ustagram_edit_prompt),
                            color = UstagramInk,
                        )
                    },
                    onClick = onEditPrompt,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = UstagramInk,
                        )
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.ustagram_delete_all_posts),
                            color = Color(0xFFFF6B6B),
                        )
                    },
                    onClick = onDeleteAll,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun UstagramComposerDialog(
    onDismiss: () -> Unit,
    onPublish: (caption: String, attachAiImage: Boolean, cachedImagePath: String?) -> Unit,
) {
    val context = LocalContext.current
    val imageReadyToPost = stringResource(R.string.image_ready_to_post)
    var caption by rememberSaveable { mutableStateOf("") }
    var attachAiImage by rememberSaveable { mutableStateOf(true) }
    var cachedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var imageStatus by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Thread {
            val imported = runCatching { importUserImageToCache(context, uri) }
            Handler(Looper.getMainLooper()).post {
                imported.onSuccess {
                    cachedImagePath?.let(::File)?.takeIf { it.parentFile == context.cacheDir }?.delete()
                    cachedImagePath = it
                    attachAiImage = false
                    imageStatus = imageReadyToPost
                }.onFailure {
                    imageStatus = it.message.orEmpty()
                }
            }
        }.start()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UstagramComposerScrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(UstagramComposerSurface)
                    .padding(20.dp)
                    .semantics { testTag = "ustagram-composer" },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            Text(
                stringResource(R.string.ustagram_new_post),
                color = UstagramInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            BasicTextField(
                value = caption,
                onValueChange = { caption = it },
                textStyle = TextStyle(color = UstagramInk, fontSize = 15.sp),
                cursorBrush = SolidColor(FancyGold),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .border(1.dp, UstagramReplyIdle, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        if (caption.isEmpty()) {
                            Text(
                                stringResource(R.string.ustagram_prompt_or_caption),
                                color = UstagramMuted,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = "ustagram-composer-caption" },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = attachAiImage,
                    onCheckedChange = {
                        attachAiImage = it
                        if (it) cachedImagePath = null
                    },
                    modifier = Modifier.semantics { testTag = "ustagram-attach-ai-image" },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = FancyGold,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = UstagramReplyIdle,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ustagram_attach_ai_image),
                    color = UstagramInk,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    .padding(vertical = 4.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = UstagramInk,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ustagram_pick_gallery),
                    color = UstagramInk,
                )
            }
            imageStatus?.let {
                Text(it, color = UstagramMuted, style = MaterialTheme.typography.labelMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ustagram_cancel), color = FancyGold)
                }
                TextButton(
                    onClick = {
                        if (caption.isBlank() && cachedImagePath == null) return@TextButton
                        onPublish(caption.trim(), attachAiImage, cachedImagePath)
                    },
                    modifier = Modifier.semantics { testTag = "ustagram-composer-post" },
                ) {
                    Text(stringResource(R.string.ustagram_post_action), color = FancyGold)
                }
            }
        }
        }
    }
}

@Composable
private fun UstagramPostCard(
    post: SocialPost,
    comments: List<SocialComment>,
    authorAvatarPath: String?,
    userHandle: String,
    highlighted: Boolean,
    onOpenConsumed: () -> Unit,
    onToggleLike: () -> Unit,
    onReply: (String) -> Unit,
    onDeletePost: () -> Unit,
    onDeleteComment: (Long) -> Unit,
    onHidePost: () -> Unit,
) {
    var reply by rememberSaveable(post.id) { mutableStateOf("") }
    var cardMenu by remember { mutableStateOf(false) }
    var promptOpen by remember(post.id) { mutableStateOf(false) }
    val replyInteraction = remember { MutableInteractionSource() }
    val replyFocused by replyInteraction.collectIsFocusedAsState()
    if (highlighted) onOpenConsumed()
    val promptText = post.mediaPrompt?.takeIf { it.isNotBlank() }
        ?: post.mediaDescription.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, UstagramCardBorder, RoundedCornerShape(18.dp))
            .background(UstagramCard)
            .padding(14.dp)
            .semantics { testTag = "ustagram-post-card" }
            .pointerInput(post.id, post.reactedByUser) {
                detectTapGestures(
                    onDoubleTap = { onToggleLike() },
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(
                post.authorName.take(1).uppercase(),
                40.dp,
                authorAvatarPath,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    post.authorName.removeSuffix(" · NPC"),
                    color = UstagramInk,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${socialHandle(post.authorName)} · ${ustagramRelativeTimeLabel(post.createdAt)}",
                    color = UstagramMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = onDeletePost, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_post),
                    tint = UstagramMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box {
                IconButton(onClick = { cardMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        tint = UstagramMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = cardMenu, onDismissRequest = { cardMenu = false }) {
                    if (post.authorKind != "user") {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.hide_ai_post)) },
                            onClick = {
                                cardMenu = false
                                onHidePost()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_post)) },
                        onClick = {
                            cardMenu = false
                            onDeletePost()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (post.reactedByUser) {
                                    stringResource(R.string.unlike)
                                } else {
                                    stringResource(R.string.like)
                                },
                            )
                        },
                        onClick = {
                            cardMenu = false
                            onToggleLike()
                        },
                    )
                }
            }
        }
        if (post.body.isNotBlank()) {
            Text(post.body, color = UstagramInk, style = MaterialTheme.typography.bodyLarge)
        }
        UstagramPostMedia(post)
        if (promptText != null && (post.mediaPath != null || post.mediaStatus == "pending")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { promptOpen = !promptOpen }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = FancyGold,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.ustagram_image_prompt),
                    color = FancyGold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = UstagramMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (promptOpen) {
                Text(
                    promptText,
                    color = UstagramMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (post.reactionCount > 0 || post.reactedByUser) {
            AnimatedLikeButton(
                liked = post.reactedByUser,
                count = post.reactionCount,
                onToggle = onToggleLike,
            )
        }
        comments.forEach { comment ->
            val handle = socialHandle(comment.authorName)
            val own = handle.equals(userHandle, ignoreCase = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(color = if (own) FancyGold else UstagramInk),
                        ) {
                            append(handle)
                        }
                        withStyle(SpanStyle(color = UstagramInk)) {
                            append(": ${comment.body}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.delete),
                    tint = UstagramMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onDeleteComment(comment.id) },
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.ustagram_report),
                    tint = UstagramMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.imePadding(),
        ) {
            BasicTextField(
                value = reply,
                onValueChange = { reply = it },
                singleLine = true,
                textStyle = TextStyle(color = UstagramInk, fontSize = 14.sp),
                cursorBrush = SolidColor(FancyGold),
                interactionSource = replyInteraction,
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (replyFocused) FancyGold else UstagramReplyIdle,
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        if (reply.isEmpty()) {
                            Text(
                                stringResource(R.string.ustagram_add_reply),
                                color = UstagramMuted,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { testTag = "ustagram-reply-input" },
            )
            IconButton(
                onClick = {
                    val body = reply.trim()
                    if (body.isEmpty()) return@IconButton
                    onReply(body)
                    reply = ""
                },
                modifier = Modifier.semantics { testTag = "ustagram-reply-send" },
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
private fun UstagramPostMedia(post: SocialPost) {
    post.mediaPath?.let { path ->
        val bitmap = remember(path) {
            android.graphics.BitmapFactory.decodeFile(path)
        }
        bitmap?.let {
            val ratio = (it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f))
                .coerceIn(0.8f, 1.91f)
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.generated_post_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
    }
    when (post.mediaStatus) {
        "pending" -> Text(
            stringResource(R.string.local_dream_pending),
            color = FancyGold,
            style = MaterialTheme.typography.labelMedium,
        )
        "failed" -> Text(
            stringResource(R.string.local_dream_failed),
            color = Color(0xFFFF8A80),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
