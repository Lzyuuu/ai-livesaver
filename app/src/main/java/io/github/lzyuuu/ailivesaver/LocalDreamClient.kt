package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal data class LocalDreamImage(
    val path: String,
    val seed: Long,
)

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
        prompt: String,
        onProgress: (Int, Int) -> Unit,
        callback: (Result<LocalDreamImage>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val connection = connection("/generate")
                val request = JSONObject()
                    .put("prompt", prompt)
                    .put("negative_prompt", "")
                    .put("steps", 20)
                    .put("cfg", 7.5)
                    .put("scheduler", "dpm")
                    .put("size", 512)
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
