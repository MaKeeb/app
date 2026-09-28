package com.makeeb.core.common

/** Minimal logging port. Keyboard code runs inside other apps; never log typed text. */
interface Logger {
    fun debug(tag: String, message: String)
    fun warn(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}

/** The platform logger: logcat on Android, NSLog on iOS, stdout on the JVM (tests). */
expect fun platformLogger(): Logger

/** Process-wide logger. Swap it in tests or to route logs elsewhere. */
object Log : Logger {
    var delegate: Logger = platformLogger()

    override fun debug(tag: String, message: String) = delegate.debug(tag, message)
    override fun warn(tag: String, message: String, throwable: Throwable?) = delegate.warn(tag, message, throwable)
    override fun error(tag: String, message: String, throwable: Throwable?) = delegate.error(tag, message, throwable)
}
