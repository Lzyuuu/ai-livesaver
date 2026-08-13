package io.github.lzyuuu.ailivesaver

import android.content.Context

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
internal fun seedStoreInstallForSmoke(context: Context, appIds: Set<String>) {
    val now = System.currentTimeMillis()
    WorldStore(context).use { store ->
        store.writableDatabase.delete("app_install", null, null)
        appIds.forEach { id ->
            store.saveAppInstall(
                PersistedAppInstall(
                    appId = id,
                    status = InstallStatus.INSTALLED,
                    installedAt = now,
                    onHome = false,
                    homeOrder = 0,
                    catalogVersion = "1.0",
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
