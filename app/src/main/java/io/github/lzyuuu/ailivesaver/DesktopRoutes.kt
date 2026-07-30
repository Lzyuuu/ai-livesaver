package io.github.lzyuuu.ailivesaver

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * 系统桌面开放入口与 Hub 分组。
 * 即使参考 APP 用 Pro 锁定，这里也保持开放入口。
 */
enum class DesktopApp(
    val route: String,
    val label: String,
    val openEntry: Boolean = true,
) {
    Messenger("messenger", "Messenger"),
    Imaging("imaging", "Imaging"),
    Gallery("gallery", "Gallery"),
    Settings("settings", "Settings"),
    Characters("characters", "Characters"),
    Binder("binder", "Binder"),
    Ustagram("ustagram", "Ustagram"),
    Rebbit("rebbit", "Rebbit"),
    Y("y", "Y"),
    Phone("phone", "Phone"),
    Games("games", "Games"),
    AuraSwap("aura_swap", "Aura Swap"),
    Storage("storage", "Storage"),
    ;

    companion object {
        fun fromRoute(route: String?): DesktopApp? =
            entries.firstOrNull { it.route == route }
    }
}

enum class DesktopHub(
    val route: String,
    val label: String,
    val apps: List<DesktopApp>,
) {
    Social(
        "social_hub",
        "Social Hub",
        listOf(
            DesktopApp.Characters,
            DesktopApp.Binder,
            DesktopApp.Ustagram,
            DesktopApp.Rebbit,
            DesktopApp.Y,
            DesktopApp.Phone,
        ),
    ),
    CreativeSuite(
        "creative_suite",
        "Creative Suite",
        listOf(
            DesktopApp.Imaging,
            DesktopApp.AuraSwap,
            DesktopApp.Gallery,
        ),
    ),
    SystemCore(
        "system_core",
        "System Core",
        listOf(
            DesktopApp.Storage,
            DesktopApp.Settings,
        ),
    ),
    Entertainment(
        "entertainment",
        "Entertainment",
        listOf(
            DesktopApp.Games,
        ),
    ),
    ;

    companion object {
        fun fromRoute(route: String?): DesktopHub? =
            entries.firstOrNull { it.route == route }
    }
}

/** Dock 固定四入口，对齐 fancy-ai 系统桌面。 */
val DesktopDockApps: List<DesktopApp> = listOf(
    DesktopApp.Messenger,
    DesktopApp.Imaging,
    DesktopApp.Gallery,
    DesktopApp.Settings,
)

/**
 * Games Hub 六个列表入口（对齐 fancy-ai Games Hub 卡片）。
 * 玩法可占位；入口保持开放，无 Pro 墙。
 */
data class GamesHubEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val iconRes: Int,
    @StringRes val statusNoteRes: Int,
)

val GamesHubEntries: List<GamesHubEntry> = listOf(
    GamesHubEntry(
        id = "world_adventure",
        titleRes = R.string.game_world_adventure_title,
        descriptionRes = R.string.game_world_adventure_description,
        iconRes = R.drawable.ic_game_world_adventure,
        statusNoteRes = R.string.game_world_adventure_status,
    ),
    GamesHubEntry(
        id = "dice_duel_rpg",
        titleRes = R.string.game_dice_duel_rpg_title,
        descriptionRes = R.string.game_dice_duel_rpg_description,
        iconRes = R.drawable.ic_game_dice_duel,
        statusNoteRes = R.string.game_dice_duel_rpg_status,
    ),
    GamesHubEntry(
        id = "tactical_command",
        titleRes = R.string.game_tactical_command_title,
        descriptionRes = R.string.game_tactical_command_description,
        iconRes = R.drawable.ic_game_tactical,
        statusNoteRes = R.string.game_tactical_command_status,
    ),
    GamesHubEntry(
        id = "truth_or_dare",
        titleRes = R.string.game_truth_or_dare_title,
        descriptionRes = R.string.game_truth_or_dare_description,
        iconRes = R.drawable.ic_game_truth_or_dare,
        statusNoteRes = R.string.game_truth_or_dare_status,
    ),
    GamesHubEntry(
        id = "two_truths_lie",
        titleRes = R.string.game_two_truths_lie_title,
        descriptionRes = R.string.game_two_truths_lie_description,
        iconRes = R.drawable.ic_game_two_truths,
        statusNoteRes = R.string.game_two_truths_lie_status,
    ),
    GamesHubEntry(
        id = "the_oracle",
        titleRes = R.string.game_the_oracle_title,
        descriptionRes = R.string.game_the_oracle_description,
        iconRes = R.drawable.ic_game_oracle,
        statusNoteRes = R.string.game_the_oracle_status,
    ),
)

sealed class DesktopRoute {
    data object Home : DesktopRoute()
    data class Hub(val hub: DesktopHub) : DesktopRoute()
    data class App(val app: DesktopApp) : DesktopRoute()
}

object DesktopNavigator {
    fun openHub(hub: DesktopHub): DesktopRoute = DesktopRoute.Hub(hub)

    fun openApp(app: DesktopApp): DesktopRoute {
        require(app.openEntry) { "入口 ${app.label} 被锁定，与开放入口约定冲突" }
        return DesktopRoute.App(app)
    }

    fun backFrom(route: DesktopRoute): DesktopRoute = when (route) {
        DesktopRoute.Home -> DesktopRoute.Home
        is DesktopRoute.Hub -> DesktopRoute.Home
        is DesktopRoute.App -> DesktopRoute.Home
    }

    fun isPaywalled(app: DesktopApp): Boolean = false
}
