package com.makeeb.shared.companion

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberSensitiveCopy(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val clip = ClipData.newPlainText("MaKeeb sample", text)
            // What password managers set; the constant is API 33, the key works before it too.
            val key = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
            clip.description.extras = PersistableBundle().apply { putBoolean(key, true) }
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
    }
}
