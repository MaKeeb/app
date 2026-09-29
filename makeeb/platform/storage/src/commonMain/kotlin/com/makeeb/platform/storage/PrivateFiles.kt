package com.makeeb.platform.storage

/**
 * Small files that belong to the keyboard alone, such as the words it learned. They stay on the
 * device: Android keeps them in credential-encrypted storage outside Auto Backup, iOS in the
 * keyboard extension's own container, excluded from backups. Neither is shared with other apps,
 * and on iOS not even with the companion app, which cannot read the extension's container.
 *
 * The storage can be locked: on Android before the first unlock after a reboot (the keyboard is
 * direct-boot aware), on iOS while data protection holds the file. A locked read or write throws
 * [PrivateFilesUnavailableException] rather than pretending the file is missing, so callers never
 * mistake a locked file for an empty one and overwrite it.
 *
 * Reads and writes block; call them off the main thread.
 */
interface PrivateFiles {
    /** The file's bytes, or null when it doesn't exist. */
    @Throws(PrivateFilesUnavailableException::class)
    fun read(name: String): ByteArray?

    /** Replaces [name] atomically: a reader sees the old content or the new, never a mix. */
    @Throws(PrivateFilesUnavailableException::class)
    fun write(name: String, bytes: ByteArray)

    @Throws(PrivateFilesUnavailableException::class)
    fun delete(name: String)
}

/** The storage can't be used now (locked before the first unlock, or an I/O error); try again later. */
class PrivateFilesUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
