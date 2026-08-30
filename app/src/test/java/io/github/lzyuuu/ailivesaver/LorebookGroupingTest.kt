package io.github.lzyuuu.ailivesaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 世界书书册 / 分组（SO-12）：名称归一化、展示顺序，以及注入语义不因分组改变。 */
class LorebookGroupingTest {
    @Test
    fun bookNameNormalizationFallsBackToSharedBook() {
        assertEquals(LOREBOOK_SHARED_BOOK, normalizeLorebookBookName("   "))
        assertEquals(LOREBOOK_SHARED_BOOK, normalizeLorebookBookName(""))
        assertEquals("Root Sudo", normalizeLorebookBookName("  Root   Sudo  "))
        assertEquals("长".repeat(24), normalizeLorebookBookName("长".repeat(40)))
    }

    @Test
    fun groupNameNormalizationBlanksMeanUngrouped() {
        assertEquals("", normalizeLorebookGroupName("  "))
        assertEquals("地点", normalizeLorebookGroupName(" 地点 "))
        assertEquals(24, normalizeLorebookGroupName("分".repeat(30)).length)
    }

    @Test
    fun orderedBooksKeepSharedAndRootFirstThenRemainingSorted() {
        // 两本书册始终展示：默认「已分享」居首，其次「Root Sudo」。
        assertEquals(
            listOf(LOREBOOK_SHARED_BOOK, LOREBOOK_ROOT_BOOK),
            orderedLorebookBooks(emptyList()),
        )
        // 其余书册按名称排序、去重，且不改变两本默认书册的相对顺序。
        assertEquals(
            listOf(LOREBOOK_SHARED_BOOK, LOREBOOK_ROOT_BOOK, "Alpha", "beta"),
            orderedLorebookBooks(listOf("beta", LOREBOOK_ROOT_BOOK, "Alpha", LOREBOOK_SHARED_BOOK)),
        )
        assertEquals(
            listOf(LOREBOOK_SHARED_BOOK, LOREBOOK_ROOT_BOOK),
            orderedLorebookBooks(listOf(LOREBOOK_ROOT_BOOK, LOREBOOK_SHARED_BOOK, LOREBOOK_ROOT_BOOK)),
        )
    }

    @Test
    fun injectionIgnoresBookAndGroup() {
        val shared = WorldFact(
            1, "共享事实", pinned = false, createdAt = 1,
            book = LOREBOOK_SHARED_BOOK,
        )
        val root = WorldFact(
            2, "Root 设定", pinned = false, createdAt = 2,
            keywords = "root", book = LOREBOOK_ROOT_BOOK, group = "设定",
        )
        val gatedShared = WorldFact(
            3, "地点事实", pinned = false, createdAt = 3,
            keywords = "车站", book = LOREBOOK_SHARED_BOOK, group = "地点",
        )
        // 始终注入（无关键词）与关键词命中不因书册/分组变化。
        assertEquals(
            listOf(shared),
            selectLorebookFacts(listOf(shared, root, gatedShared), "普通对话"),
        )
        assertEquals(
            listOf(shared, root),
            selectLorebookFacts(listOf(shared, root, gatedShared), "提到 root 关键词"),
        )
        // 总开关仍生效。
        assertTrue(selectLorebookFacts(listOf(shared), "任何", lorebookEnabled = false).isEmpty())
        // enabled 仍生效。
        val disabled = WorldFact(
            4, "停用条目", pinned = false, createdAt = 4, enabled = false,
            book = LOREBOOK_ROOT_BOOK,
        )
        assertFalse(selectLorebookFacts(listOf(disabled), "任何").isNotEmpty())
    }
}