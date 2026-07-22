package com.ares.analytics.service.log

import com.ares.analytics.shared.TelemetryFrame

/**
 * Encapsulates topic name mapping, signal normalization, and NT4 framing.
 */
class TelemetryTopicExtractor {

    /**

     * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

     *

     */
    fun extractTopics(frame: TelemetryFrame): TelemetryFrame {
        // TODO: Normalize topics, handle NetworkTables 4.0 mapping, etc.
        return frame
    }
}
