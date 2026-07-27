package io.github.lzyuuu.ailivesaver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class ResidentCharacter(
    val id: Long,
    val name: String,
    val persona: String,
)

internal data class ChatMessage(
    val id: Long,
    val characterId: Long,
    val sender: String,
    val body: String,
    val createdAt: Long,
)

internal data class LongTermMemory(
    val id: Long,
    val characterId: Long,
    val characterName: String,
    val body: String,
    val sourceMessageId: Long,
    val createdAt: Long,
    val pinned: Boolean,
)

internal data class SocialPost(
    val id: Long,
    val kind: String,
    val authorName: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val mediaPath: String?,
    val mediaPrompt: String?,
    val mediaSeed: Long?,
    val mediaStatus: String,
)

internal data class SocialComment(
    val id: Long,
    val postId: Long,
    val authorName: String,
    val body: String,
    val createdAt: Long,
)

internal class WorldStore(context: Context) :
    SQLiteOpenHelper(context, "world.db", null, 4) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE profile (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                name TEXT NOT NULL
            )
            """.trimIndent(),
        )
        createSocialTables(database)
        createMediaVersionsTable(database)
        database.execSQL(
            """
            CREATE TABLE characters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                persona TEXT NOT NULL,
                is_primary INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                character_id INTEGER NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE memories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                character_id INTEGER NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                source_message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    override fun onConfigure(database: SQLiteDatabase) {
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createSocialTables(database)
        } else if (oldVersion < 3) {
            database.execSQL("ALTER TABLE social_posts ADD COLUMN media_path TEXT")
            database.execSQL("ALTER TABLE social_posts ADD COLUMN media_prompt TEXT")
            database.execSQL("ALTER TABLE social_posts ADD COLUMN media_seed INTEGER")
            database.execSQL(
                "ALTER TABLE social_posts ADD COLUMN media_status TEXT NOT NULL DEFAULT 'none'",
            )
        }
        if (oldVersion < 4) createMediaVersionsTable(database)
    }

    private fun createSocialTables(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE social_posts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                author_name TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                media_path TEXT,
                media_prompt TEXT,
                media_seed INTEGER,
                media_status TEXT NOT NULL DEFAULT 'none'
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE social_comments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                post_id INTEGER NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
                author_name TEXT NOT NULL,
                body TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createMediaVersionsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE media_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                post_id INTEGER NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
                path TEXT NOT NULL,
                prompt TEXT NOT NULL,
                seed INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    fun primaryCharacter(): ResidentCharacter? = readableDatabase.rawQuery(
        "SELECT id, name, persona FROM characters ORDER BY is_primary DESC, id LIMIT 1",
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) null
        else ResidentCharacter(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
    }

    fun userName(): String = readableDatabase.rawQuery(
        "SELECT name FROM profile WHERE id = 1",
        null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "你" }

    fun createWorld(userName: String, characterName: String, persona: String): ResidentCharacter {
        return writableDatabase.run {
            beginTransaction()
            try {
                insertOrThrow(
                    "profile",
                    null,
                    ContentValues().apply {
                        put("id", 1)
                        put("name", userName.trim())
                    },
                )
                val values = ContentValues().apply {
                    put("name", characterName.trim())
                    put("persona", persona.trim())
                    put("is_primary", 1)
                    put("created_at", System.currentTimeMillis())
                }
                val id = insertOrThrow("characters", null, values)
                setTransactionSuccessful()
                ResidentCharacter(id, characterName.trim(), persona.trim())
            } finally {
                endTransaction()
            }
        }
    }

    fun messages(characterId: Long): List<ChatMessage> = readableDatabase.rawQuery(
        """
        SELECT id, character_id, sender, body, created_at
        FROM messages
        WHERE character_id = ?
        ORDER BY id
        """.trimIndent(),
        arrayOf(characterId.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ChatMessage(
                        id = cursor.getLong(0),
                        characterId = cursor.getLong(1),
                        sender = cursor.getString(2),
                        body = cursor.getString(3),
                        createdAt = cursor.getLong(4),
                    ),
                )
            }
        }
    }

    fun addMessage(characterId: Long, sender: String, body: String): ChatMessage {
        val createdAt = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("character_id", characterId)
            put("sender", sender)
            put("body", body.trim())
            put("created_at", createdAt)
        }
        val id = writableDatabase.insertOrThrow("messages", null, values)
        return ChatMessage(id, characterId, sender, body.trim(), createdAt)
    }

    fun remember(characterId: Long, sourceMessageId: Long, body: String) {
        val values = ContentValues().apply {
            put("character_id", characterId)
            put("body", body.trim())
            put("source_message_id", sourceMessageId)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("memories", null, values)
    }

    fun memories(characterId: Long): List<LongTermMemory> = readableDatabase.rawQuery(
        """
        SELECT memories.id, memories.character_id, characters.name, memories.body,
               memories.source_message_id, memories.created_at, memories.pinned
        FROM memories
        JOIN characters ON characters.id = memories.character_id
        WHERE memories.character_id = ?
        ORDER BY memories.pinned DESC, memories.created_at DESC
        """.trimIndent(),
        arrayOf(characterId.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    LongTermMemory(
                        id = cursor.getLong(0),
                        characterId = cursor.getLong(1),
                        characterName = cursor.getString(2),
                        body = cursor.getString(3),
                        sourceMessageId = cursor.getLong(4),
                        createdAt = cursor.getLong(5),
                        pinned = cursor.getInt(6) == 1,
                    ),
                )
            }
        }
    }

    fun updateMemory(id: Long, body: String) {
        writableDatabase.update(
            "memories",
            ContentValues().apply { put("body", body.trim()) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun setMemoryPinned(id: Long, pinned: Boolean) {
        writableDatabase.update(
            "memories",
            ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun deleteMemory(id: Long) {
        writableDatabase.delete("memories", "id = ?", arrayOf(id.toString()))
    }

    fun createPost(kind: String, title: String, body: String): Long {
        return createPost(kind, userName(), title, body)
    }

    fun createPost(kind: String, authorName: String, title: String, body: String): Long {
        return writableDatabase.insertOrThrow(
            "social_posts",
            null,
            ContentValues().apply {
                put("kind", kind)
                put("author_name", authorName)
                put("title", title.trim())
                put("body", body.trim())
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun posts(kind: String): List<SocialPost> = readableDatabase.rawQuery(
        """
        SELECT id, kind, author_name, title, body, created_at,
               media_path, media_prompt, media_seed, media_status
        FROM social_posts
        WHERE kind = ?
        ORDER BY created_at DESC
        """.trimIndent(),
        arrayOf(kind),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SocialPost(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getLong(5),
                        cursor.getString(6),
                        cursor.getString(7),
                        if (cursor.isNull(8)) null else cursor.getLong(8),
                        cursor.getString(9),
                    ),
                )
            }
        }
    }

    fun comments(postId: Long): List<SocialComment> = readableDatabase.rawQuery(
        """
        SELECT id, post_id, author_name, body, created_at
        FROM social_comments
        WHERE post_id = ?
        ORDER BY created_at
        """.trimIndent(),
        arrayOf(postId.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SocialComment(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                    ),
                )
            }
        }
    }

    fun addComment(postId: Long, body: String) {
        writableDatabase.insertOrThrow(
            "social_comments",
            null,
            ContentValues().apply {
                put("post_id", postId)
                put("author_name", userName())
                put("body", body.trim())
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun createMediaPost(body: String, prompt: String): Long {
        return writableDatabase.insertOrThrow(
            "social_posts",
            null,
            ContentValues().apply {
                put("kind", "moment")
                put("author_name", userName())
                put("body", body.trim())
                put("media_prompt", prompt.trim())
                put("media_status", "pending")
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun markMediaReady(postId: Long, path: String, seed: Long) {
        writableDatabase.run {
            beginTransaction()
            try {
                val prompt = rawQuery(
                    "SELECT media_prompt FROM social_posts WHERE id = ?",
                    arrayOf(postId.toString()),
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "" }
                update(
                    "social_posts",
                    ContentValues().apply {
                        put("media_path", path)
                        put("media_seed", seed)
                        put("media_status", "ready")
                    },
                    "id = ?",
                    arrayOf(postId.toString()),
                )
                insertOrThrow(
                    "media_versions",
                    null,
                    ContentValues().apply {
                        put("post_id", postId)
                        put("path", path)
                        put("prompt", prompt)
                        put("seed", seed)
                        put("created_at", System.currentTimeMillis())
                    },
                )
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    fun markMediaFailed(postId: Long) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply { put("media_status", "failed") },
            "id = ?",
            arrayOf(postId.toString()),
        )
    }

    fun prepareRedraw(postId: Long, prompt: String) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply {
                put("media_prompt", prompt.trim())
                put("media_status", "pending")
            },
            "id = ?",
            arrayOf(postId.toString()),
        )
    }

    fun queueCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM social_posts WHERE media_status = 'pending'",
        null,
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
}

internal object MemoryExtractor {
    // ponytail: deterministic first pass; replace with structured model extraction after capability checks.
    fun fromUserMessage(message: String): String? {
        val normalized = message.trim()
        val lowered = normalized.lowercase()
        return normalized.takeIf {
            it.length in 4..240 &&
                (
                    listOf("记住", "我叫", "我喜欢", "我不喜欢", "我的").any(normalized::contains) ||
                        listOf("remember", "my name", "i like", "i dislike").any(lowered::contains)
                    )
        }
    }
}
