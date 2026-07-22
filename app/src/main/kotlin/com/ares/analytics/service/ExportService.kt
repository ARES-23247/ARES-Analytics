package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class ExportService(private val databaseService: DatabaseService) {

    suspend fun exportToCsvList(
        sessionId: String,
        selectedKeys: List<String>,
        destinationFile: File
    ) = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()
        destinationFile.printWriter().use { writer ->
            writer.println("key,timestamp_ms,value")
            for (key in selectedKeys) {
                val frames = databaseService.getTelemetryForKey(sessionId, key)
                for (frame in frames) {
                    writer.println("${frame.key},${frame.timestampMs},${frame.value}")
                }
            }
        }
    }

    suspend fun exportToCsvTable(
        sessionId: String,
        selectedKeys: List<String>,
        destinationFile: File,
        samplingPeriodMs: Long? = null
    ) = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()

        // Fetch all frames
        val allFrames = mutableListOf<TelemetryFrame>()
        for (key in selectedKeys) {
            allFrames.addAll(databaseService.getTelemetryForKey(sessionId, key))
        }

        if (allFrames.isEmpty()) {
            destinationFile.printWriter().use { writer ->
                writer.println("timestamp_ms," + selectedKeys.joinToString(","))
            }
            return@withContext
        }
        val minTime = allFrames.minOf { it.timestampMs }
        val maxTime = allFrames.maxOf { it.timestampMs }

        // Generate timestamps
        val timestamps = if (samplingPeriodMs != null && samplingPeriodMs > 0) {
            val list = mutableListOf<Long>()
            var curr = minTime
            while (curr <= maxTime) {
                list.add(curr)
                curr += samplingPeriodMs
            }
            list
        } else {
            allFrames.map { it.timestampMs }.distinct().sorted()
        }

        // Map key to sorted frames
        val framesByKey = selectedKeys.associateWith { key ->
            databaseService.getTelemetryForKey(sessionId, key).sortedBy { it.timestampMs }
        }

        destinationFile.printWriter().use { writer ->
            writer.println("timestamp_ms," + selectedKeys.joinToString(","))

            // Track current frame index for each key for efficient sample-and-hold
            val indices = selectedKeys.associateWith { 0 }.toMutableMap()
            val lastValues = selectedKeys.associateWith { "" }.toMutableMap()

            for (ts in timestamps) {
                val rowValues = mutableListOf<String>()
                for (key in selectedKeys) {
                    val keyFrames = framesByKey[key] ?: emptyList()
                    var idx = indices[key] ?: 0

                    // Advance pointer to the latest frame <= ts
                    while (idx < keyFrames.size && keyFrames[idx].timestampMs <= ts) {
                        lastValues[key] = keyFrames[idx].value.toString()
                        idx++
                    }
                    indices[key] = idx
                    rowValues.add(lastValues[key] ?: "")
                }
                writer.println("$ts," + rowValues.joinToString(","))
            }
        }
    }

    suspend fun exportToWpiLog(
        sessionId: String,
        selectedKeys: List<String>,
        destinationFile: File
    ) = withContext(Dispatchers.IO) {
        destinationFile.parentFile?.mkdirs()
        val records = mutableListOf<WpiRecord>()

        // 1. Write control records for starting each key
        var nextEntryId = 1
        val keyEntryIds = mutableMapOf<String, Int>()

        for (key in selectedKeys) {
            val entryId = nextEntryId++
            keyEntryIds[key] = entryId

            // CONTROL_START = 0
            val nameBytes = key.toByteArray(Charsets.UTF_8)
            val typeBytes = "double".toByteArray(Charsets.UTF_8)
            val metadataBytes = "".toByteArray(Charsets.UTF_8)
            val payload = ByteBuffer.allocate(1 + 4 + 4 + nameBytes.size + 4 + typeBytes.size + 4 + metadataBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
            payload.put(0.toByte()) // CONTROL_START
            payload.putInt(entryId)
            payload.putInt(nameBytes.size)
            payload.put(nameBytes)
            payload.putInt(typeBytes.size)
            payload.put(typeBytes)
            payload.putInt(metadataBytes.size)
            payload.put(metadataBytes)

            records.add(WpiRecord(0, 0L, payload.array()))
        }

        // 2. Add data records
        for (key in selectedKeys) {
            val entryId = keyEntryIds[key] ?: continue
            val frames = databaseService.getTelemetryForKey(sessionId, key)
            for (frame in frames) {
                val doublePayload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(frame.value).array()
                records.add(WpiRecord(entryId, frame.timestampMs * 1000L, doublePayload))
            }
        }

        // Sort data records by timestamp
        records.sortBy { it.timestampMicro }

        // 3. Write to file
        BufferedOutputStream(FileOutputStream(destinationFile)).use { bos ->
            // Header: "WPILOG" (6 bytes)
            bos.write("WPILOG".toByteArray(Charsets.UTF_8))
            // Version: 0x0100 (2 bytes)
            bos.write(byteArrayOf(0x00.toByte(), 0x01.toByte()))
            // Extra header len: 0 (4 bytes)
            bos.write(byteArrayOf(0, 0, 0, 0))

            // Write each record
            for (rec in records) {
                val entryBytes = encodeInteger(rec.entry.toLong())
                val sizeBytes = encodeInteger(rec.payload.size.toLong())
                val tsBytes = encodeInteger(rec.timestampMicro)
                var lengthBitfield = 0
                lengthBitfield = lengthBitfield or ((entryBytes.size - 1) and 0x3)
                lengthBitfield = lengthBitfield or (((sizeBytes.size - 1) and 0x3) shl 2)
                lengthBitfield = lengthBitfield or (((tsBytes.size - 1) and 0x7) shl 4)

                bos.write(lengthBitfield)
                bos.write(entryBytes)
                bos.write(sizeBytes)
                bos.write(tsBytes)
                bos.write(rec.payload)
            }
        }
    }

    private class WpiRecord(val entry: Int, val timestampMicro: Long, val payload: ByteArray)

    private fun encodeInteger(value: Long): ByteArray {
        val bytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
        for (i in 7 downTo 1) {
            if (bytes[i] != 0.toByte()) {
                return bytes.copyOfRange(0, i + 1)
            }
        }
        return bytes.copyOfRange(0, 1)
    }
}
