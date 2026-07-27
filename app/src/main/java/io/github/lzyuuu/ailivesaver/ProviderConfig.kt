package io.github.lzyuuu.ailivesaver

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
    fun complete(
        config: ProviderConfig,
        character: ResidentCharacter,
        messages: List<ChatMessage>,
        memories: List<LongTermMemory>,
        recap: ConversationRecap?,
        callback: (Result<String>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val system = buildChatSystemPrompt(character, memories, recap)
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
                ProviderProtocol.parseReply(ProviderHttp.post(config, body))
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
    messages.takeLast(40)
} else {
    messages.filter { it.id > recap.throughMessageId }.takeLast(20)
}

internal fun buildChatSystemPrompt(
    character: ResidentCharacter,
    memories: List<LongTermMemory>,
    recap: ConversationRecap?,
): String = buildString {
    append("You are ${character.name}. ")
    append(character.persona)
    append("\nStay in character. The user is the center of this relationship.")
    recap?.let {
        append("\nConversation recap through message #${it.throughMessageId}:\n${it.body}")
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

private object ProviderHttp {
    fun post(config: ProviderConfig, body: JSONObject): String {
        val connection = URL(
            ProviderProtocol.chatCompletionsUrl(config.baseUrl),
        ).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        ProviderProtocol.parseHeaders(config.extraHeaders).forEach {
            connection.setRequestProperty(it.key, it.value)
        }
        return try {
            connection.outputStream.use {
                it.write(body.toString().toByteArray())
            }
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
