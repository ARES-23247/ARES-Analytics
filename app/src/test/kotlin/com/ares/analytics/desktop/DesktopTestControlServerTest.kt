package com.ares.analytics.desktop

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopTestControlServerTest {
    @Test
    fun `parses supported visible UI commands`() {
        assertEquals(DesktopTestCommand.Click(12, 34), DesktopTestCommandParser.parse("CLICK 12 34"))
        assertEquals(DesktopTestCommand.Key(65, 128), DesktopTestCommandParser.parse("KEY 65 128"))
        assertEquals(DesktopTestCommand.KeyDown(87, 0), DesktopTestCommandParser.parse("KEY_DOWN 87"))
        assertEquals(DesktopTestCommand.KeyUp(87, 0), DesktopTestCommandParser.parse("KEY_UP 87 0"))
        assertEquals(DesktopTestCommand.Capture, DesktopTestCommandParser.parse("CAPTURE"))
        assertEquals(DesktopTestCommand.Ping, DesktopTestCommandParser.parse("PING"))

        val encoded = Base64.getEncoder().encodeToString("Robot π".toByteArray(StandardCharsets.UTF_8))
        assertEquals(DesktopTestCommand.Text("Robot π"), DesktopTestCommandParser.parse("TEXT $encoded"))
    }

    @Test
    fun `rejects malformed or unknown commands`() {
        assertFailsWith<IllegalArgumentException> { DesktopTestCommandParser.parse("CLICK 12") }
        assertFailsWith<IllegalArgumentException> { DesktopTestCommandParser.parse("KEY") }
        assertFailsWith<IllegalStateException> { DesktopTestCommandParser.parse("DELETE EVERYTHING") }
    }
}
