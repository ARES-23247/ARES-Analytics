package com.ares.analytics.service

import com.ares.analytics.shared.GoalEndState
import com.ares.analytics.shared.IdealStartingState
import com.ares.analytics.shared.PathConstraints
import com.ares.analytics.ui.components.pathplanner.Waypoint
import kotlin.test.Test
import kotlin.test.assertTrue

class TrajectoryEstimatorTest {
    @Test
    fun `trajectory duration respects angular velocity constraint`() {
        val trajectory = TrajectoryEstimator.generateTrajectory(
            waypoints = listOf(
                Waypoint(0.0, 0.0, rotationDeg = 0.0),
                Waypoint(1.0, 0.0, rotationDeg = 180.0)
            ),
            globalConstraints = PathConstraints(
                maxVelocity = 10.0,
                maxAcceleration = 10.0,
                maxAngularVelocity = 30.0,
                maxAngularAcceleration = 360.0
            ),
            constraintZones = emptyList(),
            rotationTargets = emptyList(),
            idealStartingState = IdealStartingState(rotation = 0.0),
            goalEndState = GoalEndState(rotation = 180.0)
        )

        // The shortest 0°→180° rotation at 30°/s requires at least six seconds,
        // regardless of how quickly the one-metre translation can complete.
        assertTrue(trajectory.durationSeconds >= 6.0 - 1e-6, "duration=${trajectory.durationSeconds}")
    }
}
