package io.github.lzyuuu.ailivesaver

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class ResidentCharacter(
    val id: Long,
    val name: String,
    val persona: String,
    val attentionTier: String = "resident",
    val active: Boolean = true,
    val appearance: String = "",
    val clothing: String = "",
    val negativePrompt: String = "",
    val cardJson: String = "",
)

internal data class UserIdentity(
    val name: String,
    val addressPreference: String,
    val bio: String,
)

internal data class CharacterTurningPoint(
    val id: Long,
    val previousPersona: String,
    val newPersona: String,
    val createdAt: Long,
)

internal data class RelationshipState(
    val label: String,
    val summary: String,
    val sourceMessageId: Long?,
    val createdAt: Long,
)

internal fun nextRelationship(
    current: RelationshipState,
    sharedPersonalFact: Boolean,
): Pair<String, String>? = when {
    current.createdAt == 0L ->
        "开始交谈" to "你主动开启了一段只属于你们的对话。"
    sharedPersonalFact && current.label != "更了解彼此" ->
        "更了解彼此" to "你分享了一件值得长期记住的事。"
    else -> null
}

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

internal data class ConversationRecap(
    val body: String,
    val throughMessageId: Long,
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
    SQLiteOpenHelper(context, "world.db", null, 9) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE profile (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                name TEXT NOT NULL,
                address_preference TEXT NOT NULL DEFAULT '',
                bio TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE characters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                persona TEXT NOT NULL,
                is_primary INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                attention_tier TEXT NOT NULL DEFAULT 'resident',
                active INTEGER NOT NULL DEFAULT 1,
                appearance TEXT NOT NULL DEFAULT '',
                clothing TEXT NOT NULL DEFAULT '',
                negative_prompt TEXT NOT NULL DEFAULT '',
                card_json TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL DEFAULT 0
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
        createTurningPointsTable(database)
        createRelationshipEventsTable(database)
        createConversationRecapsTable(database)
        createSocialTables(database)
        createMediaVersionsTable(database)
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
        if (oldVersion < 5) {
            database.execSQL(
                "ALTER TABLE profile ADD COLUMN address_preference TEXT NOT NULL DEFAULT ''",
            )
            database.execSQL("ALTER TABLE profile ADD COLUMN bio TEXT NOT NULL DEFAULT ''")
            database.execSQL(
                "ALTER TABLE profile ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "ALTER TABLE characters ADD COLUMN attention_tier TEXT NOT NULL DEFAULT 'resident'",
            )
            database.execSQL(
                "ALTER TABLE characters ADD COLUMN active INTEGER NOT NULL DEFAULT 1",
            )
            database.execSQL(
                "ALTER TABLE characters ADD COLUMN appearance TEXT NOT NULL DEFAULT ''",
            )
            database.execSQL(
                "ALTER TABLE characters ADD COLUMN clothing TEXT NOT NULL DEFAULT ''",
            )
            database.execSQL(
                "ALTER TABLE characters ADD COLUMN negative_prompt TEXT NOT NULL DEFAULT ''",
            )
            database.execSQL(
                "ALTER TABLE characters ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0",
            )
            database.execSQL(
                "UPDATE characters SET attention_tier = 'special_focus' WHERE is_primary = 1",
            )
        }
        if (oldVersion < 6) {
            database.execSQL(
                "ALTER TABLE characters ADD COLUMN card_json TEXT NOT NULL DEFAULT ''",
            )
        }
        if (oldVersion < 7) createTurningPointsTable(database)
        if (oldVersion < 8) createRelationshipEventsTable(database)
        if (oldVersion < 9) createConversationRecapsTable(database)
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

    private fun createTurningPointsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE character_turning_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                character_id INTEGER NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
                previous_persona TEXT NOT NULL,
                new_persona TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createRelationshipEventsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE relationship_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                character_id INTEGER NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
                label TEXT NOT NULL,
                summary TEXT NOT NULL,
                source_message_id INTEGER REFERENCES messages(id) ON DELETE SET NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createConversationRecapsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE conversation_recaps (
                character_id INTEGER PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                through_message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    fun primaryCharacter(): ResidentCharacter? = readableDatabase.rawQuery(
        """
        SELECT id, name, persona, attention_tier, active, appearance, clothing, negative_prompt,
               card_json
        FROM characters
        WHERE active = 1
        ORDER BY CASE attention_tier WHEN 'special_focus' THEN 0 ELSE 1 END, is_primary DESC, id
        LIMIT 1
        """.trimIndent(),
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) null
        else cursor.residentCharacter()
    }

    fun characters(includeDeparted: Boolean = true): List<ResidentCharacter> =
        readableDatabase.rawQuery(
            """
            SELECT id, name, persona, attention_tier, active, appearance, clothing, negative_prompt,
                   card_json
            FROM characters
            ${if (includeDeparted) "" else "WHERE active = 1"}
            ORDER BY active DESC,
                     CASE attention_tier WHEN 'special_focus' THEN 0 ELSE 1 END,
                     name COLLATE NOCASE
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.residentCharacter()) }
        }

    fun identity(): UserIdentity = readableDatabase.rawQuery(
        "SELECT name, address_preference, bio FROM profile WHERE id = 1",
        null,
    ).use { cursor ->
        if (cursor.moveToFirst()) {
            UserIdentity(cursor.getString(0), cursor.getString(1), cursor.getString(2))
        } else {
            UserIdentity("你", "", "")
        }
    }

    fun userName(): String = identity().name

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
                        put("updated_at", System.currentTimeMillis())
                    },
                )
                val values = ContentValues().apply {
                    put("name", characterName.trim())
                    put("persona", persona.trim())
                    put("is_primary", 1)
                    put("attention_tier", "special_focus")
                    put("created_at", System.currentTimeMillis())
                    put("updated_at", System.currentTimeMillis())
                }
                val id = insertOrThrow("characters", null, values)
                setTransactionSuccessful()
                ResidentCharacter(
                    id,
                    characterName.trim(),
                    persona.trim(),
                    attentionTier = "special_focus",
                )
            } finally {
                endTransaction()
            }
        }
    }

    fun updateIdentity(name: String, addressPreference: String, bio: String) {
        writableDatabase.update(
            "profile",
            ContentValues().apply {
                put("name", name.trim())
                put("address_preference", addressPreference.trim())
                put("bio", bio.trim())
                put("updated_at", System.currentTimeMillis())
            },
            "id = 1",
            null,
        )
    }

    fun addCharacter(
        name: String,
        persona: String,
        attentionTier: String,
        appearance: String,
        clothing: String,
        negativePrompt: String,
        cardJson: String = "",
    ): Long = writableDatabase.insertOrThrow(
        "characters",
        null,
        ContentValues().apply {
            put("name", name.trim())
            put("persona", persona.trim())
            put("attention_tier", attentionTier)
            put("appearance", appearance.trim())
            put("clothing", clothing.trim())
            put("negative_prompt", negativePrompt.trim())
            put("card_json", cardJson)
            put("created_at", System.currentTimeMillis())
            put("updated_at", System.currentTimeMillis())
        },
    )

    fun updateCharacter(character: ResidentCharacter) {
        writableDatabase.run {
            beginTransaction()
            try {
                val previous = rawQuery(
                    "SELECT persona FROM characters WHERE id = ?",
                    arrayOf(character.id.toString()),
                ).use { if (it.moveToFirst()) it.getString(0) else character.persona }
                if (previous != character.persona.trim()) {
                    insertOrThrow(
                        "character_turning_points",
                        null,
                        ContentValues().apply {
                            put("character_id", character.id)
                            put("previous_persona", previous)
                            put("new_persona", character.persona.trim())
                            put("created_at", System.currentTimeMillis())
                        },
                    )
                }
                update(
                    "characters",
                    ContentValues().apply {
                        put("name", character.name.trim())
                        put("persona", character.persona.trim())
                        put("attention_tier", character.attentionTier)
                        put("appearance", character.appearance.trim())
                        put("clothing", character.clothing.trim())
                        put("negative_prompt", character.negativePrompt.trim())
                        put("updated_at", System.currentTimeMillis())
                    },
                    "id = ?",
                    arrayOf(character.id.toString()),
                )
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    fun turningPoints(characterId: Long): List<CharacterTurningPoint> =
        readableDatabase.rawQuery(
            """
            SELECT id, previous_persona, new_persona, created_at
            FROM character_turning_points
            WHERE character_id = ?
            ORDER BY created_at DESC, id DESC
            """.trimIndent(),
            arrayOf(characterId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CharacterTurningPoint(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getLong(3),
                        ),
                    )
                }
            }
        }

    fun relationship(characterId: Long): RelationshipState =
        readableDatabase.rawQuery(
            """
            SELECT label, summary, source_message_id, created_at
            FROM relationship_events
            WHERE character_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(characterId.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                RelationshipState(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getLong(2).takeUnless { cursor.isNull(2) },
                    cursor.getLong(3),
                )
            } else {
                RelationshipState("刚认识", "你们的共同经历才刚刚开始。", null, 0)
            }
        }

    fun recordConversationRelationship(
        characterId: Long,
        sourceMessageId: Long,
        sharedPersonalFact: Boolean,
    ) {
        val current = relationship(characterId)
        val next = nextRelationship(current, sharedPersonalFact) ?: return
        writableDatabase.insertOrThrow(
            "relationship_events",
            null,
            ContentValues().apply {
                put("character_id", characterId)
                put("label", next.first)
                put("summary", next.second)
                put("source_message_id", sourceMessageId)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun conversationRecap(characterId: Long): ConversationRecap? =
        readableDatabase.rawQuery(
            """
            SELECT body, through_message_id, created_at, pinned
            FROM conversation_recaps
            WHERE character_id = ?
            """.trimIndent(),
            arrayOf(characterId.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                ConversationRecap(
                    cursor.getString(0),
                    cursor.getLong(1),
                    cursor.getLong(2),
                    cursor.getInt(3) == 1,
                )
            } else {
                null
            }
        }

    fun saveConversationRecap(characterId: Long, body: String, throughMessageId: Long) {
        val pinned = conversationRecap(characterId)?.pinned == true
        writableDatabase.insertWithOnConflict(
            "conversation_recaps",
            null,
            ContentValues().apply {
                put("character_id", characterId)
                put("body", body.trim())
                put("through_message_id", throughMessageId)
                put("created_at", System.currentTimeMillis())
                put("pinned", if (pinned) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun updateConversationRecap(characterId: Long, body: String) {
        writableDatabase.update(
            "conversation_recaps",
            ContentValues().apply {
                put("body", body.trim())
                put("created_at", System.currentTimeMillis())
            },
            "character_id = ?",
            arrayOf(characterId.toString()),
        )
    }

    fun setConversationRecapPinned(characterId: Long, pinned: Boolean) {
        writableDatabase.update(
            "conversation_recaps",
            ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "character_id = ?",
            arrayOf(characterId.toString()),
        )
    }

    fun setCharacterActive(id: Long, active: Boolean) {
        writableDatabase.update(
            "characters",
            ContentValues().apply {
                put("active", if (active) 1 else 0)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
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

private fun Cursor.residentCharacter() = ResidentCharacter(
    id = getLong(0),
    name = getString(1),
    persona = getString(2),
    attentionTier = getString(3),
    active = getInt(4) == 1,
    appearance = getString(5),
    clothing = getString(6),
    negativePrompt = getString(7),
    cardJson = getString(8),
)
