package io.github.lzyuuu.ailivesaver

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Seeds first-launch state before the Compose activity rule starts MainActivity. */
internal class SeededMainActivityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            TestDevicePreparation.prepare(instrumentation, context)
            seedDesktopShellForSmoke(context)
            base.evaluate()
        }
    }
}
