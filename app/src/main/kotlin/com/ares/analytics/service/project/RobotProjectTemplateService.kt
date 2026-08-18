package com.ares.analytics.service.project

import com.ares.analytics.service.writeFileAtomically
import com.ares.analytics.service.drivebase.DrivebaseProjectRepository
import com.ares.analytics.service.hardware.HardwareSetupService
import com.ares.analytics.service.tuning.TuningProfileRepository
import com.ares.analytics.shared.League
import com.ares.analytics.util.ProjectLayout
import com.areslib.catalog.CapabilityCatalogCodec
import com.areslib.drivetrain.DrivetrainDocumentCodec
import com.areslib.project.AresLeague
import com.areslib.project.AresProjectMetadataCodec
import com.areslib.routine.AutonomousCatalogCodec
import com.areslib.tuning.TuningComponentDocumentCodec
import com.areslib.tuning.TuningProfileDocumentCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

private val PROJECT_TEMPLATE_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
}

/** Immutable identity for one reviewed robot-project starter archive. */
data class RobotProjectTemplate(
    val id: String,
    val displayName: String,
    val league: League,
    val aresVersion: String,
    val revision: String,
    val archiveUrl: String,
    val archiveSha256: String,
    /** Physical deployment policy carried into the newly created workspace. */
    val deploymentPolicy: RobotProjectDeploymentPolicy = RobotProjectDeploymentPolicy.SIMULATION_ONLY_REFERENCE,
)

data class RobotProjectCreationRequest(
    val parentDirectory: File,
    val folderName: String,
    val league: League,
    val teamId: String,
    val seasonId: String,
    val robotId: String,
    val robotName: String,
)

data class RobotProjectCreationPlan(
    val template: RobotProjectTemplate,
    val destination: File,
    val issues: List<String>,
) {
    val canCreate: Boolean get() = issues.isEmpty()
}

enum class RobotProjectTemplateSource { VERIFIED_CACHE, VERIFIED_DOWNLOAD }

data class RobotProjectCreationResult(
    val destination: File,
    val template: RobotProjectTemplate,
    val source: RobotProjectTemplateSource,
)

@Serializable
enum class RobotProjectDeploymentPolicy {
    SIMULATION_ONLY_REFERENCE,
    HARDWARE_REVIEW_REQUIRED,
    DEPLOYMENT_READY,
}

@Serializable
internal data class RobotProjectTemplateProvenance(
    val schemaVersion: Int = 1,
    val templateId: String,
    val templateRevision: String,
    val templateArchiveSha256: String,
    val aresVersion: String,
    val deploymentPolicy: RobotProjectDeploymentPolicy = RobotProjectDeploymentPolicy.SIMULATION_ONLY_REFERENCE,
)

@Serializable
private data class RobotIdentity(
    val teamId: String,
    val seasonId: String,
    val robotId: String,
    val name: String,
    val league: String,
)

/**
 * Creates a robot repository from a hash-pinned official source archive.
 *
 * Creation is deliberately all-or-nothing: files are extracted into a sibling staging directory,
 * validated and personalized there, then moved into the requested destination without replacement.
 * A verified archive is cached so a previously downloaded starter can be reused offline. No
 * existing directory or user-owned source is ever merged, deleted, or overwritten.
 */
class RobotProjectTemplateService(
    private val cacheDirectory: File = File(
        System.getProperty("user.home"),
        ".ares-analytics/project-templates",
    ),
    templates: List<RobotProjectTemplate> = OFFICIAL_PROJECT_TEMPLATES,
    private val archiveDownloader: (RobotProjectTemplate, File) -> Unit = ::downloadArchive,
    private val androidSdkLocator: () -> File? = ::locateAndroidSdk,
    private val projectPublisher: (Path, Path) -> Unit = { staging, destination ->
        publishProjectDirectory(staging, destination)
    },
) {
    private val templatesByLeague = templates.associateBy(RobotProjectTemplate::league)

    fun templateFor(league: League): RobotProjectTemplate =
        requireNotNull(templatesByLeague[league]) { "No reviewed ${league.name} starter is bundled with this app." }

    fun plan(request: RobotProjectCreationRequest): RobotProjectCreationPlan {
        val template = templateFor(request.league)
        val folderName = request.folderName.trim()
        val parent = runCatching { request.parentDirectory.canonicalFile }.getOrElse { request.parentDirectory.absoluteFile }
        val destination = File(parent, folderName)
        val issues = buildList {
            if (!parent.isDirectory) add("Choose an existing parent folder for the new robot project.")
            if (!parent.canWrite()) add("ARES cannot write to the selected parent folder.")
            projectFolderNameError(folderName)?.let(::add)
            if (folderName.isNotEmpty()) {
                val canonicalDestination = runCatching { destination.canonicalFile }.getOrNull()
                if (canonicalDestination == null || canonicalDestination.parentFile != parent) {
                    add("The project folder must be a direct child of the selected parent folder.")
                }
                if (Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    add("A file or folder already exists at ${destination.path}.")
                }
            }
        }
        return RobotProjectCreationPlan(template, destination, issues.distinct())
    }

    suspend fun create(
        request: RobotProjectCreationRequest,
        onProgress: (String) -> Unit = {},
    ): RobotProjectCreationResult = withContext(Dispatchers.IO) {
        val initialPlan = plan(request)
        require(initialPlan.canCreate) { initialPlan.issues.joinToString(" ") }
        validateIdentity(request)

        val parent = request.parentDirectory.canonicalFile
        val destination = initialPlan.destination.canonicalFile
        val staging = Files.createTempDirectory(parent.toPath(), ".${request.folderName}.ares-partial-").toFile()
        check(staging.parentFile.canonicalFile == parent) { "Project staging directory escaped the selected parent." }

        var published = false
        try {
            onProgress("Checking the verified ${initialPlan.template.displayName} starter…")
            val (archive, source) = obtainVerifiedArchive(initialPlan.template, onProgress)
            onProgress("Unpacking the starter into a protected staging folder…")
            extractArchive(archive, staging)
            personalizeProject(staging, request, initialPlan.template)

            ProjectLayout.validationError(staging.path, request.league)?.let { validationError -> error(validationError) }
            check(!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                "The destination appeared while the project was being created; nothing was replaced."
            }
            onProgress("Publishing the completed project…")
            projectPublisher(staging.toPath(), destination.toPath())
            published = true
            onProgress("Project created. ARES can now open Robot Studio and the simulator.")
            RobotProjectCreationResult(destination, initialPlan.template, source)
        } finally {
            if (!published && staging.exists()) staging.deleteRecursively()
        }
    }

    private fun obtainVerifiedArchive(
        template: RobotProjectTemplate,
        onProgress: (String) -> Unit,
    ): Pair<File, RobotProjectTemplateSource> {
        cacheDirectory.mkdirs()
        check(cacheDirectory.isDirectory) { "ARES could not create its project-template cache." }
        val cacheFile = File(cacheDirectory, "${template.id}-${template.revision}.zip")
        if (cacheFile.isFile && sha256(cacheFile) == template.archiveSha256) {
            return cacheFile to RobotProjectTemplateSource.VERIFIED_CACHE
        }
        if (cacheFile.exists() && !cacheFile.delete()) {
            error("A damaged cached starter could not be removed. Delete ${cacheFile.path}, then try again.")
        }

        onProgress("Downloading the pinned ${template.displayName} starter once for offline reuse…")
        writeFileAtomically(cacheFile) { temporary ->
            archiveDownloader(template, temporary)
            val actualHash = sha256(temporary)
            check(actualHash == template.archiveSha256) {
                "The downloaded starter did not match its reviewed SHA-256. Expected ${template.archiveSha256}; got $actualHash."
            }
        }
        return cacheFile to RobotProjectTemplateSource.VERIFIED_DOWNLOAD
    }

    private fun personalizeProject(
        root: File,
        request: RobotProjectCreationRequest,
        template: RobotProjectTemplate,
    ) {
        val identity = RobotIdentity(
            teamId = request.teamId.trim(),
            seasonId = request.seasonId.trim(),
            robotId = request.robotId.trim(),
            name = request.robotName.trim(),
            league = request.league.name,
        )
        writeTextAtomically(File(root, ".ares-robot.json"), PROJECT_TEMPLATE_JSON.encodeToString(identity))
        configureLocalBuildEnvironment(root, request.league)

        val metadataFile = File(root, ".ares/project.json")
        check(metadataFile.isFile) { "The reviewed starter is missing .ares/project.json." }
        val oldMetadata = AresProjectMetadataCodec.decode(metadataFile.readText())
        val expectedLeague = if (request.league == League.FTC) AresLeague.FTC else AresLeague.FRC
        check(oldMetadata.league == expectedLeague) { "The starter's project metadata has the wrong league." }
        val personalizedProjectId = projectId(request.teamId, request.robotId)
        val personalizedMetadata = oldMetadata.copy(projectId = personalizedProjectId)
        writeTextAtomically(metadataFile, AresProjectMetadataCodec.encode(personalizedMetadata))

        val actionCatalogFile = File(root, ".ares/action-catalog.json")
        check(actionCatalogFile.isFile) { "The reviewed starter is missing .ares/action-catalog.json." }
        val actionCatalog = CapabilityCatalogCodec.decode(actionCatalogFile.readText())
        writeTextAtomically(
            actionCatalogFile,
            CapabilityCatalogCodec.encode(actionCatalog.copy(projectId = personalizedProjectId)),
        )

        val autonomousCatalogFile = File(root, ".ares/autonomous-catalog.json")
        check(autonomousCatalogFile.isFile) { "The reviewed starter is missing .ares/autonomous-catalog.json." }
        val autonomousCatalog = AutonomousCatalogCodec.decode(autonomousCatalogFile.readText())
        writeTextAtomically(
            autonomousCatalogFile,
            AutonomousCatalogCodec.encode(autonomousCatalog.copy(projectId = personalizedProjectId)),
        )

        personalizeRuntimeIdentity(root, request)

        val provenance = RobotProjectTemplateProvenance(
            templateId = template.id,
            templateRevision = template.revision,
            templateArchiveSha256 = template.archiveSha256,
            aresVersion = template.aresVersion,
            deploymentPolicy = template.deploymentPolicy,
        )
        writeTextAtomically(File(root, ".ares/template-provenance.json"), PROJECT_TEMPLATE_JSON.encodeToString(provenance))
    }

    /**
     * Rebinds canonical drivetrain and tuning documents to the new robot rather than leaving
     * Team 23247's application/runtime identity inside a different team's project.
     *
     * Parameter and component UIDs remain stable because they identify schema fields consumed by
     * the reviewed season runtime. Only robot-, drivebase-, and profile-level ownership changes.
     */
    private fun personalizeRuntimeIdentity(root: File, request: RobotProjectCreationRequest) {
        val ares = File(root, ".ares")
        val tuningRepository = TuningProfileRepository()
        val originalTuning = tuningRepository.load(root.path).getOrThrow()
        val drivetrainFiles = File(ares, "drivetrains")
            .listFiles { file -> file.extension == "aresdrivetrain" }
            ?.sortedBy(File::getName)
            .orEmpty()
        require(drivetrainFiles.size <= 1) {
            "The reviewed starter contains multiple drivebases. Choose one explicitly before using it as a novice template."
        }
        val drivetrains = drivetrainFiles.associateWith { file -> DrivetrainDocumentCodec.decode(file.readText()) }
        val projectUid = runtimeProjectUid(request)
        val drivebaseUidMap = drivetrains.values.associate { drivetrain ->
            drivetrain.uid to "$projectUid.drivebase.${uidSegment(drivetrain.drivebaseId, "primary")}"
        }
        val profileUidMap = originalTuning.profiles.associate { profile ->
            profile.uid to "$projectUid.profile.${uidSegment(profile.profileId, "competition")}"
        }

        drivetrains.forEach { (file, drivetrain) ->
            val profileUid = requireNotNull(profileUidMap[drivetrain.canonicalProfileUid]) {
                "The reviewed starter drivebase '${drivetrain.displayName}' references missing tuning profile '${drivetrain.canonicalProfileUid}'."
            }
            writeTextAtomically(
                file,
                DrivetrainDocumentCodec.encode(
                    drivetrain.copy(
                        uid = drivebaseUidMap.getValue(drivetrain.uid),
                        canonicalProfileUid = profileUid,
                    ),
                ),
            )
        }

        File(ares, "tuning-components").listFiles { file -> file.extension == "arestuningcomponent" }
            ?.sortedBy(File::getName)
            .orEmpty()
            .forEach { file ->
                val document = TuningComponentDocumentCodec.decode(file.readText())
                writeTextAtomically(file, TuningComponentDocumentCodec.encode(document.copy(projectUid = projectUid)))
            }

        val profileFiles = File(ares, "tuning").listFiles { file -> file.extension == "arestuning" }
            ?.sortedBy(File::getName)
            .orEmpty()
        val profilesByUid = originalTuning.profiles.associateBy { it.uid }
        profileFiles.forEach { file ->
            val profile = TuningProfileDocumentCodec.decode(file.readText(), originalTuning.catalog)
            require(profilesByUid.containsKey(profile.uid)) { "Unexpected tuning profile '${profile.uid}' in reviewed starter." }
            val personalized = profile.copy(
                uid = profileUidMap.getValue(profile.uid),
                projectUid = projectUid,
                drivebaseUid = profile.drivebaseUid?.let { oldUid ->
                    requireNotNull(drivebaseUidMap[oldUid]) { "Tuning profile '${profile.uid}' references missing drivebase '$oldUid'." }
                },
                baseProfileUid = profile.baseProfileUid?.let { oldUid ->
                    requireNotNull(profileUidMap[oldUid]) { "Tuning profile '${profile.uid}' references missing base profile '$oldUid'." }
                },
            )
            writeTextAtomically(file, TuningProfileDocumentCodec.encode(personalized, originalTuning.catalog))
        }

        tuningRepository.load(root.path).getOrThrow()
        DrivebaseProjectRepository().load(root.path).getOrThrow()
    }

    private fun runtimeProjectUid(request: RobotProjectCreationRequest): String = listOf(
        "team${request.teamId.filter(Char::isDigit)}",
        request.league.name.lowercase(),
        uidSegment("season${request.seasonId}", "seasonunknown"),
        uidSegment(request.robotId, "robot"),
    ).joinToString(".")

    private fun uidSegment(value: String, fallback: String): String {
        val normalized = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return when {
            normalized.isBlank() -> fallback
            normalized.first().isLetter() -> normalized
            else -> "id$normalized"
        }
    }

    private fun writeTextAtomically(file: File, content: String) {
        writeFileAtomically(file) { temporary ->
            Files.writeString(
                temporary.toPath(),
                content.trimEnd() + System.lineSeparator(),
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
    }

    private fun configureLocalBuildEnvironment(root: File, league: League) {
        if (league != League.FTC) return
        val localProperties = File(root, "local.properties")
        if (localProperties.exists()) return
        val sdk = androidSdkLocator()?.takeIf(File::isDirectory)?.canonicalFile ?: return
        writeTextAtomically(localProperties, "sdk.dir=${sdk.path.replace('\\', '/')}")
    }

    private fun validateIdentity(request: RobotProjectCreationRequest) {
        require(request.teamId.isNotBlank() && request.teamId.all(Char::isDigit)) {
            "Enter a numeric FIRST team number before creating the project."
        }
        require(request.seasonId.isNotBlank()) { "Enter the competition season before creating the project." }
        require(request.robotId.matches(Regex("[A-Za-z][A-Za-z0-9._-]{0,63}"))) {
            "Robot ID must start with a letter and use only letters, numbers, dots, underscores, or dashes."
        }
    }

    companion object {
        const val MAX_ARCHIVE_BYTES: Long = 250L * 1024L * 1024L
        const val MAX_EXTRACTED_BYTES: Long = 750L * 1024L * 1024L
        const val MAX_ARCHIVE_ENTRIES: Int = 20_000

        val OFFICIAL_PROJECT_TEMPLATES: List<RobotProjectTemplate> = listOf(
            RobotProjectTemplate(
                id = "ares-ftc-9.2.1",
                displayName = "ARES FTC",
                league = League.FTC,
                aresVersion = "9.2.1",
                revision = "a9cb5fb45881d28e0576318a32f2741dbf77b4ab",
                archiveUrl = "https://github.com/ARES-23247/ARES-FTC/archive/a9cb5fb45881d28e0576318a32f2741dbf77b4ab.zip",
                archiveSha256 = "68775b39bb9629959110ac97a32e92506e11b0001e60cedd1bfe678b2e5af972",
            ),
            RobotProjectTemplate(
                id = "ares-frc-9.2.1",
                displayName = "ARES FRC",
                league = League.FRC,
                aresVersion = "9.2.1",
                revision = "fb9b81886ac1bc6502dafb97dbb229417a7fcd3c",
                archiveUrl = "https://github.com/ARES-23247/ARES-FRC/archive/fb9b81886ac1bc6502dafb97dbb229417a7fcd3c.zip",
                archiveSha256 = "de5aea597ecd043272adb0f79c6e56eddd806e4ef5d53b6f7f575f94548c55d3c",
            ),
        )

        internal fun projectFolderNameError(folderName: String): String? = when {
            folderName.isBlank() -> "Enter a folder name for the new robot project."
            !folderName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) ->
                "Folder names may use letters, numbers, dots, underscores, and dashes."
            folderName.endsWith('.') || folderName.endsWith(' ') ->
                "Folder names cannot end with a dot or space."
            folderName.uppercase() in WINDOWS_RESERVED_NAMES ->
                "That folder name is reserved by Windows. Choose another name."
            else -> null
        }

        internal fun projectId(teamId: String, robotId: String): String {
            val robot = robotId.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-', '.', '_')
            return "team${teamId.filter(Char::isDigit)}-${robot.ifBlank { "robot" }}".take(64)
        }

        private fun downloadArchive(template: RobotProjectTemplate, destination: File) {
            val connection = URL(template.archiveUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Accept", "application/zip")
            connection.setRequestProperty("User-Agent", "ARES-Analytics/${template.aresVersion}")
            try {
                check(connection.responseCode in 200..299) {
                    "The official starter download failed with HTTP ${connection.responseCode}. Check the internet connection and try again."
                }
                val announcedLength = connection.contentLengthLong
                check(announcedLength < 0 || announcedLength <= MAX_ARCHIVE_BYTES) { "The starter archive is unexpectedly large." }
                connection.inputStream.use { input ->
                    BufferedOutputStream(FileOutputStream(destination)).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            check(total <= MAX_ARCHIVE_BYTES) { "The starter archive exceeded the safe download limit." }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

        private fun locateAndroidSdk(): File? {
            val candidates = buildList {
                System.getenv("ANDROID_HOME")?.let { add(File(it)) }
                System.getenv("ANDROID_SDK_ROOT")?.let { add(File(it)) }
                System.getenv("LOCALAPPDATA")?.let { add(File(it, "Android/Sdk")) }
                val home = System.getProperty("user.home")
                add(File(home, "Android/Sdk"))
                add(File(home, "Library/Android/sdk"))
            }
            return candidates.firstOrNull(File::isDirectory)
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        private fun extractArchive(archive: File, destination: File) {
            var entryCount = 0
            var extractedBytes = 0L
            var archiveRoot: String? = null
            ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    check(entryCount <= MAX_ARCHIVE_ENTRIES) { "The starter archive contains too many files." }
                    val rawName = entry.name
                    check(rawName.isNotBlank() && !rawName.contains('\\')) { "The starter archive contains an invalid path." }
                    val parts = rawName.split('/').filter(String::isNotEmpty)
                    if (parts.isEmpty()) continue
                    val root = parts.first()
                    if (archiveRoot == null) archiveRoot = root
                    check(root == archiveRoot) { "The starter archive contains multiple roots." }
                    if (parts.size == 1) continue
                    val relative = parts.drop(1).joinToString(File.separator)
                    val target = File(destination, relative).canonicalFile
                    check(target.toPath().startsWith(destination.canonicalFile.toPath())) {
                        "The starter archive attempted to write outside the new project."
                    }
                    if (entry.isDirectory) {
                        check(target.mkdirs() || target.isDirectory) { "Could not create ${target.path}." }
                    } else {
                        check(target.parentFile.mkdirs() || target.parentFile.isDirectory) { "Could not create ${target.parent}." }
                        BufferedOutputStream(FileOutputStream(target)).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                extractedBytes += read
                                check(extractedBytes <= MAX_EXTRACTED_BYTES) { "The starter expanded beyond its safe size limit." }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            check(entryCount > 0 && archiveRoot != null) { "The starter archive was empty." }
            File(destination, "gradlew").takeIf(File::isFile)?.setExecutable(true, false)
        }

        private val WINDOWS_RESERVED_NAMES = buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL"))
            (1..9).forEach { index ->
                add("COM$index")
                add("LPT$index")
            }
        }
    }
}

/**
 * Publishes a fully validated sibling directory without replacing an existing path.
 *
 * Local filesystems normally support the atomic rename. Cloud-backed providers such as
 * OneDrive can reject `ATOMIC_MOVE` even for siblings, sometimes with a generic filesystem
 * exception. In that case a plain, non-replacing move is the narrow fallback. No recursive
 * copy is used, so ARES never merges into a destination.
 */
internal fun publishProjectDirectory(staging: Path, destination: Path) {
    publishProjectDirectory(staging, destination) { source, target, options ->
        Files.move(source, target, *options)
    }
}

internal fun publishProjectDirectory(
    staging: Path,
    destination: Path,
    mover: (Path, Path, Array<out CopyOption>) -> Path,
) {
    val normalizedStaging = staging.toAbsolutePath().normalize()
    val normalizedDestination = destination.toAbsolutePath().normalize()
    require(normalizedStaging.parent == normalizedDestination.parent) {
        "Project staging and destination directories must be siblings."
    }
    if (Files.exists(normalizedDestination, LinkOption.NOFOLLOW_LINKS)) {
        throw FileAlreadyExistsException(
            normalizedDestination.toString(),
            null,
            "The destination appeared while the project was being created; nothing was replaced.",
        )
    }

    try {
        mover(
            normalizedStaging,
            normalizedDestination,
            arrayOf(StandardCopyOption.ATOMIC_MOVE),
        )
        return
    } catch (atomicFailure: IOException) {
        if (Files.exists(normalizedDestination, LinkOption.NOFOLLOW_LINKS)) {
            throw FileAlreadyExistsException(
                normalizedDestination.toString(),
                null,
                "The destination appeared while the project was being created; nothing was replaced.",
            ).also { it.addSuppressed(atomicFailure) }
        }

        try {
            mover(normalizedStaging, normalizedDestination, emptyArray())
            return
        } catch (fallbackFailure: IOException) {
            val stagingStillExists = Files.exists(normalizedStaging, LinkOption.NOFOLLOW_LINKS)
            val destinationNowExists = Files.exists(normalizedDestination, LinkOption.NOFOLLOW_LINKS)
            // Some cloud providers can report an error after completing the rename. Reconcile
            // that ambiguous result only when our unique staging entry disappeared and the
            // previously absent destination now exists.
            if (!stagingStillExists && Files.isDirectory(normalizedDestination, LinkOption.NOFOLLOW_LINKS)) {
                return
            }
            if (stagingStillExists && destinationNowExists) {
                throw FileAlreadyExistsException(
                    normalizedDestination.toString(),
                    null,
                    "The destination appeared while the project was being created; nothing was replaced.",
                ).also {
                    it.addSuppressed(atomicFailure)
                    it.addSuppressed(fallbackFailure)
                }
            }
            throw IOException(
                "ARES could not publish the completed project folder. If this location is managed by OneDrive " +
                    "or another cloud provider, wait for it to finish syncing or choose a local folder, then try again.",
                fallbackFailure,
            ).also { it.addSuppressed(atomicFailure) }
        }
    }
}

/** Returns a fail-closed deploy reason for downloaded reference projects, or null for normal projects. */
internal fun templateDeploymentBlockReason(projectRoot: File): String? {
    val provenanceFile = File(projectRoot, ".ares/template-provenance.json")
    if (!provenanceFile.exists()) return null
    val provenance = runCatching {
        PROJECT_TEMPLATE_JSON.decodeFromString<RobotProjectTemplateProvenance>(provenanceFile.readText())
    }.getOrElse {
        return "Template provenance is invalid. Deployment is blocked; create a fresh project or ask a mentor to inspect the repository."
    }
    return when (provenance.deploymentPolicy) {
        RobotProjectDeploymentPolicy.DEPLOYMENT_READY -> null
        RobotProjectDeploymentPolicy.HARDWARE_REVIEW_REQUIRED -> {
            val metadataFile = File(projectRoot, ".ares/project.json")
            val league = runCatching { AresProjectMetadataCodec.decode(metadataFile.readText()).league }
                .getOrElse {
                    return "Canonical project metadata is missing or invalid. Deployment is blocked until project identity and hardware are reviewed."
                }
            HardwareSetupService().deploymentBlockReason(
                projectRoot.path,
                if (league == AresLeague.FTC) League.FTC else League.FRC,
            )
        }
        RobotProjectDeploymentPolicy.SIMULATION_ONLY_REFERENCE ->
            "This downloaded ${provenance.templateId} project is a simulator/reference starting point, not a hardware-neutral robot image. " +
                "Physical deployment is blocked until ARES provides a reviewed generic runtime template for this league."
    }
}
