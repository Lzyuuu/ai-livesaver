package io.github.lzyuuu.ailivesaver

import android.content.Context
import org.json.JSONObject

/**
 * 记忆页「云端自动记忆」消费方（参考 V4.51 记忆页·写入）：
 * 每轮回复完成后，向当前云端聊天模型发一次额外结构化请求，提取值得长期记住的
 * 用户事实并写入 memories。失败静默，不阻塞聊天；关闭开关即完全跳过。
 * 「本地自动记忆」语义为复用已加载的本地聊天模型——本地推理链路（阶段②）落地后接入。
 */
internal const val MEMORY_AUTO_MAX_FACTS = 3

internal fun memoryAutoExtractPrompt(transcript: String): String =
    "从下面的对话提取 0-$MEMORY_AUTO_MAX_FACTS 条值得长期记住的事实（用户身份、偏好、约定、重要事件）。" +
        "只输出一个 JSON 对象：{\"facts\": [\"...\"]}。没有就输出 {\"facts\": []}。" +
        "每条一句话，写清主体，不要提问。\n\n对话：\n$transcript"

internal fun parseExtractedFacts(raw: String): List<String> = runCatching {
    val cleaned = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val payload = JSONObject(cleaned)
    val array = payload.optJSONArray("facts") ?: return@runCatching emptyList()
    (0 until array.length())
        .mapNotNull { array.optString(it) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}.getOrDefault(emptyList())

internal fun autoExtractMemories(
    context: Context,
    characterId: Long,
) {
    val prefs = context.getSharedPreferences(UI_PREFS, android.content.Context.MODE_PRIVATE)
    if (!prefs.getBoolean(PREF_MEMORY_CLOUD_AUTO, true)) return
    val config = ProviderStore(context).loadTask(ProviderTask.Chat) ?: return
    if (!config.supports(ProviderCapability.Structured)) return
    val (recent, latestUserMessageId) = WorldStore(context).use { store ->
        val active = store.messages(characterId)
            .filter { it.active && it.status == "complete" }
            .takeLast(8)
        active to (active.lastOrNull { it.sender == "user" }?.id)
    }
    if (latestUserMessageId == null) return
    val transcript = recent.joinToString("\n") { message ->
        val speaker = if (message.sender == "user") "用户" else "角色"
        "$speaker: ${message.body.take(200)}"
    }
    val generation = ProviderStore(context).loadDefaultGeneration()
    ProviderTextClient.completeStructured(
        config,
        generation,
        "你负责从聊天中提取应被角色长期记住的用户事实。只输出 JSON。",
        memoryAutoExtractPrompt(transcript),
    ) { result ->
        result.fold(
            onSuccess = { response ->
                val facts = parseExtractedFacts(response.text).take(MEMORY_AUTO_MAX_FACTS)
                if (facts.isNotEmpty()) {
                    WorldStore(context).use { store ->
                        facts.forEach { fact ->
                            runCatching { store.remember(characterId, latestUserMessageId, fact) }
                        }
                    }
                }
            },
            onFailure = { },
        )
    }
}
