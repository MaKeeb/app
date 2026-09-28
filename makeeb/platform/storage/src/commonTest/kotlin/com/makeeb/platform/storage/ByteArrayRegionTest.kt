package com.makeeb.platform.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ByteArrayRegionTest {
    private val region = ByteArrayRegion(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF.toByte(), 0xFE.toByte(), 0x7F, 0x80.toByte()))

    @Test
    fun readsAreLittleEndianAndUnsigned() {
        assertEquals(0x01, region.u8(0))
        assertEquals(0xFF, region.u8(4))
        assertEquals(0x0201, region.u16(0))
        assertEquals(0xFEFF, region.u16(4))
        assertEquals(0x030201, region.u24(0))
        assertEquals(0x04030201, region.i32(0))
        assertEquals(0x807FFEFF.toInt(), region.i32(4))
    }

    @Test
    fun copiesARange() {
        val out = ByteArray(4)
        region.copyInto(2, out, 1, 3)
        assertContentEquals(byteArrayOf(0, 0x03, 0x04, 0xFF.toByte()), out)
    }

    @Test
    fun readsOutsideTheRegionThrow() {
        assertFailsWith<IndexOutOfBoundsException> { region.u8(8) }
        assertFailsWith<IndexOutOfBoundsException> { region.u16(7) }
    }
}
