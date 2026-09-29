package com.makeeb.platform.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.numberWithBool
import platform.posix.ENOENT
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.open
import platform.posix.rename
import platform.posix.stat
import platform.posix.unlink
import platform.posix.write

/**
 * [PackFiles] in the App Group container shared by the companion app and the keyboard extension.
 * The extension can read the container without Full Access (it is read-only then), which is why
 * packs live here and not in either target's own container. Only the companion app writes.
 *
 * Files are written with POSIX calls and renamed into place, so the extension never maps a half
 * written pack, and they are excluded from backups: they can be downloaded again (Apple's data
 * storage guidelines).
 */
@OptIn(ExperimentalForeignApi::class)
class AppGroupPackFiles(private val appGroupId: String, private val directoryName: String = DIRECTORY) : PackFiles {
    private val fileManager get() = NSFileManager.defaultManager

    /** `<container>/Library/Application Support/Packs`, or null without the App Group entitlement. */
    private val directory: String? by lazy {
        fileManager.containerURLForSecurityApplicationGroupIdentifier(appGroupId)
            ?.URLByAppendingPathComponent("Library/Application Support/$directoryName", isDirectory = true)
            ?.path
    }

    override fun list(): List<PackFile> {
        val dir = directory ?: return emptyList()
        val names = fileManager.contentsOfDirectoryAtPath(dir, error = null) ?: return emptyList()
        return names.mapNotNull { it as? String }
            .filter { !it.endsWith(PENDING) }
            .mapNotNull { name -> regularFileSize("$dir/$name")?.let { PackFile(name, it) } }
    }

    override fun map(name: String): ByteRegion? = directory?.let { MmapByteRegion.map("$it/$name") }

    override fun create(name: String): PendingPackFile {
        val dir = ensureDirectory()
        val target = "$dir/$name"
        val pending = target + PENDING
        val fd = open(pending, O_WRONLY or O_CREAT or O_TRUNC, FILE_MODE)
        if (fd < 0) throw PackFilesException("can't create $name (errno $errno)")
        return object : PendingPackFile {
            private var descriptor = fd

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (length == 0) return
                bytes.usePinned { pinned ->
                    var done = 0
                    while (done < length) {
                        val written = write(descriptor, pinned.addressOf(offset + done), (length - done).convert())
                        if (written <= 0) throw PackFilesException("write failed (errno $errno)")
                        done += written.toInt()
                    }
                }
            }

            override fun map(): ByteRegion = MmapByteRegion.map(pending) ?: throw PackFilesException("can't map $name")

            override fun commit() {
                if (fsync(descriptor) != 0) throw PackFilesException("sync failed (errno $errno)")
                closeDescriptor()
                if (rename(pending, target) != 0) throw PackFilesException("rename failed (errno $errno)")
            }

            override fun discard() {
                closeDescriptor()
                unlink(pending)
            }

            private fun closeDescriptor() {
                if (descriptor < 0) return
                close(descriptor)
                descriptor = -1
            }
        }
    }

    override fun delete(name: String) {
        val dir = directory ?: return
        if (unlink("$dir/$name") != 0 && errno != ENOENT) throw PackFilesException("can't delete $name (errno $errno)")
    }

    override fun deletePending() {
        val dir = directory ?: return
        fileManager.contentsOfDirectoryAtPath(dir, error = null)?.mapNotNull { it as? String }
            ?.filter { it.endsWith(PENDING) }
            ?.forEach { unlink("$dir/$it") }
    }

    /** Creates the directory once, excluded from iCloud and device backups. */
    private fun ensureDirectory(): String {
        val dir = directory ?: throw PackFilesException("no App Group container")
        if (!fileManager.fileExistsAtPath(dir)) {
            if (!fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)) {
                throw PackFilesException("can't create the packs directory")
            }
            NSURL.fileURLWithPath(dir, isDirectory = true).setResourceValue(NSNumber.numberWithBool(true), forKey = NSURLIsExcludedFromBackupKey, error = null)
        }
        return dir
    }

    private fun regularFileSize(path: String): Long? = memScoped {
        val info = alloc<stat>()
        if (stat(path, info.ptr) != 0) return null
        if ((info.st_mode.toInt() and S_IFMT) != S_IFREG) return null
        info.st_size
    }

    private companion object {
        const val DIRECTORY = "Packs"
        const val PENDING = ".part"

        /** rw-r--r--: the extension reads what the app writes. */
        const val FILE_MODE = 420
    }
}
