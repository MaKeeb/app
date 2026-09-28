package com.makeeb.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.makeeb.platform.host.ImeServiceKeyboardHost
import com.makeeb.shared.companion.CompanionApp

/** Launcher activity and the IME's settings activity (see `method.xml`). */
class MainActivity : ComponentActivity() {
    /** Bumped for each "All settings" tap in the keyboard's quick settings. */
    private val settingsRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent { CompanionApp(settingsRequest = settingsRequests.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(ImeServiceKeyboardHost.EXTRA_OPEN_SETTINGS, false)) settingsRequests.intValue++
    }
}
