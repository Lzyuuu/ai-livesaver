package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.DateFormat
import java.util.Date

private data class SocialReplyDraft(
    val body: String,
    val parentId: Long?,
    val replyToName: String,
)

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
    onChanged: () -> Unit,
    onStartWorld: () -> Unit,
) {
    val context = LocalContext.current
    val character = remember(revision) { store.primaryCharacter() }
    val characters = remember(revision) { store.characters(includeDeparted = false) }
    var forumSort by rememberSaveable { mutableStateOf("latest") }
    val posts = remember(revision, kind, forumSort) { store.posts(kind, forumSort) }
    var selectedPostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var npcProfile by remember { mutableStateOf<NpcProfile?>(null) }
    var previewPostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openPreviewMenu by rememberSaveable { mutableStateOf(false) }
    var generationStatus by remember { mutableStateOf<String?>(null) }
    val generationFailed = stringResource(R.string.local_dream_generation_failed)
    val generationReady = stringResource(R.string.local_dream_generation_ready)
    val connecting = stringResource(R.string.local_dream_connecting)
    val rewriting = stringResource(R.string.rewriting_ai_post)
    val rewriteFailed = stringResource(R.string.rewrite_failed)
    val selectedPost = posts.firstOrNull { it.id == selectedPostId }
    val previewPost = posts.firstOrNull { it.id == previewPostId }

    npcProfile?.let { npc ->
        NpcProfileDialog(npc = npc, onDismiss = { npcProfile = null })
    }

    fun redraw(post: SocialPost, prompt: String) {
        store.prepareRedraw(post.id, prompt)
        onChanged()
        generationStatus = connecting
        LocalDreamClient.generate(
            context = context,
            prompt = prompt,
            onProgress = { step, total -> generationStatus = "$step / $total" },
        ) { result ->
            result.onSuccess {
                store.markMediaReady(post.id, it.path, it.seed)
                generationStatus = generationReady
            }.onFailure {
                store.markMediaFailed(post.id)
                generationStatus = "$generationFailed：${it.message.orEmpty()}"
            }
            onChanged()
        }
    }

    BackHandler(selectedPost != null) { selectedPostId = null }

    previewPost?.let { post ->
        FullScreenMediaPreview(
            post = post,
            menuInitiallyOpen = openPreviewMenu,
            onDismiss = {
                previewPostId = null
                openPreviewMenu = false
            },
            onRedraw = { redraw(post, it) },
        )
    }

    if (selectedPost != null) {
        PostDetailScreen(
            contentPadding = contentPadding,
            post = selectedPost,
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
            onUpdatePost = { title, body ->
                store.updateUserPost(selectedPost.id, title, body)
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
            Text(
                stringResource(if (kind == "moment") R.string.moments_title else R.string.commons_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    if (kind == "moment") R.string.moments_social_summary
                    else R.string.commons_social_summary,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (character == null) {
            item {
                StatusCard(stringResource(R.string.social_requires_world))
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStartWorld, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.begin_world_building))
                }
            }
        } else {
            item {
                PostComposer(kind, characters) {
                        title, body, prompt, audience, audienceCharacterIds, aiResponsesEnabled ->
                    if (prompt == null) {
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
                            ) { if (it) onChanged() }
                        }
                    } else {
                        val postId = store.createMediaPost(
                            body,
                            prompt,
                            audience,
                            audienceCharacterIds,
                            aiResponsesEnabled,
                        )
                        generationStatus = connecting
                        LocalDreamClient.generate(
                            context = context,
                            prompt = prompt,
                            onProgress = { step, total ->
                                generationStatus = "$step / $total"
                            },
                        ) { result ->
                            result.onSuccess {
                                store.markMediaReady(postId, it.path, it.seed)
                                generationStatus = generationReady
                                if (aiResponsesEnabled) {
                                    WorldEngine.respondToPost(
                                        context,
                                        postId,
                                        kind,
                                        body,
                                        audience,
                                        audienceCharacterIds,
                                    ) { if (it) onChanged() }
                                }
                            }.onFailure {
                                store.markMediaFailed(postId)
                                generationStatus = "$generationFailed：${it.message.orEmpty()}"
                            }
                            onChanged()
                        }
                    }
                    onChanged()
                }
            }
        }
        if (kind == "forum") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = forumSort == "latest",
                        onClick = { forumSort = "latest" },
                        label = { Text(stringResource(R.string.sort_latest)) },
                    )
                    FilterChip(
                        selected = forumSort == "active",
                        onClick = { forumSort = "active" },
                        label = { Text(stringResource(R.string.sort_active)) },
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
                onOpen = { selectedPostId = post.id },
                onToggleReaction = {
                    store.toggleReaction(post.id)
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
    onPost: (String, String, String?, String, String, Boolean) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var audience by rememberSaveable { mutableStateOf("world") }
    var selectedCharacterIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var aiResponsesEnabled by rememberSaveable { mutableStateOf(true) }
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
            Button(
                onClick = {
                    onPost(
                        title.trim(),
                        body.trim(),
                        prompt.trim().ifBlank { null },
                        if (kind == "moment") audience else "world",
                        selectedCharacterIds.joinToString(","),
                        kind != "moment" || aiResponsesEnabled,
                    )
                    title = ""
                    body = ""
                    prompt = ""
                },
                enabled = body.isNotBlank() &&
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
    onOpen: () -> Unit,
    onToggleReaction: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenImage: (Boolean) -> Unit,
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    post.authorName.removeSuffix(" · NPC"),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onOpenAuthor),
                )
                Text(
                    formatter.format(Date(post.createdAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (post.title.isNotBlank()) {
                Text(post.title, style = MaterialTheme.typography.titleLarge)
            }
            Text(post.body)
            PostMedia(post, onOpenImage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleReaction) {
                    Text(
                        "${if (post.reactedByUser) "♥" else "♡"} " +
                            stringResource(R.string.likes_count, post.reactionCount),
                    )
                }
                Text(
                    stringResource(R.string.open_discussion),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun PostDetailScreen(
    contentPadding: PaddingValues,
    post: SocialPost,
    comments: List<SocialComment>,
    versions: List<SocialPostVersion>,
    status: String?,
    onBack: () -> Unit,
    onComment: (SocialReplyDraft) -> Unit,
    onToggleReaction: () -> Unit,
    onUpdatePost: (String, String) -> Unit,
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
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val visibleComments = remember(comments, post.kind) {
        if (post.kind == "forum") threadedComments(comments) else comments.map { it to 0 }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("‹  ${stringResource(R.string.back)}") }
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
            PostMedia(post, onOpenImage)
            TextButton(onClick = onOpenAuthor) {
                Text(
                    "${post.authorName.removeSuffix(" · NPC")} · " +
                        formatter.format(Date(post.createdAt)),
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
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onToggleReaction) {
                    Text(
                        "${if (post.reactedByUser) "♥" else "♡"} " +
                            stringResource(R.string.likes_count, post.reactionCount),
                    )
                }
                if (post.authorKind == "user") {
                    TextButton(
                        onClick = {
                            if (editing) {
                                if (editBody.isNotBlank()) onUpdatePost(editTitle, editBody)
                                editing = false
                            } else {
                                editing = true
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (editing) R.string.save_changes else R.string.edit_post,
                            ),
                        )
                    }
                    TextButton(onClick = onDeletePost) {
                        Text(stringResource(R.string.delete_post))
                    }
                } else {
                    TextButton(onClick = onRewritePost) {
                        Text(stringResource(R.string.rewrite_ai_post))
                    }
                    TextButton(onClick = onHidePost) {
                        Text(stringResource(R.string.hide_ai_post))
                    }
                    TextButton(onClick = onDeletePost) {
                        Text(stringResource(R.string.delete_post))
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
        items(visibleComments, key = { it.first.id }) { (reply, depth) ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(start = (depth.coerceAtMost(4) * 18).dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(reply.authorName.removeSuffix(" · NPC"), fontWeight = FontWeight.Bold)
                    if (reply.replyToName.isNotBlank()) {
                        Text(
                            stringResource(R.string.reply_to_member, reply.replyToName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(reply.body)
                    Text(
                        formatter.format(Date(reply.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { replyTo = reply }) {
                        Text(stringResource(R.string.reply))
                    }
                }
            }
        }
        item {
            replyTo?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.reply_to_member, it.authorName))
                    TextButton(onClick = { replyTo = null }) {
                        Text(stringResource(R.string.cancel_reply))
                    }
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.write_reply)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reply))
            }
        }
    }
}

@Composable
private fun PostMedia(post: SocialPost, onOpenImage: (Boolean) -> Unit) {
    post.mediaPath?.let { path ->
        val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.generated_post_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(post.id) {
                        detectTapGestures(
                            onTap = { onOpenImage(false) },
                            onDoubleTap = { onOpenImage(true) },
                        )
                    },
            )
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
    post.mediaPrompt?.let { prompt ->
        Text(
            stringResource(R.string.media_description_from_prompt, prompt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FullScreenMediaPreview(
    post: SocialPost,
    menuInitiallyOpen: Boolean,
    onDismiss: () -> Unit,
    onRedraw: (String) -> Unit,
) {
    var menuOpen by rememberSaveable(post.id) { mutableStateOf(menuInitiallyOpen) }
    var editingPrompt by rememberSaveable(post.id) { mutableStateOf(false) }
    var prompt by rememberSaveable(post.id) { mutableStateOf(post.mediaPrompt.orEmpty()) }
    val bitmap = remember(post.mediaPath) {
        post.mediaPath?.let { BitmapFactory.decodeFile(it) }
    }

    BackHandler {
        when {
            editingPrompt -> editingPrompt = false
            menuOpen -> menuOpen = false
            else -> onDismiss()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
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
                TextButton(onClick = onDismiss) {
                    Text("‹", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
                TextButton(onClick = { menuOpen = true }) {
                    Text("⋮", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (menuOpen) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 104.dp, end = 18.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        TextButton(
                            onClick = {
                                onRedraw(post.mediaPrompt.orEmpty())
                                menuOpen = false
                                onDismiss()
                            },
                        ) {
                            Text(stringResource(R.string.redraw_directly))
                        }
                        TextButton(
                            onClick = {
                                editingPrompt = true
                                menuOpen = false
                            },
                        ) {
                            Text(stringResource(R.string.edit_prompt_and_redraw))
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
