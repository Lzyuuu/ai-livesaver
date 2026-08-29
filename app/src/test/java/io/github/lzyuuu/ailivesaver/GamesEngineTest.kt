package io.github.lzyuuu.ailivesaver

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Games Hub 六游戏纯逻辑引擎：覆盖六 id、规则/选项/结果资源与核心动作。 */
class GamesEngineTest {
    @Test
    fun `six specs cover every games hub entry id`() {
        assertEquals(6, GamesEngine.specs.size)
        assertEquals(GamesHubEntries.map { it.id }, GamesEngine.specs.map { it.entryId })
    }

    @Test
    fun `every game exposes rules options and result resources`() {
        assertTrue(GamesEngine.isFullyPlayable())
        GamesEngine.specs.forEach { spec ->
            assertTrue("${spec.entryId} 缺规则文案", spec.rulesRes != 0)
            assertTrue("${spec.entryId} 缺核心操作", spec.options.isNotEmpty())
            spec.options.forEach { option ->
                assertTrue("${spec.entryId}.${option.id} 缺选项文案", option.labelRes != 0)
                assertTrue("${spec.entryId}.${option.id} id 为空", option.id.isNotBlank())
            }
            val results = when (spec.playMode) {
                GamePlayMode.FIXED -> spec.outcomes
                GamePlayMode.RANDOM -> spec.outcomePool
            }
            assertTrue("${spec.entryId} 缺结果状态", results.isNotEmpty())
            results.forEach { outcome ->
                assertTrue("${spec.entryId} 结果缺标题", outcome.titleRes != 0)
                assertTrue("${spec.entryId} 结果缺详情", outcome.detailRes != 0)
            }
        }
    }

    @Test
    fun `play returns a result for every option of every game`() {
        GamesEngine.specs.forEach { spec ->
            spec.options.indices.forEach { index ->
                val outcome = GamesEngine.play(spec.entryId, index, Random(7))
                assertNotNull("${spec.entryId}[$index] 无结果", outcome)
                checkNotNull(outcome)
                when (spec.playMode) {
                    GamePlayMode.FIXED -> assertEquals(spec.outcomes[index], outcome)
                    GamePlayMode.RANDOM -> assertTrue(outcome in spec.outcomePool)
                }
            }
        }
    }

    @Test
    fun `random games only draw from their own outcome pool`() {
        listOf("dice_duel_rpg", "the_oracle").forEach { id ->
            val spec = GamesEngine.specFor(id)!!
            repeat(64) { seed ->
                val outcome = GamesEngine.play(id, 0, Random(seed.toLong()))
                assertNotNull(outcome)
                assertTrue("$id 抽到池外结果", checkNotNull(outcome) in spec.outcomePool)
            }
        }
    }

    @Test
    fun `two truths and a lie has exactly one winning option`() {
        val spec = GamesEngine.specFor("two_truths_lie")!!
        assertEquals(GamePlayMode.FIXED, spec.playMode)
        assertEquals(3, spec.options.size)
        assertEquals(1, spec.outcomes.count { it.kind == GameResultKind.WIN })
        assertEquals(2, spec.outcomes.count { it.kind == GameResultKind.LOSE })
        val winIndex = spec.outcomes.indexOfFirst { it.kind == GameResultKind.WIN }
        assertTrue(winIndex in spec.options.indices)
        spec.options.indices.forEach { index ->
            val outcome = GamesEngine.play(spec.entryId, index, Random(3))!!
            assertEquals(index == winIndex, outcome.kind == GameResultKind.WIN)
        }
    }

    @Test
    fun `unknown game or option returns null`() {
        assertNull(GamesEngine.specFor("missing_game"))
        assertNull(GamesEngine.play("missing_game", 0))
        assertNull(GamesEngine.play("world_adventure", 99))
        assertNull(GamesEngine.play("world_adventure", -1))
        assertNull(GamesEngine.play("the_oracle", 1))
    }

    @Test
    fun `dice duel roll is either a win or a lose`() {
        repeat(64) { seed ->
            val outcome = GamesEngine.play("dice_duel_rpg", 0, Random(seed.toLong()))!!
            assertTrue(outcome.kind == GameResultKind.WIN || outcome.kind == GameResultKind.LOSE)
        }
    }
}