package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32

internal data class ImportedCharacterCard(
    val name: String,
    val persona: String,
    val firstMessage: String,
    val rawJson: String,
    val lore: List<String>,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val relationship: String = "",
    val handle: String = "",
    val avatarBytes: ByteArray? = null,
    val visualStyle: String = "",
    val gender: String = "",
    val appearancePrompt: String = "",
    val clothing: String = "",
    val negativePrompt: String = "",
)

internal data class CharacterProfileFields(
    val handle: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val relationship: String = "",
    val avatarPath: String = "",
    val visualStyle: String = "",
    val gender: String = "",
    val appearancePrompt: String = "",
    val clothing: String = "",
    val negativePrompt: String = "",
)

internal data class ExtractedAppearance(
    val appearancePrompt: String = "",
    val clothing: String = "",
    val visualStyle: String = "",
    val gender: String = "",
    val negativePrompt: String = "",
    val hair: String = "",
    val eyes: String = "",
    val build: String = "",
)

internal object CharacterCardV2 {
    private val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    private val xmlLead = Regex("""^\s*<\?xml|^\s*<[A-Za-z_]""", RegexOption.IGNORE_CASE)

    fun read(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= 16 * 1024 * 1024) { "角色卡文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun parse(bytes: ByteArray): ImportedCharacterCard {
        require(bytes.size <= 16 * 1024 * 1024) { "角色卡文件过大" }
        if (bytes.startsWith(pngSignature)) {
            val json = extractJson(bytes)
            return parseText(json, avatarBytes = bytes)
        }
        val text = bytes.toString(StandardCharsets.UTF_8)
        if (looksLikeXml(text)) {
            return parseXml(text)
        }
        return parseText(text)
    }

    fun profileFields(character: ResidentCharacter): CharacterProfileFields {
        val root = runCatching { JSONObject(character.cardJson) }.getOrElse { JSONObject() }
        val data = root.optJSONObject("data") ?: JSONObject()
        val extensions = data.optJSONObject("extensions") ?: JSONObject()
        val visualIdentity = extensions.optJSONObject("visual_identity")
        val description = data.optString("description").trim()
        val personality = data.optString("personality").trim()
        val scenario = data.optString("scenario").trim()

        val style = extensions.optString("visual_style").trim()
            .ifBlank { visualIdentity?.optString("style")?.trim().orEmpty() }
        val gender = extensions.optString("gender").trim()
            .ifBlank { visualIdentity?.optString("gender")?.trim().orEmpty() }
        val appearance = extensions.optString("appearance_prompt").trim()
            .ifBlank { visualIdentity?.optString("prompt")?.trim().orEmpty() }
            .ifBlank { character.appearance.trim() }
        val clothing = extensions.optString("clothing").trim()
            .ifBlank { visualIdentity?.optString("clothing")?.trim().orEmpty() }
            .ifBlank { character.clothing.trim() }
        val negative = extensions.optString("negative_prompt").trim()
            .ifBlank { visualIdentity?.optString("negative_prompt")?.trim().orEmpty() }
            .ifBlank { character.negativePrompt.trim() }

        return CharacterProfileFields(
            handle = extensions.optString("handle").trim()
                .ifBlank { slugHandle(character.name) },
            description = description.ifBlank { character.persona },
            personality = personality,
            scenario = scenario,
            firstMessage = data.optString("first_mes").trim(),
            relationship = extensions.optString("relationship_to_user").trim(),
            avatarPath = extensions.optString("avatar_path").trim(),
            visualStyle = style,
            gender = gender,
            appearancePrompt = appearance,
            clothing = clothing,
            negativePrompt = negative,
        )
    }

    fun buildCardJson(
        name: String,
        fields: CharacterProfileFields,
        existingJson: String = "",
    ): String {
        val root = runCatching { JSONObject(existingJson) }.getOrElse { JSONObject() }
        root.put("spec", "chara_card_v2")
        root.put("spec_version", "2.0")
        val data = root.optJSONObject("data") ?: JSONObject().also { root.put("data", it) }
        data.put("name", name.trim())
        data.put("description", fields.description.trim())
        data.put("personality", fields.personality.trim())
        data.put("scenario", fields.scenario.trim())
        data.put("first_mes", fields.firstMessage.trim())
        listOf(
            "mes_example",
            "creator_notes",
            "system_prompt",
            "post_history_instructions",
            "creator",
            "character_version",
        ).forEach { if (!data.has(it)) data.put(it, "") }
        if (!data.has("alternate_greetings")) data.put("alternate_greetings", JSONArray())
        if (!data.has("tags")) data.put("tags", JSONArray())
        val extensions = data.optJSONObject("extensions") ?: JSONObject().also {
            data.put("extensions", it)
        }
        extensions.put("handle", normalizeHandle(fields.handle.ifBlank { name }))
        extensions.put("relationship_to_user", fields.relationship.trim())
        extensions.put("avatar_path", fields.avatarPath.trim())
        extensions.put("visual_style", fields.visualStyle.trim())
        extensions.put("gender", fields.gender.trim())
        extensions.put("appearance_prompt", fields.appearancePrompt.trim())
        extensions.put("clothing", fields.clothing.trim())
        extensions.put("negative_prompt", fields.negativePrompt.trim())

        // Preserve and sync visual_identity dictionary
        val visualIdentity = extensions.optJSONObject("visual_identity") ?: JSONObject().also {
            extensions.put("visual_identity", it)
        }
        visualIdentity.put("style", fields.visualStyle.trim())
        visualIdentity.put("gender", fields.gender.trim())
        visualIdentity.put("prompt", fields.appearancePrompt.trim())
        visualIdentity.put("clothing", fields.clothing.trim())
        visualIdentity.put("negative_prompt", fields.negativePrompt.trim())

        return root.toString(2)
    }

    fun extractAppearanceKeywords(description: String, personality: String = ""): ExtractedAppearance {
        val combined = "$description $personality"
        val hairMatches = mutableListOf<String>()
        val eyesMatches = mutableListOf<String>()
        val clothingMatches = mutableListOf<String>()
        val buildMatches = mutableListOf<String>()

        // English hair patterns
        Regex("(?i)\\b(?:long|short|curly|straight|wavy|messy|spiky|braided|bob|shoulder-length|ponytail|twintail|silver|black|blonde|blond|white|red|blue|green|purple|pink|brown|gray|grey|golden|dark|light)\\s+(?:(?:silver|black|blonde|blond|white|red|blue|green|purple|pink|brown|gray|grey|golden|dark|light)\\s+)?hair\\b")
            .findAll(combined)
            .forEach { hairMatches.add(it.value.trim()) }

        // Chinese hair patterns
        Regex("(?:黑发|白发|银发|金发|红发|蓝发|绿发|粉发|紫发|棕发|深色头发|浅色头发|长发|短发|卷发|直发|齐肩发|马尾|双马尾)")
            .findAll(combined)
            .forEach { hairMatches.add(it.value.trim()) }

        // English eyes patterns
        Regex("(?i)\\b(?:blue|green|brown|dark|hazel|amber|red|golden|violet|purple|black|gray|grey|crimson|ruby|emerald|sapphire)\\s+eyes?\\b")
            .findAll(combined)
            .forEach { eyesMatches.add(it.value.trim()) }

        // Chinese eyes patterns
        Regex("(?:蓝眸|蓝眼|绿瞳|绿眼|红瞳|红眼|黑瞳|黑眼|金瞳|金眼|紫瞳|琥珀色眼[睛眸]|清澈的[双眼眸子]+)")
            .findAll(combined)
            .forEach { eyesMatches.add(it.value.trim()) }

        // English clothing patterns
        Regex("(?i)\\b(?:(?:black|white|red|blue|green|dark|leather|trench|formal|casual|school|winter|summer)\\s+)?(?:coat|trench\\s+coat|jacket|suit|uniform|dress|hoodie|sweater|shirt|robe|cloak|armor|boots|jeans)\\b")
            .findAll(combined)
            .forEach { clothingMatches.add(it.value.trim()) }

        // Chinese clothing patterns
        Regex("(?:黑色风衣|白色连衣裙|制服|衬衫|西装|卫衣|长裙|大衣|风衣|夹克|毛衣|斗篷|长靴|铠甲)")
            .findAll(combined)
            .forEach { clothingMatches.add(it.value.trim()) }

        // English build / traits
        Regex("(?i)\\b(?:tall|slender|petite|athletic|muscular|slim|curvy|fit|lean|delicate)\\b")
            .findAll(combined)
            .forEach { buildMatches.add(it.value.trim()) }

        // Chinese build / traits
        Regex("(?:高挑|修长|挺拔|娇小|健美|纤细|清瘦|体格强健|优雅)")
            .findAll(combined)
            .forEach { buildMatches.add(it.value.trim()) }

        // Gender heuristic
        val gender = when {
            Regex("(?i)\\b(?:woman|girl|female|lady|sister|mother)\\b|少女|女生|女性|女人|姐姐|妹妹|女孩|女士").containsMatchIn(combined) -> "Woman"
            Regex("(?i)\\b(?:man|boy|male|gentleman|brother|father)\\b|少年|青年|男生|男性|男人|哥哥|弟弟|男孩|男士").containsMatchIn(combined) -> "Man"
            Regex("(?i)\\b(?:non-binary|enby)\\b|非二元").containsMatchIn(combined) -> "Non-binary"
            else -> "Unspecified"
        }

        // Style heuristic
        val visualStyle = when {
            Regex("(?i)\\b(?:anime|manga|cel-shading|2d)\\b|二次元|动漫|日漫").containsMatchIn(combined) -> "Anime"
            Regex("(?i)\\b(?:film|vintage|polaroid|analog|kodak|35mm)\\b|胶片|复古胶片").containsMatchIn(combined) -> "Film"
            Regex("(?i)\\b(?:painting|painted|oil painting|watercolor|illustration)\\b|油画|水彩|插画|绘画").containsMatchIn(combined) -> "Painted"
            Regex("(?i)\\b(?:photoreal|realistic|photo|portrait)\\b|写实|摄影|真人").containsMatchIn(combined) -> "Photoreal"
            else -> "Photoreal"
        }

        val hairStr = hairMatches.distinct().joinToString(", ")
        val eyesStr = eyesMatches.distinct().joinToString(", ")
        val buildStr = buildMatches.distinct().joinToString(", ")
        val clothingStr = clothingMatches.distinct().joinToString(", ")

        val traits = listOf(hairStr, eyesStr, buildStr).filter(String::isNotBlank).joinToString(", ")

        return ExtractedAppearance(
            appearancePrompt = traits,
            clothing = clothingStr,
            visualStyle = visualStyle,
            gender = gender,
            negativePrompt = "blurry, bad anatomy, deformed, low quality",
            hair = hairStr,
            eyes = eyesStr,
            build = buildStr,
        )
    }

    fun appearanceFromDescription(description: String): String {
        val extracted = extractAppearanceKeywords(description)
        return listOf(extracted.appearancePrompt, extracted.clothing)
            .filter(String::isNotBlank)
            .joinToString(", ")
    }

    fun composePersona(fields: CharacterProfileFields): String =
        listOf(fields.description, fields.personality, fields.scenario)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")

    fun export(character: ResidentCharacter): String {
        val fields = profileFields(character)
        return buildCardJson(
            name = character.name,
            fields = fields.copy(
                description = fields.description.ifBlank { character.persona },
                appearancePrompt = fields.appearancePrompt.ifBlank { character.appearance },
                clothing = fields.clothing.ifBlank { character.clothing },
                negativePrompt = fields.negativePrompt.ifBlank { character.negativePrompt },
            ),
            existingJson = character.cardJson,
        )
    }

    fun coverPng(name: String): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 768, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(102, 73, 94))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 190f
        }
        canvas.drawText(name.take(1).uppercase(), 256f, 450f, paint)
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
            it.toByteArray()
        }
    }

    fun embedJson(png: ByteArray, json: String): ByteArray {
        require(png.startsWith(pngSignature)) { "不是 PNG 文件" }
        val iend = findChunk(png, "IEND").first
        val payload = "chara\u0000".toByteArray() +
            Base64.getEncoder().encode(json.toByteArray())
        return ByteArrayOutputStream(png.size + payload.size + 12).use { output ->
            output.write(png, 0, iend)
            DataOutputStream(output).use { data ->
                data.writeInt(payload.size)
                data.writeBytes("tEXt")
                data.write(payload)
                val crc = CRC32().apply {
                    update("tEXt".toByteArray())
                    update(payload)
                }
                data.writeInt(crc.value.toInt())
                data.write(png, iend, png.size - iend)
            }
            output.toByteArray()
        }
    }

    fun slugHandle(name: String): String = normalizeHandle(name)

    fun normalizeHandle(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("@")
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}_]+"""), "_")
            .trim('_')
        return cleaned.ifBlank { "character" }.take(32)
    }

    private fun looksLikeXml(text: String): Boolean = xmlLead.containsMatchIn(text)

    private fun parseText(json: String, avatarBytes: ByteArray? = null): ImportedCharacterCard {
        val root = JSONObject(json)
        return when {
            root.optString("spec") == "chara_card_v2" -> parseV2(root, avatarBytes)
            root.has("data") && root.optJSONObject("data")?.has("name") == true ->
                parseV2(root.put("spec", "chara_card_v2").put("spec_version", "2.0"), avatarBytes)
            root.has("char_name") || root.has("name") -> parseLegacyJson(root, avatarBytes)
            else -> error("无法识别的角色卡格式")
        }
    }

    private fun parseV2(root: JSONObject, avatarBytes: ByteArray?): ImportedCharacterCard {
        require(root.optString("spec_version").ifBlank { "2.0" }.startsWith("2.")) {
            "不支持的角色卡版本"
        }
        val data = root.getJSONObject("data")
        val name = data.getString("name").trim()
        require(name.isNotEmpty()) { "角色卡缺少名字" }
        val description = data.optString("description").trim()
        val personality = data.optString("personality").trim()
        val scenario = data.optString("scenario").trim()
        val extensions = data.optJSONObject("extensions") ?: JSONObject()
        val visualIdentity = extensions.optJSONObject("visual_identity")

        val lore = data.optJSONObject("character_book")
            ?.optJSONArray("entries")
            ?.let { entries ->
                buildList {
                    for (index in 0 until entries.length()) {
                        val entry = entries.optJSONObject(index) ?: continue
                        if (!entry.optBoolean("enabled", true)) continue
                        entry.optString("content").trim().takeIf(String::isNotEmpty)?.let(::add)
                    }
                }
            }
            .orEmpty()

        val style = extensions.optString("visual_style").trim()
            .ifBlank { visualIdentity?.optString("style")?.trim().orEmpty() }
        val gender = extensions.optString("gender").trim()
            .ifBlank { visualIdentity?.optString("gender")?.trim().orEmpty() }
        val hair = visualIdentity?.optString("hair")?.trim().orEmpty()
        val eyes = visualIdentity?.optString("eyes")?.trim().orEmpty()
        val build = visualIdentity?.optString("build")?.trim().orEmpty()
        val prompt = extensions.optString("appearance_prompt").trim()
            .ifBlank { visualIdentity?.optString("prompt")?.trim().orEmpty() }
            .ifBlank { listOf(hair, eyes, build).filter(String::isNotBlank).joinToString(", ") }
        val clothing = extensions.optString("clothing").trim()
            .ifBlank { visualIdentity?.optString("clothing")?.trim().orEmpty() }
        val negative = extensions.optString("negative_prompt").trim()
            .ifBlank { visualIdentity?.optString("negative_prompt")?.trim().orEmpty() }

        val fields = CharacterProfileFields(
            handle = extensions.optString("handle").trim().ifBlank { slugHandle(name) },
            description = description,
            personality = personality,
            scenario = scenario,
            firstMessage = data.optString("first_mes").trim(),
            relationship = extensions.optString("relationship_to_user").trim(),
            visualStyle = style,
            gender = gender,
            appearancePrompt = prompt,
            clothing = clothing,
            negativePrompt = negative,
        )
        return ImportedCharacterCard(
            name = name,
            persona = composePersona(fields).ifBlank { name },
            firstMessage = fields.firstMessage,
            rawJson = buildCardJson(name, fields, root.toString()),
            lore = lore,
            description = description,
            personality = personality,
            scenario = scenario,
            relationship = fields.relationship,
            handle = fields.handle,
            avatarBytes = avatarBytes,
            visualStyle = fields.visualStyle,
            gender = fields.gender,
            appearancePrompt = fields.appearancePrompt,
            clothing = fields.clothing,
            negativePrompt = fields.negativePrompt,
        )
    }

    private fun parseLegacyJson(root: JSONObject, avatarBytes: ByteArray?): ImportedCharacterCard {
        val name = root.optString("char_name").ifBlank { root.optString("name") }.trim()
        require(name.isNotEmpty()) { "角色卡缺少名字" }
        val description = root.optString("description").ifBlank {
            root.optString("char_persona")
        }.trim()
        val personality = root.optString("personality").trim()
        val scenario = root.optString("scenario").ifBlank {
            root.optString("world_scenario")
        }.trim()
        val firstMessage = root.optString("first_mes").ifBlank {
            root.optString("char_greeting")
        }.trim()
        val fields = CharacterProfileFields(
            handle = slugHandle(name),
            description = description,
            personality = personality,
            scenario = scenario,
            firstMessage = firstMessage,
            relationship = root.optString("relationship").trim(),
        )
        return ImportedCharacterCard(
            name = name,
            persona = composePersona(fields).ifBlank { name },
            firstMessage = firstMessage,
            rawJson = buildCardJson(name, fields),
            lore = emptyList(),
            description = description,
            personality = personality,
            scenario = scenario,
            relationship = fields.relationship,
            handle = fields.handle,
            avatarBytes = avatarBytes,
        )
    }

    private fun parseXml(text: String): ImportedCharacterCard {
        val values = extractXmlTagValues(text)
        fun value(vararg keys: String): String =
            keys.asSequence()
                .mapNotNull { values[it.lowercase()]?.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()

        val name = value("name", "char_name", "character_name", "名称")
        require(name.isNotEmpty()) { "角色卡缺少名字" }
        val description = value("description", "char_persona", "persona", "描述")
        val personality = value("personality", "bio", "性格")
        val scenario = value("scenario", "world_scenario", "情景")
        val firstMessage = value("first_mes", "first_message", "char_greeting", "greeting", "第一条消息")
        val relationship = value("relationship", "relationship_to_you", "与你的关系")
        val handle = value("handle", "username", "user_name", "用户名").ifBlank { slugHandle(name) }
        val fields = CharacterProfileFields(
            handle = normalizeHandle(handle),
            description = description,
            personality = personality,
            scenario = scenario,
            firstMessage = firstMessage,
            relationship = relationship,
        )
        return ImportedCharacterCard(
            name = name,
            persona = composePersona(fields).ifBlank { name },
            firstMessage = firstMessage,
            rawJson = buildCardJson(name, fields),
            lore = value("mes_example", "example_dialogue", "lore")
                .takeIf(String::isNotEmpty)
                ?.let(::listOf)
                .orEmpty(),
            description = description,
            personality = personality,
            scenario = scenario,
            relationship = relationship,
            handle = fields.handle,
        )
    }

    /** Lightweight tag extractor so JVM unit tests do not need Android XmlPullParser. */
    private fun extractXmlTagValues(text: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        val tag = Regex(
            """<(?!/)([A-Za-z_][\w.-]*)(?:\s[^>]*)?>([\s\S]*?)</\1>""",
            RegexOption.IGNORE_CASE,
        )
        fun decode(raw: String): String = raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .trim()

        fun walk(fragment: String) {
            tag.findAll(fragment).forEach { match ->
                val name = match.groupValues[1].lowercase()
                val body = match.groupValues[2]
                if (name !in setOf("character", "charactercard", "card", "root", "data", "chara")) {
                    val nested = tag.containsMatchIn(body)
                    if (!nested) {
                        values.putIfAbsent(name, decode(body))
                    } else {
                        walk(body)
                    }
                } else {
                    walk(body)
                }
            }
        }
        walk(text)
        return values
    }

    private fun extractJson(png: ByteArray): String {
        var offset = 8
        while (offset + 12 <= png.size) {
            val length = readLength(png, offset)
            require(length <= png.size - offset - 12) { "PNG 数据损坏" }
            val type = png.copyOfRange(offset + 4, offset + 8).toString(StandardCharsets.US_ASCII)
            if (type == "tEXt") {
                val data = png.copyOfRange(offset + 8, offset + 8 + length)
                val separator = data.indexOf(0)
                if (
                    separator >= 0 &&
                    data.copyOfRange(0, separator).toString(StandardCharsets.ISO_8859_1) == "chara"
                ) {
                    return Base64.getDecoder()
                        .decode(data.copyOfRange(separator + 1, data.size))
                        .toString(StandardCharsets.UTF_8)
                }
            }
            offset += length + 12
        }
        error("PNG 中没有 Character Card V2 数据")
    }

    private fun findChunk(png: ByteArray, wanted: String): Pair<Int, Int> {
        var offset = 8
        while (offset + 12 <= png.size) {
            val length = readLength(png, offset)
            require(length <= png.size - offset - 12) { "PNG 数据损坏" }
            val type = png.copyOfRange(offset + 4, offset + 8).toString(StandardCharsets.US_ASCII)
            if (type == wanted) return offset to length
            offset += length + 12
        }
        error("PNG 缺少 $wanted")
    }

    private fun readLength(bytes: ByteArray, offset: Int): Int {
        val value = (0..3).fold(0L) { result, index ->
            (result shl 8) or (bytes[offset + index].toLong() and 0xff)
        }
        require(value <= Int.MAX_VALUE) { "PNG 区块过大" }
        return value.toInt()
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}

internal object AppearanceExtractor {
    fun parseLlmResponse(jsonText: String): ExtractedAppearance {
        val root = runCatching { JSONObject(jsonText) }.getOrElse {
            val start = jsonText.indexOf('{')
            val end = jsonText.lastIndexOf('}')
            if (start in 0 until end) {
                JSONObject(jsonText.substring(start, end + 1))
            } else {
                JSONObject()
            }
        }
        val hair = root.optString("hair").trim()
        val eyes = root.optString("eyes").trim()
        val build = root.optString("build").trim()
        val prompt = root.optString("appearance_prompt").trim().ifBlank {
            listOf(hair, eyes, build).filter(String::isNotBlank).joinToString(", ")
        }
        val clothing = root.optString("clothing").trim()
        val style = root.optString("visual_style").trim()
        val gender = root.optString("gender").trim()
        val negative = root.optString("negative_prompt").trim().ifBlank {
            "blurry, bad anatomy, deformed, low quality"
        }
        return ExtractedAppearance(
            appearancePrompt = prompt,
            clothing = clothing,
            visualStyle = style,
            gender = gender,
            negativePrompt = negative,
            hair = hair,
            eyes = eyes,
            build = build,
        )
    }

    fun extract(
        context: Context,
        description: String,
        personality: String,
        onResult: (ExtractedAppearance, Boolean) -> Unit,
    ) {
        val localFallback = CharacterCardV2.extractAppearanceKeywords(description, personality)
        val providerStore = runCatching { ProviderStore(context) }.getOrNull()
        val config = providerStore?.loadFor(ProviderTask.Chat)
        val defaults = providerStore?.loadDefaultGeneration() ?: GenerationSettings()
        if (config == null || !config.isValid()) {
            onResult(localFallback, false)
            return
        }
        val system = "You are a prompt extractor for AI character visual identity. Extract visual appearance keywords in JSON format."
        val prompt = """
            Extract visual identity for image generation from this character profile:
            Description: $description
            Personality: $personality

            Respond with a valid JSON object:
            {
              "hair": "e.g. short silver hair",
              "eyes": "e.g. blue eyes",
              "clothing": "e.g. black trench coat",
              "build": "e.g. tall and slender",
              "appearance_prompt": "combined hair, eyes, facial traits, build",
              "visual_style": "one of: Photoreal, Film, Anime, Painted",
              "gender": "one of: Woman, Man, Non-binary, Unspecified",
              "negative_prompt": "recommended negative prompts"
            }
        """.trimIndent()
        ProviderTextClient.complete(config, defaults, system, prompt) { result ->
            result.onSuccess { response ->
                val extracted = runCatching { parseLlmResponse(response.text) }.getOrNull()
                if (extracted != null && (extracted.appearancePrompt.isNotBlank() || extracted.clothing.isNotBlank())) {
                    onResult(extracted, true)
                } else {
                    onResult(localFallback, false)
                }
            }.onFailure {
                onResult(localFallback, false)
            }
        }
    }
}
