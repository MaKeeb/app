package com.makeeb.shared.companion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPasteboard

@Composable
internal actual fun rememberSensitiveCopy(): (String) -> Unit = remember {
    { text ->
        // The nspasteboard.org marker password managers put next to secrets they copy.
        UIPasteboard.generalPasteboard.setItems(
            listOf(mapOf("public.utf8-plain-text" to text, "org.nspasteboard.ConcealedType" to text)),
            options = emptyMap<Any?, Any>(),
        )
    }
}
