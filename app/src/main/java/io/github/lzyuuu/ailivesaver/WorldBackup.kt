package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object WorldBackup {
    private const val MAX_EXTRACTED_BYTES = 8L * 1024 * 1024 * 1024

    fun export(
        context: Context,
        store: WorldStore,
        destination: Uri,
        includeMedia: Boolean,
        callback: (Result<Unit>) -> Unit,
    ) = background(callback) {
        val staging = File(context.cacheDir, "backup-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            store.checkpointForBackup()
            val database = context.getDatabasePath("world.db")
            if (!database.isFile) throw IOException("世界数据库不存在")
            val stagedDatabase = File(staging, "world.db")
            database.copyTo(stagedDatabase)
            if (!includeMedia) {
                SQLiteDatabase.openDatabase(
                    stagedDatabase.path,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                ).use {
                    it.execSQL(
                        "UPDATE social_posts SET media_path = NULL, media_status = " +
                            "CASE WHEN media_prompt IS NULL THEN 'none' ELSE 'failed' END",
                    )
                    it.delete("media_versions", null, null)
                }
            }
            context.contentResolver.openOutputStream(destination, "w")!!.use { output ->
                ZipOutputStream(output.buffered()).use { zip ->
                    val manifest = JSONObject()
                        .put("format", 2)
                        .put("type", if (includeMedia) "full" else "light")
                        .put("worldSettings", JSONObject(WorldEngine.backupSettings(context)))
                    zip.writeEntry("manifest.json", manifest.toString().toByteArray())
                    zip.writeFile("world.db", stagedDatabase)
                    if (includeMedia) {
                        File(context.filesDir, "media").listFiles()?.forEach { file ->
                            if (file.isFile) zip.writeFile("media/${file.name}", file)
                        }
                    }
                }
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    fun restore(
        context: Context,
        store: WorldStore,
        source: Uri,
        callback: (Result<Unit>) -> Unit,
    ) = background(callback) {
        val staging = File(context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val rollback = File(context.cacheDir, "rollback-${UUID.randomUUID()}").apply { mkdirs() }
        val previousSettings = WorldEngine.backupSettings(context)
        try {
            val maxExtractedBytes = minOf(
                MAX_EXTRACTED_BYTES,
                (StatFs(context.cacheDir.path).availableBytes - 256L * 1024 * 1024)
                    .coerceAtLeast(0),
            )
            if (maxExtractedBytes == 0L) throw IOException("存储空间不足，无法恢复备份")
            val sourceStream = context.contentResolver.openInputStream(source)
                ?: throw IOException("无法读取备份文件")
            sourceStream.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var count = 0
                    var extractedBytes = 0L
                    val names = mutableSetOf<String>()
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (
                            ++count > 10_000 ||
                            !safeEntry(entry.name) ||
                            !names.add(entry.name)
                        ) {
                            throw IOException("备份文件包含无效条目")
                        }
                        val target = File(staging, entry.name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read < 0) break
                                    extractedBytes += read
                                    if (extractedBytes > maxExtractedBytes) {
                                        throw IOException("备份文件过大")
                                    }
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                    }
                }
            }
            val manifest = JSONObject(File(staging, "manifest.json").readText())
            val format = manifest.getInt("format")
            if (format !in 1..2) throw IOException("不支持的备份版本")
            if (manifest.optString("type") !in setOf("full", "light")) {
                throw IOException("备份类型无效")
            }
            if (format == 2 && manifest.optJSONObject("worldSettings") == null) {
                throw IOException("备份缺少世界设置")
            }
            val stagedDatabase = File(staging, "world.db")
            validateDatabase(stagedDatabase)

            WorldEngine.suspendAutomation(context)
            store.close()
            val database = context.getDatabasePath("world.db")
            val media = File(context.filesDir, "media")
            normalizeMediaPaths(stagedDatabase, File(staging, "media"), media)
            if (database.exists()) database.copyTo(File(rollback, "world.db"))
            if (media.exists() && !media.renameTo(File(rollback, "media"))) {
                throw IOException("无法创建恢复安全副本")
            }
            try {
                database.parentFile?.mkdirs()
                File(database.path + "-wal").delete()
                File(database.path + "-shm").delete()
                val pendingDatabase = File(database.parentFile, ".world-${UUID.randomUUID()}.db")
                stagedDatabase.copyTo(pendingDatabase)
                moveReplace(pendingDatabase, database)
                val stagedMedia = File(staging, "media")
                val pendingMedia = File(context.filesDir, ".media-${UUID.randomUUID()}")
                if (stagedMedia.exists()) {
                    if (
                        !stagedMedia.renameTo(pendingMedia) &&
                        !stagedMedia.copyRecursively(pendingMedia)
                    ) {
                        throw IOException("无法准备媒体文件")
                    }
                } else {
                    pendingMedia.mkdirs()
                }
                if (!pendingMedia.renameTo(media)) {
                    throw IOException("无法恢复媒体文件")
                }
                val settings = if (format == 1) {
                    mapOf(
                        "daily_budget" to manifest.optInt("dailyBudget", 20),
                        "enabled" to manifest.optBoolean("worldEngineEnabled", true),
                    )
                } else {
                    manifest.optJSONObject("worldSettings")?.toMap().orEmpty()
                }
                WorldEngine.restoreSettings(context, settings)
            } catch (error: Throwable) {
                database.delete()
                File(rollback, "world.db").takeIf(File::exists)?.copyTo(database)
                media.deleteRecursively()
                File(rollback, "media").takeIf(File::exists)?.renameTo(media)
                WorldEngine.restoreSettings(context, previousSettings)
                throw error
            }
        } finally {
            WorldEngine.resumeAutomation(context)
            context.filesDir.listFiles()
                ?.filter { it.name.startsWith(".media-") }
                ?.forEach(File::deleteRecursively)
            context.getDatabasePath("world.db").parentFile?.listFiles()
                ?.filter { it.name.startsWith(".world-") }
                ?.forEach(File::delete)
            staging.deleteRecursively()
            rollback.deleteRecursively()
        }
    }

    fun rebuildWorld(
        context: Context,
        store: WorldStore,
        callback: (Result<Unit>) -> Unit,
    ) = background(callback) {
        WorldEngine.suspendAutomation(context)
        store.close()
        context.deleteDatabase("world.db")
        File(context.filesDir, "media").deleteRecursively()
        WorldEngine.resetRuntime(context)
    }

    fun eraseAll(
        context: Context,
        store: WorldStore,
        callback: (Result<Unit>) -> Unit,
    ) = background(callback) {
        WorldEngine.clearAll(context)
        store.close()
        context.deleteDatabase("world.db")
        File(context.filesDir, "media").deleteRecursively()
        clearTemporaryCache(context)
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ProviderStore(context).clearAll()
    }

    internal fun safeEntry(name: String): Boolean =
        name == "manifest.json" ||
            name == "world.db" ||
            (name.startsWith("media/") && name.removePrefix("media/").isNotBlank() &&
                '/' !in name.removePrefix("media/") && '\\' !in name)

    private fun validateDatabase(file: File) {
        if (!file.isFile) throw IOException("备份缺少世界数据库")
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val integrity = database.rawQuery("PRAGMA integrity_check(1)", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == "ok"
            }
            if (!integrity) throw IOException("世界数据库完整性校验失败")
            val foreignKeysValid = database.rawQuery("PRAGMA foreign_key_check", null).use {
                !it.moveToFirst()
            }
            if (!foreignKeysValid) throw IOException("世界数据库关联校验失败")
            val version = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (!supportsDatabaseVersion(version)) {
                throw IOException("备份数据库版本不受支持")
            }
            val tables = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
                null,
            ).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            if (!tables.containsAll(setOf("profile", "characters", "messages", "memories", "social_posts"))) {
                throw IOException("世界数据库校验失败")
            }
        }
    }

    internal fun supportsDatabaseVersion(version: Int) =
        version in 1..WORLD_DATABASE_VERSION

    private fun normalizeMediaPaths(databaseFile: File, source: File, destination: File) {
        SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            val versions = database.rawQuery(
                "SELECT id, path FROM media_versions",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1))
                }
            }
            versions.forEach { (id, path) ->
                val name = File(path).name
                if (File(source, name).isFile) {
                    database.execSQL(
                        "UPDATE media_versions SET path = ? WHERE id = ?",
                        arrayOf(File(destination, name).path, id),
                    )
                } else {
                    database.execSQL("DELETE FROM media_versions WHERE id = ?", arrayOf(id))
                }
            }
            val posts = database.rawQuery(
                "SELECT id, media_path FROM social_posts WHERE media_path IS NOT NULL",
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getString(1))
                }
            }
            posts.forEach { (id, path) ->
                val name = File(path).name
                if (File(source, name).isFile) {
                    database.execSQL(
                        "UPDATE social_posts SET media_path = ? WHERE id = ?",
                        arrayOf(File(destination, name).path, id),
                    )
                } else {
                    database.execSQL(
                        """
                        UPDATE social_posts
                        SET media_path = NULL,
                            media_status = CASE
                                WHEN media_prompt IS NULL THEN 'none' ELSE 'failed'
                            END
                        WHERE id = ?
                        """.trimIndent(),
                        arrayOf(id),
                    )
                }
            }
        }
    }

    private fun moveReplace(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun JSONObject.toMap(): Map<String, Any> = keys().asSequence().associateWith(::get)

    private fun background(callback: (Result<Unit>) -> Unit, block: () -> Unit) {
        Thread {
            val result = runCatching(block)
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }
}
