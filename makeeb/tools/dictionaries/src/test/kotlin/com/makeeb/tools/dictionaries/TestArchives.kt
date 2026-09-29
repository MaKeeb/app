package com.makeeb.tools.dictionaries

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/** A ustar archive of [entries], enough for [forEachTarEntry]: corpora in tests. */
internal fun tar(entries: List<Pair<String, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    for ((name, content) in entries) {
        val header = ByteArray(512)
        name.encodeToByteArray().copyInto(header, 0)
        "%011o".format(content.size).encodeToByteArray().copyInto(header, 124)
        header[156] = '0'.code.toByte()
        "ustar".encodeToByteArray().copyInto(header, 257)
        out.write(header)
        out.write(content)
        out.write(ByteArray((512 - content.size % 512) % 512))
    }
    out.write(ByteArray(1024))
    return out.toByteArray()
}

internal fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { GZIPOutputStream(it).use { gz -> gz.write(bytes) } }.toByteArray()

/** A Leipzig-style corpus file: `id<TAB>sentence` lines in a `*-sentences.txt` inside a tar.gz. */
internal fun corpusFile(lines: List<String>): File = File.createTempFile("corpus", ".tar.gz").apply {
    deleteOnExit()
    writeBytes(gzip(tar(listOf("x/x-sentences.txt" to lines.joinToString("\n", postfix = "\n").encodeToByteArray()))))
}
