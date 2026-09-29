package com.makeeb.testing

import com.makeeb.platform.storage.PrivateFiles
import com.makeeb.platform.storage.PrivateFilesUnavailableException

/**
 * Private files in memory. [locked] plays Android's direct boot (or iOS data protection): every
 * call throws until it is cleared. [failWrites] plays a full disk.
 */
class FakePrivateFiles(vararg initial: Pair<String, ByteArray>) : PrivateFiles {
    val files: MutableMap<String, ByteArray> = initial.toMap(mutableMapOf())

    var locked = false
    var failWrites = false

    /** Successful writes so far, to check batching. */
    var writes = 0
        private set

    override fun read(name: String): ByteArray? {
        if (locked) throw PrivateFilesUnavailableException("locked")
        return files[name]?.copyOf()
    }

    override fun write(name: String, bytes: ByteArray) {
        if (locked || failWrites) throw PrivateFilesUnavailableException("unavailable")
        files[name] = bytes.copyOf()
        writes++
    }

    override fun delete(name: String) {
        if (locked) throw PrivateFilesUnavailableException("locked")
        files.remove(name)
    }
}
