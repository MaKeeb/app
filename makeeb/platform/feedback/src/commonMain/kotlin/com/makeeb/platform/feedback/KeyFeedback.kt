package com.makeeb.platform.feedback

/** Which kind of key was pressed; platforms pick a matching click sound or haptic. */
enum class KeyFeedbackType { Standard, Delete, Space, Return, Modifier }

fun interface HapticFeedback {
    /** [intensity] 0..1 from the user's vibration-strength setting. */
    fun keyPress(type: KeyFeedbackType, intensity: Float)

    /** The keyboard is about to be typed on: iOS wakes the Taptic Engine so the first tap isn't late. */
    fun prepare() {}

    /**
     * A light tick as a slide moves a selection one step: the next long-press alternate, the
     * cursor on the space bar, another character from a held delete.
     */
    fun selectionTick(intensity: Float) {}
}

fun interface SoundFeedback {
    /** [volume] 0..1 from the user's click-volume setting, where the platform allows one. */
    fun keyPress(type: KeyFeedbackType, volume: Float)
}

/** For previews, tests, and platforms or modes where feedback is unavailable. */
object NoFeedback : HapticFeedback, SoundFeedback {
    override fun keyPress(type: KeyFeedbackType, intensity: Float) = Unit
}
