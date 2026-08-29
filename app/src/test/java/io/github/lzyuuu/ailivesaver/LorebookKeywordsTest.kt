package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 世界书条目结构：关键词解析、触发判定、注入选择（对齐参考「使用世界书知识」）。 */
class LorebookKeywordsTest {
    private fun fact(
        id: Long,
        body: String,
        keywords: String = "",
        enabled: Boolean = true,
        pinned: Boolean = false,
    ) = WorldFact(id, body, pinned, createdAt = id, keywords = keywords, enabled = enabled)

    @Test
    fun keywordsParseSplitsOnMixedSeparatorsAndDedupes() {
        assertEquals(
            listOf("细雨", "rain", "老车站"),
            parseLorebookKeywords("细雨，rain、 老车站;细雨,, rain"),
        )
        assertEquals(emptyList<String>(), parseLorebookKeywords("，、；  "))
        // 各截 24 字、最多 12 个。
        assertEquals(12, parseLorebookKeywords((1..20).joinToString("，") { "关键词$it" }).size)
        assertEquals("a".repeat(24), parseLorebookKeywords("a".repeat(40)).single())
    }

    @Test
    fun keywordTriggerIsCaseInsensitiveAndEmptyNeverTriggers() {
        assertTrue(lorebookKeywordTriggered(listOf("Rain"), "it was a rainy night（下雨）"))
        assertTrue(lorebookKeywordTriggered(listOf("rain"), "昨晚开始 RAIN 了"))
        assertFalse(lorebookKeywordTriggered(emptyList(), "任何文本"))
        assertFalse(lorebookKeywordTriggered(listOf("雪"), "只有雨"))
    }

    @Test
    fun selectIncludesGlobalFactsAndGatesKeywordFactsByConversation() {
        val global = fact(1, "这座城市永远下着细雨。")
        val gated = fact(2, "老车站月台通往废弃线路。", keywords = "老车站，station")
        val other = fact(3, "北方有雪。", keywords = "雪")
        val selected = selectLorebookFacts(
            listOf(global, gated, other),
            conversationText = "user: 我们去 old station 看看",
        )
        // 全局事实始终注入；带关键词的仅命中时注入。
        assertEquals(listOf(global, gated), selected)
    }

    @Test
    fun selectExcludesDisabledFactsAndHonorsMasterSwitchAndLimit() {
        val facts = listOf(
            fact(1, "事实一"),
            fact(2, "已停用", enabled = false),
            fact(3, "关键词未命中", keywords = "不存在"),
        )
        assertEquals(listOf(fact(1, "事实一")), selectLorebookFacts(facts, ""))
        // 总开关关闭 → 空。
        assertTrue(selectLorebookFacts(facts, "任何", lorebookEnabled = false).isEmpty())
        // limit 生效。
        val many = (1..30).map { fact(it.toLong(), "事实$it") }
        assertEquals(20, selectLorebookFacts(many, "").size)
    }

    @Test
    fun chatSystemPromptOnlyInjectsMatchedKeywordFacts() {
        val character = ResidentCharacter(
            id = 1,
            name = "小满",
            persona = "冷静的法医。",
        )
        val facts = listOf(
            WorldFact(1, "城市永远下着细雨。", pinned = false, createdAt = 1),
            WorldFact(2, "老车站通往废弃线路。", pinned = false, createdAt = 2, keywords = "老车站"),
        )
        val promptWithoutMatch = buildChatSystemPrompt(character, emptyList(), null, facts)
        assertTrue(promptWithoutMatch.contains("城市永远下着细雨"))
        assertFalse(promptWithoutMatch.contains("老车站通往废弃线路"))
        val promptWithMatch = buildChatSystemPrompt(
            character,
            emptyList(),
            null,
            facts,
            conversationText = "我们坐车去老车站。",
        )
        assertTrue(promptWithMatch.contains("老车站通往废弃线路"))
    }
}
