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
    Groups("groups", "Groups"),
    RootProducer("root_producer", "Root Producer"),
    Lorebook("lorebook", "Lorebook"),
    RootCreator("root_creator", "Root Creator"),
    Games("games", "Games"),
    AuraSwap("aura_swap", "Aura Swap"),
    Storage("storage", "Storage"),
    Benchmark("benchmark", "Benchmark"),
    Store("store", "商店"),
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

/** Dock 固定四入口 + 商店，对齐 fancy-ai 系统桌面。 */
val DesktopDockApps: List<DesktopApp> = listOf(
    DesktopApp.Messenger,
    DesktopApp.Imaging,
    DesktopApp.Gallery,
    DesktopApp.Store,
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

    /**
     * 商店安装态门控：目录 App 需要已安装才可达（未安装一律降级到商店）；
     * coming_soon/upcoming 只影响商店内的获取按钮，不额外拦截已安装的旧数据。
     */
    fun isOpenable(app: DesktopApp, installed: Boolean): Boolean = when (app) {
        DesktopApp.Store, DesktopApp.Messenger, DesktopApp.Imaging,
        DesktopApp.Gallery, DesktopApp.Settings, DesktopApp.Characters,
        -> true
        else -> installed
    }

    fun backFrom(route: DesktopRoute): DesktopRoute = when (route) {
        DesktopRoute.Home -> DesktopRoute.Home
        is DesktopRoute.Hub -> DesktopRoute.Home
        is DesktopRoute.App -> DesktopRoute.Home
    }

    fun isPaywalled(app: DesktopApp): Boolean = false

    /**
     * Dock 固定 5 格，不随首页安装增长（#70，对齐参考 V4.51 不增长行为）；
     * 已添加到首页的目录 App 只出现在首页应用网格。
     */
    fun composeDock(): List<DesktopApp> = DesktopDockApps

    /**
     * 首页应用网格：Characters 固定首位，其后为 on_home 商店 App（按 home_order）。
     */
    internal fun composeHomeGrid(
        homeInstalls: List<PersistedAppInstall>,
        products: List<StoreProduct>,
    ): List<DesktopHomeGridEntry> {
        val productById = products.associateBy { it.id }
        val homeEntries = homeInstalls
            .filter { it.onHome }
            .sortedBy { it.homeOrder }
            .mapNotNull { install ->
                val product = productById[install.appId] ?: return@mapNotNull null
                val app = DesktopApp.fromRoute(product.launchTarget) ?: return@mapNotNull null
                if (app == DesktopApp.Characters) return@mapNotNull null
                DesktopHomeGridEntry(
                    app = app,
                    label = product.name,
                    symbol = product.symbol,
                    productId = install.appId,
                )
            }
        return listOf(
            DesktopHomeGridEntry(
                app = DesktopApp.Characters,
                label = DesktopApp.Characters.label,
                symbol = null,
                productId = null,
            ),
        ) + homeEntries
    }

    /**
     * DT-02 桌面第二页「全部应用」网格集合：当前全部可达桌面应用。
     *
     * 集合 = 首页网格项（[composeHomeGrid]，保留 desktop-grid-* 标签与长按/门控语义）在前，
     * 其余可达应用按 [DesktopApp] 声明序随后：
     *  - 常开壳层入口（[isOpenable] 不依赖安装态：Messenger/Imaging/Gallery/Settings/Store 等）；
     *  - 已安装但未上首页的目录 App（安装态门控下当前可达）。
     * 未安装的目录 App 不可达，不出现在集合中。补充项 productId 一律为 null
     * （无长按菜单），由界面层以 desktop-apps-* 标签区分，避免与首页网格语义混淆。
     */
    internal fun composeFullAppGrid(
        homeInstalls: List<PersistedAppInstall>,
        products: List<StoreProduct>,
        installStatus: Map<String, InstallStatus>,
    ): List<DesktopHomeGridEntry> {
        val homeEntries = composeHomeGrid(homeInstalls, products)
        val homeRoutes = homeEntries.mapTo(mutableSetOf()) { it.app.route }
        val extras = DesktopApp.entries
            .filter { it.route !in homeRoutes }
            .filter { app ->
                val productId = products.firstOrNull { it.launchTarget == app.route }?.id
                isOpenable(
                    app,
                    installed = productId != null &&
                        installStatus[productId] == InstallStatus.INSTALLED,
                )
            }
            .map { app ->
                // 仅当目录产品与路由一一对应时沿用产品名/符号；扩展包类目录项
                // （如 hd-upscalers → imaging）不抢占壳层入口的默认标签。
                val product = products.firstOrNull { it.id == app.route }
                DesktopHomeGridEntry(
                    app = app,
                    label = product?.name ?: app.label,
                    symbol = product?.symbol,
                    productId = null,
                )
            }
        return homeEntries + extras
    }
}

/** 系统桌面首页网格单元：内置 Characters 或已添加到首页的商店 App。 */
data class DesktopHomeGridEntry(
    val app: DesktopApp,
    val label: String,
    val symbol: String?,
    /** 商店 product id；仅已添加到首页的商店 App 有值，用于长按管理。 */
    val productId: String? = null,
) {
    val testTag: String = "desktop-grid-${app.route}"

    /** 商店安装且已添加到首页的网格项才支持长按菜单。 */
    val supportsLongPressMenu: Boolean get() = productId != null
}
