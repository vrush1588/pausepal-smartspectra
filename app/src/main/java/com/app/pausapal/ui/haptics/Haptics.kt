package com.app.pausapal.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Optional, gentle haptics. Every call checks [enabled] first, so nothing ever vibrates unless the
 * user turned on "Gentle vibration" (off by default). Short, low-amplitude one-shot pulses only.
 */
class Haptics(context: Context, private val enabled: () -> Boolean) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** One soft pulse: the break sheet appeared. */
    fun soft() = play(durationMs = 60, amplitude = 60)

    /** Very light tick: start of a guided inhale. */
    fun tick() = play(durationMs = 15, amplitude = 35)

    private fun play(durationMs: Long, amplitude: Int) {
        if (!enabled()) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val amp = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        runCatching { v.vibrate(VibrationEffect.createOneShot(durationMs, amp)) }
    }
}
