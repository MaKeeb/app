package com.makeeb.platform.feedback

import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * Taptic feedback. Keyboard extensions only get haptics with Full Access, so the composition
 * root installs this only when `hasFullAccess` is true.
 */
class ImpactHapticFeedback : HapticFeedback {
    private val light = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)

    override fun keyPress(type: KeyFeedbackType) {
        when (type) {
            KeyFeedbackType.Standard, KeyFeedbackType.Space -> light.impactOccurred()
            KeyFeedbackType.Delete, KeyFeedbackType.Return, KeyFeedbackType.Modifier -> medium.impactOccurred()
        }
    }
}

/**
 * The system input click. It only plays when the extension's input view adopts
 * `UIInputViewAudioFeedback` with `enableInputClicksWhenVisible` (done in the Swift shell), and
 * it follows the user's Keyboard Clicks setting.
 */
class InputClickSoundFeedback : SoundFeedback {
    override fun keyPress(type: KeyFeedbackType) {
        UIDevice.currentDevice.playInputClick()
    }
}
