package com.makeeb.tools.dictionaries

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64

/**
 * A source file pinned by content: every mirror must serve bytes with [sha256]. Mirrors are tried
 * in order, and the first verified download is cached in the builder's download directory.
 * Downloads stream to disk, so corpora of hundreds of megabytes never sit on the heap.
 */
data class PinnedSource(
    /** The cached file's name. */
    val fileName: String,
    val sha256: String,
    val mirrors: List<Mirror>,
) {
    /** A URL for the file; gitiles serves raw files only as base64 (`?format=TEXT`). */
    data class Mirror(val url: String, val base64: Boolean = false)

    /** The verified file, downloading it first unless the cache already holds it. */
    fun fetch(cacheDirectory: File, log: (String) -> Unit): File {
        val cached = File(cacheDirectory, fileName)
        if (cached.isFile && sha256Of(cached) == sha256) return cached
        cacheDirectory.mkdirs()
        val failures = mutableListOf<String>()
        val partial = File(cacheDirectory, "$fileName.part")
        for (mirror in mirrors) {
            try {
                download(mirror, partial)
            } catch (e: IOException) {
                failures += "${mirror.url}: ${e.message}"
                continue
            }
            val actual = sha256Of(partial)
            if (actual != sha256) {
                failures += "${mirror.url}: SHA-256 $actual, expected $sha256"
                continue
            }
            Files.move(partial.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            log("downloaded $fileName from ${mirror.url} (${cached.length()} bytes, SHA-256 verified)")
            return cached
        }
        partial.delete()
        throw IOException("could not fetch $fileName:\n  " + failures.joinToString("\n  "))
    }

    private fun download(mirror: Mirror, target: File) {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val request = HttpRequest.newBuilder(URI(mirror.url)).timeout(Duration.ofMinutes(30)).GET().build()
        try {
            if (mirror.base64) {
                val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()}")
                target.writeBytes(Base64.getMimeDecoder().decode(response.body()))
            } else {
                val response = client.send(request, HttpResponse.BodyHandlers.ofFile(target.toPath()))
                if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()}")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted", e)
        }
    }

    companion object {
        fun sha256Of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}
