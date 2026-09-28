package com.makeeb.platform.host

import platform.UIKit.UIInputViewController

/** [KeyboardHost] backed by the extension's `UIInputViewController`. */
class InputViewControllerKeyboardHost(private val controller: UIInputViewController) : KeyboardHost {
    override val needsInputMethodSwitchKey: Boolean
        get() = controller.needsInputModeSwitchKey

    override fun switchToNextInputMethod() {
        controller.advanceToNextInputMode()
    }

    /**
     * iOS shows its keyboard list via `handleInputModeList(from:with:)`, which needs the touch
     * event of a UIKit view; the Swift shell wires that to the globe key's long press.
     */
    override fun showInputMethodPicker() = Unit

    override fun hideKeyboard() {
        controller.dismissKeyboard()
    }

    /** App Review 4.4.1: a keyboard may not launch other apps, its own container app included. */
    override val canOpenSettings: Boolean = false

    override fun openSettings() = Unit
}
