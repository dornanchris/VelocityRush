//
//  HapticsManager.kt
//  Starshower Run
//
//  Maps the iOS feedback styles onto Android vibration effects.
//

package com.starshower.run.services

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class Haptic {
    LIGHT, MEDIUM, HEAVY, RIGID, SOFT,
    SUCCESS, WARNING, ERROR,
    SELECTION,
}

class HapticsManager(context: Context, private val preferences: Preferences) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    val isEnabled: Boolean get() = preferences.hapticsEnabled

    fun play(haptic: Haptic) {
        val vibrator = vibrator ?: return
        if (!isEnabled || !vibrator.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) predefined(haptic) else legacy(haptic)
        runCatching { vibrator.vibrate(effect) }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun predefined(haptic: Haptic): VibrationEffect = when (haptic) {
        Haptic.SELECTION, Haptic.SOFT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        Haptic.LIGHT -> VibrationEffect.createOneShot(12, 90)
        Haptic.MEDIUM, Haptic.RIGID -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        Haptic.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        Haptic.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 18, 70, 28), intArrayOf(0, 140, 0, 220), -1)
        Haptic.WARNING -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        Haptic.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40, 50, 60), intArrayOf(0, 255, 0, 200, 0, 255), -1)
    }

    private fun legacy(haptic: Haptic): VibrationEffect = when (haptic) {
        Haptic.SELECTION, Haptic.SOFT, Haptic.LIGHT -> VibrationEffect.createOneShot(10, 80)
        Haptic.MEDIUM, Haptic.RIGID -> VibrationEffect.createOneShot(18, 160)
        Haptic.HEAVY -> VibrationEffect.createOneShot(30, 255)
        Haptic.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 18, 70, 28), intArrayOf(0, 140, 0, 220), -1)
        Haptic.WARNING -> VibrationEffect.createWaveform(longArrayOf(0, 20, 60, 20), intArrayOf(0, 200, 0, 200), -1)
        Haptic.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40, 50, 60), intArrayOf(0, 255, 0, 200, 0, 255), -1)
    }
}
