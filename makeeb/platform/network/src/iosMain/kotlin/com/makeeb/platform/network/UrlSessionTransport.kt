package com.makeeb.platform.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseCancel
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [HttpTransport] over `NSURLSession`, for the companion app (the keyboard extension never
 * downloads). An ephemeral session per request: nothing is cached or kept in cookies. The body
 * streams to the sink from the session's serial delegate queue, so a pack is hashed and written
 * as it arrives.
 */
class UrlSessionTransport(private val timeoutSeconds: Double = TIMEOUT_SECONDS) : HttpTransport {
    override suspend fun get(url: String, sink: BodySink) {
        val nsUrl = NSURL.URLWithString(url) ?: throw TransportException("not a URL")
        suspendCancellableCoroutine { continuation ->
            val delegate = Receiver(sink) { failure ->
                if (failure == null) continuation.resume(Unit) else continuation.resumeWithException(failure)
            }
            val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration.apply {
                timeoutIntervalForRequest = timeoutSeconds
            }
            val queue = NSOperationQueue().apply { maxConcurrentOperationCount = 1 }
            val session = NSURLSession.sessionWithConfiguration(configuration, delegate, queue)
            val task = session.dataTaskWithRequest(NSURLRequest.requestWithURL(nsUrl))
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }

    /**
     * The session's delegate. Nothing may throw out of a delegate call (a Kotlin exception that
     * reaches Objective-C ends the process), so failures are kept and reported on completion.
     */
    @OptIn(ExperimentalForeignApi::class)
    private class Receiver(private val sink: BodySink, private val done: (Throwable?) -> Unit) : NSObject(), NSURLSessionDataDelegateProtocol {
        private var failure: Throwable? = null

        override fun URLSession(
            session: NSURLSession,
            dataTask: NSURLSessionDataTask,
            didReceiveResponse: NSURLResponse,
            completionHandler: (NSURLSessionResponseDisposition) -> Unit,
        ) {
            val status = (didReceiveResponse as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
            if (status != HTTP_OK) {
                failure = TransportException("HTTP $status", status)
                completionHandler(NSURLSessionResponseCancel)
                return
            }
            try {
                sink.start(didReceiveResponse.expectedContentLength)
                completionHandler(NSURLSessionResponseAllow)
            } catch (e: Throwable) {
                failure = e
                completionHandler(NSURLSessionResponseCancel)
            }
        }

        override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
            if (failure != null) return
            try {
                val length = didReceiveData.length.toInt()
                if (length == 0) return
                val bytes = ByteArray(length)
                bytes.usePinned { memcpy(it.addressOf(0), didReceiveData.bytes, didReceiveData.length) }
                sink.write(bytes, 0, length)
            } catch (e: Throwable) {
                failure = e
                dataTask.cancel()
            }
        }

        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            session.finishTasksAndInvalidate()
            done(failure ?: didCompleteWithError?.let { TransportException(it.localizedDescription) })
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20.0
        const val HTTP_OK = 200
    }
}
