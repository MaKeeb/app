package com.makeeb.platform.feedback

import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * Taptic feedback. Keyboard extensions only get haptics with Full Access, so the composition
 * root installs this only when `hasFullAccess` is true.
 */
class ImpactHapticFeedback : HapticFeedback {
    /** A short, crisp tap for letters and space, like the system keyboard's. */
    private val tap = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)

    /** A harder-edged tap for delete, return and function keys. */
    private val firm = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val selection = UISelectionFeedbackGenerator()

    /**
     * Wakes the Taptic Engine, which otherwise sleeps and makes the first tap late. It stays ready
     * for a few seconds, so every tap prepares the next.
     */
    override fun prepare() {
        tap.prepare()
        firm.prepare()
        selection.prepare()
    }

    override fun keyPress(type: KeyFeedbackType, intensity: Float) {
        if (intensity <= 0f) return
        val firmer = type == KeyFeedbackType.Delete || type == KeyFeedbackType.Return || type == KeyFeedbackType.Modifier
        val generator = if (firmer) firm else tap
        generator.impactOccurredWithIntensity(intensity.coerceIn(0f, 1f).toDouble())
        generator.prepare()
    }

    /** The system's selection click (picker wheels, sliders); it has no strength, only on or off. */
    override fun selectionTick(intensity: Float) {
        if (intensity <= 0f) return
        selection.selectionChanged()
        selection.prepare()
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
