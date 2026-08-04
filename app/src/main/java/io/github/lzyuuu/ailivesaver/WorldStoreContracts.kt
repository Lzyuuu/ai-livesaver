package io.github.lzyuuu.ailivesaver

import android.content.ContentValues

internal data class MessengerGroup(val id: Long, val name: String, val prompt: String, val memberIds: List<Long>)
internal data class BinderDraft(val id: String, val step: Int, val payload: String, val updatedAt: Long)
internal data class CreativeAsset(val id: Long, val pathOrUri: String, val kind: String, val backend: String, val prompt: String, val character: String, val sourceId: Long?, val targetId: Long?, val status: String, val error: String, val createdAt: Long)

internal fun WorldStore.saveMessengerGroup(group: MessengerGroup): Long = writableDatabase.let { db ->
    val v = ContentValues().apply { put("name", group.name); put("prompt", group.prompt) }
    val id = db.insertWithOnConflict("messenger_groups", null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    db.delete("messenger_group_members", "group_id=?", arrayOf(id.toString()))
    group.memberIds.distinct().forEach { member -> db.insert("messenger_group_members", null, ContentValues().apply { put("group_id", id); put("character_id", member) }) }
    id
}
internal fun WorldStore.loadMessengerGroup(id: Long): MessengerGroup? = writableDatabase.query("messenger_groups", arrayOf("id","name","prompt"), "id=?", arrayOf(id.toString()), null,null,null).use { c -> if (!c.moveToFirst()) null else MessengerGroup(c.getLong(0),c.getString(1),c.getString(2), writableDatabase.query("messenger_group_members", arrayOf("character_id"), "group_id=?", arrayOf(id.toString()),null,null,null).use { m -> buildList { while(m.moveToNext()) add(m.getLong(0)) } }) }
internal fun WorldStore.putBinderDraft(draft: BinderDraft) { writableDatabase.insertWithOnConflict("binder_drafts", null, ContentValues().apply { put("id",draft.id); put("step",draft.step); put("payload",draft.payload); put("updated_at",draft.updatedAt) }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE) }
internal fun WorldStore.getBinderDraft(id: String): BinderDraft? = writableDatabase.query("binder_drafts",null,"id=?",arrayOf(id),null,null,null).use { c -> if(!c.moveToFirst()) null else BinderDraft(c.getString(c.getColumnIndexOrThrow("id")),c.getInt(c.getColumnIndexOrThrow("step")),c.getString(c.getColumnIndexOrThrow("payload")),c.getLong(c.getColumnIndexOrThrow("updated_at"))) }
internal fun WorldStore.saveCreativeAsset(a: CreativeAsset): Long = writableDatabase.insert("creative_assets",null,ContentValues().apply { put("path_uri",a.pathOrUri);put("kind",a.kind);put("backend",a.backend);put("prompt",a.prompt);put("character",a.character);put("source_id",a.sourceId);put("target_id",a.targetId);put("status",a.status);put("error",a.error);put("created_at",a.createdAt) })
internal fun WorldStore.queryCreativeAssets(): List<CreativeAsset> = writableDatabase.query("creative_assets",null,null,null,null,null,"created_at DESC").use { c -> buildList { while(c.moveToNext()) add(CreativeAsset(c.getLong(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("path_uri")),c.getString(c.getColumnIndexOrThrow("kind")),c.getString(c.getColumnIndexOrThrow("backend")),c.getString(c.getColumnIndexOrThrow("prompt")),c.getString(c.getColumnIndexOrThrow("character")),c.getLong(c.getColumnIndexOrThrow("source_id")).takeUnless { c.isNull(c.getColumnIndexOrThrow("source_id")) },c.getLong(c.getColumnIndexOrThrow("target_id")).takeUnless { c.isNull(c.getColumnIndexOrThrow("target_id")) },c.getString(c.getColumnIndexOrThrow("status")),c.getString(c.getColumnIndexOrThrow("error")),c.getLong(c.getColumnIndexOrThrow("created_at")))) } }
