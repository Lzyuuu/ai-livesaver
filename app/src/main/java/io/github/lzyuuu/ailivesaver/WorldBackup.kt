package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object WorldBackup {
    fun export(
        context: Context,
        store: WorldStore,
        destination: Uri,
        includeMedia: Boolean,
        callback: (Result<Unit>) -> Unit,
    ) = background(callback) {
        val staging = File(context.cacheDir, "backup-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            store.close()
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
                        .put("format", 1)
                        .put("type", if (includeMedia) "full" else "light")
                        .put("worldEngineEnabled", WorldEngine.isEnabled(context))
                        .put("dailyBudget", WorldEngine.dailyBudget(context))
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
        try {
            context.contentResolver.openInputStream(source)!!.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var count = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (++count > 10_000 || !safeEntry(entry.name)) {
                            throw IOException("备份文件包含无效条目")
                        }
                        val target = File(staging, entry.name)
                        if (entry.isDirectory) {
                            target.mkdirs()
                        } else {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
            val manifest = JSONObject(File(staging, "manifest.json").readText())
            if (manifest.getInt("format") != 1) throw IOException("不支持的备份版本")
            val stagedDatabase = File(staging, "world.db")
            validateDatabase(stagedDatabase)

            store.close()
            val database = context.getDatabasePath("world.db")
            val media = File(context.filesDir, "media")
            if (database.exists()) database.copyTo(File(rollback, "world.db"))
            if (media.exists() && !media.renameTo(File(rollback, "media"))) {
                throw IOException("无法创建恢复安全副本")
            }
            try {
                database.parentFile?.mkdirs()
                stagedDatabase.copyTo(database, overwrite = true)
                File(database.path + "-wal").delete()
                File(database.path + "-shm").delete()
                val stagedMedia = File(staging, "media")
                if (stagedMedia.exists() && !stagedMedia.renameTo(media)) {
                    throw IOException("无法恢复媒体文件")
                }
                if (!media.exists()) media.mkdirs()
                WorldEngine.setDailyBudget(context, manifest.optInt("dailyBudget", 12))
                WorldEngine.setEnabled(context, manifest.optBoolean("worldEngineEnabled", true))
            } catch (error: Throwable) {
                database.delete()
                File(rollback, "world.db").takeIf(File::exists)?.copyTo(database)
                media.deleteRecursively()
                File(rollback, "media").takeIf(File::exists)?.renameTo(media)
                throw error
            }
        } finally {
            staging.deleteRecursively()
            rollback.deleteRecursively()
        }
    }

    internal fun safeEntry(name: String): Boolean =
        name == "manifest.json" ||
            name == "world.db" ||
            (name.startsWith("media/") && name.removePrefix("media/").isNotBlank() &&
                '/' !in name.removePrefix("media/") && '\\' !in name)

    private fun validateDatabase(file: File) {
        if (!file.isFile) throw IOException("备份缺少世界数据库")
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
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
