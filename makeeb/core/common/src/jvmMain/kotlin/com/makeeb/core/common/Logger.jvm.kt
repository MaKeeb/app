package com.makeeb.core.common

actual fun platformLogger(): Logger = object : Logger {
    override fun debug(tag: String, message: String) = println("D/$tag: $message")

    override fun warn(tag: String, message: String, throwable: Throwable?) {
        println("W/$tag: $message")
        throwable?.printStackTrace()
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        println("E/$tag: $message")
        throwable?.printStackTrace()
    }
}
