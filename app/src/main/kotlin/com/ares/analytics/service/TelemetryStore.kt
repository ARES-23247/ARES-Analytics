package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Single fan-out point for live and replay telemetry.
 *
 * Frames are indexed once at ingestion, then consumers can subscribe to only the topics they
 * render. This avoids every dashboard widget scanning the full NT4 stream independently.
 */
class TelemetryStore(
    private val historyWindowMs: Long = 120_000,
    private val maxFramesPerTopic: Int = 2_000
) {
    init {
        require(historyWindowMs > 0) { "historyWindowMs must be positive" }
        require(maxFramesPerTopic > 0) { "maxFramesPerTopic must be positive" }
    }

    private val mutableUpdates = MutableSharedFlow<TelemetryFrame>(
        replay = 100,
        extraBufferCapacity = 4_096,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val updates: SharedFlow<TelemetryFrame> = mutableUpdates.asSharedFlow()

    private val topicFlows = ConcurrentHashMap<String, MutableStateFlow<TelemetryFrame?>>()
    internal val latestFrames = ConcurrentHashMap<String, TelemetryFrame>()
    internal val frameHistory = ConcurrentHashMap<String, ArrayDeque<TelemetryFrame>>()

    private val acceptedFrameCount = AtomicLong()
    private val lastAcceptedAtMs = AtomicLong()

    suspend fun accept(frame: TelemetryFrame, notifyConsumers: Boolean = true): TelemetryFrame {
        val canonicalFrame = if (frame.key.startsWith('/')) {
            frame.copy(key = frame.key.removePrefix("/"))
        } else {
            frame
        }
        latestFrames[canonicalFrame.key] = canonicalFrame

        val history = frameHistory.computeIfAbsent(canonicalFrame.key) { ArrayDeque() }
        synchronized(history) {
            history.addLast(canonicalFrame)
            val cutoff = canonicalFrame.timestampMs - historyWindowMs
            while (history.isNotEmpty() && history.first.timestampMs < cutoff) {
                history.removeFirst()
            }
            while (history.size > maxFramesPerTopic) {
                history.removeFirst()
            }
        }

        acceptedFrameCount.incrementAndGet()
        lastAcceptedAtMs.set(canonicalFrame.timestampMs)
        if (notifyConsumers) {
            topicFlows.computeIfAbsent(canonicalFrame.key) { MutableStateFlow(null) }.value = canonicalFrame
            mutableUpdates.emit(canonicalFrame)
        }
        return canonicalFrame
    }

    fun latest(topic: String): TelemetryFrame? = latestFrames[canonical(topic)]

    fun history(topic: String): List<TelemetryFrame> {
        val history = frameHistory[canonical(topic)] ?: return emptyList()
        return synchronized(history) { history.toList() }
    }

    fun observe(topic: String): StateFlow<TelemetryFrame?> =
        topicFlows.computeIfAbsent(canonical(topic)) { MutableStateFlow(latest(topic)) }.asStateFlow()

    fun observe(topics: Set<String>): Flow<TelemetryFrame> {
        val canonicalTopics = topics.mapTo(HashSet(topics.size)) { canonical(it) }
        return updates.filter { it.key in canonicalTopics }
    }

    fun snapshotMetrics(): TelemetryStoreMetrics = TelemetryStoreMetrics(
        acceptedFrames = acceptedFrameCount.get(),
        activeTopics = latestFrames.size,
        bufferedFrames = frameHistory.values.sumOf { history -> synchronized(history) { history.size.toLong() } },
        lastAcceptedAtMs = lastAcceptedAtMs.get()
    )

    fun clear() {
        latestFrames.clear()
        frameHistory.clear()
        topicFlows.values.forEach { it.value = null }
        acceptedFrameCount.set(0)
        lastAcceptedAtMs.set(0)
    }

    private fun canonical(topic: String): String = topic.removePrefix("/")
}

data class TelemetryStoreMetrics(
    val acceptedFrames: Long,
    val activeTopics: Int,
    val bufferedFrames: Long,
    val lastAcceptedAtMs: Long
)
