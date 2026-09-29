package com.makeeb.platform.storage

import android.content.Context
import android.os.UserManager
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * [PrivateFiles] in credential-encrypted storage, the app's `noBackupFilesDir`: readable only
 * once the user has unlocked the device, and left out of backups (the manifest also turns backup
 * off). Learned words are typed text, so they don't belong in device-protected storage.
 *
 * The keyboard is direct-boot aware and may run before the first unlock. Then credential-encrypted
 * files can't be opened, and a missing file looks the same as a locked one, so every call checks
 * [UserManager.isUserUnlocked] first and throws [PrivateFilesUnavailableException] while locked.
 */
class CredentialProtectedFiles(context: Context, private val directoryName: String = DIRECTORY) : PrivateFiles {
    private val context = context.applicationContext
    private val userManager = context.getSystemService(UserManager::class.java)

    /** Resolved on first use, after the unlock check: touching it earlier logs errors in direct boot. */
    private val directory by lazy { File(this.context.noBackupFilesDir, directoryName) }

    override fun read(name: String): ByteArray? {
        requireUnlocked()
        return try {
            AtomicFile(File(directory, name)).readFully()
        } catch (_: FileNotFoundException) {
            null
        } catch (e: IOException) {
            throw PrivateFilesUnavailableException("read failed", e)
        }
    }

    override fun write(name: String, bytes: ByteArray) {
        requireUnlocked()
        if (!directory.isDirectory && !directory.mkdirs()) throw PrivateFilesUnavailableException("no directory")
        val file = AtomicFile(File(directory, name))
        val out = try {
            file.startWrite()
        } catch (e: IOException) {
            throw PrivateFilesUnavailableException("write failed", e)
        }
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw PrivateFilesUnavailableException("write failed", e)
        }
    }

    override fun delete(name: String) {
        requireUnlocked()
        AtomicFile(File(directory, name)).delete()
    }

    private fun requireUnlocked() {
        if (userManager?.isUserUnlocked == false) throw PrivateFilesUnavailableException("locked until the first unlock")
    }

    private companion object {
        const val DIRECTORY = "keyboard"
    }
}
