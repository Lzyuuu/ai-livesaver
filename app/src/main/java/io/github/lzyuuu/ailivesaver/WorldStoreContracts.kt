package io.github.lzyuuu.ailivesaver

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

internal data class PersistedMessengerGroup(val id: Long, val name: String, val prompt: String, val memberIds: List<Long>)
internal data class BinderDraft(val id: String, val step: Int, val payload: String, val updatedAt: Long)
internal data class BinderCandidate(val id: String, val draftId: String, val payload: String, val confirmed: Boolean)
internal data class CreativeAsset(val id: Long, val pathOrUri: String, val kind: String, val backend: String, val prompt: String, val characterId: Long?, val character: String, val sourceId: Long?, val targetId: Long?, val status: String, val error: String, val createdAt: Long)

internal data class PersistedAppInstall(
    val appId: String,
    val status: InstallStatus,
    val installedAt: Long?,
    val onHome: Boolean,
    val homeOrder: Int,
    val catalogVersion: String,
    val updatedAt: Long,
)

/** 写入或替换一个安装行。 */
internal fun WorldStore.saveAppInstall(install: PersistedAppInstall) {
    writableDatabase.insertWithOnConflict(
        "app_install",
        null,
        ContentValues().apply {
            put("app_id", install.appId)
            put("status", install.status.name)
            put("installed_at", install.installedAt)
            put("on_home", if (install.onHome) 1 else 0)
            put("home_order", install.homeOrder)
            put("catalog_version", install.catalogVersion)
            put("updated_at", install.updatedAt)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
    )
}

internal fun WorldStore.loadAppInstall(appId: String): PersistedAppInstall? =
    writableDatabase.query("app_install", null, "app_id=?", arrayOf(appId), null, null, null).use { c ->
        if (!c.moveToFirst()) null else PersistedAppInstall(
            appId = c.getString(c.getColumnIndexOrThrow("app_id")),
            status = InstallStatus.fromRaw(c.getString(c.getColumnIndexOrThrow("status"))),
            installedAt = if (c.isNull(c.getColumnIndexOrThrow("installed_at"))) null else c.getLong(c.getColumnIndexOrThrow("installed_at")),
            onHome = c.getInt(c.getColumnIndexOrThrow("on_home")) != 0,
            homeOrder = c.getInt(c.getColumnIndexOrThrow("home_order")),
            catalogVersion = c.getString(c.getColumnIndexOrThrow("catalog_version")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        )
    }

internal fun WorldStore.loadHomeApps(): List<PersistedAppInstall> =
    writableDatabase.query("app_install", null, "on_home=1", null, null, null, "home_order").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    PersistedAppInstall(
                        appId = c.getString(c.getColumnIndexOrThrow("app_id")),
                        status = InstallStatus.fromRaw(c.getString(c.getColumnIndexOrThrow("status"))),
                        installedAt = if (c.isNull(c.getColumnIndexOrThrow("installed_at"))) null else c.getLong(c.getColumnIndexOrThrow("installed_at")),
                        onHome = true,
                        homeOrder = c.getInt(c.getColumnIndexOrThrow("home_order")),
                        catalogVersion = c.getString(c.getColumnIndexOrThrow("catalog_version")),
                        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                    ),
                )
            }
        }
    }

internal fun WorldStore.deleteAppInstall(appId: String): Boolean =
    writableDatabase.delete("app_install", "app_id=?", arrayOf(appId)) > 0

internal fun WorldStore.countAppInstalls(): Int =
    writableDatabase.query("app_install", arrayOf("app_id"), null, null, null, null, null).use { it.count }

/** Creates or replaces a group and its complete member set atomically. */
internal fun WorldStore.savePersistedMessengerGroup(group: PersistedMessengerGroup): Long {
    val database = writableDatabase
    database.beginTransaction()
    return try {
        val values = ContentValues().apply { put("name", group.name); put("prompt", group.prompt) }
        val id = if (group.id == 0L) database.insertOrThrow("messenger_groups", null, values) else {
            check(database.update("messenger_groups", values, "id=?", arrayOf(group.id.toString())) == 1)
            group.id
        }
        database.delete("messenger_group_members", "group_id=?", arrayOf(id.toString()))
        group.memberIds.distinct().forEach { memberId ->
            database.insertOrThrow("messenger_group_members", null, ContentValues().apply {
                put("group_id", id)
                put("character_id", memberId)
            })
        }
        database.setTransactionSuccessful()
        id
    } finally {
        database.endTransaction()
    }
}

internal fun WorldStore.loadPersistedMessengerGroup(id: Long): PersistedMessengerGroup? {
    writableDatabase.query("messenger_groups", arrayOf("id", "name", "prompt"), "id=?", arrayOf(id.toString()), null, null, null).use { cursor ->
        if (!cursor.moveToFirst()) return null
        val members = writableDatabase.query("messenger_group_members", arrayOf("character_id"), "group_id=?", arrayOf(id.toString()), null, null, "character_id").use { memberCursor ->
            buildList { while (memberCursor.moveToNext()) add(memberCursor.getLong(0)) }
        }
        return PersistedMessengerGroup(cursor.getLong(0), cursor.getString(1), cursor.getString(2), members)
    }
}

internal fun WorldStore.putBinderDraft(draft: BinderDraft) {
    writableDatabase.insertWithOnConflict("binder_drafts", null, ContentValues().apply {
        put("id", draft.id); put("step", draft.step); put("payload", draft.payload); put("updated_at", draft.updatedAt)
    }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
}
internal fun WorldStore.getBinderDraft(id: String): BinderDraft? = writableDatabase.query("binder_drafts", null, "id=?", arrayOf(id), null, null, null).use { c ->
    if (!c.moveToFirst()) null else BinderDraft(c.getString(c.getColumnIndexOrThrow("id")), c.getInt(c.getColumnIndexOrThrow("step")), c.getString(c.getColumnIndexOrThrow("payload")), c.getLong(c.getColumnIndexOrThrow("updated_at")))
}
internal fun WorldStore.saveBinderCandidate(candidate: BinderCandidate) {
    val result = writableDatabase.insertWithOnConflict(
        "binder_candidates",
        null,
        ContentValues().apply {
            put("id", candidate.id)
            put("draft_id", candidate.draftId)
            put("payload", candidate.payload)
            put("confirmed", if (candidate.confirmed) 1 else 0)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
    )
    check(result != -1L)
}
internal fun WorldStore.confirmBinderCandidate(id: String) {
    val database = writableDatabase
    database.beginTransaction()
    try {
        val draftId = database.query("binder_candidates", arrayOf("draft_id"), "id=?", arrayOf(id), null, null, null).use { cursor ->
            check(cursor.moveToFirst()) { "Unknown binder candidate: $id" }
            cursor.getString(0)
        }
        database.update("binder_candidates", ContentValues().apply { put("confirmed", 0) }, "draft_id=?", arrayOf(draftId))
        check(database.update("binder_candidates", ContentValues().apply { put("confirmed", 1) }, "id=?", arrayOf(id)) == 1)
        database.setTransactionSuccessful()
    } finally {
        database.endTransaction()
    }
}
internal fun WorldStore.confirmBinderCandidateIdempotently(id: String, draftId: String, candidate: BinderCandidatePayload): Long =
    synchronized(writableDatabase) {
        val existing = getConfirmedBinderCandidate(draftId)
        if (existing != null) {
            return@synchronized characters(includeDeparted = true).firstOrNull { character ->
                runCatching {
                    JSONObject(character.cardJson)
                        .getJSONObject("data")
                        .getJSONObject("extensions")
                        .optString("binder_candidate_id") == existing.id
                }.getOrDefault(false)
            }?.id ?: 0L
        }
        saveBinderCandidate(BinderCandidate(id, draftId, candidate.toJson(), false))
        val baseCard = CharacterCardV2.buildCardJson(
            candidate.name,
            CharacterProfileFields(
                description = candidate.persona,
                relationship = candidate.relationship,
            ),
        )
        val card = JSONObject(baseCard).apply {
            getJSONObject("data")
                .getJSONObject("extensions")
                .put("binder_candidate_id", id)
        }.toString(2)
        val characterId = addCharacter(candidate.name, candidate.persona, "resident", "", "", "", card)
        confirmBinderCandidate(id)
        characterId
    }

internal fun WorldStore.getConfirmedBinderCandidate(draftId: String): BinderCandidate? = writableDatabase.query("binder_candidates", null, "draft_id=? AND confirmed=1", arrayOf(draftId), null, null, "id").use { c -> if (!c.moveToFirst()) null else BinderCandidate(c.getString(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("draft_id")), c.getString(c.getColumnIndexOrThrow("payload")), true) }

internal fun WorldStore.saveCreativeAsset(asset: CreativeAsset): Long {
    val database = writableDatabase
    val values = ContentValues().apply {
        put("path_uri", asset.pathOrUri)
        put("kind", asset.kind)
        put("backend", asset.backend)
        put("prompt", asset.prompt)
        put("character_id", asset.characterId)
        put("character", asset.character)
        put("source_id", asset.sourceId)
        put("target_id", asset.targetId)
        put("status", asset.status)
        put("error", asset.error)
        put("created_at", asset.createdAt)
    }
    val inserted = database.insertWithOnConflict("creative_assets", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    if (inserted != -1L) return inserted
    return database.query("creative_assets", arrayOf("id"), "path_uri=?", arrayOf(asset.pathOrUri), null, null, null).use { cursor ->
        check(cursor.moveToFirst()) { "Creative asset insert failed: ${asset.pathOrUri}" }
        cursor.getLong(0)
    }
}
internal fun WorldStore.getCreativeAsset(id: Long): CreativeAsset? = queryCreative("id=?", arrayOf(id.toString())).firstOrNull()
internal fun WorldStore.updateCreativeAssetStatus(id: Long, status: String, error: String = "") { check(writableDatabase.update("creative_assets", ContentValues().apply { put("status", status); put("error", error) }, "id=?", arrayOf(id.toString())) == 1) }
internal fun WorldStore.deleteCreativeAsset(id: Long): Boolean =
    writableDatabase.delete("creative_assets", "id=?", arrayOf(id.toString())) == 1
internal fun WorldStore.queryCreativeAssets(status: String? = null, characterId: Long? = null): List<CreativeAsset> {
    val clauses = mutableListOf<String>()
    val args = mutableListOf<String>()
    if (status != null) { clauses += "status=?"; args += status }
    if (characterId != null) { clauses += "character_id=?"; args += characterId.toString() }
    return queryCreative(clauses.joinToString(" AND ").ifEmpty { null }, args.toTypedArray())
}

private fun WorldStore.queryCreative(selection: String?, args: Array<String>): List<CreativeAsset> = writableDatabase.query("creative_assets", null, selection, args, null, null, "created_at DESC, id DESC").use { cursor ->
    buildList {
        while (cursor.moveToNext()) {
            fun text(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
            fun nullableLong(name: String): Long? {
                val index = cursor.getColumnIndexOrThrow(name)
                return if (cursor.isNull(index)) null else cursor.getLong(index)
            }
            add(CreativeAsset(cursor.getLong(cursor.getColumnIndexOrThrow("id")), text("path_uri"), text("kind"), text("backend"), text("prompt"), nullableLong("character_id"), text("character"), nullableLong("source_id"), nullableLong("target_id"), text("status"), text("error"), cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))))
        }
    }
}
