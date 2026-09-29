package com.makeeb.platform.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * [PackFiles] in a directory of the app's own storage. The keyboard (an input method service)
 * runs in the companion app's process, so both use the same directory, and a pack the companion
 * installs is there for the keyboard's next field.
 *
 * [forContext] picks device-protected storage: the keyboard is direct-boot aware and packs aren't
 * personal data, so they can be read on the lock screen before the first unlock, like the
 * preferences. They are downloadable again, so they stay out of backups (`noBackupFilesDir`).
 */
class DirectoryPackFiles(private val directory: File) : PackFiles {
    override fun list(): List<PackFile> =
        directory.listFiles().orEmpty()
            .filter { it.isFile && !it.name.endsWith(PENDING) }
            .map { PackFile(it.name, it.length()) }

    override fun map(name: String): ByteRegion? = mapFile(File(directory, name))

    override fun create(name: String): PendingPackFile {
        if (!directory.isDirectory && !directory.mkdirs()) throw PackFilesException("no directory")
        val target = File(directory, name)
        val pending = File(directory, name + PENDING)
        val out = try {
            FileOutputStream(pending)
        } catch (e: IOException) {
            throw PackFilesException("can't create $name", e)
        }
        return object : PendingPackFile {
            private var open = true

            override fun write(bytes: ByteArray, offset: Int, length: Int) = io("write") { out.write(bytes, offset, length) }

            override fun map(): ByteRegion = io("map") {
                out.flush()
                mapFile(pending) ?: throw IOException("unmappable")
            }

            override fun commit() = io("commit") {
                out.fd.sync()
                close()
                if (!pending.renameTo(target)) throw IOException("rename failed")
            }

            override fun discard() {
                close()
                pending.delete()
            }

            private fun close() {
                if (!open) return
                open = false
                try {
                    out.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    override fun delete(name: String) {
        val file = File(directory, name)
        if (file.exists() && !file.delete()) throw PackFilesException("can't delete $name")
    }

    override fun deletePending() {
        directory.listFiles().orEmpty().filter { it.name.endsWith(PENDING) }.forEach { it.delete() }
    }

    private inline fun <T> io(what: String, block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw PackFilesException("$what failed", e)
    }

    companion object {
        private const val PENDING = ".part"

        /** The packs directory in the app's device-protected, non-backed-up storage. */
        fun forContext(context: Context): DirectoryPackFiles =
            DirectoryPackFiles(File(context.createDeviceProtectedStorageContext().noBackupFilesDir, "packs"))

        /**
         * Maps [file] whole, read-only. The channel closes at once; the mapping stays valid until
         * the buffer is collected, even after the file is deleted.
         */
        private fun mapFile(file: File): ByteRegion? = try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                if (length <= 0 || length > Int.MAX_VALUE) null
                else MappedByteBufferRegion(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, length))
            }
        } catch (_: IOException) {
            null
        }
    }
}
