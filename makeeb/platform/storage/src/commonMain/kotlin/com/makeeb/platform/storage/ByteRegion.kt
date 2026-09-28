package com.makeeb.platform.storage

/**
 * Read-only random access to a block of bytes, usually a memory-mapped file.
 *
 * Dictionaries are read through this port instead of being loaded into Kotlin collections. A
 * mapped file lives outside the Kotlin heap, and its pages are clean memory: the OS can drop and
 * re-read them, and iOS doesn't count them toward the keyboard extension's jetsam footprint
 * (docs/research/dictionaries-autocorrect.md §5.2).
 *
 * Reads are absolute (there is no cursor), so one region can be read from several threads.
 * Multi-byte values are little-endian. Reading outside `0 until size` throws
 * [IndexOutOfBoundsException] rather than touching memory past the mapping.
 */
interface ByteRegion {
    val size: Int

    /** The byte at [offset], as 0..255. */
    fun u8(offset: Int): Int

    fun u16(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

    fun u24(offset: Int): Int = u16(offset) or (u8(offset + 2) shl 16)

    /** Four bytes as a signed Int (the bits are the unsigned value). */
    fun i32(offset: Int): Int = u16(offset) or (u16(offset + 2) shl 16)

    /** Copies [length] bytes starting at [offset] into [destination]. */
    fun copyInto(offset: Int, destination: ByteArray, destinationOffset: Int = 0, length: Int) {
        for (i in 0 until length) destination[destinationOffset + i] = u8(offset + i).toByte()
    }

    /**
     * Releases the mapping. Reads afterwards throw. Regions held for the life of the process
     * (bundled dictionaries) never need closing.
     */
    fun close() {}
}

/** A [ByteRegion] over a heap array: tests, the pack builder, and packs small enough to read whole. */
class ByteArrayRegion(private val bytes: ByteArray) : ByteRegion {
    override val size: Int get() = bytes.size

    override fun u8(offset: Int): Int = bytes[offset].toInt() and 0xFF

    override fun copyInto(offset: Int, destination: ByteArray, destinationOffset: Int, length: Int) {
        bytes.copyInto(destination, destinationOffset, offset, offset + length)
    }
}
