package io.github.lzyuuu.ailivesaver

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 设置目的地。索引主列表一比一对齐参考 V4.51 的 10 行结构（settingsPrimaryOrder），
 * 我方增强能力（参考没有的页面）归入 settingsExtendedOrder 的「扩展」分组。
 * 其余历史目的地（PROVIDER/IDENTITY/CALLS 等）不再出现在索引，但保留路由与搜索，
 * 供页面内部跳转与旧别名解析。
 */
enum class SettingsDestination(val title: String, val synonyms: Set<String> = emptySet()) {
    GENERAL("General", setOf("general", "常规", "资料", "profile", "文本大小", "语言", "language")),
    MODELS_ENGINE("Models & Engine", setOf("models", "engine", "模型与引擎", "本地模型", "gguf", "litert", "llama")),
    CLOUD_LLM_IMAGE("Cloud LLM & Image", setOf("cloud", "provider", "providers", "供应商", "端点", "云 llm")),
    VOICE("Voice", setOf("voice", "calls", "语音", "通话")),
    INSTRUCTION("Instruction", setOf("instruction", "instructions", "指令", "指令模板", "模板", "roleplay", "说话")),
    GENERATION("Generation", setOf("generation", "temperature", "top_p", "sampling", "生成", "生成参数", "温度", "采样")),
    MEMORY("Memory", setOf("memory", "记忆", "召回", "历史窗口")),
    BACKUPS("Backups", setOf("backup", "backups", "备份", "恢复")),
    CLEANUP("Cleanup", setOf("cleanup", "清理", "缓存", "孤立行")),
    DEVELOPER("Developer", setOf("developer", "about", "诊断", "日志", "版本", "开发者")),
    APPEARANCE("Appearance", setOf("appearance", "外观", "theme", "主题")),
    WORLD("World", setOf("world", "世界")),
    KNOWLEDGE("Knowledge", setOf("knowledge", "知识", "世界设定与角色认知")),
    PRIVACY("Privacy", setOf("privacy", "隐私")),
    STORAGE("Storage", setOf("storage", "存储")),
    HELP("Help Guide", setOf("help", "guide", "帮助", "指南")),
    UPDATE("Update", setOf("update", "updates", "release", "更新")),
    // —— 历史目的地：不出现在索引，仅用于路由与搜索别名 ——
    PROVIDER("Provider", setOf("推理配置", "model")),
    CHAT_BRAIN("Chat brain", setOf("brain", "inference", "推理")),
    CALLS("Calls", setOf("call", "通话选项")),
    LOCAL_DREAM("Local Dream", setOf("image", "imaging", "dream", "local dream", "生图", "图像")),
    IMAGING("Imaging", setOf("creative", "图片")),
    IDENTITY("Identity", setOf("identity", "身份", "个人资料")),
    CHARACTERS("Characters", setOf("character", "角色")),
    APP("App", setOf("theme", "language", "应用")),
    DIAGNOSTICS("Diagnostics", setOf("diagnostic", "debug")),
    RUNTIME("Runtime", setOf("runtime", "运行时")),
    SYSTEM("System Settings", setOf("system", "系统设置")),
}

/** 索引主列表：一比一对齐参考 V4.51 设置索引的顺序与成员。 */
val settingsPrimaryOrder: List<SettingsDestination> = listOf(
    SettingsDestination.GENERAL,
    SettingsDestination.MODELS_ENGINE,
    SettingsDestination.CLOUD_LLM_IMAGE,
    SettingsDestination.VOICE,
    SettingsDestination.INSTRUCTION,
    SettingsDestination.GENERATION,
    SettingsDestination.MEMORY,
    SettingsDestination.BACKUPS,
    SettingsDestination.CLEANUP,
    SettingsDestination.DEVELOPER,
)

/** 扩展分组：我方增强能力，参考 V4.51 无对应行。 */
val settingsExtendedOrder: List<SettingsDestination> = listOf(
    SettingsDestination.APPEARANCE,
    SettingsDestination.WORLD,
    SettingsDestination.KNOWLEDGE,
    SettingsDestination.PRIVACY,
    SettingsDestination.STORAGE,
    SettingsDestination.HELP,
    SettingsDestination.UPDATE,
)

internal fun settingsDestinationTitleRes(destination: SettingsDestination): Int = when (destination) {
    SettingsDestination.GENERAL -> R.string.settings_general_title
    SettingsDestination.MODELS_ENGINE -> R.string.settings_models_engine_title
    SettingsDestination.CLOUD_LLM_IMAGE -> R.string.settings_cloud_llm_title
    SettingsDestination.VOICE -> R.string.voice_calls_settings
    SettingsDestination.INSTRUCTION -> R.string.instruction_settings
    SettingsDestination.GENERATION -> R.string.generation_settings
    SettingsDestination.MEMORY -> R.string.settings_memory_title
    SettingsDestination.BACKUPS -> R.string.backup_settings
    SettingsDestination.CLEANUP -> R.string.settings_cleanup_title
    SettingsDestination.DEVELOPER -> R.string.settings_developer_title
    SettingsDestination.APPEARANCE -> R.string.appearance_settings
    SettingsDestination.WORLD -> R.string.world_settings
    SettingsDestination.KNOWLEDGE -> R.string.world_knowledge
    SettingsDestination.PRIVACY -> R.string.privacy_settings
    SettingsDestination.STORAGE -> R.string.storage_settings
    SettingsDestination.HELP -> R.string.settings_category_help_guide
    SettingsDestination.UPDATE -> R.string.about_updates
    SettingsDestination.PROVIDER -> R.string.provider_settings
    SettingsDestination.CHAT_BRAIN -> R.string.settings_entry_chat_brain
    SettingsDestination.CALLS -> R.string.settings_entry_calls
    SettingsDestination.LOCAL_DREAM -> R.string.local_dream_settings
    SettingsDestination.IMAGING -> R.string.settings_entry_imaging
    SettingsDestination.IDENTITY -> R.string.user_identity
    SettingsDestination.CHARACTERS -> R.string.settings_entry_characters
    SettingsDestination.APP -> R.string.settings_entry_app
    SettingsDestination.DIAGNOSTICS -> R.string.diagnostics_title
    SettingsDestination.RUNTIME -> R.string.runtime_status
    SettingsDestination.SYSTEM -> R.string.settings_category_system_settings
}

internal fun settingsDestinationSummaryRes(destination: SettingsDestination): Int = when (destination) {
    SettingsDestination.GENERAL -> R.string.settings_general_summary
    SettingsDestination.MODELS_ENGINE -> R.string.settings_models_engine_summary
    SettingsDestination.VOICE -> R.string.voice_calls_settings_summary
    SettingsDestination.INSTRUCTION -> R.string.instruction_settings_summary
    SettingsDestination.GENERATION -> R.string.generation_settings_summary
    SettingsDestination.MEMORY -> R.string.settings_memory_summary
    SettingsDestination.BACKUPS -> R.string.backup_settings_summary
    SettingsDestination.CLEANUP -> R.string.settings_cleanup_summary
    SettingsDestination.DEVELOPER -> R.string.settings_developer_summary
    SettingsDestination.APPEARANCE -> R.string.appearance_settings_summary
    SettingsDestination.WORLD -> R.string.world_settings_summary
    SettingsDestination.KNOWLEDGE -> R.string.world_knowledge_summary
    SettingsDestination.PRIVACY -> R.string.privacy_settings_summary
    SettingsDestination.STORAGE -> R.string.storage_settings_summary
    SettingsDestination.HELP -> R.string.settings_entry_help_summary
    SettingsDestination.UPDATE -> R.string.about_updates_summary
    SettingsDestination.PROVIDER -> R.string.provider_settings_summary
    SettingsDestination.CHAT_BRAIN -> R.string.settings_entry_chat_brain_summary
    SettingsDestination.CALLS -> R.string.settings_entry_calls_summary
    SettingsDestination.CLOUD_LLM_IMAGE -> R.string.settings_cloud_llm_summary
    SettingsDestination.LOCAL_DREAM -> R.string.local_dream_settings_summary
    SettingsDestination.IMAGING -> R.string.settings_entry_imaging_summary
    SettingsDestination.IDENTITY -> R.string.user_identity_summary
    SettingsDestination.CHARACTERS -> R.string.settings_entry_characters_summary
    SettingsDestination.APP -> R.string.appearance_settings_summary
    SettingsDestination.DIAGNOSTICS -> R.string.diagnostics_summary
    SettingsDestination.RUNTIME -> R.string.runtime_status_summary
    SettingsDestination.SYSTEM -> R.string.storage_settings_summary
}

data class SettingsSearchResult(val destination: SettingsDestination, val score: Int)
private val destinationOrder = SettingsDestination.entries.toList()
fun settingsDestinationsInOrder(): List<SettingsDestination> = settingsPrimaryOrder + settingsExtendedOrder
fun resolveSettingsDestination(key: String): SettingsDestination? {
    val normalized = key.trim().replace('-', '_').replace('/', '_')
    return destinationOrder.firstOrNull { it.name.equals(normalized, true) }
        ?: destinationOrder.firstOrNull {
            it.synonyms.any { synonym -> synonym.equals(key.trim(), true) }
        }
}
fun searchSettings(query: String): List<SettingsSearchResult> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return settingsDestinationsInOrder().map { SettingsSearchResult(it, 0) }
    return destinationOrder.mapNotNull { destination ->
        val terms = listOf(destination.name, destination.title) + destination.synonyms.toList()
        val score = terms.maxOf { term -> when { term.lowercase() == q -> 100; term.lowercase().startsWith(q) -> 50; term.lowercase().contains(q) -> 10; else -> 0 } }
        if (score == 0) null else SettingsSearchResult(destination, score)
    }.sortedWith(compareByDescending<SettingsSearchResult> { it.score }.thenBy { settingsDestinationsInOrder().indexOf(it.destination) })
}

@Composable
internal fun settingsDestinationTitle(destination: SettingsDestination): String =
    stringResource(settingsDestinationTitleRes(destination))
