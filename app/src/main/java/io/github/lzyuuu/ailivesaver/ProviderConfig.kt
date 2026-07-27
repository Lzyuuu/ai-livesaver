package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal enum class ProviderPreset(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
) {
    DeepSeek("DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash"),
    OpenAI("OpenAI", "https://api.openai.com/v1", ""),
    OpenRouter("OpenRouter", "https://openrouter.ai/api/v1", "openrouter/auto"),
    Custom("自定义", "", ""),
}

internal data class ProviderConfig(
    val preset: ProviderPreset = ProviderPreset.DeepSeek,
    val baseUrl: String = ProviderPreset.DeepSeek.defaultBaseUrl,
    val model: String = ProviderPreset.DeepSeek.defaultModel,
    val apiKey: String = "",
    val extraHeaders: String = "",
) {
    fun isValid() = baseUrl.startsWith("https://") && model.isNotBlank() && apiKey.isNotBlank()
}

internal object ProviderProtocol {
    fun chatCompletionsUrl(baseUrl: String) =
        "${baseUrl.trim().trimEnd('/')}/chat/completions"

    fun parseHeaders(raw: String): Map<String, String> = raw.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null
            else line.substring(0, separator).trim()
                .takeIf { it.isNotEmpty() }
                ?.let { it to line.substring(separator + 1).trim() }
        }
        .toMap()

    fun parseReply(json: String): String = JSONObject(json)
        .getJSONArray("choices")
        .getJSONObject(0)
        .getJSONObject("message")
        .getString("content")
        .trim()

    fun parseStreamDelta(json: String): String {
        val choice = JSONObject(json).getJSONArray("choices").optJSONObject(0) ?: return ""
        val delta = choice.optJSONObject("delta") ?: return ""
        return delta.optString("content")
    }

    fun streamFinished(json: String): Boolean {
        val reason = JSONObject(json)
            .getJSONArray("choices")
            .optJSONObject(0)
            ?.opt("finish_reason")
        return reason != null && reason != JSONObject.NULL
    }

    fun visionRequest(model: String, dataUrl: String): JSONObject = JSONObject()
        .put("model", model)
        .put("max_tokens", 180)
        .put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("type", "text")
                                    .put(
                                        "text",
                                        "Describe only the visible content of this image in concise natural language. Do not infer unseen facts.",
                                    ),
                            )
                            .put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put(
                                        "image_url",
                                        JSONObject().put("url", dataUrl),
                                    ),
                            ),
                    ),
            ),
        )
}

internal class ProviderStore(context: Context) {
    private val preferences = context.getSharedPreferences("provider", Context.MODE_PRIVATE)

    fun load() = load("")

    fun loadVision() = if (preferences.getBoolean("vision_enabled", false)) {
        load("vision_")
    } else {
        null
    }

    private fun load(prefix: String) = ProviderConfig(
        preset = runCatching {
            ProviderPreset.valueOf(preferences.getString("${prefix}preset", null).orEmpty())
        }.getOrDefault(ProviderPreset.DeepSeek),
        baseUrl = preferences.getString(
            "${prefix}base_url",
            ProviderPreset.DeepSeek.defaultBaseUrl,
        ).orEmpty(),
        model = preferences.getString(
            "${prefix}model",
            ProviderPreset.DeepSeek.defaultModel,
        ).orEmpty(),
        apiKey = preferences.getString("${prefix}api_key", null)?.let(::decrypt).orEmpty(),
        extraHeaders = preferences.getString("${prefix}extra_headers", null)
            ?.let(::decrypt)
            .orEmpty(),
    )

    fun save(config: ProviderConfig) = save(config, "")

    fun saveVision(config: ProviderConfig) {
        preferences.edit { putBoolean("vision_enabled", true) }
        save(config, "vision_")
    }

    fun clearVision() {
        preferences.edit { putBoolean("vision_enabled", false) }
    }

    fun clearAll() {
        preferences.edit(commit = true) { clear() }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun save(config: ProviderConfig, prefix: String) {
        preferences.edit {
            putString("${prefix}preset", config.preset.name)
            putString("${prefix}base_url", config.baseUrl)
            putString("${prefix}model", config.model)
            putString("${prefix}api_key", encrypt(config.apiKey))
            putString("${prefix}extra_headers", encrypt(config.extraHeaders))
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return listOf(cipher.iv, cipher.doFinal(value.toByteArray()))
            .joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(value: String): String = runCatching {
        val (iv, ciphertext) = value.split(":", limit = 2).map {
            Base64.decode(it, Base64.NO_WRAP)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext))
    }.getOrDefault("")

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }
            .generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "ai_livesaver_provider"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

internal object ProviderConnectionTester {
    fun test(config: ProviderConfig, callback: (Result<Unit>) -> Unit) {
        Thread {
            val result = runCatching {
                val body = JSONObject()
                    .put("model", config.model)
                    .put("max_tokens", 1)
                    .put(
                        "messages",
                        JSONArray().put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", "Reply with OK."),
                        ),
                    )
                ProviderHttp.post(config, body)
                Unit
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }
}

internal object ProviderChatClient {
    fun stream(
        config: ProviderConfig,
        character: ResidentCharacter,
        messages: List<ChatMessage>,
        memories: List<LongTermMemory>,
        recap: ConversationRecap?,
        worldFacts: List<WorldFact>,
        cognition: List<CharacterCognition>,
        userContext: MemberWorldContext,
        characterContext: MemberWorldContext,
        onDelta: (String) -> Unit,
        callback: (Result<String>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val system = buildChatSystemPrompt(
                    character,
                    memories,
                    recap,
                    worldFacts,
                    cognition,
                    userContext,
                    characterContext,
                )
                val requestMessages = JSONArray().put(
                    JSONObject().put("role", "system").put("content", system),
                )
                recentMessagesForContext(messages, recap).forEach { message ->
                    requestMessages.put(
                        JSONObject()
                            .put("role", if (message.sender == "user") "user" else "assistant")
                            .put("content", message.body),
                    )
                }
                val body = JSONObject()
                    .put("model", config.model)
                    .put("messages", requestMessages)
                    .put("stream", true)
                ProviderHttp.stream(config, body) { accumulated ->
                    Handler(Looper.getMainLooper()).post { onDelta(accumulated) }
                }
                    .ifBlank { throw IOException("Provider returned an empty reply") }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }
}

internal fun recentMessagesForContext(
    messages: List<ChatMessage>,
    recap: ConversationRecap?,
): List<ChatMessage> = if (recap == null) {
    messages.filter(::isCanonicalContextMessage).takeLast(40)
} else {
    messages.filter { it.id > recap.throughMessageId && isCanonicalContextMessage(it) }
        .takeLast(20)
}

private fun isCanonicalContextMessage(message: ChatMessage) =
    message.active && message.status == "complete"

internal fun buildChatSystemPrompt(
    character: ResidentCharacter,
    memories: List<LongTermMemory>,
    recap: ConversationRecap?,
    worldFacts: List<WorldFact> = emptyList(),
    cognition: List<CharacterCognition> = emptyList(),
    userContext: MemberWorldContext? = null,
    characterContext: MemberWorldContext? = null,
): String = buildString {
    append("You are ${character.name}. ")
    append(character.persona)
    append("\nStay in character. The user is the center of this relationship.")
    recap?.let {
        append("\nConversation recap through message #${it.throughMessageId}:\n${it.body}")
    }
    if (worldFacts.isNotEmpty()) {
        append("\nShared world facts (pinned facts take priority):\n")
        worldFacts.take(20).forEach { append("- ${it.body}\n") }
    }
    if (cognition.isNotEmpty()) {
        append("\nWhat this character knows or believes; it may differ from shared facts:\n")
        cognition.take(20).forEach { append("- ${it.body}\n") }
    }
    userContext?.let {
        if (it.location.isNotBlank()) append("\nUser-disclosed location: ${it.location}")
        if (it.timeZone.isNotBlank()) append("\nUser-disclosed time zone: ${it.timeZone}")
    }
    characterContext?.let {
        if (it.location.isNotBlank()) append("\nCharacter location: ${it.location}")
        if (it.timeZone.isNotBlank()) append("\nCharacter time zone: ${it.timeZone}")
    }
    if (memories.isNotEmpty()) {
        append("\nLong-term memories:\n")
        memories.take(20).forEach { append("- ${it.body}\n") }
    }
}

internal object ProviderTextClient {
    fun complete(
        config: ProviderConfig,
        system: String,
        prompt: String,
        callback: (Result<String>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", prompt))
                val body = JSONObject()
                    .put("model", config.model)
                    .put("messages", messages)
                    .put("max_tokens", 180)
                ProviderProtocol.parseReply(ProviderHttp.post(config, body))
                    .ifBlank { throw IOException("Provider returned an empty reply") }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }
}

internal object ProviderVisionClient {
    fun describe(
        config: ProviderConfig,
        imagePath: String,
        callback: (Result<String>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val dataUrl = minimizedImageDataUrl(imagePath)
                ProviderProtocol.parseReply(
                    ProviderHttp.post(config, ProviderProtocol.visionRequest(config.model, dataUrl)),
                ).ifBlank { throw IOException("Vision Provider returned an empty description") }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun minimizedImageDataUrl(path: String): String {
        val file = File(path)
        if (!file.isFile || file.length() > 25L * 1024 * 1024) {
            throw IOException("Image is missing or too large")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Invalid image")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1536) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw IOException("Could not decode image")
        return try {
            val bytes = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, bytes)) {
                throw IOException("Could not prepare image")
            }
            "data:image/jpeg;base64," +
                Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }
}

private object ProviderHttp {
    fun post(config: ProviderConfig, body: JSONObject): String {
        var lastFailure: IOException? = null
        repeat(3) { attempt ->
            try {
                return postOnce(config, body)
            } catch (failure: IOException) {
                lastFailure = failure
                if (!isTransientProviderFailure(failure.message.orEmpty()) || attempt == 2) {
                    throw failure
                }
                Thread.sleep(500L shl attempt)
            }
        }
        throw lastFailure ?: IOException("Provider request failed")
    }

    fun stream(
        config: ProviderConfig,
        body: JSONObject,
        onDelta: (String) -> Unit,
    ): String {
        var lastFailure: IOException? = null
        repeat(3) { attempt ->
            var delivered = false
            try {
                return streamOnce(config, body) {
                    delivered = true
                    onDelta(it)
                }
            } catch (failure: IOException) {
                lastFailure = failure
                if (
                    delivered ||
                    !isTransientProviderFailure(failure.message.orEmpty()) ||
                    attempt == 2
                ) {
                    throw failure
                }
                Thread.sleep(500L shl attempt)
            }
        }
        throw lastFailure ?: IOException("Provider request failed")
    }

    private fun postOnce(config: ProviderConfig, body: JSONObject): String {
        val connection = open(config)
        return try {
            write(connection, body)
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun streamOnce(
        config: ProviderConfig,
        body: JSONObject,
        onDelta: (String) -> Unit,
    ): String {
        val connection = open(config)
        return try {
            connection.setRequestProperty("Accept", "text/event-stream")
            write(connection, body)
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            if (!connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)) {
                return ProviderProtocol.parseReply(
                    connection.inputStream.bufferedReader().use { it.readText() },
                )
            }
            val accumulated = StringBuilder()
            var complete = false
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") {
                        complete = true
                        return@forEach
                    }
                    if (runCatching { ProviderProtocol.streamFinished(data) }.getOrDefault(false)) {
                        complete = true
                    }
                    val delta = runCatching { ProviderProtocol.parseStreamDelta(data) }
                        .getOrDefault("")
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        onDelta(accumulated.toString())
                    }
                }
            }
            if (!complete) throw IOException("Provider stream ended before [DONE]")
            accumulated.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun open(config: ProviderConfig): HttpURLConnection =
        (URL(ProviderProtocol.chatCompletionsUrl(config.baseUrl))
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Content-Type", "application/json")
            ProviderProtocol.parseHeaders(config.extraHeaders).forEach {
                setRequestProperty(it.key, it.value)
            }
        }

    private fun write(connection: HttpURLConnection, body: JSONObject) {
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
    }
}

internal fun isTransientProviderFailure(message: String): Boolean {
    val code = Regex("""HTTP (\d{3})""").find(message)?.groupValues?.get(1)?.toIntOrNull()
    return code == null || code == 408 || code == 429 || code >= 500
}
