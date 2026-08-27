package io.github.lzyuuu.ailivesaver

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun GalleryScreen(contentPadding: PaddingValues, store: WorldStore, revision: Int, onBack: () -> Unit, onUseAsAuraSource: (CreativeAsset) -> Unit = {}, onUseAsAuraTarget: (CreativeAsset) -> Unit = {}) {
    val assets = remember(revision) {
        val unified = store.queryCreativeAssets().toMutableList()
        val known = unified.map { it.pathOrUri }.toSet()
        store.posts("moment").asSequence()
            .filter { it.mediaStatus == "ready" && !it.mediaPath.isNullOrBlank() && java.io.File(it.mediaPath!!).isFile && it.mediaPath !in known }
            .forEach { post -> unified += CreativeAsset(0, post.mediaPath!!, "image", "social", post.mediaDescription, post.authorCharacterId, post.authorName, null, null, "ready", "", post.createdAt) }
        unified
    }
    var selected by remember { mutableStateOf<CreativeAsset?>(null) }
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var backendFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<CreativeAsset?>(null) }
    val visibleAssets = assets.filter { (statusFilter == null || it.status == statusFilter) && (backendFilter == null || it.backend == backendFilter) }
    Column(Modifier.fillMaxSize().background(ReferencePalette.PageBg).padding(contentPadding).testTag("gallery-screen")) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) { IconButton(onBack, Modifier.testTag("gallery-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }; Text("Gallery", color=FancyCream, modifier=Modifier.padding(12.dp)) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = statusFilter == null, onClick = { statusFilter = null }, label = { Text("全部状态") }, modifier = Modifier.testTag("gallery-filter-all"))
            FilterChip(selected = statusFilter == "ready", onClick = { statusFilter = "ready" }, label = { Text("Ready") })
            FilterChip(selected = backendFilter == "aura_swap", onClick = { backendFilter = if (backendFilter == "aura_swap") null else "aura_swap" }, label = { Text("Aura") })
        }
        if (visibleAssets.isEmpty()) Text("还没有图片资产", color=FancyCream, modifier=Modifier.padding(20.dp).testTag("gallery-empty"))
        else LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding=PaddingValues(12.dp), verticalArrangement=Arrangement.spacedBy(10.dp), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(visibleAssets, key={it.id}) { asset ->
                val bitmap = remember(asset.pathOrUri) { BitmapFactory.decodeFile(asset.pathOrUri)?.asImageBitmap() }
                Column(Modifier.testTag("gallery-image-${asset.id.takeIf { it > 0 } ?: asset.pathOrUri.hashCode()}").clickable { selected=asset }) { if(bitmap!=null) Image(bitmap, "图片", Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)), contentScale=ContentScale.Crop); Row { Text(asset.backend+" · "+asset.status, color=FancyCream, modifier = Modifier.weight(1f)); TextButton(onClick = { onUseAsAuraSource(asset) }) { Text("Aura Source") }; TextButton(onClick = { onUseAsAuraTarget(asset) }) { Text("Aura Target") }; TextButton(onClick = { deleteCandidate = asset }) { Text("删除") } } }
            }
        }
    }
    deleteCandidate?.let { asset ->
        AlertDialog(onDismissRequest = { deleteCandidate = null }, confirmButton = { TextButton(onClick = { store.deleteCreativeAsset(asset.id); deleteCandidate = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("取消") } }, title = { Text("删除资产？") }, text = { Text("仅删除 Gallery 记录，不会影响 social media 原始内容。") })
    }
    selected?.let { asset ->
        val detail = asset.prompt + "\n" + asset.pathOrUri + "\n" + asset.status + if (asset.error.isBlank()) "" else "\n" + asset.error
        AlertDialog(
            modifier = Modifier.testTag("gallery-viewer"),
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
            title = { Text("Asset #${asset.id}") },
            text = { Text(detail) },
        )
    }
}
