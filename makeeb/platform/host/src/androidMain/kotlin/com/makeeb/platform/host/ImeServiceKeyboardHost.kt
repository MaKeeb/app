package com.makeeb.platform.host

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.inputmethod.InputMethodManager

/** [KeyboardHost] backed by the running [InputMethodService]. */
class ImeServiceKeyboardHost(private val service: InputMethodService) : KeyboardHost {
    private val inputMethodManager: InputMethodManager
        get() = service.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    /**
     * Android 10+ draws its own keyboard switcher (and a hide button) in the navigation bar under
     * the IME, so a globe key would duplicate it. Older versions get the key when switching is
     * possible.
     */
    override val needsInputMethodSwitchKey: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            false
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.shouldOfferSwitchingToNextInputMethod()
        } else {
            val token = service.window?.window?.attributes?.token
            @Suppress("DEPRECATION")
            token != null && inputMethodManager.shouldOfferSwitchingToNextInputMethod(token)
        }

    override fun switchToNextInputMethod() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.switchToNextInputMethod(false)
        } else {
            val token = service.window?.window?.attributes?.token ?: return
            @Suppress("DEPRECATION")
            inputMethodManager.switchToNextInputMethod(token, false)
        }
    }

    override fun showInputMethodPicker() {
        inputMethodManager.showInputMethodPicker()
    }

    override fun hideKeyboard() {
        service.requestHideSelf(0)
    }

    override val canOpenSettings: Boolean = true

    /** Opens the companion app on its Settings tab, reusing the app's task if it is open. */
    override fun openSettings() {
        val intent = service.packageManager.getLaunchIntentForPackage(service.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        service.startActivity(intent.putExtra(EXTRA_OPEN_SETTINGS, true))
        service.requestHideSelf(0)
    }

    companion object {
        /** Boolean extra on the companion's launch intent: show the Settings tab. */
        const val EXTRA_OPEN_SETTINGS = "com.makeeb.extra.OPEN_SETTINGS"
    }
}
