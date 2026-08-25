package io.github.lzyuuu.ailivesaver

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Settings categories in the same order as the shipped desktop. */
enum class SettingsSection { CHAT_BRAIN, VOICE_CALLS, IMAGE_GENERATION, YOU_PERSONAS, APP, DEVELOPER_ABOUT, SYSTEM_SETTINGS, HELP_GUIDE, UPDATE }

internal fun defaultExpandedSettingsSections(): Map<SettingsSection, Boolean> =
    SettingsSection.entries.associateWith { true }

@Composable
internal fun settingsSectionTitle(section: SettingsSection): String =
    stringResource(settingsSectionTitleRes(section))

internal fun settingsSectionTitleRes(section: SettingsSection): Int = when (section) {
    SettingsSection.CHAT_BRAIN -> R.string.settings_category_ai_models
    SettingsSection.VOICE_CALLS -> R.string.settings_category_voice_calls
    SettingsSection.IMAGE_GENERATION -> R.string.settings_category_image_generation
    SettingsSection.YOU_PERSONAS -> R.string.settings_category_you_personas
    SettingsSection.APP -> R.string.settings_category_app
    SettingsSection.DEVELOPER_ABOUT -> R.string.settings_category_developer_about
    SettingsSection.SYSTEM_SETTINGS -> R.string.settings_category_system_settings
    SettingsSection.HELP_GUIDE -> R.string.settings_category_help_guide
    SettingsSection.UPDATE -> R.string.settings_category_update
}

/** Typed settings destinations. Route keys are compatibility input only. */
enum class SettingsDestination(val section: SettingsSection, val title: String, val synonyms: Set<String> = emptySet()) {
    PROVIDER(SettingsSection.CHAT_BRAIN, "Provider", setOf("provider", "providers", "供应商", "模型", "model")),
    CHAT_BRAIN(SettingsSection.CHAT_BRAIN, "Chat brain", setOf("brain", "inference", "推理")),
    GENERATION(SettingsSection.CHAT_BRAIN, "Generation", setOf("generation", "temperature", "top_p", "sampling", "生成参数", "温度", "采样", "指令模板", "模板")),
    VOICE(SettingsSection.VOICE_CALLS, "Voice", setOf("voice", "语音")),
    CALLS(SettingsSection.VOICE_CALLS, "Calls", setOf("call", "通话")),
    LOCAL_DREAM(SettingsSection.IMAGE_GENERATION, "Local Dream", setOf("image", "imaging", "dream", "local dream", "生图", "图像")),
    IMAGING(SettingsSection.IMAGE_GENERATION, "Imaging", setOf("creative", "图片")),
    IDENTITY(SettingsSection.YOU_PERSONAS, "Identity", setOf("profile", "identity", "身份", "个人资料")),
    CHARACTERS(SettingsSection.YOU_PERSONAS, "Characters", setOf("character", "角色")),
    APPEARANCE(SettingsSection.YOU_PERSONAS, "Appearance", setOf("appearance", "外观")),
    WORLD(SettingsSection.YOU_PERSONAS, "World", setOf("world", "世界")),
    KNOWLEDGE(SettingsSection.YOU_PERSONAS, "Knowledge", setOf("memory", "knowledge", "知识", "记忆")),
    PRIVACY(SettingsSection.APP, "Privacy", setOf("privacy", "隐私")),
    APP(SettingsSection.APP, "App", setOf("theme", "language", "应用")),
    BACKUPS(SettingsSection.APP, "Backups", setOf("backup", "备份")),
    DIAGNOSTICS(SettingsSection.DEVELOPER_ABOUT, "Diagnostics", setOf("diagnostic", "debug", "诊断")),
    RUNTIME(SettingsSection.DEVELOPER_ABOUT, "Runtime", setOf("runtime", "运行时")),
    ABOUT(SettingsSection.DEVELOPER_ABOUT, "About", setOf("about", "关于")),
    STORAGE(SettingsSection.SYSTEM_SETTINGS, "Storage", setOf("storage", "存储")),
    SYSTEM(SettingsSection.SYSTEM_SETTINGS, "System Settings", setOf("system", "系统设置")),
    HELP(SettingsSection.HELP_GUIDE, "Help Guide", setOf("help", "guide", "帮助", "指南")),
    UPDATE(SettingsSection.UPDATE, "Update", setOf("update", "updates", "release", "更新"));
}

internal fun settingsDestinationTitleRes(destination: SettingsDestination): Int = when (destination) {
    SettingsDestination.PROVIDER -> R.string.provider_settings
    SettingsDestination.GENERATION -> R.string.generation_settings
    SettingsDestination.CHAT_BRAIN -> R.string.settings_entry_chat_brain
    SettingsDestination.VOICE -> R.string.voice_calls_settings
    SettingsDestination.CALLS -> R.string.settings_entry_calls
    SettingsDestination.LOCAL_DREAM -> R.string.local_dream_settings
    SettingsDestination.IMAGING -> R.string.settings_entry_imaging
    SettingsDestination.IDENTITY -> R.string.user_identity
    SettingsDestination.CHARACTERS -> R.string.settings_entry_characters
    SettingsDestination.APPEARANCE -> R.string.appearance_settings
    SettingsDestination.WORLD -> R.string.world_settings
    SettingsDestination.KNOWLEDGE -> R.string.world_knowledge
    SettingsDestination.PRIVACY -> R.string.privacy_settings
    SettingsDestination.APP -> R.string.settings_entry_app
    SettingsDestination.BACKUPS -> R.string.backup_settings
    SettingsDestination.DIAGNOSTICS -> R.string.diagnostics_title
    SettingsDestination.RUNTIME -> R.string.runtime_status
    SettingsDestination.ABOUT -> R.string.about_updates
    SettingsDestination.STORAGE -> R.string.storage_settings
    SettingsDestination.SYSTEM -> R.string.settings_category_system_settings
    SettingsDestination.HELP -> R.string.settings_category_help_guide
    SettingsDestination.UPDATE -> R.string.about_updates
}

internal fun settingsDestinationSummaryRes(destination: SettingsDestination): Int = when (destination) {
    SettingsDestination.PROVIDER -> R.string.provider_settings_summary
    SettingsDestination.GENERATION -> R.string.generation_settings_summary
    SettingsDestination.VOICE -> R.string.voice_calls_settings_summary
    SettingsDestination.CHAT_BRAIN -> R.string.settings_entry_chat_brain_summary
    SettingsDestination.CALLS -> R.string.settings_entry_calls_summary
    SettingsDestination.LOCAL_DREAM -> R.string.local_dream_settings_summary
    SettingsDestination.IMAGING -> R.string.settings_entry_imaging_summary
    SettingsDestination.IDENTITY -> R.string.user_identity_summary
    SettingsDestination.CHARACTERS -> R.string.settings_entry_characters_summary
    SettingsDestination.APPEARANCE -> R.string.appearance_settings_summary
    SettingsDestination.WORLD -> R.string.world_settings_summary
    SettingsDestination.KNOWLEDGE -> R.string.world_knowledge_summary
    SettingsDestination.PRIVACY -> R.string.privacy_settings_summary
    SettingsDestination.APP -> R.string.appearance_settings_summary
    SettingsDestination.BACKUPS -> R.string.backup_settings_summary
    SettingsDestination.DIAGNOSTICS -> R.string.diagnostics_summary
    SettingsDestination.RUNTIME -> R.string.runtime_status_summary
    SettingsDestination.ABOUT -> R.string.about_updates_summary
    SettingsDestination.STORAGE -> R.string.storage_settings_summary
    SettingsDestination.SYSTEM -> R.string.storage_settings_summary
    SettingsDestination.HELP -> R.string.settings_entry_help_summary
    SettingsDestination.UPDATE -> R.string.about_updates_summary
}

data class SettingsSearchResult(val destination: SettingsDestination, val score: Int)
private val destinationOrder = SettingsDestination.values().toList()
fun settingsDestinationsInOrder(): List<SettingsDestination> = destinationOrder.sortedWith(compareBy({ it.section.ordinal }, { it.ordinal }))
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
    }.sortedWith(compareByDescending<SettingsSearchResult> { it.score }.thenBy { it.destination.section.ordinal }.thenBy { it.destination.ordinal })
}
