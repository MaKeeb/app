package com.makeeb.platform.feedback

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Key vibration at the user's strength. Uses amplitude where the motor supports it, otherwise the
 * nearest predefined click. Tagged as touch feedback on Android 13+, so the system's touch-feedback
 * setting still silences it; on older versions that setting is checked here.
 */
class VibratorHapticFeedback(private val context: Context) : HapticFeedback {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    override fun keyPress(type: KeyFeedbackType, intensity: Float) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (intensity <= 0f) return
        // Delete and return read as heavier keys, as on the system keyboards.
        val strength = (if (type == KeyFeedbackType.Delete || type == KeyFeedbackType.Return) intensity * 1.25f else intensity).coerceAtMost(1f)
        val effect = when {
            vibrator.hasAmplitudeControl() -> VibrationEffect.createOneShot(DURATION_MS, (strength * 255).toInt().coerceIn(1, 255))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(
                when {
                    strength < 0.34f -> VibrationEffect.EFFECT_TICK
                    strength < 0.75f -> VibrationEffect.EFFECT_CLICK
                    else -> VibrationEffect.EFFECT_HEAVY_CLICK
                },
            )
            else -> VibrationEffect.createOneShot(DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else if (systemTouchFeedbackOn()) {
            vibrator.vibrate(effect)
        }
    }

    private fun systemTouchFeedbackOn(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private companion object {
        const val DURATION_MS = 18L
    }
}

/**
 * The system keyboard click sounds at the user's volume. Silent while the phone is on silent or
 * vibrate, or in Do Not Disturb.
 */
class AudioManagerSoundFeedback(context: Context) : SoundFeedback {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    override fun keyPress(type: KeyFeedbackType, volume: Float) {
        if (volume <= 0f || audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val filter = notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
        val effect = when (type) {
            KeyFeedbackType.Delete -> AudioManager.FX_KEYPRESS_DELETE
            KeyFeedbackType.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyFeedbackType.Return -> AudioManager.FX_KEYPRESS_RETURN
            KeyFeedbackType.Standard, KeyFeedbackType.Modifier -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audioManager.playSoundEffect(effect, volume.coerceIn(0f, 1f))
    }
}
