package com.makeeb.platform.storage

import android.content.res.AssetManager
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Maps APK assets in place, the way AOSP LatinIME maps its dictionaries: an asset stored
 * uncompressed is a byte range of the APK file, so `openFd` hands out the APK's descriptor with
 * that range, and the range is mapped read-only. Nothing is copied or extracted, the mapping lives
 * outside the Java heap, and assets are readable in direct boot.
 *
 * A compressed asset has no file range: `openFd` throws and [map] returns null. Pack extensions
 * must be listed in `androidResources.noCompress` (see app/android/build.gradle.kts).
 */
class AssetBundledFiles(private val assets: AssetManager) : BundledFiles {
    override fun map(name: String): ByteRegion? = try {
        assets.openFd(name).use { descriptor ->
            // A stream over the raw descriptor sees the whole APK, so the channel's positions are
            // absolute; closing the descriptor afterwards leaves the mapping valid.
            val channel = FileInputStream(descriptor.fileDescriptor).channel
            MappedByteBufferRegion(channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.length))
        }
    } catch (_: IOException) {
        null
    }
}

/** A [ByteRegion] over a (usually memory-mapped) [ByteBuffer]; absolute reads only, so thread-safe. */
class MappedByteBufferRegion(buffer: ByteBuffer) : ByteRegion {
    private val buffer: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    override val size: Int = this.buffer.limit()

    override fun u8(offset: Int): Int = buffer.get(offset).toInt() and 0xFF

    override fun u16(offset: Int): Int = buffer.getShort(offset).toInt() and 0xFFFF

    override fun i32(offset: Int): Int = buffer.getInt(offset)
}
