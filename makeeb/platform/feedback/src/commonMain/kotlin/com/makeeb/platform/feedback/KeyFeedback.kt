package com.makeeb.platform.feedback

/** Which kind of key was pressed; platforms pick a matching click sound or haptic. */
enum class KeyFeedbackType { Standard, Delete, Space, Return, Modifier }

fun interface HapticFeedback {
    /** [intensity] 0..1 from the user's vibration-strength setting. */
    fun keyPress(type: KeyFeedbackType, intensity: Float)
}

fun interface SoundFeedback {
    /** [volume] 0..1 from the user's click-volume setting, where the platform allows one. */
    fun keyPress(type: KeyFeedbackType, volume: Float)
}

/** For previews, tests, and platforms or modes where feedback is unavailable. */
object NoFeedback : HapticFeedback, SoundFeedback {
    override fun keyPress(type: KeyFeedbackType, intensity: Float) = Unit
}
