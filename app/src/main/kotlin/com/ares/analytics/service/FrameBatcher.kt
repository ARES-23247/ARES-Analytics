package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame

/**
 * Accumulates [TelemetryFrame] objects and auto-flushes them to the database
 * in constant-size batches (default 5,000) to maintain bounded heap usage
 * during log imports. Tracks min/max timestamps incrementally so session
 * duration can be computed without holding the full frame list in memory.
 *
 * @param databaseService The database service to flush batches into.
 * @param batchSize Number of frames to accumulate before auto-flushing.
 * @param keyTransform Optional transformation applied to every frame's key
 *   before it is added to the batch (e.g. prepending `/Log0/` for multi-file imports).
 */
class FrameBatcher(
    private val databaseService: DatabaseService,
    private val batchSize: Int = 50_000,
    private val keyTransform: ((String) -> String)? = null
) {
    private val buffer = mutableListOf<TelemetryFrame>()

    /** Earliest timestamp observed across all frames added to this batcher. */
    var minTimestamp: Long = Long.MAX_VALUE
        private set

    /** Latest timestamp observed across all frames added to this batcher. */
    var maxTimestamp: Long = Long.MIN_VALUE
        private set

    /** Total number of frames that have been flushed + those still in the buffer. */
    val frameCount: Int get() = totalFlushed + buffer.size

    private var totalFlushed = 0

    /**
     * Adds a single frame to the internal buffer. If the buffer reaches
     * [batchSize], the batch is automatically flushed to the database.
     */
    suspend fun add(frame: TelemetryFrame) {
        if (frame.timestampMs < minTimestamp) minTimestamp = frame.timestampMs
        if (frame.timestampMs > maxTimestamp) maxTimestamp = frame.timestampMs

        val finalFrame = if (keyTransform != null) {
            frame.copy(key = keyTransform.invoke(frame.key))
        } else {
            frame
        }
        buffer.add(finalFrame)

        if (buffer.size >= batchSize) {
            flush()
        }
    }

    /**
     * Flushes any remaining frames in the buffer to the database.
     * Must be called after parsing completes to ensure no frames are lost.
     */
    suspend fun flush() {
        if (buffer.isNotEmpty()) {
            databaseService.insertTelemetryFrames(buffer.toList())
            totalFlushed += buffer.size
            buffer.clear()
        }
    }
}
