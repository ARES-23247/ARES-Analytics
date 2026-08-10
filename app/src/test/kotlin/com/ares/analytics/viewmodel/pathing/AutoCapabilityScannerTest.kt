package com.ares.analytics.viewmodel.pathing

import com.ares.analytics.shared.League
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoCapabilityScannerTest {
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
