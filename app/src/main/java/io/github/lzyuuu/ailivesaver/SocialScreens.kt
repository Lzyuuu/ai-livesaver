package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.util.Date
import java.io.File

private data class SocialReplyDraft(
    val body: String,
    val parentId: Long?,
    val replyToName: String,
)

private const val MAX_RENDERED_IMAGE_DIMENSION = 2048

internal fun renderedImageSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int = MAX_RENDERED_IMAGE_DIMENSION,
): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / sample > maxDimension) sample *= 2
    return sample
}

private fun decodeSocialBitmap(path: String): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = renderedImageSampleSize(bounds.outWidth, bounds.outHeight)
        },
    )
}

internal fun composeVisualPrompt(
    character: ResidentCharacter?,
    scene: String,
    globalStyle: String = "",
): String =
    listOf(
        character?.appearance,
        character?.clothing,
        globalStyle,
        scene,
    ).map { it.orEmpty().trim() }.filter(String::isNotBlank).joinToString(", ")

internal fun mediaPromptForEditing(mediaPrompt: String?, mediaDescription: String): String =
    mediaPrompt.orEmpty().ifBlank { mediaDescription }

internal fun socialComposerVisible(hasCharacter: Boolean, expanded: Boolean): Boolean =
    hasCharacter && expanded

internal fun threadedComments(
    comments: List<SocialComment>,
): List<Pair<SocialComment, Int>> {
    val children = comments.groupBy { it.parentId }
    val result = mutableListOf<Pair<SocialComment, Int>>()
    val visited = mutableSetOf<Long>()
    fun add(comment: SocialComment, depth: Int) {
        if (!visited.add(comment.id)) return
        result += comment to depth
        children[comment.id].orEmpty().forEach { add(it, depth + 1) }
    }
    children[null].orEmpty().forEach { add(it, 0) }
    comments.filterNot { it.id in visited }.forEach { add(it, 0) }
    return result
}

@Composable
internal fun SocialScreen(
    kind: String,
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    openPostId: Long?,
    onOpenPostConsumed: () -> Unit,
    onChanged: () -> Unit,
    onResumeQueuedResponses: () -> Unit,
    onStartWorld: () -> Unit,
) {
    val context = LocalContext.current
    val identity = remember(revision) { store.identity() }
    val character = remember(revision) { store.primaryCharacter() }
    val characters = remember(revision) { store.characters(includeDeparted = false) }
    var forumSort by rememberSaveable { mutableStateOf("latest") }
    val posts = remember(revision, kind, forumSort) { store.posts(kind, forumSort) }
    val queuedResponseCount = remember(revision) { store.socialResponseQueueCount() }
    var selectedPostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var npcProfile by remember { mutableStateOf<NpcProfile?>(null) }
    var previewPostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openPreviewMenu by rememberSaveable { mutableStateOf(false) }
    var composerOpen by rememberSaveable(kind) { mutableStateOf(false) }
    val showComposer = socialComposerVisible(character != null, composerOpen)
    var generationStatus by remember { mutableStateOf<String?>(null) }
    val generationFailed = stringResource(R.string.local_dream_generation_failed)
    val generationReady = stringResource(R.string.local_dream_generation_ready)
    val connecting = stringResource(R.string.local_dream_connecting)
    val visionAnalysisFailed = stringResource(R.string.vision_analysis_failed)
    val rewriting = stringResource(R.string.rewriting_ai_post)
    val rewriteFailed = stringResource(R.string.rewrite_failed)
    val selectedPost = posts.firstOrNull { it.id == selectedPostId }
    val previewPost = posts.firstOrNull { it.id == previewPostId }
    val previewVersions = remember(revision, previewPostId) {
        previewPostId?.let(store::mediaVersions).orEmpty()
    }

    LaunchedEffect(openPostId, posts) {
        val postId = openPostId ?: return@LaunchedEffect
        if (posts.any { it.id == postId }) selectedPostId = postId
        onOpenPostConsumed()
    }

    npcProfile?.let { npc ->
        NpcProfileDialog(npc = npc, onDismiss = { npcProfile = null })
    }

    fun redraw(post: SocialPost, prompt: String) {
        store.prepareRedraw(post.id, prompt)
        onChanged()
        generationStatus = connecting
        LocalDreamQueue.resume(
            context = context,
            onProgress = { postId, step, total ->
                if (postId == post.id) generationStatus = "$step / $total"
            },
            onFinished = { postId, result ->
                if (postId == post.id) {
                    generationStatus = result.fold(
                        onSuccess = { generationReady },
                        onFailure = { "$generationFailed：${it.message.orEmpty()}" },
                    )
                }
                onChanged()
            }
        )
    }

    BackHandler(selectedPost != null) { selectedPostId = null }

    previewPost?.let { post ->
        FullScreenMediaPreview(
            post = post,
            versions = previewVersions,
            menuInitiallyOpen = openPreviewMenu,
            onDismiss = {
                previewPostId = null
                openPreviewMenu = false
            },
            onRedraw = { redraw(post, it) },
            onRestoreVersion = {
                store.restoreMediaVersion(post.id, it)
                onChanged()
            },
            onDeleteVersion = {
                store.deleteMediaVersion(post.id, it)
                onChanged()
            },
        )
    }

    if (selectedPost != null) {
        PostDetailScreen(
            contentPadding = contentPadding,
            post = selectedPost,
            authorAvatarPath = selectedPost.authorKind
                .takeIf { it == "user" }
                ?.let { identity.avatarPath },
            comments = remember(revision, selectedPost.id) {
                store.comments(selectedPost.id)
            },
            versions = remember(revision, selectedPost.id) {
                store.socialPostVersions(selectedPost.id)
            },
            status = generationStatus,
            onBack = { selectedPostId = null },
            onComment = {
                store.addComment(
                    selectedPost.id,
                    it.body,
                    parentId = if (kind == "forum") it.parentId else null,
                    replyToName = it.replyToName,
                )
                onChanged()
            },
            onToggleReaction = {
                store.toggleReaction(selectedPost.id)
                onChanged()
            },
            onVote = { value ->
                store.voteOnPost(selectedPost.id, value)
                onChanged()
            },
            onUpdatePost = { title, body ->
                store.updateUserPost(selectedPost.id, title, body)
                onChanged()
            },
            onUpdateMediaDescription = { description ->
                store.updateMediaDescription(selectedPost.id, description)
                onChanged()
            },
            onDeletePost = {
                if (selectedPost.authorKind == "user") {
                    store.deleteUserPost(selectedPost.id)
                } else {
                    store.deleteAiPost(selectedPost.id)
                }
                selectedPostId = null
                onChanged()
            },
            onHidePost = {
                store.hideAiPost(selectedPost.id)
                selectedPostId = null
                onChanged()
            },
            onRewritePost = {
                generationStatus = rewriting
                if (!WorldEngine.rewriteSocialPost(context, selectedPost) { success ->
                        generationStatus = if (success) null else rewriteFailed
                        if (success) onChanged()
                    }
                ) {
                    generationStatus = rewriteFailed
                }
            },
            onRestoreVersion = {
                store.restoreAiPostVersion(selectedPost.id, it)
                onChanged()
            },
            onOpenAuthor = {
                if (selectedPost.authorKind == "npc") {
                    npcProfile = store.npcByName(selectedPost.authorName)
                }
            },
            onOpenImage = { menu ->
                previewPostId = selectedPost.id
                openPreviewMenu = menu
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 20.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        if (kind == "moment") R.string.moments_title
                        else R.string.commons_title,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                )
                if (character != null) {
                    TextButton(onClick = { composerOpen = !composerOpen }) {
                        if (!composerOpen) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(
                            stringResource(
                                if (composerOpen) {
                                    R.string.cancel
                                } else if (kind == "moment") {
                                    R.string.share_moment
                                } else {
                                    R.string.start_topic
                                },
                            ),
                        )
                    }
                }
            }
            Text(
                stringResource(
                    if (kind == "moment") R.string.moments_social_summary
                    else R.string.commons_social_summary,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (queuedResponseCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.social_responses_waiting, queuedResponseCount))
                        TextButton(
                            onClick = onResumeQueuedResponses,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.continue_social_responses))
                        }
                    }
                }
            }
        }
        if (character == null) {
            item {
                StatusCard(stringResource(R.string.social_requires_world))
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStartWorld, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.begin_world_building))
                }
            }
        } else if (showComposer) {
            item {
                PostComposer(
                    kind = kind,
                    characters = characters,
                    onPublished = { composerOpen = false },
                ) {
                        title,
                        body,
                        prompt,
                        audience,
                        audienceCharacterIds,
                        aiResponsesEnabled,
                        cachedImagePath,
                        suppliedDescription,
                        analyzeImage,
                        negativePrompt,
                        localDreamParameters,
                    ->
                    if (cachedImagePath != null) {
                        runCatching {
                            persistImportedImage(context, cachedImagePath)
                        }.onSuccess { path ->
                            val imported = parseLocalDreamParameters(localDreamParameters)
                            val description = suppliedDescription.ifBlank { imported.prompt }
                            val postId = store.createImportedMediaPost(
                                body,
                                path,
                                description = description,
                                audience = audience,
                                audienceCharacterIds = audienceCharacterIds,
                                aiResponsesEnabled = aiResponsesEnabled,
                                prompt = imported.prompt,
                                negativePrompt = imported.negativePrompt,
                                seed = imported.seed ?: 0,
                                steps = imported.steps ?: 20,
                                cfg = imported.cfg ?: 7.5,
                                scheduler = imported.scheduler.ifBlank { "dpm" },
                                width = imported.width ?: 512,
                                height = imported.height ?: 512,
                            )
                            fun respond(description: String) {
                                if (aiResponsesEnabled && (body.isNotBlank() || description.isNotBlank())) {
                                    WorldEngine.respondToPost(
                                        context,
                                        postId,
                                        kind,
                                        buildString {
                                            append(body)
                                            if (description.isNotBlank()) {
                                                append("\nImage description: $description")
                                            }
                                        },
                                        audience,
                                        audienceCharacterIds,
                                    ) { onChanged() }
                                }
                            }
                            if (analyzeImage && description.isBlank()) {
                                val configStore = ProviderStore(context)
                                val vision = configStore.loadFor(ProviderTask.Vision)
                                ProviderVisionClient.describe(vision, path) { result ->
                                    result.onSuccess { response ->
                                        if (
                                            store.applyVisionDescription(
                                                postId,
                                                path,
                                                response.text,
                                            )
                                        ) {
                                            respond(response.text)
                                        } else {
                                            respond(
                                                store.posts("moment")
                                                    .firstOrNull { it.id == postId }
                                                    ?.mediaDescription
                                                    .orEmpty(),
                                            )
                                        }
                                    }.onFailure {
                                        generationStatus =
                                            "$visionAnalysisFailed：" +
                                            it.message.orEmpty()
                                        respond(description)
                                    }
                                    onChanged()
                                }
                            } else {
                                respond(description)
                            }
                        }.onFailure {
                            generationStatus = "$generationFailed：${it.message.orEmpty()}"
                        }
                    } else if (prompt == null) {
                        val postId = store.createPost(
                            kind,
                            store.userName(),
                            title,
                            body,
                            authorKind = "user",
                            audience = audience,
                            audienceCharacterIds = audienceCharacterIds,
                            aiResponsesEnabled = aiResponsesEnabled,
                        )
                        if (aiResponsesEnabled) {
                            WorldEngine.respondToPost(
                                context,
                                postId,
                                kind,
                                body,
                                audience,
                                audienceCharacterIds,
                            ) { onChanged() }
                        }
                    } else {
                        val postId = store.createMediaPost(
                            body,
                            prompt,
                            negativePrompt = negativePrompt,
                            audience = audience,
                            audienceCharacterIds = audienceCharacterIds,
                            aiResponsesEnabled = aiResponsesEnabled,
                        )
                        generationStatus = connecting
                        LocalDreamQueue.resume(
                            context = context,
                            onProgress = { activePostId, step, total ->
                                if (activePostId == postId) {
                                    generationStatus = "$step / $total"
                                }
                            },
                            onFinished = { activePostId, result ->
                                if (activePostId == postId) {
                                    generationStatus = result.fold(
                                        onSuccess = { generationReady },
                                        onFailure = {
                                            "$generationFailed：${it.message.orEmpty()}"
                                        },
                                    )
                                }
                                onChanged()
                            },
                        )
                    }
                    onChanged()
                }
            }
        }
        if (kind == "forum") {
            item {
                val sortChipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = forumSort == "latest",
                        onClick = { forumSort = "latest" },
                        label = { Text(stringResource(R.string.sort_latest)) },
                        shape = RoundedCornerShape(50),
                        colors = sortChipColors,
                    )
                    FilterChip(
                        selected = forumSort == "top",
                        onClick = { forumSort = "top" },
                        label = { Text(stringResource(R.string.sort_top)) },
                        shape = RoundedCornerShape(50),
                        colors = sortChipColors,
                    )
                    FilterChip(
                        selected = forumSort == "active",
                        onClick = { forumSort = "active" },
                        label = { Text(stringResource(R.string.sort_active)) },
                        shape = RoundedCornerShape(50),
                        colors = sortChipColors,
                    )
                }
            }
        }
        generationStatus?.let { item { StatusCard(it) } }
        if (posts.isEmpty()) {
            item {
                StatusCard(
                    stringResource(
                        if (kind == "moment") R.string.no_moments_yet
                        else R.string.no_commons_yet,
                    ),
                )
            }
        }
        items(posts, key = SocialPost::id) { post ->
            PostCard(
                post = post,
                authorAvatarPath = post.authorKind
                    .takeIf { it == "user" }
                    ?.let { identity.avatarPath },
                onOpen = { selectedPostId = post.id },
                onToggleReaction = {
                    store.toggleReaction(post.id)
                    onChanged()
                },
                onVote = { value ->
                    store.voteOnPost(post.id, value)
                    onChanged()
                },
                onOpenAuthor = {
                    if (post.authorKind == "npc") {
                        npcProfile = store.npcByName(post.authorName)
                    }
                },
                onOpenImage = { menu ->
                    previewPostId = post.id
                    openPreviewMenu = menu
                },
            )
        }
    }
}

@Composable
private fun PostComposer(
    kind: String,
    characters: List<ResidentCharacter>,
    onPublished: () -> Unit,
    onPost: (
        String,
        String,
        String?,
        String,
        String,
        Boolean,
        String?,
        String,
        Boolean,
        String,
        String,
    ) -> Unit,
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var visualCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var cachedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaDescription by rememberSaveable { mutableStateOf("") }
    var localDreamParameters by rememberSaveable { mutableStateOf("") }
    var analyzeImage by rememberSaveable { mutableStateOf(false) }
    var imageStatus by remember { mutableStateOf<String?>(null) }
    var audience by rememberSaveable { mutableStateOf("world") }
    var selectedCharacterIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var aiResponsesEnabled by rememberSaveable { mutableStateOf(true) }
    val imageReady = stringResource(R.string.image_ready_to_post)
    val imageImportFailed = stringResource(R.string.image_import_failed)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        imageStatus = null
        Thread {
            val imported = runCatching { importUserImageToCache(context, uri) }
            Handler(Looper.getMainLooper()).post {
                imported.onSuccess {
                    cachedImagePath?.let(::File).let { previous ->
                        if (previous?.parentFile == context.cacheDir) previous.delete()
                    }
                    cachedImagePath = it
                    prompt = ""
                    mediaDescription = ""
                    localDreamParameters = ""
                    analyzeImage = false
                    imageStatus = imageReady
                }.onFailure {
                    imageStatus = "$imageImportFailed：${it.message.orEmpty()}"
                }
            }
        }.start()
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (kind == "forum") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.topic_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = {
                    Text(
                        stringResource(
                            if (kind == "moment") R.string.share_moment
                            else R.string.topic_body,
                        ),
                    )
                },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind == "moment") {
                TextButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.choose_local_image))
                }
                imageStatus?.let { StatusCard(it) }
                if (cachedImagePath != null) {
                    OutlinedTextField(
                        value = localDreamParameters,
                        onValueChange = { localDreamParameters = it },
                        label = { Text(stringResource(R.string.local_dream_parameters_optional)) },
                        supportingText = {
                            Text(stringResource(R.string.local_dream_parameters_summary))
                        },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = mediaDescription,
                        onValueChange = { mediaDescription = it },
                        label = { Text(stringResource(R.string.media_description_optional)) },
                        supportingText = {
                            Text(stringResource(R.string.media_description_privacy))
                        },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilterChip(
                        selected = analyzeImage,
                        onClick = { analyzeImage = !analyzeImage },
                        label = { Text(stringResource(R.string.allow_vision_analysis)) },
                    )
                }
                Text(
                    stringResource(R.string.audience),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "world" to R.string.audience_world,
                        "circle" to R.string.audience_circle,
                        "selected" to R.string.audience_selected,
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = audience == value,
                            onClick = { audience = value },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                if (audience == "selected") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        characters.forEach { character ->
                            FilterChip(
                                selected = character.id in selectedCharacterIds,
                                onClick = {
                                    selectedCharacterIds = if (character.id in selectedCharacterIds) {
                                        selectedCharacterIds - character.id
                                    } else {
                                        selectedCharacterIds + character.id
                                    }
                                },
                                label = { Text(character.name) },
                            )
                        }
                    }
                }
                FilterChip(
                    selected = aiResponsesEnabled,
                    onClick = { aiResponsesEnabled = !aiResponsesEnabled },
                    label = { Text(stringResource(R.string.allow_ai_responses)) },
                )
                if (cachedImagePath == null) {
                    if (characters.isNotEmpty()) {
                        Text(
                            stringResource(R.string.visual_identity),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.visual_identity_prompt_summary),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = visualCharacterId == null,
                                onClick = { visualCharacterId = null },
                                label = { Text(stringResource(R.string.no_character_identity)) },
                            )
                            characters.forEach { character ->
                                FilterChip(
                                    selected = visualCharacterId == character.id,
                                    onClick = { visualCharacterId = character.id },
                                    label = { Text(character.name) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text(stringResource(R.string.local_dream_prompt_optional)) },
                        supportingText = {
                            Text(stringResource(R.string.local_dream_prompt_summary))
                        },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Button(
                onClick = {
                    onPost(
                        title.trim(),
                        body.trim(),
                        composeVisualPrompt(
                            characters.firstOrNull { it.id == visualCharacterId },
                            prompt,
                            WorldEngine.globalStyle(context),
                        ).ifBlank { null },
                        if (kind == "moment") audience else "world",
                        selectedCharacterIds.joinToString(","),
                        kind != "moment" || aiResponsesEnabled,
                        cachedImagePath,
                        mediaDescription.trim(),
                        analyzeImage,
                        characters.firstOrNull { it.id == visualCharacterId }?.negativePrompt.orEmpty(),
                        localDreamParameters,
                    )
                    title = ""
                    body = ""
                    prompt = ""
                    visualCharacterId = null
                    cachedImagePath = null
                    mediaDescription = ""
                    localDreamParameters = ""
                    analyzeImage = false
                    imageStatus = null
                    onPublished()
                },
                enabled = (body.isNotBlank() || cachedImagePath != null || prompt.isNotBlank()) &&
                    (kind != "forum" || title.isNotBlank()) &&
                    (kind != "moment" || audience != "selected" || selectedCharacterIds.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        when {
                            kind == "forum" -> R.string.start_topic
                            prompt.isNotBlank() -> R.string.generate_and_publish
                            else -> R.string.publish
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun PostCard(
    post: SocialPost,
    authorAvatarPath: String?,
    onOpen: () -> Unit,
    onToggleReaction: () -> Unit,
    onVote: (Int) -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenImage: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(post.authorName.take(1).uppercase(), 40.dp, authorAvatarPath)
                Column(Modifier.weight(1f)) {
                    Text(
                        post.authorName.removeSuffix(" · NPC"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onOpenAuthor),
                    )
                    Text(
                        relativeTimeLabel(post.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (post.title.isNotBlank()) {
                Text(
                    post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (post.body.isNotBlank()) {
                Text(
                    post.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (post.kind == "forum") 4 else Int.MAX_VALUE,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            PostMedia(
                post,
                onOpenImage,
                onDoubleTapLike = if (post.kind == "forum") null else onToggleReaction,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.kind == "forum") {
                    VotePill(
                        score = post.voteScore,
                        userVote = post.userVote,
                        onVote = onVote,
                    )
                } else {
                    AnimatedLikeButton(
                        liked = post.reactedByUser,
                        count = post.reactionCount,
                        onToggle = onToggleReaction,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        CommentOutlineIcon,
                        contentDescription = stringResource(R.string.replies_count, post.commentCount),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    if (post.commentCount > 0) {
                        Text(
                            "${post.commentCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostDetailScreen(
    contentPadding: PaddingValues,
    post: SocialPost,
    authorAvatarPath: String?,
    comments: List<SocialComment>,
    versions: List<SocialPostVersion>,
    status: String?,
    onBack: () -> Unit,
    onComment: (SocialReplyDraft) -> Unit,
    onToggleReaction: () -> Unit,
    onVote: (Int) -> Unit,
    onUpdatePost: (String, String) -> Unit,
    onUpdateMediaDescription: (String) -> Unit,
    onDeletePost: () -> Unit,
    onHidePost: () -> Unit,
    onRewritePost: () -> Unit,
    onRestoreVersion: (Long) -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenImage: (Boolean) -> Unit,
) {
    var comment by rememberSaveable { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<SocialComment?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editTitle by rememberSaveable(post.id) { mutableStateOf(post.title) }
    var editBody by rememberSaveable(post.id) { mutableStateOf(post.body) }
    var editingDescription by rememberSaveable(post.id) { mutableStateOf(false) }
    var editDescription by rememberSaveable(post.id) {
        mutableStateOf(post.mediaDescription)
    }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val animationsEnabled = remember { systemAnimationsEnabled(context) }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val visibleComments = remember(comments, post.kind) {
        if (post.kind == "forum") threadedComments(comments) else comments.map { it to 0 }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    post.authorName.removeSuffix(" · NPC"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more_actions),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (post.authorKind == "user") {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_post)) },
                                onClick = {
                                    menuOpen = false
                                    editTitle = post.title
                                    editBody = post.body
                                    editing = true
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rewrite_ai_post)) },
                                onClick = {
                                    menuOpen = false
                                    onRewritePost()
                                },
                            )
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
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (editing && post.kind == "forum") {
                OutlinedTextField(
                    value = editTitle,
                    onValueChange = { editTitle = it },
                    label = { Text(stringResource(R.string.topic_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (post.title.isNotBlank()) {
                Text(
                    post.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (editing) {
                OutlinedTextField(
                    value = editBody,
                    onValueChange = { editBody = it },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(post.body, style = MaterialTheme.typography.bodyLarge)
            }
            PostMedia(
                post,
                onOpenImage,
                onDoubleTapLike = if (post.kind == "forum") null else onToggleReaction,
            )
            if (post.mediaPath != null && post.mediaSource == "user") {
                Text(
                    stringResource(R.string.media_description_detail_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (editingDescription) {
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        supportingText = {
                            Text(stringResource(R.string.media_description_detail_summary))
                        },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onUpdateMediaDescription(editDescription.trim())
                                editingDescription = false
                            },
                        ) {
                            Text(stringResource(R.string.save_media_description))
                        }
                        TextButton(onClick = {
                            editDescription = post.mediaDescription
                            editingDescription = false
                        }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                } else {
                    Text(
                        post.mediaDescription.ifBlank {
                            stringResource(R.string.media_description_empty)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { editingDescription = true }) {
                        Text(stringResource(R.string.edit_media_description))
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(post.authorName.take(1).uppercase(), 32.dp, authorAvatarPath)
                Text(
                    post.authorName.removeSuffix(" · NPC"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onOpenAuthor),
                )
                Text(
                    relativeTimeLabel(post.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (post.kind == "moment" && post.authorKind == "user") {
                Text(
                    "${stringResource(R.string.audience)}：${
                        stringResource(
                            when (post.audience) {
                                "circle" -> R.string.audience_circle
                                "selected" -> R.string.audience_selected
                                else -> R.string.audience_world
                            },
                        )
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!post.aiResponsesEnabled) {
                    Text(
                        stringResource(R.string.ai_responses_disabled),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (editBody.isNotBlank()) onUpdatePost(editTitle, editBody)
                            editing = false
                        },
                        enabled = editBody.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.save_changes))
                    }
                    TextButton(onClick = { editing = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            } else {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (post.kind == "forum") {
                        VotePill(
                            score = post.voteScore,
                            userVote = post.userVote,
                            onVote = onVote,
                        )
                    } else {
                        AnimatedLikeButton(
                            liked = post.reactedByUser,
                            count = post.reactionCount,
                            onToggle = onToggleReaction,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            CommentOutlineIcon,
                            contentDescription = stringResource(
                                R.string.replies_count,
                                post.commentCount,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        if (post.commentCount > 0) {
                            Text(
                                "${post.commentCount}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (post.authorKind != "user" && post.providerName.isNotBlank()) {
                Text(
                    stringResource(
                        R.string.generation_provenance,
                        post.providerName,
                        post.modelName,
                        formatter.format(Date(post.createdAt)),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
        status?.let { message -> item { StatusCard(message) } }
        if (versions.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.previous_versions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(versions, key = SocialPostVersion::id) { version ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(version.body)
                        Text(
                            stringResource(
                                R.string.generation_provenance,
                                version.providerName.ifBlank { "—" },
                                version.modelName.ifBlank { "—" },
                                formatter.format(Date(version.createdAt)),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onRestoreVersion(version.id) }) {
                            Text(stringResource(R.string.restore_version))
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.replies_count, comments.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (visibleComments.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.comment_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(visibleComments, key = { _, it -> it.first.id }) { _, (reply, depth) ->
            CommentRow(
                reply = reply,
                depth = depth,
                modifier = if (animationsEnabled) Modifier.animateItem() else Modifier,
                onReply = { replyTo = reply },
            )
        }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.imePadding(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                replyTo?.let {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                R.string.reply_to_member,
                                it.authorName.removeSuffix(" · NPC"),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel_reply),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { replyTo = null }
                                .padding(2.dp),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = {
                            Text(
                                if (replyTo == null) {
                                    stringResource(R.string.comment_hint)
                                } else {
                                    stringResource(R.string.write_reply)
                                },
                            )
                        },
                        minLines = 1,
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = {
                            onComment(
                                SocialReplyDraft(
                                    comment.trim(),
                                    replyTo?.id,
                                    replyTo?.authorName.orEmpty().removeSuffix(" · NPC"),
                                ),
                            )
                            comment = ""
                            replyTo = null
                        },
                        enabled = comment.isNotBlank(),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.reply),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    reply: SocialComment,
    depth: Int,
    modifier: Modifier = Modifier,
    onReply: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (depth.coerceAtMost(3) * 18).dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(reply.authorName.take(1).uppercase(), 28.dp)
        Column(Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    reply.authorName.removeSuffix(" · NPC"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    relativeTimeLabel(reply.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            if (reply.replyToName.isNotBlank()) {
                Text(
                    stringResource(R.string.reply_to_member, reply.replyToName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(reply.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.reply),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onReply)
                    .padding(horizontal = 2.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun PostMedia(
    post: SocialPost,
    onOpenImage: (Boolean) -> Unit,
    onDoubleTapLike: (() -> Unit)? = null,
) {
    post.mediaPath?.let { path ->
        val bitmap = remember(path) { decodeSocialBitmap(path) }
        bitmap?.let {
            val context = LocalContext.current
            val animationsEnabled = remember { systemAnimationsEnabled(context) }
            var burst by remember(post.id) { mutableStateOf(0) }
            val heartScale = remember { Animatable(0.4f) }
            val heartAlpha = remember { Animatable(0f) }
            LaunchedEffect(burst) {
                if (burst > 0 && animationsEnabled) {
                    heartAlpha.snapTo(1f)
                    heartScale.snapTo(0.4f)
                    heartScale.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                    kotlinx.coroutines.delay(550)
                    heartAlpha.animateTo(0f, tween(220))
                }
            }
            val mediaRatio = it.width.toFloat() / it.height.toFloat()
            Box {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.generated_post_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(mediaRatio.coerceIn(0.8f, 1.91f))
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(post.id) {
                            detectTapGestures(
                                onTap = { onOpenImage(false) },
                                onDoubleTap = {
                                    if (onDoubleTapLike == null) {
                                        onOpenImage(true)
                                    } else {
                                        if (!post.reactedByUser) onDoubleTapLike()
                                        burst++
                                    }
                                },
                                onLongPress = { onOpenImage(true) },
                            )
                        },
                )
                if (burst > 0 || heartAlpha.value > 0f) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(96.dp)
                            .graphicsLayer {
                                scaleX = heartScale.value
                                scaleY = heartScale.value
                                alpha = heartAlpha.value
                            },
                    )
                }
            }
        }
    }
    when (post.mediaStatus) {
        "pending" -> Text(
            stringResource(R.string.local_dream_pending),
            color = MaterialTheme.colorScheme.primary,
        )
        "failed" -> Text(
            stringResource(R.string.local_dream_failed),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun FullScreenMediaPreview(
    post: SocialPost,
    versions: List<MediaVersion>,
    menuInitiallyOpen: Boolean,
    onDismiss: () -> Unit,
    onRedraw: (String) -> Unit,
    onRestoreVersion: (Long) -> Unit,
    onDeleteVersion: (Long) -> Unit,
) {
    var menuOpen by rememberSaveable(post.id) { mutableStateOf(menuInitiallyOpen) }
    var editingPrompt by rememberSaveable(post.id) { mutableStateOf(false) }
    var versionListOpen by rememberSaveable(post.id) { mutableStateOf(false) }
    val originalPrompt = mediaPromptForEditing(post.mediaPrompt, post.mediaDescription)
    var prompt by rememberSaveable(post.id) { mutableStateOf(originalPrompt) }
    val bitmap = remember(post.mediaPath) {
        post.mediaPath?.let(::decodeSocialBitmap)
    }
    val closePreviewLabel = stringResource(R.string.close_image_preview)
    val imageActionsLabel = stringResource(R.string.open_image_actions)

    LaunchedEffect(post.id, menuInitiallyOpen) {
        menuOpen = menuInitiallyOpen
        editingPrompt = false
        versionListOpen = false
    }
    LaunchedEffect(post.id, originalPrompt) {
        prompt = originalPrompt
    }

    fun dismissTopLayer() {
        when {
            editingPrompt -> editingPrompt = false
            versionListOpen -> versionListOpen = false
            menuOpen -> menuOpen = false
            else -> onDismiss()
        }
    }
    BackHandler { dismissTopLayer() }
    Dialog(
        onDismissRequest = ::dismissTopLayer,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.generated_post_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(post.id) {
                            detectTapGestures(onTap = { onDismiss() })
                        },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics {
                        contentDescription = closePreviewLabel
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
                Box {
                    TextButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.semantics {
                            contentDescription = imageActionsLabel
                        },
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        if (originalPrompt.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.redraw_directly)) },
                                onClick = {
                                    onRedraw(originalPrompt)
                                    menuOpen = false
                                    onDismiss()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_prompt_and_redraw)) },
                            onClick = {
                                editingPrompt = true
                                menuOpen = false
                            },
                        )
                        if (versions.isNotEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.image_versions, versions.size))
                                },
                                onClick = {
                                    versionListOpen = true
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            }
            if (versionListOpen) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(18.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.image_version_history),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        LazyColumn(Modifier.heightIn(max = 460.dp)) {
                            items(versions, key = MediaVersion::id) { version ->
                                val current = version.path == post.mediaPath
                                Card(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            if (current) {
                                                stringResource(R.string.current_image_version)
                                            } else {
                                                DateFormat.getDateTimeInstance().format(
                                                    Date(version.createdAt),
                                                )
                                            },
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(version.prompt, maxLines = 3)
                                        if (!current) {
                                            Row {
                                                TextButton(
                                                    onClick = {
                                                        onRestoreVersion(version.id)
                                                        versionListOpen = false
                                                    },
                                                ) {
                                                    Text(stringResource(R.string.restore_version))
                                                }
                                                TextButton(
                                                    onClick = {
                                                        onDeleteVersion(version.id)
                                                    },
                                                ) {
                                                    Text(stringResource(R.string.delete_version))
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        TextButton(
                            onClick = { versionListOpen = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
            }
            if (editingPrompt) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(18.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            label = { Text(stringResource(R.string.edit_generation_prompt)) },
                            minLines = 5,
                            maxLines = 9,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                onRedraw(prompt)
                                editingPrompt = false
                                onDismiss()
                            },
                            enabled = prompt.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.generate_again))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NpcProfileDialog(
    npc: NpcProfile,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    npc.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.npc_profile),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(npc.bio)
                Text(
                    stringResource(R.string.npc_disclosure),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        if (npc.active) R.string.npc_active else R.string.npc_departed,
                    ),
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.back))
                }
            }
        }
    }
}
