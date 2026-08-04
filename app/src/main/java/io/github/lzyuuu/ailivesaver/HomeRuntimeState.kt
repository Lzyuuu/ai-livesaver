package io.github.lzyuuu.ailivesaver

/** Honest status shown by the desktop root card. No local engine is claimed unless one exists. */
enum class HomeRuntimeState { UNCONFIGURED, CLOUD_READY, LOCAL_READY, FAULT }

data class HomeRuntimePresentation(val status: String, val actionLabel: String, val action: HomeAction)
enum class HomeAction { CONFIGURE_PROVIDER, OPEN_MESSENGER, OPEN_RUNTIME }

internal fun homeRuntimePresentation(state: HomeRuntimeState): HomeRuntimePresentation = when (state) {
    HomeRuntimeState.UNCONFIGURED -> HomeRuntimePresentation("还没有连接 Chat brain", "连接 Provider", HomeAction.CONFIGURE_PROVIDER)
    HomeRuntimeState.CLOUD_READY -> HomeRuntimePresentation("云端 Chat brain 已连接", "打开 Messenger", HomeAction.OPEN_MESSENGER)
    HomeRuntimeState.LOCAL_READY -> HomeRuntimePresentation("本地 Chat brain 已就绪", "打开 Messenger", HomeAction.OPEN_MESSENGER)
    HomeRuntimeState.FAULT -> HomeRuntimePresentation("Chat brain 连接异常", "检查运行状态", HomeAction.OPEN_RUNTIME)
}

internal fun homeRuntimeState(config: ProviderConfig?, localReady: Boolean, fault: Boolean): HomeRuntimeState = when {
    fault -> HomeRuntimeState.FAULT
    config == null || !config.isValid() -> HomeRuntimeState.UNCONFIGURED
    localReady -> HomeRuntimeState.LOCAL_READY
    else -> HomeRuntimeState.CLOUD_READY
}
