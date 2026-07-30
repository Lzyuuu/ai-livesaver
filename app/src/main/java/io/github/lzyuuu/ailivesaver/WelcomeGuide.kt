package io.github.lzyuuu.ailivesaver

/** 迎宾引导步骤，对齐 fancy-ai 首次叙事路径。 */
enum class WelcomeStep {
    NotificationPermission,
    RootIntro1,
    RootIntro2,
    UserName,
    Appearance,
    About,
    SetupMode,
    AiMode,
    LocalDownload,
    CloudSetup,
    Finished,
}

enum class WelcomeSetupMode {
    Easy,
    Custom,
}

enum class WelcomeAiMode {
    Local,
    Cloud,
}

/** Root 外观六选一：anime/real × blonde/brunette/redhead。 */
enum class RootAppearance(val id: String, val labelZh: String) {
    AnimeBlonde("anime_blonde", "动漫 · 金发"),
    AnimeBrunette("anime_brunette", "动漫 · 深发"),
    AnimeRedhead("anime_redhead", "动漫 · 红发"),
    RealBlonde("real_blonde", "写实 · 金发"),
    RealBrunette("real_brunette", "写实 · 深发"),
    RealRedhead("real_redhead", "写实 · 红发"),
    ;

    companion object {
        fun fromId(id: String?): RootAppearance? =
            entries.firstOrNull { it.id == id }
    }
}

data class WelcomeGuideState(
    val step: WelcomeStep = WelcomeStep.NotificationPermission,
    val userName: String = "",
    val appearanceId: String? = null,
    val about: String = "",
    val setupMode: WelcomeSetupMode? = null,
    val aiMode: WelcomeAiMode? = null,
    val localFailed: Boolean = false,
    val cloudProvider: String? = null,
) {
    val appearance: RootAppearance?
        get() = RootAppearance.fromId(appearanceId)

    val canContinueName: Boolean
        get() = userName.trim().isNotEmpty()

    val canContinueAppearance: Boolean
        get() = appearanceId != null

    val canContinueCloud: Boolean
        get() = !cloudProvider.isNullOrBlank()
}

/** 迎宾引导纯状态机：对照验收为主、单元测试可覆盖路径。 */
object WelcomeGuide {
    fun afterNotificationHandled(state: WelcomeGuideState): WelcomeGuideState =
        state.copy(step = WelcomeStep.RootIntro1)

    fun continueFrom(state: WelcomeGuideState): WelcomeGuideState = when (state.step) {
        WelcomeStep.NotificationPermission -> state.copy(step = WelcomeStep.RootIntro1)
        WelcomeStep.RootIntro1 -> state.copy(step = WelcomeStep.RootIntro2)
        WelcomeStep.RootIntro2 -> state.copy(step = WelcomeStep.UserName)
        WelcomeStep.UserName ->
            if (state.canContinueName) state.copy(step = WelcomeStep.Appearance) else state
        WelcomeStep.Appearance ->
            if (state.canContinueAppearance) state.copy(step = WelcomeStep.About) else state
        WelcomeStep.About -> state.copy(step = WelcomeStep.SetupMode)
        WelcomeStep.SetupMode ->
            if (state.setupMode != null) state.copy(step = WelcomeStep.AiMode) else state
        WelcomeStep.AiMode -> when (state.aiMode) {
            WelcomeAiMode.Local -> state.copy(step = WelcomeStep.LocalDownload, localFailed = false)
            WelcomeAiMode.Cloud -> state.copy(step = WelcomeStep.CloudSetup)
            null -> state
        }
        WelcomeStep.LocalDownload ->
            if (state.localFailed) state else state.copy(step = WelcomeStep.Finished)
        WelcomeStep.CloudSetup ->
            if (state.canContinueCloud) state.copy(step = WelcomeStep.Finished) else state
        WelcomeStep.Finished -> state
    }

    fun backFrom(state: WelcomeGuideState): WelcomeGuideState = when (state.step) {
        WelcomeStep.NotificationPermission,
        WelcomeStep.RootIntro1,
        WelcomeStep.Finished,
        -> state
        WelcomeStep.RootIntro2 -> state.copy(step = WelcomeStep.RootIntro1)
        WelcomeStep.UserName -> state.copy(step = WelcomeStep.RootIntro2)
        WelcomeStep.Appearance -> state.copy(step = WelcomeStep.UserName)
        WelcomeStep.About -> state.copy(step = WelcomeStep.Appearance)
        WelcomeStep.SetupMode -> state.copy(step = WelcomeStep.About)
        WelcomeStep.AiMode -> state.copy(step = WelcomeStep.SetupMode, aiMode = null)
        WelcomeStep.LocalDownload ->
            state.copy(step = WelcomeStep.AiMode, localFailed = false)
        WelcomeStep.CloudSetup ->
            state.copy(step = WelcomeStep.AiMode, cloudProvider = null)
    }

    fun withName(state: WelcomeGuideState, name: String): WelcomeGuideState =
        state.copy(userName = name)

    fun withAppearance(state: WelcomeGuideState, appearance: RootAppearance): WelcomeGuideState =
        state.copy(appearanceId = appearance.id)

    fun withAbout(state: WelcomeGuideState, about: String): WelcomeGuideState =
        state.copy(about = about)

    fun withSetupMode(state: WelcomeGuideState, mode: WelcomeSetupMode): WelcomeGuideState =
        state.copy(setupMode = mode)

    fun withAiMode(state: WelcomeGuideState, mode: WelcomeAiMode): WelcomeGuideState =
        state.copy(aiMode = mode)

    fun markLocalFailed(state: WelcomeGuideState): WelcomeGuideState =
        state.copy(step = WelcomeStep.LocalDownload, localFailed = true)

    /** 本地模型失败时允许 Walk in，直接进入系统桌面。 */
    fun walkIn(state: WelcomeGuideState): WelcomeGuideState =
        state.copy(step = WelcomeStep.Finished, localFailed = true)

    fun withCloudProvider(state: WelcomeGuideState, provider: String): WelcomeGuideState =
        state.copy(cloudProvider = provider)

    fun isFinished(state: WelcomeGuideState): Boolean =
        state.step == WelcomeStep.Finished
}
