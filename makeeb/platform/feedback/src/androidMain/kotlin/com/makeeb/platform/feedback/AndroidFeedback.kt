package com.makeeb.platform.feedback

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptics through the keyboard's own view: `KEYBOARD_TAP` follows the system's touch-feedback
 * setting and needs no VIBRATE permission.
 */
class ViewHapticFeedback(private val view: () -> View?) : HapticFeedback {
    override fun keyPress(type: KeyFeedbackType) {
        view()?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

/** The system keyboard click sounds, at the user's touch-sound volume. */
class AudioManagerSoundFeedback(context: Context) : SoundFeedback {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun keyPress(type: KeyFeedbackType) {
        val effect = when (type) {
            KeyFeedbackType.Delete -> AudioManager.FX_KEYPRESS_DELETE
            KeyFeedbackType.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyFeedbackType.Return -> AudioManager.FX_KEYPRESS_RETURN
            KeyFeedbackType.Standard, KeyFeedbackType.Modifier -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audioManager.playSoundEffect(effect, -1f)
    }
}
