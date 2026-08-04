package io.github.lzyuuu.ailivesaver

/** Stable, non-stringly-typed destinations for the Settings desktop. */
enum class SettingsSection { CHAT_BRAIN, VOICE_CALLS, IMAGE_GENERATION, YOU_PERSONAS, APP, DEVELOPER_ABOUT, SYSTEM_SETTINGS, HELP_GUIDE, UPDATE }
enum class SettingsDestination(val section: SettingsSection, val title: String, val synonyms: Set<String> = emptySet()) {
    CHAT_BRAIN_MAIN(SettingsSection.CHAT_BRAIN, "Chat brain", setOf("model", "provider", "inference")),
    VOICE_CALLS_MAIN(SettingsSection.VOICE_CALLS, "Voice & Calls", setOf("voice", "call", "audio")),
    IMAGE_GENERATION_MAIN(SettingsSection.IMAGE_GENERATION, "Image Generation", setOf("image", "picture", "local dream")),
    YOU_PERSONAS_MAIN(SettingsSection.YOU_PERSONAS, "You & Personas", setOf("profile", "identity", "character")),
    APP_MAIN(SettingsSection.APP, "App", setOf("theme", "language", "notification")),
    DEVELOPER_ABOUT_MAIN(SettingsSection.DEVELOPER_ABOUT, "Developer & About", setOf("about", "debug", "diagnostic")),
    SYSTEM_SETTINGS_MAIN(SettingsSection.SYSTEM_SETTINGS, "System Settings", setOf("storage", "backup", "permission")),
    HELP_GUIDE_MAIN(SettingsSection.HELP_GUIDE, "Help Guide", setOf("help", "guide", "faq")),
    UPDATE_MAIN(SettingsSection.UPDATE, "Update", setOf("release", "version"));
}

data class SettingsSearchResult(val destination: SettingsDestination, val score: Int)

fun settingsDestinationsInOrder(): List<SettingsDestination> = SettingsDestination.entries.sortedWith(compareBy({ it.section.ordinal }, { it.ordinal }))
fun resolveSettingsDestination(key: String): SettingsDestination? = SettingsDestination.entries.firstOrNull { it.name.equals(key.trim().replace('-', '_'), true) }
fun searchSettings(query: String): List<SettingsSearchResult> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return settingsDestinationsInOrder().map { SettingsSearchResult(it, 0) }
    return SettingsDestination.entries.mapNotNull { d ->
        val hay = listOf(d.name, d.title) + d.synonyms.toList()
        val score = hay.maxOfOrNull { value -> when { value.lowercase() == q -> 100; value.lowercase().startsWith(q) -> 50; value.lowercase().contains(q) -> 10; else -> 0 } } ?: 0
        if (score > 0) SettingsSearchResult(d, score) else null
    }.sortedWith(compareByDescending<SettingsSearchResult> { it.score }.thenBy { it.destination.section.ordinal })
}
