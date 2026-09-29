package com.makeeb.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    // FIPS 180-4 examples and the NIST "one million a" vector.
    @Test
    fun matchesTheStandardVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc".encodeToByteArray()))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.hex(ByteArray(1_000_000) { 'a'.code.toByte() }))
    }

    @Test
    fun anySplitOfTheInputGivesTheSameDigest() {
        val bytes = ByteArray(1000) { (it * 31 + 7).toByte() }
        val whole = Sha256.hex(bytes)
        for (chunk in listOf(1, 3, 55, 56, 63, 64, 65, 128, 999)) {
            val sha = Sha256()
            var at = 0
            while (at < bytes.size) {
                val length = minOf(chunk, bytes.size - at)
                sha.update(bytes, at, length)
                at += length
            }
            assertEquals(whole, sha.hexDigest(), "chunks of $chunk")
        }
    }
}
