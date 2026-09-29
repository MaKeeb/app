package com.makeeb.platform.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionCompleteUntilFirstUserAuthentication
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.numberWithBool
import platform.Foundation.writeToURL
import platform.posix.memcpy

/**
 * [PrivateFiles] in the calling process's own container, under Application Support. In the
 * keyboard extension that is the extension's container, which it can always write, with or
 * without Full Access (unlike the App Group, which is read-only without it). The companion app
 * can't see it.
 *
 * Files are written atomically with "complete until first user authentication" protection, and
 * the directory is excluded from backups, so the data never leaves the device. Before the first
 * unlock after a reboot the files can't be read: [read] then throws rather than returning null.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class ContainerFiles(private val directoryName: String = DIRECTORY) : PrivateFiles {
    private val fileManager get() = NSFileManager.defaultManager

    private val directory: NSURL? by lazy {
        val base = fileManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask).firstOrNull() as? NSURL
        base?.URLByAppendingPathComponent(directoryName, isDirectory = true)
    }

    override fun read(name: String): ByteArray? {
        val url = fileUrl(name)
        val path = url.path ?: throw PrivateFilesUnavailableException("no path")
        if (!fileManager.fileExistsAtPath(path)) return null
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val data = NSData.dataWithContentsOfURL(url, options = 0u, error = error.ptr)
                ?: throw PrivateFilesUnavailableException("read failed: ${error.value?.code}")
            return data.toByteArray()
        }
    }

    override fun write(name: String, bytes: ByteArray) {
        ensureDirectory()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val options = NSDataWritingAtomic or NSDataWritingFileProtectionCompleteUntilFirstUserAuthentication
            if (!bytes.toNSData().writeToURL(fileUrl(name), options = options, error = error.ptr)) {
                throw PrivateFilesUnavailableException("write failed: ${error.value?.code}")
            }
        }
    }

    override fun delete(name: String) {
        val url = fileUrl(name)
        val path = url.path ?: return
        if (fileManager.fileExistsAtPath(path) && !fileManager.removeItemAtURL(url, error = null)) {
            throw PrivateFilesUnavailableException("delete failed")
        }
    }

    private fun fileUrl(name: String): NSURL =
        directory?.URLByAppendingPathComponent(name) ?: throw PrivateFilesUnavailableException("no container")

    /** Creates the directory once, excluded from iCloud and device backups. */
    private fun ensureDirectory() {
        val url = directory ?: throw PrivateFilesUnavailableException("no container")
        val path = url.path ?: throw PrivateFilesUnavailableException("no path")
        if (fileManager.fileExistsAtPath(path)) return
        if (!fileManager.createDirectoryAtURL(url, withIntermediateDirectories = true, attributes = null, error = null)) {
            throw PrivateFilesUnavailableException("no directory")
        }
        url.setResourceValue(NSNumber.numberWithBool(true), forKey = NSURLIsExcludedFromBackupKey, error = null)
    }

    private companion object {
        const val DIRECTORY = "Keyboard"
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
