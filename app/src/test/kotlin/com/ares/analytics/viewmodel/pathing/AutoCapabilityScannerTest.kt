package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.League
import com.areslib.catalog.ActionDescriptor
import com.areslib.catalog.CapabilityCatalogCodec
import com.areslib.catalog.CapabilityCatalogDocument
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoCapabilityScannerTest {
    @Test
    fun `canonical project catalog is authoritative and needs no source scan`() {
        val root = createTempDir(prefix = "ares-canonical-capabilities-")
        try {
            File(root, ".ares/action-catalog.json").apply {
                parentFile.mkdirs()
                writeText(
                    CapabilityCatalogCodec.encode(
                        CapabilityCatalogDocument(
                            projectId = "test-robot",
                            actions = listOf(
                                ActionDescriptor(
                                    key = "shooter.fire",
                                    displayName = "Fire shooter",
                                    description = "Fires one game piece.",
                                    category = "Shooter"
                                )
                            )
                        )
                    )
                )
            }
            File(root, "TeamCode/src/main/java/example/Fake.kt").apply {
                parentFile.mkdirs()
                writeText("val misleading = CommandKey(\"do.not.discover\")")
            }
            File(root, "TeamCode/src/main/assets/ares/auto-capabilities.json").apply {
                parentFile.mkdirs()
                writeText("""{"schemaVersion":1,"actions":[{"key":"legacy.only"}]}""")
            }

            val result = AutoCapabilityScanner().scan(root.path, League.FTC)

            assertEquals(listOf("shooter.fire"), result.catalog.map { it.key.value })
            assertEquals(CapabilityCatalogSource.CANONICAL_PROJECT_CATALOG, result.source)
            assertEquals(0, result.kotlinFileCount)
            assertTrue(result.warnings.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `corrupt canonical catalog fails closed instead of falling back`() {
        val root = createTempDir(prefix = "ares-corrupt-capabilities-")
        try {
            File(root, ".ares/action-catalog.json").apply {
                parentFile.mkdirs()
                writeText("{broken")
            }
            File(root, ".ares/auto-capabilities.json").writeText(
                """{"schemaVersion":1,"actions":[{"key":"unsafe.fallback"}]}"""
            )

            val result = AutoCapabilityScanner().scan(root.path, League.FRC)

            assertTrue(result.catalog.isEmpty())
            assertEquals(CapabilityCatalogSource.CANONICAL_PROJECT_CATALOG, result.source)
            assertTrue(result.warnings.single().contains("invalid"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `FTC asset manifest is discovered without a robot connection`() {
        val root = createTempDir(prefix = "ares-capabilities-")
        try {
            File(root, "TeamCode/src/main/assets/ares/auto-capabilities.json").apply {
                parentFile.mkdirs()
                writeText(
                    """
                    {
                      "schemaVersion": 1,
                      "actions": [{
                        "key": "lights.green",
                        "displayName": "Lights green",
                        "description": "Shows the ready state.",
                        "category": "Indicator"
                      }]
                    }
                    """.trimIndent()
                )
            }

            val result = AutoCapabilityScanner().scan(root.path, League.FTC)

            assertEquals(listOf("lights.green"), result.catalog.map { it.key.value })
            assertTrue(result.warnings.isEmpty())
            assertEquals(1, result.manifestsRead.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `asset-only folder explains why no actions were found`() {
        val root = createTempDir(prefix = "ares-empty-capabilities-")
        try {
            File(root, "src/main/assets").mkdirs()

            val result = AutoCapabilityScanner().scan(root.path, League.FTC)

            assertTrue(result.catalog.isEmpty())
            assertTrue(result.warnings.single().contains(root.canonicalPath))
        } finally {
            root.deleteRecursively()
        }
    }
}
