package io.github.lzyuuu.ailivesaver

import androidx.annotation.StringRes
import kotlin.random.Random

/** 游戏会话结果的性质：胜负或中性结果。 */
enum class GameResultKind { WIN, LOSE, NEUTRAL }

/** 单次核心动作产生的结果状态，由 [GamesEngine.play] 纯函数产出。 */
data class GameSessionOutcome(
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    val kind: GameResultKind = GameResultKind.NEUTRAL,
)

/** 一个可点击的核心操作控件（选项 / 掷骰 / 抽牌）。 */
data class GameSessionOption(
    val id: String,
    @StringRes val labelRes: Int,
)

/** 选项结果的取法。 */
enum class GamePlayMode {
    /** 结果与选项一一对应（固定结局）。 */
    FIXED,

    /** 结果从 outcomePool 中随机抽取（掷骰 / 抽牌）。 */
    RANDOM,
}

/** 单个游戏的可玩会话定义：专属规则、核心操作与结果状态。 */
data class GameSessionSpec(
    val entryId: String,
    @StringRes val rulesRes: Int,
    val options: List<GameSessionOption>,
    /** FIXED 模式：与 options 一一对应的结果。 */
    val outcomes: List<GameSessionOutcome> = emptyList(),
    /** RANDOM 模式：结果池（骰子点数 / 塔罗牌）。 */
    val outcomePool: List<GameSessionOutcome> = emptyList(),
    val playMode: GamePlayMode = GamePlayMode.FIXED,
)

/**
 * Games Hub 六游戏的纯逻辑引擎：把「选项 → 结果」抽成可单测的纯函数，
 * GameSessionScreen 只负责渲染。六个 entry id 与 [GamesHubEntries] 一一对应。
 */
object GamesEngine {
    val specs: List<GameSessionSpec> = listOf(
        GameSessionSpec(
            entryId = "world_adventure",
            rulesRes = R.string.game_world_adventure_rules,
            options = listOf(
                GameSessionOption("forest", R.string.game_world_adventure_option_forest),
                GameSessionOption("river", R.string.game_world_adventure_option_river),
                GameSessionOption("camp", R.string.game_world_adventure_option_camp),
            ),
            outcomes = listOf(
                GameSessionOutcome(
                    R.string.game_world_adventure_outcome_forest_title,
                    R.string.game_world_adventure_outcome_forest_detail,
                ),
                GameSessionOutcome(
                    R.string.game_world_adventure_outcome_river_title,
                    R.string.game_world_adventure_outcome_river_detail,
                ),
                GameSessionOutcome(
                    R.string.game_world_adventure_outcome_camp_title,
                    R.string.game_world_adventure_outcome_camp_detail,
                ),
            ),
        ),
        GameSessionSpec(
            entryId = "dice_duel_rpg",
            rulesRes = R.string.game_dice_duel_rpg_rules,
            options = listOf(
                GameSessionOption("roll", R.string.game_dice_duel_rpg_option_roll),
            ),
            outcomePool = listOf(
                GameSessionOutcome(
                    R.string.game_dice_duel_rpg_outcome_hit_title,
                    R.string.game_dice_duel_rpg_outcome_hit_detail,
                    kind = GameResultKind.WIN,
                ),
                GameSessionOutcome(
                    R.string.game_dice_duel_rpg_outcome_miss_title,
                    R.string.game_dice_duel_rpg_outcome_miss_detail,
                    kind = GameResultKind.LOSE,
                ),
            ),
            playMode = GamePlayMode.RANDOM,
        ),
        GameSessionSpec(
            entryId = "tactical_command",
            rulesRes = R.string.game_tactical_command_rules,
            options = listOf(
                GameSessionOption("advance", R.string.game_tactical_command_option_advance),
                GameSessionOption("hold", R.string.game_tactical_command_option_hold),
                GameSessionOption("flank", R.string.game_tactical_command_option_flank),
            ),
            outcomes = listOf(
                GameSessionOutcome(
                    R.string.game_tactical_command_outcome_advance_title,
                    R.string.game_tactical_command_outcome_advance_detail,
                ),
                GameSessionOutcome(
                    R.string.game_tactical_command_outcome_hold_title,
                    R.string.game_tactical_command_outcome_hold_detail,
                ),
                GameSessionOutcome(
                    R.string.game_tactical_command_outcome_flank_title,
                    R.string.game_tactical_command_outcome_flank_detail,
                ),
            ),
        ),
        GameSessionSpec(
            entryId = "truth_or_dare",
            rulesRes = R.string.game_truth_or_dare_rules,
            options = listOf(
                GameSessionOption("truth", R.string.game_truth_or_dare_option_truth),
                GameSessionOption("dare", R.string.game_truth_or_dare_option_dare),
            ),
            outcomes = listOf(
                GameSessionOutcome(
                    R.string.game_truth_or_dare_outcome_truth_title,
                    R.string.game_truth_or_dare_outcome_truth_detail,
                ),
                GameSessionOutcome(
                    R.string.game_truth_or_dare_outcome_dare_title,
                    R.string.game_truth_or_dare_outcome_dare_detail,
                ),
            ),
        ),
        GameSessionSpec(
            entryId = "two_truths_lie",
            rulesRes = R.string.game_two_truths_lie_rules,
            options = listOf(
                GameSessionOption("a", R.string.game_two_truths_lie_option_a),
                GameSessionOption("b", R.string.game_two_truths_lie_option_b),
                GameSessionOption("c", R.string.game_two_truths_lie_option_c),
            ),
            outcomes = listOf(
                GameSessionOutcome(
                    R.string.game_two_truths_lie_outcome_a_title,
                    R.string.game_two_truths_lie_outcome_a_detail,
                    kind = GameResultKind.LOSE,
                ),
                GameSessionOutcome(
                    R.string.game_two_truths_lie_outcome_b_title,
                    R.string.game_two_truths_lie_outcome_b_detail,
                    kind = GameResultKind.WIN,
                ),
                GameSessionOutcome(
                    R.string.game_two_truths_lie_outcome_c_title,
                    R.string.game_two_truths_lie_outcome_c_detail,
                    kind = GameResultKind.LOSE,
                ),
            ),
        ),
        GameSessionSpec(
            entryId = "the_oracle",
            rulesRes = R.string.game_the_oracle_rules,
            options = listOf(
                GameSessionOption("draw", R.string.game_the_oracle_option_draw),
            ),
            outcomePool = listOf(
                GameSessionOutcome(
                    R.string.game_the_oracle_outcome_sun_title,
                    R.string.game_the_oracle_outcome_sun_detail,
                ),
                GameSessionOutcome(
                    R.string.game_the_oracle_outcome_moon_title,
                    R.string.game_the_oracle_outcome_moon_detail,
                ),
                GameSessionOutcome(
                    R.string.game_the_oracle_outcome_tower_title,
                    R.string.game_the_oracle_outcome_tower_detail,
                ),
                GameSessionOutcome(
                    R.string.game_the_oracle_outcome_star_title,
                    R.string.game_the_oracle_outcome_star_detail,
                ),
                GameSessionOutcome(
                    R.string.game_the_oracle_outcome_judgement_title,
                    R.string.game_the_oracle_outcome_judgement_detail,
                ),
            ),
            playMode = GamePlayMode.RANDOM,
        ),
    )

    fun specFor(entryId: String): GameSessionSpec? = specs.firstOrNull { it.entryId == entryId }

    /**
     * 执行一次核心动作：选择 [optionIndex] 号操作，返回结果状态。
     * entryId 或选项越界时返回 null（未知游戏不可玩）。
     */
    fun play(
        entryId: String,
        optionIndex: Int,
        random: Random = Random.Default,
    ): GameSessionOutcome? {
        val spec = specFor(entryId) ?: return null
        if (optionIndex !in spec.options.indices) return null
        return when (spec.playMode) {
            GamePlayMode.FIXED -> spec.outcomes[optionIndex]
            GamePlayMode.RANDOM -> spec.outcomePool[random.nextInt(spec.outcomePool.size)]
        }
    }

    /** 六个游戏均具备规则、可操作核心控件与结果状态（供测试与诊断）。 */
    fun isFullyPlayable(): Boolean = specs.size == GamesHubEntries.size && specs.all { spec ->
        spec.rulesRes != 0 &&
            spec.options.isNotEmpty() &&
            spec.options.all { it.id.isNotBlank() && it.labelRes != 0 } &&
            when (spec.playMode) {
                GamePlayMode.FIXED -> spec.outcomes.size == spec.options.size
                GamePlayMode.RANDOM -> spec.outcomePool.isNotEmpty()
            } &&
            (spec.outcomes + spec.outcomePool).all { it.titleRes != 0 && it.detailRes != 0 }
    }
}

// —— 规则深化（对齐参考玩法深度）：多回合 HP 战斗、行程进度、题库、猜谎、逆位塔罗。 ——

/** HP 战斗状态（骰子对决 / 战术指挥）：任一方 HP 归零即结束。 */
data class GameBattleState(
    val playerHp: Int = 10,
    val enemyHp: Int = 10,
    val round: Int = 0,
) {
    val finished: Boolean get() = playerHp <= 0 || enemyHp <= 0
    val won: Boolean get() = enemyHp <= 0 && playerHp > 0
}

/** 行程进度（世界冒险）：[total] 站旅程，[maxRounds] 回合内到达算胜，超时算负。 */
data class GameJourneyState(
    val step: Int = 0,
    val rounds: Int = 0,
    val total: Int = 5,
    val maxRounds: Int = 8,
) {
    val finished: Boolean get() = step >= total || rounds >= maxRounds
    val won: Boolean get() = step >= total
}

/** 两真一假单轮：三条陈述 + 谎言位置。 */
data class TwoTruthsRound(
    val statements: List<Int>,
    val lieIndex: Int,
) {
    init {
        check(statements.size == 3 && lieIndex in 0..2) { "两真一假轮次必须是 3 条陈述且谎言位置合法" }
    }
}

/** 神谕抽牌：牌面 + 是否逆位。 */
data class OracleDraw(val card: GameSessionOutcome, val reversed: Boolean)

/** 战斗单回合报告：新状态 + 叙事资源（含格式化参数，UI 侧 stringResource 解析）。 */
data class BattleTurn(
    val state: GameBattleState,
    val detailRes: Int,
    val args: List<Int> = emptyList(),
)

object GamesRules {
    private fun damageFor(diff: Int): Int = when {
        diff >= 3 -> 2
        diff >= 1 -> 1
        else -> 0
    }

    /** 骰子对决单回合：双方各掷 d6，差值 ≥3 伤 2、1..2 伤 1、平局无伤。 */
    fun diceDuelRound(state: GameBattleState, random: Random): BattleTurn {
        if (state.finished) return BattleTurn(state, R.string.game_dice_round_detail, listOf(0, 0))
        val mine = random.nextInt(6) + 1
        val theirs = random.nextInt(6) + 1
        val next = state.copy(
            enemyHp = (state.enemyHp - damageFor(mine - theirs)).coerceAtLeast(0),
            playerHp = (state.playerHp - damageFor(theirs - mine)).coerceAtLeast(0),
            round = state.round + 1,
        )
        return BattleTurn(next, R.string.game_dice_round_detail, listOf(mine, theirs))
    }

    /**
     * 战术指挥单回合：advance 克 flank、flank 克 hold、hold 克 advance
     * （b == (a+2)%3 时 a 克 b）。克制 -3、被克己 -3、同选僵持各 -1。
     */
    fun tacticalRound(state: GameBattleState, optionIndex: Int, random: Random): BattleTurn {
        if (state.finished) return BattleTurn(state, R.string.game_tactical_round_tie, listOf(state.playerHp, state.enemyHp))
        require(optionIndex in 0..2) { "战术指令越界" }
        val enemy = random.nextInt(3)
        val next = when {
            optionIndex == enemy -> state.copy(
                playerHp = (state.playerHp - 1).coerceAtLeast(0),
                enemyHp = (state.enemyHp - 1).coerceAtLeast(0),
                round = state.round + 1,
            )
            (optionIndex + 2) % 3 == enemy -> state.copy(
                enemyHp = (state.enemyHp - 3).coerceAtLeast(0),
                round = state.round + 1,
            )
            else -> state.copy(
                playerHp = (state.playerHp - 3).coerceAtLeast(0),
                round = state.round + 1,
            )
        }
        val (res, args) = when {
            optionIndex == enemy -> R.string.game_tactical_round_tie to listOf(next.playerHp, next.enemyHp)
            (optionIndex + 2) % 3 == enemy -> R.string.game_tactical_round_win to listOf(next.playerHp, next.enemyHp)
            else -> R.string.game_tactical_round_lose to listOf(next.playerHp, next.enemyHp)
        }
        return BattleTurn(next, res, args)
    }

    /** 世界冒险单回合：推进 1..2 站，20% 受阻原地踏步；到达 total 胜，maxRounds 耗尽负。 */
    fun worldAdventureStep(state: GameJourneyState, random: Random): GameJourneyState {
        if (state.finished) return state
        val blocked = random.nextInt(5) == 0
        val gain = if (blocked) 0 else 1 + random.nextInt(2)
        return state.copy(
            step = (state.step + gain).coerceAtMost(state.total),
            rounds = state.rounds + 1,
        )
    }

    /** 真话/大冒险题库（资源 id，UI 侧 stringResource 解析）。 */
    val truthPrompts: List<Int> = listOf(
        R.string.game_tot_truth_1,
        R.string.game_tot_truth_2,
        R.string.game_tot_truth_3,
        R.string.game_tot_truth_4,
        R.string.game_tot_truth_5,
        R.string.game_tot_truth_6,
    )
    val darePrompts: List<Int> = listOf(
        R.string.game_tot_dare_1,
        R.string.game_tot_dare_2,
        R.string.game_tot_dare_3,
        R.string.game_tot_dare_4,
        R.string.game_tot_dare_5,
        R.string.game_tot_dare_6,
    )

    fun truthOrDarePrompt(optionIndex: Int, random: Random): Int {
        require(optionIndex == 0 || optionIndex == 1) { "真话/大冒险选项越界" }
        val pool = if (optionIndex == 0) truthPrompts else darePrompts
        return pool[random.nextInt(pool.size)]
    }

    /** 两真一假轮次池（lieIndex 为谎言陈述下标）。 */
    val twoTruthsRounds: List<TwoTruthsRound> = listOf(
        TwoTruthsRound(
            listOf(R.string.game_ttl_a1, R.string.game_ttl_a2, R.string.game_ttl_a3),
            lieIndex = 2,
        ),
        TwoTruthsRound(
            listOf(R.string.game_ttl_b1, R.string.game_ttl_b2, R.string.game_ttl_b3),
            lieIndex = 1,
        ),
        TwoTruthsRound(
            listOf(R.string.game_ttl_c1, R.string.game_ttl_c2, R.string.game_ttl_c3),
            lieIndex = 0,
        ),
    )

    fun twoTruthsRound(random: Random): TwoTruthsRound =
        twoTruthsRounds[random.nextInt(twoTruthsRounds.size)]

    /** 猜中谎言才算胜。 */
    fun twoTruthsGuess(round: TwoTruthsRound, optionIndex: Int): Boolean = optionIndex == round.lieIndex

    /** 神谕抽牌：从 the_oracle 结果池抽牌面，独立掷逆位。 */
    fun oracleDraw(random: Random): OracleDraw {
        val pool = GamesEngine.specFor("the_oracle")?.outcomePool.orEmpty()
        check(pool.isNotEmpty()) { "神谕结果池缺失" }
        return OracleDraw(pool[random.nextInt(pool.size)], random.nextBoolean())
    }
}