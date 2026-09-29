package com.makeeb.platform.storage

/**
 * Downloaded dictionary packs, stored where the keyboard can map them. Android keeps them in the
 * app's own device-protected files: the keyboard runs in the companion app's process, and can
 * read them before the first unlock too. iOS keeps them in the App Group container, which the
 * keyboard extension can read without Full Access. Only the companion app writes; the keyboard
 * lists and maps.
 *
 * A file appears under its name only once complete ([PendingPackFile.commit] renames it into
 * place), and no file is ever changed in place: a new version gets a new name. So whatever the
 * keyboard has mapped stays valid, even in another process, until it lets go of the mapping.
 *
 * Every call blocks on the file system: call them off the main thread.
 */
interface PackFiles {
    /** The complete files, in no particular order. Empty when there are none or the directory can't be read. */
    fun list(): List<PackFile>

    /** Maps [name] read-only, or returns null when it is missing or can't be mapped. */
    fun map(name: String): ByteRegion?

    /** Starts writing [name] under a temporary name nothing lists; a previous unfinished [name] is overwritten. */
    @Throws(PackFilesException::class)
    fun create(name: String): PendingPackFile

    @Throws(PackFilesException::class)
    fun delete(name: String)

    /** Deletes what interrupted downloads left behind. Call it when none is running. */
    fun deletePending()
}

/** A complete file and its size in bytes. */
data class PackFile(val name: String, val size: Long)

/** A file being written; invisible to [PackFiles.list] until [commit]. */
interface PendingPackFile {
    @Throws(PackFilesException::class)
    fun write(bytes: ByteArray, offset: Int, length: Int)

    /** Maps what has been written so far, to check it before it is committed. Release it before [commit]. */
    @Throws(PackFilesException::class)
    fun map(): ByteRegion

    /** Flushes the file to storage and renames it to its name in one step. */
    @Throws(PackFilesException::class)
    fun commit()

    /** Deletes the file. Safe to call after a failed write or [commit], and more than once. */
    fun discard()
}

/** The pack storage failed: no space, no container, an I/O error. */
class PackFilesException(message: String, cause: Throwable? = null) : Exception(message, cause)
