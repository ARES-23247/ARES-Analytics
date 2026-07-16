package com.ares.analytics.service

import com.ares.analytics.shared.SessionAnnotation
import com.ares.analytics.shared.TelemetryFrame
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class DSLogDecoderService(private val databaseService: DatabaseService) {

    enum class PowerDistributionType {
        REV, CTRE, NONE
    }

    suspend fun parseDsLog(
        dslogFile: File,
        sessionId: String,
        batcher: FrameBatcher
    ) {
        if (!dslogFile.exists()) return

        var startTimeMs = 0.0
        var recordCount = 0

        FileInputStream(dslogFile).use { fis ->
            DataInputStream(fis).use { dis ->
                try {
                    val version = dis.readInt()
                    if (version != 4) {
                        throw IllegalArgumentException("Unsupported dslog version: $version")
                    }

                    val seconds = dis.readLong()
                    val fractional = dis.readLong()
                    startTimeMs = convertLVTime(seconds, fractional)

                    var lastBatteryVolts = 0.0

                    while (true) {
                        // Read 10-byte DS status record
                        val tripTimeByte = dis.readUnsignedByte()
                        val packetLossByte = dis.readByte()
                        val batteryVoltageShort = dis.readUnsignedShort()
                        val cpuUtilizationByte = dis.readUnsignedByte()
                        val maskByte = dis.readUnsignedByte()
                        val canUtilizationByte = dis.readUnsignedByte()
                        val wifiDbByte = dis.readUnsignedByte()
                        val wifiMbShort = dis.readUnsignedShort()

                        val timestampMs = (startTimeMs + recordCount * 20).toLong()

                        val tripTimeMs = tripTimeByte * 0.5
                        val packetLoss = Math.min(Math.max(packetLossByte * 4 * 0.01, 0.0), 1.0)
                        
                        var batteryVolts = batteryVoltageShort.toDouble() / 256.0
                        if (batteryVolts > 20.0) {
                            batteryVolts = lastBatteryVolts
                        } else {
                            lastBatteryVolts = batteryVolts
                        }

                        val cpuUtilization = cpuUtilizationByte * 0.5 * 0.01
                        val brownout = (maskByte and (1 shl 7)) == 0
                        val watchdog = (maskByte and (1 shl 6)) == 0
                        val dsTeleop = (maskByte and (1 shl 5)) == 0
                        val dsDisabled = (maskByte and (1 shl 3)) == 0
                        val robotTeleop = (maskByte and (1 shl 2)) == 0
                        val robotAuto = (maskByte and (1 shl 1)) == 0
                        val robotDisabled = (maskByte and 1) == 0
                        val canUtilization = canUtilizationByte * 0.5 * 0.01
                        val wifiDb = wifiDbByte * 0.5
                        val wifiMb = wifiMbShort.toDouble() / 256.0

                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/TripTimeMS", tripTimeMs))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/PacketLoss", packetLoss))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/BatteryVoltage", batteryVolts))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/CPUUtilization", cpuUtilization))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/Status/Brownout", if (brownout) 1.0 else 0.0))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/Status/Watchdog", if (watchdog) 1.0 else 0.0))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/Status/DSTeleop", if (dsTeleop) 1.0 else 0.0))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/Status/DSDisabled", if (dsDisabled) 1.0 else 0.0))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/Status/RobotTeleop", if (robotTeleop) 1.0 else 0.0))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/Status/RobotAuto", if (robotAuto) 1.0 else 0.0))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/Status/RobotDisabled", if (robotDisabled) 1.0 else 0.0))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/CANUtilization", canUtilization))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/WifiDb", wifiDb))
                        batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/WifiMb", wifiMb))

                        // Power Distribution Header (4 bytes)
                        val pdHeader = ByteArray(4)
                        dis.readFully(pdHeader)
                        val pdTypeByte = pdHeader[3].toInt() and 0xFF
                        val pdType = getPDType(pdTypeByte)

                        when {
                            pdType == PowerDistributionType.REV -> {
                                dis.readUnsignedByte() // skip CAN ID
                                val bitBytes = ByteArray(27)
                                dis.readFully(bitBytes)

                                val revBooleans = BooleanArray(216)
                                var bitIdx = 0
                                for (b in bitBytes) {
                                    val byteVal = b.toInt() and 0xFF
                                    for (i in 0 until 8) {
                                        revBooleans[bitIdx++] = (byteVal and (1 shl i)) != 0
                                    }
                                }

                                val currents = mutableListOf<Double>()
                                for (i in 0 until 20) {
                                    val readPosition = (i / 3) * 32 + (i % 3) * 10
                                    var value = 0
                                    for (j in 0 until 10) {
                                        if (revBooleans[readPosition + j]) {
                                            value = value or (1 shl j)
                                        }
                                    }
                                    currents.add(value.toDouble() / 8.0)
                                }

                                val extraBytes = ByteArray(4)
                                dis.readFully(extraBytes)
                                for (i in 0 until 4) {
                                    currents.add((extraBytes[i].toInt() and 0xFF).toDouble() / 16.0)
                                }

                                dis.readUnsignedByte() // skip last byte

                                for (i in currents.indices) {
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/PowerDistributionCurrents[$i]", currents[i]))
                                }
                            }
                            pdType == PowerDistributionType.CTRE -> {
                                dis.readUnsignedByte() // skip CAN ID
                                val bitBytes = ByteArray(21)
                                dis.readFully(bitBytes)

                                val ctreBooleans = BooleanArray(168)
                                var bitIdx = 0
                                for (b in bitBytes) {
                                    val byteVal = b.toInt() and 0xFF
                                    for (i in 0 until 8) {
                                        ctreBooleans[bitIdx++] = (byteVal and (1 shl i)) != 0
                                    }
                                }

                                val currents = mutableListOf<Double>()
                                for (i in 0 until 16) {
                                    val readPosition = (i / 6) * 64 + (i % 6) * 10
                                    var value = 0
                                    for (j in 0 until 8) {
                                        if (ctreBooleans[readPosition + j]) {
                                            value = value or (1 shl j)
                                        }
                                    }
                                    currents.add(value.toDouble() / 8.0)
                                }

                                dis.skipBytes(3) // skip extra metadata bytes (25 total CTRE payload size minus 1 CAN ID minus 21 currents)

                                for (i in currents.indices) {
                                    batcher.add(TelemetryFrame(timestampMs, sessionId, "/DSLog/PowerDistributionCurrents[$i]", currents[i]))
                                }
                            }
                        }

                        recordCount++
                    }
                } catch (e: EOFException) {
                    // Normal end of file
                }
            }
        }

        // Try parsing matching .dsevents file if it exists
        val eventsFile = File(dslogFile.parentFile, dslogFile.nameWithoutExtension + ".dsevents")
        if (eventsFile.exists()) {
            parseDsEvents(eventsFile, sessionId, startTimeMs)
        }
    }

    private suspend fun parseDsEvents(
        dseventsFile: File,
        sessionId: String,
        startTimeMs: Double
    ) {
        FileInputStream(dseventsFile).use { fis ->
            DataInputStream(fis).use { dis ->
                try {
                    val version = dis.readInt()
                    if (version != 4) return

                    val seconds = dis.readLong()
                    val fractional = dis.readLong()
                    val fileStartTimeMs = convertLVTime(seconds, fractional)

                    while (true) {
                        val recSeconds = dis.readLong()
                        val recFractional = dis.readLong()
                        val eventTimeMs = convertLVTime(recSeconds, recFractional)

                        val length = dis.readInt()
                        if (length <= 0) continue

                        val textBytes = ByteArray(length)
                        dis.readFully(textBytes)
                        var text = String(textBytes, Charsets.UTF_8)

                        // Filter XML tags
                        val tags = listOf("<TagVersion>", "<time>", "<count>", "<flags>", "<Code>", "<location>", "<stack>")
                        for (tag in tags) {
                            while (text.contains(tag)) {
                                val tagIndex = text.indexOf(tag)
                                val nextIndex = text.indexOf("<", tagIndex + 1)
                                if (nextIndex != -1) {
                                    text = text.substring(0, tagIndex) + text.substring(nextIndex)
                                } else {
                                    text = text.substring(0, tagIndex)
                                }
                            }
                        }
                        text = text.replace("<message> ", "")
                        text = text.replace("<details> ", "")
                        text = text.trim()

                        val relativeSec = (eventTimeMs - fileStartTimeMs) / 1000.0
                        val annotation = SessionAnnotation(
                            annotationId = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            text = "[Event at +${String.format("%.2f", relativeSec)}s] $text",
                            createdAt = eventTimeMs.toLong(),
                            authorId = "Driver Station"
                        )
                        databaseService.insertAnnotation(annotation)
                    }
                } catch (e: EOFException) {
                    // Normal end of file
                }
            }
        }
    }

    private fun convertLVTime(seconds: Long, fractional: Long): Double {
        var time = -2082826800L // 1904/1/1
        time += seconds
        val fracDouble = fractional.toULong().toDouble() / Math.pow(2.0, 64.0)
        return (time.toDouble() + fracDouble) * 1000.0
    }

    private fun getPDType(id: Int): PowerDistributionType {
        return when (id) {
            33 -> PowerDistributionType.REV
            25 -> PowerDistributionType.CTRE
            else -> PowerDistributionType.NONE
        }
    }
}
