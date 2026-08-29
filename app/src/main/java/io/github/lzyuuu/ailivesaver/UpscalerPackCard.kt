package io.github.lzyuuu.ailivesaver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * HD Upscaler 模型包卡片（阶段③，商店条目 hd-upscalers / Enhance HD）：
 * Real-ESRGAN x4 双模型（照片/画稿）下载管理 + 对最近生成图执行 4× 离线放大。
 */
@Composable
internal fun UpscalerPackCard() {
    val context = LocalContext.current
    var revision by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var upscaling by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var upscaledPath by remember { mutableStateOf<String?>(null) }

    val photo = UpscalerModelStore.catalog[0]
    val anime = UpscalerModelStore.catalog[1]
    val photoReady = remember(revision) { UpscalerModelStore.isReady(context, photo) }
    val animeReady = remember(revision) { UpscalerModelStore.isReady(context, anime) }

    fun latestGenerated(): File? =
        File(context.filesDir, "media").listFiles()
            .orEmpty()
            .filter { it.name.startsWith("ondevice-") || it.name.startsWith("forge-") }
            .maxByOrNull { it.lastModified() }

    fun startDownload(entry: UpscalerModelEntry) {
        if (downloadingId != null) return
        downloadingId = entry.id
        progress = 0f
        UpscalerModelStore.download(
            context = context,
            entry = entry,
            onProgress = { done, total -> if (total > 0) progress = done.toFloat() / total },
            onDone = {
                downloadingId = null
                revision++
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ReferencePalette.Card)
            .padding(16.dp)
            .testTag("upscaler-pack"),
    ) {
        Text("高清放大包", color = FancyCream, fontWeight = FontWeight.Bold)
        Text(
            "Enhance HD 工具所用的两个设备端 Real-ESRGAN 放大模型（4×，离线）。",
            color = ReferencePalette.TextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        listOf(photo to photoReady, anime to animeReady).forEach { (entry, ready) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, color = FancyCream, fontSize = 13.sp)
                    Text(
                        when {
                            downloadingId == entry.id -> "下载中 ${(progress * 100).toInt()}%"
                            ready -> "已就绪 · ${formatBytes(entry.bytes)}"
                            else -> formatBytes(entry.bytes)
                        },
                        color = ReferencePalette.TextSecondary,
                        fontSize = 11.sp,
                    )
                    if (downloadingId == entry.id) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }
                if (ready) {
                    IconButton(onClick = {
                        UpscalerModelStore.remove(context)
                        revision++
                    }) { Icon(Icons.Default.Delete, contentDescription = "删除 ${entry.name}", tint = ReferencePalette.TextSecondary) }
                } else {
                    IconButton(
                        onClick = { startDownload(entry) },
                        enabled = downloadingId == null,
                    ) { Icon(Icons.Default.Download, contentDescription = "下载 ${entry.name}", tint = FancyGold) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (upscaling) return@Button
                val source = latestGenerated() ?: run {
                    message = "先生成一张图片，再执行高清放大。"
                    return@Button
                }
                upscaling = true
                message = null
                val entry = if (animeReady) anime else photo
                Thread {
                    val result = UpscalerRuntime.upscaleFile(context, entry, source)
                    upscaling = false
                    if (result == null) {
                        message = "放大失败（模型未就绪或内存不足）。"
                    } else {
                        upscaledPath = result.second.absolutePath
                        message = "高清放大完成：${result.first.width}×${result.first.height}"
                        WorldStore(context).use { store ->
                            store.saveCreativeAsset(
                                CreativeAsset(0, result.second.absolutePath, "image", "upscaler", "hd upscale", null, "", null, null, "ready", "", System.currentTimeMillis()),
                            )
                        }
                    }
                }.start()
            },
            enabled = (photoReady || animeReady) && !upscaling,
            colors = ButtonDefaults.buttonColors(
                containerColor = ReferencePalette.Gold,
                contentColor = ReferencePalette.PageBg,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("upscaler-run"),
        ) {
            if (upscaling) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ReferencePalette.PageBg)
                Spacer(Modifier.width(8.dp))
                Text("放大中…", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            } else {
                Text("高清放大最近生成图 4×", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
        message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = ReferencePalette.TextSecondary, fontSize = 11.sp)
        }
        upscaledPath?.let {
            Text(
                "已保存：${File(it).name}",
                color = FancyGold,
                fontSize = 11.sp,
            )
        }
    }
}
