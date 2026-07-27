package io.github.lzyuuu.ailivesaver

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

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
    val pinned: Boolean = false,
    val source: String = "conversation",
)

internal data class RelationshipEvent(
    val id: Long,
    val characterId: Long,
    val label: String,
    val summary: String,
    val sourceMessageId: Long?,
    val createdAt: Long,
    val active: Boolean,
    val pinned: Boolean,
    val source: String,
)

internal data class RelationshipSignals(
    val tension: Boolean,
    val repair: Boolean,
)

internal fun nextRelationship(
    current: RelationshipState,
    sharedPersonalFact: Boolean,
    signals: RelationshipSignals = RelationshipSignals(false, false),
): Pair<String, String>? = if (current.pinned) {
    null
} else {
    when {
        current.createdAt == 0L ->
            "开始交谈" to "你主动开启了一段只属于你们的对话。"
        signals.tension && current.label == "关系出现裂痕" ->
            "有些疏远" to "刚才的分歧还没有真正过去，你们需要一点空间。"
        signals.tension ->
            "关系出现裂痕" to "这次对话留下了没有被轻轻带过的分歧。"
        signals.repair && current.label in setOf("关系出现裂痕", "有些疏远") ->
            "重新靠近" to "你们愿意把没有说完的部分重新放回对话里。"
        sharedPersonalFact && current.label != "更了解彼此" ->
            "更了解彼此" to "你分享了一件值得长期记住的事。"
        else -> null
    }
}

internal fun relationshipSignals(message: String): RelationshipSignals {
    // ponytail: keyword heuristic keeps relation mutation bounded; replace with structured signals later.
    val normalized = message.trim().lowercase()
    return RelationshipSignals(
        tension = listOf(
            "生气",
            "失望",
            "别烦",
            "讨厌",
            "不想聊",
            "你不懂",
            "吵架",
            "误会",
            "不信任",
            "离开我",
            "angry",
            "disappointed",
            "leave me alone",
            "don't understand",
        ).any(normalized::contains),
        repair = listOf(
            "对不起",
            "抱歉",
            "和好",
            "原谅",
            "想聊聊",
            "我们谈谈",
            "我在乎你",
            "别生气",
            "sorry",
            "forgive",
            "talk this through",
        ).any(normalized::contains),
    )
}

internal data class ChatMessage(
    val id: Long,
    val characterId: Long,
    val sender: String,
    val body: String,
    val createdAt: Long,
    val status: String = "complete",
    val active: Boolean = true,
    val draftBody: String = "",
    val providerName: String = "",
    val modelName: String = "",
    val error: String = "",
)

internal data class MessageVersion(
    val id: Long,
    val messageId: Long,
    val body: String,
    val providerName: String,
    val modelName: String,
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
    val authorKind: String = "user",
    val authorCharacterId: Long? = null,
    val audience: String = "world",
    val audienceCharacterIds: String = "",
    val aiResponsesEnabled: Boolean = true,
    val reactionCount: Int = 0,
    val reactedByUser: Boolean = false,
    val providerName: String = "",
    val modelName: String = "",
    val mediaDescription: String = "",
    val mediaSource: String = "",
)

internal data class SocialPostVersion(
    val id: Long,
    val body: String,
    val providerName: String,
    val modelName: String,
    val createdAt: Long,
)

internal data class MediaJob(
    val postId: Long,
    val prompt: String,
    val negativePrompt: String,
    val steps: Int,
    val cfg: Double,
    val scheduler: String,
    val width: Int,
    val height: Int,
    val seed: Long?,
    val status: String,
    val error: String,
)

internal data class MediaVersion(
    val id: Long,
    val postId: Long,
    val path: String,
    val prompt: String,
    val seed: Long,
    val createdAt: Long,
)

internal data class SocialComment(
    val id: Long,
    val postId: Long,
    val authorName: String,
    val body: String,
    val createdAt: Long,
    val authorKind: String = "user",
    val authorCharacterId: Long? = null,
    val parentId: Long? = null,
    val replyToName: String = "",
)

internal data class SocialResponseJob(
    val id: Long,
    val postId: Long,
    val kind: String,
    val body: String,
    val audience: String,
    val audienceCharacterIds: String,
    val createdAt: Long,
    val attempts: Int,
)

internal data class NpcProfile(
    val id: Long,
    val name: String,
    val bio: String,
    val active: Boolean,
    val createdAt: Long,
    val lastSeenAt: Long,
)

internal data class WorldFact(
    val id: Long,
    val body: String,
    val pinned: Boolean,
    val createdAt: Long,
)

internal data class CharacterCognition(
    val id: Long,
    val characterId: Long,
    val body: String,
    val pinned: Boolean,
    val createdAt: Long,
)

internal data class WorldEvent(
    val id: Long,
    val kind: String,
    val summary: String,
    val actorName: String,
    val needsResponse: Boolean,
    val seen: Boolean,
    val createdAt: Long,
)

internal data class MemberWorldContext(
    val memberKey: String,
    val location: String,
    val timeZone: String,
)

internal class WorldStore(context: Context) :
    SQLiteOpenHelper(context, "world.db", null, 17),
    java.io.Closeable {
    private val mediaDirectory = File(context.filesDir, "media").canonicalFile

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
                created_at INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'complete',
                active INTEGER NOT NULL DEFAULT 1,
                draft_body TEXT NOT NULL DEFAULT '',
                provider_name TEXT NOT NULL DEFAULT '',
                model_name TEXT NOT NULL DEFAULT '',
                error TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        createMessageVersionsTable(database)
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
        createM3Tables(database)
        createSocialResponseQueueTable(database)
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
        if (oldVersion < 10) migrateM3(database)
        if (oldVersion < 11) createM3Tables(database)
        if (oldVersion < 12) repairLegacyPostAuthors(database)
        if (oldVersion < 13) migrateMediaQueue(database)
        if (oldVersion < 14) migrateMediaDescriptions(database)
        if (oldVersion < 15) migrateChatTimeline(database)
        if (oldVersion < 16) migrateRelationshipControls(database)
        if (oldVersion < 17) createSocialResponseQueueTable(database)
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
                media_status TEXT NOT NULL DEFAULT 'none',
                media_negative_prompt TEXT NOT NULL DEFAULT '',
                media_steps INTEGER NOT NULL DEFAULT 20,
                media_cfg REAL NOT NULL DEFAULT 7.5,
                media_scheduler TEXT NOT NULL DEFAULT 'dpm',
                media_width INTEGER NOT NULL DEFAULT 512,
                media_height INTEGER NOT NULL DEFAULT 512,
                media_error TEXT NOT NULL DEFAULT '',
                media_description TEXT NOT NULL DEFAULT '',
                media_source TEXT NOT NULL DEFAULT '',
                author_kind TEXT NOT NULL DEFAULT 'user',
                author_character_id INTEGER REFERENCES characters(id) ON DELETE SET NULL,
                audience TEXT NOT NULL DEFAULT 'world',
                audience_character_ids TEXT NOT NULL DEFAULT '',
                ai_responses_enabled INTEGER NOT NULL DEFAULT 1,
                hidden INTEGER NOT NULL DEFAULT 0,
                provider_name TEXT NOT NULL DEFAULT '',
                model_name TEXT NOT NULL DEFAULT ''
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
                created_at INTEGER NOT NULL,
                author_kind TEXT NOT NULL DEFAULT 'user',
                author_character_id INTEGER REFERENCES characters(id) ON DELETE SET NULL,
                parent_id INTEGER REFERENCES social_comments(id) ON DELETE CASCADE,
                reply_to_name TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
    }

    private fun migrateM3(database: SQLiteDatabase) {
        addColumnIfMissing(
            database,
            "social_posts",
            "author_kind",
            "TEXT NOT NULL DEFAULT 'user'",
        )
        addColumnIfMissing(
            database,
            "social_posts",
            "author_character_id",
            "INTEGER REFERENCES characters(id) ON DELETE SET NULL",
        )
        addColumnIfMissing(database, "social_posts", "audience", "TEXT NOT NULL DEFAULT 'world'")
        addColumnIfMissing(
            database,
            "social_posts",
            "audience_character_ids",
            "TEXT NOT NULL DEFAULT ''",
        )
        addColumnIfMissing(
            database,
            "social_posts",
            "ai_responses_enabled",
            "INTEGER NOT NULL DEFAULT 1",
        )
        addColumnIfMissing(database, "social_posts", "hidden", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(
            database,
            "social_posts",
            "provider_name",
            "TEXT NOT NULL DEFAULT ''",
        )
        addColumnIfMissing(
            database,
            "social_posts",
            "model_name",
            "TEXT NOT NULL DEFAULT ''",
        )
        addColumnIfMissing(
            database,
            "social_comments",
            "author_kind",
            "TEXT NOT NULL DEFAULT 'user'",
        )
        addColumnIfMissing(
            database,
            "social_comments",
            "author_character_id",
            "INTEGER REFERENCES characters(id) ON DELETE SET NULL",
        )
        addColumnIfMissing(
            database,
            "social_comments",
            "parent_id",
            "INTEGER REFERENCES social_comments(id) ON DELETE CASCADE",
        )
        addColumnIfMissing(
            database,
            "social_comments",
            "reply_to_name",
            "TEXT NOT NULL DEFAULT ''",
        )
        database.execSQL(
            """
            UPDATE social_posts
            SET author_kind = CASE
                WHEN author_name = (SELECT name FROM profile WHERE id = 1) THEN 'user'
                WHEN author_name LIKE '% · NPC' THEN 'npc'
                ELSE 'resident'
            END
            """.trimIndent(),
        )
        createM3Tables(database)
    }

    private fun migrateMediaQueue(database: SQLiteDatabase) {
        addColumnIfMissing(
            database,
            "social_posts",
            "media_negative_prompt",
            "TEXT NOT NULL DEFAULT ''",
        )
        addColumnIfMissing(database, "social_posts", "media_steps", "INTEGER NOT NULL DEFAULT 20")
        addColumnIfMissing(database, "social_posts", "media_cfg", "REAL NOT NULL DEFAULT 7.5")
        addColumnIfMissing(
            database,
            "social_posts",
            "media_scheduler",
            "TEXT NOT NULL DEFAULT 'dpm'",
        )
        addColumnIfMissing(database, "social_posts", "media_width", "INTEGER NOT NULL DEFAULT 512")
        addColumnIfMissing(database, "social_posts", "media_height", "INTEGER NOT NULL DEFAULT 512")
        addColumnIfMissing(database, "social_posts", "media_error", "TEXT NOT NULL DEFAULT ''")
    }

    private fun migrateMediaDescriptions(database: SQLiteDatabase) {
        addColumnIfMissing(
            database,
            "social_posts",
            "media_description",
            "TEXT NOT NULL DEFAULT ''",
        )
        addColumnIfMissing(database, "social_posts", "media_source", "TEXT NOT NULL DEFAULT ''")
        database.execSQL(
            """
            UPDATE social_posts
            SET media_description = COALESCE(media_prompt, ''),
                media_source = CASE WHEN media_prompt IS NULL THEN '' ELSE 'local_dream' END
            WHERE media_description = ''
            """.trimIndent(),
        )
    }
    private fun repairLegacyPostAuthors(database: SQLiteDatabase) {
        database.execSQL(
            """
            UPDATE social_posts
            SET author_kind = CASE
                    WHEN author_name LIKE '% · NPC' THEN 'npc'
                    WHEN EXISTS (
                        SELECT 1 FROM characters WHERE characters.name = social_posts.author_name
                    ) THEN 'resident'
                    ELSE 'user'
                END,
                author_character_id = (
                    SELECT id FROM characters
                    WHERE characters.name = social_posts.author_name
                    LIMIT 1
                )
            WHERE provider_name = '' AND model_name = ''
            """.trimIndent(),
        )
    }

    private fun addColumnIfMissing(
        database: SQLiteDatabase,
        table: String,
        column: String,
        declaration: String,
    ) {
        val exists = database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == column }
        }
        if (!exists) database.execSQL("ALTER TABLE $table ADD COLUMN $column $declaration")
    }

    private fun createM3Tables(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS social_reactions (
                post_id INTEGER NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
                actor_kind TEXT NOT NULL,
                actor_name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (post_id, actor_kind, actor_name)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS npcs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                bio TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                last_seen_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS world_facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                body TEXT NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS character_cognition (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                character_id INTEGER NOT NULL REFERENCES characters(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS world_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kind TEXT NOT NULL,
                summary TEXT NOT NULL,
                actor_name TEXT NOT NULL,
                needs_response INTEGER NOT NULL DEFAULT 0,
                seen INTEGER NOT NULL DEFAULT 0,
                source_post_id INTEGER REFERENCES social_posts(id) ON DELETE SET NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS social_post_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                post_id INTEGER NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                provider_name TEXT NOT NULL DEFAULT '',
                model_name TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS member_world_context (
                member_key TEXT PRIMARY KEY,
                location TEXT NOT NULL DEFAULT '',
                time_zone TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
    }

    private fun createSocialResponseQueueTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS social_response_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                post_id INTEGER NOT NULL UNIQUE REFERENCES social_posts(id) ON DELETE CASCADE,
                kind TEXT NOT NULL,
                body TEXT NOT NULL,
                audience TEXT NOT NULL,
                audience_character_ids TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0
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
                created_at INTEGER NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                pinned INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL DEFAULT 'conversation'
            )
            """.trimIndent(),
        )
    }

    private fun migrateRelationshipControls(database: SQLiteDatabase) {
        addColumnIfMissing(database, "relationship_events", "pinned", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing(
            database,
            "relationship_events",
            "source",
            "TEXT NOT NULL DEFAULT 'conversation'",
        )
    }

    private fun createMessageVersionsTable(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_versions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
                body TEXT NOT NULL,
                provider_name TEXT NOT NULL DEFAULT '',
                model_name TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun migrateChatTimeline(database: SQLiteDatabase) {
        addColumnIfMissing(database, "messages", "status", "TEXT NOT NULL DEFAULT 'complete'")
        addColumnIfMissing(database, "messages", "active", "INTEGER NOT NULL DEFAULT 1")
        addColumnIfMissing(database, "messages", "draft_body", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(database, "messages", "provider_name", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(database, "messages", "model_name", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(database, "messages", "error", "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(
            database,
            "relationship_events",
            "active",
            "INTEGER NOT NULL DEFAULT 1",
        )
        createMessageVersionsTable(database)
        database.execSQL(
            """
            INSERT INTO message_versions (
                message_id, body, provider_name, model_name, created_at
            )
            SELECT id, body, provider_name, model_name, created_at
            FROM messages
            WHERE sender = 'assistant' AND body != ''
              AND NOT EXISTS (
                  SELECT 1 FROM message_versions WHERE message_id = messages.id
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
            SELECT label, summary, source_message_id, created_at, pinned, source
            FROM relationship_events
            WHERE character_id = ? AND active = 1
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
                    cursor.getInt(4) == 1,
                    cursor.getString(5),
                )
            } else {
                RelationshipState("刚认识", "你们的共同经历才刚刚开始。", null, 0)
            }
        }

    fun recordConversationRelationship(
        characterId: Long,
        sourceMessageId: Long,
        sharedPersonalFact: Boolean,
        messageBody: String = "",
    ) {
        val current = relationship(characterId)
        if (current.pinned) return
        val next = nextRelationship(
            current,
            sharedPersonalFact,
            relationshipSignals(messageBody),
        ) ?: return
        writableDatabase.insertOrThrow(
            "relationship_events",
            null,
            ContentValues().apply {
                put("character_id", characterId)
                put("label", next.first)
                put("summary", next.second)
                put("source_message_id", sourceMessageId)
                put("created_at", System.currentTimeMillis())
                put("source", "conversation")
            },
        )
    }

    fun relationshipEvents(characterId: Long): List<RelationshipEvent> =
        readableDatabase.rawQuery(
            """
            SELECT id, character_id, label, summary, source_message_id, created_at, active,
                   pinned, source
            FROM relationship_events
            WHERE character_id = ?
            ORDER BY created_at DESC, id DESC
            """.trimIndent(),
            arrayOf(characterId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        RelationshipEvent(
                            id = cursor.getLong(0),
                            characterId = cursor.getLong(1),
                            label = cursor.getString(2),
                            summary = cursor.getString(3),
                            sourceMessageId = cursor.getLong(4).takeUnless { cursor.isNull(4) },
                            createdAt = cursor.getLong(5),
                            active = cursor.getInt(6) == 1,
                            pinned = cursor.getInt(7) == 1,
                            source = cursor.getString(8),
                        ),
                    )
                }
            }
        }

    fun correctRelationship(
        characterId: Long,
        label: String,
        summary: String,
        pinned: Boolean,
    ) {
        val cleanLabel = label.trim()
        val cleanSummary = summary.trim()
        require(cleanLabel.isNotEmpty() && cleanSummary.isNotEmpty())
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "relationship_events",
                ContentValues().apply { put("active", 0) },
                "character_id = ? AND active = 1",
                arrayOf(characterId.toString()),
            )
            writableDatabase.insertOrThrow(
                "relationship_events",
                null,
                ContentValues().apply {
                    put("character_id", characterId)
                    put("label", cleanLabel)
                    put("summary", cleanSummary)
                    putNull("source_message_id")
                    put("created_at", System.currentTimeMillis())
                    put("active", 1)
                    put("pinned", if (pinned) 1 else 0)
                    put("source", "user_correction")
                },
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun setRelationshipPinned(characterId: Long, pinned: Boolean) {
        val id = readableDatabase.rawQuery(
            """
            SELECT id FROM relationship_events
            WHERE character_id = ? AND active = 1
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(characterId.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null } ?: return
        writableDatabase.update(
            "relationship_events",
            ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
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

    fun deleteCharacter(id: Long) {
        writableDatabase.apply {
            beginTransaction()
            try {
                delete(
                    "member_world_context",
                    "member_key = ?",
                    arrayOf("character:$id"),
                )
                delete("characters", "id = ?", arrayOf(id.toString()))
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    fun messages(
        characterId: Long,
        includeRetired: Boolean = false,
    ): List<ChatMessage> = readableDatabase.rawQuery(
        """
        SELECT id, character_id, sender, body, created_at, status, active, draft_body,
               provider_name, model_name, error
        FROM messages
        WHERE character_id = ? ${if (includeRetired) "" else "AND active = 1"}
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
                        status = cursor.getString(5),
                        active = cursor.getInt(6) == 1,
                        draftBody = cursor.getString(7),
                        providerName = cursor.getString(8),
                        modelName = cursor.getString(9),
                        error = cursor.getString(10),
                    ),
                )
            }
        }
    }

    fun retiredMessages(characterId: Long): List<ChatMessage> =
        messages(characterId, includeRetired = true).filterNot(ChatMessage::active)

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

    fun beginAssistantReply(
        characterId: Long,
        providerName: String,
        modelName: String,
        existingMessageId: Long? = null,
    ): ChatMessage {
        val now = System.currentTimeMillis()
        val id = if (existingMessageId == null) {
            writableDatabase.insertOrThrow(
                "messages",
                null,
                ContentValues().apply {
                    put("character_id", characterId)
                    put("sender", "assistant")
                    put("body", "")
                    put("created_at", now)
                    put("status", "streaming")
                    put("provider_name", providerName)
                    put("model_name", modelName)
                },
            )
        } else {
            val updated = writableDatabase.update(
                "messages",
                ContentValues().apply {
                    put("status", "streaming")
                    put("draft_body", "")
                    put("provider_name", providerName)
                    put("model_name", modelName)
                    put("error", "")
                },
                "id = ? AND character_id = ? AND sender = 'assistant' AND active = 1",
                arrayOf(existingMessageId.toString(), characterId.toString()),
            )
            require(updated == 1) { "Reply is no longer in the current timeline" }
            existingMessageId
        }
        return messages(characterId).first { it.id == id }
    }

    fun updateAssistantDraft(messageId: Long, body: String) {
        writableDatabase.update(
            "messages",
            ContentValues().apply { put("draft_body", body) },
            "id = ? AND sender = 'assistant' AND status = 'streaming' AND active = 1",
            arrayOf(messageId.toString()),
        )
    }

    fun completeAssistantReply(
        messageId: Long,
        body: String,
        providerName: String,
        modelName: String,
    ) {
        val finalBody = body.trim()
        require(finalBody.isNotEmpty()) { "Provider returned an empty reply" }
        writableDatabase.run {
            beginTransaction()
            try {
                val updated = update(
                    "messages",
                    ContentValues().apply {
                        put("body", finalBody)
                        put("draft_body", "")
                        put("status", "complete")
                        put("provider_name", providerName)
                        put("model_name", modelName)
                        put("error", "")
                    },
                    "id = ? AND sender = 'assistant' AND active = 1",
                    arrayOf(messageId.toString()),
                )
                require(updated == 1) { "Reply is no longer in the current timeline" }
                insertOrThrow(
                    "message_versions",
                    null,
                    ContentValues().apply {
                        put("message_id", messageId)
                        put("body", finalBody)
                        put("provider_name", providerName)
                        put("model_name", modelName)
                        put("created_at", System.currentTimeMillis())
                    },
                )
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    fun failAssistantReply(messageId: Long, error: String) {
        writableDatabase.update(
            "messages",
            ContentValues().apply {
                put("status", "failed")
                put("error", error.take(240))
            },
            "id = ? AND sender = 'assistant' AND active = 1",
            arrayOf(messageId.toString()),
        )
    }

    fun recoverInterruptedReplies(characterId: Long? = null): Int =
        writableDatabase.update(
            "messages",
            ContentValues().apply {
                put("status", "interrupted")
                put("error", "")
            },
            buildString {
                append("sender = 'assistant' AND status = 'streaming' AND active = 1")
                if (characterId != null) append(" AND character_id = ?")
            },
            characterId?.let { arrayOf(it.toString()) },
        )

    fun messageVersions(messageId: Long): List<MessageVersion> = readableDatabase.rawQuery(
        """
        SELECT id, message_id, body, provider_name, model_name, created_at
        FROM message_versions
        WHERE message_id = ?
        ORDER BY created_at DESC, id DESC
        """.trimIndent(),
        arrayOf(messageId.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    MessageVersion(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getLong(5),
                    ),
                )
            }
        }
    }

    fun restoreMessageVersion(messageId: Long, versionId: Long) {
        readableDatabase.rawQuery(
            """
            SELECT body, provider_name, model_name
            FROM message_versions
            WHERE id = ? AND message_id = ?
            """.trimIndent(),
            arrayOf(versionId.toString(), messageId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return
            writableDatabase.update(
                "messages",
                ContentValues().apply {
                    put("body", cursor.getString(0))
                    put("provider_name", cursor.getString(1))
                    put("model_name", cursor.getString(2))
                    put("status", "complete")
                    put("draft_body", "")
                    put("error", "")
                },
                "id = ? AND sender = 'assistant' AND active = 1",
                arrayOf(messageId.toString()),
            )
        }
    }

    fun pinnedMemoriesFrom(characterId: Long, messageId: Long): Int =
        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM memories
            WHERE character_id = ? AND source_message_id >= ? AND pinned = 1
            """.trimIndent(),
            arrayOf(characterId.toString(), messageId.toString()),
        ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    fun rewriteFromMessage(
        characterId: Long,
        messageId: Long,
        body: String,
    ): ChatMessage = writableDatabase.run {
        beginTransaction()
        try {
            val valid = rawQuery(
                """
                SELECT EXISTS(
                    SELECT 1 FROM messages
                    WHERE id = ? AND character_id = ? AND sender = 'user' AND active = 1
                )
                """.trimIndent(),
                arrayOf(messageId.toString(), characterId.toString()),
            ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }
            require(valid) { "Message is no longer in the current timeline" }

            update(
                "relationship_events",
                ContentValues().apply { put("active", 0) },
                """
                character_id = ? AND source_message_id IN (
                    SELECT id FROM messages
                    WHERE character_id = ? AND id >= ? AND active = 1
                )
                """.trimIndent(),
                arrayOf(characterId.toString(), characterId.toString(), messageId.toString()),
            )
            delete(
                "memories",
                """
                character_id = ? AND pinned = 0 AND source_message_id IN (
                    SELECT id FROM messages
                    WHERE character_id = ? AND id >= ? AND active = 1
                )
                """.trimIndent(),
                arrayOf(characterId.toString(), characterId.toString(), messageId.toString()),
            )
            delete(
                "conversation_recaps",
                "character_id = ? AND through_message_id >= ?",
                arrayOf(characterId.toString(), messageId.toString()),
            )
            update(
                "messages",
                ContentValues().apply { put("active", 0) },
                "character_id = ? AND id >= ? AND active = 1",
                arrayOf(characterId.toString(), messageId.toString()),
            )
            val createdAt = System.currentTimeMillis()
            val newId = insertOrThrow(
                "messages",
                null,
                ContentValues().apply {
                    put("character_id", characterId)
                    put("sender", "user")
                    put("body", body.trim())
                    put("created_at", createdAt)
                },
            )
            setTransactionSuccessful()
            ChatMessage(newId, characterId, "user", body.trim(), createdAt)
        } finally {
            endTransaction()
        }
    }

    fun remember(characterId: Long, sourceMessageId: Long, body: String) {
        rememberIfCurrent(characterId, sourceMessageId, body)
    }

    fun rememberIfCurrent(characterId: Long, sourceMessageId: Long, body: String): Boolean {
        val cleanBody = body.trim()
        if (cleanBody.isBlank()) return false
        writableDatabase.beginTransaction()
        try {
            val current = readableDatabase.rawQuery(
                """
                SELECT EXISTS(
                    SELECT 1 FROM messages
                    WHERE id = ? AND character_id = ? AND sender = 'user' AND active = 1
                )
                """.trimIndent(),
                arrayOf(sourceMessageId.toString(), characterId.toString()),
            ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }
            if (!current) return false
            val values = ContentValues().apply {
                put("character_id", characterId)
                put("body", cleanBody)
                put("source_message_id", sourceMessageId)
                put("created_at", System.currentTimeMillis())
            }
            writableDatabase.insertOrThrow("memories", null, values)
            writableDatabase.setTransactionSuccessful()
            return true
        } finally {
            writableDatabase.endTransaction()
        }
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
        return createPost(kind, userName(), title, body, authorKind = "user")
    }

    fun createPost(
        kind: String,
        authorName: String,
        title: String,
        body: String,
        authorKind: String = "resident",
        authorCharacterId: Long? = null,
        audience: String = "world",
        audienceCharacterIds: String = "",
        aiResponsesEnabled: Boolean = true,
        providerName: String = "",
        modelName: String = "",
        worldEventKind: String? = null,
        eventNeedsResponse: Boolean = authorKind == "resident",
    ): Long {
        val postId = writableDatabase.insertOrThrow(
            "social_posts",
            null,
            ContentValues().apply {
                put("kind", kind)
                put("author_name", authorName)
                put("title", title.trim())
                put("body", body.trim())
                put("created_at", System.currentTimeMillis())
                put("author_kind", authorKind)
                put("author_character_id", authorCharacterId)
                put("audience", audience)
                put("audience_character_ids", audienceCharacterIds)
                put("ai_responses_enabled", if (aiResponsesEnabled) 1 else 0)
                put("provider_name", providerName)
                put("model_name", modelName)
            },
        )
        addWorldEvent(
            kind = worldEventKind ?: if (kind == "moment") "moment" else "commons",
            summary = title.ifBlank { body }.take(120),
            actorName = authorName,
            needsResponse = eventNeedsResponse,
            sourcePostId = postId,
        )
        return postId
    }

    fun posts(kind: String, sort: String = "latest"): List<SocialPost> {
        val order = if (kind == "forum" && sort == "active") {
            "MAX(social_posts.created_at, COALESCE((SELECT MAX(created_at) FROM social_comments WHERE post_id = social_posts.id), 0)) DESC"
        } else {
            "social_posts.created_at DESC"
        }
        return readableDatabase.rawQuery(
            """
        SELECT id, kind, author_name, title, body, created_at,
               media_path, media_prompt, media_seed, media_status,
               author_kind, author_character_id, audience, audience_character_ids,
               ai_responses_enabled,
               (SELECT COUNT(*) FROM social_reactions WHERE post_id = social_posts.id),
               EXISTS(
                   SELECT 1 FROM social_reactions
                   WHERE post_id = social_posts.id AND actor_kind = 'user' AND actor_name = ?
               ),
               provider_name, model_name, media_description, media_source
        FROM social_posts
        WHERE kind = ? AND hidden = 0 AND media_status IN ('none', 'ready')
        ORDER BY $order
        """.trimIndent(),
            arrayOf(userName(), kind),
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
                        cursor.getString(10),
                        if (cursor.isNull(11)) null else cursor.getLong(11),
                        cursor.getString(12),
                        cursor.getString(13),
                        cursor.getInt(14) == 1,
                        cursor.getInt(15),
                        cursor.getInt(16) == 1,
                        cursor.getString(17),
                        cursor.getString(18),
                        cursor.getString(19),
                        cursor.getString(20),
                    ),
                )
            }
        }
    }
    }

    fun comments(postId: Long): List<SocialComment> = readableDatabase.rawQuery(
        """
        SELECT id, post_id, author_name, body, created_at,
               author_kind, author_character_id, parent_id, reply_to_name
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
                        cursor.getString(5),
                        if (cursor.isNull(6)) null else cursor.getLong(6),
                        if (cursor.isNull(7)) null else cursor.getLong(7),
                        cursor.getString(8),
                    ),
                )
            }
        }
    }

    fun addComment(
        postId: Long,
        body: String,
        parentId: Long? = null,
        replyToName: String = "",
        authorName: String = userName(),
        authorKind: String = "user",
        authorCharacterId: Long? = null,
    ) {
        writableDatabase.insertOrThrow(
            "social_comments",
            null,
            ContentValues().apply {
                put("post_id", postId)
                put("author_name", authorName)
                put("body", body.trim())
                put("created_at", System.currentTimeMillis())
                put("author_kind", authorKind)
                put("author_character_id", authorCharacterId)
                put("parent_id", parentId)
                put("reply_to_name", replyToName)
            },
        )
    }

    fun createMediaPost(
        body: String,
        prompt: String,
        audience: String = "world",
        audienceCharacterIds: String = "",
        aiResponsesEnabled: Boolean = true,
    ): Long {
        return writableDatabase.insertOrThrow(
            "social_posts",
            null,
            ContentValues().apply {
                put("kind", "moment")
                put("author_name", userName())
                put("body", body.trim())
                put("media_prompt", prompt.trim())
                put("media_status", "pending")
                put("media_description", prompt.trim())
                put("media_source", "local_dream")
                put("created_at", System.currentTimeMillis())
                put("author_kind", "user")
                put("audience", audience)
                put("audience_character_ids", audienceCharacterIds)
                put("ai_responses_enabled", if (aiResponsesEnabled) 1 else 0)
            },
        )
    }

    fun createImportedMediaPost(
        body: String,
        path: String,
        description: String,
        audience: String,
        audienceCharacterIds: String,
        aiResponsesEnabled: Boolean,
    ): Long {
        val createdAt = System.currentTimeMillis()
        val postId = writableDatabase.insertOrThrow(
            "social_posts",
            null,
            ContentValues().apply {
                put("kind", "moment")
                put("author_name", userName())
                put("body", body.trim())
                put("media_path", path)
                put("media_status", "ready")
                put("media_description", description.trim())
                put("media_source", "user")
                if (description.isNotBlank()) put("media_prompt", description.trim())
                put("created_at", createdAt)
                put("author_kind", "user")
                put("audience", audience)
                put("audience_character_ids", audienceCharacterIds)
                put("ai_responses_enabled", if (aiResponsesEnabled) 1 else 0)
            },
        )
        writableDatabase.insertOrThrow(
            "media_versions",
            null,
            ContentValues().apply {
                put("post_id", postId)
                put("path", path)
                put("prompt", description.trim())
                put("seed", 0)
                put("created_at", createdAt)
            },
        )
        addWorldEvent("moment", body.take(120), userName(), false, postId)
        return postId
    }

    fun toggleReaction(postId: Long) {
        val name = userName()
        val removed = writableDatabase.delete(
            "social_reactions",
            "post_id = ? AND actor_kind = 'user' AND actor_name = ?",
            arrayOf(postId.toString(), name),
        )
        if (removed == 0) {
            writableDatabase.insertOrThrow(
                "social_reactions",
                null,
                ContentValues().apply {
                    put("post_id", postId)
                    put("actor_kind", "user")
                    put("actor_name", name)
                    put("created_at", System.currentTimeMillis())
                },
            )
        }
    }

    fun updateUserPost(postId: Long, title: String, body: String) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply {
                put("title", title.trim())
                put("body", body.trim())
            },
            "id = ? AND author_kind = 'user'",
            arrayOf(postId.toString()),
        )
    }

    fun deleteUserPost(postId: Long) {
        deletePost(postId, "author_kind = 'user'")
    }

    fun deleteAiPost(postId: Long) {
        deletePost(postId, "author_kind != 'user'")
    }

    private fun deletePost(postId: Long, authorClause: String) {
        val paths = readableDatabase.rawQuery(
            """
            SELECT media_path FROM social_posts WHERE id = ? AND media_path IS NOT NULL
            UNION
            SELECT path FROM media_versions WHERE post_id = ?
            """.trimIndent(),
            arrayOf(postId.toString(), postId.toString()),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "world_events",
                "source_post_id = ?",
                arrayOf(postId.toString()),
            )
            writableDatabase.delete(
                "social_posts",
                "id = ? AND $authorClause",
                arrayOf(postId.toString()),
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        paths.forEach(::deleteMediaFileIfUnreferenced)
    }

    fun hideAiPost(postId: Long) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply { put("hidden", 1) },
            "id = ? AND author_kind != 'user'",
            arrayOf(postId.toString()),
        )
        writableDatabase.update(
            "world_events",
            ContentValues().apply { put("seen", 1) },
            "source_post_id = ?",
            arrayOf(postId.toString()),
        )
    }

    fun socialPostVersions(postId: Long): List<SocialPostVersion> =
        readableDatabase.rawQuery(
            """
            SELECT id, body, provider_name, model_name, created_at
            FROM social_post_versions
            WHERE post_id = ?
            ORDER BY created_at DESC, id DESC
            """.trimIndent(),
            arrayOf(postId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SocialPostVersion(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getLong(4),
                        ),
                    )
                }
            }
        }

    fun rewriteAiPost(
        postId: Long,
        body: String,
        providerName: String,
        modelName: String,
    ) {
        writableDatabase.run {
            beginTransaction()
            try {
                val current = rawQuery(
                    """
                    SELECT body, provider_name, model_name, created_at
                    FROM social_posts
                    WHERE id = ? AND author_kind != 'user'
                    """.trimIndent(),
                    arrayOf(postId.toString()),
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        SocialPostVersion(
                            0,
                            cursor.getString(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getLong(3),
                        )
                    } else {
                        null
                    }
                } ?: return@run
                insertOrThrow(
                    "social_post_versions",
                    null,
                    ContentValues().apply {
                        put("post_id", postId)
                        put("body", current.body)
                        put("provider_name", current.providerName)
                        put("model_name", current.modelName)
                        put("created_at", current.createdAt)
                    },
                )
                update(
                    "social_posts",
                    ContentValues().apply {
                        put("body", body.trim())
                        put("provider_name", providerName)
                        put("model_name", modelName)
                        put("created_at", System.currentTimeMillis())
                    },
                    "id = ? AND author_kind != 'user'",
                    arrayOf(postId.toString()),
                )
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    fun restoreAiPostVersion(postId: Long, versionId: Long) {
        val version = readableDatabase.rawQuery(
            """
            SELECT body, provider_name, model_name
            FROM social_post_versions
            WHERE id = ? AND post_id = ?
            """.trimIndent(),
            arrayOf(versionId.toString(), postId.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))
            } else {
                null
            }
        } ?: return
        rewriteAiPost(postId, version.first, version.second, version.third)
    }

    fun addWorldEvent(
        kind: String,
        summary: String,
        actorName: String,
        needsResponse: Boolean,
        sourcePostId: Long? = null,
    ) {
        writableDatabase.insertOrThrow(
            "world_events",
            null,
            ContentValues().apply {
                put("kind", kind)
                put("summary", summary.trim())
                put("actor_name", actorName)
                put("needs_response", if (needsResponse) 1 else 0)
                put("source_post_id", sourcePostId)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun worldEvents(needsResponseOnly: Boolean = false): List<WorldEvent> =
        readableDatabase.rawQuery(
            """
            SELECT id, kind, summary, actor_name, needs_response, seen, created_at
            FROM world_events
            ${if (needsResponseOnly) "WHERE needs_response = 1 AND seen = 0" else ""}
            ORDER BY created_at DESC
            LIMIT 20
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        WorldEvent(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getInt(4) == 1,
                            cursor.getInt(5) == 1,
                            cursor.getLong(6),
                        ),
                    )
                }
            }
        }

    fun markWorldEventSeen(id: Long) {
        writableDatabase.update(
            "world_events",
            ContentValues().apply { put("seen", 1) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun ensureNpc(name: String, bio: String): NpcProfile {
        val existing = readableDatabase.rawQuery(
            """
            SELECT id, name, bio, active, created_at, last_seen_at
            FROM npcs WHERE name = ? ORDER BY id DESC LIMIT 1
            """.trimIndent(),
            arrayOf(name),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.npcProfile() else null }
        if (existing != null) {
            writableDatabase.update(
                "npcs",
                ContentValues().apply {
                    put("active", 1)
                    put("last_seen_at", System.currentTimeMillis())
                },
                "id = ?",
                arrayOf(existing.id.toString()),
            )
            return existing.copy(active = true, lastSeenAt = System.currentTimeMillis())
        }
        val now = System.currentTimeMillis()
        val id = writableDatabase.insertOrThrow(
            "npcs",
            null,
            ContentValues().apply {
                put("name", name.trim())
                put("bio", bio.trim())
                put("created_at", now)
                put("last_seen_at", now)
            },
        )
        return NpcProfile(id, name.trim(), bio.trim(), true, now, now)
    }

    fun npcByName(name: String): NpcProfile? = readableDatabase.rawQuery(
        """
        SELECT id, name, bio, active, created_at, last_seen_at
        FROM npcs WHERE name = ? ORDER BY id DESC LIMIT 1
        """.trimIndent(),
        arrayOf(name.removeSuffix(" · NPC")),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.npcProfile() else null }

    fun npcs(includeDeparted: Boolean = true): List<NpcProfile> = readableDatabase.rawQuery(
        """
        SELECT id, name, bio, active, created_at, last_seen_at
        FROM npcs
        ${if (includeDeparted) "" else "WHERE active = 1"}
        ORDER BY active DESC, last_seen_at DESC
        """.trimIndent(),
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.npcProfile())
        }
    }

    fun retireNpc(id: Long) {
        writableDatabase.update(
            "npcs",
            ContentValues().apply {
                put("active", 0)
                put("last_seen_at", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun retireOtherNpcs(activeId: Long) {
        writableDatabase.update(
            "npcs",
            ContentValues().apply {
                put("active", 0)
                put("last_seen_at", System.currentTimeMillis())
            },
            "id != ? AND active = 1",
            arrayOf(activeId.toString()),
        )
    }

    fun worldFacts(): List<WorldFact> = readableDatabase.rawQuery(
        "SELECT id, body, pinned, created_at FROM world_facts ORDER BY pinned DESC, created_at DESC",
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(WorldFact(cursor.getLong(0), cursor.getString(1), cursor.getInt(2) == 1, cursor.getLong(3)))
            }
        }
    }

    fun addWorldFact(body: String, pinned: Boolean = false) {
        writableDatabase.insertOrThrow(
            "world_facts",
            null,
            ContentValues().apply {
                put("body", body.trim())
                put("pinned", if (pinned) 1 else 0)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun deleteWorldFact(id: Long) {
        writableDatabase.delete("world_facts", "id = ?", arrayOf(id.toString()))
    }

    fun setWorldFactPinned(id: Long, pinned: Boolean) {
        writableDatabase.update(
            "world_facts",
            ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun characterCognition(characterId: Long): List<CharacterCognition> =
        readableDatabase.rawQuery(
            """
            SELECT id, character_id, body, pinned, created_at
            FROM character_cognition
            WHERE character_id = ?
            ORDER BY pinned DESC, created_at DESC
            """.trimIndent(),
            arrayOf(characterId.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CharacterCognition(
                            cursor.getLong(0),
                            cursor.getLong(1),
                            cursor.getString(2),
                            cursor.getInt(3) == 1,
                            cursor.getLong(4),
                        ),
                    )
                }
            }
        }

    fun addCharacterCognition(characterId: Long, body: String) {
        writableDatabase.insertOrThrow(
            "character_cognition",
            null,
            ContentValues().apply {
                put("character_id", characterId)
                put("body", body.trim())
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun deleteCharacterCognition(id: Long) {
        writableDatabase.delete("character_cognition", "id = ?", arrayOf(id.toString()))
    }

    fun setCharacterCognitionPinned(id: Long, pinned: Boolean) {
        writableDatabase.update(
            "character_cognition",
            ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun promoteCognitionToWorldFact(id: Long) {
        readableDatabase.rawQuery(
            "SELECT body FROM character_cognition WHERE id = ?",
            arrayOf(id.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) addWorldFact(cursor.getString(0), pinned = true)
        }
    }

    fun memberWorldContext(memberKey: String): MemberWorldContext =
        readableDatabase.rawQuery(
            """
            SELECT member_key, location, time_zone
            FROM member_world_context
            WHERE member_key = ?
            """.trimIndent(),
            arrayOf(memberKey),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                MemberWorldContext(cursor.getString(0), cursor.getString(1), cursor.getString(2))
            } else {
                MemberWorldContext(memberKey, "", "")
            }
        }

    fun saveMemberWorldContext(memberKey: String, location: String, timeZone: String) {
        writableDatabase.insertWithOnConflict(
            "member_world_context",
            null,
            ContentValues().apply {
                put("member_key", memberKey)
                put("location", location.trim())
                put("time_zone", timeZone.trim())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun markMediaReady(postId: Long, path: String, seed: Long) {
        var eventSummary = ""
        var actorName = ""
        writableDatabase.run {
            beginTransaction()
            try {
                val post = rawQuery(
                    "SELECT media_prompt, body, author_name FROM social_posts WHERE id = ?",
                    arrayOf(postId.toString()),
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2))
                    } else {
                        Triple("", "", "")
                    }
                }
                val prompt = post.first
                eventSummary = post.second
                actorName = post.third
                update(
                    "social_posts",
                    ContentValues().apply {
                        put("media_path", path)
                        put("media_seed", seed)
                        put("media_status", "ready")
                        put("media_error", "")
                        put("media_description", prompt)
                        put("media_source", "local_dream")
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
        if (eventSummary.isNotBlank()) {
            addWorldEvent(
                "moment",
                eventSummary.take(120),
                actorName,
                needsResponse = false,
                sourcePostId = postId,
            )
        }
    }

    fun markMediaFailed(postId: Long) {
        markMediaFailed(postId, "")
    }

    fun markMediaFailed(postId: Long, error: String) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply {
                put("media_status", "failed")
                put("media_error", error.take(240))
            },
            "id = ?",
            arrayOf(postId.toString()),
        )
    }

    fun markMediaWaiting(postId: Long, error: String) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply {
                put("media_status", "pending")
                put("media_error", error.take(240))
            },
            "id = ?",
            arrayOf(postId.toString()),
        )
    }

    fun updateMediaDescription(postId: Long, description: String) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply { put("media_description", description.trim()) },
            "id = ? AND media_source = 'user'",
            arrayOf(postId.toString()),
        )
    }

    fun prepareRedraw(postId: Long, prompt: String) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply {
                put("media_prompt", prompt.trim())
                put("media_status", "pending")
                putNull("media_seed")
                put("media_error", "")
            },
            "id = ?",
            arrayOf(postId.toString()),
        )
    }

    fun nextPendingMediaJob(): MediaJob? = readableDatabase.rawQuery(
        """
        SELECT id, media_prompt, media_negative_prompt, media_steps, media_cfg,
               media_scheduler, media_width, media_height, media_seed, media_status, media_error
        FROM social_posts
        WHERE media_status = 'pending' AND media_prompt IS NOT NULL
        ORDER BY created_at, id
        LIMIT 1
        """.trimIndent(),
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        MediaJob(
            postId = cursor.getLong(0),
            prompt = cursor.getString(1),
            negativePrompt = cursor.getString(2),
            steps = cursor.getInt(3),
            cfg = cursor.getDouble(4),
            scheduler = cursor.getString(5),
            width = cursor.getInt(6),
            height = cursor.getInt(7),
            seed = if (cursor.isNull(8)) null else cursor.getLong(8),
            status = cursor.getString(9),
            error = cursor.getString(10),
        )
    }

    fun mediaJobs(): List<MediaJob> = readableDatabase.rawQuery(
        """
        SELECT id, media_prompt, media_negative_prompt, media_steps, media_cfg,
               media_scheduler, media_width, media_height, media_seed, media_status, media_error
        FROM social_posts
        WHERE media_status IN ('pending', 'failed') AND media_prompt IS NOT NULL
        ORDER BY created_at, id
        """.trimIndent(),
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    MediaJob(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getInt(3),
                        cursor.getDouble(4),
                        cursor.getString(5),
                        cursor.getInt(6),
                        cursor.getInt(7),
                        if (cursor.isNull(8)) null else cursor.getLong(8),
                        cursor.getString(9),
                        cursor.getString(10),
                    ),
                )
            }
        }
    }

    fun retryMediaJob(postId: Long) {
        writableDatabase.update(
            "social_posts",
            ContentValues().apply {
                put("media_status", "pending")
                put("media_error", "")
            },
            "id = ? AND media_status = 'failed'",
            arrayOf(postId.toString()),
        )
    }

    fun mediaVersions(postId: Long): List<MediaVersion> = readableDatabase.rawQuery(
        """
        SELECT id, post_id, path, prompt, seed, created_at
        FROM media_versions
        WHERE post_id = ?
        ORDER BY created_at DESC, id DESC
        """.trimIndent(),
        arrayOf(postId.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    MediaVersion(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getLong(4),
                        cursor.getLong(5),
                    ),
                )
            }
        }
    }

    fun restoreMediaVersion(postId: Long, versionId: Long) {
        readableDatabase.rawQuery(
            """
            SELECT path, prompt, seed
            FROM media_versions
            WHERE id = ? AND post_id = ?
            """.trimIndent(),
            arrayOf(versionId.toString(), postId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return
            writableDatabase.update(
                "social_posts",
                ContentValues().apply {
                    put("media_path", cursor.getString(0))
                    put("media_prompt", cursor.getString(1))
                    put("media_seed", cursor.getLong(2))
                    put("media_status", "ready")
                    put("media_error", "")
                },
                "id = ?",
                arrayOf(postId.toString()),
            )
        }
    }

    fun deleteMediaVersion(postId: Long, versionId: Long) {
        val version = mediaVersions(postId).firstOrNull { it.id == versionId } ?: return
        val currentPath = readableDatabase.rawQuery(
            "SELECT media_path FROM social_posts WHERE id = ?",
            arrayOf(postId.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (version.path == currentPath) return
        writableDatabase.delete(
            "media_versions",
            "id = ? AND post_id = ?",
            arrayOf(versionId.toString(), postId.toString()),
        )
        val referenced = readableDatabase.rawQuery(
            """
            SELECT EXISTS(SELECT 1 FROM media_versions WHERE path = ?)
                OR EXISTS(SELECT 1 FROM social_posts WHERE media_path = ?)
            """.trimIndent(),
            arrayOf(version.path, version.path),
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }
        if (!referenced) deleteOwnedMediaFile(version.path)
    }

    private fun deleteMediaFileIfUnreferenced(path: String) {
        val referenced = readableDatabase.rawQuery(
            """
            SELECT EXISTS(SELECT 1 FROM media_versions WHERE path = ?)
                OR EXISTS(SELECT 1 FROM social_posts WHERE media_path = ?)
            """.trimIndent(),
            arrayOf(path, path),
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }
        if (!referenced) deleteOwnedMediaFile(path)
    }

    private fun deleteOwnedMediaFile(path: String) {
        runCatching { File(path).canonicalFile }
            .getOrNull()
            ?.takeIf { it.parentFile == mediaDirectory }
            ?.delete()
    }

    fun mediaStorageBytes(): Long = readableDatabase.rawQuery(
        """
        SELECT path FROM media_versions
        UNION
        SELECT media_path FROM social_posts WHERE media_path IS NOT NULL
        """.trimIndent(),
        null,
    ).use { cursor ->
        var total = 0L
        while (cursor.moveToNext()) total += File(cursor.getString(0)).length()
        total
    }

    fun enqueueSocialResponse(
        postId: Long,
        kind: String,
        body: String,
        audience: String,
        audienceCharacterIds: String,
    ): Long {
        writableDatabase.insertWithOnConflict(
            "social_response_queue",
            null,
            ContentValues().apply {
                put("post_id", postId)
                put("kind", kind)
                put("body", body.trim())
                put("audience", audience)
                put("audience_character_ids", audienceCharacterIds)
                put("created_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return readableDatabase.rawQuery(
            "SELECT id FROM social_response_queue WHERE post_id = ?",
            arrayOf(postId.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
    }

    fun socialResponseJobs(limit: Int = 3): List<SocialResponseJob> = readableDatabase.rawQuery(
        """
        SELECT id, post_id, kind, body, audience, audience_character_ids, created_at, attempts
        FROM social_response_queue
        ORDER BY created_at, id
        LIMIT ?
        """.trimIndent(),
        arrayOf(limit.coerceIn(1, 20).toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    SocialResponseJob(
                        id = cursor.getLong(0),
                        postId = cursor.getLong(1),
                        kind = cursor.getString(2),
                        body = cursor.getString(3),
                        audience = cursor.getString(4),
                        audienceCharacterIds = cursor.getString(5),
                        createdAt = cursor.getLong(6),
                        attempts = cursor.getInt(7),
                    ),
                )
            }
        }
    }

    fun incrementSocialResponseAttempts(id: Long) {
        writableDatabase.execSQL(
            "UPDATE social_response_queue SET attempts = attempts + 1 WHERE id = ?",
            arrayOf(id),
        )
    }

    fun removeSocialResponse(id: Long) {
        writableDatabase.delete("social_response_queue", "id = ?", arrayOf(id.toString()))
    }

    fun socialResponseQueueCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM social_response_queue",
        null,
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    fun queueCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM social_posts WHERE media_status IN ('pending', 'failed')",
        null,
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
}

internal object MemoryExtractor {
    // ponytail: lexical gate keeps routine chat off the extra Memory Provider request.
    private val candidateHints = listOf(
        "我叫",
        "我的",
        "我喜欢",
        "我不喜欢",
        "我住",
        "我在",
        "我从事",
        "我的工作",
        "工作",
        "我的生日",
        "我计划",
        "remember",
        "my name",
        "i like",
        "i dislike",
        "i live",
        "my job",
        "work",
        "my birthday",
    )

    fun shouldInspect(message: String): Boolean {
        val normalized = message.trim()
        return normalized.length in 8..400 &&
            candidateHints.any(normalized.lowercase()::contains)
    }

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

    fun fromProvider(body: String): String? = body.trim().takeUnless {
        it.isBlank() || it.equals("none", ignoreCase = true) || it == "无"
    }?.takeIf { it.length <= 240 }
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

private fun Cursor.npcProfile() = NpcProfile(
    id = getLong(0),
    name = getString(1),
    bio = getString(2),
    active = getInt(3) == 1,
    createdAt = getLong(4),
    lastSeenAt = getLong(5),
)
