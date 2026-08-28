package io.github.lzyuuu.ailivesaver

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * 本地模型清单（阶段② #73 开工项 3）：解析参考官方 manifest.json 的
 * chat/litert 组件，提供 下载（SHA-256 校验）/删除/引擎切换。
 * 清单本身来自参考 APP 官方 HF 仓库，随 APK 发布。
 */
internal data class LocalModelEntry(
    val id: String,
    val name: String,
    val type: String,
    val quant: String,
    val description: String,
    val minRamMb: Int,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    val fileName: String get() = url.substringAfterLast('/')
    val sizeMb: Int get() = (sizeBytes / (1L shl 20)).toInt()
}

internal object LocalModels {
    private const val PREFS = "local_engine"
    private const val KEY_ACTIVE = "active_engine_id"

    fun parseManifest(json: String): List<LocalModelEntry> {
        val root = JSONObject(json)
        val array = root.optJSONArray("components") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val type = o.optString("type")
                if (type != "chat" && type != "litert") continue
                val url = o.optString("url").trim()
                val sha = o.optString("sha256").trim()
                if (!url.startsWith("https://") || sha.length != 64) continue
                add(
                    LocalModelEntry(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        type = type,
                        quant = o.optString("quant"),
                        description = o.optString("description"),
                        minRamMb = o.optInt("minRamMb", 0),
                        url = url,
                        sizeBytes = o.optLong("sizeBytes", 0L),
                        sha256 = sha,
                    ),
                )
            }
        }
    }

    fun load(context: Context): List<LocalModelEntry> =
        parseManifest(context.assets.open("fancy/manifest.json").bufferedReader().readText())

    fun modelFile(context: Context, entry: LocalModelEntry): File =
        File(HfModelStore.directory(context), entry.id).apply { mkdirs() }.let { dir ->
            File(dir, entry.fileName)
        }

    fun isDownloaded(context: Context, entry: LocalModelEntry): Boolean =
        HfModelStore.matchesTrustedSha(modelFile(context, entry), entry.sha256)

    /** 当前激活的本地引擎 id；null = 使用云端。 */
    fun activeEngineId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, null)

    fun setActiveEngine(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE, id)
            .apply()
    }

    /** 本地引擎是否可用：激活 + LlamaNative so 存在 + 模型已下载（LiteRT 引擎运行时未接，见 PLAN）。 */
    fun isChatEngineReady(context: Context): Boolean {
        val id = activeEngineId(context) ?: return false
        if (!LlamaNative.isAvailable) return false
        return load(context).firstOrNull { it.id == id }?.let { isDownloaded(context, it) } == true
    }

    fun download(
        context: Context,
        entry: LocalModelEntry,
        onDone: (Result<File>) -> Unit,
    ) {
        HfModelStore.download(context, entry.url, entry.fileName, entry.sha256) { result ->
            // downloadFile 固定写 models/<fileName>；迁移到 models/<id>/ 下统一管理。
            val downloaded = result.getOrNull()
            if (downloaded != null) {
                val target = modelFile(context, entry)
                if (downloaded != target) {
                    downloaded.copyTo(target, overwrite = true)
                    downloaded.delete()
                }
            }
            onDone(
                result.map {
                    if (isDownloaded(context, entry)) File(modelFile(context, entry), "").also { }
                    else modelFile(context, entry)
                },
            )
        }
    }

    fun remove(context: Context, entry: LocalModelEntry) {
        modelFile(context, entry).delete()
        if (activeEngineId(context) == entry.id) setActiveEngine(context, null)
    }
}
