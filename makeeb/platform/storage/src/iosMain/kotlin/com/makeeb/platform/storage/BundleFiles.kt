package com.makeeb.platform.storage

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toLong
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import platform.Foundation.NSBundle
import platform.posix.MAP_PRIVATE
import platform.posix.O_RDONLY
import platform.posix.PROT_READ
import platform.posix.close
import platform.posix.fstat
import platform.posix.mmap
import platform.posix.munmap
import platform.posix.open
import platform.posix.stat

/**
 * Maps files from a bundle with POSIX `mmap`. In the keyboard extension [NSBundle.mainBundle] is
 * the `.appex`, which the extension can always read, with or without Full Access.
 *
 * Mapped pages are clean and file-backed: iOS can evict them under pressure and doesn't count
 * them toward `phys_footprint`, the number jetsam uses (WWDC 2018 session 416).
 */
class BundleFiles(private val bundle: NSBundle = NSBundle.mainBundle) : BundledFiles {
    override fun map(name: String): ByteRegion? {
        val dot = name.lastIndexOf('.')
        val path = if (dot < 0) bundle.pathForResource(name, ofType = null)
        else bundle.pathForResource(name.substring(0, dot), ofType = name.substring(dot + 1))
        return path?.let(MmapByteRegion::map)
    }
}

/** A read-only `mmap` of a whole file. Reads are bounds-checked: a bad offset throws instead of faulting. */
@OptIn(ExperimentalForeignApi::class)
class MmapByteRegion private constructor(private val mapping: Mapping) : ByteRegion {
    private val base: CPointer<ByteVar> = mapping.base
    override val size: Int = mapping.size

    /** Readable bytes: [size] until [close], then 0. */
    private var limit = size

    /**
     * Unmaps a region nobody closed once it is unreachable: a downloaded pack the keyboard stopped
     * using. Only the GC decides that, so no reader can still be inside it.
     */
    @OptIn(ExperimentalNativeApi::class)
    @Suppress("unused")
    private val cleaner = createCleaner(mapping) { it.release() }

    override fun u8(offset: Int): Int {
        if (offset < 0 || offset >= limit) throw IndexOutOfBoundsException("offset $offset, size $limit")
        return base[offset].toInt() and 0xFF
    }

    override fun close() {
        if (limit == 0) return
        limit = 0
        mapping.release()
    }

    /** The mapping itself, apart from the region so the cleaner can hold it without the region. */
    private class Mapping(val base: CPointer<ByteVar>, val size: Int) {
        private var mapped = true

        fun release() {
            if (!mapped) return
            mapped = false
            munmap(base, size.convert())
        }
    }

    companion object {
        /** Maps the file at [path], or returns null if it is missing, empty, over 2 GB or unmappable. */
        fun map(path: String): MmapByteRegion? {
            val fd = open(path, O_RDONLY)
            if (fd < 0) return null
            try {
                val length = memScoped {
                    val info = alloc<stat>()
                    if (fstat(fd, info.ptr) != 0) return null
                    info.st_size
                }
                if (length <= 0 || length > Int.MAX_VALUE) return null
                val address = mmap(null, length.convert(), PROT_READ, MAP_PRIVATE, fd, 0)
                // MAP_FAILED is ((void *)-1).
                if (address == null || address.toLong() == -1L) return null
                return MmapByteRegion(Mapping(address.reinterpret(), length.toInt()))
            } finally {
                // The mapping holds its own reference to the file.
                close(fd)
            }
        }
    }
}
