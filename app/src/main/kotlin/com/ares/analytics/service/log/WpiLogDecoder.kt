package com.ares.analytics.service.log

import com.ares.analytics.service.FrameBatcher
import com.ares.analytics.shared.TelemetryFrame
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**

 * Physical units: Distances in $m$, angles in $rad$, velocities in $m/s$ or $rad/s$, time in $s$.

 *

 */
class WpiLogDecoder {

    suspend fun parseWpiLog(file: File, sessionId: String, batcher: FrameBatcher) {
        FileInputStream(file).use { fis ->
            val headerBytes = ByteArray(12)
            if (fis.read(headerBytes) != 12) return
            val magic = String(headerBytes, 0, 6)
            if (magic != "WPILOG") return

            // Map from entryId to (name, type)
            val entryMap = mutableMapOf<Int, Pair<String, String>>()
            val fileLength = file.length()
            var bytesRead = 12L

            while (bytesRead < fileLength) {
                val headerByte = fis.read()
                if (headerByte == -1) break
                bytesRead++
                val entryIdSize = (headerByte and 0x03) + 1
                val payloadSizeSize = ((headerByte ushr 2) and 0x03) + 1
                val timestampSize = ((headerByte ushr 4) and 0x03) + 1
                val recordHeaderSize = entryIdSize + payloadSizeSize + timestampSize
                val recordHeaderBytes = ByteArray(recordHeaderSize)
                if (fis.read(recordHeaderBytes) != recordHeaderSize) break
                bytesRead += recordHeaderSize
                val buffer = ByteBuffer.wrap(recordHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
                val entryId = readVariableInt(buffer, entryIdSize)
                val payloadSize = readVariableInt(buffer, payloadSizeSize)
                val timestampMicro = readVariableLong(buffer, timestampSize)
                val timestampMs = timestampMicro / 1000
                val payload = ByteArray(payloadSize)
                if (fis.read(payload) != payloadSize) break
                bytesRead += payloadSize

                if (entryId == 0) {
                    // Control Record
                    if (payload.isNotEmpty()) {
                        val controlType = payload[0].toInt()
                        val controlBuffer = ByteBuffer.wrap(payload, 1, payload.size - 1).order(ByteOrder.LITTLE_ENDIAN)
                        when (controlType) {
                            0 -> { // Start
                                if (controlBuffer.remaining() >= 4) {
                                    val newEntryId = controlBuffer.int
                                    val name = readNullTerminatedString(controlBuffer)
                                    val type = readNullTerminatedString(controlBuffer)
                                    entryMap[newEntryId] = Pair(name, type)
                                }
                            }
                            1 -> { // Finish
                                if (controlBuffer.remaining() >= 4) {
                                    val finishEntryId = controlBuffer.int
                                    entryMap.remove(finishEntryId)
                                }
                            }
                        }
                    }
                } else {
                    // Data Record
                    val entry = entryMap[entryId] ?: continue
                    val name = entry.first
                    val type = entry.second
                    val valBuffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                    when (type) {
                        "double" -> {
                            if (payload.size == 8) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.double))
                            }
                        }
                        "float" -> {
                            if (payload.size == 4) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.float.toDouble()))
                            }
                        }
                        "int64" -> {
                            if (payload.size == 8) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.long.toDouble()))
                            }
                        }
                        "int32" -> {
                            if (payload.size == 4) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, valBuffer.int.toDouble()))
                            }
                        }
                        "boolean" -> {
                            if (payload.isNotEmpty()) {
                                batcher.add(TelemetryFrame(timestampMs, sessionId, name, if (payload[0].toInt() != 0) 1.0 else 0.0))
                            }
                        }
                        "double[]" -> {
                            val count = payload.size / 8
                            for (i in 0 until count) {
                                if (valBuffer.remaining() >= 8) {
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, "$name[$i]", valBuffer.double))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun readVariableInt(buffer: ByteBuffer, size: Int): Int {
        var value = 0
        for (i in 0 until size) {
            value = value or ((buffer.get().toInt() and 0xFF) shl (i * 8))
        }
        return value
    }

    private fun readVariableLong(buffer: ByteBuffer, size: Int): Long {
        var value = 0L
        for (i in 0 until size) {
            value = value or ((buffer.get().toLong() and 0xFFL) shl (i * 8))
        }
        return value
    }

    private fun readNullTerminatedString(buffer: ByteBuffer): String {
        val bytes = mutableListOf<Byte>()
        while (buffer.hasRemaining()) {
            val b = buffer.get()
            if (b == 0.toByte()) break
            bytes.add(b)
        }
        return String(bytes.toByteArray())
    }
}
