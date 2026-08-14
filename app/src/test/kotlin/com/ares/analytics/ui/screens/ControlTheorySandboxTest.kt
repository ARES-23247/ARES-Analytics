package com.ares.analytics.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ControlTheorySandboxTest {

    @Test
    fun testFlywheelVelocityStepResponse() {
        val (trajectory, metrics) = simulateStepResponse(
            plant = MechanismPlantKind.FLYWHEEL,
            strategy = SandboxControllerStrategy.PID_FEEDFORWARD,
            kp = 6.0,
            ki = 0.5,
            kd = 0.1,
            ks = 0.05,
            kv = 1.0,
            ka = 0.0,
            kg = 0.0
        )

        assertTrue(metrics.isStable, "Simulation should remain stable with tuned PIDF gains")
        assertTrue(trajectory.isNotEmpty(), "Trajectory points should be populated")
        assertNotNull(metrics.riseTimeSec, "Rise time should be recorded")
        assertTrue(metrics.riseTimeSec!! > 0.0 && metrics.riseTimeSec!! < 1.0, "Flywheel rise time should be under 1 second")
        assertTrue(metrics.steadyStateError < 0.10, "Steady state error should be low under feedforward + integral action")
    }

    @Test
    fun testArmGravityCompensation() {
        // Without gravity compensation (kg = 0), proportional feedback will have steady-state error due to gravity sag
        val (_, uncompensatedMetrics) = simulateStepResponse(
            plant = MechanismPlantKind.ARM,
            strategy = SandboxControllerStrategy.PID_FEEDFORWARD,
            kp = 3.0,
            ki = 0.0,
            kd = 0.2,
            ks = 0.0,
            kv = 0.0,
            ka = 0.0,
            kg = 0.0
        )

        // With gravity compensation (kg = 2.5), arm holds target angle
        val (_, compensatedMetrics) = simulateStepResponse(
            plant = MechanismPlantKind.ARM,
            strategy = SandboxControllerStrategy.PID_FEEDFORWARD,
            kp = 3.0,
            ki = 0.0,
            kd = 0.2,
            ks = 0.0,
            kv = 0.0,
            ka = 0.0,
            kg = 2.5
        )

        assertTrue(
            compensatedMetrics.steadyStateError < uncompensatedMetrics.steadyStateError,
            "Gravity feedforward should significantly reduce steady-state error on arm mechanism"
        )
    }

    @Test
    fun testElevatorStepResponseStability() {
        val (trajectory, metrics) = simulateStepResponse(
            plant = MechanismPlantKind.ELEVATOR,
            strategy = SandboxControllerStrategy.PID_FEEDFORWARD,
            kp = 12.0,
            ki = 0.5,
            kd = 1.5,
            ks = 0.05,
            kv = 0.5,
            ka = 0.0,
            kg = 1.12
        )

        assertTrue(metrics.isStable, "Elevator simulation should be stable")
        assertEquals(201, trajectory.size, "Should have 201 samples over 2 seconds at dt=0.01")
        assertTrue(metrics.steadyStateError < 0.15, "Steady state error should be bounded with gravity feedforward")
    }

    @Test
    fun testAdrcDisturbanceRejection() {
        val (trajectory, metrics) = simulateStepResponse(
            plant = MechanismPlantKind.FLYWHEEL,
            strategy = SandboxControllerStrategy.LINEAR_ADRC,
            kp = 0.0,
            ki = 0.0,
            kd = 0.0,
            ks = 0.0,
            kv = 0.0,
            ka = 0.0,
            kg = 0.0,
            b0 = 4.8,
            omegaO = 25.0,
            omegaC = 10.0
        )

        assertTrue(metrics.isStable, "Linear ADRC should remain stable")
        assertTrue(trajectory.isNotEmpty(), "ADRC should generate trajectory")
        assertNotNull(metrics.riseTimeSec, "Rise time should be recorded")
        assertTrue(metrics.steadyStateError < 0.05, "ADRC extended state observer should eliminate steady-state error without kI")
    }
}
