package com.ares.analytics.service

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import javax.tools.ToolProvider
import com.ares.analytics.shared.League
import com.areslib.codegen.SubsystemKotlinCodegenTarget
import com.areslib.codegen.SubsystemKotlinGenerator
import com.areslib.subsystem.SubsystemDocumentCodec
import com.areslib.subsystem.SubsystemPlatform
import com.areslib.subsystem.SubsystemTemplate
import com.areslib.subsystem.SubsystemTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessManagerServiceTest {

    @Test
    fun `explicit isolated repository file URI decorates every nested Gradle command`() {
        val repository = Files.createTempDirectory("ares-release-repository").toFile()
        val service = ProcessManagerService(
            monitorAdbConnection = false,
            aresRepositoryUri = repository.toURI().toASCIIString(),
        )
        try {
            val expected = "-ParesRepository=${repository.canonicalFile.toURI().toASCIIString()}"
            assertEquals(
                repository.canonicalFile.toURI().toASCIIString(),
                service.configuredAresRepositoryEnvironmentForTest(),
                "Arbitrary child simulator commands must inherit the equivalent Gradle project property without shell mutation",
            )
            val representativeCommands = listOf(
                listOf("gradlew.bat", ":TeamCode:assembleDebug"),
                listOf("java", "org.gradle.wrapper.GradleWrapperMain", "generateAresProject"),
                listOf("./gradlew", "simulateJava"),
            )

            representativeCommands.forEach { base ->
                val configured = service.configuredGradleCommandForTest(base)
                assertEquals(base + expected, configured)
                assertFalse(configured.any { it.contains("mavenLocal", ignoreCase = true) })
            }
        } finally {
            service.shutdown()
            repository.deleteRecursively()
        }
    }

    @Test
    fun `repository forwarding rejects non-file and missing locations`() {
        assertFailsWith<IllegalArgumentException> {
            ProcessManagerService(false, "https://repo.example/ares")
        }
        val missing = Files.createTempDirectory("missing-ares-repository").resolve("gone").toFile()
        assertFailsWith<IllegalArgumentException> {
            ProcessManagerService(false, missing.toURI().toASCIIString())
        }
    }

    @Test
    fun `normal installer command construction adds no implicit local repository`() {
        val service = ProcessManagerService(monitorAdbConnection = false)
        try {
            assertEquals(
                listOf("./gradlew", "assemble"),
                service.configuredGradleCommandForTest(listOf("./gradlew", "assemble")),
            )
            assertEquals(null, service.configuredAresRepositoryEnvironmentForTest())
        } finally {
            service.shutdown()
        }
    }

    @Test
    fun `starter preview token is hash-bound and stale apply is rejected before Gradle`() {
        val service = ProcessManagerService(monitorAdbConnection = false)
        val root = Files.createTempDirectory("process-manager-starter-plan").toFile()
        try {
            val document = SubsystemTemplates.create(
                SubsystemTemplate.POSITION_CONTROLLED_MECHANISM,
                "elevator",
                "Elevator",
                SubsystemPlatform.FTC,
            )
            root.resolve(".ares/subsystems").mkdirs()
            root.resolve(".ares/subsystems/elevator.aressubsystem").writeText(SubsystemDocumentCodec.encode(document))
            val generated = SubsystemKotlinGenerator.generate(
                document,
                SubsystemKotlinCodegenTarget(SubsystemPlatform.FTC, "org.firstinspires.ftc.teamcode.subsystems"),
            ).first { it.ownership == com.areslib.codegen.SubsystemArtifactOwnership.GENERATED_STARTER }
            val starter = root.resolve(
                "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/${generated.relativePath}"
            )
            starter.parentFile.mkdirs()
            starter.writeText(generated.content.lines().toMutableList().also {
                it[1] = "// reviewed local customization"
            }.joinToString("\n"))

            val preview = service.previewSubsystemStarters(root.path, League.FTC)
            assertTrue(preview.hasReplacements)
            assertTrue(preview.confirmationToken?.matches(Regex("[a-f0-9]{64}")) == true)
            assertFailsWith<IllegalArgumentException> {
                service.applySubsystemStarters(root.path, League.FTC, "0".repeat(64))
            }
        } finally {
            service.shutdown()
            root.deleteRecursively()
        }
    }

    @Test
    fun `replacement joins old generation and cannot clear the new process state`() = runBlocking {
        val service = ProcessManagerService(monitorAdbConnection = false)
        val directory = Files.createTempDirectory("process-manager-replacement").toFile()
        val oldParentPid = File(directory, "old-parent.pid")
        val oldChildPid = File(directory, "old-child.pid")
        val newPidFile = File(directory, "new.pid")
        val releaseNew = File(directory, "release-new")

        try {
            service.runManagedProcessForTest(
                probeCommand("tree", oldParentPid.absolutePath, oldChildPid.absolutePath),
                generationOperation = true
            )
            val oldParent = awaitPid(oldParentPid)
            val oldChild = awaitPid(oldChildPid)

            service.runManagedProcessForTest(
                probeCommand("wait", newPidFile.absolutePath, releaseNew.absolutePath)
            )
            val newPid = awaitPid(newPidFile)

            awaitProcessExit(oldParent)
            awaitProcessExit(oldChild)
            assertTrue(service.isBuildRunning.value, "old cleanup cleared the replacement's running state")
            assertTrue(isAlive(newPid), "replacement exited before the release signal")
            assertEquals(AresGenerationPhase.FAILED, service.aresGenerationState.value.phase)

            releaseNew.writeText("release")
            service.awaitBuildIdleForTest()
            awaitProcessExit(newPid)
            assertFalse(service.isBuildRunning.value)
        } finally {
            withContext(Dispatchers.IO) { service.shutdown() }
            directory.deleteRecursively()
        }
    }

    @Test
    fun `shutdown remains non-cancellable and kills the complete process tree`() = runBlocking {
        val service = ProcessManagerService(monitorAdbConnection = false)
        val directory = Files.createTempDirectory("process-manager-shutdown").toFile()
        val parentPidFile = File(directory, "parent.pid")
        val childPidFile = File(directory, "child.pid")

        try {
            service.runManagedProcessForTest(
                probeCommand("tree", parentPidFile.absolutePath, childPidFile.absolutePath)
            )
            val parentPid = awaitPid(parentPidFile)
            val childPid = awaitPid(childPidFile)

            val shutdown = launch(start = CoroutineStart.UNDISPATCHED) { service.shutdownAndJoin() }
            shutdown.cancelAndJoin()

            awaitProcessExit(parentPid)
            awaitProcessExit(childPid)
            assertFalse(service.isBuildRunning.value)
        } finally {
            withContext(Dispatchers.IO) { service.shutdown() }
            directory.deleteRecursively()
        }
    }

    private suspend fun awaitPid(file: File): Long = withTimeout(5_000L) {
        while (true) {
            val pid = runCatching { file.takeIf(File::isFile)?.readText()?.trim()?.toLongOrNull() }
                .getOrNull()
            if (pid != null) return@withTimeout pid
            delay(10L)
        }
        error("unreachable")
    }

    private suspend fun awaitProcessExit(pid: Long) {
        withTimeout(5_000L) {
            while (isAlive(pid)) delay(10L)
        }
        assertFalse(isAlive(pid), "process $pid survived cleanup")
    }

    private fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    private fun probeCommand(mode: String, vararg arguments: String): List<String> {
        val javaExecutable = File(
            System.getProperty("java.home"),
            "bin/java${if (System.getProperty("os.name").contains("win", ignoreCase = true)) ".exe" else ""}"
        )
        return buildList {
            add(javaExecutable.absolutePath)
            add("-cp")
            add(compiledProcessProbe().absolutePath)
            add(PROCESS_PROBE_CLASS)
            add(mode)
            addAll(arguments)
        }
    }

    private fun compiledProcessProbe(): File = synchronized(PROBE_LOCK) {
        compiledProbeDirectory?.takeIf(File::isDirectory)?.let { return@synchronized it }
        val directory = Files.createTempDirectory("process-manager-probe").toFile().apply { deleteOnExit() }
        val source = File(directory, "$PROCESS_PROBE_CLASS.java").apply {
            writeText(
                """
                import java.nio.file.Files;
                import java.nio.file.Path;

                public final class $PROCESS_PROBE_CLASS {
                    public static void main(String[] args) throws Exception {
                        if ("child".equals(args[0])) {
                            Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
                            Thread.sleep(60_000L);
                            return;
                        }
                        if ("tree".equals(args[0])) {
                            String javaHome = System.getProperty("java.home");
                            String executable = Path.of(
                                javaHome,
                                "bin",
                                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
                            ).toString();
                            new ProcessBuilder(
                                executable,
                                "-cp",
                                System.getProperty("java.class.path"),
                                $PROCESS_PROBE_CLASS.class.getName(),
                                "child",
                                args[2]
                            ).start();
                            Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
                            Thread.sleep(60_000L);
                            return;
                        }
                        Files.writeString(Path.of(args[1]), Long.toString(ProcessHandle.current().pid()));
                        while (!Files.exists(Path.of(args[2]))) Thread.sleep(10L);
                    }
                }
                """.trimIndent()
            )
        }
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "Tests require a JDK compiler" }
        assertEquals(0, compiler.run(null, null, null, "-d", directory.absolutePath, source.absolutePath))
        compiledProbeDirectory = directory
        directory
    }

    private companion object {
        const val PROCESS_PROBE_CLASS = "ProcessManagerProbe"
        val PROBE_LOCK = Any()
        var compiledProbeDirectory: File? = null
    }
}
