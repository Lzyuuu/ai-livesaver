package io.github.lzyuuu.ailivesaver

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

/** Games Hub 六个列表入口；玩法可占位。 */
val GamesHubEntries: List<String> = listOf(
    "World Adventure",
    "Dice Duel RPG",
    "Tactical Command",
    "Truth or Dare",
    "Two Truths & A Lie",
    "The Oracle",
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
