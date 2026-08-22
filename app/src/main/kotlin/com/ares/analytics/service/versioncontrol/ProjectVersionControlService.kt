package com.ares.analytics.service.versioncontrol

import com.ares.analytics.BuildConfig
import com.ares.analytics.shared.AppJson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

enum class ProjectChangeKind { ADDED, MODIFIED, DELETED, RENAMED, CONFLICT }

data class ProjectChange(val path: String, val kind: ProjectChangeKind)

data class ProjectBackupPlan(
    val projectPath: String,
    val initialized: Boolean,
    val branch: String?,
    val changes: List<ProjectChange>,
    val blockedSensitivePaths: List<String>,
    val lastCommit: String?,
    val remoteUrl: String?,
    val confirmationToken: String?,
) {
    val canCommit: Boolean get() = initialized && changes.isNotEmpty() && blockedSensitivePaths.isEmpty() && confirmationToken != null
}

sealed class GitHubConnectionState {
    object Disconnected : GitHubConnectionState()
    data class Unavailable(val message: String) : GitHubConnectionState()
    data class AwaitingUser(val userCode: String, val verificationUri: String, val expiresAtEpochSeconds: Long) : GitHubConnectionState()
    data class Connected(val login: String) : GitHubConnectionState()
    data class Error(val message: String) : GitHubConnectionState()
}

@Serializable
internal data class StoredGitHubCredential(
    val accessToken: String,
    val login: String,
    val scope: String,
)

internal data class GitHubDeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

internal sealed class GitHubDevicePollResult {
    object Pending : GitHubDevicePollResult()
    object SlowDown : GitHubDevicePollResult()
    data class Authorized(val token: String, val scope: String) : GitHubDevicePollResult()
    data class Failed(val code: String) : GitHubDevicePollResult()
}

internal interface GitHubProjectApi {
    fun beginDeviceAuthorization(clientId: String): GitHubDeviceAuthorization
    fun pollDeviceAuthorization(clientId: String, deviceCode: String): GitHubDevicePollResult
    fun currentLogin(token: String): String
    fun createPrivateRepository(token: String, name: String, description: String): String
}

/**
 * Review-first local Git history plus optional GitHub backup for one robot project.
 *
 * JGit is embedded, so local history does not require a separate Git installation. Credentials are
 * never written into a repository URL or project file. GitHub operations require a configured
 * public OAuth client ID and use device authorization without a client secret.
 */
class ProjectVersionControlService internal constructor(
    private val githubClientId: String = BuildConfig.GITHUB_OAUTH_CLIENT_ID,
    private val credentialStore: ProjectBackupCredentialStore = createProjectBackupCredentialStore(),
    private val githubApi: GitHubProjectApi = DefaultGitHubProjectApi(),
    private val browserLauncher: (String) -> Unit = { uri -> Desktop.getDesktop().browse(URI(uri)) },
    private val pollDelay: suspend (Long) -> Unit = { milliseconds -> delay(milliseconds) },
) {
    private val _githubState = MutableStateFlow<GitHubConnectionState>(initialGitHubState())
    val githubState: StateFlow<GitHubConnectionState> = _githubState.asStateFlow()

    suspend fun inspect(projectPath: String): ProjectBackupPlan = withContext(Dispatchers.IO) {
        buildPlan(requireProjectRoot(projectPath))
    }

    suspend fun initialize(projectPath: String, authorName: String, authorEmail: String): ProjectBackupPlan =
        withContext(Dispatchers.IO) {
            validateIdentity(authorName, authorEmail)
            val root = requireProjectRoot(projectPath)
            require(!File(root, ".git").exists()) { "This project already has local version history." }
            Git.init().setDirectory(root).setInitialBranch("main").call().use { git ->
                val config = git.repository.config
                config.setString("user", null, "name", authorName.trim())
                config.setString("user", null, "email", authorEmail.trim())
                config.save()
            }
            buildPlan(root)
        }

    suspend fun commit(
        projectPath: String,
        expectedConfirmationToken: String,
        message: String,
        authorName: String,
        authorEmail: String,
    ): ProjectBackupPlan = withContext(Dispatchers.IO) {
        validateIdentity(authorName, authorEmail)
        require(message.trim().length in 3..120) { "Describe this saved version in 3 to 120 characters." }
        val root = requireProjectRoot(projectPath)
        val current = buildPlan(root)
        require(current.canCommit) {
            if (current.blockedSensitivePaths.isNotEmpty()) {
                "Remove or ignore sensitive local files before saving a version: ${current.blockedSensitivePaths.joinToString()}."
            } else {
                "There are no reviewed project changes to save."
            }
        }
        require(current.confirmationToken == expectedConfirmationToken) {
            "The project changed after this preview. Review the updated file list before saving."
        }
        Git.open(root).use { git ->
            val config = git.repository.config
            config.setString("user", null, "name", authorName.trim())
            config.setString("user", null, "email", authorEmail.trim())
            config.save()
            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call()
            git.commit()
                .setMessage(message.trim())
                .setAuthor(authorName.trim(), authorEmail.trim())
                .setCommitter(authorName.trim(), authorEmail.trim())
                // App-managed history must not inherit a machine's optional Git/GPG signing
                // policy. Students should not need a separate Git or GPG installation.
                .setSign(false)
                .call()
        }
        buildPlan(root)
    }

    suspend fun signInToGitHub() {
        withContext(Dispatchers.IO) {
            require(validGitHubClientId(githubClientId)) {
                "This ARES build has no GitHub application identity. Local history still works; install an official build configured for GitHub backup."
            }
            val authorization = githubApi.beginDeviceAuthorization(githubClientId)
            val expiresAt = Instant.now().epochSecond + authorization.expiresInSeconds
            _githubState.value = GitHubConnectionState.AwaitingUser(
                authorization.userCode,
                authorization.verificationUri,
                expiresAt,
            )
            browserLauncher(authorization.verificationUri)
            var interval = authorization.intervalSeconds.coerceAtLeast(5)
            while (Instant.now().epochSecond < expiresAt) {
                pollDelay(interval * 1_000)
                when (val result = githubApi.pollDeviceAuthorization(githubClientId, authorization.deviceCode)) {
                    GitHubDevicePollResult.Pending -> Unit
                    GitHubDevicePollResult.SlowDown -> interval += 5
                    is GitHubDevicePollResult.Authorized -> {
                        val login = githubApi.currentLogin(result.token)
                        credentialStore.write(
                            AppJson.encodeToString(
                                StoredGitHubCredential.serializer(),
                                StoredGitHubCredential(result.token, login, result.scope),
                            ).toByteArray(StandardCharsets.UTF_8),
                        )
                        _githubState.value = GitHubConnectionState.Connected(login)
                        return@withContext
                    }
                    is GitHubDevicePollResult.Failed -> {
                        val message = githubDeviceFailureMessage(result.code)
                        _githubState.value = GitHubConnectionState.Error(message)
                        error(message)
                    }
                }
            }
            val message = "The GitHub sign-in code expired. Start sign-in again to receive a new code."
            _githubState.value = GitHubConnectionState.Error(message)
            error(message)
        }
    }

    suspend fun createPrivateBackupAndPush(projectPath: String, repositoryName: String): ProjectBackupPlan =
        withContext(Dispatchers.IO) {
            val credential = requireCredential()
            val root = requireProjectRoot(projectPath)
            validateRepositoryName(repositoryName)
            Git.open(root).use { git ->
                require(git.repository.resolve(Constants.HEAD) != null) { "Save at least one local version before creating a GitHub backup." }
                require(git.remoteList().call().none { it.name == "origin" }) {
                    "This project already has an origin remote. Use Sync backup instead of creating another repository."
                }
                val remoteUrl = githubApi.createPrivateRepository(
                    credential.accessToken,
                    repositoryName.trim(),
                    "ARES robot project backup",
                )
                validateGitHubRepositoryUrl(remoteUrl)
                git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(remoteUrl)).call()
                push(git, credential)
            }
            buildPlan(root)
        }

    suspend fun pushBackup(projectPath: String): ProjectBackupPlan = withContext(Dispatchers.IO) {
        val credential = requireCredential()
        val root = requireProjectRoot(projectPath)
        Git.open(root).use { git ->
            require(git.status().call().isClean) { "Save the current changes as a local version before syncing GitHub." }
            val origin = git.remoteList().call().singleOrNull { it.name == "origin" }
                ?: error("Create or connect a GitHub backup before syncing.")
            validateGitHubRepositoryUrl(origin.urIs.singleOrNull()?.toString().orEmpty())
            push(git, credential)
        }
        buildPlan(root)
    }

    fun disconnectGitHub() {
        check(credentialStore.delete()) { "GitHub credentials could not be removed from this computer." }
        _githubState.value = if (validGitHubClientId(githubClientId)) {
            GitHubConnectionState.Disconnected
        } else {
            GitHubConnectionState.Unavailable("Official GitHub backup is not configured in this build.")
        }
    }

    private fun push(git: Git, credential: StoredGitHubCredential) {
        val results = git.push()
            .setRemote("origin")
            .setCredentialsProvider(UsernamePasswordCredentialsProvider("x-access-token", credential.accessToken))
            .setPushAll()
            .call()
        val failures = results.flatMap { it.remoteUpdates }
            .filter { update -> update.status.name !in setOf("OK", "UP_TO_DATE") }
        require(failures.isEmpty()) {
            "GitHub rejected the backup update (${failures.joinToString { it.status.name }}). Nothing remote was overwritten; refresh or ask a mentor to resolve the history difference."
        }
    }

    private fun buildPlan(root: File): ProjectBackupPlan {
        if (!File(root, ".git").isDirectory) {
            return ProjectBackupPlan(root.path, false, null, emptyList(), emptyList(), null, null, null)
        }
        Git.open(root).use { git ->
            val status = git.status().call()
            val changes = buildList {
                status.added.forEach { add(ProjectChange(it, ProjectChangeKind.ADDED)) }
                status.untracked.forEach { add(ProjectChange(it, ProjectChangeKind.ADDED)) }
                status.changed.forEach { add(ProjectChange(it, ProjectChangeKind.MODIFIED)) }
                status.modified.forEach { add(ProjectChange(it, ProjectChangeKind.MODIFIED)) }
                status.removed.forEach { add(ProjectChange(it, ProjectChangeKind.DELETED)) }
                status.missing.forEach { add(ProjectChange(it, ProjectChangeKind.DELETED)) }
                status.conflicting.forEach { add(ProjectChange(it, ProjectChangeKind.CONFLICT)) }
            }.distinctBy { it.path to it.kind }.sortedWith(compareBy(ProjectChange::path, ProjectChange::kind))
            val sensitive = changes.map(ProjectChange::path).filter(::isSensitiveProjectPath).distinct().sorted()
            val head = git.repository.resolve(Constants.HEAD)
            val lastCommit = head?.name
            val branch = runCatching { git.repository.branch }.getOrNull()
            val origin = git.remoteList().call().singleOrNull { it.name == "origin" }
                ?.urIs?.singleOrNull()?.toString()
            return ProjectBackupPlan(
                projectPath = root.path,
                initialized = true,
                branch = branch,
                changes = changes,
                blockedSensitivePaths = sensitive,
                lastCommit = lastCommit,
                remoteUrl = origin,
                confirmationToken = changes.takeIf { it.isNotEmpty() }?.let { contentBoundToken(root, it) },
            )
        }
    }

    private fun contentBoundToken(root: File, changes: List<ProjectChange>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        changes.forEach { change ->
            digest.update(change.kind.name.toByteArray())
            digest.update(0)
            digest.update(change.path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            val file = File(root, change.path).canonicalFile
            require(file.toPath().startsWith(root.canonicalFile.toPath())) { "A changed path escaped the project." }
            if (file.isFile) {
                require(file.length() <= MAX_REVIEWED_FILE_BYTES) { "${change.path} is too large for reviewed project backup." }
                totalBytes += file.length()
                require(totalBytes <= MAX_REVIEWED_CHANGE_BYTES) { "The pending change set is too large for one reviewed backup." }
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun requireCredential(): StoredGitHubCredential {
        val bytes = runCatching { credentialStore.read() }.getOrElse {
            credentialStore.delete()
            _githubState.value = GitHubConnectionState.Error("Saved GitHub access was unreadable and has been cleared. Sign in again.")
            error("Saved GitHub access was unreadable and has been cleared. Sign in again.")
        } ?: error("Sign in with GitHub before creating or syncing a backup.")
        val credential = runCatching {
            AppJson.decodeFromString(StoredGitHubCredential.serializer(), bytes.toString(StandardCharsets.UTF_8))
        }.getOrElse {
            credentialStore.delete()
            _githubState.value = GitHubConnectionState.Error("Saved GitHub access was unreadable and has been cleared. Sign in again.")
            error("Saved GitHub access was unreadable and has been cleared. Sign in again.")
        }
        require(credential.accessToken.isNotBlank() && credential.login.isNotBlank()) { "Saved GitHub access is invalid. Sign in again." }
        return credential
    }

    private fun initialGitHubState(): GitHubConnectionState {
        if (!validGitHubClientId(githubClientId)) {
            return GitHubConnectionState.Unavailable("Official GitHub backup is not configured in this build. Local history is still available.")
        }
        val bytes = runCatching { credentialStore.read() }.getOrElse {
            credentialStore.delete()
            return GitHubConnectionState.Error("Saved GitHub access was unreadable and has been cleared. Sign in again.")
        }
        val credential = bytes?.let { stored ->
            runCatching {
                AppJson.decodeFromString(StoredGitHubCredential.serializer(), stored.toString(StandardCharsets.UTF_8))
            }.getOrElse {
                credentialStore.delete()
                return GitHubConnectionState.Error("Saved GitHub access was unreadable and has been cleared. Sign in again.")
            }
        }
        if (credential != null && (credential.accessToken.isBlank() || credential.login.isBlank())) {
            credentialStore.delete()
            return GitHubConnectionState.Error("Saved GitHub access was invalid and has been cleared. Sign in again.")
        }
        return if (credential != null) GitHubConnectionState.Connected(credential.login) else GitHubConnectionState.Disconnected
    }

    private fun requireProjectRoot(projectPath: String): File {
        require(projectPath.isNotBlank()) { "Choose a robot project before opening Project Backup." }
        val root = File(projectPath).canonicalFile
        require(root.isDirectory) { "The selected robot project folder does not exist." }
        require(File(root, ".ares/project.json").isFile) { "The selected folder is not a canonical ARES robot project." }
        return root
    }

    private fun validateIdentity(name: String, email: String) {
        require(name.trim().length in 2..80) { "Enter the student or team member name used for saved versions." }
        require(email.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) { "Enter a valid email address for saved versions." }
    }

    private fun validateRepositoryName(name: String) {
        require(name.trim().matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}"))) {
            "GitHub repository names may use letters, numbers, dots, underscores, and dashes."
        }
    }

    private fun validateGitHubRepositoryUrl(raw: String) {
        val uri = runCatching { URI(raw) }.getOrElse { error("The GitHub repository URL is invalid.") }
        require(uri.scheme == "https" && uri.host.equals("github.com", ignoreCase = true) && uri.userInfo == null) {
            "Project Backup only accepts credential-free HTTPS repositories on github.com."
        }
    }

    companion object {
        const val MAX_REVIEWED_FILE_BYTES = 20L * 1024L * 1024L
        const val MAX_REVIEWED_CHANGE_BYTES = 100L * 1024L * 1024L
    }
}

internal fun validGitHubClientId(value: String): Boolean =
    value.matches(Regex("[A-Za-z0-9_-]{12,128}")) && !value.contains("mock", ignoreCase = true)

internal fun isSensitiveProjectPath(path: String): Boolean {
    val normalized = path.replace('\\', '/').lowercase(Locale.ROOT)
    val name = normalized.substringAfterLast('/')
    return normalized == "local.properties" ||
        name == ".env" || name.startsWith(".env.") ||
        name.endsWith(".jks") || name.endsWith(".keystore") || name.endsWith(".p12") || name.endsWith(".pfx") ||
        name in setOf("credentials.json", "service-account.json", "service_account.json") ||
        normalized.startsWith(".ares/secrets/")
}

private fun githubDeviceFailureMessage(code: String): String = when (code) {
    "expired_token" -> "The GitHub sign-in code expired. Start sign-in again."
    "access_denied" -> "GitHub sign-in was cancelled. Local project history is unchanged."
    "device_flow_disabled" -> "The ARES GitHub application is not enabled for device sign-in. Contact an ARES administrator."
    "incorrect_client_credentials" -> "This ARES build has an invalid GitHub application identity. Update the app or contact an administrator."
    else -> "GitHub could not complete sign-in ($code). Local project history is unchanged."
}

private class DefaultGitHubProjectApi : GitHubProjectApi {
    override fun beginDeviceAuthorization(clientId: String): GitHubDeviceAuthorization {
        val json = postForm(
            "https://github.com/login/device/code",
            mapOf("client_id" to clientId, "scope" to "repo"),
        )
        return GitHubDeviceAuthorization(
            deviceCode = json.requiredString("device_code"),
            userCode = json.requiredString("user_code"),
            verificationUri = json.requiredString("verification_uri"),
            expiresInSeconds = json.get("expires_in")?.asLong ?: 900L,
            intervalSeconds = json.get("interval")?.asLong ?: 5L,
        )
    }

    override fun pollDeviceAuthorization(clientId: String, deviceCode: String): GitHubDevicePollResult {
        val json = postForm(
            "https://github.com/login/oauth/access_token",
            mapOf(
                "client_id" to clientId,
                "device_code" to deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
        )
        json.get("access_token")?.asString?.takeIf(String::isNotBlank)?.let { token ->
            return GitHubDevicePollResult.Authorized(token, json.get("scope")?.asString.orEmpty())
        }
        return when (val error = json.get("error")?.asString.orEmpty()) {
            "authorization_pending" -> GitHubDevicePollResult.Pending
            "slow_down" -> GitHubDevicePollResult.SlowDown
            else -> GitHubDevicePollResult.Failed(error.ifBlank { "unknown_error" })
        }
    }

    override fun currentLogin(token: String): String = authorizedJson("GET", "https://api.github.com/user", token)
        .requiredString("login")

    override fun createPrivateRepository(token: String, name: String, description: String): String {
        val body = """{"name":${jsonString(name)},"description":${jsonString(description)},"private":true,"auto_init":false}"""
        return authorizedJson("POST", "https://api.github.com/user/repos", token, body).requiredString("clone_url")
    }

    private fun postForm(url: String, values: Map<String, String>): com.google.gson.JsonObject {
        val body = values.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
        return requestJson(url, "POST", body, token = null, contentType = "application/x-www-form-urlencoded")
    }

    private fun authorizedJson(method: String, url: String, token: String, body: String? = null): com.google.gson.JsonObject =
        requestJson(url, method, body, token, "application/json")

    private fun requestJson(
        rawUrl: String,
        method: String,
        body: String?,
        token: String?,
        contentType: String,
    ): com.google.gson.JsonObject {
        val uri = URI(rawUrl)
        require(uri.scheme == "https" && uri.host in setOf("github.com", "api.github.com")) { "Unexpected GitHub endpoint." }
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "ARES-Analytics Project Backup")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText().take(256 * 1024) }.orEmpty()
            check(status in 200..299) {
                when (status) {
                    401 -> "GitHub access was revoked or expired. Disconnect GitHub and sign in again."
                    403 -> "GitHub denied this operation. Check organization policy and repository permissions."
                    422 -> "GitHub could not create that repository. The name may already be in use."
                    else -> "GitHub returned HTTP $status. No local project files were changed."
                }
            }
            return JsonParser.parseString(response).asJsonObject
        } finally {
            connection.disconnect()
        }
    }
}

private fun com.google.gson.JsonObject.requiredString(name: String): String =
    get(name)?.asString?.takeIf(String::isNotBlank) ?: error("GitHub response is missing '$name'.")

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}
