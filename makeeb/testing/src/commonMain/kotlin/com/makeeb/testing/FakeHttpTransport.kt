package com.makeeb.testing

import com.makeeb.platform.network.BodySink
import com.makeeb.platform.network.HttpTransport
import com.makeeb.platform.network.TransportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.yield

/**
 * A server in memory. Each URL answers with a [Response]; anything else is a 404. [offline]
 * fails every request as a missing connection would. Bodies arrive in [chunkSize] pieces, with a
 * suspension point between them; [pauseAfter] holds a body after that many bytes until [resume],
 * to look at a download in progress or cancel it.
 */
class FakeHttpTransport : HttpTransport {
    sealed interface Response {
        /** [declaredLength] is the Content-Length sent (-1: none). */
        class Body(val bytes: ByteArray, val declaredLength: Long = bytes.size.toLong()) : Response

        class Status(val code: Int) : Response
    }

    val responses: MutableMap<String, Response> = linkedMapOf()
    val requests: MutableList<String> = mutableListOf()
    var offline = false
    var chunkSize = 4096

    var pauseAfter: Long? = null
    private var gate: CompletableDeferred<Unit>? = null

    /** Lets a body held by [pauseAfter] continue. */
    fun resume() {
        pauseAfter = null
        gate?.complete(Unit)
    }

    fun serve(url: String, bytes: ByteArray) {
        responses[url] = Response.Body(bytes)
    }

    override suspend fun get(url: String, sink: BodySink) {
        requests += url
        yield()
        if (offline) throw TransportException("offline")
        when (val response = responses[url] ?: Response.Status(404)) {
            is Response.Status -> throw TransportException("HTTP ${response.code}", response.code)
            is Response.Body -> {
                sink.start(response.declaredLength)
                var at = 0
                while (at < response.bytes.size) {
                    val limit = pauseAfter
                    if (limit != null && at >= limit) {
                        val held = CompletableDeferred<Unit>()
                        gate = held
                        held.await()
                    }
                    val length = minOf(chunkSize, response.bytes.size - at)
                    sink.write(response.bytes, at, length)
                    at += length
                    yield()
                }
            }
        }
    }
}
