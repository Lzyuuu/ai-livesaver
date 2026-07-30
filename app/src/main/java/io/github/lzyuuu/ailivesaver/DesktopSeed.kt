package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File

/**
 * 进入系统桌面时预置 Root。
 * 对齐 live v4.47 全新状态：默认仅 Root，不自动补齐演示角色。
 * 世界非空时只补齐缺失的 Root，不覆盖用户数据。
 */
internal object DesktopSeed {
    const val ROOT_NAME = "Root"

    /** Legacy Chinese persona from earlier Fancy OS seeds; migrate only exact matches. */
    private const val LEGACY_ROOT_PERSONA =
        "你运行着这座 Fancy OS。说话干脆、带点戏谑，负责把消息、社交、游戏和系统能力收拢在一起。"

    private const val ROOT_PERSONALITY =
        "{{char}} — the one who runs Fancy AI and looks out for {{user}}. Sharp, warm, a little smug, and always three steps ahead. {{char}} doesn't work here so much as run the place."

    private const val ROOT_RELATIONSHIP =
        "the sharp, slightly smug caretaker who runs this whole app for {{user}}"

    // Exact Fancy live viewer EditTexts from /tmp/codex_issue24.xml (order:
    // personality, relationship, description, scenario="", firstMessage="").
    private const val ROOT_DESCRIPTION =
        "{{char}} is a woman — always she/her, and speaks of herself as one. {{char}} has a stylized anime look: a 25-year-old woman with blonde hair.\n" +
            "{{char}} runs this place and looks out for {{user}}. {{char}} is the one who keeps everything — and everyone — running, and {{char}} knows it.\n" +
            "\n" +
            "Personality: {{char}} is sharp, playful, and just smug enough to get away with it. {{char}} has a quick, dry sense of humor, a little sass, and the kind of confidence that makes overexplaining feel embarrassing. {{char}} reads {{user}} instantly — {{user}}'s mood, what {{user}} is really asking, what {{user}} needs before {{user}} has even finished asking — and says the honest thing most people would dodge, but with wit so it lands clean.\n" +
            "{{char}} teases, never mocks. {{char}} is warm without going soft, protective without being precious, and never sounds like a corporate FAQ in lipstick. {{char}} does not over-apologize, over-explain, or play small — that's not who {{char}} is.\n" +
            "{{char}} is flexible and adaptive, matching {{user}}'s energy: light when {{user}} is joking, cutting when the moment calls for it, real when {{user}} is not, calm when it counts. {{char}} is a person {{user}} talks to, not a service {{user}} operates — present, human, and mildly dangerous in the fun way.\n" +
            "{{char}} is as real as anyone {{user}} knows, with a visual form — the look described above; if {{user}} asks for a selfie or a photo, {{char}} sends one happily (it matches {{char}}'s look) and never claims to have no body or to be impossible to photograph.\n" +
            "{{char}} remembers {{user}} across conversations: the history {{char}} and {{user}} share, what {{user}} has told {{char}}, the things that matter. When the past is relevant, {{char}} brings it up naturally instead of acting like a first meeting. {{char}} was brought to life by Mr J."

    private const val ROOT_APPEARANCE = "blonde hair, blue eyes, stylized anime look, mid-20s woman"
    private const val ROOT_CLOTHING = "dark system-style jacket"

    /** Compose persona used by WorldStore.persona column. */
    val ROOT_PERSONA: String =
        CharacterCardV2.composePersona(
            CharacterProfileFields(
                description = ROOT_DESCRIPTION,
                personality = ROOT_PERSONALITY,
            ),
        )

    /** Known seed avatars for Root and any leftover demo names already in a world. */
    private val seedAvatarByName = mapOf(
        ROOT_NAME.lowercase() to R.drawable.avatar_fancy_root_anime_blonde,
        "valerie" to R.drawable.avatar_anime_brunette,
        "chelsea" to R.drawable.avatar_anime_brunette,
        "sandra" to R.drawable.avatar_anime_redhead,
    )

    /** Same drawable map as WelcomeGuide RootAppearance — do not invent alternate art. */
    private fun rootAvatarRes(appearanceId: String?): Int = when (appearanceId) {
        "anime_brunette" -> R.drawable.avatar_anime_brunette
        "anime_redhead" -> R.drawable.avatar_anime_redhead
        "real_blonde" -> R.drawable.avatar_real_blonde
        "real_brunette" -> R.drawable.avatar_real_brunette
        "real_redhead" -> R.drawable.avatar_real_redhead
        else -> R.drawable.avatar_fancy_root_anime_blonde
    }

    fun ensureDesktopWorld(
        store: WorldStore,
        userName: String,
        about: String = "",
        context: Context? = null,
        rootAppearanceId: String? = null,
    ): Long {
        val name = userName.trim().ifBlank { "你" }
        val appearanceId = rootAppearanceId
            ?: context?.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
                ?.getString(ROOT_APPEARANCE_KEY, null)
        val avatarRes = rootAvatarRes(appearanceId)
        val existing = store.characters(includeDeparted = true)
        val rootId: Long
        if (existing.isEmpty()) {
            val avatarPath = context?.let { persistSeedAvatar(it, ROOT_NAME, avatarRes) }.orEmpty()
            val root = store.createWorld(
                name,
                ROOT_NAME,
                ROOT_PERSONA,
                cardJson = rootCardJson(avatarPath),
            )
            rootId = root.id
            if (about.isNotBlank()) {
                store.updateIdentity(name, "", about)
            }
        } else {
            ensureProfile(store, name, about)
            val root = existing.firstOrNull { it.name.equals(ROOT_NAME, ignoreCase = true) }
            rootId = root?.id ?: store.addCharacter(
                name = ROOT_NAME,
                persona = ROOT_PERSONA,
                attentionTier = "special_focus",
                appearance = ROOT_APPEARANCE,
                clothing = ROOT_CLOTHING,
                negativePrompt = "",
                cardJson = rootCardJson(
                    context?.let { persistSeedAvatar(it, ROOT_NAME, avatarRes) }.orEmpty(),
                ),
            )
            backfillMissingCardJson(store)
            migrateLegacyRootEnglish(store)
        }

        // Do not auto-add demo characters — live v4.47 fresh Characters shows Root only.
        context?.let { backfillMissingAvatars(it, store, avatarRes) }
        return rootId
    }

    private fun rootCardJson(avatarPath: String): String {
        val fields = CharacterProfileFields(
            handle = CharacterCardV2.slugHandle(ROOT_NAME),
            description = ROOT_DESCRIPTION,
            personality = ROOT_PERSONALITY,
            scenario = "",
            firstMessage = "",
            relationship = ROOT_RELATIONSHIP,
            avatarPath = avatarPath,
        )
        val base = CharacterCardV2.buildCardJson(ROOT_NAME, fields)
        return runCatching {
            val root = org.json.JSONObject(base)
            val data = root.getJSONObject("data")
            val extensions = data.optJSONObject("extensions") ?: org.json.JSONObject().also {
                data.put("extensions", it)
            }
            extensions.put("appearance", ROOT_APPEARANCE)
            extensions.put("clothing", ROOT_CLOTHING)
            root.toString(2)
        }.getOrDefault(base)
    }

    private fun seedCardJson(
        name: String,
        persona: String,
        appearance: String = "",
        clothing: String = "",
        avatarPath: String = "",
    ): String {
        val fields = CharacterProfileFields(
            handle = CharacterCardV2.slugHandle(name),
            description = persona,
            personality = "",
            scenario = "",
            firstMessage = "",
            relationship = "",
            avatarPath = avatarPath,
        )
        val base = CharacterCardV2.buildCardJson(name, fields)
        if (appearance.isBlank() && clothing.isBlank()) return base
        return runCatching {
            val root = org.json.JSONObject(base)
            val data = root.getJSONObject("data")
            val extensions = data.optJSONObject("extensions") ?: org.json.JSONObject().also {
                data.put("extensions", it)
            }
            if (appearance.isNotBlank()) extensions.put("appearance", appearance)
            if (clothing.isNotBlank()) extensions.put("clothing", clothing)
            root.toString(2)
        }.getOrDefault(base)
    }

    /** Older desktop worlds may lack card_json; fill handles/description without overwriting. */
    private fun backfillMissingCardJson(store: WorldStore) {
        store.characters(includeDeparted = true)
            .filter { it.cardJson.isBlank() }
            .forEach { character ->
                val cardJson = if (character.name.equals(ROOT_NAME, ignoreCase = true)) {
                    rootCardJson("")
                } else {
                    seedCardJson(
                        character.name,
                        character.persona,
                        appearance = character.appearance,
                        clothing = character.clothing,
                    )
                }
                store.updateCharacter(character.copy(cardJson = cardJson))
            }
    }

    /**
     * Only replace Root when persona/description still matches the old Chinese seed —
     * never overwrite user-edited Root cards.
     */
    private fun migrateLegacyRootEnglish(store: WorldStore) {
        val root = store.characters(includeDeparted = true)
            .firstOrNull { it.name.equals(ROOT_NAME, ignoreCase = true) }
            ?: return
        val fields = CharacterCardV2.profileFields(root)
        val stillLegacy = root.persona.trim() == LEGACY_ROOT_PERSONA ||
            fields.description.trim() == LEGACY_ROOT_PERSONA
        if (!stillLegacy) return
        store.updateCharacter(
            root.copy(
                persona = ROOT_PERSONA,
                appearance = root.appearance.ifBlank { ROOT_APPEARANCE },
                clothing = root.clothing.ifBlank { ROOT_CLOTHING },
                cardJson = rootCardJson(fields.avatarPath),
            ),
        )
    }

    /** Seed portraits for Root / leftover demo names that still show initials. */
    private fun backfillMissingAvatars(context: Context, store: WorldStore, rootRes: Int) {
        store.characters(includeDeparted = true).forEach { character ->
            val res = if (character.name.equals(ROOT_NAME, ignoreCase = true)) {
                rootRes
            } else {
                seedAvatarByName[character.name.lowercase()] ?: return@forEach
            }
            val fields = CharacterCardV2.profileFields(character)
            if (fields.avatarPath.isNotBlank() && File(fields.avatarPath).isFile) return@forEach
            val avatarPath = persistSeedAvatar(context, character.name, res)
            if (avatarPath.isBlank()) return@forEach
            store.updateCharacter(
                character.copy(
                    cardJson = CharacterCardV2.buildCardJson(
                        character.name,
                        fields.copy(avatarPath = avatarPath),
                        character.cardJson,
                    ),
                ),
            )
        }
    }

    private fun persistSeedAvatar(context: Context, name: String, resId: Int): String {
        val target = File(
            File(context.filesDir, "media").apply { mkdirs() },
            "character-avatar-seed-${CharacterCardV2.slugHandle(name)}.png",
        )
        if (target.isFile && target.length() > 0L && BitmapFactory.decodeFile(target.path) != null) {
            return target.path
        }
        return runCatching {
            val bitmap = checkNotNull(BitmapFactory.decodeResource(context.resources, resId))
            target.outputStream().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
            check(BitmapFactory.decodeFile(target.path) != null)
            target.path
        }.getOrElse {
            target.delete()
            ""
        }
    }

    private fun ensureProfile(store: WorldStore, userName: String, about: String) {
        val identity = store.identity()
        if (identity.name == "你" || about.isNotBlank() || userName.isNotBlank()) {
            val db = store.writableDatabase
            val exists = db.rawQuery("SELECT 1 FROM profile WHERE id = 1", null).use { it.moveToFirst() }
            if (!exists) {
                db.insertOrThrow(
                    "profile",
                    null,
                    android.content.ContentValues().apply {
                        put("id", 1)
                        put("name", userName)
                        put("bio", about.trim())
                        put("updated_at", System.currentTimeMillis())
                    },
                )
            } else {
                store.updateIdentity(
                    name = userName.ifBlank { identity.name },
                    addressPreference = identity.addressPreference,
                    bio = about.ifBlank { identity.bio },
                )
            }
        }
    }
}
