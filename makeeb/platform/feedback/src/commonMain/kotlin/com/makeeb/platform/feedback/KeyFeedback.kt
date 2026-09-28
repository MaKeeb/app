package com.makeeb.platform.feedback

/** Which kind of key was pressed; platforms pick a matching click sound or haptic. */
enum class KeyFeedbackType { Standard, Delete, Space, Return, Modifier }

fun interface HapticFeedback {
    fun keyPress(type: KeyFeedbackType)
}

fun interface SoundFeedback {
    fun keyPress(type: KeyFeedbackType)
}

/** For previews, tests, and platforms or modes where feedback is unavailable. */
object NoFeedback : HapticFeedback, SoundFeedback {
    override fun keyPress(type: KeyFeedbackType) = Unit
}
