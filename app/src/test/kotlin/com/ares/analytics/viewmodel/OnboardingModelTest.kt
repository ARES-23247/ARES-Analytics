package com.ares.analytics.viewmodel

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingModelTest {
    @Test
    fun `JDK 17 output is accepted with concise readiness message`() {
        val result = evaluateJava17(
            commandSucceeded = true,
            rawMessage = "Java executable valid. Output:\nopenjdk version \"17.0.12\" 2024-07-16",
        )

        assertTrue(result.isValid)
        assertEquals(17, result.majorVersion)
        assertEquals("JDK 17 is ready.", result.message)
    }

    @Test
    fun `a runnable non-17 Java is rejected`() {
        val result = evaluateJava17(
            commandSucceeded = true,
            rawMessage = "java version \"21.0.2\" 2024-01-16 LTS",
        )

        assertFalse(result.isValid)
        assertEquals(21, result.majorVersion)
        assertEquals(
            "JDK 17 is required. We found Java 21. Set JAVA_HOME to a JDK 17 installation, then check again.",
            result.message,
        )
    }

    @Test
    fun `legacy Java version syntax is parsed correctly`() {
        assertEquals(8, parseJavaMajorVersion("java version \"1.8.0_402\""))
        assertNull(parseJavaMajorVersion("Java executable valid but returned no version text"))
    }

    @Test
    fun `project step reports only its own field error`() {
        val errors = validateOnboardingFields(OnboardingState(), OnboardingStep.PROJECT)

        assertEquals("Choose your robot project folder.", errors.projectPath)
        assertNull(errors.teamId)
        assertNull(errors.seasonId)
        assertNull(errors.robotId)
    }

    @Test
    fun `robot step has field-specific errors and accepts a real project folder`() {
        val directory = Files.createTempDirectory("ares-onboarding-test").toFile()
        try {
            val errors = validateOnboardingFields(
                OnboardingState(
                    projectPath = directory.absolutePath,
                    teamId = "23A47",
                    seasonId = "",
                    robotId = "",
                ),
                OnboardingStep.ROBOT,
            )

            assertNull(errors.projectPath)
            assertEquals("Use numbers only for the team number.", errors.teamId)
            assertEquals("Enter the season, for example 2026.", errors.seasonId)
            assertEquals("Enter a short robot ID.", errors.robotId)
        } finally {
            directory.delete()
        }
    }

    @Test
    fun `optional cloud and advanced fields never block local readiness`() {
        val directory = Files.createTempDirectory("ares-onboarding-ready-test").toFile()
        try {
            val state = OnboardingState(
                projectPath = directory.absolutePath,
                teamId = "23247",
                seasonId = "2026",
                robotId = "AresIII",
                nt4Host = "",
                simulatorCommand = "",
            )

            assertFalse(validateOnboardingFields(state, OnboardingStep.REVIEW).hasRequiredFieldErrors)
        } finally {
            directory.delete()
        }
    }

    @Test
    fun `advancing onboarding steps updates current step index and flags step completion`() {
        val directory = Files.createTempDirectory("ares-onboarding-step-advance-test").toFile()
        try {
            val initial = OnboardingState()
            assertEquals(OnboardingStep.PROJECT, initial.currentStep)
            assertEquals(0, initial.currentStep.ordinal)
            assertEquals(1, initial.currentStep.number)
            assertFalse(initial.isProjectReady)
            assertFalse(initial.isRobotReady)

            val projectConfigured = initial.copy(projectPath = directory.absolutePath)
            assertTrue(projectConfigured.isProjectReady)
            assertFalse(projectConfigured.isRobotReady)
            assertFalse(validateOnboardingFields(projectConfigured, OnboardingStep.PROJECT).hasRequiredFieldErrors)

            val robotStep = OnboardingStep.entries[(projectConfigured.currentStep.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)]
            val stepTwo = projectConfigured.copy(currentStep = robotStep)
            assertEquals(OnboardingStep.ROBOT, stepTwo.currentStep)
            assertEquals(1, stepTwo.currentStep.ordinal)
            assertEquals(2, stepTwo.currentStep.number)
            assertTrue(stepTwo.isProjectReady)
            assertFalse(stepTwo.isRobotReady)

            val robotConfigured = stepTwo.copy(
                teamId = "23247",
                seasonId = "2026",
                robotId = "AresIII",
            )
            assertTrue(robotConfigured.isRobotReady)
            assertFalse(validateOnboardingFields(robotConfigured, OnboardingStep.ROBOT).hasRequiredFieldErrors)

            val optionalStep = OnboardingStep.entries[(robotConfigured.currentStep.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)]
            val stepThree = robotConfigured.copy(currentStep = optionalStep)
            assertEquals(OnboardingStep.OPTIONAL, stepThree.currentStep)
            assertEquals(2, stepThree.currentStep.ordinal)
            assertEquals(3, stepThree.currentStep.number)
            assertTrue(stepThree.isProjectReady)
            assertTrue(stepThree.isRobotReady)

            val reviewStep = OnboardingStep.entries[(stepThree.currentStep.ordinal + 1).coerceAtMost(OnboardingStep.entries.lastIndex)]
            val stepFour = stepThree.copy(currentStep = reviewStep)
            assertEquals(OnboardingStep.REVIEW, stepFour.currentStep)
            assertEquals(3, stepFour.currentStep.ordinal)
            assertEquals(4, stepFour.currentStep.number)
            assertTrue(stepFour.isProjectReady)
            assertTrue(stepFour.isRobotReady)
            assertFalse(validateOnboardingFields(stepFour, OnboardingStep.REVIEW).hasRequiredFieldErrors)
        } finally {
            directory.delete()
        }
    }
}
