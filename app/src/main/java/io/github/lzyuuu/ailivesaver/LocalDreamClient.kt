package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal data class LocalDreamImage(
    val path: String,
    val seed: Long,
)

internal data class LocalDreamImportParameters(
    val prompt: String = "",
    val negativePrompt: String = "",
    val seed: Long? = null,
    val steps: Int? = null,
    val cfg: Double? = null,
    val scheduler: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

internal fun parseLocalDreamParameters(raw: String): LocalDreamImportParameters {
    val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val json = runCatching { JSONObject(text) }.getOrNull()
    fun value(vararg aliases: String): String {
        val candidate = json?.let { findJsonValue(it, aliases.map(String::lowercase).toSet()) }
        if (candidate != null) return candidate.toString().trim().trim('"')
        return aliases.asSequence()
            .map { key ->
                Regex("(?im)^\\s*${key.replace("_", "[ _]")}\\s*[:=]\\s*(.+)$")
                    .find(text)?.groupValues?.getOrNull(1)?.trim()?.trim('"', ',')
            }
            .filterNotNull()
            .firstOrNull()
            .orEmpty()
    }
    fun number(vararg aliases: String): String = value(*aliases).substringBefore(" ").trim()
    return LocalDreamImportParameters(
        prompt = value("prompt", "positive_prompt", "positive prompt", "text_prompt"),
        negativePrompt = value("negative_prompt", "negative prompt", "negative"),
        seed = number("seed").toLongOrNull(),
        steps = number("steps", "step_count").toIntOrNull(),
        cfg = number("cfg", "cfg_scale", "guidance_scale").toDoubleOrNull(),
        scheduler = value("scheduler", "sampler"),
        width = number("width").toIntOrNull(),
        height = number("height").toIntOrNull(),
    )
}

private fun findJsonValue(value: JSONObject, aliases: Set<String>, depth: Int = 0): Any? {
    if (depth > 4) return null
    val keys = value.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val candidate = value.opt(key)
        if (key.lowercase() in aliases && candidate != JSONObject.NULL) return candidate
        when (candidate) {
            is JSONObject -> findJsonValue(candidate, aliases, depth + 1)?.let { return it }
            is JSONArray -> for (index in 0 until candidate.length()) {
                val nested = candidate.opt(index)
                if (nested is JSONObject) {
                    findJsonValue(nested, aliases, depth + 1)?.let { return it }
                }
            }
        }
    }
    return null
}

internal fun storageAllowsGeneration(availableBytes: Long): Boolean =
    availableBytes >= 256L * 1024 * 1024

internal fun isLocalDreamUnavailable(error: Throwable): Boolean =
    error is ConnectException ||
        error is SocketTimeoutException ||
        error.message.orEmpty().contains("failed to connect", ignoreCase = true) ||
        error.message.orEmpty().contains("connection refused", ignoreCase = true)

internal fun importUserImageToCache(context: Context, uri: Uri): String {
    if (!storageAllowsGeneration(StatFs(context.cacheDir.path).availableBytes)) {
        throw IOException("存储空间不足")
    }
    val file = File(context.cacheDir, "import-${UUID.randomUUID()}")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 25L * 1024 * 1024) throw IOException("图片超过 25 MB")
                    output.write(buffer, 0, count)
                }
            }
        } ?: throw IOException("无法读取图片")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("不是有效图片")
        return file.path
    } catch (error: Throwable) {
        file.delete()
        throw error
    }
}

internal fun persistImportedImage(context: Context, cachedPath: String): String {
    val cached = File(cachedPath)
    if (!cached.isFile || cached.parentFile != context.cacheDir) throw IOException("无效图片")
    val directory = File(context.filesDir, "media").apply { mkdirs() }
    val target = File(directory, "${UUID.randomUUID()}.image")
    if (!cached.renameTo(target)) {
        cached.copyTo(target)
        cached.delete()
    }
    return target.path
}

internal object LocalDreamClient {
    private const val BASE_URL = "http://127.0.0.1:8081"

    fun probe(callback: (Result<Int>) -> Unit) {
        Thread {
            val result = runCatching {
                val body = post("/tokenize", JSONObject().put("prompt", "test"))
                JSONObject(body).getInt("max_length")
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    fun generate(
        context: Context,
        job: MediaJob,
        onProgress: (Int, Int) -> Unit,
        callback: (Result<LocalDreamImage>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val connection = connection("/generate")
                val request = JSONObject()
                    .put("prompt", job.prompt)
                    .put("negative_prompt", job.negativePrompt)
                    .put("steps", job.steps)
                    .put("cfg", job.cfg)
                    .put("scheduler", job.scheduler)
                    .put("width", job.width)
                    .put("height", job.height)
                job.seed?.let { request.put("seed", it) }
                try {
                    connection.outputStream.use {
                        it.write(request.toString().toByteArray())
                    }
                    if (connection.responseCode !in 200..299) {
                        throw IOException("Local Dream HTTP ${connection.responseCode}")
                    }
                    var completed: JSONObject? = null
                    connection.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (!line.startsWith("data:")) return@forEach
                            val event = JSONObject(line.removePrefix("data:").trim())
                            when (event.optString("type")) {
                                "progress" -> {
                                    val step = event.optInt("step")
                                    val total = event.optInt("total_steps")
                                    Handler(Looper.getMainLooper()).post {
                                        onProgress(step, total)
                                    }
                                }
                                "complete" -> completed = event
                                "error" -> throw IOException(
                                    event.optString("message", "Local Dream generation failed"),
                                )
                            }
                        }
                    }
                    saveImage(context, completed ?: throw IOException("Missing complete event"))
                } finally {
                    connection.disconnect()
                }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun post(path: String, body: JSONObject): String {
        val connection = connection(path)
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode !in 200..299) {
                throw IOException("Local Dream HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun connection(path: String) =
        (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3_000
            readTimeout = 10 * 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

    private fun saveImage(context: Context, event: JSONObject): LocalDreamImage {
        val width = event.getInt("width")
        val height = event.getInt("height")
        val channels = event.optInt("channels", 3)
        if (width !in 8..2048 || height !in 8..2048 || channels != 3) {
            throw IOException("Unsupported Local Dream image shape")
        }
        val rgb = Base64.decode(event.getString("image"), Base64.DEFAULT)
        if (rgb.size != width * height * channels) throw IOException("Incomplete image data")
        val pixels = IntArray(width * height)
        var source = 0
        for (index in pixels.indices) {
            pixels[index] = (0xFF shl 24) or
                ((rgb[source++].toInt() and 0xFF) shl 16) or
                ((rgb[source++].toInt() and 0xFF) shl 8) or
                (rgb[source++].toInt() and 0xFF)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val directory = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        file.outputStream().use {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                throw IOException("Could not save generated image")
            }
        }
        bitmap.recycle()
        return LocalDreamImage(file.absolutePath, event.optLong("seed"))
    }
}

internal object LocalDreamQueue {
    private val running = AtomicBoolean(false)

    fun resume(
        context: Context,
        onProgress: (Long, Int, Int) -> Unit = { _, _, _ -> },
        onFinished: (Long, Result<LocalDreamImage>) -> Unit = { _, _ -> },
    ) {
        if (!running.compareAndSet(false, true)) return
        processNext(context.applicationContext, onProgress, onFinished)
    }

    private fun processNext(
        context: Context,
        onProgress: (Long, Int, Int) -> Unit,
        onFinished: (Long, Result<LocalDreamImage>) -> Unit,
    ) {
        val store = WorldStore(context)
        val job = store.nextPendingMediaJob()
        if (job == null) {
            store.close()
            running.set(false)
            return
        }
        if (!storageAllowsGeneration(StatFs(context.filesDir.path).availableBytes)) {
            val error = IOException(context.getString(R.string.storage_low_generation_paused))
            store.markMediaFailed(job.postId, error.message.orEmpty())
            store.close()
            running.set(false)
            onFinished(job.postId, Result.failure(error))
            return
        }
        val firstPublication = store.mediaVersions(job.postId).isEmpty()
        store.close()
        LocalDreamClient.generate(
            context = context,
            job = job,
            onProgress = { step, total -> onProgress(job.postId, step, total) },
        ) { generation ->
            val persisted = generation.mapCatching { image ->
                WorldStore(context).use { updateStore ->
                    updateStore.markMediaReady(job.postId, image.path, image.seed)
                }
                image
            }
            if (persisted.isFailure) {
                val error = persisted.exceptionOrNull()!!
                WorldStore(context).use { updateStore ->
                    if (isLocalDreamUnavailable(error)) {
                        updateStore.markMediaWaiting(job.postId, error.message.orEmpty())
                    } else {
                        updateStore.markMediaFailed(job.postId, error.message.orEmpty())
                    }
                }
            } else if (firstPublication) {
                WorldStore(context).use { updateStore ->
                    updateStore.posts("moment").firstOrNull { it.id == job.postId }
                }?.takeIf { it.authorKind == "user" && it.aiResponsesEnabled }?.let { post ->
                    WorldEngine.respondToPost(
                        context,
                        post.id,
                        post.kind,
                        buildString {
                            append(post.body)
                            if (post.mediaDescription.isNotBlank()) {
                                append("\nMedia description: ${post.mediaDescription}")
                            }
                        },
                        post.audience,
                        post.audienceCharacterIds,
                    ) {}
                }
            }
            onFinished(job.postId, persisted)
            if (persisted.isSuccess) {
                processNext(context, onProgress, onFinished)
            } else {
                running.set(false)
            }
        }
    }
}
