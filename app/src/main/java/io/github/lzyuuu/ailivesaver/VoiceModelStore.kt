package io.github.lzyuuu.ailivesaver

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * 语音模型仓库（阶段② SE-05）：ASR = whisper.cpp ggml 单文件，TTS = sherpa-onnx 声音 zip。
 * 目录来自 assets/fancy/manifest.json（参考 V4.51 官方清单），下载复用 HfModelStore
 * 的重试与 SHA-256 校验；TTS zip 校验后解包进 voice-models/<id>/。
 */
internal enum class VoiceKind { ASR, TTS }

internal data class VoiceModelEntry(
    val id: String,
    val name: String,
    val kind: VoiceKind,
    val language: String,
    val url: String,
    val bytes: Long,
    val sha256: String,
)

internal object VoiceModelStore {
    private const val PREFS = "voice_model_store"
    private const val KEY_ASR = "active_asr"
    private const val KEY_TTS = "active_tts"
    private const val ROOT = "voice-models"

    fun parseManifest(json: String): List<VoiceModelEntry> {
        val root = JSONObject(json)
        val components = root.optJSONArray("components") ?: return emptyList()
        val entries = mutableListOf<VoiceModelEntry>()
        for (i in 0 until components.length()) {
            val item = components.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val kind = when (type) {
                "asr" -> VoiceKind.ASR
                "tts" -> VoiceKind.TTS
                else -> continue
            }
            val url = item.optString("url")
            val sha = item.optString("sha256")
            if (!url.startsWith("https://") || sha.length != 64) continue
            entries += VoiceModelEntry(
                id = item.optString("id"),
                name = item.optString("name").substringAfterLast("—").trim(),
                kind = kind,
                language = item.optString("language", ""),
                url = url,
                bytes = item.optLong("sizeBytes"),
                sha256 = sha,
            )
        }
        return entries
    }

    fun catalog(context: Context): List<VoiceModelEntry> =
        parseManifest(runCatching {
            context.assets.open("fancy/manifest.json").bufferedReader().use { it.readText() }
        }.getOrDefault(""))

    fun asrModels(context: Context): List<VoiceModelEntry> =
        catalog(context).filter { it.kind == VoiceKind.ASR }

    fun ttsVoices(context: Context): List<VoiceModelEntry> =
        catalog(context).filter { it.kind == VoiceKind.TTS }

    fun dir(context: Context, entry: VoiceModelEntry): File =
        File(File(context.filesDir, ROOT), entry.id).apply { mkdirs() }

    fun isReady(context: Context, entry: VoiceModelEntry): Boolean = when (entry.kind) {
        VoiceKind.ASR -> asrFile(context, entry) != null
        VoiceKind.TTS -> ttsModelFile(context, entry) != null
    }

    fun readyBytes(context: Context): Long =
        catalog(context).filter { isReady(context, it) }.sumOf { it.bytes }

    /** ASR 模型 ggml 文件（sha 校验通过才返回）。 */
    fun asrFile(context: Context, entry: VoiceModelEntry): File? {
        val dir = dir(context, entry)
        val files = dir.listFiles().orEmpty().filter { it.isFile }
        val candidate = files.firstOrNull { it.name.endsWith(".bin") } ?: return null
        return if (HfModelStore.matchesTrustedSha(candidate, entry.sha256)) candidate else null
    }

    /** TTS 解包目录内的 onnx 模型文件。 */
    fun ttsModelFile(context: Context, entry: VoiceModelEntry): File? =
        dir(context, entry).listFiles().orEmpty().firstOrNull { it.isFile && it.name.endsWith(".onnx") }

    fun remove(context: Context, entry: VoiceModelEntry) {
        dir(context, entry).deleteRecursively()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ASR, null) == entry.id) prefs.edit().remove(KEY_ASR).apply()
        if (prefs.getString(KEY_TTS, null) == entry.id) prefs.edit().remove(KEY_TTS).apply()
    }

    /** 下载（TTS 为 zip 校验后解包）。onDone 在工作线程回调。 */
    fun download(
        context: Context,
        entry: VoiceModelEntry,
        onProgress: (Long, Long) -> Unit,
        onDone: (Result<Unit>) -> Unit,
    ): Boolean {
        val target = dir(context, entry)
        Thread {
            val result = runCatching {
                onProgress(0L, entry.bytes)
                if (entry.kind == VoiceKind.ASR) {
                    val name = entry.url.substringAfterLast('/')
                    val file = HfModelStore.downloadFile(target, entry.url, name, entry.sha256)
                    if (!HfModelStore.matchesTrustedSha(file, entry.sha256)) error("校验失败：${entry.id}")
                } else {
                    val zip = HfModelStore.downloadFile(target, entry.url, "${entry.id}.zip", entry.sha256)
                    unzipInto(zip, target)
                    if (ttsModelFile(context, entry) == null) error("包内缺少 onnx 模型：${entry.id}")
                    zip.delete()
                }
                onProgress(entry.bytes, entry.bytes)
            }
            result.onFailure { android.util.Log.e("VoiceModelStore", "download ${entry.id} failed", it) }
            onDone(result)
        }.start()
        return true
    }

    private fun unzipInto(zip: File, target: File) {
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { stream ->
            while (true) {
                val item = stream.nextEntry ?: break
                if (item.isDirectory) continue
                val out = File(target, item.name.removePrefix("./"))
                if (!out.canonicalPath.startsWith(target.canonicalPath)) continue
                out.parentFile?.mkdirs()
                out.outputStream().use { stream.copyTo(it) }
                stream.closeEntry()
            }
        }
    }

    fun activeAsrId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ASR, null)

    fun activeTtsId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TTS, null)

    fun setActiveAsr(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ASR, id).apply()
    }

    fun setActiveTts(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TTS, id).apply()
    }

    fun activeAsrEntry(context: Context): VoiceModelEntry? {
        val id = activeAsrId(context)
        return asrModels(context).firstOrNull { it.id == id && isReady(context, it) }
    }

    fun activeTtsEntry(context: Context): VoiceModelEntry? {
        val id = activeTtsId(context)
        return ttsVoices(context).firstOrNull { it.id == id && isReady(context, it) }
    }
}
