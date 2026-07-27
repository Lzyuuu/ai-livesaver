package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
    val posts = remember(revision, kind) { store.posts(kind) }
    var selectedPostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var previewPostId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openPreviewMenu by rememberSaveable { mutableStateOf(false) }
    var generationStatus by remember { mutableStateOf<String?>(null) }
    val generationFailed = stringResource(R.string.local_dream_generation_failed)
    val generationReady = stringResource(R.string.local_dream_generation_ready)
    val connecting = stringResource(R.string.local_dream_connecting)
    val selectedPost = posts.firstOrNull { it.id == selectedPostId }
    val previewPost = posts.firstOrNull { it.id == previewPostId }

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
            onBack = { selectedPostId = null },
            onComment = {
                store.addComment(selectedPost.id, it)
                onChanged()
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
                PostComposer(kind) { title, body, prompt ->
                    if (prompt == null) {
                        store.createPost(kind, title, body)
                    } else {
                        val postId = store.createMediaPost(body, prompt)
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
    onPost: (String, String, String?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
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
                    onPost(title.trim(), body.trim(), prompt.trim().ifBlank { null })
                    title = ""
                    body = ""
                    prompt = ""
                },
                enabled = body.isNotBlank() && (kind != "forum" || title.isNotBlank()),
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
                Text(post.authorName, fontWeight = FontWeight.Bold)
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
            Text(
                stringResource(R.string.open_discussion),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PostDetailScreen(
    contentPadding: PaddingValues,
    post: SocialPost,
    comments: List<SocialComment>,
    onBack: () -> Unit,
    onComment: (String) -> Unit,
    onOpenImage: (Boolean) -> Unit,
) {
    var comment by rememberSaveable { mutableStateOf("") }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
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
            if (post.title.isNotBlank()) {
                Text(
                    post.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(post.body, style = MaterialTheme.typography.bodyLarge)
            PostMedia(post, onOpenImage)
            Text(
                "${post.authorName} · ${formatter.format(Date(post.createdAt))}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.replies_count, comments.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(comments, key = SocialComment::id) { reply ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(reply.authorName, fontWeight = FontWeight.Bold)
                    Text(reply.body)
                    Text(
                        formatter.format(Date(reply.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
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
                    onComment(comment.trim())
                    comment = ""
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
