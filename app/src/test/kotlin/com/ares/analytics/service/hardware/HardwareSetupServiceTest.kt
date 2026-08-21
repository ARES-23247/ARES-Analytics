package com.ares.analytics.service.hardware

import com.ares.analytics.service.drivebase.DrivebaseKind
import com.ares.analytics.service.drivebase.DrivebaseProjectRepository
import com.ares.analytics.service.drivebase.defaultDrivebase
import com.ares.analytics.shared.League
import com.ares.analytics.viewmodel.project.SubsystemProjectRepository
import com.areslib.drivetrain.DrivetrainComponentDocument
import com.areslib.drivetrain.DrivetrainComponentRole
import com.areslib.drivetrain.DrivetrainDocumentCodec
import com.areslib.subsystem.SubsystemHardwareConnection
import com.areslib.subsystem.SubsystemPlatform
import com.areslib.subsystem.SubsystemTemplate
import com.areslib.subsystem.SubsystemTemplates
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardwareSetupServiceTest {
    @Test
    fun `review is bound to exact canonical hardware hashes and becomes stale after an edit`() {
        val root = Files.createTempDirectory("ares-hardware-review").toFile()
        try {
            seedDrivebase(root)
            val subsystemRepository = SubsystemProjectRepository()
            val lift = lift("arm")
            subsystemRepository.save(root.path, lift)
            val service = HardwareSetupService()

            val initial = service.inspect(root.path, League.FTC)
            assertTrue(initial.items.any { it.displayName == "Motor" && it.address == "arm" })
            assertTrue(initial.canReview)
            assertEquals(HardwareReviewStatus.NOT_REVIEWED, initial.reviewStatus)

            val reviewed = service.saveReview(
                root.path,
                League.FTC,
                HardwareReviewRequest(
                    reviewerName = "Student Driver",
                    wiringMatched = true,
                    addressesChecked = true,
                    directionsChecked = true,
                    neutralOutputsChecked = true,
                    limitsChecked = true,
                ),
            )
            assertEquals(HardwareReviewStatus.CURRENT, reviewed.reviewStatus)
            assertEquals("Student Driver", reviewed.reviewedBy)
            assertEquals(null, service.deploymentBlockReason(root.path, League.FTC))

            val changed = lift.copy(
                hardware = lift.hardware.map { device ->
                    device.copy(connection = device.connection.copy(hardwareMapName = "arm-updated"))
                },
            )
            subsystemRepository.save(root.path, changed)
            val stale = service.inspect(root.path, League.FTC)
            assertEquals(HardwareReviewStatus.STALE, stale.reviewStatus)
            assertTrue(service.deploymentBlockReason(root.path, League.FTC)!!.contains("changed after"))

            java.io.File(root, ".ares/hardware-review.json").writeText("not-json")
            val invalid = service.inspect(root.path, League.FTC)
            assertEquals(HardwareReviewStatus.INVALID, invalid.reviewStatus)
            assertTrue(invalid.canReview, "A malformed review must be replaceable after the hardware itself validates")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `cross-document address collision fails review before any record is written`() {
        val root = Files.createTempDirectory("ares-hardware-collision").toFile()
        try {
            seedDrivebase(root)
            SubsystemProjectRepository().save(root.path, lift("fl"))
            val service = HardwareSetupService()

            val snapshot = service.inspect(root.path, League.FTC)
            assertTrue(snapshot.errorIssues.any { it.message.contains("is claimed by") })
            assertTrue(!snapshot.canReview)
            assertTrue(service.deploymentBlockReason(root.path, League.FTC)!!.contains("blocking issue"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `FTC commissioning plan includes rear motors exact names and hold-to-run controls`() {
        val root = Files.createTempDirectory("ares-hardware-commissioning").toFile()
        try {
            seedDrivebase(root, includeLogicalWheelModule = true)
            val snapshot = HardwareSetupService().inspect(root.path, League.FTC)

            val plan = snapshot.commissioningPlan()

            assertTrue(plan.ftcDiagnosticAvailable)
            assertEquals(listOf("A / Cross", "B / Circle", "X / Square", "Y / Triangle"), plan.ftcMotorChecks.map { it.gamepadControl })
            assertEquals(listOf("fl", "fr", "rl", "rr"), plan.ftcMotorChecks.map { it.hardwareMapName })
            assertTrue(plan.hardwareMapEntries.none { it.displayName == "Mecanum drivebase" })
            assertTrue(!plan.clipboardText.contains("Mecanum drivebase"))
            assertTrue(plan.clipboardText.contains("Rear left: rl"))
            assertTrue(plan.clipboardText.contains("Rear right: rr"))
            assertTrue(plan.clipboardText.contains("release to stop"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `FTC motor diagnostic fails closed when a canonical wheel role is absent`() {
        val root = Files.createTempDirectory("ares-hardware-incomplete-diagnostic").toFile()
        try {
            seedDrivebase(root)
            val snapshot = HardwareSetupService().inspect(root.path, League.FTC)
            val incomplete = snapshot.copy(items = snapshot.items.filterNot { it.roleKey == "REAR_RIGHT_DRIVE" })

            val plan = incomplete.commissioningPlan()

            assertTrue(!plan.ftcDiagnosticAvailable)
            assertTrue(plan.ftcDiagnosticBlockReason!!.contains("exactly one"))
            assertEquals(listOf("fl", "fr", "rl"), plan.ftcMotorChecks.map { it.hardwareMapName })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun seedDrivebase(root: java.io.File, includeLogicalWheelModule: Boolean = false) {
        val base = defaultDrivebase("team1-robot", DrivebaseKind.FTC_MECANUM)
        DrivebaseProjectRepository().saveReviewed(
            root.path,
            expectedContentHash = null,
            document = base,
        )
        if (includeLogicalWheelModule) {
            val source = java.io.File(root, ".ares/drivetrains").listFiles().orEmpty().single()
            val canonical = DrivetrainDocumentCodec.decode(source.readText())
            source.writeText(
                DrivetrainDocumentCodec.encode(
                    canonical.copy(
                        components = canonical.components + DrivetrainComponentDocument(
                            uid = "drive.mecanum",
                            displayName = "Mecanum drivebase",
                            role = DrivetrainComponentRole.WHEEL_MODULE,
                            hardwareId = "mecanum",
                        ),
                    ),
                ),
            )
        }
    }

    private fun lift(hardwareMapName: String) = SubsystemTemplates.create(
        template = SubsystemTemplate.SIMPLE_ACTUATOR,
        documentId = "lift",
        kotlinTypeName = "Lift",
        platform = SubsystemPlatform.FTC,
    ).let { document ->
        document.copy(
            hardware = document.hardware.map { device ->
                device.copy(connection = SubsystemHardwareConnection(hardwareMapName = hardwareMapName))
            },
        )
    }
}
