package com.ares.analytics.gateway.routes

import com.google.api.core.SettableApiFuture
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiagnosticsFutureTest {
    @Test
    fun `coroutine timeout cancels the underlying RPC future`() = runBlocking {
        val future = SettableApiFuture.create<String>()

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(10) { awaitApiFuture(future) }
        }

        assertTrue(future.isCancelled)
    }

    @Test
    fun `completed RPC future resumes the caller`() = runBlocking {
        val future = SettableApiFuture.create<String>()
        future.set("ok")

        assertEquals("ok", awaitApiFuture(future))
    }
}
