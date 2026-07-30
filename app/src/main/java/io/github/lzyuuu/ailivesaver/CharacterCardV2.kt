package io.github.lzyuuu.ailivesaver

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
)

internal data class CharacterProfileFields(
    val handle: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val relationship: String = "",
    val avatarPath: String = "",
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
        val description = data.optString("description").trim()
        val personality = data.optString("personality").trim()
        val scenario = data.optString("scenario").trim()
        return CharacterProfileFields(
            handle = extensions.optString("handle").trim()
                .ifBlank { slugHandle(character.name) },
            description = description.ifBlank { character.persona },
            personality = personality,
            scenario = scenario,
            firstMessage = data.optString("first_mes").trim(),
            relationship = extensions.optString("relationship_to_user").trim(),
            avatarPath = extensions.optString("avatar_path").trim(),
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
        return root.toString(2)
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
        val fields = CharacterProfileFields(
            handle = extensions.optString("handle").trim().ifBlank { slugHandle(name) },
            description = description,
            personality = personality,
            scenario = scenario,
            firstMessage = data.optString("first_mes").trim(),
            relationship = extensions.optString("relationship_to_user").trim(),
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
