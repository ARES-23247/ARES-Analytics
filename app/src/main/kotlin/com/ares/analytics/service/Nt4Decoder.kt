package com.ares.analytics.service

import kotlinx.serialization.json.*

object Nt4Decoder {
    fun decodeMsgPackInt(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        if (offset >= bytes.size) return Pair(0, 0)
        val marker = bytes[offset].toInt() and 0xFF
        return when {
            marker in 0x00..0x7f -> Pair(marker, 1)
            marker in 0xe0..0xff -> Pair(marker - 256, 1)
            marker == 0xcc || marker == 0xd0 -> Pair(if (marker == 0xcc) bytes[offset + 1].toInt() and 0xFF else bytes[offset + 1].toInt(), 2)
            marker == 0xcd || marker == 0xd1 -> {
                val value = readInt16(bytes, offset + 1)
                Pair(if (marker == 0xcd) value else value.toShort().toInt(), 3)
            }
            marker == 0xce || marker == 0xd2 -> Pair(readInt32(bytes, offset + 1), 5)
            else -> Pair(0, 1) // Fallback
        }
    }

    fun decodeMsgPackLong(bytes: ByteArray, offset: Int): Pair<Long, Int> {
        if (offset >= bytes.size) return Pair(0L, 0)
        val marker = bytes[offset].toInt() and 0xFF
        return when {
            marker in 0x00..0x7f -> Pair(marker.toLong(), 1)
            marker in 0xe0..0xff -> Pair((marker - 256).toLong(), 1)
            marker == 0xcc || marker == 0xd0 -> Pair((if (marker == 0xcc) bytes[offset + 1].toInt() and 0xFF else bytes[offset + 1].toInt()).toLong(), 2)
            marker == 0xcd || marker == 0xd1 -> {
                val value = readInt16(bytes, offset + 1)
                Pair((if (marker == 0xcd) value else value.toShort().toInt()).toLong(), 3)
            }
            marker == 0xce || marker == 0xd2 -> {
                val value = readInt32(bytes, offset + 1)
                Pair(if (marker == 0xce) (value.toLong() and 0xFFFFFFFFL) else value.toLong(), 5)
            }
            marker == 0xcf || marker == 0xd3 -> Pair(readInt64(bytes, offset + 1), 9)
            else -> Pair(0L, 1)
        }
    }

    fun parseMsgPackValue(bytes: ByteArray, offset: Int): Pair<Any?, Int> {
        if (offset >= bytes.size) return Pair(null, 0)
        val marker = bytes[offset].toInt() and 0xFF

        when {
            // Booleans
            marker == 0xc2 -> return Pair(false, 1)
            marker == 0xc3 -> return Pair(true, 1)
            
            // Positive/Negative FixInt
            marker in 0x00..0x7f -> return Pair(marker, 1)
            marker in 0xe0..0xff -> return Pair(marker - 256, 1)
            
            // int8, uint8
            marker == 0xcc || marker == 0xd0 -> {
                if (offset + 1 < bytes.size) {
                    val value = if (marker == 0xcc) bytes[offset + 1].toInt() and 0xFF else bytes[offset + 1].toInt()
                    return Pair(value, 2)
                }
            }
            
            // int16, uint16
            marker == 0xcd || marker == 0xd1 -> {
                if (offset + 2 < bytes.size) {
                    val value = readInt16(bytes, offset + 1)
                    val out = if (marker == 0xcd) value else value.toShort().toInt()
                    return Pair(out, 3)
                }
            }
            
            // int32, uint32
            marker == 0xce || marker == 0xd2 -> {
                if (offset + 4 < bytes.size) {
                    val value = readInt32(bytes, offset + 1)
                    val out = if (marker == 0xce) (value.toLong() and 0xFFFFFFFFL) else value.toLong()
                    return Pair(out, 5)
                }
            }
            
            // int64, uint64
            marker == 0xcf || marker == 0xd3 -> {
                if (offset + 8 < bytes.size) {
                    val value = readInt64(bytes, offset + 1)
                    return Pair(value, 9)
                }
            }
            
            // float32
            marker == 0xca -> {
                if (offset + 4 < bytes.size) {
                    val bits = readInt32(bytes, offset + 1)
                    return Pair(java.lang.Float.intBitsToFloat(bits).toDouble(), 5)
                }
            }
            
            // float64
            marker == 0xcb -> {
                if (offset + 8 < bytes.size) {
                    val bits = readInt64(bytes, offset + 1)
                    return Pair(java.lang.Double.longBitsToDouble(bits), 9)
                }
            }
            
            // Strings
            marker in 0xa0..0xbf || marker == 0xd9 || marker == 0xda || marker == 0xdb -> {
                val (len, headerSize) = getStringLengthAndHeader(marker, bytes, offset)
                if (offset + headerSize + len <= bytes.size) {
                    val strValue = String(bytes, offset + headerSize, len, Charsets.UTF_8)
                    return Pair(strValue, headerSize + len)
                }
            }
            
            // Arrays
            marker in 0x90..0x9f || marker == 0xdc || marker == 0xdd -> {
                val (arrayLen, headerSize) = getArrayLengthAndHeader(marker, bytes, offset)
                var currentOffset = offset + headerSize
                val list = ArrayList<Any?>(arrayLen)
                for (i in 0 until arrayLen) {
                    val (elem, size) = parseMsgPackValue(bytes, currentOffset)
                    list.add(elem)
                    currentOffset += size
                }
                return Pair(list, currentOffset - offset)
            }
        }

        val size = getMsgPackValueLength(bytes, offset)
        return Pair(null, size)
    }

    fun readInt16(bytes: ByteArray, offset: Int): Int {
        return (((bytes[offset].toInt() and 0xFF) shl 8) or
                (bytes[offset + 1].toInt() and 0xFF))
    }

    fun readInt32(bytes: ByteArray, offset: Int): Int {
        return (((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF))
    }

    fun readInt64(bytes: ByteArray, offset: Int): Long {
        return (((bytes[offset].toLong() and 0xFF) shl 56) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 48) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 40) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 32) or
                ((bytes[offset + 4].toLong() and 0xFF) shl 24) or
                ((bytes[offset + 5].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 6].toLong() and 0xFF) shl 8) or
                (bytes[offset + 7].toLong() and 0xFF))
    }

    fun getStringLengthAndHeader(marker: Int, bytes: ByteArray, offset: Int): Pair<Int, Int> {
        return when {
            marker in 0xa0..0xbf -> Pair(marker - 0xa0, 1)
            marker == 0xd9 -> {
                if (offset + 1 < bytes.size) Pair(bytes[offset + 1].toInt() and 0xFF, 2)
                else Pair(0, 2)
            }
            marker == 0xda -> {
                if (offset + 2 < bytes.size) Pair(readInt16(bytes, offset + 1), 3)
                else Pair(0, 3)
            }
            marker == 0xdb -> {
                if (offset + 4 < bytes.size) Pair(readInt32(bytes, offset + 1), 5)
                else Pair(0, 5)
            }
            else -> Pair(0, 1)
        }
    }

    fun getArrayLengthAndHeader(marker: Int, bytes: ByteArray, offset: Int): Pair<Int, Int> {
        return when {
            marker in 0x90..0x9f -> Pair(marker - 0x90, 1)
            marker == 0xdc -> {
                if (offset + 2 < bytes.size) Pair(readInt16(bytes, offset + 1), 3)
                else Pair(0, 3)
            }
            marker == 0xdd -> {
                if (offset + 4 < bytes.size) Pair(readInt32(bytes, offset + 1), 5)
                else Pair(0, 5)
            }
            else -> Pair(0, 1)
        }
    }

    fun getMsgPackValueLength(bytes: ByteArray, offset: Int): Int {
        if (offset >= bytes.size) return 0
        val marker = bytes[offset].toInt() and 0xFF
        return when {
            marker in 0x00..0x7f || marker in 0xe0..0xff -> 1
            marker in 0x80..0x8f -> {
                val size = marker - 0x80
                var len = 1
                for (i in 0 until size * 2) {
                    len += getMsgPackValueLength(bytes, offset + len)
                }
                len
            }
            marker in 0x90..0x9f -> {
                val size = marker - 0x90
                var len = 1
                for (i in 0 until size) {
                    len += getMsgPackValueLength(bytes, offset + len)
                }
                len
            }
            marker in 0xa0..0xbf -> 1 + (marker - 0xa0)
            marker == 0xc0 || marker == 0xc2 || marker == 0xc3 -> 1
            marker == 0xc4 || marker == 0xd9 -> {
                if (offset + 1 < bytes.size) 2 + (bytes[offset + 1].toInt() and 0xFF) else 2
            }
            marker == 0xc5 || marker == 0xda -> {
                if (offset + 2 < bytes.size) 3 + readInt16(bytes, offset + 1) else 3
            }
            marker == 0xc6 || marker == 0xdb -> {
                if (offset + 4 < bytes.size) 5 + readInt32(bytes, offset + 1) else 5
            }
            marker == 0xca -> 5
            marker == 0xcb -> 9
            marker == 0xcc || marker == 0xd0 -> 2
            marker == 0xcd || marker == 0xd1 -> 3
            marker == 0xce || marker == 0xd2 -> 5
            marker == 0xcf || marker == 0xd3 -> 9
            marker == 0xdc -> {
                if (offset + 2 < bytes.size) {
                    val size = readInt16(bytes, offset + 1)
                    var len = 3
                    for (i in 0 until size) {
                        len += getMsgPackValueLength(bytes, offset + len)
                    }
                    len
                } else 3
            }
            marker == 0xdd -> {
                if (offset + 4 < bytes.size) {
                    val size = readInt32(bytes, offset + 1)
                    var len = 5
                    for (i in 0 until size) {
                        len += getMsgPackValueLength(bytes, offset + len)
                    }
                    len
                } else 5
            }
            marker == 0xde -> {
                if (offset + 2 < bytes.size) {
                    val size = readInt16(bytes, offset + 1)
                    var len = 3
                    for (i in 0 until size * 2) {
                        len += getMsgPackValueLength(bytes, offset + len)
                    }
                    len
                } else 3
            }
            marker == 0xdf -> {
                if (offset + 4 < bytes.size) {
                    val size = readInt32(bytes, offset + 1)
                    var len = 5
                    for (i in 0 until size * 2) {
                        len += getMsgPackValueLength(bytes, offset + len)
                    }
                    len
                } else 5
            }
            else -> 1
        }
    }
}
