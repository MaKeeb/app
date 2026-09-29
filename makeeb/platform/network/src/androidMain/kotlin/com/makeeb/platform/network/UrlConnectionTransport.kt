package com.makeeb.platform.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * [HttpTransport] over `HttpURLConnection`, which needs no library and follows redirects between
 * HTTPS hosts (GitHub release assets → their CDN). Runs on the IO dispatcher. A blocked read
 * doesn't notice cancellation, so a watcher disconnects the socket when the caller cancels.
 */
class UrlConnectionTransport(private val timeoutMillis: Int = TIMEOUT_MILLIS) : HttpTransport {
    override suspend fun get(url: String, sink: BodySink): Unit = withContext(Dispatchers.IO) {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw TransportException("can't open the connection", cause = e)
        }
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        coroutineScope {
            val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    connection.disconnect()
                }
            }
            try {
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) throw TransportException("HTTP $status", status)
                sink.start(connection.contentLengthLong)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                    }
                }
            } catch (e: IOException) {
                // A disconnect from the watcher surfaces as an IOException: report the cancellation.
                coroutineContext.ensureActive()
                throw TransportException(e.message ?: "download failed", cause = e)
            } finally {
                watcher.cancel()
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 20_000
        const val BUFFER_SIZE = 64 * 1024
    }
}
