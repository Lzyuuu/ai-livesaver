package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizationAndMemoryTest {
    @Test
    fun postAllowedMapsPlatformKindsToTheirSwitches() {
        val all = ChatControls(
            autoImageGeneration = false,
            allowPostY = true,
            allowPostUstagram = true,
            allowPostRebbit = true,
            webSearchEnabled = false,
        )
        val none = ChatControls(false, false, false, false, false)
        assertTrue(postAllowed(all, Y_POST_KIND))
        assertTrue(postAllowed(all, "forum"))
        assertTrue(postAllowed(all, "moment"))
        assertTrue(postAllowed(none, "interaction"))
        assertEquals(false, postAllowed(none, Y_POST_KIND))
        assertEquals(false, postAllowed(none, "forum"))
        assertEquals(false, postAllowed(none, "moment"))
    }

    @Test
    fun parseExtractedFactsReadsJsonAndToleratesFences() {
        assertEquals(
            listOf("用户喜欢夜跑", "用户住在杭州"),
            parseExtractedFacts("{\"facts\": [\"用户喜欢夜跑\", \"用户住在杭州\"]}"),
        )
        assertEquals(
            listOf("一条"),
            parseExtractedFacts("```json\n{\"facts\": [\"一条\"]}\n```"),
        )
        assertTrue(parseExtractedFacts("{\"facts\": []}").isEmpty())
        assertTrue(parseExtractedFacts("not json at all").isEmpty())
        assertTrue(parseExtractedFacts("{\"other\": 1}").isEmpty())
    }

    @Test
    fun extractPromptBoundsFactCount() {
        val prompt = memoryAutoExtractPrompt("对话内容")
        assertTrue(prompt.contains("0-$MEMORY_AUTO_MAX_FACTS"))
        assertTrue(prompt.contains("对话内容"))
    }
}
