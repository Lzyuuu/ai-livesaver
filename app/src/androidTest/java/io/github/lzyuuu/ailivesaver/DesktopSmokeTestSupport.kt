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
