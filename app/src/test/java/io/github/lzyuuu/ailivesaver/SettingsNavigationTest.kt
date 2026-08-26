package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationTest {
    @Test
    fun resolvesAllLegacyRouteAliases() {
        mapOf(
            "providers" to SettingsDestination.PROVIDER,
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
    fun searchTieBreakIsExactAndStable() {
        val result = searchSettings("a")
        assertEquals(
            result.sortedWith(compareByDescending<SettingsSearchResult> { it.score }.thenBy { it.destination.section.ordinal }.thenBy { it.destination.ordinal }),
            result,
        )
        assertTrue(result.size > 1)
    }

    @Test
    fun imageGenerationEntriesRouteToDistinctDestinations() {
        assertEquals(SettingsDestination.LOCAL_DREAM, resolveSettingsDestination("dream"))
        assertEquals(SettingsDestination.IMAGING, resolveSettingsDestination("imaging"))
        assertEquals(
            R.string.settings_entry_imaging,
            settingsDestinationTitleRes(SettingsDestination.IMAGING),
        )
        assertEquals(
            R.string.local_dream_settings,
            settingsDestinationTitleRes(SettingsDestination.LOCAL_DREAM),
        )
    }

    @Test
    fun instructionAndGenerationAreSeparateAiModelEntries() {
        assertEquals(SettingsDestination.INSTRUCTION, resolveSettingsDestination("指令"))
        assertEquals(SettingsDestination.GENERATION, resolveSettingsDestination("生成"))
        assertEquals(
            R.string.instruction_settings,
            settingsDestinationTitleRes(SettingsDestination.INSTRUCTION),
        )
        assertEquals(
            R.string.generation_settings,
            settingsDestinationTitleRes(SettingsDestination.GENERATION),
        )
        val aiEntries = settingsDestinationsInOrder().filter { it.section == SettingsSection.CHAT_BRAIN }
        assertTrue(aiEntries.contains(SettingsDestination.INSTRUCTION))
        assertTrue(aiEntries.contains(SettingsDestination.GENERATION))
        assertTrue(
            aiEntries.indexOf(SettingsDestination.INSTRUCTION) <
                aiEntries.indexOf(SettingsDestination.GENERATION),
        )
    }

    @Test
    fun appearanceAndAppShareThemeSummary() {
        assertEquals(
            R.string.appearance_settings_summary,
            settingsDestinationSummaryRes(SettingsDestination.APPEARANCE),
        )
        assertEquals(
            R.string.appearance_settings_summary,
            settingsDestinationSummaryRes(SettingsDestination.APP),
        )
    }
}
