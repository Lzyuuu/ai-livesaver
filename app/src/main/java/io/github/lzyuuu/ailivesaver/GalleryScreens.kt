package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
internal fun GalleryScreen(
    contentPadding: PaddingValues,
    store: WorldStore,
    revision: Int,
    onBack: () -> Unit,
) {
    val characters = remember(revision) { store.characters(false) }
    val images = remember(revision) {
        store.posts("moment").filter { post ->
            post.mediaStatus == "ready" && !post.mediaPath.isNullOrBlank() &&
                post.mediaPath.let(::File).exists()
        }
    }
    var selectedCharacter by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = images.filter { selectedCharacter == null || it.authorCharacterId == selectedCharacter }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FancyInk)
            .padding(contentPadding)
            .testTag("gallery-screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("gallery-back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("Gallery", color = FancyCream, fontSize = 22.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCharacter == null,
                onClick = { selectedCharacter = null },
                label = { Text("全部") },
                modifier = Modifier.testTag("gallery-filter-all"),
            )
            characters.forEach { character ->
                FilterChip(
                    selected = selectedCharacter == character.id,
                    onClick = { selectedCharacter = character.id },
                    label = { Text(character.name) },
                    modifier = Modifier.testTag("gallery-filter-${character.id}"),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            Text(
                "还没有图片资产",
                color = FancyCream.copy(alpha = 0.7f),
                modifier = Modifier.padding(20.dp).testTag("gallery-empty"),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.id }) { post ->
                    val bitmap = remember(post.mediaPath) {
                        post.mediaPath?.let(BitmapFactory::decodeFile)?.asImageBitmap()
                    }
                    if (bitmap != null) {
                        Column(modifier = Modifier.testTag("gallery-image-${post.id}")) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = post.mediaDescription.ifBlank { "图片" },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedPath = post.mediaPath },
                            )
                            Text(
                                text = characters.firstOrNull { it.id == post.authorCharacterId }?.name
                                    ?: post.authorName,
                                color = FancyCream.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    selectedPath?.let { path ->
        val bitmap = remember(path) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        if (bitmap != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(FancyInk).testTag("gallery-viewer"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "图片查看器",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable { selectedPath = null },
                )
                IconButton(
                    onClick = { selectedPath = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = FancyCream)
                }
            }
        }
    }
}
