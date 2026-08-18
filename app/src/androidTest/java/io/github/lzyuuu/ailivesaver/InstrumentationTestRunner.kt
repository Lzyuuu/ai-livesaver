package io.github.lzyuuu.ailivesaver

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Seeds welcome-guide completion and a deterministic desktop world before the
 * activity under test starts, so instrumentation does not depend on manual setup.
 */
class InstrumentationTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context): Application {
        seedDesktopShellForSmoke(context)
        return super.newApplication(cl, className, context)
    }
}
