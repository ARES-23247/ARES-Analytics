package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DSLogDecoderServiceTest class.
 */
class DSLogDecoderServiceTest {

    @Test
    /**
     * testParseDsLogNonePdType fun.
     */
    fun testParseDsLogNonePdType() = runTest {
        val tempDb = File.createTempFile("dslog_test_db", ".db").apply { deleteOnExit() }
        val databaseService = DatabaseService(tempDb.absolutePath)
        val decoderService = DSLogDecoderService(databaseService)
        val batcher = FrameBatcher(databaseService, batchSize = 100)

        // Create mock binary dslog content
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1. Header
        dos.writeInt(4) // version
        dos.writeLong(1718200000L) // seconds
        dos.writeLong(0L) // fractional

        // 2. Record 1: NONE PD Type
        dos.writeByte(100) // tripTimeByte
        dos.writeByte(5) // packetLossByte
        dos.writeShort(3072) // batteryVoltageShort (3072 / 256.0 = 12.0 V)
        dos.writeByte(80) // cpuUtilizationByte
        dos.writeByte(0xFF) // maskByte
        dos.writeByte(40) // canUtilizationByte
        dos.writeByte(50) // wifiDbByte
        dos.writeShort(2560) // wifiMbShort (2560 / 256.0 = 10.0 MB)

        // PD Header (4 bytes, last byte 0 = NONE)
        dos.write(byteArrayOf(0, 0, 0, 0))

        dos.flush()
        val tempDsLog = File.createTempFile("mock_dslog", ".dslog").apply { deleteOnExit() }
        FileOutputStream(tempDsLog).use { fos ->
            fos.write(baos.toByteArray())
        }
        val sessionId = "dslog-session-1"
        decoderService.parseDsLog(tempDsLog, sessionId, batcher)
        batcher.flush()

        // Verify the database has the telemetry frames
        val batteryFrames = databaseService.getTelemetryForKey(sessionId, "/DSLog/BatteryVoltage")
        assertEquals(1, batteryFrames.size)
        assertEquals(12.0, batteryFrames[0].value, 1e-6)
        val cpuFrames = databaseService.getTelemetryForKey(sessionId, "/DSLog/CPUUtilization")
        assertEquals(1, cpuFrames.size)
        assertEquals(0.4, cpuFrames[0].value, 1e-6)
        val tripTimeFrames = databaseService.getTelemetryForKey(sessionId, "/DSLog/TripTimeMS")
        assertEquals(1, tripTimeFrames.size)
        assertEquals(50.0, tripTimeFrames[0].value, 1e-6)

        tempDsLog.delete()
        tempDb.delete()
    }
}
