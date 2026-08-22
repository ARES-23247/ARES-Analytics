package com.ares.analytics.ui.components.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalSimulatorControlBarTest {
    @Test
    fun `normal mecanum TeleOp is preferred regardless of announcement order`() {
        val teleOps = listOf(
            "org.firstinspires.ftc.teamcode.opmodes.NullOpMode",
            "org.firstinspires.ftc.teamcode.opmodes.ARESMecanumTeleOp",
            "org.firstinspires.ftc.teamcode.opmodes.ARESRemoteDriveOpMode",
        )

        assertEquals(teleOps[1], preferredSimulatorTeleOp(teleOps))
    }

    @Test
    fun `remote drive is the fallback when normal mecanum TeleOp is absent`() {
        val teleOps = listOf(
            "org.firstinspires.ftc.teamcode.opmodes.NullOpMode",
            "org.firstinspires.ftc.teamcode.opmodes.ARESRemoteDriveOpMode",
        )

        assertEquals(teleOps[1], preferredSimulatorTeleOp(teleOps))
        assertNull(preferredSimulatorTeleOp(emptyList()))
    }

    @Test
    fun `malformed Driver Station inventory cannot crash the dashboard`() {
        assertEquals(emptyList(), decodeSimulatorTeleOps("not-json"))
        assertEquals(listOf("One", "Two"), decodeSimulatorTeleOps("[\"One\",\"Two\"]"))
    }

    @Test
    fun `offline primary action launches the simulator instead of offering disabled drive`() {
        assertEquals(
            LocalSimulatorPrimaryAction.LAUNCH_SIMULATOR,
            localSimulatorPrimaryAction(
                isConnected = false,
                isSimulatorProcessRunning = false,
                isLaunchPreparationRunning = false,
                launchRequiresVerification = false,
                isTeleOpStarting = false,
                isTeleOpRunning = false,
            ),
        )
    }

    @Test
    fun `managed simulator launch waits for NT4 before offering TeleOp controls`() {
        assertEquals(
            LocalSimulatorPrimaryAction.WAIT_FOR_CONNECTION,
            localSimulatorPrimaryAction(
                isConnected = false,
                isSimulatorProcessRunning = true,
                isLaunchPreparationRunning = false,
                launchRequiresVerification = false,
                isTeleOpStarting = false,
                isTeleOpRunning = false,
            ),
        )
        assertEquals(
            LocalSimulatorPrimaryAction.START_DRIVING,
            localSimulatorPrimaryAction(
                isConnected = true,
                isSimulatorProcessRunning = true,
                isLaunchPreparationRunning = false,
                launchRequiresVerification = false,
                isTeleOpStarting = false,
                isTeleOpRunning = false,
            ),
        )
    }

    @Test
    fun `connected primary action reports TeleOp transitions`() {
        assertEquals(
            LocalSimulatorPrimaryAction.STARTING_TELEOP,
            localSimulatorPrimaryAction(
                isConnected = true,
                isSimulatorProcessRunning = true,
                isLaunchPreparationRunning = false,
                launchRequiresVerification = false,
                isTeleOpStarting = true,
                isTeleOpRunning = false,
            ),
        )
        assertEquals(
            LocalSimulatorPrimaryAction.TELEOP_RUNNING,
            localSimulatorPrimaryAction(
                isConnected = true,
                isSimulatorProcessRunning = true,
                isLaunchPreparationRunning = false,
                launchRequiresVerification = false,
                isTeleOpStarting = false,
                isTeleOpRunning = true,
            ),
        )
    }

    @Test
    fun `fresh session offers verification and launch as one visible workflow`() {
        assertEquals(
            LocalSimulatorPrimaryAction.VERIFY_AND_LAUNCH,
            localSimulatorPrimaryAction(
                isConnected = false,
                isSimulatorProcessRunning = false,
                isLaunchPreparationRunning = false,
                launchRequiresVerification = true,
                isTeleOpStarting = false,
                isTeleOpRunning = false,
            ),
        )
        assertEquals(
            LocalSimulatorPrimaryAction.VERIFYING_PROJECT,
            localSimulatorPrimaryAction(
                isConnected = false,
                isSimulatorProcessRunning = false,
                isLaunchPreparationRunning = true,
                launchRequiresVerification = true,
                isTeleOpStarting = false,
                isTeleOpRunning = false,
            ),
        )
        assertEquals("Building simulator", LocalSimulatorPrimaryAction.VERIFYING_PROJECT.label)
    }

    @Test
    fun `fresh valid project requests verification before simulator process`() {
        assertEquals(
            LocalSimulatorLaunchRequest.VERIFY_THEN_START,
            localSimulatorLaunchRequest(
                canRunSimulation = false,
                canRunBuild = true,
                isBuildRunning = false,
                isSimulatorRunning = false,
                isSimulatorOnline = false,
                isLaunchPending = false,
            ),
        )
        assertEquals(
            LocalSimulatorLaunchRequest.START_SIMULATOR,
            localSimulatorLaunchRequest(
                canRunSimulation = true,
                canRunBuild = true,
                isBuildRunning = false,
                isSimulatorRunning = false,
                isSimulatorOnline = false,
                isLaunchPending = false,
            ),
        )
    }

    @Test
    fun `launch request cannot duplicate an active build or simulator`() {
        assertEquals(
            LocalSimulatorLaunchRequest.NONE,
            localSimulatorLaunchRequest(
                canRunSimulation = true,
                canRunBuild = true,
                isBuildRunning = true,
                isSimulatorRunning = false,
                isSimulatorOnline = false,
                isLaunchPending = true,
            ),
        )
        assertEquals(
            LocalSimulatorLaunchRequest.NONE,
            localSimulatorLaunchRequest(
                canRunSimulation = true,
                canRunBuild = true,
                isBuildRunning = false,
                isSimulatorRunning = false,
                isSimulatorOnline = true,
                isLaunchPending = false,
            ),
        )
    }
}
