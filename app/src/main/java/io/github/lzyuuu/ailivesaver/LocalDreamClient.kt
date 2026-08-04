package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val TEMP_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
private val TEMP_CACHE_PREFIXES = setOf(
    "backup-",
    "import-",
    "local-dream-",
    "restore-",
    "rollback-",
)

internal fun isStaleTemporaryCacheFile(
    file: File,
    now: Long,
    maxAgeMs: Long = TEMP_CACHE_MAX_AGE_MS,
): Boolean = file.parentFile?.isDirectory == true &&
    file.name.startsWithAny(TEMP_CACHE_PREFIXES) &&
    file.lastModified() > 0L &&
    now >= file.lastModified() &&
    now - file.lastModified() >= maxAgeMs

internal fun cleanupTemporaryCache(context: Context, now: Long = System.currentTimeMillis()): Int =
    context.cacheDir.listFiles()
        ?.filter { isStaleTemporaryCacheFile(it, now) }
        ?.count { it.deleteRecursively() }
        ?: 0

internal fun clearTemporaryCache(context: Context): Int =
    context.cacheDir.listFiles()
        ?.filter { it.name.startsWithAny(TEMP_CACHE_PREFIXES) }
        ?.count { it.deleteRecursively() }
        ?: 0

private fun String.startsWithAny(prefixes: Set<String>): Boolean = prefixes.any(::startsWith)

internal data class LocalDreamImage(
    val path: String,
    val seed: Long,
    val stats: LocalDreamRunStats,
)

internal data class LocalDreamRunStats(
    val generationTimeMs: Long,
    val firstStepTimeMs: Long?,
    val width: Int,
    val height: Int,
    val recordedAtMs: Long,
)

private const val LOCAL_DREAM_STATS_PREFS = "local_dream_diagnostics"
private const val LOCAL_DREAM_GENERATION_TIME_MS = "generation_time_ms"
private const val LOCAL_DREAM_FIRST_STEP_TIME_MS = "first_step_time_ms"
private const val LOCAL_DREAM_WIDTH = "width"
private const val LOCAL_DREAM_HEIGHT = "height"
private const val LOCAL_DREAM_RECORDED_AT_MS = "recorded_at_ms"

internal object LocalDreamStatsStore {
    fun save(context: Context, stats: LocalDreamRunStats) {
        context.getSharedPreferences(LOCAL_DREAM_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(LOCAL_DREAM_GENERATION_TIME_MS, stats.generationTimeMs)
            .putLong(LOCAL_DREAM_FIRST_STEP_TIME_MS, stats.firstStepTimeMs ?: -1L)
            .putInt(LOCAL_DREAM_WIDTH, stats.width)
            .putInt(LOCAL_DREAM_HEIGHT, stats.height)
            .putLong(LOCAL_DREAM_RECORDED_AT_MS, stats.recordedAtMs)
            .apply()
    }

    fun load(context: Context): LocalDreamRunStats? {
        val preferences = context.getSharedPreferences(LOCAL_DREAM_STATS_PREFS, Context.MODE_PRIVATE)
        if (!preferences.contains(LOCAL_DREAM_GENERATION_TIME_MS)) return null
        return LocalDreamRunStats(
            generationTimeMs = preferences.getLong(LOCAL_DREAM_GENERATION_TIME_MS, 0L),
            firstStepTimeMs = preferences.getLong(LOCAL_DREAM_FIRST_STEP_TIME_MS, -1L)
                .takeIf { it >= 0L },
            width = preferences.getInt(LOCAL_DREAM_WIDTH, 0),
            height = preferences.getInt(LOCAL_DREAM_HEIGHT, 0),
            recordedAtMs = preferences.getLong(LOCAL_DREAM_RECORDED_AT_MS, 0L),
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(LOCAL_DREAM_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}

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

internal sealed class LocalDreamSseEvent {
    data class Progress(val step: Int, val total: Int) : LocalDreamSseEvent()
    data class Complete(val payload: JSONObject) : LocalDreamSseEvent()
    data class Error(val message: String) : LocalDreamSseEvent()
    object Done : LocalDreamSseEvent()
}

internal fun parseLocalDreamSseLine(line: String): LocalDreamSseEvent? {
    if (!line.startsWith("data:")) return null
    val data = line.removePrefix("data:").trim()
    if (data == "[DONE]") return LocalDreamSseEvent.Done
    val event = JSONObject(data)
    return when (event.optString("type")) {
        "progress" -> LocalDreamSseEvent.Progress(
            step = event.optInt("step"),
            total = event.optInt("total_steps"),
        )
        "complete" -> LocalDreamSseEvent.Complete(event)
        "error" -> LocalDreamSseEvent.Error(
            event.optString("message", "Local Dream generation failed"),
        )
        else -> null
    }
}

internal data class LocalDreamRgbImage(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val seed: Long,
)

internal fun decodeLocalDreamRgb(event: JSONObject): LocalDreamRgbImage {
    val width = event.optInt("width", -1)
    val height = event.optInt("height", -1)
    val channels = event.optInt("channels", 3)
    if (width !in 8..2048 || height !in 8..2048 || channels != 3) {
        throw IOException("Unsupported Local Dream image shape")
    }
    val rgb = try {
        Base64.getDecoder().decode(event.optString("image", ""))
    } catch (error: IllegalArgumentException) {
        throw IOException("Invalid image data", error)
    }
    if (rgb.size != width * height * channels) throw IOException("Incomplete image data")
    val pixels = IntArray(width * height)
    var source = 0
    for (index in pixels.indices) {
        pixels[index] = (0xFF shl 24) or
            ((rgb[source++].toInt() and 0xFF) shl 16) or
            ((rgb[source++].toInt() and 0xFF) shl 8) or
            (rgb[source++].toInt() and 0xFF)
    }
    return LocalDreamRgbImage(width, height, pixels, event.optLong("seed"))
}

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
    val size = number("size").toIntOrNull()
    return LocalDreamImportParameters(
        prompt = value("prompt", "positive_prompt", "positive prompt", "text_prompt"),
        negativePrompt = value("negative_prompt", "negative prompt", "negative"),
        seed = number("seed").toLongOrNull(),
        steps = number("steps", "step_count").toIntOrNull(),
        cfg = number("cfg", "cfg_scale", "guidance_scale").toDoubleOrNull(),
        scheduler = value("scheduler", "sampler"),
        width = number("width").toIntOrNull() ?: size,
        height = number("height").toIntOrNull() ?: size,
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

internal fun isLocalDreamUnavailable(error: Throwable): Boolean {
    val message = error.message.orEmpty()
    val normalized = message.lowercase()
    return error is ConnectException ||
        error is SocketTimeoutException ||
        normalized.contains("failed to connect") ||
        normalized.contains("connection refused") ||
        Regex("local dream http 50[234]").containsMatchIn(normalized) ||
        normalized.contains("model not loaded") ||
        normalized.contains("no model loaded") ||
        normalized.contains("model is loading") ||
        normalized.contains("model not ready")
}

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
        try {
            if (!storageAllowsGeneration(StatFs(context.filesDir.path).availableBytes)) {
                throw IOException("存储空间不足")
            }
            cached.copyTo(target)
            cached.delete()
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
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
                val startedAt = SystemClock.elapsedRealtime()
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
                        throw localDreamHttpFailure(connection)
                    }
                    var completed: JSONObject? = null
                    var firstStepAt: Long? = null
                    connection.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            when (val event = parseLocalDreamSseLine(line)) {
                                is LocalDreamSseEvent.Progress -> {
                                    if (firstStepAt == null) firstStepAt = SystemClock.elapsedRealtime()
                                    Handler(Looper.getMainLooper()).post {
                                        onProgress(event.step, event.total)
                                    }
                                }
                                is LocalDreamSseEvent.Complete -> completed = event.payload
                                is LocalDreamSseEvent.Error -> throw IOException(event.message)
                                LocalDreamSseEvent.Done -> break
                                null -> Unit
                            }
                        }
                    }
                    val complete = completed ?: throw IOException("Missing complete event")
                    saveImage(
                        context,
                        complete,
                        LocalDreamRunStats(
                            generationTimeMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                            firstStepTimeMs = firstStepAt?.let { (it - startedAt).coerceAtLeast(0L) },
                            width = complete.optInt("width", 0),
                            height = complete.optInt("height", 0),
                            recordedAtMs = System.currentTimeMillis(),
                        ),
                    )
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
                throw localDreamHttpFailure(connection)
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
            setRequestProperty("Accept", "text/event-stream, application/json")
        }

    private fun localDreamHttpFailure(connection: HttpURLConnection): IOException {
        val status = connection.responseCode
        val detail = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty().trim().take(240)
        return IOException(
            buildString {
                append("Local Dream HTTP ")
                append(status)
                if (detail.isNotBlank()) append(": ").append(detail)
            },
        )
    }

    private fun saveImage(
        context: Context,
        event: JSONObject,
        stats: LocalDreamRunStats,
    ): LocalDreamImage {
        val image = decodeLocalDreamRgb(event)
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(image.pixels, 0, image.width, 0, 0, image.width, image.height)
        val directory = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.png")
        return try {
            try {
                file.outputStream().use {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                        throw IOException("Could not save generated image")
                    }
                }
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
            val recordedStats = stats.copy(width = image.width, height = image.height)
            LocalDreamStatsStore.save(context, recordedStats)
            LocalDreamImage(file.absolutePath, image.seed, recordedStats)
        } finally {
            bitmap.recycle()
        }
    }
}

internal object LocalDreamQueue {
    private val running = AtomicBoolean(false)
    private class SupersededMediaJobException(message: String) : IOException(message)

    fun isRunning(): Boolean = running.get()

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
            val waiting = store.markMediaWaiting(
                job.postId,
                job.revision,
                error.message.orEmpty(),
            )
            store.close()
            val result = Result.failure<LocalDreamImage>(
                if (waiting) error else SupersededMediaJobException(
                    context.getString(R.string.local_dream_generation_superseded),
                ),
            )
            onFinished(job.postId, result)
            if (waiting) running.set(false) else processNext(context, onProgress, onFinished)
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
                val committed = WorldStore(context).use { updateStore ->
                    updateStore.markMediaReady(
                        job.postId,
                        job.revision,
                        image.path,
                        image.seed,
                    )
                }
                if (!committed) {
                    File(image.path).delete()
                    throw SupersededMediaJobException(
                        context.getString(R.string.local_dream_generation_superseded),
                    )
                }
                image
            }
            var superseded = persisted.exceptionOrNull() is SupersededMediaJobException
            if (persisted.isFailure && !superseded) {
                val error = persisted.exceptionOrNull()!!
                val recorded = WorldStore(context).use { updateStore ->
                    if (isLocalDreamUnavailable(error)) {
                        updateStore.markMediaWaiting(
                            job.postId,
                            job.revision,
                            error.message.orEmpty(),
                        )
                    } else {
                        updateStore.markMediaFailed(
                            job.postId,
                            job.revision,
                            error.message.orEmpty(),
                        )
                    }
                }
                superseded = !recorded
            }
            if (persisted.isSuccess && firstPublication) {
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
            if (persisted.isSuccess || superseded) {
                processNext(context, onProgress, onFinished)
            } else {
                running.set(false)
            }
        }
    }
}
