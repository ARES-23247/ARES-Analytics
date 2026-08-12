package com.ares.analytics

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackagedProjectValidationTest {
    @Test
    fun `complete fixture exercises every packaged project codec`() {
        val fixture = checkNotNull(javaClass.classLoader.getResource("packaged-runtime-project"))
        val result = validatePackagedProject(File(fixture.toURI()).path)

        assertTrue(result.isValid, result.errors.joinToString())
        assertEquals(1, result.routineCount)
        assertEquals(1, result.subsystemCount)
    }

    @Test
    fun `validation command is isolated from ordinary desktop startup`() {
        assertEquals(null, runPackagedProjectValidationCommand(emptyArray()))
        assertEquals(64, runPackagedProjectValidationCommand(arrayOf(PACKAGED_PROJECT_VALIDATION_COMMAND)))
    }
}
