package com.ares.analytics.service.versioncontrol

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContains
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
        val service = localOnlyService()
        val initial = service.initialize(root.path, "Student Builder", "student@example.org")
        assertTrue(initial.canCommit)
        val staleToken = requireNotNull(initial.confirmationToken)
        File(root, "robot.txt").writeText("changed after preview")
        val staleFailure = assertFailsWith<IllegalArgumentException> {
            service.commit(root.path, staleToken, "Create robot", "Student Builder", "student@example.org")
        }
        assertContains(staleFailure.message.orEmpty(), "changed after this preview")
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
        val service = localOnlyService()
        service.initialize(root.path, "Mentor", "mentor@example.org")
        File(root, "credentials.json").writeText("{\"private\":true}")
        val plan = service.inspect(root.path)
        assertEquals(listOf("credentials.json"), plan.blockedSensitivePaths)
        assertFalse(plan.canCommit)
    }

    @Test
    fun `github app device flow stores rotating credentials outside the project without a secret`() = runBlocking {
        val store = MemoryCredentialStore()
        val api = FakeGitHubApi()
        var openedUri: String? = null
        val service = githubService(store, api, browserLauncher = { openedUri = it })
        service.signInToGitHub()
        assertEquals("https://github.com/login/device", openedUri)
        val saved = store.bytes?.toString(Charsets.UTF_8).orEmpty()
        assertContains(saved, "\"schemaVersion\":2")
        assertContains(saved, api.authorizedTokens.accessToken)
        assertContains(saved, api.authorizedTokens.refreshToken)
        assertFalse(saved.contains("client_secret", ignoreCase = true))
        assertFalse(saved.contains("\"scope\"", ignoreCase = true))
        assertEquals(GitHubConnectionState.Connected("student-team"), service.githubState.value)
        assertEquals("Ov23liExampleClientId", api.receivedClientId)
        service.disconnectGitHub()
        assertNull(store.bytes)
    }

    @Test
    fun `expired access token is refreshed and rotated before destination discovery`() = runBlocking {
        val store = MemoryCredentialStore()
        val api = FakeGitHubApi().apply {
            authorizedTokens = tokens("old-access-token-1234567890", "old-refresh-token-123456789")
            refreshedTokens = tokens("new-access-token-1234567890", "new-refresh-token-123456789")
        }
        val service = githubService(store, api, now = 1_000L)
        service.signInToGitHub()
        api.nowForTokenExpiry = true
        service.discoverGitHubDestinations()
        assertEquals(1, api.refreshCalls)
        assertTrue(api.catalogTokens.all { it == api.refreshedTokens.accessToken })
        val saved = store.bytes?.toString(Charsets.UTF_8).orEmpty()
        assertContains(saved, api.refreshedTokens.accessToken)
        assertContains(saved, api.refreshedTokens.refreshToken)
        assertFalse(saved.contains(api.authorizedTokens.refreshToken))
    }

    @Test
    fun `revoked refresh access is cleared and requires a new sign in`() = runBlocking {
        val store = MemoryCredentialStore()
        val api = FakeGitHubApi().apply { refreshFailureCode = "bad_refresh_token" }
        val service = githubService(store, api, now = 1_000L)
        service.signInToGitHub()
        api.nowForTokenExpiry = true

        val failure = assertFailsWith<IllegalStateException> { service.discoverGitHubDestinations() }

        assertContains(failure.message.orEmpty(), "expired or was revoked")
        assertTrue(service.githubState.value is GitHubConnectionState.Error)
        assertNull(store.bytes)
    }

    @Test
    fun `personal and organization installations expose only approved writable private repositories`() = runBlocking {
        val api = FakeGitHubApi().apply {
            accounts = listOf(personalAccount(), organizationAccount())
            repositories = listOf(
                privateRepository(11, 101, "student-team", "practice"),
                privateRepository(22, 202, "ARES-23247", "competition"),
                privateRepository(22, 203, "ARES-23247", "archive").copy(archived = true),
            )
        }
        val service = githubService(MemoryCredentialStore(), api)
        service.signInToGitHub()
        val catalog = service.discoverGitHubDestinations()
        assertEquals(listOf(GitHubAccountKind.PERSONAL, GitHubAccountKind.ORGANIZATION), catalog.accounts.map { it.kind })
        assertEquals(listOf(101L), catalog.repositoriesFor(11).filter { it.canUseForBackup }.map { it.repositoryId })
        assertEquals(listOf(202L), catalog.repositoriesFor(22).filter { it.canUseForBackup }.map { it.repositoryId })
        assertContains(catalog.repositories.single { it.repositoryId == 203L }.unavailableReason.orEmpty(), "archived")
    }

    @Test
    fun `approved organization repository stores stable identity and pushes without credentials in git config`() = runBlocking {
        val root = cleanCommittedProject("organization-backup")
        val api = FakeGitHubApi().apply {
            accounts = listOf(organizationAccount())
            repositories = listOf(privateRepository(22, 202, "ARES-23247", "team-robot"))
        }
        val pushedTokens = mutableListOf<String>()
        val service = githubService(MemoryCredentialStore(), api, remotePusher = { _, token -> pushedTokens += token })
        service.signInToGitHub()
        val plan = service.connectApprovedRepository(root.path, 22, 202)
        assertEquals("ARES-23247", plan.destination?.ownerLogin)
        assertEquals(202L, plan.destination?.repositoryId)
        assertEquals(listOf(api.authorizedTokens.accessToken), pushedTokens)
        val configText = File(root, ".git/config").readText()
        assertContains(configText, "installationId = 22")
        assertContains(configText, "repositoryId = 202")
        assertContains(configText, "https://github.com/ARES-23247/team-robot.git")
        assertFalse(configText.contains(api.authorizedTokens.accessToken))
        assertFalse(configText.contains(api.authorizedTokens.refreshToken))
    }

    @Test
    fun `a different existing origin is never replaced`() = runBlocking {
        val root = cleanCommittedProject("existing-origin")
        Git.open(root).use { git ->
            git.remoteAdd()
                .setName("origin")
                .setUri(URIish("https://github.com/another-team/another-robot.git"))
                .call()
        }
        val api = FakeGitHubApi().apply {
            accounts = listOf(organizationAccount())
            repositories = listOf(privateRepository(22, 202, "ARES-23247", "team-robot"))
        }
        var pushCount = 0
        val service = githubService(MemoryCredentialStore(), api, remotePusher = { _, _ -> pushCount++ })
        service.signInToGitHub()

        val failure = assertFailsWith<IllegalArgumentException> {
            service.connectApprovedRepository(root.path, 22, 202)
        }

        assertContains(failure.message.orEmpty(), "different origin")
        assertEquals(0, pushCount)
        Git.open(root).use { git ->
            assertEquals(
                "https://github.com/another-team/another-robot.git",
                git.remoteList().call().single().urIs.single().toString(),
            )
        }
    }

    @Test
    fun `failed first push rolls back managed destination and added origin`() = runBlocking {
        val root = cleanCommittedProject("failed-first-push")
        val api = FakeGitHubApi().apply {
            accounts = listOf(organizationAccount())
            repositories = listOf(privateRepository(22, 202, "ARES-23247", "team-robot"))
        }
        val service = githubService(MemoryCredentialStore(), api, remotePusher = { _, _ -> error("network unavailable") })
        service.signInToGitHub()

        assertFailsWith<IllegalStateException> { service.connectApprovedRepository(root.path, 22, 202) }

        val plan = service.inspect(root.path)
        assertNull(plan.destination)
        assertNull(plan.remoteUrl)
        assertFalse(File(root, ".git/config").readText().contains("aresBackup"))
    }

    @Test
    fun `removed repository permission blocks sync without invoking remote push`() = runBlocking {
        val root = cleanCommittedProject("revoked-backup")
        val api = FakeGitHubApi().apply {
            accounts = listOf(organizationAccount())
            repositories = listOf(privateRepository(22, 202, "ARES-23247", "team-robot"))
        }
        var pushCount = 0
        val service = githubService(MemoryCredentialStore(), api, remotePusher = { _, _ -> pushCount++ })
        service.signInToGitHub()
        service.connectApprovedRepository(root.path, 22, 202)
        assertEquals(1, pushCount)
        api.repositories = listOf(privateRepository(22, 202, "ARES-23247", "team-robot").copy(canPush = false))
        val failure = assertFailsWith<IllegalArgumentException> { service.pushBackup(root.path) }
        assertContains(failure.message.orEmpty(), "write access")
        assertEquals(1, pushCount)
    }

    @Test
    fun `concurrent sync operations are serialized for one project credential`() = runBlocking {
        val root = cleanCommittedProject("concurrent-backup")
        val api = FakeGitHubApi().apply {
            accounts = listOf(organizationAccount())
            repositories = listOf(privateRepository(22, 202, "ARES-23247", "team-robot"))
        }
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        var pushes = 0
        val service = githubService(MemoryCredentialStore(), api, remotePusher = { _, _ ->
            val current = active.incrementAndGet()
            maximumActive.updateAndGet { previous -> maxOf(previous, current) }
            Thread.sleep(20)
            pushes++
            active.decrementAndGet()
        })
        service.signInToGitHub()
        service.connectApprovedRepository(root.path, 22, 202)

        coroutineScope {
            List(4) { async { service.pushBackup(root.path) } }.awaitAll()
        }

        assertEquals(5, pushes)
        assertEquals(1, maximumActive.get())
    }

    @Test
    fun `application state cannot select a repository outside the visible installation catalog`() = runBlocking {
        val root = cleanCommittedProject("isolated-backup")
        val api = FakeGitHubApi().apply {
            accounts = listOf(organizationAccount())
            repositories = listOf(privateRepository(22, 202, "ARES-23247", "team-a"))
        }
        var pushCount = 0
        val service = githubService(MemoryCredentialStore(), api, remotePusher = { _, _ -> pushCount++ })
        service.signInToGitHub()
        val failure = assertFailsWith<IllegalStateException> {
            service.connectApprovedRepository(root.path, installationId = 99, repositoryId = 909)
        }
        assertContains(failure.message.orEmpty(), "no longer available")
        assertEquals(0, pushCount)
        assertNull(service.inspect(root.path).remoteUrl)
    }

    @Test
    fun `legacy broad oauth credential is deleted and requires github app sign in`() {
        val store = MemoryCredentialStore().apply {
            bytes = """{"accessToken":"legacy-token","login":"student-team","scope":"repo"}""".toByteArray()
        }
        val service = githubService(store, FakeGitHubApi())
        val state = service.githubState.value as GitHubConnectionState.Error
        assertContains(state.message, "older broad GitHub OAuth credential")
        assertNull(store.bytes)
    }

    @Test
    fun `unreadable saved github access is cleared without crashing the screen`() {
        val store = UnreadableCredentialStore()
        val service = ProjectVersionControlService(
            githubClientId = "Ov23liExampleClientId",
            githubAppSlug = "ares-project-backup",
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
        val service = localOnlyService()
        assertFailsWith<IllegalArgumentException> {
            runBlocking { service.initialize(root.path, "Student", "student@example.org") }
        }
        assertFalse(File(root, ".git").exists())
    }

    private fun localOnlyService() = ProjectVersionControlService(
        githubClientId = "",
        githubAppSlug = "",
        credentialStore = MemoryCredentialStore(),
        githubApi = FakeGitHubApi(),
        browserLauncher = {},
        pollDelay = {},
    )

    private fun githubService(
        store: ProjectBackupCredentialStore,
        api: FakeGitHubApi,
        now: Long = 1_000L,
        browserLauncher: (String) -> Unit = {},
        remotePusher: (Git, String) -> Unit = { _, _ -> },
    ) = ProjectVersionControlService(
        githubClientId = "Ov23liExampleClientId",
        githubAppSlug = "ares-project-backup",
        credentialStore = store,
        githubApi = api,
        browserLauncher = browserLauncher,
        pollDelay = {},
        epochSeconds = { if (api.nowForTokenExpiry) now + 3_570L else now },
        remotePusher = remotePusher,
    )

    private suspend fun cleanCommittedProject(name: String): File {
        val root = canonicalProject(name)
        File(root, "robot.txt").writeText("robot")
        val service = localOnlyService()
        val preview = service.initialize(root.path, "Student", "student@example.org")
        service.commit(root.path, requireNotNull(preview.confirmationToken), "Create robot", "Student", "student@example.org")
        return root
    }

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
    var authorizedTokens = tokens("authorized-access-token-123456", "authorized-refresh-token-12345")
    var refreshedTokens = tokens("refreshed-access-token-1234567", "refreshed-refresh-token-123456")
    var accounts: List<GitHubBackupAccount> = listOf(personalAccount())
    var repositories: List<GitHubBackupRepository> = emptyList()
    var nowForTokenExpiry = false
    var refreshFailureCode: String? = null
    var refreshCalls = 0
    val catalogTokens = mutableListOf<String>()

    override fun beginDeviceAuthorization(clientId: String): GitHubDeviceAuthorization {
        receivedClientId = clientId
        return GitHubDeviceAuthorization("device", "ABCD-1234", "https://github.com/login/device", 600, 5)
    }
    override fun pollDeviceAuthorization(clientId: String, deviceCode: String): GitHubDevicePollResult =
        GitHubDevicePollResult.Authorized(authorizedTokens)
    override fun refreshUserAccessToken(clientId: String, refreshToken: String): GitHubUserTokens {
        refreshCalls++
        assertEquals(authorizedTokens.refreshToken, refreshToken)
        refreshFailureCode?.let { throw GitHubAuthorizationException(it) }
        return refreshedTokens
    }
    override fun currentLogin(token: String): String = "student-team"
    override fun listInstallations(token: String): List<GitHubBackupAccount> {
        catalogTokens += token
        return accounts
    }
    override fun listRepositories(token: String, installationId: Long): List<GitHubBackupRepository> {
        catalogTokens += token
        return repositories.filter { it.installationId == installationId }
    }
}

private fun tokens(access: String, refresh: String) = GitHubUserTokens(
    accessToken = access,
    expiresInSeconds = 3_600,
    refreshToken = refresh,
    refreshTokenExpiresInSeconds = 180L * 24L * 60L * 60L,
)

private fun personalAccount() = GitHubBackupAccount(
    installationId = 11,
    login = "student-team",
    kind = GitHubAccountKind.PERSONAL,
    repositorySelection = "selected",
    contentsPermission = "write",
    installationUrl = "https://github.com/settings/installations/11",
)

private fun organizationAccount() = GitHubBackupAccount(
    installationId = 22,
    login = "ARES-23247",
    kind = GitHubAccountKind.ORGANIZATION,
    repositorySelection = "selected",
    contentsPermission = "write",
    installationUrl = "https://github.com/organizations/ARES-23247/settings/installations/22",
)

private fun privateRepository(
    installationId: Long,
    repositoryId: Long,
    owner: String,
    name: String,
) = GitHubBackupRepository(
    installationId = installationId,
    repositoryId = repositoryId,
    ownerLogin = owner,
    name = name,
    fullName = "$owner/$name",
    cloneUrl = "https://github.com/$owner/$name.git",
    webUrl = "https://github.com/$owner/$name",
    visibility = "private",
    isPrivate = true,
    canPush = true,
    archived = false,
    disabled = false,
)
