package com.makeeb.testing

import com.makeeb.platform.host.KeyboardHost

class FakeKeyboardHost(override val needsInputMethodSwitchKey: Boolean = true) : KeyboardHost {
    var switchCount = 0
        private set
    var pickerShown = false
        private set
    var hidden = false
        private set

    override fun switchToNextInputMethod() {
        switchCount++
    }

    override fun showInputMethodPicker() {
        pickerShown = true
    }

    override fun hideKeyboard() {
        hidden = true
    }

    override val canOpenSettings: Boolean = true
    var settingsOpened = false
        private set

    override fun openSettings() {
        settingsOpened = true
    }
}
