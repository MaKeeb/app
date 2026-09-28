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
        if (cached.isFile && sha256Of(cached.readBytes()) == sha256) return cached
        cacheDirectory.mkdirs()
        val failures = mutableListOf<String>()
        for (mirror in mirrors) {
            val bytes = try {
                download(mirror)
            } catch (e: IOException) {
                failures += "${mirror.url}: ${e.message}"
                continue
            }
            val actual = sha256Of(bytes)
            if (actual != sha256) {
                failures += "${mirror.url}: SHA-256 $actual, expected $sha256"
                continue
            }
            val partial = File(cacheDirectory, "$fileName.part")
            partial.writeBytes(bytes)
            Files.move(partial.toPath(), cached.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            log("downloaded $fileName from ${mirror.url} (${bytes.size} bytes, SHA-256 verified)")
            return cached
        }
        throw IOException("could not fetch $fileName:\n  " + failures.joinToString("\n  "))
    }

    private fun download(mirror: Mirror): ByteArray {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val request = HttpRequest.newBuilder(URI(mirror.url)).timeout(Duration.ofSeconds(120)).GET().build()
        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted", e)
        }
        if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()}")
        val body = response.body()
        return if (mirror.base64) Base64.getMimeDecoder().decode(body) else body
    }

    companion object {
        fun sha256Of(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
