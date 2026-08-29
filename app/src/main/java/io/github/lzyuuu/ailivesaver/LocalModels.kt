package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地模型清单（阶段② #73 开工项 3）：解析参考官方 manifest.json 的
 * chat/litert 组件，提供 下载（SHA-256 校验）/导入/删除/引擎切换。
 * 清单本身来自参考 APP 官方 HF 仓库，随 APK 发布；用户导入的模型追加在清单之后。
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
    val imported: Boolean get() = id.startsWith("import-")
}

internal object LocalModels {
    private const val PREFS = "local_engine"
    private const val KEY_ACTIVE = "active_engine_id"
    private const val KEY_IMPORTED = "imported_models"

    /** 导入大小上限 8 GiB：E4B 级模型以内，防止误选整盘文件写满存储。 */
    internal const val IMPORT_SIZE_CAP_BYTES: Long = 8L shl 30

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
        mergeManifestAndImported(
            parseManifest(context.assets.open("fancy/manifest.json").bufferedReader().readText()),
            importedEntries(context),
        )

    fun modelFile(context: Context, entry: LocalModelEntry): File =
        File(HfModelStore.directory(context), entry.id).apply { mkdirs() }.let { dir ->
            File(dir, entry.fileName)
        }

    fun isDownloaded(context: Context, entry: LocalModelEntry): Boolean {
        // 导入模型的"可信哈希"是导入时计算的注册值；文件被替换后校验失效。
        val expected = if (entry.imported) importedSha256(context, entry.id) ?: entry.sha256 else entry.sha256
        return HfModelStore.matchesTrustedSha(modelFile(context, entry), expected)
    }

    /** 当前激活的本地引擎 id；null = 使用云端。 */
    fun activeEngineId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, null)

    fun setActiveEngine(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ACTIVE, id)
            .apply()
    }

    /**
     * 纯判定（供 JVM 单测）：仅当激活条目存在、类型为 chat（llama.cpp）且已下载时
     * 返回该条目；litert 条目不在此列（其引擎分支见 [LiteRtLm]），不得误走 llama.cpp。
     */
    fun chatReadyEntry(
        activeId: String?,
        entries: List<LocalModelEntry>,
        isDownloaded: (LocalModelEntry) -> Boolean,
    ): LocalModelEntry? =
        activeId?.let { id ->
            entries.firstOrNull { entry -> entry.id == id && entry.type == "chat" && isDownloaded(entry) }
        }

    /** 本地引擎是否可用：激活 + 对应运行时存在 + 模型已下载。chat（llama.cpp）或 litert（LiteRT-LM）。 */
    fun isChatEngineReady(context: Context): Boolean {
        val id = activeEngineId(context) ?: return false
        val entry = load(context).firstOrNull { it.id == id } ?: return false
        return when (entry.type) {
            "chat" -> LlamaNative.isAvailable && isDownloaded(context, entry)
            "litert" -> LiteRtLm.isAvailable && isDownloaded(context, entry)
            else -> false
        }
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
        if (entry.imported) {
            saveImportedRegistry(context, importedEntries(context).filterNot { it.id == entry.id })
        }
        if (activeEngineId(context) == entry.id) setActiveEngine(context, null)
    }

    // ---- 用户导入（SAF）：纯逻辑在 JVM 测试覆盖，IO 走后台线程。 ----

    /** 按扩展名判定导入模型类型；其他类型拒绝，避免把任意文件喂给推理引擎。 */
    internal fun importedModelType(fileName: String): String? = when {
        fileName.endsWith(".gguf", ignoreCase = true) -> "chat"
        fileName.endsWith(".litertlm", ignoreCase = true) -> "litert"
        else -> null
    }

    internal fun isImportSizeAllowed(
        sizeBytes: Long,
        capBytes: Long = IMPORT_SIZE_CAP_BYTES,
    ): Boolean = sizeBytes in 1..capBytes

    internal fun mergeManifestAndImported(
        manifest: List<LocalModelEntry>,
        imported: List<LocalModelEntry>,
    ): List<LocalModelEntry> =
        manifest + imported.filter { candidate -> manifest.none { it.id == candidate.id } }

    internal fun parseImportedModels(json: String): List<LocalModelEntry> {
        if (json.isBlank()) return emptyList()
        val array = runCatching { JSONObject(json).optJSONArray("models") }
            .getOrNull()
            ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val fileName = o.optString("fileName")
                val sha = o.optString("sha256")
                val type = o.optString("type")
                // 只接受本 APP 自己写入的形态：id 前缀、类型白名单、完整哈希。
                if (!id.startsWith("import-") || fileName.isEmpty()) continue
                if (type != "chat" && type != "litert") continue
                if (sha.length != 64) continue
                add(
                    LocalModelEntry(
                        id = id,
                        name = o.optString("name").ifEmpty { fileName },
                        type = type,
                        quant = "import",
                        description = "从文件导入",
                        minRamMb = 0,
                        url = "imported/$fileName",
                        sizeBytes = o.optLong("sizeBytes", 0L),
                        sha256 = sha,
                    ),
                )
            }
        }
    }

    internal fun encodeImportedModels(entries: List<LocalModelEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("type", entry.type)
                    .put("fileName", entry.fileName)
                    .put("sizeBytes", entry.sizeBytes)
                    .put("sha256", entry.sha256),
            )
        }
        return JSONObject().put("models", array).toString()
    }

    fun importedEntries(context: Context): List<LocalModelEntry> =
        parseImportedModels(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_IMPORTED, null)
                .orEmpty(),
        )

    private fun saveImportedRegistry(context: Context, entries: List<LocalModelEntry>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_IMPORTED, encodeImportedModels(entries))
            .apply()
    }

    private fun importedSha256(context: Context, id: String): String? =
        importedEntries(context).firstOrNull { it.id == id }?.sha256

    /**
     * 从 SAF uri 导入模型：复制到 models/<id>/<文件名>，边复制边算 SHA-256，
     * 完成后注册进导入清单。拒绝未知扩展名、空文件与超过上限的文件。
     */
    fun importFromUri(
        context: Context,
        uri: Uri,
        onDone: (Result<LocalModelEntry>) -> Unit,
    ) {
        Thread {
            var partial: File? = null
            onDone(
                runCatching {
                    val resolver = context.contentResolver
                    val displayName = (queryDisplayName(resolver, uri) ?: uri.lastPathSegment)
                        ?.replace('/', '_')
                        .orEmpty()
                    check(displayName.isNotEmpty()) { "无法读取文件名" }
                    val type = importedModelType(displayName)
                        ?: error("仅支持 .gguf 或 .litertlm 文件")
                    val size = querySize(resolver, uri)
                    check(isImportSizeAllowed(size)) {
                        if (size <= 0) "文件为空或大小未知" else "文件超过 8 GB 导入上限"
                    }
                    val id = "import-" + UUID.randomUUID().toString().substringBefore('-')
                    val target = modelFile(
                        context,
                        LocalModelEntry(id, displayName, type, "import", "", 0, "imported/$displayName", size, ""),
                    )
                    partial = target
                    val digest = MessageDigest.getInstance("SHA-256")
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(1 shl 16)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                            }
                        }
                    } ?: error("无法打开所选文件")
                    val entry = LocalModelEntry(
                        id = id,
                        name = displayName,
                        type = type,
                        quant = "import",
                        description = "从文件导入",
                        minRamMb = 0,
                        url = "imported/$displayName",
                        sizeBytes = target.length(),
                        sha256 = digest.digest().joinToString("") { "%02x".format(it) },
                    )
                    saveImportedRegistry(context, importedEntries(context) + entry)
                    entry
                }.onFailure {
                    // 半成品文件不得留在模型目录里冒充已安装。
                    partial?.delete()
                },
            )
        }.start()
    }

    private fun queryDisplayName(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()

    private fun querySize(
        resolver: android.content.ContentResolver,
        uri: Uri,
    ): Long =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
            } ?: -1L
        }.getOrDefault(-1L)
}
