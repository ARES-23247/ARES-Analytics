package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class UiTelemetryFanoutTest {
    @Test
    fun `same-topic bursts become one latest UI update while distinct topics survive`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val fanout = UiTelemetryFanout(scope, frameIntervalMs = 10L)
            val received = mutableListOf<TelemetryFrame>()
            val collector = launch { fanout.updates.take(2).collect(received::add) }

            fanout.offer(frame("Drive/Pose_X", 1.0))
            fanout.offer(frame("Drive/Pose_X", 2.0))
            fanout.offer(frame("Drive/Pose_Y", 3.0))

            withTimeout(1_000L) { collector.join() }
            assertEquals(
                mapOf("Drive/Pose_X" to 2.0, "Drive/Pose_Y" to 3.0),
                received.associate { it.key to it.value },
            )
        } finally {
            scope.cancel()
        }
    }

    private fun frame(key: String, value: Double) = TelemetryFrame(
        timestampMs = 100L,
        sessionId = "test",
        key = key,
        value = value,
    )
}
