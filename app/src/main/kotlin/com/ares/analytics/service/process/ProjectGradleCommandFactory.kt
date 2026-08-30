package com.ares.analytics.service.process

import com.ares.analytics.shared.League
import com.areslib.simulation.SimulationProductId
import java.io.File

/**
 * Pure command policy for Studio-owned Gradle children.
 *
 * Process lifecycle, cancellation, output streaming, and toolchain discovery remain in
 * ProcessManagerService; this type owns only deterministic argument construction.
 */
internal class ProjectGradleCommandFactory(
    private val gradleJavaInstallationsArgument: String?,
    private val aresRepositoryArgument: String?,
    private val aresVersionArgument: String?,
) {
    fun verification(league: League, isWindows: Boolean): List<String> = decorate(buildList {
        addGradleWrapper(isWindows)
        when (league) {
            League.FTC -> addAll(
                listOf(
                    "generateAresProject",
                    ":TeamCode:verifyAresProject",
                    ":TeamCode:testDebugUnitTest",
                    ":simulator:test",
                    ":TeamCode:assembleDebug",
                ),
            )
            League.FRC -> addAll(listOf("generateAresProject", "verifyAresProject", "test", "build"))
        }
        addDesktopProcessOptions()
        add("--rerun-tasks")
    })

    fun ftcDeploy(isWindows: Boolean): List<String> = buildList {
        addGradleWrapper(isWindows)
        add("generateAresProject")
        add("verifyAresProject")
        add(":TeamCode:testDebugUnitTest")
        add(":simulator:test")
        add(":TeamCode:assembleDebug")
        addDesktopProcessOptions()
    }

    fun frcDeploy(isWindows: Boolean): List<String> = buildList {
        addGradleWrapper(isWindows)
        add("generateAresProject")
        add("verifyAresProject")
        add("test")
        add("build")
        add("deploy")
        addDesktopProcessOptions()
    }

    fun simulation(
        isWindows: Boolean,
        product: SimulationProductId,
        frcJavaExecutable: File? = null,
    ): List<String> {
        val isFrc = product == SimulationProductId.FRC_WPILIB_DESKTOP
        val base = decorate(buildList {
            addGradleWrapper(isWindows)
            add(
                when (product) {
                    SimulationProductId.FTC_DESKTOP_OPMODE -> ":TeamCode:runSim"
                    SimulationProductId.FRC_WPILIB_DESKTOP -> "simulateJava"
                },
            )
            addDesktopProcessOptions()
        })
        if (!isFrc) return base
        return buildList {
            addAll(base)
            add("-ParesFrcHalGui=false")
            if (isWindows && frcJavaExecutable?.isFile == true) {
                add("-ParesFrcJavaExecutable=${frcJavaExecutable.path}")
            }
        }
    }

    fun authoring(
        task: String,
        isWindows: Boolean,
        confirmationToken: String? = null,
    ): List<String> = decorate(buildList {
        addGradleWrapper(isWindows)
        add(task)
        addDesktopProcessOptions()
        confirmationToken?.let { add("-Pares.subsystemReplacementToken=$it") }
    })

    fun decorate(command: List<String>): List<String> = buildList {
        addAll(command)
        gradleJavaInstallationsArgument?.let(::add)
        aresRepositoryArgument?.let(::add)
        aresVersionArgument?.let(::add)
    }

    private fun MutableList<String>.addGradleWrapper(isWindows: Boolean) {
        if (isWindows) addAll(listOf("cmd.exe", "/c", "gradlew.bat")) else add("./gradlew")
    }

    private fun MutableList<String>.addDesktopProcessOptions() {
        add("--no-parallel")
        add("--no-daemon")
        add("--console=plain")
    }
}
