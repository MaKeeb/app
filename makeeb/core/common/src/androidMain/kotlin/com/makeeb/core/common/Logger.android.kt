package com.makeeb.core.common

actual fun platformLogger(): Logger = object : Logger {
    override fun debug(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.w(tag, message, throwable)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.e(tag, message, throwable)
    }
}
