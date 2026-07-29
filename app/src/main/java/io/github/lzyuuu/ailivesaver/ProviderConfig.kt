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

internal enum class ProviderTask(
    val key: String,
    val labelRes: Int,
    val prefix: String,
) {
    Chat("chat", R.string.general_provider, ""),
    World("world", R.string.world_provider, "world_"),
    Memory("memory", R.string.memory_provider, "memory_"),
    Vision("vision", R.string.vision_provider, "vision_"),
}

internal enum class ProviderCapability(
    val labelRes: Int,
) {
    Chat(R.string.capability_chat),
    Streaming(R.string.capability_streaming),
    Structured(R.string.capability_structured),
    Vision(R.string.capability_vision),
}

internal data class ProviderCapabilities(
    val supported: Set<ProviderCapability> = emptySet(),
    val checkedAt: Long = 0,
    val failures: Map<ProviderCapability, String> = emptyMap(),
    val manualOverride: Boolean = false,
) {
    fun supports(capability: ProviderCapability) = manualOverride || capability in supported

    fun withResults(results: List<CapabilityResult>) = ProviderCapabilities(
        supported = results.filter(CapabilityResult::passed).mapTo(mutableSetOf()) {
            it.capability
        },
        checkedAt = System.currentTimeMillis(),
        failures = results.filterNot(CapabilityResult::passed).associate {
            it.capability to it.detail
        },
        manualOverride = manualOverride,
    )
}

internal data class CapabilityResult(
    val capability: ProviderCapability,
    val passed: Boolean,
    val detail: String = "",
)

internal data class ProviderConfig(
    val preset: ProviderPreset = ProviderPreset.DeepSeek,
    val baseUrl: String = ProviderPreset.DeepSeek.defaultBaseUrl,
    val model: String = ProviderPreset.DeepSeek.defaultModel,
    val apiKey: String = "",
    val extraHeaders: String = "",
    val contextBudget: Int = DEFAULT_CONTEXT_BUDGET,
    val capabilities: ProviderCapabilities = ProviderCapabilities(),
    val fallback: ProviderConfig? = null,
) {
    fun isValid() = baseUrl.startsWith("https://") && model.isNotBlank() && apiKey.isNotBlank()

    fun supports(capability: ProviderCapability): Boolean =
        isValid() && (
            capabilities.supports(capability) ||
                fallback?.let { it.isValid() && it.capabilities.supports(capability) } == true
            )
}

internal data class ProviderResponse(
    val text: String,
    val config: ProviderConfig,
)

internal fun providerCandidates(config: ProviderConfig): List<ProviderConfig> =
    listOfNotNull(
        config.takeIf(ProviderConfig::isValid)?.copy(fallback = null),
        config.fallback?.takeIf(ProviderConfig::isValid)?.copy(fallback = null),
    ).distinctBy { "${it.baseUrl}\u0000${it.model}\u0000${it.apiKey}" }

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

    fun structuredRequest(
        model: String,
        system: String,
        prompt: String,
        disableThinking: Boolean = false,
    ): JSONObject = JSONObject()
        .put("model", model)
        .put("max_tokens", 240)
        .put("response_format", JSONObject().put("type", "json_object"))
        .apply {
            if (disableThinking) put("thinking", JSONObject().put("type", "disabled"))
        }
        .put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", prompt)),
        )

    fun parseStructuredBody(json: String): String {
        val content = parseReply(json)
            .removePrefix("```")
            .removePrefix("json")
            .removeSuffix("```")
            .trim()
        val payload = JSONObject(content)
        require(payload.length() == 1 && payload.has("body")) {
            "Structured reply must contain only body"
        }
        val value = payload.get("body")
        require(value is String) { "Structured reply body must be a string" }
        val body = value.trim()
        require(body.isNotEmpty()) { "Structured reply is missing body" }
        return body
    }

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

    fun visionRequest(
        model: String,
        dataUrl: String,
        disableThinking: Boolean = false,
    ): JSONObject = JSONObject()
        .put("model", model)
        .put("max_tokens", 180)
        .apply {
            if (disableThinking) put("thinking", JSONObject().put("type", "disabled"))
        }
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

    fun loadTask(task: ProviderTask): ProviderConfig? = when (task) {
        ProviderTask.Chat -> load()
        else -> if (preferences.getBoolean("${task.prefix}enabled", false)) {
            load(task.prefix)
        } else {
            null
        }
    }

    fun loadFor(task: ProviderTask): ProviderConfig {
        val primary = loadTask(task) ?: load()
        return primary.copy(fallback = loadFallback(task))
    }

    fun loadVision() = loadTask(ProviderTask.Vision)

    fun loadFallback(task: ProviderTask): ProviderConfig? {
        val prefix = fallbackPrefix(task)
        return if (preferences.getBoolean("${prefix}enabled", false)) load(prefix) else null
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
        contextBudget = preferences.getInt(
            "${prefix}context_budget",
            DEFAULT_CONTEXT_BUDGET,
        ).coerceIn(MIN_CONTEXT_BUDGET, MAX_CONTEXT_BUDGET),
        apiKey = preferences.getString("${prefix}api_key", null)?.let(::decrypt).orEmpty(),
        extraHeaders = preferences.getString("${prefix}extra_headers", null)
            ?.let(::decrypt)
            .orEmpty(),
        capabilities = decodeCapabilities(preferences.getString("${prefix}capabilities", null)),
    )

    fun save(config: ProviderConfig) = save(config, "")

    fun saveVision(config: ProviderConfig) {
        saveTask(ProviderTask.Vision, config)
    }

    fun saveTask(task: ProviderTask, config: ProviderConfig) {
        if (task == ProviderTask.Chat) {
            save(config)
        } else {
            preferences.edit { putBoolean("${task.prefix}enabled", true) }
            save(config, task.prefix)
        }
    }

    fun saveFallback(task: ProviderTask, config: ProviderConfig) {
        val prefix = fallbackPrefix(task)
        preferences.edit { putBoolean("${prefix}enabled", true) }
        save(config, prefix)
    }

    fun clearFallback(task: ProviderTask) {
        val prefix = fallbackPrefix(task)
        preferences.edit(commit = true) {
            remove("${prefix}enabled")
            remove("${prefix}preset")
            remove("${prefix}base_url")
            remove("${prefix}model")
            remove("${prefix}context_budget")
            remove("${prefix}api_key")
            remove("${prefix}extra_headers")
            remove("${prefix}capabilities")
        }
    }

    fun clearVision() {
        clearTask(ProviderTask.Vision)
    }

    fun clearTask(task: ProviderTask) {
        val prefix = task.prefix
        preferences.edit(commit = true) {
            if (task != ProviderTask.Chat) remove("${prefix}enabled")
            remove("${prefix}preset")
            remove("${prefix}base_url")
            remove("${prefix}model")
            remove("${prefix}context_budget")
            remove("${prefix}api_key")
            remove("${prefix}extra_headers")
            remove("${prefix}capabilities")
        }
    }

    fun clearAll() {
        preferences.edit(commit = true) { clear() }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun fallbackPrefix(task: ProviderTask) = "fallback_${task.key}_"

    private fun save(config: ProviderConfig, prefix: String) {
        preferences.edit {
            putString("${prefix}preset", config.preset.name)
            putString("${prefix}base_url", config.baseUrl)
            putString("${prefix}model", config.model)
            putInt(
                "${prefix}context_budget",
                config.contextBudget.coerceIn(MIN_CONTEXT_BUDGET, MAX_CONTEXT_BUDGET),
            )
            putString("${prefix}api_key", encrypt(config.apiKey))
            putString("${prefix}extra_headers", encrypt(config.extraHeaders))
            putString("${prefix}capabilities", encodeCapabilities(config.capabilities))
        }
    }

    private fun encodeCapabilities(capabilities: ProviderCapabilities): String = JSONObject()
        .put("supported", JSONArray(capabilities.supported.map(ProviderCapability::name)))
        .put("checked_at", capabilities.checkedAt)
        .put("manual_override", capabilities.manualOverride)
        .put(
            "failures",
            JSONObject().apply {
                capabilities.failures.forEach { (capability, detail) -> put(capability.name, detail) }
            },
        )
        .toString()

    private fun decodeCapabilities(raw: String?): ProviderCapabilities = runCatching {
        val json = JSONObject(raw.orEmpty())
        val supported = buildSet {
            val values = json.optJSONArray("supported") ?: JSONArray()
            for (index in 0 until values.length()) {
                runCatching {
                    add(ProviderCapability.valueOf(values.getString(index)))
                }
            }
        }
        val failuresJson = json.optJSONObject("failures") ?: JSONObject()
        val failures = buildMap {
            ProviderCapability.entries.forEach { capability ->
                failuresJson.optString(capability.name).takeIf(String::isNotBlank)?.let {
                    put(capability, it)
                }
            }
        }
        ProviderCapabilities(
            supported = supported,
            checkedAt = json.optLong("checked_at"),
            failures = failures,
            manualOverride = json.optBoolean("manual_override"),
        )
    }.getOrDefault(ProviderCapabilities())

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
                    .put("max_tokens", 16)
                    .apply {
                        if (config.preset == ProviderPreset.DeepSeek) {
                            put("thinking", JSONObject().put("type", "disabled"))
                        }
                    }
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

internal object ProviderCapabilityTester {
    fun test(config: ProviderConfig, callback: (Result<List<CapabilityResult>>) -> Unit) {
        Thread {
            val result = runCatching {
                ProviderCapability.entries.map { capability ->
                    runCatching {
                        when (capability) {
                            ProviderCapability.Chat -> {
                                ProviderProtocol.parseReply(
                                    ProviderHttp.post(config, capabilityRequest(config, false)),
                                ).ifBlank { throw IOException("empty reply") }
                            }
                            ProviderCapability.Streaming -> {
                                var deltas = 0
                                ProviderHttp.stream(config, capabilityRequest(config, true)) {
                                    deltas++
                                }
                                require(deltas > 0) { "endpoint did not return SSE deltas" }
                            }
                            ProviderCapability.Structured -> {
                                ProviderProtocol.parseStructuredBody(
                                    ProviderHttp.post(
                                        config,
                                        ProviderProtocol.structuredRequest(
                                            config.model,
                                            "Return JSON only with a single string field named body.",
                                            "Return body equal to OK.",
                                        ),
                                    ),
                                )
                            }
                            ProviderCapability.Vision -> {
                                ProviderProtocol.parseReply(
                                    ProviderHttp.post(
                                        config,
                                        ProviderProtocol.visionRequest(
                                            config.model,
                                            ONE_PIXEL_DATA_URL,
                                        ),
                                    ),
                                ).ifBlank { throw IOException("empty vision description") }
                            }
                        }
                        CapabilityResult(capability, passed = true)
                    }.getOrElse { failure ->
                        CapabilityResult(
                            capability,
                            passed = false,
                            detail = failure.message.orEmpty().take(160),
                        )
                    }
                }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun capabilityRequest(config: ProviderConfig, stream: Boolean) = JSONObject()
        .put("model", config.model)
        .put("messages", JSONArray().put(JSONObject().apply {
            put("role", "user")
            put("content", "Reply with OK.")
        }))
        .put("max_tokens", 16)
        .put("stream", stream)

    private const val ONE_PIXEL_DATA_URL =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
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
        relationship: RelationshipState,
        onDelta: (String) -> Unit,
        callback: (Result<ProviderResponse>) -> Unit,
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
                    relationship,
                )
                val requestMessages = JSONArray().put(
                    JSONObject().put("role", "system").put("content", system),
                )
                val recentBudget = (
                    config.contextBudget.coerceIn(MIN_CONTEXT_BUDGET, MAX_CONTEXT_BUDGET) -
                        estimatedTokenCount(system) -
                        RESPONSE_TOKEN_RESERVE
                    ).coerceAtLeast(MIN_RECENT_MESSAGE_BUDGET)
                recentMessagesForContext(messages, recap, recentBudget).forEach { message ->
                    requestMessages.put(
                        JSONObject()
                            .put("role", if (message.sender == "user") "user" else "assistant")
                            .put("content", message.body),
                    )
                }
                var lastFailure: Throwable? = null
                for (candidate in providerCandidates(config)) {
                    var delivered = false
                    try {
                        val body = JSONObject()
                            .put("model", candidate.model)
                            .put("messages", requestMessages)
                            .put("stream", true)
                        val text = ProviderHttp.stream(candidate, body) { accumulated ->
                            delivered = true
                            Handler(Looper.getMainLooper()).post { onDelta(accumulated) }
                        }.ifBlank { throw IOException("Provider returned an empty reply") }
                        return@runCatching ProviderResponse(text, candidate)
                    } catch (failure: Throwable) {
                        lastFailure = failure
                        if (delivered) throw failure
                    }
                }
                throw lastFailure ?: IOException("No valid Provider is configured")
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }
}

internal fun recentMessagesForContext(
    messages: List<ChatMessage>,
    recap: ConversationRecap?,
    tokenBudget: Int = DEFAULT_CONTEXT_BUDGET,
): List<ChatMessage> {
    val candidates = messages.filter {
        isCanonicalContextMessage(it) && (recap == null || it.id > recap.throughMessageId)
    }
    val selected = ArrayDeque<ChatMessage>()
    var remaining = tokenBudget.coerceAtLeast(1)
    for (message in candidates.asReversed()) {
        val cost = estimatedTokenCount(message.body) + MESSAGE_TOKEN_OVERHEAD
        if (cost > remaining) {
            if (selected.isEmpty()) {
                selected.addFirst(
                    message.copy(
                        body = truncateToEstimatedTokens(
                            message.body,
                            (remaining - MESSAGE_TOKEN_OVERHEAD).coerceAtLeast(1),
                        ),
                    ),
                )
            }
            break
        }
        selected.addFirst(message)
        remaining -= cost
    }
    return selected.toList()
}

internal fun estimatedTokenCount(text: String): Int {
    val units = text.sumOf { character -> if (character.code > 0x7f) 4 else 1 }
    return (units + 3) / 4
}

private fun truncateToEstimatedTokens(text: String, tokenBudget: Int): String {
    val maxUnits = tokenBudget.coerceAtLeast(1) * 4
    var units = 0
    return text.takeWhile { character ->
        val next = units + if (character.code > 0x7f) 4 else 1
        (next <= maxUnits).also { if (it) units = next }
    }
}

private fun isCanonicalContextMessage(message: ChatMessage) =
    message.active && message.status == "complete"

internal const val DEFAULT_CONTEXT_BUDGET = 16_384
internal const val MIN_CONTEXT_BUDGET = 2_048
internal const val MAX_CONTEXT_BUDGET = 1_000_000
private const val RESPONSE_TOKEN_RESERVE = 1_024
private const val MIN_RECENT_MESSAGE_BUDGET = 128
private const val MESSAGE_TOKEN_OVERHEAD = 4

internal fun buildChatSystemPrompt(
    character: ResidentCharacter,
    memories: List<LongTermMemory>,
    recap: ConversationRecap?,
    worldFacts: List<WorldFact> = emptyList(),
    cognition: List<CharacterCognition> = emptyList(),
    userContext: MemberWorldContext? = null,
    characterContext: MemberWorldContext? = null,
    relationship: RelationshipState? = null,
): String = buildString {
    append("You are ${character.name}. ")
    append(character.persona)
    append("\nStay in character. The user is the center of this relationship.")
    relationship?.takeIf { it.createdAt > 0 }?.let {
        append("\nCurrent relationship: ${it.label}. ${it.summary}")
        append("\nRelationship behavior: ${relationshipBehaviorGuidance(it)}")
    }
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

internal fun relationshipBehaviorGuidance(relationship: RelationshipState): String = when {
    relationship.tension >= 5 ->
        "Give the user space, avoid false intimacy, and leave room for repair."
    relationship.tension >= 2 ->
        "Acknowledge unresolved tension and respond carefully without pretending it vanished."
    relationship.trust >= 4 ->
        "Respond with established trust and warmth without becoming possessive."
    relationship.closeness >= 2 ->
        "Use familiar warmth while continuing to earn trust through the conversation."
    else ->
        "Keep the connection tentative and let closeness grow through concrete interaction."
}

internal object ProviderTextClient {
    fun complete(
        config: ProviderConfig,
        system: String,
        prompt: String,
        callback: (Result<ProviderResponse>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", prompt))
                var lastFailure: Throwable? = null
                for (candidate in providerCandidates(config)) {
                    try {
                        val body = JSONObject()
                            .put("model", candidate.model)
                            .put("messages", messages)
                            .put("max_tokens", 180)
                        val text = ProviderProtocol.parseReply(ProviderHttp.post(candidate, body))
                            .ifBlank { throw IOException("Provider returned an empty reply") }
                        return@runCatching ProviderResponse(text, candidate)
                    } catch (failure: Throwable) {
                        lastFailure = failure
                    }
                }
                throw lastFailure ?: IOException("No valid Provider is configured")
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    fun completeStructured(
        config: ProviderConfig,
        system: String,
        prompt: String,
        callback: (Result<ProviderResponse>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                var lastFailure: Throwable? = null
                for (candidate in providerCandidates(config)) {
                    try {
                        if (!candidate.capabilities.supports(ProviderCapability.Structured)) {
                            throw IOException("Provider structured JSON capability is not qualified")
                        }
                        val text = ProviderProtocol.parseStructuredBody(
                            ProviderHttp.post(
                                candidate,
                                ProviderProtocol.structuredRequest(
                                    candidate.model,
                                    "$system\nReturn a JSON object with only one string field named body.",
                                    "$prompt\nReturn only the JSON object; put the response text in body.",
                                ),
                            ),
                        )
                        return@runCatching ProviderResponse(text, candidate)
                    } catch (failure: Throwable) {
                        lastFailure = failure
                    }
                }
                throw lastFailure ?: IOException("No valid Provider is configured")
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }
}

internal object ProviderVisionClient {
    fun describe(
        config: ProviderConfig,
        imagePath: String,
        callback: (Result<ProviderResponse>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val dataUrl = minimizedImageDataUrl(imagePath)
                var lastFailure: Throwable? = null
                for (candidate in providerCandidates(config)) {
                    try {
                        if (!candidate.capabilities.supports(ProviderCapability.Vision)) {
                            throw IOException("Provider vision capability is not qualified")
                        }
                        val text = ProviderProtocol.parseReply(
                            ProviderHttp.post(
                                candidate,
                                ProviderProtocol.visionRequest(candidate.model, dataUrl),
                            ),
                        ).ifBlank { throw IOException("Vision Provider returned an empty description") }
                        return@runCatching ProviderResponse(text, candidate)
                    } catch (failure: Throwable) {
                        lastFailure = failure
                    }
                }
                throw lastFailure ?: IOException("No valid Provider is configured")
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

    private fun applyPresetTuning(config: ProviderConfig, body: JSONObject): JSONObject {
        // DeepSeek v4 是推理模型：不关闭 thinking 时推理段会耗尽短 max_tokens
        // （能力检测拿不到 content），长对话也要先静默推理很久才有可见输出。
        // 陪伴聊天优先响应速度，因此对 DeepSeek 预设统一关闭 thinking。
        if (config.preset == ProviderPreset.DeepSeek && !body.has("thinking")) {
            body.put("thinking", JSONObject().put("type", "disabled"))
        }
        return body
    }

    private fun postOnce(config: ProviderConfig, body: JSONObject): String {
        val connection = open(config)
        return try {
            write(connection, applyPresetTuning(config, body))
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
            write(connection, applyPresetTuning(config, body))
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
