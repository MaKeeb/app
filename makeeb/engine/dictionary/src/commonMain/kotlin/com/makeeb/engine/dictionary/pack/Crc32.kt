package com.makeeb.engine.dictionary.pack

import com.makeeb.platform.storage.ByteRegion

/**
 * CRC-32 (IEEE 802.3, as in zip and PNG). Common Kotlin has no checksum API. Packs are verified
 * when they are built or installed, never on every mapping: checking means reading every page.
 */
internal object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun of(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Int {
        var crc = -1
        for (i in from until to) crc = table[(crc xor bytes[i].toInt()) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }

    fun of(region: ByteRegion, from: Int, to: Int): Int {
        var crc = -1
        for (i in from until to) crc = table[(crc xor region.u8(i)) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }
}
