package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class HfModelEntry(val id: String, val title: String, val size: String, val file: String, val role: String, val url: String)

internal val HfModelCatalog = listOf(
    HfModelEntry("scrfd-10g", "SCRFD 10G face detector", "MNN · ~3 MB", "scrfd_10g.fp16.mnn", "detector", "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/scrfd_10g.fp16.mnn"),
    HfModelEntry("arcface-w600k-r50", "ArcFace W600K R50", "MNN · ~166 MB", "arcface_w600k_r50.fp16.mnn", "embedding", "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/arcface_w600k_r50.fp16.mnn"),
    HfModelEntry("inswapper-128", "InsightFace inswapper_128", "MNN · ~529 MB", "inswapper_128.fp16.mnn", "swapper", "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/inswapper_128.fp16.mnn"),
    HfModelEntry("codeformer", "CodeFormer face restoration", "MNN · ~350 MB", "codeformer.fp16.mnn", "restore", "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/codeformer.fp16.mnn"),
)

internal data class AuraSwapRequest(val source: File, val target: File, val swapper: File, val detector: File, val embedding: File, val restore: File)

internal interface AuraSwapBackend {
    fun run(context: Context, request: AuraSwapRequest): File
}

internal data class AuraFace(val score: Float, val points: List<PointF>)

/** Small, deterministic image bridge for the MNN graphs. Fusion/masking remains a separate step. */
internal object AuraImagePipeline {
    private val template112 = arrayOf(
        PointF(38.2946f, 51.6963f), PointF(73.5318f, 51.5014f), PointF(56.0252f, 71.7366f),
        PointF(41.5493f, 92.3655f), PointF(70.7299f, 92.2041f),
    )

    fun bitmapToChw(bitmap: Bitmap, size: Int, mean: Float = 127.5f, scale: Float = 127.5f): FloatArray {
        val out = FloatArray(3 * size * size)
        val pixels = IntArray(size * size)
        Bitmap.createScaledBitmap(bitmap, size, size, true).getPixels(pixels, 0, size, 0, 0, size, size)
        val plane = size * size
        for (i in pixels.indices) {
            out[i] = (Color.red(pixels[i]) - mean) / scale
            out[plane + i] = (Color.green(pixels[i]) - mean) / scale
            out[2 * plane + i] = (Color.blue(pixels[i]) - mean) / scale
        }
        return out
    }

    fun parseFaces(raw: FloatArray, width: Int, height: Int): List<AuraFace> {
        val stride = when {
            raw.size >= 15 && raw.size % 15 == 0 -> 15
            raw.size >= 14 && raw.size % 14 == 0 -> 14
            else -> return emptyList()
        }
        return raw.asList().chunked(stride).mapNotNull { row ->
            val scoreIndex = if (stride == 15) 4 else 0
            val score = row[scoreIndex]
            if (score < .35f) return@mapNotNull null
            val boxOffset = if (stride == 15) 0 else 1
            val pointsOffset = if (stride == 15) 5 else 4
            val points = (0 until 5).map { i ->
                val x = row[pointsOffset + i * 2]
                val y = row[pointsOffset + i * 2 + 1]
                PointF(if (x <= 1f) x * width else x, if (y <= 1f) y * height else y)
            }
            if (points.any { it.x !in 0f..width.toFloat() || it.y !in 0f..height.toFloat() }) null
            else AuraFace(score, points)
        }.sortedByDescending { it.score }
    }

    fun align(bitmap: Bitmap, face: AuraFace, size: Int): Bitmap {
        val source = face.points
        val target = template112.map { PointF(it.x * size / 112f, it.y * size / 112f) }
        val matrix = Matrix()
        // Android's affine mapping uses the first three correspondences; the remaining two
        // points are still used by the detector and keep the template compatible with InsightFace.
        val src = source.take(3).flatMap { listOf(it.x, it.y) }.toFloatArray()
        val dst = target.take(3).flatMap { listOf(it.x, it.y) }.toFloatArray()
        check(matrix.setPolyToPoly(src, 0, dst, 0, 3)) { "人脸仿射变换失败" }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return Bitmap.createScaledBitmap(transformed, size, size, true)
    }

    fun chwToBitmap(chw: FloatArray, size: Int): Bitmap {
        require(chw.size >= 3 * size * size) { "输出 Tensor 尺寸不是 RGB CHW" }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val plane = size * size
        for (i in pixels.indices) {
            fun channel(offset: Int) = ((chw[offset + i] * 127.5f + 127.5f).coerceIn(0f, 255f)).toInt()
            pixels[i] = Color.argb(255, channel(0), channel(plane), channel(2 * plane))
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /** Maps the restored 512px face back onto the target and blends only its feathered ellipse. */
    fun fuseRestored(target: Bitmap, face: AuraFace, restored: Bitmap): Bitmap {
        require(restored.width == 512 && restored.height == 512) { "CodeFormer 输出必须是 512x512" }
        val masked = restored.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(512 * 512)
        masked.getPixels(pixels, 0, 512, 0, 0, 512, 512)
        val cx = 56f * 512f / 112f
        val cy = 70f * 512f / 112f
        val rx = 42f * 512f / 112f
        val ry = 52f * 512f / 112f
        for (y in 0 until 512) for (x in 0 until 512) {
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            val alpha = ((1f - distance) / .18f).coerceIn(0f, 1f)
            val old = pixels[y * 512 + x]
            pixels[y * 512 + x] = Color.argb((Color.alpha(old) * alpha).toInt(), Color.red(old), Color.green(old), Color.blue(old))
        }
        masked.setPixels(pixels, 0, 512, 0, 0, 512, 512)

        val source = template112.map { PointF(it.x * 512f / 112f, it.y * 512f / 112f) }
        val matrix = Matrix()
        val src = source.take(3).flatMap { listOf(it.x, it.y) }.toFloatArray()
        val dst = face.points.take(3).flatMap { listOf(it.x, it.y) }.toFloatArray()
        check(matrix.setPolyToPoly(src, 0, dst, 0, 3)) { "人脸逆仿射变换失败" }
        val output = target.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(output).drawBitmap(masked, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    fun run(source: Bitmap, target: Bitmap): Bitmap {
        val targetFace = parseFaces(MnnNative.nativeDetect(bitmapToChw(target, 640)), target.width, target.height).firstOrNull()
            ?: error("SCRFD 未检测到 Target 人脸")
        val sourceFace = parseFaces(MnnNative.nativeDetect(bitmapToChw(source, 640)), source.width, source.height).firstOrNull()
            ?: error("SCRFD 未检测到 Source 人脸")
        val alignedTarget = align(target, targetFace, 512)
        val embedding = MnnNative.nativeEmbed(bitmapToChw(align(source, sourceFace, 112), 112))
        val swapped = MnnNative.nativeSwap(bitmapToChw(alignedTarget, 128), embedding)
        val swappedBitmap = chwToBitmap(swapped, 128)
        val restored = chwToBitmap(MnnNative.nativeRestore(bitmapToChw(swappedBitmap, 512), .5f), 512)
        return fuseRestored(target, targetFace, restored)
    }
}

/**
 * Loads the three authorized MNN graphs. Image preprocessing/postprocessing is intentionally
 * separate: this method never fabricates an output image.
 */
internal object MnnAuraBackend : AuraSwapBackend {
    override fun run(context: Context, request: AuraSwapRequest): File {
        require(request.source.isFile && request.target.isFile) { "Source 和 Target 图片不存在" }
        require(request.swapper.isFile && request.detector.isFile && request.embedding.isFile && request.restore.isFile) { "Aura 四个模型必须同时安装" }
        val error = runCatching {
            MnnNative.nativeLoad(
                request.detector.absolutePath,
                request.embedding.absolutePath,
                request.swapper.absolutePath,
                request.restore.absolutePath,
                false,
            )
        }.getOrElse { "MNN JNI 加载失败：${it.message ?: it::class.simpleName}" }
        if (error.isNotEmpty()) throw IllegalStateException("MNN 模型加载失败：$error")
        val sourceBitmap = android.graphics.BitmapFactory.decodeFile(request.source.absolutePath)
            ?: error("Source 图片无法解码")
        val targetBitmap = android.graphics.BitmapFactory.decodeFile(request.target.absolutePath)
            ?: error("Target 图片无法解码")
        val output = try {
            AuraImagePipeline.run(sourceBitmap, targetBitmap)
        } finally {
            MnnNative.nativeUnload()
        }
        val result = File(context.cacheDir, "aura-${System.currentTimeMillis()}.png")
        result.outputStream().use { stream -> check(output.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Aura 输出保存失败" } }
        return result
    }
}

internal object HfModelStore {
    private const val PREFS = "hf_model_store"
    private const val KEY_URL = "download_url"

    fun directory(context: Context) = File(context.filesDir, "models").apply { mkdirs() }
    fun installed(context: Context): List<File> = directory(context).listFiles()?.filter { it.isFile } ?: emptyList()
    fun downloadUrl(context: Context): String = context.getSharedPreferences(PREFS, 0).getString(KEY_URL, "").orEmpty()
    fun saveDownloadUrl(context: Context, value: String) = context.getSharedPreferences(PREFS, 0).edit().putString(KEY_URL, value.trim()).apply()

    internal fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    internal fun downloadFile(
        context: Context,
        url: String,
        targetName: String,
        expectedSha256: String? = null,
        maxAttempts: Int = 3,
    ): File {
        require(url.startsWith("https://") || url.startsWith("http://")) { "请输入有效的 HTTP(S) 下载地址" }
        require(targetName.matches(Regex("[A-Za-z0-9._-]+"))) { "模型文件名无效" }
        val target = File(directory(context), targetName)
        val expected = expectedSha256?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        var lastError: Throwable? = null
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            val temp = File(directory(context), ".$targetName.part-$attempt")
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 60_000
                    connection.instanceFollowRedirects = true
                    connection.connect()
                    if (connection.responseCode !in 200..299) error("HF HTTP ${connection.responseCode}")
                    connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                } finally {
                    connection.disconnect()
                }
                if (temp.length() == 0L) error("下载文件为空")
                if (expected != null && sha256(temp) != expected) error("SHA-256 校验失败")
                if (target.exists() && !target.delete()) error("无法替换旧模型文件")
                if (!temp.renameTo(target)) error("无法原子保存模型文件")
                return target
            } catch (error: Throwable) {
                lastError = error
                temp.delete()
                if (attempt + 1 == maxAttempts.coerceAtLeast(1)) throw error
            }
        }
        throw IOException(lastError?.message ?: "下载失败")
    }

    fun download(context: Context, url: String, targetName: String, expectedSha256: String? = null, onDone: (Result<File>) -> Unit) {
        Thread {
            val result = runCatching { downloadFile(context, url, targetName, expectedSha256) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(result) }
        }.start()
    }

    internal fun validateModel(file: File) = require(file.isFile && file.length() > 8) { "模型文件不存在或为空" }
}

@Composable
internal fun AuraSwapScreen(contentPadding: PaddingValues, onBack: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("aura") }
    Column(Modifier.fillMaxSize().background(FancyInk).padding(contentPadding).testTag("aura-swap-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("aura-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = FancyCream) }
            Text("Aura Swap", color = FancyCream, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { tab = if (tab == "aura") "store" else "aura" }, modifier = Modifier.testTag("aura-store-toggle")) {
                Icon(if (tab == "aura") Icons.Default.Download else Icons.Default.Refresh, "切换", tint = FancyGold)
            }
        }
        if (tab == "store") ModelStoreContent(context) else AuraSwapContent(context)
    }
}

@Composable
private fun AuraSwapContent(context: Context) {
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Swap the aura between two images.", color = FancyCream)
        Text("Source image", color = FancyGold, fontSize = 12.sp)
        OutlinedTextField(source, { source = it }, label = { Text("Source path") }, modifier = Modifier.fillMaxWidth().testTag("aura-source"), colors = auraFieldColors())
        Text("Target image", color = FancyGold, fontSize = 12.sp)
        OutlinedTextField(target, { target = it }, label = { Text("Target path") }, modifier = Modifier.fillMaxWidth().testTag("aura-target"), colors = auraFieldColors())
        Button(onClick = {
            val sourceFile = File(source.trim()); val targetFile = File(target.trim())
            status = when {
                !sourceFile.isFile || !targetFile.isFile -> "请选择存在的 Source 和 Target 图片。"
                !HfModelCatalog.all { model -> File(HfModelStore.directory(context), model.file).isFile } -> "请先下载 SCRFD、ArcFace、inswapper_128 和 CodeFormer 四个模型。"
                else -> runCatching {
                    val models = HfModelStore.installed(context)
                    val swapper = models.first { it.name == "inswapper_128.fp16.mnn" }
                    val detector = models.first { it.name == "scrfd_10g.fp16.mnn" }
                    val embedding = models.first { it.name == "arcface_w600k_r50.fp16.mnn" }
                    val restore = models.first { it.name == "codeformer.fp16.mnn" }
                    HfModelCatalog.forEach { model -> HfModelStore.validateModel(File(HfModelStore.directory(context), model.file)) }
                    MnnAuraBackend.run(context, AuraSwapRequest(sourceFile, targetFile, swapper, detector, embedding, restore))
                    "Aura Swap 完成"
                }.getOrElse { "Aura Swap 失败：${it.message}" }
            }
        }, modifier = Modifier.fillMaxWidth().testTag("aura-run"), colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk)) { Text("Run Aura Swap") }
        status?.let { Text(it, color = if (it.startsWith("Aura Swap 完成")) FancyGold else FancyCream, modifier = Modifier.testTag("aura-status")) }
    }
}

@Composable
private fun ModelStoreContent(context: Context) {
    var url by rememberSaveable { mutableStateOf(HfModelStore.downloadUrl(context)) }
    var downloading by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Model Store", color = FancyCream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("需要 SCRFD + ArcFace + inswapper_128 + CodeFormer；仅下载已获授权的模型。", color = FancyCream, fontSize = 12.sp)
        HfModelCatalog.forEach { model ->
            Row(Modifier.fillMaxWidth().background(FancyNavyMid, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(model.title, color = FancyCream, fontWeight = FontWeight.SemiBold); Text(model.size, color = FancyGoldDim, fontSize = 11.sp) }
                Icon(Icons.Default.Check, null, tint = if (HfModelStore.installed(context).any { it.name == model.file }) FancyGold else FancyGoldDim)
                Button(onClick = {
                    downloading = true; status = null
                    HfModelStore.download(context, model.url, model.file) { result ->
                        downloading = false
                        status = result.fold({ "下载完成：${it.name}" }, { "下载失败：${it.message}" })
                    }
                }, enabled = !downloading, colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk)) { Text("下载") }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(url, { url = it; HfModelStore.saveDownloadUrl(context, it) }, label = { Text("自定义 SCRFD 模型 URL") }, modifier = Modifier.fillMaxWidth().testTag("hf-url"), colors = auraFieldColors())
        Button(onClick = {
            status = null; downloading = true
            HfModelStore.download(context, url, HfModelCatalog.first().file) { result -> downloading = false; status = result.fold({ "下载完成：${it.name}（尚未完成推理配对）" }, { "下载失败：${it.message}" }) }
        }, enabled = !downloading, modifier = Modifier.fillMaxWidth().testTag("hf-download"), colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk)) {
            Icon(Icons.Default.Download, null); Text(if (downloading) "Downloading…" else "Download from Hugging Face")
        }
        status?.let { Text(it, color = FancyCream, modifier = Modifier.testTag("hf-status")) }
        Text("已安装模型：${HfModelStore.installed(context).joinToString { it.name }.ifBlank { "暂无" }}", color = FancyCream, fontSize = 12.sp, modifier = Modifier.testTag("hf-installed"))
    }
}

@Composable
private fun auraFieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = FancyCream, unfocusedTextColor = FancyCream, focusedLabelColor = FancyGold, unfocusedLabelColor = FancyCream.copy(alpha = .7f), focusedBorderColor = FancyGold, unfocusedBorderColor = FancyGoldDim, cursorColor = FancyGold)
