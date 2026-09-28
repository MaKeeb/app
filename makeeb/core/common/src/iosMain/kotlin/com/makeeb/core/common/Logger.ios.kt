package com.makeeb.core.common

import platform.Foundation.NSLog

actual fun platformLogger(): Logger = object : Logger {
    override fun debug(tag: String, message: String) = NSLog("D/%@: %@", tag, message)

    override fun warn(tag: String, message: String, throwable: Throwable?) =
        NSLog("W/%@: %@ %@", tag, message, throwable?.stackTraceToString().orEmpty())

    override fun error(tag: String, message: String, throwable: Throwable?) =
        NSLog("E/%@: %@ %@", tag, message, throwable?.stackTraceToString().orEmpty())
}
