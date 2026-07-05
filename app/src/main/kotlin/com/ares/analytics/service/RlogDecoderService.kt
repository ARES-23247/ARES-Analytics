package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RlogDecoderService {

    suspend fun parseRlog(
        file: File,
        sessionId: String,
        batcher: FrameBatcher
    ) {
        val bytes = file.readBytes()
        if (bytes.size < 2) return

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        var offset = 0

        val logRevision = bytes[offset].toInt() and 0xFF
        offset += 1
        offset += 1 // skip second byte

        if (logRevision != 1 && logRevision != 2) return

        val keyIDs = mutableMapOf<Int, String>()
        val keyTypes = mutableMapOf<Int, String>()

        fun readString(length: Int): String {
            val str = String(bytes, offset, length, Charsets.UTF_8)
            offset += length
            return str
        }

        try {
            while (offset < bytes.size) {
                if (offset + 8 > bytes.size) break
                val timestampSec = buffer.getDouble(offset)
                offset += 8
                val timestampMs = (timestampSec * 1000.0).toLong()

                while (true) {
                    if (offset >= bytes.size) break
                    val type = bytes[offset].toInt() and 0xFF
                    offset += 1

                    if (type == 0) {
                        // New timestamp record boundary
                        break
                    }

                    when (type) {
                        1 -> { // New key ID
                            val keyID = buffer.getShort(offset).toInt() and 0xFFFF
                            offset += 2
                            val keyLength = buffer.getShort(offset).toInt() and 0xFFFF
                            offset += 2
                            val newKey = readString(keyLength)
                            keyIDs[keyID] = newKey

                            if (logRevision == 2) {
                                val typeLength = buffer.getShort(offset).toInt() and 0xFFFF
                                offset += 2
                                val newType = readString(typeLength)
                                keyTypes[keyID] = newType
                            }
                        }
                        2 -> { // Updated field
                            val keyID = buffer.getShort(offset).toInt() and 0xFFFF
                            offset += 2
                            val keyName = keyIDs[keyID] ?: continue

                            if (logRevision == 2) {
                                val valueLength = buffer.getShort(offset).toInt() and 0xFFFF
                                offset += 2
                                val fieldType = keyTypes[keyID] ?: "unknown"

                                val startValOffset = offset
                                when (fieldType) {
                                    "boolean" -> {
                                        val v = bytes[offset].toInt() != 0
                                        offset += 1
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, if (v) 1.0 else 0.0))
                                    }
                                    "int", "int64" -> {
                                        val v = buffer.getLong(offset)
                                        offset += 8
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, v.toDouble()))
                                    }
                                    "float" -> {
                                        val v = buffer.getFloat(offset)
                                        offset += 4
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, v.toDouble()))
                                    }
                                    "double" -> {
                                        val v = buffer.getDouble(offset)
                                        offset += 8
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, v))
                                    }
                                    "boolean[]" -> {
                                        for (i in 0 until valueLength) {
                                            val v = bytes[offset + i].toInt() != 0
                                            batcher.add(TelemetryFrame(timestampMs, sessionId, "$keyName[$i]", if (v) 1.0 else 0.0))
                                        }
                                        offset += valueLength
                                    }
                                    "int[]", "int64[]" -> {
                                        val count = valueLength / 8
                                        for (i in 0 until count) {
                                            val v = buffer.getLong(offset)
                                            offset += 8
                                            batcher.add(TelemetryFrame(timestampMs, sessionId, "$keyName[$i]", v.toDouble()))
                                        }
                                    }
                                    "float[]" -> {
                                        val count = valueLength / 4
                                        for (i in 0 until count) {
                                            val v = buffer.getFloat(offset)
                                            offset += 4
                                            batcher.add(TelemetryFrame(timestampMs, sessionId, "$keyName[$i]", v.toDouble()))
                                        }
                                    }
                                    "double[]" -> {
                                        val count = valueLength / 8
                                        for (i in 0 until count) {
                                            val v = buffer.getDouble(offset)
                                            offset += 8
                                            batcher.add(TelemetryFrame(timestampMs, sessionId, "$keyName[$i]", v))
                                        }
                                    }
                                    else -> {
                                        // Skip other complex types
                                        offset = startValOffset + valueLength
                                    }
                                }
                            } else if (logRevision == 1) {
                                val valueType = bytes[offset].toInt() and 0xFF
                                offset += 1
                                when (valueType) {
                                    0 -> { // null -> default 0.0
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, 0.0))
                                    }
                                    1 -> { // Boolean
                                        val v = bytes[offset].toInt() != 0
                                        offset += 1
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, if (v) 1.0 else 0.0))
                                    }
                                    9 -> { // Byte
                                        val v = bytes[offset].toInt() and 0xFF
                                        offset += 1
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, v.toDouble()))
                                    }
                                    3 -> { // Integer
                                        val v = buffer.getInt(offset)
                                        offset += 4
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, v.toDouble()))
                                    }
                                    5 -> { // Double
                                        val v = buffer.getDouble(offset)
                                        offset += 8
                                        batcher.add(TelemetryFrame(timestampMs, sessionId, keyName, v))
                                    }
                                    7 -> { // String
                                        val strLen = buffer.getShort(offset).toInt() and 0xFFFF
                                        offset += 2
                                        offset += strLen // skip string bytes
                                    }
                                    2 -> { // BooleanArray
                                        val arrLen = buffer.getShort(offset).toInt() and 0xFFFF
                                        offset += 2
                                        for (i in 0 until arrLen) {
                                            val v = bytes[offset].toInt() != 0
                                            offset += 1
                                            batcher.add(TelemetryFrame(timestampMs, sessionId, "$keyName[$i]", if (v) 1.0 else 0.0))
                                        }
                                    }
                                    10 -> { // ByteArray
                                        val arrLen = buffer.getShort(offset).toInt() and 0xFFFF
                                        offset += 2
                                        for (i in 0 until arrLen) {
                                            val v = bytes[offset].toInt() and 0xFF
                                            offset += 1
                                            batcher.add(TelemetryFrame(timestampMs, sessionId, "$keyName[$i]", v.toDouble()))
                                        }
                                    }
                                    4 -> { // IntegerArray
                                        val arrLen = buffer.getShort(offset).toInt() and 0xFFFF
                                        offset += 2
                                        for (i in 0 until arrLen) {
                                            val v = buffer.getInt(offset)
                                            offset += 4
                                            batcher.add(TelemetryFrame(timestampMs, sessionId, "$keyName[$i]", v.toDouble()))
                                        }
                                    }
                                    6 -> { // DoubleArray
                                        val arrLen = buffer.getShort(offset).toInt() and 0xFFFF
                                        offset += 2
                                        for (i in 0 until arrLen) {
                                            val v = buffer.getDouble(offset)
                                            offset += 8
                                            batcher.add(TelemetryFrame(timestampMs, sessionId, "$keyName[$i]", v))
                                        }
                                    }
                                    8 -> { // StringArray
                                        val arrLen = buffer.getShort(offset).toInt() and 0xFFFF
                                        offset += 2
                                        for (i in 0 until arrLen) {
                                            val strLen = buffer.getShort(offset).toInt() and 0xFFFF
                                            offset += 2
                                            offset += strLen
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Handle parsing/corrupted data skips
        }
    }
}
