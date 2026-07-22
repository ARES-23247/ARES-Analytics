package com.ares.analytics.service

import com.ares.analytics.service.calibration.CameraCalibrationSolver
import com.ares.analytics.service.calibration.OdometryCalibrationSolver

data class Pose3d(
    val x: Double, // Left-Right (meters)
    val y: Double, // Up-Down (meters)
    val z: Double, // Depth (meters)
    val roll: Double,
    val pitch: Double,
    val yaw: Double // Heading (radians, CCW-positive)
)

data class CalibrationDiagnostics(
    val pose: Pose3d,
    val standardErrors: DoubleArray,
    val covarianceMatrix: Array<DoubleArray>,
    val reducedChiSquared: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CalibrationDiagnostics
        if (pose != other.pose) return false
        if (!standardErrors.contentEquals(other.standardErrors)) return false
        if (!covarianceMatrix.contentDeepEquals(other.covarianceMatrix)) return false
        if (reducedChiSquared != other.reducedChiSquared) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pose.hashCode()
        result = 31 * result + standardErrors.contentHashCode()
        result = 31 * result + covarianceMatrix.contentDeepHashCode()
        result = 31 * result + reducedChiSquared.hashCode()
        return result
    }
}

data class CalibrationMeasurement(
    val gyroHeading: Double, // radians (CCW-positive)
    val tagId: Int,
    val tagFieldX: Double,
    val tagFieldY: Double,
    val tagFieldZ: Double,
    // Tag relative target space measurements from Limelight
    val targetSpaceX: Double,
    val targetSpaceY: Double,
    val targetSpaceZ: Double,
    val targetSpaceRoll: Double,
    val targetSpacePitch: Double,
    val targetSpaceYaw: Double
)

class CalibrationService(private val databaseService: DatabaseService) {

    private val cameraSolver = CameraCalibrationSolver(databaseService)
    private val odometrySolver = OdometryCalibrationSolver(databaseService)

    /**
     * Solves for the 6-DOF camera extrinsic calibration offset using a Levenberg-Marquardt approach
     */
    fun solveCameraExtrinsics(measurements: List<CalibrationMeasurement>): Pose3d {
        return cameraSolver.solveCameraExtrinsics(measurements)
    }

    /**
     * Scans NT4 telemetry from a calibration run to pull measurements and run the solver.
     */
    suspend fun runExtrinsicCalibration(
        sessionId: String,
        cameraIndex: Int
    ): Pose3d {
        return cameraSolver.runExtrinsicCalibration(sessionId, cameraIndex)
    }

    fun solveCameraExtrinsicsWithDiagnostics(measurements: List<CalibrationMeasurement>): CalibrationDiagnostics {
        return cameraSolver.solveCameraExtrinsicsWithDiagnostics(measurements)
    }

    suspend fun runExtrinsicCalibrationWithDiagnostics(
        sessionId: String,
        cameraIndex: Int
    ): CalibrationDiagnostics {
        return cameraSolver.runExtrinsicCalibrationWithDiagnostics(sessionId, cameraIndex)
    }
}

data class FieldTag(val x: Double, val y: Double, val z: Double)
