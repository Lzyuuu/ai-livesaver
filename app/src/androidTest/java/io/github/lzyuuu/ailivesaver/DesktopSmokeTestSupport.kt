package io.github.lzyuuu.ailivesaver

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule

/**
 * Isolates MainActivitySmokeTest imaging assertions from #28 connected tests
 * that persist a non-default Imaging Studio backend on the shared emulator.
 */
internal fun resetImagingStudioOnDeviceForSmoke(context: Context) {
    context.getSharedPreferences("imaging_studio", Context.MODE_PRIVATE)
        .edit()
        .putString("backend", "on_device")
        .apply()
}

internal fun grantPostNotificationIfNeeded(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
            .close()
    }
}

internal fun seedDesktopShellForSmoke(context: Context) {
    writeWelcomeGuideCompleted(context, true)
    resetImagingStudioOnDeviceForSmoke(context)
    WorldStore(context).use { store ->
        DesktopSeed.ensureDesktopWorld(
            store,
            userName = "焰宇",
            about = "smoke",
            context = context,
        )
    }
}

/** 为商店门控改造预装目录 App（T4）：先清空 app_install，再种子指定集合，保证确定性。 */
internal fun seedStoreInstallForSmoke(
    context: Context,
    appIds: Set<String>,
    onHome: Boolean = false,
) {
    val now = System.currentTimeMillis()
    WorldStore(context).use { store ->
        store.writableDatabase.delete("app_install", null, null)
        appIds.forEachIndexed { index, id ->
            store.saveAppInstall(
                PersistedAppInstall(
                    appId = id,
                    status = InstallStatus.INSTALLED,
                    installedAt = now,
                    onHome = onHome,
                    homeOrder = if (onHome) index + 1 else 0,
                    catalogVersion = "1.0",
                    updatedAt = now,
                ),
            )
        }
    }
}

/**
 * 将目录 App 标记为 on_home，供桌面网格门控/长按测试使用。
 * [installed] 为 false 时仅写首页状态，不标记为已安装。
 */
internal fun seedHomeGridForSmoke(
    context: Context,
    appIds: List<String>,
    installed: Boolean = true,
) {
    val now = System.currentTimeMillis()
    WorldStore(context).use { store ->
        appIds.forEachIndexed { index, id ->
            val existing = store.loadAppInstall(id)
            store.saveAppInstall(
                PersistedAppInstall(
                    appId = id,
                    status = if (installed) {
                        existing?.status ?: InstallStatus.INSTALLED
                    } else {
                        InstallStatus.NOT_INSTALLED
                    },
                    installedAt = if (installed) existing?.installedAt ?: now else null,
                    onHome = true,
                    homeOrder = index + 1,
                    catalogVersion = existing?.catalogVersion ?: "1.0",
                    updatedAt = now,
                ),
            )
        }
    }
}

/** 安装并添加到首页，供桌面网格冒烟测试使用。 */
internal fun seedInstallOnHomeForSmoke(context: Context, appIds: List<String>) {
    seedStoreInstallForSmoke(context, appIds.toSet(), onHome = true)
    seedHomeGridForSmoke(context, appIds, installed = true)
}

/** @see seedInstallOnHomeForSmoke */
internal fun seedHomeAppsForSmoke(context: Context, appIds: List<String>) {
    seedInstallOnHomeForSmoke(context, appIds)
}

internal fun openDesktopAppFromGrid(
    composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
    route: String,
) {
    composeRule.onNodeWithTag("desktop-grid-$route").performClick()
    composeRule.waitForIdle()
}

/** 将已安装 App 标记为 on_home，供系统桌面网格冒烟测试使用。 */
internal fun seedHomeDesktopAppsForSmoke(context: Context, appIds: Set<String>) {
    val now = System.currentTimeMillis()
    WorldStore(context).use { store ->
        appIds.forEachIndexed { index, id ->
            val existing = store.loadAppInstall(id)
            store.saveAppInstall(
                PersistedAppInstall(
                    appId = id,
                    status = existing?.status ?: InstallStatus.INSTALLED,
                    installedAt = existing?.installedAt ?: now,
                    onHome = true,
                    homeOrder = index + 1,
                    catalogVersion = existing?.catalogVersion ?: "1.0",
                    updatedAt = now,
                ),
            )
        }
    }
}

internal fun clearMomentPostsForSmoke(context: Context) {
    WorldStore(context).use { store ->
        store.clearPosts("moment")
    }
}
