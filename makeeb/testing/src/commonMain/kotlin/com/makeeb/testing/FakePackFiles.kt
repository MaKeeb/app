package com.makeeb.testing

import com.makeeb.platform.storage.ByteArrayRegion
import com.makeeb.platform.storage.ByteRegion
import com.makeeb.platform.storage.PackFile
import com.makeeb.platform.storage.PackFiles
import com.makeeb.platform.storage.PackFilesException
import com.makeeb.platform.storage.PendingPackFile

/**
 * Pack storage in memory. Committed files are in [files]; unfinished ones in [pending]. [failWrites]
 * plays a full disk, [failCommits] a rename that fails.
 */
class FakePackFiles(vararg initial: Pair<String, ByteArray>) : PackFiles {
    val files: MutableMap<String, ByteArray> = initial.toMap(linkedMapOf())
    val pending: MutableMap<String, ByteArray> = linkedMapOf()

    var failWrites = false
    var failCommits = false

    /** Every [map] call so far, by name, to check what the keyboard mapped. */
    val mapped = mutableListOf<String>()

    override fun list(): List<PackFile> = files.map { (name, bytes) -> PackFile(name, bytes.size.toLong()) }

    override fun map(name: String): ByteRegion? {
        mapped += name
        return files[name]?.let(::ByteArrayRegion)
    }

    override fun create(name: String): PendingPackFile {
        pending[name] = ByteArray(0)
        return object : PendingPackFile {
            private var bytes = ByteArray(1024)
            private var size = 0

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (failWrites) throw PackFilesException("disk full")
                if (size + length > this.bytes.size) this.bytes = this.bytes.copyOf(maxOf(size + length, this.bytes.size * 2))
                bytes.copyInto(this.bytes, size, offset, offset + length)
                size += length
                pending[name] = this.bytes.copyOf(size)
            }

            override fun map(): ByteRegion = ByteArrayRegion(bytes.copyOf(size))

            override fun commit() {
                if (failCommits) throw PackFilesException("rename failed")
                pending.remove(name)
                files[name] = bytes.copyOf(size)
            }

            override fun discard() {
                pending.remove(name)
            }
        }
    }

    override fun delete(name: String) {
        files.remove(name)
    }

    override fun deletePending() {
        pending.clear()
    }
}
