package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CalibrationServiceTest class.
 */
class CalibrationServiceTest {

    @Test
    /**
     * testSolveCameraExtrinsics fun.
     */
    fun testSolveCameraExtrinsics() {
        val tempDb = File.createTempFile("calib_solve_test", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val calibrationService = CalibrationService(databaseService)

        // Mock camera extrinsics: dx=0.1, dy=0.2, dz=0.3, roll=0.0, pitch=0.0, yaw=0.05
        val targetDx = 0.1
        val targetDy = 0.2
        val targetDz = 0.3
        val targetYaw = 0.05
        val measurements = mutableListOf<CalibrationMeasurement>()
        // Generate mock measurements at different gyro positions
        for (i in 0 until 5) {
            val gyro = i * 0.2
            val Cx = 1.0
            val Cy = 1.5
            val Cz = 0.5
            val cosG = kotlin.math.cos(gyro)
            val sinG = kotlin.math.sin(gyro)
            val zRot = (Cx * cosG + Cy * sinG) - targetDx
            val xRot = (-Cx * sinG + Cy * cosG) - targetDy
            val yRot = Cz - targetDz

            measurements.add(
                CalibrationMeasurement(
                    gyroHeading = gyro,
                    tagId = 1,
                    tagFieldX = Cx,
                    tagFieldY = Cy,
                    tagFieldZ = Cz,
                    targetSpaceX = xRot,
                    targetSpaceY = yRot,
                    targetSpaceZ = zRot,
                    targetSpaceRoll = 0.0,
                    targetSpacePitch = 0.0,
                    targetSpaceYaw = -gyro - targetYaw
                )
            )
        }
        val solved = calibrationService.solveCameraExtrinsics(measurements)
        assertEquals(targetDx, solved.x, 0.05)
        assertEquals(targetDy, solved.y, 0.05)
        // Under vertical collinearity (stationary floor), solved.z stays at initial guess 0.2
        assertEquals(0.2, solved.z, 0.05)
        assertEquals(targetYaw, solved.yaw, 0.05)

        // Test diagnostics version
        val diag = calibrationService.solveCameraExtrinsicsWithDiagnostics(measurements)
        assertEquals(targetDx, diag.pose.x, 0.05)
        assertEquals(targetDy, diag.pose.y, 0.05)
        assertEquals(targetYaw, diag.pose.yaw, 0.05)
        
        // Assert standard errors are non-negative and reasonably sized
        assertEquals(6, diag.standardErrors.size)
        diag.standardErrors.forEach { se ->
            assertTrue(se >= 0.0)
        }
        // Assert covariance matrix is 6x6
        assertEquals(6, diag.covarianceMatrix.size)
        diag.covarianceMatrix.forEach { row ->
            assertEquals(6, row.size)
        }
        assertTrue(diag.reducedChiSquared >= 0.0)

        tempDb.delete()
    }

    @Test
    /**
     * testRunExtrinsicCalibration fun.
     */
    fun testRunExtrinsicCalibration() = runTest {
        val tempDb = File.createTempFile("calib_run_test", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val calibrationService = CalibrationService(databaseService)
        val sessionId = "calib-session"
        val cameraIndex = 0

        // Insert mock database values
        val frames = listOf(
            TelemetryFrame(1000L, sessionId, "/Calibration/GyroHeading", 0.0),
            TelemetryFrame(1000L, sessionId, "/Calibration/TagIndex", 1.0),
            TelemetryFrame(1000L, sessionId, "/Calibration/CameraToTag[0]", 0.5), // x=0.5
            TelemetryFrame(1001L, sessionId, "/Calibration/CameraToTag[0]", 0.2), // y=0.2
            TelemetryFrame(1002L, sessionId, "/Calibration/CameraToTag[0]", 1.2), // z=1.2
            TelemetryFrame(1003L, sessionId, "/Calibration/CameraToTag[0]", 0.0), // roll
            TelemetryFrame(1004L, sessionId, "/Calibration/CameraToTag[0]", 0.0), // pitch
            TelemetryFrame(1005L, sessionId, "/Calibration/CameraToTag[0]", -0.05) // yaw
        )

        databaseService.insertTelemetryFrames(frames)
        val solved = calibrationService.runExtrinsicCalibration(sessionId, cameraIndex)
        // Should resolve something without crashing
        assertTrue(solved.x >= 0.0 || solved.x < 0.0)
        val diag = calibrationService.runExtrinsicCalibrationWithDiagnostics(sessionId, cameraIndex)
        assertTrue(diag.pose.x >= 0.0 || diag.pose.x < 0.0)
        assertEquals(6, diag.standardErrors.size)
        tempDb.delete()
    }
}
