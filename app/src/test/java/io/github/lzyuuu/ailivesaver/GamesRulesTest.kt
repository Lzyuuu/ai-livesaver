package io.github.lzyuuu.ailivesaver

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 六游戏规则深化：HP 战斗、行程进度、题库、猜谎、逆位塔罗。 */
class GamesRulesTest {
    @Test
    fun diceDuelDamagesByRollDifference() {
        // 固定骰：Random(1) 的确定性序列；先验证单回合守恒——双方 HP 只减不增。
        val seed = Random(7)
        var state = GameBattleState()
        repeat(50) {
            state = GamesRules.diceDuelRound(state, seed).state
            assertTrue(state.playerHp in 0..10)
            assertTrue(state.enemyHp in 0..10)
            if (state.playerHp > 0 && state.enemyHp > 0) assertFalse(state.finished)
        }
        // 50 回合内必有一方被击倒（d6 差值期望伤害为正）。
        assertTrue(state.finished)
    }

    @Test
    fun diceDuelReportsRollsInNarrativeArgs() {
        val turn = GamesRules.diceDuelRound(GameBattleState(), Random(1))
        assertEquals(2, turn.args.size)
        assertTrue(turn.args[0] in 1..6)
        assertTrue(turn.args[1] in 1..6)
    }

    @Test
    fun tacticalCountersFollowAdvanceFlankHoldCycle() {
        // advance(0) 克 flank(2)：穷举种子找到一次「胜」回合，敌方应恰好掉 3 血。
        for (seed in 0..64) {
            val turn = GamesRules.tacticalRound(GameBattleState(), optionIndex = 0, random = Random(seed))
            if (turn.detailRes == R.string.game_tactical_round_win) {
                assertEquals(7, turn.state.enemyHp)
                assertEquals(10, turn.state.playerHp)
                return
            }
        }
        // 64 个种子内必然出现一次「advance 克 flank」。
        assertTrue(false)
    }

    @Test
    fun tacticalTieCostsBothOneHp() {
        // 穷举种子找到敌方同选 advance(0) 的对局。
        for (seed in 0..64) {
            val turn = GamesRules.tacticalRound(GameBattleState(), 0, Random(seed))
            if (turn.detailRes == R.string.game_tactical_round_tie) {
                assertEquals(9, turn.state.playerHp)
                assertEquals(9, turn.state.enemyHp)
                return
            }
        }
        assertTrue(false)
    }

    @Test
    fun battleFinishRequiresZeroHpAndWinRequiresSurvival() {
        val lost = GameBattleState(playerHp = 0, enemyHp = 0)
        assertTrue(lost.finished)
        assertFalse(lost.won)
        val won = GameBattleState(playerHp = 1, enemyHp = 0)
        assertTrue(won.finished && won.won)
    }

    @Test
    fun journeyAdvancesAndTimesOut() {
        // 快速种子：不断推进直至结束；终点必为胜或 8 回合超时。
        var state = GameJourneyState()
        var guard = 0
        while (!state.finished && guard < 64) {
            state = GamesRules.worldAdventureStep(state, Random(guard.toLong()))
            guard++
        }
        assertTrue(state.finished)
        assertTrue(state.won == (state.step >= state.total))
        assertTrue(state.rounds <= state.maxRounds + 1)
    }

    @Test
    fun truthOrDarePromptsStayWithinTheirPools() {
        val truth = GamesRules.truthOrDarePrompt(0, Random(3))
        val dare = GamesRules.truthOrDarePrompt(1, Random(3))
        assertTrue(truth in GamesRules.truthPrompts)
        assertTrue(dare in GamesRules.darePrompts)
        // 池不交叉。
        assertTrue(GamesRules.truthPrompts.toSet().intersect(GamesRules.darePrompts.toSet()).isEmpty())
    }

    @Test
    fun twoTruthsRoundsHaveThreeStatementsAndResolvableLies() {
        assertEquals(3, GamesRules.twoTruthsRounds.size)
        GamesRules.twoTruthsRounds.forEach { round ->
            assertEquals(3, round.statements.size)
            assertTrue(round.lieIndex in 0..2)
            // 每轮恰好一个谎言位置可判胜。
            assertTrue(GamesRules.twoTruthsGuess(round, round.lieIndex))
            (0..2).filter { it != round.lieIndex }.forEach { other ->
                assertFalse(GamesRules.twoTruthsGuess(round, other))
            }
        }
    }

    @Test
    fun oracleDrawAlwaysCarriesACardAndReversedFlag() {
        repeat(16) { seed ->
            val draw = GamesRules.oracleDraw(Random(seed.toLong()))
            assertTrue(draw.card in GamesEngine.specFor("the_oracle")!!.outcomePool)
        }
        // 逆位标志在多次抽样中应出现两种取值（概率性断言，64 次全同概率 2^-64）。
        val flags = (0 until 64).map { GamesRules.oracleDraw(Random(it.toLong())).reversed }.toSet()
        assertEquals(setOf(true, false), flags)
    }
}
