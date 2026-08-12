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
                googleClientId = "",
                googleClientSecret = "",
                nt4Host = "",
                simulatorCommand = "",
            )

            assertFalse(validateOnboardingFields(state, OnboardingStep.REVIEW).hasRequiredFieldErrors)
        } finally {
            directory.delete()
        }
    }
}
