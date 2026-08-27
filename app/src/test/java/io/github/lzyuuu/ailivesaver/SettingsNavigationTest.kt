package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {
    @Test
    fun primaryIndexMatchesReferenceV451Order() {
        // 参考 V4.51 设置索引固定 10 行，顺序一比一。
        assertEquals(
            listOf(
                "general",
                "models_engine",
                "cloud_llm_image",
                "voice",
                "instruction",
                "generation",
                "memory",
                "backups",
                "cleanup",
                "developer",
            ),
            settingsPrimaryOrder.map { it.name.lowercase() },
        )
    }

    @Test
    fun extendedSectionHoldsOurExtrasOnly() {
        assertEquals(
            listOf(
                "appearance",
                "world",
                "knowledge",
                "privacy",
                "storage",
                "help",
                "update",
            ),
            settingsExtendedOrder.map { it.name.lowercase() },
        )
        // 扩展组不与主列表重叠，参考没有的行不混入主列表。
        assertTrue(settingsPrimaryOrder.intersect(settingsExtendedOrder.toSet()).isEmpty())
    }

    @Test
    fun resolvesLegacyRouteAliases() {
        mapOf(
            "providers" to SettingsDestination.CLOUD_LLM_IMAGE,
            "voice" to SettingsDestination.VOICE,
            "dream" to SettingsDestination.LOCAL_DREAM,
            "world" to SettingsDestination.WORLD,
            "knowledge" to SettingsDestination.KNOWLEDGE,
            "diagnostics" to SettingsDestination.DIAGNOSTICS,
            "privacy" to SettingsDestination.PRIVACY,
            "backups" to SettingsDestination.BACKUPS,
            "updates" to SettingsDestination.UPDATE,
            "storage" to SettingsDestination.STORAGE,
            "identity" to SettingsDestination.IDENTITY,
            "characters" to SettingsDestination.CHARACTERS,
        ).forEach { (key, destination) -> assertEquals(destination, resolveSettingsDestination(key)) }
    }

    @Test
    fun instructionAndGenerationSearchableAndOrdered() {
        assertEquals(
            SettingsDestination.INSTRUCTION,
            searchSettings("指令").firstOrNull()?.destination,
        )
        assertEquals(
            SettingsDestination.GENERATION,
            searchSettings("生成").firstOrNull()?.destination,
        )
        assertTrue(
            settingsPrimaryOrder.indexOf(SettingsDestination.INSTRUCTION) <
                settingsPrimaryOrder.indexOf(SettingsDestination.GENERATION),
        )
        assertEquals(
            R.string.instruction_settings,
            settingsDestinationTitleRes(SettingsDestination.INSTRUCTION),
        )
        assertEquals(
            R.string.generation_settings,
            settingsDestinationTitleRes(SettingsDestination.GENERATION),
        )
    }

    @Test
    fun searchTieBreakIsExactAndStable() {
        val result = searchSettings("a")
        assertEquals(
            result.sortedWith(compareByDescending<SettingsSearchResult> { it.score }.thenBy { settingsDestinationsInOrder().indexOf(it.destination) }),
            result,
        )
        assertTrue(result.size > 1)
    }
}
