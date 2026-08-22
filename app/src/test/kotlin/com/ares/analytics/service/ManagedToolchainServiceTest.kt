package com.ares.analytics.service

import com.ares.analytics.shared.League
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManagedToolchainServiceTest {
    @Test
    fun `managed JDK redirects remain on reviewed HTTPS hosts`() {
        assertTrue(ManagedToolchainService.isAllowedJdkDownloadUri(URI("https://release-assets.githubusercontent.com/file.zip")))
        assertTrue(!ManagedToolchainService.isAllowedJdkDownloadUri(URI("https://downloads.example.net/file.zip")))
        assertTrue(!ManagedToolchainService.isAllowedJdkDownloadUri(URI("http://github.com/file.zip")))
    }

    @Test
    fun `verified managed JDK installs privately and configures child processes`() = runBlocking {
        val root = Files.createTempDirectory("ares-managed-jdk-test").toFile()
        val oldRoot = System.getProperty("ares.toolchains.root")
        try {
            System.setProperty("ares.toolchains.root", root.path)
            val archive = fakeJdkArchive()
            val checksum = sha256(archive)
            val service = ManagedToolchainService(
                rootDirectory = root,
                packageResolver = {
                    JdkPackage(
                        name = "OpenJDK21U-jdk_x64_windows_hotspot_test.zip",
                        link = "https://github.com/adoptium/temurin21-binaries/releases/download/test/jdk.zip",
                        checksum = checksum,
                    )
                },
                packageDownloader = { _, destination, progress ->
                    destination.writeBytes(archive)
                    progress(archive.size.toLong(), archive.size.toLong())
                },
                jdkVerifier = { javaHome ->
                    assertTrue(File(javaHome, "bin/java.exe").isFile)
                    assertTrue(File(javaHome, "bin/javac.exe").isFile)
                },
            )

            val snapshot = service.installManagedJdk21(League.FTC)

            val java = assertNotNull(ManagedToolchainPaths.javaExecutable())
            assertTrue(java.path.contains("temurin-21-${checksum.take(12)}"))
            assertEquals(ToolchainReadiness.READY, snapshot.components.first().readiness)
            val builder = ManagedToolchainPaths.configureEnvironment(ProcessBuilder("fixture"))
            assertEquals(java.parentFile.parentFile.path, builder.environment()["JAVA_HOME"])
            assertTrue(service.installState.value is ManagedToolchainInstallState.Succeeded)
        } finally {
            if (oldRoot == null) System.clearProperty("ares.toolchains.root") else System.setProperty("ares.toolchains.root", oldRoot)
            root.deleteRecursively()
        }
    }

    @Test
    fun `checksum mismatch installs nothing and reports failure`() = runBlocking {
        val root = Files.createTempDirectory("ares-managed-jdk-hash-test").toFile()
        try {
            val archive = fakeJdkArchive()
            val service = ManagedToolchainService(
                rootDirectory = root,
                packageResolver = {
                    JdkPackage("fixture.zip", "https://github.com/example/fixture.zip", "0".repeat(64))
                },
                packageDownloader = { _, destination, _ -> destination.writeBytes(archive) },
                jdkVerifier = { error("must not verify") },
            )

            runCatching { service.installManagedJdk21(League.FTC) }

            assertTrue(service.installState.value is ManagedToolchainInstallState.Failed)
            assertTrue(root.listFiles().orEmpty().none { it.name.startsWith("temurin-21-") })
            assertTrue(!File(root, ManagedToolchainService.ACTIVE_JDK_MARKER).exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun fakeJdkArchive(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            listOf(
                "jdk-21-test/bin/java.exe" to "java",
                "jdk-21-test/bin/javac.exe" to "javac",
                "jdk-21-test/release" to "JAVA_VERSION=\"21\"",
            ).forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
