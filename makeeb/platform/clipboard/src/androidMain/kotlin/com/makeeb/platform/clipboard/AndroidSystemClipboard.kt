package com.makeeb.platform.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android 10+ only lets the focused app or the default IME read the clipboard, which is what the
 * keyboard is while it is showing.
 */
class AndroidSystemClipboard(context: Context) : SystemClipboard {
    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val appContext = context.applicationContext

    override fun read(): Clip? {
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(appContext)?.toString()
        if (text.isNullOrEmpty()) return null
        return Clip(text = text, isSensitive = clip.description.isSensitive())
    }

    override fun write(text: String) {
        manager.setPrimaryClip(ClipData.newPlainText("MaKeeb", text))
    }

    override val changes: Flow<Clip> = callbackFlow {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            read()?.let { trySend(it) }
        }
        manager.addPrimaryClipChangedListener(listener)
        awaitClose { manager.removePrimaryClipChangedListener(listener) }
    }
}

private fun ClipDescription.isSensitive(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
