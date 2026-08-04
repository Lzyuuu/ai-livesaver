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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun GalleryScreen(contentPadding: PaddingValues, store: WorldStore, revision: Int, onBack: () -> Unit) {
    val assets = remember(revision) {
        val unified = store.queryCreativeAssets().toMutableList()
        val known = unified.map { it.pathOrUri }.toSet()
        store.posts("moment").asSequence()
            .filter { it.mediaStatus == "ready" && !it.mediaPath.isNullOrBlank() && java.io.File(it.mediaPath!!).isFile && it.mediaPath !in known }
            .forEach { post -> unified += CreativeAsset(0, post.mediaPath!!, "image", "social", post.mediaDescription, post.authorCharacterId, post.authorName, null, null, "ready", "", post.createdAt) }
        unified
    }
    var selected by remember { mutableStateOf<CreativeAsset?>(null) }
    Column(Modifier.fillMaxSize().background(FancyInk).padding(contentPadding).testTag("gallery-screen")) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) { IconButton(onBack, Modifier.testTag("gallery-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }; Text("Gallery", color=FancyCream, modifier=Modifier.padding(12.dp)) }
        if (assets.isEmpty()) Text("还没有图片资产", color=FancyCream, modifier=Modifier.padding(20.dp).testTag("gallery-empty"))
        else LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding=PaddingValues(12.dp), verticalArrangement=Arrangement.spacedBy(10.dp), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            items(assets, key={it.id}) { asset ->
                val bitmap = remember(asset.pathOrUri) { BitmapFactory.decodeFile(asset.pathOrUri)?.asImageBitmap() }
                Column(Modifier.testTag("gallery-asset-${asset.id}").clickable { selected=asset }) { if(bitmap!=null) Image(bitmap, "图片", Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)), contentScale=ContentScale.Crop); Text(asset.backend+" · "+asset.status, color=FancyCream) }
            }
        }
    }
    selected?.let { asset -> AlertDialog(onDismissRequest={selected=null}, confirmButton={TextButton({selected=null}){Text("关闭")}}, title={Text("Asset #${asset.id}")}, text={Text("${asset.prompt}\n${asset.pathOrUri}\n${asset.status}${if(asset.error.isBlank()) "" else "\n${asset.error}" )}")}) }
}
