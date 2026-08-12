package com.ares.analytics.ui.controls

import com.ares.analytics.ui.components.controls.advancedBindingSummary
import com.ares.analytics.ui.components.controls.hasAdvancedBindingSettings
import com.areslib.controls.ControlBindingDocument
import com.areslib.controls.ControlEvent
import com.areslib.controls.ControlSourceDocument
import com.areslib.controls.ControlSourceKind
import com.areslib.controls.ControlTargetDocument
import com.areslib.controls.ControlTargetKind
import com.areslib.controls.ControlTimingDocument
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlsEditorPresentationTest {
    private fun binding(timing: ControlTimingDocument = ControlTimingDocument()) = ControlBindingDocument(
        bindingId = "intake",
        displayName = "Run intake",
        source = ControlSourceDocument(ControlSourceKind.BUTTON, "driver", listOf("a")),
        event = ControlEvent.PRESS,
        target = ControlTargetDocument(ControlTargetKind.ACTION, "intake.start"),
        timing = timing
    )

    @Test
    fun `default timing stays collapsed with a plain-language summary`() {
        val binding = binding()

        assertFalse(hasAdvancedBindingSettings(binding))
        assertTrue(advancedBindingSummary(binding).contains("safe defaults"))
    }

    @Test
    fun `non-default safety timing is surfaced automatically`() {
        val binding = binding(ControlTimingDocument(maximumActiveSeconds = 2.0, cooldownSeconds = 0.25))

        assertTrue(hasAdvancedBindingSettings(binding))
        assertTrue(advancedBindingSummary(binding).contains("maximum active time"))
        assertTrue(advancedBindingSummary(binding).contains("cooldown"))
    }
}
