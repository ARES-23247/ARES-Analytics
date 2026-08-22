package com.ares.analytics.service.versioncontrol

import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectVersionControlServiceTest {
    private val temporaryDirectory: Path = Files.createTempDirectory("ares-project-backup-test")

    @Test
    fun `local history requires a content-bound review before commit`() = runBlocking {
        val root = canonicalProject("reviewed-project")
        File(root, "robot.txt").writeText("first")
        val service = service()

        val initial = service.initialize(root.path, "Student Builder", "student@example.org")
        assertTrue(initial.initialized)
        assertTrue(initial.canCommit)
        assertTrue(initial.changes.any { it.path == "robot.txt" })

        val staleToken = requireNotNull(initial.confirmationToken)
        File(root, "robot.txt").writeText("changed after preview")
        val staleFailure = assertFailsWith<IllegalArgumentException> {
            runBlocking {
                service.commit(root.path, staleToken, "Create robot", "Student Builder", "student@example.org")
            }
        }
        assertTrue(staleFailure.message.orEmpty().contains("changed after this preview"))

        val reviewed = service.inspect(root.path)
        val clean = service.commit(
            root.path,
            requireNotNull(reviewed.confirmationToken),
            "Create robot",
            "Student Builder",
            "student@example.org",
        )
        assertTrue(clean.changes.isEmpty())
        assertNotNull(clean.lastCommit)
        Git.open(root).use { git ->
            assertEquals("Create robot", git.log().call().first().fullMessage)
            assertEquals("Student Builder", git.log().call().first().authorIdent.name)
        }
    }

    @Test
    fun `sensitive local files block version creation`() = runBlocking {
        val root = canonicalProject("private-file-project")
        val service = service()
        service.initialize(root.path, "Mentor", "mentor@example.org")
        File(root, "credentials.json").writeText("{\"private\":true}")

        val plan = service.inspect(root.path)
        assertEquals(listOf("credentials.json"), plan.blockedSensitivePaths)
        assertFalse(plan.canCommit)
    }

    @Test
    fun `github device flow stores token outside project and never needs a secret`() = runBlocking {
        val store = MemoryCredentialStore()
        val api = FakeGitHubApi()
        var openedUri: String? = null
        val service = ProjectVersionControlService(
            githubClientId = "Ov23liExampleClientId",
            credentialStore = store,
            githubApi = api,
            browserLauncher = { openedUri = it },
            pollDelay = {},
        )

        service.signInToGitHub()

        assertEquals("https://github.com/login/device", openedUri)
        assertTrue(store.bytes?.toString(Charsets.UTF_8).orEmpty().contains("github_pat_test"))
        assertEquals(GitHubConnectionState.Connected("student-team"), service.githubState.value)
        assertEquals("Ov23liExampleClientId", api.receivedClientId)
        assertFalse(api.receivedClientId.orEmpty().contains("secret", ignoreCase = true))

        service.disconnectGitHub()
        assertNull(store.bytes)
        assertEquals(GitHubConnectionState.Disconnected, service.githubState.value)
    }

    @Test
    fun `unreadable saved github access is cleared without crashing the screen`() {
        val store = UnreadableCredentialStore()
        val service = ProjectVersionControlService(
            githubClientId = "Ov23liExampleClientId",
            credentialStore = store,
            githubApi = FakeGitHubApi(),
            browserLauncher = {},
            pollDelay = {},
        )

        assertTrue(service.githubState.value is GitHubConnectionState.Error)
        assertTrue(store.deleted)
    }

    @Test
    fun `non canonical folders are rejected before git writes`() {
        val root = temporaryDirectory.resolve("ordinary-folder").toFile().apply { mkdirs() }
        val service = service()
        assertFailsWith<IllegalArgumentException> {
            runBlocking { service.initialize(root.path, "Student", "student@example.org") }
        }
        assertFalse(File(root, ".git").exists())
    }

    private fun service() = ProjectVersionControlService(
        githubClientId = "",
        credentialStore = MemoryCredentialStore(),
        githubApi = FakeGitHubApi(),
        browserLauncher = {},
        pollDelay = {},
    )

    private fun canonicalProject(name: String): File = temporaryDirectory.resolve(name).toFile().apply {
        File(this, ".ares").mkdirs()
        File(this, ".ares/project.json").writeText("{}")
        File(this, ".gitignore").writeText("local.properties\n*.jks\n.ares/secrets/\n")
    }
}

private class MemoryCredentialStore : ProjectBackupCredentialStore {
    var bytes: ByteArray? = null
    override fun read(): ByteArray? = bytes?.copyOf()
    override fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
    override fun delete(): Boolean { bytes = null; return true }
    override val protectionDescription: String = "test memory"
}

private class UnreadableCredentialStore : ProjectBackupCredentialStore {
    var deleted = false
    override fun read(): ByteArray? = error("corrupt DPAPI fixture")
    override fun write(bytes: ByteArray) = Unit
    override fun delete(): Boolean { deleted = true; return true }
    override val protectionDescription: String = "test corruption"
}

private class FakeGitHubApi : GitHubProjectApi {
    var receivedClientId: String? = null
    override fun beginDeviceAuthorization(clientId: String): GitHubDeviceAuthorization {
        receivedClientId = clientId
        return GitHubDeviceAuthorization("device", "ABCD-1234", "https://github.com/login/device", 60, 5)
    }
    override fun pollDeviceAuthorization(clientId: String, deviceCode: String): GitHubDevicePollResult =
        GitHubDevicePollResult.Authorized("github_pat_test", "repo")
    override fun currentLogin(token: String): String = "student-team"
    override fun createPrivateRepository(token: String, name: String, description: String): String =
        "https://github.com/student-team/$name.git"
}
