package com.ares.analytics.ui.components.dashboard

import kotlin.test.Test

class DashboardWidgetGridBytecodeTest {
    @Test
    fun generatedDashboardGridLambdaIsValidJvmBytecode() {
        // A labeled return from the Compose key lambda previously produced an invalid
        // $$$$$NON_LOCAL_RETURN$$$$$.<anonymous> method reference. Compilation passed,
        // but the JVM threw ClassFormatError when the dashboard first rendered.
        Class.forName(
            "com.ares.analytics.ui.components.dashboard.DashboardWidgetGridKt\$DashboardWidgetGrid\$1",
            true,
            javaClass.classLoader
        )
    }
}
