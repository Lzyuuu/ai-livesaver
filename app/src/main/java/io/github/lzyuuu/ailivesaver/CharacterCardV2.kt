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
)

internal object CharacterCardV2 {
    private val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

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
        val json = if (bytes.startsWith(pngSignature)) extractJson(bytes) else {
            bytes.toString(StandardCharsets.UTF_8)
        }
        val root = JSONObject(json)
        require(root.optString("spec") == "chara_card_v2") { "仅支持 Character Card V2" }
        require(root.optString("spec_version").startsWith("2.")) { "不支持的角色卡版本" }
        val data = root.getJSONObject("data")
        val name = data.getString("name").trim()
        require(name.isNotEmpty()) { "角色卡缺少名字" }
        val description = data.optString("description").trim()
        val personality = data.optString("personality").trim()
        val scenario = data.optString("scenario").trim()
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
        return ImportedCharacterCard(
            name = name,
            persona = listOf(description, personality, scenario)
                .filter(String::isNotEmpty)
                .joinToString("\n\n")
                .ifBlank { name },
            firstMessage = data.optString("first_mes").trim(),
            rawJson = root.toString(),
            lore = lore,
        )
    }

    fun export(character: ResidentCharacter): String {
        val root = runCatching { JSONObject(character.cardJson) }.getOrElse { JSONObject() }
        root.put("spec", "chara_card_v2")
        root.put("spec_version", "2.0")
        val data = root.optJSONObject("data") ?: JSONObject().also { root.put("data", it) }
        data.put("name", character.name)
        data.put("description", character.persona)
        listOf(
            "personality",
            "scenario",
            "first_mes",
            "mes_example",
            "creator_notes",
            "system_prompt",
            "post_history_instructions",
            "creator",
            "character_version",
        ).forEach { if (!data.has(it)) data.put(it, "") }
        if (!data.has("alternate_greetings")) data.put("alternate_greetings", JSONArray())
        if (!data.has("tags")) data.put("tags", JSONArray())
        if (!data.has("extensions")) data.put("extensions", JSONObject())
        return root.toString(2)
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
