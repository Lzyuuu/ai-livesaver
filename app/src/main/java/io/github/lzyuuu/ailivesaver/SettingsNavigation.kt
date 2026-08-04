package io.github.lzyuuu.ailivesaver

/** Settings categories in the same order as the shipped desktop. */
enum class SettingsSection { CHAT_BRAIN, VOICE_CALLS, IMAGE_GENERATION, YOU_PERSONAS, APP, DEVELOPER_ABOUT, SYSTEM_SETTINGS, HELP_GUIDE, UPDATE }

/** Typed settings destinations. Route keys are compatibility input only. */
enum class SettingsDestination(val section: SettingsSection, val title: String, val synonyms: Set<String> = emptySet()) {
    PROVIDER(SettingsSection.CHAT_BRAIN, "Provider", setOf("provider", "providers", "供应商", "模型", "model")),
    CHAT_BRAIN(SettingsSection.CHAT_BRAIN, "Chat brain", setOf("brain", "inference", "推理")),
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

data class SettingsSearchResult(val destination: SettingsDestination, val score: Int)
private val destinationOrder = SettingsDestination.values().toList()
fun settingsDestinationsInOrder(): List<SettingsDestination> = destinationOrder.sortedWith(compareBy({ it.section.ordinal }, { it.ordinal }))
fun resolveSettingsDestination(key: String): SettingsDestination? {
    val normalized = key.trim().replace('-', '_').replace('/', '_')
    return destinationOrder.firstOrNull { it.name.equals(normalized, true) || it.synonyms.any { synonym -> synonym.equals(key.trim(), true) } }
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
