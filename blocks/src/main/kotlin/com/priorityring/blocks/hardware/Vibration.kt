package com.priorityring.blocks.hardware

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over the Vibrator API differences between Android versions.
 *
 * We tag vibrations as *alarm* usage: Android may drop vibrations from background apps unless they
 * are alarms/ringtones, and alarm-tagged vibration also respects the user's alarm vibration setting.
 */
@Singleton
class Vibration @Inject constructor(@ApplicationContext context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }

    val isAvailable: Boolean get() = vibrator?.hasVibrator() == true

    /**
     * @param timings alternating off/on durations in ms (first value is the initial delay).
     * @param repeat loop until [stop] is called.
     */
    fun start(timings: List<Long>, repeat: Boolean): Boolean {
        val v = vibrator ?: return false
        if (!v.hasVibrator() || timings.isEmpty()) return false
        val effect = VibrationEffect.createWaveform(timings.toLongArray(), if (repeat) 0 else -1)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        return true
    }

    fun stop() {
        vibrator?.cancel()
    }
}
