package com.ares.analytics.service.log

import com.ares.analytics.shared.TelemetryFrame

/**
 * Encapsulates topic name mapping, signal normalization, and NT4 framing.
 */
object TelemetryTopicExtractor {

    fun normalizeTopic(key: String): String {
        return when (key) {
            "ARES/EstimatedPose/0" -> "Drive/Pose_X"
            "ARES/EstimatedPose/1" -> "Drive/Pose_Y"
            "ARES/EstimatedPose/2" -> "Drive/Pose_Heading"
            "Drive/Drive_Heading" -> "Drive/Pose_Heading"
            "pinpoint_x", "pinpoint/x" -> "Drive/Odom_X"
            "pinpoint_y", "pinpoint/y" -> "Drive/Odom_Y"
            "pinpoint_heading", "pinpoint/heading" -> "Drive/Odom_Heading"
            "Vision/Pose/X" -> "Vision/Pose_X"
            "Vision/Pose/Y" -> "Vision/Pose_Y"
            "Vision/Pose/Heading" -> "Vision/Pose_Heading"
            "SysId_Data", "sysid_data" -> "SysId/Data"
            else -> key
        }
    }

    fun extractTopics(frame: TelemetryFrame): TelemetryFrame {
        return frame.copy(key = normalizeTopic(frame.key))
    }
}
