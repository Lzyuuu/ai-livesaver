package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * 聊天图片附件（参考 V4.51 聊天附件）：选图后压缩入应用私有目录，
 * 消息体以 `[image:<filesDir 相对路径>]` 承载；发送时转 data URL 走
 * OpenAI 兼容视觉消息（content 数组）。
 */
internal const val CHAT_IMAGE_MAX_DIM = 1024
internal val IMAGE_MESSAGE_REGEX = Regex("""^\[image:([^]]+)]$""")

internal fun imageMessagePath(body: String): String? =
    IMAGE_MESSAGE_REGEX.find(body)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

internal object ChatImages {
    /** 压缩保存选中的图片，返回 filesDir 相对路径；失败返回 null。 */
    fun saveFromUri(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= CHAT_IMAGE_MAX_DIM) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        val bitmap = scaleWithin(decoded, CHAT_IMAGE_MAX_DIM)
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val target = File(dir, "chat-${UUID.randomUUID()}.jpg")
        target.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        "media/${target.name}"
    }.getOrNull()

    /** 读取图片并压成 data URL（再次限尺寸，控制 token/带宽）。 */
    fun readDataUrl(context: Context, relativePath: String): String? = runCatching {
        val file = File(context.filesDir, relativePath)
        if (!file.exists()) return null
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val bitmap = scaleWithin(decoded, CHAT_IMAGE_MAX_DIM)
        val bytes = ByteArrayOutputStream().also { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }.toByteArray()
        "data:image/jpeg;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }.getOrNull()

    private fun scaleWithin(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
