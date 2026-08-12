package com.ares.analytics.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class AresThemeTest {
    @Test
    fun `large text preserves and multiplies the system font scale`() {
        assertEquals(1.25f, effectiveAresFontScale(1.25f, false))
        assertEquals(1.475f, effectiveAresFontScale(1.25f, true), 0.0001f)
    }
}
