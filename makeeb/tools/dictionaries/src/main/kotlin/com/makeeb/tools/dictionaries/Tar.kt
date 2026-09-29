package com.makeeb.tools.dictionaries

import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream

/**
 * Reads a tar stream entry by entry: enough of ustar and GNU tar for the corpus archives, so the
 * builder needs no archive library. Calls [action] with each regular file's path and a stream of
 * its bytes; whatever [action] leaves unread is skipped.
 */
fun forEachTarEntry(input: InputStream, action: (path: String, content: InputStream) -> Unit) {
    val header = ByteArray(BLOCK)
    var longName: String? = null
    while (true) {
        if (!readBlock(input, header)) return
        if (header.all { it.toInt() == 0 }) return
        val size = size(header)
        val type = header[156].toInt().toChar()
        val content = Bounded(input, size)
        when (type) {
            'L' -> longName = content.readBytes().decodeToString().trimEnd('\u0000')
            '0', '\u0000' -> {
                val name = longName ?: (text(header, 345, 155).let { if (it.isEmpty()) "" else "$it/" } + text(header, 0, 100))
                longName = null
                action(name, content)
            }
            else -> longName = null
        }
        content.skipRest()
        val padding = (BLOCK - size % BLOCK) % BLOCK
        skipFully(input, padding)
    }
}

private const val BLOCK = 512

private fun readBlock(input: InputStream, block: ByteArray): Boolean {
    var read = 0
    while (read < block.size) {
        val n = input.read(block, read, block.size - read)
        if (n < 0) {
            if (read == 0) return false
            throw EOFException("truncated tar header")
        }
        read += n
    }
    return true
}

private fun text(header: ByteArray, at: Int, length: Int): String {
    var end = at
    while (end < at + length && header[end].toInt() != 0) end++
    return String(header, at, end - at, Charsets.UTF_8)
}

/** Octal, or GNU base-256 when the first byte has its high bit set. */
private fun size(header: ByteArray): Long {
    if (header[124].toInt() and 0x80 != 0) {
        var value = 0L
        for (i in 125 until 136) value = (value shl 8) or (header[i].toLong() and 0xFF)
        return value
    }
    return text(header, 124, 12).trim().ifEmpty { "0" }.toLong(8)
}

private fun skipFully(input: InputStream, count: Long) {
    var left = count
    while (left > 0) {
        val skipped = input.skip(left)
        if (skipped > 0) left -= skipped
        else if (input.read() < 0) throw EOFException("truncated tar entry") else left--
    }
}

/** At most [size] bytes of the underlying stream; closing it does not close the archive. */
private class Bounded(input: InputStream, size: Long) : FilterInputStream(input) {
    private var left = size

    override fun read(): Int {
        if (left <= 0) return -1
        val byte = super.read()
        if (byte >= 0) left--
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val n = super.read(b, off, minOf(len.toLong(), left).toInt())
        if (n > 0) left -= n
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(minOf(n, left))
        left -= skipped
        return skipped
    }

    override fun available(): Int = minOf(super.available().toLong(), left).toInt()

    override fun close() = Unit

    fun skipRest() {
        skipFully(`in`, left)
        left = 0
    }
}
