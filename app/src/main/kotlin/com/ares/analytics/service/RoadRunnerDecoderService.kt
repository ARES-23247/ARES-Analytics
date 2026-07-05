package com.ares.analytics.service

import com.ares.analytics.shared.TelemetryFrame
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RoadRunnerDecoderService {

    sealed interface RRSchema
    object IntSchema : RRSchema
    object LongSchema : RRSchema
    object DoubleSchema : RRSchema
    object StringSchema : RRSchema
    object BooleanSchema : RRSchema
    class EnumSchema(val constants: List<String>) : RRSchema
    class ArraySchema(val elementSchema: RRSchema) : RRSchema
    class StructSchema(val fields: List<Pair<String, RRSchema>>) : RRSchema

    suspend fun parseRoadRunnerLog(
        file: File,
        sessionId: String,
        batcher: FrameBatcher
    ) {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        
        var offset = 0
        fun readString(): String {
            val len = buffer.getInt(offset)
            offset += 4
            val str = String(bytes, offset, len, Charsets.UTF_8)
            offset += len
            return str
        }

        fun readSchema(): RRSchema {
            val schemaType = buffer.getInt(offset)
            offset += 4
            return when (schemaType) {
                0 -> {
                    val numFields = buffer.getInt(offset)
                    offset += 4
                    val fields = mutableListOf<Pair<String, RRSchema>>()
                    for (i in 0 until numFields) {
                        fields.add(Pair(readString(), readSchema()))
                    }
                    StructSchema(fields)
                }
                1 -> IntSchema
                2 -> LongSchema
                3 -> DoubleSchema
                4 -> StringSchema
                5 -> BooleanSchema
                6 -> {
                    val numConstants = buffer.getInt(offset)
                    offset += 4
                    val constants = mutableListOf<String>()
                    for (i in 0 until numConstants) {
                        constants.add(readString())
                    }
                    EnumSchema(constants)
                }
                7 -> ArraySchema(readSchema())
                else -> throw IllegalArgumentException("Unknown schema type: $schemaType")
            }
        }

        fun arraySchemaCount(schema: RRSchema): Int {
            return when (schema) {
                is StructSchema -> {
                    var count = 0
                    for (field in schema.fields) {
                        count += arraySchemaCount(field.second)
                    }
                    count
                }
                is ArraySchema -> 1 + arraySchemaCount(schema.elementSchema)
                else -> 0
            }
        }

        fun readMsg(schema: RRSchema): Any {
            return when (schema) {
                is StructSchema -> {
                    val map = mutableMapOf<String, Any>()
                    for (field in schema.fields) {
                        map[field.first] = readMsg(field.second)
                    }
                    map
                }
                is IntSchema -> {
                    val v = buffer.getInt(offset)
                    offset += 4
                    v
                }
                is LongSchema -> {
                    val v = buffer.getLong(offset)
                    offset += 8
                    v
                }
                is DoubleSchema -> {
                    val v = buffer.getDouble(offset)
                    offset += 8
                    v
                }
                is StringSchema -> readString()
                is BooleanSchema -> {
                    val v = bytes[offset].toInt() != 0
                    offset += 1
                    v
                }
                is EnumSchema -> {
                    val ordinal = buffer.getInt(offset)
                    offset += 4
                    if (ordinal >= 0 && ordinal < schema.constants.size) {
                        schema.constants[ordinal]
                    } else {
                        "UNKNOWN"
                    }
                }
                is ArraySchema -> {
                    val size = buffer.getInt(offset)
                    offset += 4
                    val list = mutableListOf<Any>()
                    for (i in 0 until size) {
                        list.add(readMsg(schema.elementSchema))
                    }
                    list
                }
            }
        }

        // Check Magic
        if (bytes.size < 4) return
        val magic = String(bytes, offset, 2, Charsets.UTF_8)
        offset += 2
        if (magic != "RR") return

        var logRevision = buffer.getShort(offset).toInt()
        offset += 2
        if (logRevision != 0 && logRevision != 1) return

        val keyIDs = mutableMapOf<Int, String>()
        val keySchemas = mutableMapOf<Int, RRSchema>()
        var firstRRTimestamp: Long? = null
        var lastTimestampMs = 0L

        suspend fun flatten(prefix: String, value: Any, timestampMs: Long) {
            when (value) {
                is Boolean -> {
                    batcher.add(TelemetryFrame(timestampMs, sessionId, prefix, if (value) 1.0 else 0.0))
                }
                is Number -> {
                    batcher.add(TelemetryFrame(timestampMs, sessionId, prefix, value.toDouble()))
                }
                is Map<*, *> -> {
                    val x = value["x"] as? Number
                    val y = value["y"] as? Number
                    val heading = value["heading"] as? Number
                    if (x != null && y != null && heading != null) {
                        // Pose2d: convert inches to meters
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "$prefix/x", x.toDouble() * 0.0254))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "$prefix/y", y.toDouble() * 0.0254))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "$prefix/heading", heading.toDouble()))
                    } else {
                        for ((k, v) in value) {
                            if (k is String && v != null) {
                                flatten("$prefix/$k", v, timestampMs)
                            }
                        }
                    }
                }
                is List<*> -> {
                    for (i in value.indices) {
                        val v = value[i]
                        if (v != null) {
                            flatten("$prefix[$i]", v, timestampMs)
                        }
                    }
                }
            }
        }

        try {
            while (offset < bytes.size) {
                // Check for concatenated logs
                if (offset + 2 <= bytes.size && String(bytes, offset, 2, Charsets.UTF_8) == "RR") {
                    offset += 2
                    logRevision = buffer.getShort(offset).toInt()
                    offset += 2
                    keyIDs.clear()
                    keySchemas.clear()
                    continue
                }

                if (offset + 4 > bytes.size) break
                val type = buffer.getInt(offset)
                offset += 4

                when (type) {
                    0 -> {
                        val keyID = keyIDs.size
                        val keyName = readString()
                        val schema = readSchema()
                        keyIDs[keyID] = keyName
                        keySchemas[keyID] = schema
                        if (logRevision == 0) {
                            offset += 4 * arraySchemaCount(schema)
                        }
                    }
                    1 -> {
                        val keyID = buffer.getInt(offset)
                        offset += 4
                        val key = keyIDs[keyID] ?: continue
                        val schema = keySchemas[keyID] ?: continue
                        val msg = readMsg(schema)

                        // Update timestamp if timestamp is present in log
                        if ((key == "OPMODE_PRE_INIT" || key == "OPMODE_PRE_START" || key == "OPMODE_POST_STOP" || key == "TIMESTAMP") && msg is Long) {
                            if (firstRRTimestamp == null) {
                                firstRRTimestamp = msg
                            }
                            lastTimestampMs = (msg - firstRRTimestamp) / 1_000_000
                        } else if (msg is Map<*, *>) {
                            val ts = msg["timestamp"] as? Long
                            if (ts != null) {
                                if (firstRRTimestamp == null) {
                                    firstRRTimestamp = ts
                                }
                                lastTimestampMs = (ts - firstRRTimestamp) / 1_000_000
                            }
                        }

                        flatten(key, msg, lastTimestampMs)
                    }
                }
            }
        } catch (e: Exception) {
            // Handle unexpected EOF or parsing errors gracefully
        }
    }
}
