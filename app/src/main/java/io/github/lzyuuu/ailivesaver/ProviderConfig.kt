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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 可取消的 Provider 流式请求句柄。 */
internal class ProviderStreamHandle {
    private val cancelled = AtomicBoolean(false)
    private val connection = AtomicReference<HttpURLConnection?>(null)

    fun cancel() {
        cancelled.set(true)
        runCatching { connection.getAndSet(null)?.disconnect() }
    }

    fun isCancelled(): Boolean = cancelled.get()

    internal fun attach(conn: HttpURLConnection) {
        connection.set(conn)
        if (cancelled.get()) {
            runCatching { conn.disconnect() }
        }
    }

    internal fun detach() {
        connection.set(null)
    }
}

internal class ProviderStreamCancelledException : IOException("Generation stopped")

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

internal enum class GenerationPreset(
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val minP: Float,
    val xtcSurprise: Float,
    val repetitionPenalty: Float,
) {
    Precise(0.4f, 1024, 0.8f, 0.10f, 0.0f, 1.05f),
    Balanced(0.8f, 1024, 0.95f, 0.05f, 0.0f, 1.0f),
    Creative(1.05f, 1024, 0.95f, 0.02f, 0.5f, 1.03f),
    Custom(0.8f, 1024, 0.95f, 0.05f, 0.0f, 1.0f),
}

/** 专家采样：只存全局默认，不进推理配置覆盖，也不写入云端请求（ADR-0064）。 */
internal data class ExpertSampling(
    val minP: Float = 0.05f,
    val repetitionPenalty: Float = 1.0f,
    val penaltyWindow: Int = 64,
    val xtcSurprise: Float = 0.0f,
    val xtcFloor: Float = 0.10f,
    val dryLoopBreaker: Float = 0.0f,
    val drySteepness: Float = 1.75f,
    val dryAllowedRepeat: Int = 2,
    val dynamicTemperature: Float = 0.0f,
) {
    fun normalized() = copy(
        minP = minP.coerceIn(0f, 0.5f),
        repetitionPenalty = repetitionPenalty.coerceIn(1f, 1.5f),
        penaltyWindow = penaltyWindow.coerceIn(0, 1024),
        xtcSurprise = xtcSurprise.coerceIn(0f, 1f),
        xtcFloor = xtcFloor.coerceIn(0.05f, 0.5f),
        dryLoopBreaker = dryLoopBreaker.coerceIn(0f, 2f),
        drySteepness = drySteepness.coerceIn(1f, 4f),
        dryAllowedRepeat = dryAllowedRepeat.coerceIn(0, 10),
        dynamicTemperature = dynamicTemperature.coerceIn(0f, 1f),
    )
}

private const val OLD_FACTORY_BALANCED_TEMPERATURE = 0.7f
private const val OLD_FACTORY_BALANCED_MAX_TOKENS = 2048
private const val OLD_FACTORY_BALANCED_TOP_P = 0.95f

internal const val FACTORY_INSTRUCTION_ROLEPLAY_ID = "Roleplay"
internal const val FACTORY_INSTRUCTION_DIRECT_ID = "Direct"
internal const val FACTORY_INSTRUCTION_STRAIGHT_ID = "StraightAnswers"

/** 出厂正文抄自 FancyAI GitHub V4.51（jadx `zl4`/`yl4`），与参考指令页一致。 */
internal const val FACTORY_INSTRUCTION_ROLEPLAY_BODY =
    "Engage as {{char}}, be creative. Embrace the character's personality, emotion, mental state. Consider the character's history and mood."
internal const val FACTORY_INSTRUCTION_DIRECT_BODY =
    "You are {{char}}, talking with {{user}}. Be sharp, warm and direct. Keep your answers short and concise."
internal const val FACTORY_INSTRUCTION_STRAIGHT_BODY =
    "You are {{char}}, answer what {{user}} actually asked with one or two sentences at top."

internal const val FACTORY_IMAGE_PROMPT_TEMPLATE =
    "You are a Stable Diffusion prompt writer. Convert the supplied scene into one concrete,\n" +
        "single-line, comma-separated visual prompt and output only that prompt. Describe the subject and\n" +
        "action, {{appearance}}, outfit, location and background, pose and composition, camera shot and\n" +
        "camera angle, lens, lighting, and visual style, quality, and mood. Use visible details rather than\n" +
        "names or story commentary; never ask questions, explain the result, or output placeholders."

internal data class InstructionTemplateEntry(
    val id: String,
    val name: String,
    val body: String,
    val factory: Boolean = false,
)

internal fun factoryInstructionLibrary(): List<InstructionTemplateEntry> = listOf(
    InstructionTemplateEntry(
        FACTORY_INSTRUCTION_ROLEPLAY_ID,
        "Roleplay",
        FACTORY_INSTRUCTION_ROLEPLAY_BODY,
        factory = true,
    ),
    InstructionTemplateEntry(
        FACTORY_INSTRUCTION_DIRECT_ID,
        "Direct",
        FACTORY_INSTRUCTION_DIRECT_BODY,
        factory = true,
    ),
    InstructionTemplateEntry(
        FACTORY_INSTRUCTION_STRAIGHT_ID,
        "Straight answers",
        FACTORY_INSTRUCTION_STRAIGHT_BODY,
        factory = true,
    ),
)

internal data class GenerationSettings(
    val temperature: Float = GenerationPreset.Balanced.temperature,
    val maxTokens: Int = GenerationPreset.Balanced.maxTokens,
    val topP: Float = GenerationPreset.Balanced.topP,
    val preset: GenerationPreset = GenerationPreset.Balanced,
    val instructionTemplateId: String = FACTORY_INSTRUCTION_ROLEPLAY_ID,
    val instructionLibrary: List<InstructionTemplateEntry> = factoryInstructionLibrary(),
    val imagePromptTemplate: String = FACTORY_IMAGE_PROMPT_TEMPLATE,
    val expertSampling: ExpertSampling = ExpertSampling(),
) {
    fun normalized() = copy(
        temperature = temperature.coerceIn(0f, 2f),
        maxTokens = maxTokens.coerceIn(128, 8192),
        topP = topP.coerceIn(0f, 1f),
        expertSampling = expertSampling.normalized(),
    )

    fun selectedInstructionEntry(): InstructionTemplateEntry =
        resolveInstructionEntry(instructionLibrary, instructionTemplateId)

    fun selectedInstructionBody(): String = selectedInstructionEntry().body

    fun selectedInstructionName(): String = selectedInstructionEntry().name
}

/** 推理配置对全局生成参数的可空覆盖字段；空 = 跟随全局默认（ADR-0063）。覆盖只存指令模板 id，不存正文。 */
internal data class GenerationOverrides(
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val preset: GenerationPreset? = null,
    val instructionTemplateId: String? = null,
)

internal fun encodeGenerationSettings(settings: GenerationSettings): String {
    val library = JSONArray()
    settings.instructionLibrary.forEach { entry ->
        library.put(
            JSONObject()
                .put("id", entry.id)
                .put("name", entry.name)
                .put("body", entry.body)
                .put("factory", entry.factory),
        )
    }
    return JSONObject()
        .put("temperature", settings.temperature.toDouble())
        .put("max_tokens", settings.maxTokens)
        .put("top_p", settings.topP.toDouble())
        .put("preset", settings.preset.name)
        .put("instruction_template", settings.instructionTemplateId)
        .put("instruction_library", library)
        .put("image_prompt_template", settings.imagePromptTemplate)
        .put("expert_sampling", encodeExpertSampling(settings.expertSampling))
        .toString()
}

internal fun decodeGenerationSettings(raw: String?): GenerationSettings = runCatching {
    val json = JSONObject(raw.orEmpty())
    val temperature = json.getDouble("temperature").toFloat()
    val maxTokens = json.getInt("max_tokens")
    val topP = json.getDouble("top_p").toFloat()
    val uncustomizedOldFactory = isUncustomizedOldFactoryBalanced(temperature, maxTokens, topP)
    GenerationSettings(
        temperature = if (uncustomizedOldFactory) GenerationPreset.Balanced.temperature else temperature,
        maxTokens = if (uncustomizedOldFactory) GenerationPreset.Balanced.maxTokens else maxTokens,
        topP = topP,
        preset = enumValueOrNull<GenerationPreset>(json.optString("preset")) ?: GenerationPreset.Balanced,
        instructionTemplateId = json.optString("instruction_template").ifBlank { FACTORY_INSTRUCTION_ROLEPLAY_ID },
        instructionLibrary = decodeInstructionLibrary(json.optJSONArray("instruction_library")),
        imagePromptTemplate = if (json.has("image_prompt_template")) {
            json.optString("image_prompt_template")
        } else {
            FACTORY_IMAGE_PROMPT_TEMPLATE
        },
        expertSampling = decodeExpertSampling(json.optJSONObject("expert_sampling")),
    ).normalized()
}.getOrDefault(GenerationSettings())

private fun isUncustomizedOldFactoryBalanced(temperature: Float, maxTokens: Int, topP: Float): Boolean =
    maxTokens == OLD_FACTORY_BALANCED_MAX_TOKENS &&
        kotlin.math.abs(temperature - OLD_FACTORY_BALANCED_TEMPERATURE) < 0.0001f &&
        kotlin.math.abs(topP - OLD_FACTORY_BALANCED_TOP_P) < 0.0001f

private fun encodeExpertSampling(expert: ExpertSampling): JSONObject = JSONObject()
    .put("min_p", expert.minP.toDouble())
    .put("repetition_penalty", expert.repetitionPenalty.toDouble())
    .put("penalty_window", expert.penaltyWindow)
    .put("xtc_probability", expert.xtcSurprise.toDouble())
    .put("xtc_threshold", expert.xtcFloor.toDouble())
    .put("dry_multiplier", expert.dryLoopBreaker.toDouble())
    .put("dry_base", expert.drySteepness.toDouble())
    .put("dry_allowed_length", expert.dryAllowedRepeat)
    .put("dynatemp_range", expert.dynamicTemperature.toDouble())

private fun decodeExpertSampling(raw: JSONObject?): ExpertSampling {
    if (raw == null) return ExpertSampling()
    val defaults = ExpertSampling()
    return ExpertSampling(
        minP = raw.optDouble("min_p", defaults.minP.toDouble()).toFloat(),
        repetitionPenalty = raw.optDouble("repetition_penalty", defaults.repetitionPenalty.toDouble()).toFloat(),
        penaltyWindow = raw.optInt("penalty_window", defaults.penaltyWindow),
        xtcSurprise = raw.optDouble("xtc_probability", defaults.xtcSurprise.toDouble()).toFloat(),
        xtcFloor = raw.optDouble("xtc_threshold", defaults.xtcFloor.toDouble()).toFloat(),
        dryLoopBreaker = raw.optDouble("dry_multiplier", defaults.dryLoopBreaker.toDouble()).toFloat(),
        drySteepness = raw.optDouble("dry_base", defaults.drySteepness.toDouble()).toFloat(),
        dryAllowedRepeat = raw.optInt("dry_allowed_length", defaults.dryAllowedRepeat),
        dynamicTemperature = raw.optDouble("dynatemp_range", defaults.dynamicTemperature.toDouble()).toFloat(),
    )
}

private fun decodeInstructionLibrary(raw: JSONArray?): List<InstructionTemplateEntry> {
    if (raw == null || raw.length() == 0) return factoryInstructionLibrary()
    val parsed = buildList {
        for (index in 0 until raw.length()) {
            val item = raw.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) continue
            add(
                InstructionTemplateEntry(
                    id = id,
                    name = name,
                    body = item.optString("body"),
                    factory = item.optBoolean("factory"),
                ),
            )
        }
    }
    return parsed.ifEmpty { factoryInstructionLibrary() }
}

internal fun encodeGenerationOverrides(overrides: GenerationOverrides): String {
    val json = JSONObject()
    overrides.temperature?.let { json.put("temperature", it.toDouble()) }
    overrides.maxTokens?.let { json.put("max_tokens", it) }
    overrides.topP?.let { json.put("top_p", it.toDouble()) }
    overrides.preset?.let { json.put("preset", it.name) }
    overrides.instructionTemplateId?.let { json.put("instruction_template", it) }
    return json.toString()
}

internal fun decodeGenerationOverrides(raw: String?): GenerationOverrides = runCatching {
    val json = JSONObject(raw.orEmpty())
    GenerationOverrides(
        temperature = if (json.has("temperature")) json.getDouble("temperature").toFloat() else null,
        maxTokens = if (json.has("max_tokens")) json.getInt("max_tokens") else null,
        topP = if (json.has("top_p")) json.getDouble("top_p").toFloat() else null,
        preset = enumValueOrNull<GenerationPreset>(json.optString("preset")),
        instructionTemplateId = json.optString("instruction_template").takeIf {
            json.has("instruction_template") && it.isNotBlank()
        },
    )
}.getOrDefault(GenerationOverrides())

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    T::class.java.enumConstants.firstOrNull { it.name == name }

/** 有效生成参数：逐项覆盖优先，未覆盖项继承全局默认（ADR-0063）。库与图像提示词只随全局默认走。 */
internal fun effectiveGeneration(
    defaults: GenerationSettings,
    overrides: GenerationOverrides?,
): GenerationSettings = GenerationSettings(
    temperature = overrides?.temperature ?: defaults.temperature,
    maxTokens = overrides?.maxTokens ?: defaults.maxTokens,
    topP = overrides?.topP ?: defaults.topP,
    preset = overrides?.preset ?: defaults.preset,
    instructionTemplateId = overrides?.instructionTemplateId ?: defaults.instructionTemplateId,
    instructionLibrary = defaults.instructionLibrary,
    imagePromptTemplate = defaults.imagePromptTemplate,
    expertSampling = defaults.expertSampling,
).normalized()

internal fun resolveInstructionEntry(
    library: List<InstructionTemplateEntry>,
    id: String,
): InstructionTemplateEntry =
    library.firstOrNull { it.id == id }
        ?: library.firstOrNull { it.id == FACTORY_INSTRUCTION_ROLEPLAY_ID }
        ?: factoryInstructionLibrary().first()

internal fun resolveInstructionBody(
    library: List<InstructionTemplateEntry>,
    id: String,
): String = resolveInstructionEntry(library, id).body

internal fun applyInstructionTemplate(system: String, settings: GenerationSettings): String =
    "$system\n\n${settings.selectedInstructionBody()}"

internal fun instructionTokenHint(text: String): Int {
    if (text.isBlank()) return 0
    val byChars = kotlin.math.ceil(text.length / 3.0).toInt()
    if (text.none { it.code > 127 }) return byChars
    val byUtf8 = kotlin.math.ceil(text.toByteArray(Charsets.UTF_8).size / 2.0).toInt()
    return maxOf(byChars, byUtf8)
}

internal fun uniqueInstructionTemplateName(
    existingNames: Collection<String>,
    desired: String,
): String {
    val base = desired.trim().ifEmpty { "Untitled" }
    if (base !in existingNames) return base
    var index = 2
    while ("$base $index" in existingNames) index++
    return "$base $index"
}

internal fun saveInstructionTemplateAsNew(
    library: List<InstructionTemplateEntry>,
    body: String,
    desiredName: String,
    newId: String,
): Pair<List<InstructionTemplateEntry>, String> {
    val name = uniqueInstructionTemplateName(library.map(InstructionTemplateEntry::name), desiredName)
    val created = InstructionTemplateEntry(id = newId, name = name, body = body, factory = false)
    return library + created to created.id
}

internal fun deleteInstructionTemplate(
    library: List<InstructionTemplateEntry>,
    selectedId: String,
    deleteId: String,
): List<InstructionTemplateEntry> {
    if (deleteId == selectedId) return library
    return library.filterNot { it.id == deleteId }
}

internal fun restoreFactoryInstructionLibrary(settings: GenerationSettings): GenerationSettings =
    settings.copy(
        instructionTemplateId = FACTORY_INSTRUCTION_ROLEPLAY_ID,
        instructionLibrary = factoryInstructionLibrary(),
    )

internal fun resetGenerationSampling(settings: GenerationSettings): GenerationSettings =
    GenerationSettings(
        instructionTemplateId = settings.instructionTemplateId,
        instructionLibrary = settings.instructionLibrary,
        imagePromptTemplate = settings.imagePromptTemplate,
    )

internal fun applyNamedGenerationPreset(
    settings: GenerationSettings,
    preset: GenerationPreset,
): GenerationSettings {
    if (preset == GenerationPreset.Custom) return settings.copy(preset = GenerationPreset.Custom)
    return settings.copy(
        temperature = preset.temperature,
        maxTokens = preset.maxTokens,
        topP = preset.topP,
        preset = preset,
        expertSampling = settings.expertSampling.copy(
            minP = preset.minP,
            xtcSurprise = preset.xtcSurprise,
            repetitionPenalty = preset.repetitionPenalty,
        ),
    ).normalized()
}

internal data class ProviderConfig(
    val preset: ProviderPreset = ProviderPreset.DeepSeek,
    val baseUrl: String = ProviderPreset.DeepSeek.defaultBaseUrl,
    val model: String = ProviderPreset.DeepSeek.defaultModel,
    val apiKey: String = "",
    val extraHeaders: String = "",
    val contextBudget: Int = DEFAULT_CONTEXT_BUDGET,
    val generationOverride: GenerationOverrides = GenerationOverrides(),
    val capabilities: ProviderCapabilities = ProviderCapabilities(),
    val fallback: ProviderConfig? = null,
) {
    // Local loopback HTTP is intentionally accepted for on-device integration tests; remote providers remain TLS-only.
    fun isValid() = (baseUrl.startsWith("https://") ||
        (baseUrl.startsWith("http://127.0.0.1") || baseUrl.startsWith("http://localhost"))) &&
        model.isNotBlank() && apiKey.isNotBlank()

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

/** DeepSeek tuning also applies when the same endpoint is saved as a Custom Provider. */
internal fun ProviderConfig.shouldDisableThinking(): Boolean =
    preset == ProviderPreset.DeepSeek ||
        baseUrl.contains("api.deepseek.com", ignoreCase = true) ||
        model.startsWith("deepseek-", ignoreCase = true)

internal fun isRetryableStructuredFormatFailure(failure: Throwable): Boolean =
    failure is org.json.JSONException ||
        failure.message.orEmpty().contains("must contain only body", ignoreCase = true) ||
        failure.message.orEmpty().contains("body must be a string", ignoreCase = true)

internal object ProviderProtocol {
    fun chatRequest(model: String, prompt: String, settings: GenerationSettings, stream: Boolean = false): JSONObject {
        val s = settings.normalized()
        return JSONObject().put("model", model).put("temperature", s.temperature).put("max_tokens", s.maxTokens).put("top_p", s.topP).put("stream", stream)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", s.selectedInstructionBody())).put(JSONObject().put("role", "user").put("content", prompt)))
    }

    /** 流式聊天请求体：由调用方先用 effectiveGeneration 解析出有效参数再传入。 */
    fun streamingChatBody(
        model: String,
        messages: JSONArray,
        generation: GenerationSettings,
    ): JSONObject {
        val s = generation.normalized()
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", true)
            .put("temperature", s.temperature.toDouble())
            .put("max_tokens", s.maxTokens)
            .put("top_p", s.topP.toDouble())
    }

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
        settings: GenerationSettings,
    ): JSONObject {
        val s = settings.normalized()
        return JSONObject()
            .put("model", model)
            // Binder candidate lists can exceed the previous 240-token ceiling. A truncated
            // outer JSON object is unusable, so reserve enough room for bounded structured payloads.
            .put("max_tokens", 1_024)
            .put("temperature", s.temperature.toDouble())
            .put("top_p", s.topP.toDouble())
            .put("response_format", JSONObject().put("type", "json_object"))
            .apply {
                if (disableThinking) put("thinking", JSONObject().put("type", "disabled"))
            }
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", applyInstructionTemplate(system, s)),
                    )
                    .put(JSONObject().put("role", "user").put("content", prompt)),
            )
    }

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
        settings: GenerationSettings,
    ): JSONObject {
        val s = settings.normalized()
        return JSONObject()
            .put("model", model)
            // 视觉描述保持简短图说预算；采样参数与指令模板仍随全局默认/覆盖下发。
            .put("max_tokens", 180)
            .put("temperature", s.temperature.toDouble())
            .put("top_p", s.topP.toDouble())
            .apply {
                if (disableThinking) put("thinking", JSONObject().put("type", "disabled"))
            }
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", s.selectedInstructionBody()))
                    .put(
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
}

internal class ProviderStore(context: Context) {
    private val preferences = context.getSharedPreferences("provider", Context.MODE_PRIVATE)

    /** 全局生成参数默认值（ADR-0063：存设置层，推理配置未覆盖项继承）。 */
    fun loadDefaultGeneration(): GenerationSettings =
        decodeGenerationSettings(preferences.getString(GENERATION_DEFAULT_KEY, null))

    fun saveDefaultGeneration(settings: GenerationSettings) {
        preferences.edit {
            putString(GENERATION_DEFAULT_KEY, encodeGenerationSettings(settings.normalized()))
        }
    }

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
        generationOverride = decodeGenerationOverrides(
            preferences.getString("${prefix}generation_overrides", null),
        ),
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
            remove("${prefix}capabilities")
            remove("${prefix}generation_overrides")
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
            remove("${prefix}capabilities")
            remove("${prefix}generation_overrides")
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
            if (config.generationOverride != GenerationOverrides()) {
                putString(
                    "${prefix}generation_overrides",
                    encodeGenerationOverrides(config.generationOverride),
                )
            }
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
        const val GENERATION_DEFAULT_KEY = "generation_default"
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
                        if (config.shouldDisableThinking()) {
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
                                            disableThinking = config.shouldDisableThinking(),
                                            // 能力探测与用户生成参数解耦，保持探测载荷确定。
                                            settings = GenerationSettings(),
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
                                            disableThinking = config.shouldDisableThinking(),
                                            settings = GenerationSettings(),
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
        defaults: GenerationSettings,
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
        systemPromptAppendix: String = "",
        webResults: List<WebSearchResult> = emptyList(),
        historyWindowMessages: Int = Int.MAX_VALUE,
        imageDataUrlFor: (String) -> String? = { null },
        onDelta: (String) -> Unit,
        callback: (Result<ProviderResponse>) -> Unit,
        handle: ProviderStreamHandle = ProviderStreamHandle(),
    ): ProviderStreamHandle {
        Thread {
            val result = runCatching {
                if (handle.isCancelled()) throw ProviderStreamCancelledException()
                val system = buildChatSystemPrompt(
                    character,
                    memories,
                    recap,
                    worldFacts,
                    cognition,
                    userContext,
                    characterContext,
                    relationship,
                    webResults,
                ) + systemPromptAppendix.trim().takeIf { it.isNotBlank() }?.let { "\n\nGroup scene/system instructions:\n$it" }.orEmpty()
                val requestMessages = JSONArray().put(
                    JSONObject().put("role", "system").put("content", system),
                )
                val recentBudget = (
                    config.contextBudget.coerceIn(MIN_CONTEXT_BUDGET, MAX_CONTEXT_BUDGET) -
                        estimatedTokenCount(system) -
                        RESPONSE_TOKEN_RESERVE
                    ).coerceAtLeast(MIN_RECENT_MESSAGE_BUDGET)
                recentMessagesForContext(messages, recap, recentBudget)
                    .take(historyWindowMessages)
                    .forEach { message ->
                        // 图片附件（参考 V4.51 聊天附件）：user 图片消息转 OpenAI 视觉 content 数组。
                        val imagePath = if (message.sender == "user") imageMessagePath(message.body) else null
                        val imageDataUrl = imagePath?.let(imageDataUrlFor)
                        requestMessages.put(
                            JSONObject()
                                .put("role", if (message.sender == "user") "user" else "assistant")
                                .put(
                                    "content",
                                    if (imageDataUrl != null) {
                                        JSONArray()
                                            .put(
                                                JSONObject()
                                                    .put("type", "text")
                                                    .put("text", "（用户发送了一张图片，请结合图片内容回应）"),
                                            )
                                            .put(
                                                JSONObject()
                                                    .put("type", "image_url")
                                                    .put("image_url", JSONObject().put("url", imageDataUrl)),
                                            )
                                    } else {
                                        message.body
                                    },
                                ),
                        )
                    }
                var lastFailure: Throwable? = null
                for (candidate in providerCandidates(config)) {
                    if (handle.isCancelled()) throw ProviderStreamCancelledException()
                    var delivered = false
                    try {
                        val generation = effectiveGeneration(defaults, candidate.generationOverride)
                        requestMessages.getJSONObject(0).put(
                            "content",
                            applyInstructionTemplate(system, generation),
                        )
                        val body = ProviderProtocol.streamingChatBody(candidate.model, requestMessages, generation)
                        val text = ProviderHttp.stream(candidate, body, handle) { accumulated ->
                            delivered = true
                            Handler(Looper.getMainLooper()).post { onDelta(accumulated) }
                        }.ifBlank { throw IOException("Provider returned an empty reply") }
                        return@runCatching ProviderResponse(text, candidate)
                    } catch (failure: Throwable) {
                        if (failure is ProviderStreamCancelledException || handle.isCancelled()) {
                            throw ProviderStreamCancelledException()
                        }
                        lastFailure = failure
                        if (delivered) throw failure
                    }
                }
                throw lastFailure ?: IOException("No valid Provider is configured")
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
        return handle
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
    webResults: List<WebSearchResult> = emptyList(),
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
    append(webResultsForPrompt(webResults))
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
        defaults: GenerationSettings,
        system: String,
        prompt: String,
        callback: (Result<ProviderResponse>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                var lastFailure: Throwable? = null
                for (candidate in providerCandidates(config)) {
                    try {
                        val generation = effectiveGeneration(defaults, candidate.generationOverride)
                        val requestMessages = JSONArray()
                            .put(
                                JSONObject()
                                    .put("role", "system")
                                    .put("content", applyInstructionTemplate(system, generation)),
                            )
                            .put(JSONObject().put("role", "user").put("content", prompt))
                        val body = JSONObject()
                            .put("model", candidate.model)
                            .put("messages", requestMessages)
                            .put("temperature", generation.temperature.toDouble())
                            .put("max_tokens", generation.maxTokens)
                            .put("top_p", generation.topP.toDouble())
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
        defaults: GenerationSettings,
        system: String,
        prompt: String,
        callback: (Result<ProviderResponse>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                var lastFailure: Throwable? = null
                for (candidate in providerCandidates(config)) {
                    if (!candidate.capabilities.supports(ProviderCapability.Structured)) {
                        lastFailure = IOException("Provider structured JSON capability is not qualified")
                        continue
                    }
                    for (formatAttempt in 0..1) {
                        try {
                            val text = ProviderProtocol.parseStructuredBody(
                                ProviderHttp.post(
                                    candidate,
                                    ProviderProtocol.structuredRequest(
                                        candidate.model,
                                        "$system\nReturn a JSON object with only one string field named body.",
                                        "$prompt\nReturn only the JSON object; put the response text in body.",
                                        disableThinking = candidate.shouldDisableThinking(),
                                        settings = effectiveGeneration(defaults, candidate.generationOverride),
                                    ),
                                ),
                            )
                            return@runCatching ProviderResponse(text, candidate)
                        } catch (failure: Throwable) {
                            lastFailure = failure
                            // A successful HTTP response can still violate the strict structured
                            // envelope. Retry once with the same shipped prompt; transport retries
                            // remain owned by ProviderHttp and non-format failures are not duplicated.
                            if (!isRetryableStructuredFormatFailure(failure) || formatAttempt == 1) break
                        }
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
        defaults: GenerationSettings = GenerationSettings(),
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
                                ProviderProtocol.visionRequest(
                                    candidate.model,
                                    dataUrl,
                                    disableThinking = candidate.shouldDisableThinking(),
                                    settings = effectiveGeneration(defaults, candidate.generationOverride),
                                ),
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
        handle: ProviderStreamHandle = ProviderStreamHandle(),
        onDelta: (String) -> Unit,
    ): String {
        var lastFailure: IOException? = null
        repeat(3) { attempt ->
            if (handle.isCancelled()) throw ProviderStreamCancelledException()
            var delivered = false
            try {
                return streamOnce(config, body, handle) {
                    delivered = true
                    onDelta(it)
                }
            } catch (failure: IOException) {
                if (failure is ProviderStreamCancelledException || handle.isCancelled()) {
                    throw ProviderStreamCancelledException()
                }
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
        if (config.shouldDisableThinking() && !body.has("thinking")) {
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
        handle: ProviderStreamHandle,
        onDelta: (String) -> Unit,
    ): String {
        if (handle.isCancelled()) throw ProviderStreamCancelledException()
        val connection = open(config)
        handle.attach(connection)
        return try {
            connection.setRequestProperty("Accept", "text/event-stream")
            write(connection, applyPresetTuning(config, body))
            if (handle.isCancelled()) throw ProviderStreamCancelledException()
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            if (!connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)) {
                if (handle.isCancelled()) throw ProviderStreamCancelledException()
                return ProviderProtocol.parseReply(
                    connection.inputStream.bufferedReader().use { it.readText() },
                )
            }
            val accumulated = StringBuilder()
            var complete = false
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (handle.isCancelled()) throw ProviderStreamCancelledException()
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
            if (handle.isCancelled()) throw ProviderStreamCancelledException()
            if (!complete) throw IOException("Provider stream ended before [DONE]")
            accumulated.toString()
        } finally {
            handle.detach()
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
