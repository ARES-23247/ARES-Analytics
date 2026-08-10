package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelemetryStoreTest {
    @Test
    fun `indexes canonical topics and isolates subscriptions`() = runTest {
        val store = TelemetryStore()
        store.accept(frame("/Drive/Pose_X", 1_000, 2.5))
        store.accept(frame("Drive/Pose_Y", 1_001, 3.5))

        assertEquals("Drive/Pose_X", store.observe("/Drive/Pose_X").value?.key)
        assertEquals(2.5, store.latest("Drive/Pose_X")?.value)
        assertEquals("Drive/Pose_X", store.observe(setOf("Drive/Pose_X")).first().key)
    }

    @Test
    fun `bounds history by age and frame count`() = runTest {
        val store = TelemetryStore(historyWindowMs = 100, maxFramesPerTopic = 3)
        store.accept(frame("Motor/Current", 0, 0.0))
        store.accept(frame("Motor/Current", 100, 1.0))
        store.accept(frame("Motor/Current", 150, 2.0))
        store.accept(frame("Motor/Current", 175, 3.0))
        store.accept(frame("Motor/Current", 200, 4.0))

        assertEquals(listOf(2.0, 3.0, 4.0), store.history("/Motor/Current").map { it.value })
        assertEquals(3L, store.snapshotMetrics().bufferedFrames)
    }

    @Test
    fun `silent indexing preserves history without notifying topic observers`() = runTest {
        val store = TelemetryStore()
        store.accept(frame("Drive/Pose_X", 1_000, 1.0))
        store.accept(frame("Drive/Pose_X", 1_001, 2.0), notifyConsumers = false)

        assertEquals(2.0, store.latest("Drive/Pose_X")?.value)
        assertEquals(1.0, store.observe("Drive/Pose_X").value?.value)

        store.clear()
        assertNull(store.latest("Drive/Pose_X"))
        assertNull(store.observe("Drive/Pose_X").value)
    }

    private fun frame(key: String, timestampMs: Long, value: Double) = TelemetryFrame(
        timestampMs = timestampMs,
        sessionId = "test",
        key = key,
        value = value
    )
}
