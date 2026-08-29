package io.github.lzyuuu.ailivesaver

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LorebookRootCreatorTest {

    // —— 路由与商店 launchTarget ——

    @Test
    fun `store launch targets resolve to lorebook and root creator routes`() {
        assertEquals(DesktopApp.Lorebook, DesktopApp.fromRoute("lorebook"))
        assertEquals(DesktopApp.RootCreator, DesktopApp.fromRoute("root_creator"))
        assertEquals("lorebook", DesktopApp.Lorebook.route)
        assertEquals("root_creator", DesktopApp.RootCreator.route)

        fun product(launchTarget: String) = StoreProduct(
            id = launchTarget,
            name = launchTarget,
            symbol = "◇",
            tagline = "t",
            description = "d",
            category = "Characters",
            launchTarget = launchTarget,
            kind = "app",
            builtIn = true,
            version = "1.0",
            features = emptyList(),
            requirements = emptyList(),
            requiredDownloadBytes = 0L,
            featured = false,
            availability = StoreAvailability.AVAILABLE,
        )
        assertEquals(DesktopApp.Lorebook, storeAppForProduct(product("lorebook")))
        assertEquals(DesktopApp.RootCreator, storeAppForProduct(product("root_creator")))
    }

    @Test
    fun `lorebook and root creator are gated by install state like other store apps`() {
        assertFalse(DesktopNavigator.isOpenable(DesktopApp.Lorebook, installed = false))
        assertTrue(DesktopNavigator.isOpenable(DesktopApp.Lorebook, installed = true))
        assertFalse(DesktopNavigator.isOpenable(DesktopApp.RootCreator, installed = false))
        assertTrue(DesktopNavigator.isOpenable(DesktopApp.RootCreator, installed = true))
    }

    // —— Lorebook 事实条目归一化 ——

    @Test
    fun `world fact entry is trimmed collapsed and capped`() {
        assertEquals("这座城市永远下着细雨。", normalizeWorldFactEntry("  这座城市永远下着细雨。  "))
        assertEquals("a b c", normalizeWorldFactEntry("a\n\n b\t  c"))
        assertEquals("", normalizeWorldFactEntry("   \n "))
        val long = "词".repeat(1000)
        assertEquals(600, normalizeWorldFactEntry(long).length)
    }

    // —— Root Creator 名字抽取 ——

    @Test
    fun `root creator extracts name from quoted or first token`() {
        assertEquals("小满", rootCreatorExtractName("「小满」，穿黑色风衣的女法医。"))
        assertEquals("小满", rootCreatorExtractName("小满，穿黑色风衣的女法医。"))
        assertEquals("Luna", rootCreatorExtractName("Luna the fox spirit"))
        assertEquals("林深", rootCreatorExtractName("林深 是一位退休的侦探。"))
        assertEquals("", rootCreatorExtractName(""))
    }

    // —— Root Creator 草稿与角色卡 ——

    @Test
    fun `root creator draft is null for blank description`() {
        assertNull(rootCreatorDraftFromDescription("   "))
        assertNull(rootCreatorDraftFromDescription(""))
    }

    @Test
    fun `root creator draft carries description and greeting`() {
        val draft = rootCreatorDraftFromDescription("小满，穿黑色风衣的女法医，冷静毒舌。")
        assertNotNull(draft)
        assertEquals("小满", draft!!.name)
        assertTrue(draft.description.contains("冷静毒舌"))
        assertTrue(draft.firstMessage.contains("小满"))
        assertTrue(draft.scenario.contains("小满"))
    }

    @Test
    fun `root creator card is valid chara card v2 json with name and first mes`() {
        val draft = rootCreatorDraftFromDescription("「小满」，穿黑色风衣的女法医，冷静毒舌。")!!
        val card = rootCreatorBuildCard(draft)
        val root = JSONObject(card.cardJson)
        assertEquals("chara_card_v2", root.optString("spec"))
        val data = root.getJSONObject("data")
        assertEquals("小满", data.optString("name"))
        assertTrue(data.optString("first_mes").isNotBlank())
        assertTrue(data.optString("description").contains("冷静毒舌"))
        // 外部图像提示词直接从描述抽取，非空。
        assertTrue(
            card.fields.appearancePrompt.isNotBlank() ||
                card.fields.clothing.isNotBlank() ||
                card.fields.visualStyle.isNotBlank(),
        )
        // 保存时写入的角色 persona 与卡内描述一致。
        val persona = CharacterCardV2.composePersona(card.fields)
        assertTrue(persona.contains("冷静毒舌"))
    }
}