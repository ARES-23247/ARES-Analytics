package com.ares.analytics.service.drivebase

import com.areslib.drivetrain.DrivetrainDocumentCodec
import com.ares.analytics.service.tuning.TuningProfileRepository
import com.areslib.tuning.TuningProfileAuthority
import com.areslib.tuning.TuningProfileDocument
import com.areslib.tuning.TuningProfileDocumentCodec
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class DrivebaseProjectRepository {
    fun load(projectPath: String): Result<DrivebaseDocument?> = runCatching {
        val files = drivetrainFiles(projectPath)
        require(files.size <= 1) { "This project has multiple drivetrain documents. Multi-drivebase selection is not available yet; choose one explicitly in project source." }
        files.singleOrNull()?.let { DrivetrainDocumentCodec.decode(it.readText()).toUiDrivebase() }
    }

    fun saveReviewed(projectPath: String, expectedContentHash: String?, document: DrivebaseDocument): DrivebaseDocument {
        require(projectPath.isNotBlank()) { "Select a project before saving a drivebase." }
        val current = load(projectPath).getOrThrow()
        val currentHash = current?.let { DrivetrainDocumentCodec.contentHash(it.toCanonicalDrivebase()) }
        require(currentHash == expectedContentHash) { "The drivebase changed on disk. Reload and review a fresh diff." }
        require(validateDrivebase(document).none { it.severity == DrivebaseIssueSeverity.ERROR }) {
            "Fix drivebase validation errors before saving."
        }
        val canonical = document.toCanonicalDrivebase()
        val saved = canonical.toUiDrivebase()
        val tuningWorkspace = TuningProfileRepository().load(projectPath).getOrThrow()
        val matchingProfiles = tuningWorkspace.profiles.filter { it.uid == canonical.canonicalProfileUid }
        require(matchingProfiles.size <= 1) { "Multiple tuning profiles claim ${canonical.canonicalProfileUid}. Resolve them before saving." }
        val canonicalProjectUid = canonical.canonicalProfileUid.substringBefore(".profile.")
        val matchingProfile = matchingProfiles.singleOrNull()
        require(matchingProfile == null ||
            (matchingProfile.projectUid == canonicalProjectUid && matchingProfile.drivebaseUid == canonical.uid && matchingProfile.authority == TuningProfileAuthority.CANONICAL_CHECKED_IN)
        ) { "Canonical tuning profile ${canonical.canonicalProfileUid} targets a different project or drivebase." }
        val newProfile = matchingProfile ?: TuningProfileDocument(
            uid = canonical.canonicalProfileUid,
            profileId = canonical.canonicalProfileUid.substringAfterLast('.'),
            displayName = "Competition",
            description = "Canonical values for ${canonical.displayName}",
            projectUid = canonicalProjectUid,
            drivebaseUid = canonical.uid,
            authority = TuningProfileAuthority.CANONICAL_CHECKED_IN,
            values = emptyList(),
        )
        val profileFile = File(File(projectPath, ".ares/tuning"), "${newProfile.uid}.arestuning")
        val createProfile = matchingProfile == null
        val target = current?.let { existingFile(projectPath, requireNotNull(it.canonical).uid) }
            ?: File(File(projectPath, ".ares/drivetrains"), "${canonical.uid}.aresdrivetrain")
        target.parentFile.mkdirs()
        if (target.exists()) backupFile(projectPath, target, canonical.uid, DrivetrainDocumentCodec.contentHash(current!!.toCanonicalDrivebase()))
        try {
            if (createProfile) atomicWrite(profileFile, TuningProfileDocumentCodec.encode(newProfile, tuningWorkspace.catalog))
            atomicWrite(target, DrivetrainDocumentCodec.encode(canonical))
        } catch (failure: Exception) {
            if (createProfile) Files.deleteIfExists(profileFile.toPath())
            throw failure
        }
        return saved
    }

    fun importCtreTunerConstants(file: File): Result<CtreTunerImport> = runCatching {
        require(file.isFile && file.extension.equals("java", true)) { "Choose the generated TunerConstants.java file." }
        CtreTunerConstantsReader.read(file)
    }

    private fun drivetrainFiles(projectPath: String): List<File> =
        File(projectPath, ".ares/drivetrains").listFiles { file -> file.extension == "aresdrivetrain" }
            ?.sortedBy(File::getName).orEmpty()

    private fun existingFile(projectPath: String, uid: String): File {
        val matches = drivetrainFiles(projectPath).filter { file ->
            runCatching { DrivetrainDocumentCodec.decode(file.readText()).uid }.getOrNull() == uid
        }
        require(matches.size == 1) { "The canonical drivetrain file for $uid is missing or duplicated. Reload before saving." }
        return matches.single()
    }

    private fun backupFile(projectPath: String, source: File, uid: String, contentHash: String) {
        val backup = File(File(projectPath, ".ares/history/drivetrains/$uid"), "${contentHash.take(16)}.aresdrivetrain")
        backup.parentFile.mkdirs()
        Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeText(content)
        runCatching {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

data class CtreTunerImport(
    val sourcePath: String,
    val sourceHash: String,
    val hardware: List<DriveHardwareDeclaration>,
    val geometry: DriveGeometry?,
    val values: Map<String, Double>,
    val warnings: List<String>
)

object CtreTunerConstantsReader {
    private val assignment = Regex("(?:public|private|protected)\\s+static\\s+(?:final\\s+)?[\\w<>,.? ]+\\s+(k\\w+)\\s*=\\s*([^;]+);")
    private val idName = Regex("(?i)k?(FrontLeft|FrontRight|BackLeft|BackRight|RearLeft|RearRight)(DriveMotorId|SteerMotorId|EncoderId)")

    fun read(file: File): CtreTunerImport {
        val text = file.readText()
        val raw = assignment.findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim().removeSurrounding("\"") }
        val canBus = Regex("new\\s+CANBus\\(\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
        fun scalar(name: String): Double? = raw[name]?.let(::parseScalar)
        fun bool(name: String): Boolean? = raw[name]?.trim()?.toBooleanStrictOrNull()
        val moduleHardware = raw.mapNotNull { (name, value) ->
            val match = idName.matchEntire(name) ?: return@mapNotNull null
            val canId = value.toIntOrNull() ?: return@mapNotNull null
            val corner = match.groupValues[1].replace("Back", "Rear")
            val device = match.groupValues[2]
            val role = runCatching { DriveHardwareRole.valueOf("${camelToEnum(corner)}_${device.removeSuffix("Id").replace("Motor", "").uppercase()}") }.getOrNull()
                ?: return@mapNotNull null
            val isLeftDrive = role == DriveHardwareRole.FRONT_LEFT_DRIVE || role == DriveHardwareRole.REAR_LEFT_DRIVE
            val isRightDrive = role == DriveHardwareRole.FRONT_RIGHT_DRIVE || role == DriveHardwareRole.REAR_RIGHT_DRIVE
            val vendorCorner = match.groupValues[1]
            DriveHardwareDeclaration(
                id = name.replaceFirstChar(Char::lowercase),
                displayName = "$corner ${device.removeSuffix("Id").replace("Motor", " motor")}",
                role = role,
                canId = canId,
                canBus = canBus,
                inverted = when {
                    isLeftDrive -> requireNotNull(bool("kInvertLeftSide")) { "CTRE import is incomplete: left drive inversion is missing." }
                    isRightDrive -> requireNotNull(bool("kInvertRightSide")) { "CTRE import is incomplete: right drive inversion is missing." }
                    role.name.endsWith("STEER") -> requireNotNull(bool("k${vendorCorner}SteerMotorInverted")) { "CTRE import is incomplete: $vendorCorner steer inversion is missing." }
                    role.name.endsWith("ENCODER") -> requireNotNull(bool("k${vendorCorner}EncoderInverted")) { "CTRE import is incomplete: $vendorCorner encoder inversion is missing." }
                    else -> false
                },
            )
        }
        val hardware = moduleHardware + listOfNotNull(raw["kPigeonId"]?.toIntOrNull()?.let { pigeonId ->
            DriveHardwareDeclaration("pigeon", "Pigeon gyro", DriveHardwareRole.GYRO, canId = pigeonId, canBus = canBus)
        })
        fun number(vararg names: String): Double? = names.firstNotNullOfOrNull(::scalar)
        val wheelRadius = number("kWheelRadius", "WheelRadius")
        val trackWidth = number("kTrackWidth", "TrackWidth")
            ?: scalar("kFrontLeftYPos")?.let { kotlin.math.abs(it) * 2.0 }
        val wheelBase = number("kWheelBase", "WheelBase")
            ?: scalar("kFrontLeftXPos")?.let { kotlin.math.abs(it) * 2.0 }
        val driveGearRatio = scalar("kDriveGearRatio")
        val steerGearRatio = scalar("kSteerGearRatio")
        val speedAt12Volts = Regex("""SPEED_AT_12_VOLTS\s*=\s*(-?\d+(?:\.\d+)?)""").find(text)?.groupValues?.get(1)?.toDouble()
        val slipCurrent = Regex("""SLIP_CURRENT_AMPS\s*=\s*(-?\d+(?:\.\d+)?)""").find(text)?.groupValues?.get(1)?.toDouble()
        val values = buildMap {
            wheelRadius?.let { put("wheelRadius", it) }
            trackWidth?.let { put("trackWidth", it) }
            wheelBase?.let { put("wheelBase", it) }
            driveGearRatio?.let { put("driveGearRatio", it) }
            steerGearRatio?.let { put("steerGearRatio", it) }
            speedAt12Volts?.let { put("speedAt12Volts", it) }
            slipCurrent?.let { put("slipCurrentAmps", it) }
            listOf("FrontLeft", "FrontRight", "BackLeft", "BackRight").forEach { corner ->
                scalar("k${corner}XPos")?.let { put("${corner.replace("Back", "Rear").replaceFirstChar(Char::lowercase)}X", it) }
                scalar("k${corner}YPos")?.let { put("${corner.replace("Back", "Rear").replaceFirstChar(Char::lowercase)}Y", it) }
                scalar("k${corner}EncoderOffset")?.let { put("${corner.replace("Back", "Rear").replaceFirstChar(Char::lowercase)}EncoderOffsetRotations", it) }
                bool("k${corner}SteerMotorInverted")?.let { put("${corner.replace("Back", "Rear").replaceFirstChar(Char::lowercase)}SteerInverted", if (it) 1.0 else 0.0) }
                bool("k${corner}EncoderInverted")?.let { put("${corner.replace("Back", "Rear").replaceFirstChar(Char::lowercase)}EncoderInverted", if (it) 1.0 else 0.0) }
            }
        }
        val warnings = buildList {
            add("Imported values are a read-only snapshot. TunerConstants.java remains vendor-owned and is never changed by ARES.")
            add("Slip current is retained as calibration evidence; ARES does not label it as an enforced controller current limit.")
        }
        val criticalErrors = buildList {
            if (moduleHardware.size != 12 || moduleHardware.mapNotNull { it.canId }.distinct().size != 12) add("Expected exactly 12 unique drive, steer, and encoder CAN IDs (three for each module).")
            if (hardware.count { it.role == DriveHardwareRole.GYRO } != 1) add("Expected one Pigeon CAN ID (`kPigeonId`).")
            if (canBus.isNullOrBlank()) add("Expected one named CTRE CAN bus (`new CANBus(\"name\", ...)`).")
            if (wheelRadius == null) add("Wheel radius (`kWheelRadius`) was not recognized.")
            if (driveGearRatio == null || steerGearRatio == null) add("Drive and steer gear ratios were not both recognized.")
            if (speedAt12Volts == null || slipCurrent == null) add("Speed-at-12V and slip-current constants were not both recognized.")
            if (bool("kInvertLeftSide") == null || bool("kInvertRightSide") == null) add("Left/right drive inversion booleans were not both recognized.")
            listOf("FrontLeft", "FrontRight", "BackLeft", "BackRight").forEach { corner ->
                if (scalar("k${corner}XPos") == null || scalar("k${corner}YPos") == null) add("$corner module X/Y position is incomplete.")
                if (scalar("k${corner}EncoderOffset") == null) add("$corner encoder offset was not recognized.")
                if (bool("k${corner}SteerMotorInverted") == null || bool("k${corner}EncoderInverted") == null) add("$corner steer/encoder inversion booleans are incomplete.")
            }
        }
        require(criticalErrors.isEmpty()) { "CTRE import is incomplete:\n- ${criticalErrors.joinToString("\n- ")}" }
        return CtreTunerImport(
            sourcePath = file.canonicalPath,
            sourceHash = sha256(text),
            hardware = hardware,
            geometry = DriveGeometry(requireNotNull(wheelRadius), requireNotNull(trackWidth), requireNotNull(wheelBase)),
            values = values,
            warnings = warnings
        )
    }

    private fun camelToEnum(value: String) = value.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
    private fun parseScalar(expression: String): Double? {
        expression.toDoubleOrNull()?.let { return it }
        val unit = Regex("(Inches|Meters|Rotations|Degrees)\\.of\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)").find(expression) ?: return null
        val value = unit.groupValues[2].toDouble()
        return when (unit.groupValues[1]) {
            "Inches" -> value * 0.0254
            "Meters" -> value
            "Rotations" -> value
            "Degrees" -> value / 360.0
            else -> null
        }
    }
    /** Hashes source text canonically so a reviewed vendor file has one identity on Windows and Linux. */
    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.replace("\r\n", "\n").toByteArray()).joinToString("") { "%02x".format(it) }
}

fun defaultDrivebase(projectId: String, kind: DrivebaseKind): DrivebaseDocument = canonicalTemplate(projectId, kind).toUiDrivebase()
