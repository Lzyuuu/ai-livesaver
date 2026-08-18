package io.github.lzyuuu.ailivesaver

import android.app.Instrumentation
import android.content.Context

/** Shared instrumentation setup for smoke tests that need a seeded desktop shell. */
internal object TestDevicePreparation {
    fun prepare(instrumentation: Instrumentation, context: Context) {
        grantPostNotificationIfNeeded(context)
    }
}
