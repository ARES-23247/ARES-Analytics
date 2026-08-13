package com.ares.analytics.service.drivebase

import com.areslib.drivetrain.*

enum class DrivebaseKind { FTC_MECANUM, FRC_CTRE_SWERVE, DIFFERENTIAL, CUSTOM }

enum class DriveHardwareRole {
    FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT,
    LEFT_LEADER, LEFT_FOLLOWER, RIGHT_LEADER, RIGHT_FOLLOWER,
    FRONT_LEFT_DRIVE, FRONT_LEFT_STEER, FRONT_LEFT_ENCODER,
    FRONT_RIGHT_DRIVE, FRONT_RIGHT_STEER, FRONT_RIGHT_ENCODER,
    REAR_LEFT_DRIVE, REAR_LEFT_STEER, REAR_LEFT_ENCODER,
    REAR_RIGHT_DRIVE, REAR_RIGHT_STEER, REAR_RIGHT_ENCODER,
    GYRO, ODOMETRY, DRIVE_MOTOR, OTHER, CUSTOM
}

enum class LocalizationKind {
    FTC_PINPOINT, WHEEL_ODOMETRY_GYRO, CTRE_POSE_ESTIMATOR, VISION_FUSION, CUSTOM
}

enum class CalibrationSource { MANUAL, SIMULATION, ROBOT_MEASURED, CTRE_TUNER_IMPORT }

data class DriveHardwareDeclaration(
    val id: String,
    val displayName: String,
    val role: DriveHardwareRole,
    val hardwareName: String = "",
    val canId: Int? = null,
    val canBus: String? = null,
    val inverted: Boolean = false,
    val required: Boolean = true,
    /** Stable ID of a direct leader. [inverted] independently controls follower direction. */
    val leaderId: String? = null,
)

data class DriveGeometry(
    val wheelRadiusMeters: Double = 0.048,
    val trackWidthMeters: Double = 0.36,
    val wheelBaseMeters: Double = 0.36
)

data class DriveSafetyDeclaration(
    val safeNeutralRequired: Boolean = true,
    val configurationHealthRequired: Boolean = true,
    val feedbackFreshnessTimeoutMs: Int = 100,
    val maxLinearSpeedMetersPerSecond: Double = 3.0,
    val maxAngularSpeedRadiansPerSecond: Double = 6.0,
    val currentMonitoringRequired: Boolean = true,
    val explicitNeutralRecoveryRequired: Boolean = true
)

data class DriveCalibrationRecord(
    val id: String,
    val source: CalibrationSource,
    val sourcePath: String? = null,
    val sourceHash: String? = null,
    val notes: String,
    val values: Map<String, Double> = emptyMap()
)

data class DrivebaseDocument(
    val schemaVersion: Int = 1,
    /** Stable editor identity; renames never change this or the canonical filename. */
    val documentId: String = "primary-drivebase",
    val projectId: String,
    val kind: DrivebaseKind,
    val displayName: String,
    val hardware: List<DriveHardwareDeclaration>,
    val geometry: DriveGeometry = DriveGeometry(),
    val localization: List<LocalizationKind> = emptyList(),
    val safety: DriveSafetyDeclaration = DriveSafetyDeclaration(),
    val calibrations: List<DriveCalibrationRecord> = emptyList(),
    val fieldRelativeEnabled: Boolean = true,
    val vendorSourceReadOnly: Boolean = true,
    /** Complete shared document retained so UI edits cannot silently drop unrepresented fields. */
    val canonical: DrivetrainDocument? = null
)

enum class DrivebaseIssueSeverity { INFO, WARNING, ERROR }

data class DrivebaseIssue(
    val severity: DrivebaseIssueSeverity,
    val path: String,
    val message: String
)

data class DrivebaseChange(val path: String, val before: String, val after: String)

fun validateDrivebase(document: DrivebaseDocument): List<DrivebaseIssue> = buildList {
    if (document.displayName.isBlank()) add(error("displayName", "Give this drivebase a name."))
    if (document.hardware.isEmpty()) add(error("hardware", "Add the hardware that makes the robot move."))
    val duplicateIds = document.hardware.groupBy { it.id }.filterValues { it.size > 1 }.keys
    duplicateIds.forEach { add(error("hardware.$it", "Hardware IDs must be unique.")) }
    document.hardware.forEach { device ->
        if (device.id.isBlank()) add(error("hardware", "Every device needs a stable ID."))
        if (device.required && device.hardwareName.isBlank() && device.canId == null) {
            add(error("hardware.${device.id}", "${device.displayName} needs a hardware-map name or CAN ID."))
        }
        if (device.canId != null && device.canId !in 0..62) {
            add(error("hardware.${device.id}.canId", "CAN IDs must be between 0 and 62."))
        }
        val follower = device.role == DriveHardwareRole.LEFT_FOLLOWER || device.role == DriveHardwareRole.RIGHT_FOLLOWER
        if (follower && device.leaderId.isNullOrBlank()) add(error("hardware.${device.id}.leaderId", "Choose a direct leader for ${device.displayName}."))
        if (!follower && device.leaderId != null) add(error("hardware.${device.id}.leaderId", "Only follower motors may name a leader."))
        device.leaderId?.let { leaderId ->
            val leader = document.hardware.firstOrNull { it.id == leaderId }
            if (leader == null) add(error("hardware.${device.id}.leaderId", "Leader '$leaderId' does not exist."))
            else if (leader.role !in setOf(DriveHardwareRole.LEFT_LEADER, DriveHardwareRole.RIGHT_LEADER, DriveHardwareRole.DRIVE_MOTOR)) {
                add(error("hardware.${device.id}.leaderId", "Followers must reference a drive-motor leader."))
            }
        }
    }
    with(document.geometry) {
        if (wheelRadiusMeters !in 0.01..0.25) add(error("geometry.wheelRadiusMeters", "Wheel radius must be 1–25 cm."))
        if (trackWidthMeters !in 0.1..2.0) add(error("geometry.trackWidthMeters", "Track width must be 0.1–2.0 m."))
        if (wheelBaseMeters !in 0.1..2.0) add(error("geometry.wheelBaseMeters", "Wheelbase must be 0.1–2.0 m."))
    }
    val primaryLocalization = document.localization.filter { it != LocalizationKind.VISION_FUSION }
    if (primaryLocalization.size != 1) add(error("localization", "Choose exactly one primary odometry source. Vision may be added only as fusion."))
    primaryLocalization.singleOrNull()?.let { source ->
        val compatible = when (document.kind) {
            DrivebaseKind.FTC_MECANUM -> source in setOf(LocalizationKind.FTC_PINPOINT, LocalizationKind.WHEEL_ODOMETRY_GYRO, LocalizationKind.CUSTOM)
            DrivebaseKind.FRC_CTRE_SWERVE -> source in setOf(LocalizationKind.CTRE_POSE_ESTIMATOR, LocalizationKind.WHEEL_ODOMETRY_GYRO, LocalizationKind.CUSTOM)
            DrivebaseKind.DIFFERENTIAL -> source in setOf(LocalizationKind.WHEEL_ODOMETRY_GYRO, LocalizationKind.CUSTOM)
            DrivebaseKind.CUSTOM -> true
        }
        if (!compatible) add(error("localization", "$source is not compatible with ${document.kind}. Choose a matching primary source."))
    }
    if (!document.safety.safeNeutralRequired) add(error("safety.safeNeutralRequired", "Drive outputs must neutralize when disabled, stopped, faulted, or closed."))
    if (!document.safety.configurationHealthRequired) add(error("safety.configurationHealthRequired", "Nonzero drive output must require healthy configuration."))
    if (!document.safety.explicitNeutralRecoveryRequired) add(error("safety.explicitNeutralRecoveryRequired", "Fault recovery must prove a successful neutral write before motion resumes."))
    if (document.safety.feedbackFreshnessTimeoutMs !in 20..1_000) add(error("safety.feedbackFreshnessTimeoutMs", "Feedback timeout must be 20–1000 ms."))
    if (document.kind == DrivebaseKind.FRC_CTRE_SWERVE && document.calibrations.none { it.source == CalibrationSource.CTRE_TUNER_IMPORT }) {
        add(warning("calibrations", "Import and validate CTRE TunerConstants before deployment. ARES never overwrites that vendor file."))
    }
    runCatching { document.toCanonicalDrivebase() }.fold(
        onSuccess = { canonical ->
            validateDrivetrainDocument(canonical).forEach { issue -> add(error("canonical.${issue.path}", issue.message)) }
        },
        onFailure = { failure -> add(error("canonical", failure.message ?: "Could not adapt the drivebase to the shared canonical contract.")) }
    )
}

fun diffDrivebase(before: DrivebaseDocument?, after: DrivebaseDocument): List<DrivebaseChange> = buildList {
    if (before == null) {
        add(DrivebaseChange("document", "Not configured", "Create ${after.displayName}"))
        return@buildList
    }
    fun change(path: String, old: Any?, new: Any?) {
        if (old != new) add(DrivebaseChange(path, old.toString(), new.toString()))
    }
    change("kind", before.kind, after.kind)
    change("displayName", before.displayName, after.displayName)
    change("geometry", before.geometry, after.geometry)
    change("localization", before.localization, after.localization)
    change("safety", before.safety, after.safety)
    change("fieldRelativeEnabled", before.fieldRelativeEnabled, after.fieldRelativeEnabled)
    (before.hardware.associateBy { it.id }.keys + after.hardware.associateBy { it.id }.keys).sorted().forEach { id ->
        change("hardware.$id", before.hardware.firstOrNull { it.id == id }, after.hardware.firstOrNull { it.id == id })
    }
    change("calibrations", before.calibrations, after.calibrations)
}

/** Lossless UI projection: all canonical fields survive in [DrivebaseDocument.canonical]. */
fun DrivetrainDocument.toUiDrivebase(): DrivebaseDocument = DrivebaseDocument(
    schemaVersion = schemaVersion,
    documentId = uid,
    projectId = canonicalProfileUid.substringBeforeLast('.', canonicalProfileUid),
    kind = when (kind) {
        DrivetrainKind.FTC_MECANUM -> DrivebaseKind.FTC_MECANUM
        DrivetrainKind.FRC_CTRE_SWERVE -> DrivebaseKind.FRC_CTRE_SWERVE
        DrivetrainKind.DIFFERENTIAL -> DrivebaseKind.DIFFERENTIAL
        DrivetrainKind.ADVANCED_CUSTOM -> DrivebaseKind.CUSTOM
    },
    displayName = displayName,
    hardware = components.map { component ->
        DriveHardwareDeclaration(
            id = component.uid,
            displayName = component.displayName,
            role = component.toUiRole(kind),
            hardwareName = component.hardwareId,
            canId = component.hardwareId.toIntOrNull(),
            canBus = ctreImport?.canBusName,
            inverted = component.inverted,
            required = component.required,
            leaderId = component.leaderUid,
        )
    },
    geometry = DriveGeometry(
        wheelRadiusMeters = geometry.wheelDiameterMeters / 2.0,
        trackWidthMeters = geometry.trackWidthMeters,
        wheelBaseMeters = geometry.wheelBaseMeters
    ),
    localization = (listOf(localization.primaryOdometry) + localization.visionFusion).map { source ->
        when (source.source) {
            LocalizationSourceKind.PINPOINT -> LocalizationKind.FTC_PINPOINT
            LocalizationSourceKind.WHEEL_ENCODERS_IMU -> LocalizationKind.WHEEL_ODOMETRY_GYRO
            LocalizationSourceKind.CTRE_VENDOR -> LocalizationKind.CTRE_POSE_ESTIMATOR
            LocalizationSourceKind.EXTERNAL -> LocalizationKind.VISION_FUSION
            LocalizationSourceKind.CUSTOM -> LocalizationKind.CUSTOM
        }
    },
    safety = DriveSafetyDeclaration(
        safeNeutralRequired = safety.safeNeutralRequired,
        configurationHealthRequired = safety.configurationHealthRequired,
        feedbackFreshnessTimeoutMs = safety.staleFeedbackTimeoutMs.toInt(),
        maxLinearSpeedMetersPerSecond = geometry.maxLinearSpeedMetersPerSecond,
        maxAngularSpeedRadiansPerSecond = geometry.maxAngularSpeedRadiansPerSecond,
        currentMonitoringRequired = safety.currentValidityRequired,
        explicitNeutralRecoveryRequired = safety.explicitNeutralRecoveryRequired
    ),
    calibrations = calibrationProvenance.map { provenance ->
        DriveCalibrationRecord(
            id = provenance.uid,
            source = when (provenance.kind) {
                CalibrationProvenanceKind.MEASURED, CalibrationProvenanceKind.REVIEWED_MANUAL -> CalibrationSource.MANUAL
                CalibrationProvenanceKind.SYSID -> CalibrationSource.ROBOT_MEASURED
                CalibrationProvenanceKind.VENDOR_GENERATED, CalibrationProvenanceKind.MANUFACTURER -> CalibrationSource.CTRE_TUNER_IMPORT
            },
            sourcePath = provenance.evidencePath,
            sourceHash = provenance.evidenceSha256,
            notes = provenance.notes
        )
    },
    fieldRelativeEnabled = control.fieldCentric,
    vendorSourceReadOnly = ctreImport?.ownership == VendorSourceOwnership.READ_ONLY_VENDOR || ctreImport == null,
    canonical = this
)

fun DrivebaseDocument.toCanonicalDrivebase(): DrivetrainDocument {
    val base = canonical ?: canonicalTemplate(projectId, kind)
    val originalUi = canonical?.toUiDrivebase()
    val baseByUid = base.components.associateBy { it.uid }
    val components = hardware.map { edit ->
        val existing = baseByUid[edit.id]
        val role = edit.role.toCanonicalRole()
        val corner = edit.role.name.substringBeforeLast('_').lowercase().replace('_', '-')
        val inferredModule = if (kind == DrivebaseKind.FRC_CTRE_SWERVE && role in setOf(DrivetrainComponentRole.DRIVE_MOTOR, DrivetrainComponentRole.STEER_MOTOR, DrivetrainComponentRole.ABSOLUTE_ENCODER)) "module.$corner" else null
        (existing ?: DrivetrainComponentDocument(
            uid = edit.id,
            displayName = edit.displayName,
            role = role,
            hardwareId = edit.canId?.toString() ?: edit.hardwareName,
            moduleUid = inferredModule,
            currentMeasurementRequired = role == DrivetrainComponentRole.DRIVE_MOTOR,
            currentMeasurementAvailable = role == DrivetrainComponentRole.DRIVE_MOTOR,
        )).copy(
            displayName = edit.displayName,
            role = role,
            hardwareId = edit.canId?.toString() ?: edit.hardwareName,
            inverted = edit.inverted,
            required = edit.required,
            leaderUid = edit.leaderId,
        )
    }
    val editedCanBuses = hardware.mapNotNull { it.canBus?.takeIf(String::isNotBlank) }.distinct()
    require(editedCanBuses.size <= 1) { "A CTRE drivetrain document has one named CAN bus. Resolve conflicting component bus names." }
    val ctreImport = base.ctreImport?.let { metadata ->
        metadata.copy(canBusName = editedCanBuses.singleOrNull() ?: metadata.canBusName)
    }
    val selectedSources = localization.toSet()
    fun source(kind: LocalizationKind): DrivetrainLocalizationSourceDocument = when (kind) {
        LocalizationKind.FTC_PINPOINT -> DrivetrainLocalizationSourceDocument("localization.pinpoint", LocalizationSourceKind.PINPOINT, components.filter { it.role == DrivetrainComponentRole.ODOMETRY_SENSOR }.map { it.uid })
        LocalizationKind.WHEEL_ODOMETRY_GYRO -> DrivetrainLocalizationSourceDocument("localization.wheel-imu", LocalizationSourceKind.WHEEL_ENCODERS_IMU, components.filter { it.role == DrivetrainComponentRole.DRIVE_MOTOR || it.role == DrivetrainComponentRole.GYRO }.map { it.uid })
        LocalizationKind.CTRE_POSE_ESTIMATOR -> DrivetrainLocalizationSourceDocument("localization.ctre", LocalizationSourceKind.CTRE_VENDOR, components.map { it.uid })
        LocalizationKind.VISION_FUSION -> DrivetrainLocalizationSourceDocument("localization.vision", LocalizationSourceKind.EXTERNAL, emptyList(), "com.areslib.vision.VisionTracker")
        LocalizationKind.CUSTOM -> DrivetrainLocalizationSourceDocument("localization.custom", LocalizationSourceKind.CUSTOM, emptyList(), "com.areslib.localization.CustomLocalization")
    }
    val localizationChanged = originalUi == null || localization != originalUi.localization
    val primaryKind = selectedSources.filter { it != LocalizationKind.VISION_FUSION }.single()
    val primary = if (localizationChanged) {
        val desired = source(primaryKind)
        (listOf(base.localization.primaryOdometry) + base.localization.visionFusion).firstOrNull { it.source == desired.source } ?: desired
    } else base.localization.primaryOdometry
    val vision = if (localizationChanged) selectedSources.filter { it == LocalizationKind.VISION_FUSION }.map { kind ->
        val desired = source(kind)
        base.localization.visionFusion.firstOrNull { it.source == desired.source } ?: desired
    } else base.localization.visionFusion
    return base.copy(
        uid = documentId,
        drivebaseId = if (documentId == base.uid) base.drivebaseId else documentId.substringAfterLast('.').replace('_', '-'),
        displayName = displayName,
        kind = when (kind) { DrivebaseKind.FTC_MECANUM -> DrivetrainKind.FTC_MECANUM; DrivebaseKind.FRC_CTRE_SWERVE -> DrivetrainKind.FRC_CTRE_SWERVE; DrivebaseKind.DIFFERENTIAL -> DrivetrainKind.DIFFERENTIAL; DrivebaseKind.CUSTOM -> DrivetrainKind.ADVANCED_CUSTOM },
        components = components,
        geometry = base.geometry.copy(
            wheelDiameterMeters = geometry.wheelRadiusMeters * 2.0,
            trackWidthMeters = geometry.trackWidthMeters,
            wheelBaseMeters = geometry.wheelBaseMeters,
            maxLinearSpeedMetersPerSecond = safety.maxLinearSpeedMetersPerSecond,
            maxAngularSpeedRadiansPerSecond = safety.maxAngularSpeedRadiansPerSecond
        ),
        localization = base.localization.copy(
            primaryOdometry = primary,
            headingSourceUid = if (localizationChanged) components.firstOrNull { it.role == DrivetrainComponentRole.GYRO }?.uid ?: primary.uid else base.localization.headingSourceUid,
            visionFusion = vision
        ),
        control = base.control.copy(fieldCentric = fieldRelativeEnabled),
        safety = base.safety.copy(
            safeNeutralRequired = safety.safeNeutralRequired,
            configurationHealthRequired = safety.configurationHealthRequired,
            staleFeedbackTimeoutMs = safety.feedbackFreshnessTimeoutMs.toLong(),
            currentValidityRequired = safety.currentMonitoringRequired,
            explicitNeutralRecoveryRequired = safety.explicitNeutralRecoveryRequired
        ),
        ctreImport = ctreImport
    )
}

private fun DrivetrainComponentDocument.toUiRole(kind: DrivetrainKind): DriveHardwareRole {
    val directLeaderUid = leaderUid
    return when (role) {
    DrivetrainComponentRole.DRIVE_MOTOR -> when {
        directLeaderUid != null && directLeaderUid.contains("left") -> DriveHardwareRole.LEFT_FOLLOWER
        directLeaderUid != null && directLeaderUid.contains("right") -> DriveHardwareRole.RIGHT_FOLLOWER
        kind == DrivetrainKind.DIFFERENTIAL && uid.contains("left") -> DriveHardwareRole.LEFT_LEADER
        kind == DrivetrainKind.DIFFERENTIAL && uid.contains("right") -> DriveHardwareRole.RIGHT_LEADER
        uid.contains("front-left") -> DriveHardwareRole.FRONT_LEFT_DRIVE
        uid.contains("front-right") -> DriveHardwareRole.FRONT_RIGHT_DRIVE
        uid.contains("rear-left") -> DriveHardwareRole.REAR_LEFT_DRIVE
        uid.contains("rear-right") -> DriveHardwareRole.REAR_RIGHT_DRIVE
        else -> DriveHardwareRole.DRIVE_MOTOR
    }
    DrivetrainComponentRole.STEER_MOTOR -> when {
        uid.contains("front-left") -> DriveHardwareRole.FRONT_LEFT_STEER
        uid.contains("front-right") -> DriveHardwareRole.FRONT_RIGHT_STEER
        uid.contains("rear-left") -> DriveHardwareRole.REAR_LEFT_STEER
        uid.contains("rear-right") -> DriveHardwareRole.REAR_RIGHT_STEER
        else -> DriveHardwareRole.CUSTOM
    }
    DrivetrainComponentRole.ABSOLUTE_ENCODER -> when {
        uid.contains("front-left") -> DriveHardwareRole.FRONT_LEFT_ENCODER
        uid.contains("front-right") -> DriveHardwareRole.FRONT_RIGHT_ENCODER
        uid.contains("rear-left") -> DriveHardwareRole.REAR_LEFT_ENCODER
        uid.contains("rear-right") -> DriveHardwareRole.REAR_RIGHT_ENCODER
        else -> DriveHardwareRole.OTHER
    }
    DrivetrainComponentRole.GYRO -> DriveHardwareRole.GYRO
    DrivetrainComponentRole.ODOMETRY_SENSOR -> DriveHardwareRole.ODOMETRY
    DrivetrainComponentRole.WHEEL_MODULE, DrivetrainComponentRole.OTHER -> DriveHardwareRole.OTHER
}
}

private fun DriveHardwareRole.toCanonicalRole(): DrivetrainComponentRole = when {
    name.endsWith("STEER") -> DrivetrainComponentRole.STEER_MOTOR
    name.endsWith("ENCODER") -> DrivetrainComponentRole.ABSOLUTE_ENCODER
    this == DriveHardwareRole.GYRO -> DrivetrainComponentRole.GYRO
    this == DriveHardwareRole.ODOMETRY -> DrivetrainComponentRole.ODOMETRY_SENSOR
    this == DriveHardwareRole.OTHER || this == DriveHardwareRole.CUSTOM -> DrivetrainComponentRole.OTHER
    else -> DrivetrainComponentRole.DRIVE_MOTOR
}

internal fun canonicalTemplate(projectId: String, kind: DrivebaseKind): DrivetrainDocument {
    val projectUid = projectId.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.').ifBlank { "robot.project" }
    fun drive(uid: String, hardware: String, inverted: Boolean = false, module: String? = null) = DrivetrainComponentDocument(uid, uid.substringAfterLast('.').replace('-', ' ').replaceFirstChar(Char::uppercase), DrivetrainComponentRole.DRIVE_MOTOR, hardware, moduleUid = module, currentMeasurementRequired = true, currentMeasurementAvailable = true, inverted = inverted)
    val components = when (kind) {
        DrivebaseKind.FTC_MECANUM -> listOf(drive("drive.front-left", "fl"), drive("drive.front-right", "fr", true), drive("drive.rear-left", "rl"), drive("drive.rear-right", "rr", true), DrivetrainComponentDocument("drive.pinpoint", "goBILDA Pinpoint", DrivetrainComponentRole.ODOMETRY_SENSOR, "pinpoint"))
        DrivebaseKind.DIFFERENTIAL -> listOf(drive("drive.left", "leftLeader"), drive("drive.right", "rightLeader", true), DrivetrainComponentDocument("drive.gyro", "Gyro", DrivetrainComponentRole.GYRO, "gyro"))
        DrivebaseKind.FRC_CTRE_SWERVE -> listOf("front-left", "front-right", "rear-left", "rear-right").flatMap { corner ->
            val module = "module.$corner"
            listOf(
                drive("drive.$corner", "0", module = module),
                DrivetrainComponentDocument("steer.$corner", "${corner.replace('-', ' ')} steer", DrivetrainComponentRole.STEER_MOTOR, "0", moduleUid = module),
                DrivetrainComponentDocument("encoder.$corner", "${corner.replace('-', ' ')} encoder", DrivetrainComponentRole.ABSOLUTE_ENCODER, "0", moduleUid = module)
            )
        } + DrivetrainComponentDocument("drive.gyro", "Pigeon gyro", DrivetrainComponentRole.GYRO, "0")
        DrivebaseKind.CUSTOM -> listOf(drive("drive.custom", "custom"), DrivetrainComponentDocument("drive.gyro", "Gyro", DrivetrainComponentRole.GYRO, "gyro"))
    }
    val modules = if (kind == DrivebaseKind.FRC_CTRE_SWERVE) listOf("front-left", "front-right", "rear-left", "rear-right").map { corner ->
        val x = if (corner.startsWith("front")) .28 else -.28; val y = if (corner.endsWith("left")) .28 else -.28
        DrivetrainModuleDocument("module.$corner", corner.replace('-', ' ').replaceFirstChar(Char::uppercase), listOf("drive.$corner", "steer.$corner", "encoder.$corner"), x, y)
    } else emptyList()
    val primary = when (kind) {
        DrivebaseKind.FTC_MECANUM -> DrivetrainLocalizationSourceDocument("localization.pinpoint", LocalizationSourceKind.PINPOINT, listOf("drive.pinpoint"))
        DrivebaseKind.FRC_CTRE_SWERVE -> DrivetrainLocalizationSourceDocument("localization.ctre", LocalizationSourceKind.CTRE_VENDOR, components.map { it.uid })
        else -> DrivetrainLocalizationSourceDocument("localization.wheel-imu", LocalizationSourceKind.WHEEL_ENCODERS_IMU, components.map { it.uid })
    }
    val diameter = .096
    return DrivetrainDocument(
        uid = "drive.primary", drivebaseId = "primary", displayName = "Primary drivebase", description = "Robot-owned drivebase contract.",
        kind = when (kind) { DrivebaseKind.FTC_MECANUM -> DrivetrainKind.FTC_MECANUM; DrivebaseKind.FRC_CTRE_SWERVE -> DrivetrainKind.FRC_CTRE_SWERVE; DrivebaseKind.DIFFERENTIAL -> DrivetrainKind.DIFFERENTIAL; DrivebaseKind.CUSTOM -> DrivetrainKind.ADVANCED_CUSTOM },
        platform = if (kind == DrivebaseKind.FTC_MECANUM) DrivetrainPlatform.FTC else DrivetrainPlatform.FRC,
        components = components, modules = modules,
        geometry = DrivetrainGeometryDocument(diameter, .36, .36, 1.0, if (kind == DrivebaseKind.FRC_CTRE_SWERVE) 1.0 else null, 3.0, 6.0),
        localization = DrivetrainLocalizationDocument(primary, components.firstOrNull { it.role == DrivetrainComponentRole.GYRO }?.uid ?: primary.uid),
        control = DrivetrainControlDocument(listOf(DrivetrainControlKind.OPEN_LOOP, DrivetrainControlKind.CHASSIS_VELOCITY), DrivetrainControlKind.OPEN_LOOP),
        simulation = DrivetrainSimulationDocument("com.areslib.simulator.DrivetrainModel", "com.areslib.simulator.DrivetrainAdapter"),
        // Physical geometry is authoritative here. It must never be duplicated as a tuning value.
        parameters = emptyList(),
        ctreImport = if (kind == DrivebaseKind.FRC_CTRE_SWERVE) CtreSwerveImportDocument("src/main/java/frc/robot/generated/TunerConstants.java", "0".repeat(64), "CTRE Tuner", "unknown", "frc.robot.generated.TunerConstants", "rio") else null,
        canonicalProfileUid = "$projectUid.profile.competition"
    )
}

private fun error(path: String, message: String) = DrivebaseIssue(DrivebaseIssueSeverity.ERROR, path, message)
private fun warning(path: String, message: String) = DrivebaseIssue(DrivebaseIssueSeverity.WARNING, path, message)
