package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.League
import com.areslib.auto.AutoDriveStep
import com.areslib.auto.AutoPose
import com.areslib.auto.AutoRoutine
import com.areslib.auto.AutoStep
import com.areslib.math.coordinate.CoordinateTransformers
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoFieldBoundsTest {
    private val squareRobot = RobotDimensions(0.60, 0.60)

    @Test
    fun `FTC pose clamps the whole robot inside center-origin field`() {
        val clamped = clampAutoPose(AutoPose(10.0, -10.0, 0.0), League.FTC, squareRobot)
        val legalMagnitude = CoordinateTransformers.FTC_FIELD_SIZE / 2.0 - 0.30

        assertEquals(legalMagnitude, clamped.xMeters, 1e-9)
        assertEquals(-legalMagnitude, clamped.yMeters, 1e-9)
        assertTrue(isAutoPoseInsideField(clamped, League.FTC, squareRobot))
    }

    @Test
    fun `rotated rectangular robot uses projected corner extent`() {
        val robot = RobotDimensions(lengthMeters = 0.80, widthMeters = 0.40)
        val bounds = legalCenterBounds(League.FTC, robot, Math.PI / 4.0)
        val projectedHalfExtent = sqrt(0.5) * (0.40 + 0.20)

        assertEquals(
            CoordinateTransformers.FTC_FIELD_SIZE / 2.0 - projectedHalfExtent,
            bounds.maxX,
            1e-9
        )
    }

    @Test
    fun `FRC bounds use corner origin`() {
        val clamped = clampAutoPose(AutoPose(-1.0, 100.0, 0.0), League.FRC, squareRobot)

        assertEquals(0.30, clamped.xMeters, 1e-9)
        assertEquals(CoordinateTransformers.FRC_FIELD_WIDTH - 0.30, clamped.yMeters, 1e-9)
    }

    @Test
    fun `validation identifies start and drive footprints outside field`() {
        val routine = AutoRoutine(
            documentId = "boundary-test",
            name = "Boundary test",
            startingPose = AutoPose(2.0, 0.0, 0.0),
            steps = listOf(
                AutoStep.drive(AutoDriveStep(AutoPose(0.0, -2.0, 0.0)))
            )
        )

        val issues = validateAutoFieldBounds(routine, League.FTC, squareRobot)

        assertFalse(issues.isEmpty())
        assertEquals(setOf("startingPose", "steps[0]"), issues.map { it.path }.toSet())
    }
}
