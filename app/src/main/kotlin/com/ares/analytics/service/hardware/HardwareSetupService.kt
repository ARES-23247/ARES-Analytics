package com.ares.analytics.service.hardware

import com.ares.analytics.service.drivebase.DriveHardwareRole
import com.ares.analytics.service.drivebase.DrivebaseProjectRepository
import com.ares.analytics.service.writeFileAtomically
import com.ares.analytics.shared.League
import com.ares.analytics.viewmodel.project.SubsystemProjectRepository
import com.areslib.drivetrain.DrivetrainDocumentCodec
import com.areslib.subsystem.SubsystemDocumentCodec
import com.areslib.subsystem.SubsystemHardwareKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

enum class HardwareInventoryOwner { DRIVEBASE, SUBSYSTEM }

enum class HardwareAddressKind(val label: String) {
    FTC_HARDWARE_MAP("FTC hardware-map name"),
    CAN("CAN device"),
    PWM("PWM channel"),
    I2C("I2C device"),
    DIO("digital-input channel"),
    ANALOG("analog-input channel"),
    UNKNOWN("unclassified address"),
}

enum class HardwareIssueSeverity { INFO, WARNING, ERROR }

data class HardwareInventoryIssue(
    val severity: HardwareIssueSeverity,
    val message: String,
    val itemUid: String? = null,
)

data class HardwareInventoryItem(
    val uid: String,
    val displayName: String,
    val owner: HardwareInventoryOwner,
    val ownerDisplayName: String,
    val sourcePath: String,
    val role: String,
    val addressKind: HardwareAddressKind,
    val address: String,
    val bus: String? = null,
    val required: Boolean,
    val inverted: Boolean,
) {
    val addressDescription: String
        get() = when {
            address.isBlank() -> "Not configured"
            bus.isNullOrBlank() -> "${addressKind.label}: $address"
            else -> "${addressKind.label}: $address on $bus"
        }
}

enum class HardwareReviewStatus {
    NOT_REVIEWED,
    CURRENT,
    STALE,
    INVALID,
}

data class HardwareSetupSnapshot(
    val projectPath: String,
    val league: League,
    val inventoryHash: String,
    val items: List<HardwareInventoryItem>,
    val issues: List<HardwareInventoryIssue>,
    val reviewStatus: HardwareReviewStatus,
    val reviewedBy: String? = null,
) {
    val errorIssues: List<HardwareInventoryIssue>
        get() = issues.filter { it.severity == HardwareIssueSeverity.ERROR }

    val canReview: Boolean
        get() = items.isNotEmpty() && errorIssues.isEmpty()
}

data class HardwareReviewRequest(
    val reviewerName: String,
    val wiringMatched: Boolean,
    val addressesChecked: Boolean,
    val directionsChecked: Boolean,
    val neutralOutputsChecked: Boolean,
    val limitsChecked: Boolean,
)

@Serializable
private data class HardwareSourceFingerprint(
    val path: String,
    val sha256: String,
)

@Serializable
private data class HardwareReviewDocument(
    val schemaVersion: Int = 1,
    val league: String,
    val inventoryHash: String,
    val reviewedBy: String,
    val wiringMatched: Boolean,
    val addressesChecked: Boolean,
    val directionsChecked: Boolean,
    val neutralOutputsChecked: Boolean,
    val limitsChecked: Boolean,
    val sources: List<HardwareSourceFingerprint>,
)

private val HARDWARE_REVIEW_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = false
}

/**
 * Aggregates physical identity from canonical drivetrain and subsystem documents.
 *
 * This service never scans Kotlin and never creates a competing hardware map. The existing
 * descriptor builders remain the only editors of addresses, inversion, safe output, and limits.
 * A review records the exact descriptor hashes and becomes stale after any later edit.
 */
class HardwareSetupService(
    private val drivebaseRepository: DrivebaseProjectRepository = DrivebaseProjectRepository(),
    private val subsystemRepository: SubsystemProjectRepository = SubsystemProjectRepository(),
) {
    fun inspect(projectPath: String, league: League): HardwareSetupSnapshot {
        val root = File(projectPath).canonicalFile
        require(root.isDirectory) { "Project directory does not exist: ${root.path}" }

        val issues = mutableListOf<HardwareInventoryIssue>()
        val items = mutableListOf<HardwareInventoryItem>()
        val sources = mutableListOf<HardwareSourceFingerprint>()

        drivebaseRepository.load(root.path).fold(
            onSuccess = { drivebase ->
                if (drivebase == null) {
                    issues += HardwareInventoryIssue(
                        HardwareIssueSeverity.ERROR,
                        "Configure a drivebase before reviewing physical hardware.",
                    )
                } else {
                    val canonical = requireNotNull(drivebase.canonical) {
                        "The loaded drivebase lost its canonical document. Reload it in Drivebase Builder."
                    }
                    val sourceFile = File(root, ".ares/drivetrains")
                        .listFiles { file -> file.isFile && file.extension.equals("aresdrivetrain", ignoreCase = true) }
                        .orEmpty()
                        .singleOrNull { file ->
                            runCatching { DrivetrainDocumentCodec.decode(file.readText()).uid == canonical.uid }
                                .getOrDefault(false)
                        }
                    requireNotNull(sourceFile) {
                        "The canonical drivetrain source for '${canonical.uid}' is missing or duplicated."
                    }
                    val sourcePath = ".ares/drivetrains/${sourceFile.name}"
                    sources += HardwareSourceFingerprint(sourcePath, DrivetrainDocumentCodec.contentHash(canonical))
                    drivebase.hardware
                        .filterNot { it.id == canonical.uid }
                        .forEach { device ->
                            val address = device.canId?.toString() ?: device.hardwareName.trim()
                            val addressKind = if (league == League.FTC) {
                                HardwareAddressKind.FTC_HARDWARE_MAP
                            } else {
                                HardwareAddressKind.CAN
                            }
                            items += HardwareInventoryItem(
                                uid = "drivebase:${device.id}",
                                displayName = device.displayName,
                                owner = HardwareInventoryOwner.DRIVEBASE,
                                ownerDisplayName = drivebase.displayName,
                                sourcePath = sourcePath,
                                role = device.role.readableName(),
                                addressKind = addressKind,
                                address = address,
                                bus = device.canBus?.takeIf(String::isNotBlank),
                                required = device.required,
                                inverted = device.inverted,
                            )
                        }
                }
            },
            onFailure = { error ->
                issues += HardwareInventoryIssue(
                    HardwareIssueSeverity.ERROR,
                    error.message ?: "The drivetrain hardware document could not be loaded.",
                )
            },
        )

        val subsystemListing = subsystemRepository.list(root.path)
        subsystemListing.diagnostics.forEach { diagnostic ->
            issues += HardwareInventoryIssue(
                HardwareIssueSeverity.ERROR,
                "${diagnostic.file.name}: ${diagnostic.message}",
            )
        }
        subsystemListing.documents.forEach { subsystem ->
            val sourcePath = ".ares/subsystems/${subsystem.documentId}.aressubsystem"
            sources += HardwareSourceFingerprint(sourcePath, SubsystemDocumentCodec.contentHash(subsystem))
            subsystem.hardware.forEach { device ->
                val address = when (league) {
                    League.FTC -> device.connection.hardwareMapName.orEmpty().trim()
                    League.FRC -> device.connection.canId?.toString()
                        ?: device.connection.channel?.toString()
                        ?: ""
                }
                val addressKind = when (league) {
                    League.FTC -> HardwareAddressKind.FTC_HARDWARE_MAP
                    League.FRC -> device.addressKind()
                }
                val bus = when {
                    league == League.FRC && addressKind == HardwareAddressKind.CAN -> device.connection.canBus
                    else -> null
                }
                items += HardwareInventoryItem(
                    uid = "subsystem:${subsystem.documentId}:${device.uid}",
                    displayName = device.displayName,
                    owner = HardwareInventoryOwner.SUBSYSTEM,
                    ownerDisplayName = subsystem.displayName,
                    sourcePath = sourcePath,
                    role = device.kind.readableName(),
                    addressKind = addressKind,
                    address = address,
                    bus = bus?.takeIf(String::isNotBlank),
                    required = device.required,
                    inverted = device.inverted,
                )
            }
        }

        items.filter { it.required && it.address.isBlank() }.forEach { item ->
            issues += HardwareInventoryIssue(
                HardwareIssueSeverity.ERROR,
                "${item.displayName} is required but has no ${item.addressKind.label}.",
                item.uid,
            )
        }
        items.filter { it.address.isNotBlank() }
            .groupBy(::collisionKey)
            .filterValues { it.size > 1 }
            .values
            .forEach { conflicts ->
                val address = conflicts.first().addressDescription
                issues += HardwareInventoryIssue(
                    HardwareIssueSeverity.ERROR,
                    "$address is claimed by ${conflicts.joinToString { "${it.ownerDisplayName} / ${it.displayName}" }}. Physical addresses must have one owner.",
                )
            }
        if (items.isEmpty() && issues.none { it.severity == HardwareIssueSeverity.ERROR }) {
            issues += HardwareInventoryIssue(
                HardwareIssueSeverity.ERROR,
                "No physical hardware is declared in the canonical drivetrain or subsystem documents.",
            )
        }

        val normalizedItems = items.sortedWith(
            compareBy<HardwareInventoryItem> { it.owner.ordinal }
                .thenBy { it.ownerDisplayName.lowercase() }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.uid },
        )
        val normalizedSources = sources.distinctBy(HardwareSourceFingerprint::path).sortedBy(HardwareSourceFingerprint::path)
        val inventoryHash = inventoryHash(league, normalizedSources, normalizedItems)
        val (reviewStatus, reviewedBy) = readReview(root, league, inventoryHash, normalizedSources, issues)

        return HardwareSetupSnapshot(
            projectPath = root.path,
            league = league,
            inventoryHash = inventoryHash,
            items = normalizedItems,
            issues = issues.distinct().sortedWith(
                compareByDescending<HardwareInventoryIssue> { it.severity.ordinal }.thenBy { it.message },
            ),
            reviewStatus = reviewStatus,
            reviewedBy = reviewedBy,
        )
    }

    fun saveReview(projectPath: String, league: League, request: HardwareReviewRequest): HardwareSetupSnapshot {
        val snapshot = inspect(projectPath, league)
        require(snapshot.canReview) {
            snapshot.errorIssues.joinToString(" ") { it.message }.ifBlank { "Fix hardware mapping errors before recording a review." }
        }
        val reviewer = request.reviewerName.trim()
        require(reviewer.length in 2..80) { "Enter the name of the student or mentor who compared the configuration with the robot." }
        require(
            request.wiringMatched && request.addressesChecked && request.directionsChecked &&
                request.neutralOutputsChecked && request.limitsChecked,
        ) { "Complete every hardware review check before recording the review." }

        val sourcePaths = snapshot.items.map(HardwareInventoryItem::sourcePath).distinct().sorted()
        val sources = sourcePaths.map { path ->
            val file = File(snapshot.projectPath, path).canonicalFile
            require(file.isFile && file.toPath().startsWith(File(snapshot.projectPath).canonicalFile.toPath())) {
                "Hardware source $path is missing or outside the project."
            }
            sourceFingerprint(path, file)
        }
        val review = HardwareReviewDocument(
            league = league.name,
            inventoryHash = snapshot.inventoryHash,
            reviewedBy = reviewer,
            wiringMatched = true,
            addressesChecked = true,
            directionsChecked = true,
            neutralOutputsChecked = true,
            limitsChecked = true,
            sources = sources,
        )
        val target = reviewFile(File(snapshot.projectPath))
        writeFileAtomically(target) { temporary ->
            Files.writeString(
                temporary.toPath(),
                HARDWARE_REVIEW_JSON.encodeToString(review).trimEnd() + System.lineSeparator(),
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
        return inspect(projectPath, league)
    }

    /** Deployment requirement used only by templates that explicitly opt into reviewed hardware. */
    fun deploymentBlockReason(projectPath: String, league: League): String? {
        val snapshot = runCatching { inspect(projectPath, league) }.getOrElse { error ->
            return "Hardware configuration could not be inspected: ${error.message}. Deployment is blocked."
        }
        if (snapshot.errorIssues.isNotEmpty()) {
            return "Hardware configuration has ${snapshot.errorIssues.size} blocking issue(s). Open Hardware Setup and resolve them before deployment."
        }
        return when (snapshot.reviewStatus) {
            HardwareReviewStatus.CURRENT -> null
            HardwareReviewStatus.NOT_REVIEWED ->
                "Hardware mapping has not been compared with the physical robot. Complete Hardware Setup before deployment."
            HardwareReviewStatus.STALE ->
                "Hardware mapping changed after its last review. Review the current addresses, directions, neutral outputs, and limits again before deployment."
            HardwareReviewStatus.INVALID ->
                "The hardware review record is invalid. Open Hardware Setup and create a new reviewed record before deployment."
        }
    }

    private fun readReview(
        root: File,
        league: League,
        inventoryHash: String,
        currentSources: List<HardwareSourceFingerprint>,
        issues: MutableList<HardwareInventoryIssue>,
    ): Pair<HardwareReviewStatus, String?> {
        val file = reviewFile(root)
        if (!file.isFile) return HardwareReviewStatus.NOT_REVIEWED to null
        val review = runCatching { HARDWARE_REVIEW_JSON.decodeFromString<HardwareReviewDocument>(file.readText()) }
            .getOrElse { error ->
                issues += HardwareInventoryIssue(
                    HardwareIssueSeverity.WARNING,
                    "hardware-review.json is invalid: ${error.message}",
                )
                return HardwareReviewStatus.INVALID to null
            }
        if (review.schemaVersion != 1 || review.league != league.name || review.reviewedBy.isBlank() ||
            !review.wiringMatched || !review.addressesChecked || !review.directionsChecked ||
            !review.neutralOutputsChecked || !review.limitsChecked
        ) {
            issues += HardwareInventoryIssue(
                HardwareIssueSeverity.WARNING,
                "hardware-review.json does not contain a complete review for ${league.name}.",
            )
            return HardwareReviewStatus.INVALID to review.reviewedBy.takeIf(String::isNotBlank)
        }
        val reviewedSources = review.sources.sortedBy(HardwareSourceFingerprint::path)
        return if (review.inventoryHash == inventoryHash && reviewedSources == currentSources) {
            HardwareReviewStatus.CURRENT to review.reviewedBy
        } else {
            HardwareReviewStatus.STALE to review.reviewedBy
        }
    }

    private fun collisionKey(item: HardwareInventoryItem): String = when (item.addressKind) {
        HardwareAddressKind.FTC_HARDWARE_MAP -> "ftc:${item.address.lowercase()}"
        HardwareAddressKind.CAN -> "can:${item.bus.orEmpty().lowercase()}:${item.address}"
        HardwareAddressKind.PWM -> "pwm:${item.address}"
        HardwareAddressKind.I2C -> "i2c:${item.address.lowercase()}"
        HardwareAddressKind.DIO -> "dio:${item.address}"
        HardwareAddressKind.ANALOG -> "analog:${item.address}"
        HardwareAddressKind.UNKNOWN -> "unknown:${item.address.lowercase()}"
    }

    private fun inventoryHash(
        league: League,
        sources: List<HardwareSourceFingerprint>,
        items: List<HardwareInventoryItem>,
    ): String {
        val canonical = buildString {
            append("hardware-inventory-v1\n")
            append(league.name).append('\n')
            sources.forEach { append(it.path).append('=').append(it.sha256).append('\n') }
            items.forEach { item ->
                append(item.uid).append('|')
                append(item.owner.name).append('|')
                append(item.addressKind.name).append('|')
                append(item.address).append('|')
                append(item.bus.orEmpty()).append('|')
                append(item.required).append('|')
                append(item.inverted).append('\n')
            }
        }
        return sha256(canonical.toByteArray())
    }

    private fun reviewFile(root: File): File = File(root, ".ares/hardware-review.json")

    private fun sourceFingerprint(path: String, file: File): HardwareSourceFingerprint {
        val hash = when {
            file.extension.equals("aresdrivetrain", ignoreCase = true) ->
                DrivetrainDocumentCodec.contentHash(DrivetrainDocumentCodec.decode(file.readText()))
            file.extension.equals("aressubsystem", ignoreCase = true) ->
                SubsystemDocumentCodec.contentHash(SubsystemDocumentCodec.decode(file.readText()))
            else -> error("Unsupported hardware source $path")
        }
        return HardwareSourceFingerprint(path, hash)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun DriveHardwareRole.readableName(): String = name.lowercase().replace('_', ' ')

private fun SubsystemHardwareKind.readableName(): String = name.lowercase().replace('_', ' ')

private fun com.areslib.subsystem.SubsystemHardwareDocument.addressKind(): HardwareAddressKind = when (kind) {
    SubsystemHardwareKind.MOTOR -> HardwareAddressKind.CAN
    SubsystemHardwareKind.POSITIONAL_SERVO,
    SubsystemHardwareKind.CONTINUOUS_SERVO,
    SubsystemHardwareKind.INDICATOR_LIGHT -> HardwareAddressKind.PWM
    SubsystemHardwareKind.PRISM_DRIVER,
    SubsystemHardwareKind.COLOR_SENSOR -> HardwareAddressKind.I2C
    SubsystemHardwareKind.DIGITAL_INPUT -> HardwareAddressKind.DIO
    SubsystemHardwareKind.ANALOG_INPUT -> HardwareAddressKind.ANALOG
}
