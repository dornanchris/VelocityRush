//
//  GameSettings.kt
//  Starshower Run
//
//  Preference keys shared by the settings UI and the game, plus a snapshot
//  of the settings the game renderer cares about.
//

package com.starshower.run.core.progression

import com.starshower.run.core.engine.Difficulty

object SettingsKey {
    const val SOUND_ENABLED = "soundEnabled"
    const val MUSIC_ENABLED = "musicEnabled"
    const val HAPTICS_ENABLED = "hapticEnabled"
    const val SHOW_FPS = "showFPS"
    const val DIFFICULTY = "difficulty"
    const val SENSITIVITY = "controlSensitivity"
    const val COLOR_BLIND_MODE = "colorBlindMode"
    const val HIGH_CONTRAST = "highContrast"
    const val REDUCED_MOTION = "reducedMotion"
    const val NOTIFICATIONS = "notifications"
    const val SELECTED_MODE = "selectedMode"
}

/** Snapshot of the settings the game scene cares about. */
data class GameSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val showFPS: Boolean = false,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val sensitivity: Double = 1.2,
    val colorBlindMode: Boolean = false,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val notifications: Boolean = false,
)
