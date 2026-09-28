package com.makeeb.platform.storage

/**
 * Read-only data files that ship inside the app: APK assets on Android, the keyboard extension's
 * bundle on iOS. Files are mapped, not read, so large data never lands on the Kotlin heap.
 *
 * Both locations are readable before the first unlock (Android direct boot) and, on iOS, without
 * Full Access.
 */
fun interface BundledFiles {
    /**
     * Maps [name] read-only, or returns null when it is missing or can't be mapped. On Android an
     * asset can only be mapped when the APK stores it uncompressed (`androidResources.noCompress`).
     */
    fun map(name: String): ByteRegion?
}
