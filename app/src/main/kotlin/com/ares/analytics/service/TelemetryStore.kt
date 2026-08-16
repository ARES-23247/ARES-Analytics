package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import com.ares.analytics.shared.TelemetryMetricCatalog
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
    private val maxFramesPerTopic: Int = 2_000,
    private val maxTrackedTopics: Int = 4_096,
) {
    init {
        require(historyWindowMs > 0) { "historyWindowMs must be positive" }
        require(maxFramesPerTopic > 0) { "maxFramesPerTopic must be positive" }
        require(maxTrackedTopics > 0) { "maxTrackedTopics must be positive" }
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
    /** Last frame intentionally published to consumers; silent persistence must not advance it. */
    private val lastNotifiedFrames = ConcurrentHashMap<String, TelemetryFrame>()
    private val trackedTopicOrder = ArrayDeque<String>()
    private val topicIndexLock = Any()

    /** Number of explicitly observed single-topic flows; ingestion alone must not grow this map. */
    internal val topicObserverCount: Int
        get() = topicFlows.size

    private val acceptedFrameCount = AtomicLong()
    private val lastAcceptedAtMs = AtomicLong()

    suspend fun accept(frame: TelemetryFrame, notifyConsumers: Boolean = true): TelemetryFrame {
        val canonicalKey = canonical(frame.key)
        val canonicalFrame = if (canonicalKey == frame.key) frame else frame.copy(key = canonicalKey)
        val observedTopicFlow = synchronized(topicIndexLock) {
            if (!latestFrames.containsKey(canonicalFrame.key)) {
                while (trackedTopicOrder.size >= maxTrackedTopics) {
                    val evictedTopic = trackedTopicOrder.removeFirst()
                    latestFrames.remove(evictedTopic)
                    frameHistory.remove(evictedTopic)
                    lastNotifiedFrames.remove(evictedTopic)
                    // Preserve the observer object so existing collectors remain connected, but
                    // do not leave an evicted value looking current.
                    topicFlows[evictedTopic]?.value = null
                }
                trackedTopicOrder.addLast(canonicalFrame.key)
            } else {
                // Refresh recency so eviction is LRU, not FIFO: without this, core topics
                // discovered at connect were the first candidates for eviction once a long
                // session churned past maxTrackedTopics distinct names.
                trackedTopicOrder.remove(canonicalFrame.key)
                trackedTopicOrder.addLast(canonicalFrame.key)
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
            if (notifyConsumers) {
                lastNotifiedFrames[canonicalFrame.key] = canonicalFrame
                topicFlows[canonicalFrame.key]
            } else {
                null
            }
        }

        acceptedFrameCount.incrementAndGet()
        lastAcceptedAtMs.set(canonicalFrame.timestampMs)
        if (notifyConsumers) {
            observedTopicFlow?.value = canonicalFrame
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
        topicFlows.computeIfAbsent(canonical(topic)) { key -> MutableStateFlow(lastNotifiedFrames[key]) }.asStateFlow()

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
        synchronized(topicIndexLock) {
            latestFrames.clear()
            frameHistory.clear()
            lastNotifiedFrames.clear()
            trackedTopicOrder.clear()
            // Explicit observers survive a session reset so existing UI collectors remain wired.
            topicFlows.values.forEach { it.value = null }
        }
        acceptedFrameCount.set(0)
        lastAcceptedAtMs.set(0)
    }

    private fun canonical(topic: String): String = TelemetryMetricCatalog.normalizeTopic(topic)
}

data class TelemetryStoreMetrics(
    val acceptedFrames: Long,
    val activeTopics: Int,
    val bufferedFrames: Long,
    val lastAcceptedAtMs: Long
)
