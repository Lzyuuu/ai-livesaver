package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapFactory
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

internal data class HfModelEntry(val id: String, val title: String, val size: String, val file: String, val role: String, val url: String, val sha256: String)

/**
 * 目录模型必须携带可信 SHA-256。取值来源：Hugging Face LFS 元数据
 * （huggingface.co/api/models/Mr-J-369/Fancy-AI/tree/main 的 lfs.oid，与 resolve URL 的
 * X-Linked-Etag 完全一致，2026-08-04 核对）。生产下载与"是否可用"的判断一律以该哈希为准，
 * 不编造、不放过任何校验失败的文件。
 */
internal val HfModelCatalog = listOf(
    HfModelEntry("scrfd-10g", "SCRFD 10G face detector", "MNN · ~8 MB", "scrfd_10g.fp16.mnn", "detector", "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/scrfd_10g.fp16.mnn", "a799477ec3eb87f09b380f31452adf5f26f837e07aacfc6b75474f9f1b6056d1"),
    HfModelEntry("arcface-w600k-r50", "ArcFace W600K R50", "MNN · ~83 MB", "arcface_w600k_r50.fp16.mnn", "embedding", "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/arcface_w600k_r50.fp16.mnn", "09a0e01fb942cb237c5204d166a48e163d4a6d601b7b5c33eec7c210c36e918b"),
    HfModelEntry("inswapper-128", "InsightFace inswapper_128", "MNN · ~264 MB", "inswapper_128.fp16.mnn", "swapper", "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/inswapper_128.fp16.mnn", "b4bde7d0ea7ca949cd90384ea2520bc7bacba464dccd4c887a98b987b9328058"),
    HfModelEntry("codeformer", "CodeFormer face restoration", "MNN · ~180 MB", "codeformer.fp16.mnn", "restore", "https://huggingface.co/Mr-J-369/Fancy-AI/resolve/main/codeformer.fp16.mnn", "676632af278ed6db02894b239bd17e7008e3c562f876def3fe98753981d6ebd9"),
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

    fun run(source: Bitmap, target: Bitmap, useRestore: Boolean = true): Bitmap {
        val targetFace = parseFaces(MnnNative.nativeDetect(bitmapToChw(target, 640)), target.width, target.height).firstOrNull()
            ?: error("SCRFD 未检测到 Target 人脸")
        val sourceFace = parseFaces(MnnNative.nativeDetect(bitmapToChw(source, 640)), source.width, source.height).firstOrNull()
            ?: error("SCRFD 未检测到 Source 人脸")
        val alignedTarget = align(target, targetFace, 512)
        val embedding = MnnNative.nativeEmbed(bitmapToChw(align(source, sourceFace, 112), 112))
        val swapped = MnnNative.nativeSwap(bitmapToChw(alignedTarget, 128), embedding)
        val swappedBitmap = chwToBitmap(swapped, 128)
        val restored = if (useRestore) {
            chwToBitmap(MnnNative.nativeRestore(bitmapToChw(swappedBitmap, 512), .5f), 512)
        } else {
            Bitmap.createScaledBitmap(swappedBitmap, 512, 512, true)
        }
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
            if ((context.getSystemService(android.app.ActivityManager::class.java)?.memoryClass ?: 0) >= 1024) {
                MnnNative.nativeLoad(request.detector.absolutePath, request.embedding.absolutePath, request.swapper.absolutePath, request.restore.absolutePath, false)
            } else {
                MnnNative.nativeLoadWithoutRestore(request.detector.absolutePath, request.embedding.absolutePath, request.swapper.absolutePath)
            }
        }.getOrElse { "MNN JNI 加载失败：${it.message ?: it::class.simpleName}" }
        if (error.isNotEmpty()) throw IllegalStateException("MNN 模型加载失败：$error")
        val sourceBitmap = android.graphics.BitmapFactory.decodeFile(request.source.absolutePath)
            ?: error("Source 图片无法解码")
        val targetBitmap = android.graphics.BitmapFactory.decodeFile(request.target.absolutePath)
            ?: error("Target 图片无法解码")
        val output = try {
            AuraImagePipeline.run(sourceBitmap, targetBitmap, (context.getSystemService(android.app.ActivityManager::class.java)?.memoryClass ?: 0) >= 1024)
        } finally {
            MnnNative.nativeUnload()
        }
        val result = File(context.cacheDir, "aura-${System.currentTimeMillis()}.png")
        result.outputStream().use { stream -> check(output.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Aura 输出保存失败" } }
        return result
    }
}

/** 从 Hugging Face LFS 元数据解析官方 SHA-256；无法可信获得时返回 null（调用方必须阻断，禁止无校验安装）。 */
internal fun interface HfShaResolver {
    fun resolveSha256(url: String): String?
}

/**
 * HEAD 请求 HF resolve URL 读取 LFS 元数据：302 响应的 X-Linked-Etag 就是 LFS 对象的官方
 * SHA-256（与 tree API 的 lfs.oid 一致），无需下载整模型。旧版 CDN 的 Location 里也嵌有 OID，
 * 作为回退。
 */
internal object HfHttpShaResolver : HfShaResolver {
    private val sha256Hex = Regex("[0-9a-f]{64}")

    override fun resolveSha256(url: String): String? {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 8_000
            }
        } catch (_: Throwable) {
            return null
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 300..399) return null
            val etag = connection.getHeaderField("X-Linked-Etag")?.trim()?.trim('"')?.lowercase()
            if (etag != null && sha256Hex.matches(etag)) etag
            else locationOid(connection.getHeaderField("Location"))
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** 旧版 CDN Location（形如 cdn-lfs.huggingface.co/repos/…/{oid}/{file}）中解析 LFS OID。 */
    private fun locationOid(location: String?): String? = location
        ?.substringBefore('?')
        ?.split('/')
        ?.firstOrNull { sha256Hex.matches(it) }
        ?.lowercase()
}

internal object HfModelStore {
    private const val PREFS = "hf_model_store"
    private const val KEY_URL = "download_url"
    private const val KEY_VERIFIED_PREFIX = "verified_"
    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    fun directory(context: Context) = File(context.filesDir, "models").apply { mkdirs() }

    /** 只返回内容通过可信 SHA-256 校验的文件；损坏或不可校验的文件一律不算已安装、不可用于推理。 */
    fun installed(context: Context): List<File> =
        directory(context).listFiles()?.filter { it.isFile && isVerified(context, it) } ?: emptyList()

    /** 目录模型可用 = 文件存在且内容与目录钉住的官方 SHA-256 一致。 */
    fun isUsable(context: Context, model: HfModelEntry): Boolean =
        isVerified(context, File(directory(context), model.file))

    /** 可信 SHA-256：目录模型取钉住的官方值；不在目录中的文件无可信哈希，视为不可校验。 */
    fun trustedSha256(context: Context, fileName: String): String? =
        HfModelCatalog.firstOrNull { it.file == fileName }?.sha256

    fun downloadUrl(context: Context): String = context.getSharedPreferences(PREFS, 0).getString(KEY_URL, "").orEmpty()
    fun saveDownloadUrl(context: Context, value: String) = context.getSharedPreferences(PREFS, 0).edit().putString(KEY_URL, value.trim()).apply()

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun matchesTrustedSha(file: File, expectedSha256: String): Boolean =
        file.isFile && file.length() > 0L && sha256(file) == expectedSha256

    /**
     * 文件内容与可信 SHA-256 是否一致。校验通过后以 (sha, size, mtime) 指纹写入偏好设置，
     * 避免每次 UI 组合都对数百 MB 模型重算；文件被替换或官方哈希变化时指纹失效会重新校验。
     */
    internal fun isVerified(context: Context, file: File): Boolean {
        if (!file.isFile) return false
        val expected = trustedSha256(context, file.name) ?: return false
        val prefs = context.getSharedPreferences(PREFS, 0)
        val fingerprint = "${file.length()}-${file.lastModified()}"
        if (prefs.getString(KEY_VERIFIED_PREFIX + file.name, null) == "$expected|$fingerprint") return true
        val ok = matchesTrustedSha(file, expected)
        if (ok) prefs.edit().putString(KEY_VERIFIED_PREFIX + file.name, "$expected|$fingerprint").apply()
        return ok
    }

    private fun markVerified(context: Context, file: File, expectedSha256: String) {
        context.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY_VERIFIED_PREFIX + file.name, "$expectedSha256|${file.length()}-${file.lastModified()}")
            .apply()
    }

    internal fun downloadFile(dir: File, url: String, targetName: String, expectedSha256: String, maxAttempts: Int = 3): File {
        require(url.startsWith("https://") || url.startsWith("http://")) { "请输入有效的 HTTP(S) 下载地址" }
        require(targetName.matches(Regex("[A-Za-z0-9._-]+"))) { "模型文件名无效" }
        val expected = expectedSha256.trim().lowercase()
        require(SHA256_HEX.matches(expected)) { "必须提供可信的 SHA-256 校验和" }
        val target = File(dir, targetName)
        var lastError: Throwable? = null
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            val temp = File(dir, ".$targetName.part-$attempt")
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
                if (sha256(temp) != expected) error("SHA-256 校验失败")
                if (target.exists() && !target.delete()) error("无法替换旧模型文件")
                if (!temp.renameTo(target)) error("无法原子保存模型文件")
                return target
            } catch (error: Throwable) {
                lastError = error
                temp.delete()
                if (attempt + 1 == maxAttempts.coerceAtLeast(1)) throw IOException(error.message ?: "下载失败", error)
            }
        }
        throw IOException(lastError?.message ?: "下载失败")
    }

    /** 生产下载入口：trustedSha256 必填（目录模型用钉住的官方 SHA-256），成功后再记入已校验指纹。 */
    fun download(context: Context, url: String, targetName: String, trustedSha256: String, onDone: (Result<File>) -> Unit) {
        Thread {
            val result = runCatching {
                val target = downloadFile(directory(context), url, targetName, trustedSha256)
                markVerified(context, target, trustedSha256)
                target
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(result) }
        }.start()
    }

    /** 自定义 URL 路径：先经 HTTP HEAD 从 HF LFS 元数据解析官方 SHA-256；无法可信获得则阻断，绝不无校验安装。 */
    fun downloadFromHf(context: Context, url: String, targetName: String, resolver: HfShaResolver = HfHttpShaResolver, onDone: (Result<File>) -> Unit) {
        Thread {
            val result = runCatching { downloadWithResolvedSha(directory(context), url, targetName, resolver) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onDone(result) }
        }.start()
    }

    internal fun downloadWithResolvedSha(dir: File, url: String, targetName: String, resolver: HfShaResolver, maxAttempts: Int = 3): File {
        val trusted = resolver.resolveSha256(url)
            ?: throw IOException("无法从 HF LFS 元数据获取可信 SHA-256，已阻断下载（禁止无校验安装）")
        return downloadFile(dir, url, targetName, trusted, maxAttempts)
    }

    internal fun validateModel(context: Context, file: File, expectedSha256: String): Unit =
        require(file.isFile && file.length() > 8 && isVerified(context, file)) { "模型文件校验失败（SHA-256 不匹配）" }
}

@Composable
internal fun AuraSwapScreen(contentPadding: PaddingValues, onBack: () -> Unit, onOpenGallery: () -> Unit = {}) {
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
        if (tab == "store") ModelStoreContent(context) else AuraSwapContent(context, onOpenGallery)
    }
}

@Composable
private fun AuraSwapContent(context: Context, onOpenGallery: () -> Unit) {
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf("source") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val file = File(context.filesDir, "media/import-${System.currentTimeMillis()}.bin").apply { parentFile?.mkdirs() }
        runCatching { context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use(input::copyTo) } }
            .onSuccess { if (picking == "source") source = file.absolutePath else target = file.absolutePath }
    }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Swap the aura between two images.", color = FancyCream)
        Text("Source image", color = FancyGold, fontSize = 12.sp)
        source.takeIf { it.isNotBlank() }?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap()?.let { bitmap -> Image(bitmap, "Source thumbnail", Modifier.size(96.dp), contentScale = ContentScale.Crop) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(source, {}, label = { Text("Source") }, modifier = Modifier.weight(1f).testTag("aura-source"), colors = auraFieldColors(), readOnly = true)
            Button(onClick = { picking = "source"; picker.launch("image/*") }) { Text("系统选择") }
        }
        Text("Target image", color = FancyGold, fontSize = 12.sp)
        target.takeIf { it.isNotBlank() }?.let { path -> BitmapFactory.decodeFile(path)?.asImageBitmap()?.let { bitmap -> Image(bitmap, "Target thumbnail", Modifier.size(96.dp), contentScale = ContentScale.Crop) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(target, {}, label = { Text("Target") }, modifier = Modifier.weight(1f).testTag("aura-target"), colors = auraFieldColors(), readOnly = true)
            Button(onClick = { picking = "target"; picker.launch("image/*") }) { Text("系统选择") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { val old = source; source = target; target = old }) { Text("交换") }
            TextButton(onClick = { source = ""; target = "" }) { Text("清空") }
            TextButton(onClick = onOpenGallery) { Text("Gallery") }
        }
        Button(onClick = {
            val sourceFile = File(source.trim()); val targetFile = File(target.trim())
            status = when {
                !sourceFile.isFile || !targetFile.isFile -> "请选择存在的 Source 和 Target 图片。"
                !HfModelCatalog.all { model -> HfModelStore.isUsable(context, model) } -> "请先下载并通过 SHA-256 校验 SCRFD、ArcFace、inswapper_128 和 CodeFormer 四个模型。"
                else -> runCatching {
                    val models = HfModelStore.installed(context)
                    val swapper = models.first { it.name == "inswapper_128.fp16.mnn" }
                    val detector = models.first { it.name == "scrfd_10g.fp16.mnn" }
                    val embedding = models.first { it.name == "arcface_w600k_r50.fp16.mnn" }
                    val restore = models.first { it.name == "codeformer.fp16.mnn" }
                    HfModelCatalog.forEach { model -> HfModelStore.validateModel(context, File(HfModelStore.directory(context), model.file), model.sha256) }
                    val cacheOutput = MnnAuraBackend.run(context, AuraSwapRequest(sourceFile, targetFile, swapper, detector, embedding, restore))
                    val persistent = File(context.filesDir, "media/aura-${System.currentTimeMillis()}.png").apply {
                        parentFile?.mkdirs()
                        cacheOutput.inputStream().use { input -> outputStream().use { output -> input.copyTo(output) } }
                    }
                    WorldStore(context).use { store ->
                        val sourceAsset = store.queryCreativeAssets().firstOrNull { it.pathOrUri == sourceFile.absolutePath }
                        val targetAsset = store.queryCreativeAssets().firstOrNull { it.pathOrUri == targetFile.absolutePath }
                        store.saveCreativeAsset(CreativeAsset(0, persistent.absolutePath, "image", "aura_swap", "Aura face swap", null, "", sourceAsset?.id, targetAsset?.id, "ready", "", System.currentTimeMillis()))
                    }
                    cacheOutput.delete()
                    "Aura Swap 完成：已保存到 Gallery"
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
        Text("需要 SCRFD + ArcFace + inswapper_128 + CodeFormer；仅下载已获授权的模型，安装前以官方 SHA-256 校验。", color = FancyCream, fontSize = 12.sp)
        HfModelCatalog.forEach { model ->
            Row(Modifier.fillMaxWidth().background(FancyNavyMid, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.title, color = FancyCream, fontWeight = FontWeight.SemiBold)
                    Text(model.size, color = FancyGoldDim, fontSize = 11.sp)
                    Text("SHA-256 ${model.sha256.take(8)}…${model.sha256.takeLast(8)}", color = FancyGoldDim, fontSize = 10.sp, modifier = Modifier.testTag("hf-sha-${model.id}"))
                }
                Icon(Icons.Default.Check, null, tint = if (HfModelStore.isUsable(context, model)) FancyGold else FancyGoldDim)
                Button(onClick = {
                    downloading = true; status = null
                    HfModelStore.download(context, model.url, model.file, model.sha256) { result ->
                        downloading = false
                        status = result.fold({ "下载完成：${it.name}（已通过官方 SHA-256 校验）" }, { "下载失败：${it.message}" })
                    }
                }, enabled = !downloading, colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk)) { Text("下载") }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(url, { url = it; HfModelStore.saveDownloadUrl(context, it) }, label = { Text("自定义 SCRFD 模型 URL") }, modifier = Modifier.fillMaxWidth().testTag("hf-url"), colors = auraFieldColors())
        Text("自定义下载会先通过 HTTP HEAD 获取 HF LFS 元数据中的官方 SHA-256；无法可信获得时将阻断下载。", color = FancyGoldDim, fontSize = 11.sp)
        Button(onClick = {
            status = null; downloading = true
            HfModelStore.downloadFromHf(context, url.trim(), HfModelCatalog.first().file) { result ->
                downloading = false
                status = result.fold({ "下载完成：${it.name}（已按解析出的 SHA-256 校验；与官方模型不一致时不会用于推理）" }, { "下载失败：${it.message}" })
            }
        }, enabled = !downloading, modifier = Modifier.fillMaxWidth().testTag("hf-download"), colors = ButtonDefaults.buttonColors(containerColor = FancyGold, contentColor = FancyInk)) {
            Icon(Icons.Default.Download, null); Text(if (downloading) "Downloading…" else "Download from Hugging Face")
        }
        status?.let { Text(it, color = FancyCream, modifier = Modifier.testTag("hf-status")) }
        Text("已安装模型：${HfModelStore.installed(context).joinToString { it.name }.ifBlank { "暂无" }}", color = FancyCream, fontSize = 12.sp, modifier = Modifier.testTag("hf-installed"))
    }
}

@Composable
private fun auraFieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = FancyCream, unfocusedTextColor = FancyCream, focusedLabelColor = FancyGold, unfocusedLabelColor = FancyCream.copy(alpha = .7f), focusedBorderColor = FancyGold, unfocusedBorderColor = FancyGoldDim, cursorColor = FancyGold)
