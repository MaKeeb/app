package com.makeeb.platform.feedback

import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

/**
 * Taptic feedback. Keyboard extensions only get haptics with Full Access, so the composition
 * root installs this only when `hasFullAccess` is true.
 */
class ImpactHapticFeedback : HapticFeedback {
    private val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)

    /** One generator at the user's strength; delete, return and function keys a little firmer. */
    override fun keyPress(type: KeyFeedbackType, intensity: Float) {
        if (intensity <= 0f) return
        val firmer = type == KeyFeedbackType.Delete || type == KeyFeedbackType.Return || type == KeyFeedbackType.Modifier
        generator.impactOccurredWithIntensity((if (firmer) intensity * 1.25f else intensity).coerceIn(0f, 1f).toDouble())
    }
}

/**
 * The system input click. It only plays when the extension's input view adopts
 * `UIInputViewAudioFeedback` with `enableInputClicksWhenVisible` (done in the Swift shell), and
 * it follows the user's Keyboard Clicks setting.
 */
class InputClickSoundFeedback : SoundFeedback {
    /** iOS offers no click volume; the silent switch and system volume govern it. */
    override fun keyPress(type: KeyFeedbackType, volume: Float) {
        if (volume > 0f) UIDevice.currentDevice.playInputClick()
    }
}
