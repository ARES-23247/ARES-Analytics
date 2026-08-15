package com.ares.analytics.service.hardware

import com.ares.analytics.service.drivebase.DrivebaseKind
import com.ares.analytics.service.drivebase.DrivebaseProjectRepository
import com.ares.analytics.service.drivebase.defaultDrivebase
import com.ares.analytics.shared.League
import com.ares.analytics.viewmodel.project.SubsystemProjectRepository
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

    private fun seedDrivebase(root: java.io.File) {
        DrivebaseProjectRepository().saveReviewed(
            root.path,
            expectedContentHash = null,
            document = defaultDrivebase("team1-robot", DrivebaseKind.FTC_MECANUM),
        )
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
