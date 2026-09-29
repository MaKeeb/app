package com.makeeb.platform.network

import kotlin.coroutines.cancellation.CancellationException

/**
 * HTTP GET for the companion app's downloads: the dictionary pack catalogue and the packs. The
 * keyboard never uses it: typing works offline and without Full Access on iOS, and a download is
 * something the user starts in the companion app.
 *
 * Bodies stream into a [BodySink] as they arrive, so a pack of several megabytes never has to sit
 * on the heap whole, and the caller can hash and store it on the way (and show progress).
 */
interface HttpTransport {
    /**
     * Fetches [url], following redirects (GitHub release assets redirect to a CDN), and feeds the
     * body to [sink] on a background thread. Cancelling the coroutine cancels the request.
     *
     * @throws TransportException for a network error or a status other than 200, and whatever
     * [sink] throws, which also stops the transfer.
     */
    @Throws(TransportException::class, CancellationException::class)
    suspend fun get(url: String, sink: BodySink)
}

/** Receives a response body. Calls come one at a time, [start] first. */
interface BodySink {
    /** [contentLength] in bytes, or -1 when the server doesn't say. */
    fun start(contentLength: Long)

    fun write(bytes: ByteArray, offset: Int, length: Int)
}

/**
 * A request failed: no connection, a timeout, or an HTTP [status] other than 200. Nothing about
 * the request beyond that is kept, so it is safe to show.
 */
class TransportException(message: String, val status: Int? = null, cause: Throwable? = null) : Exception(message, cause)
