//
//  Preferences.kt
//  Starshower Run
//
//  SharedPreferences-backed settings exposed as Compose state, the Android
//  counterpart of the iOS @AppStorage values. Every toggle here is read by
//  the game.
//

package com.starshower.run.services

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starshower.run.core.engine.Difficulty
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.progression.GameSettings
import com.starshower.run.core.progression.KeyValueStore
import com.starshower.run.core.progression.SettingsKey

class Preferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Raw string storage for the profile repository. */
    val keyValueStore: KeyValueStore = object : KeyValueStore {
        override fun getString(key: String): String? = prefs.getString(key, null)
        override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        override fun remove(key: String) { prefs.edit().remove(key).apply() }
    }

    private fun bool(key: String, default: Boolean) = BoolPref(key, default)

    inner class BoolPref(private val key: String, default: Boolean) {
        private val state = mutableStateOf(prefs.getBoolean(key, default))
        var value: Boolean
            get() = state.value
            set(newValue) {
                state.value = newValue
                prefs.edit().putBoolean(key, newValue).apply()
            }
    }

    private val soundPref = bool(SettingsKey.SOUND_ENABLED, true)
    private val musicPref = bool(SettingsKey.MUSIC_ENABLED, true)
    private val hapticsPref = bool(SettingsKey.HAPTICS_ENABLED, true)
    private val fpsPref = bool(SettingsKey.SHOW_FPS, false)
    private val colorBlindPref = bool(SettingsKey.COLOR_BLIND_MODE, false)
    private val highContrastPref = bool(SettingsKey.HIGH_CONTRAST, false)
    private val reducedMotionPref = bool(SettingsKey.REDUCED_MOTION, false)
    private val notificationsPref = bool(SettingsKey.NOTIFICATIONS, false)

    var soundEnabled: Boolean
        get() = soundPref.value
        set(value) { soundPref.value = value }
    var musicEnabled: Boolean
        get() = musicPref.value
        set(value) { musicPref.value = value }
    var hapticsEnabled: Boolean
        get() = hapticsPref.value
        set(value) { hapticsPref.value = value }
    var showFPS: Boolean
        get() = fpsPref.value
        set(value) { fpsPref.value = value }
    var colorBlindMode: Boolean
        get() = colorBlindPref.value
        set(value) { colorBlindPref.value = value }
    var highContrast: Boolean
        get() = highContrastPref.value
        set(value) { highContrastPref.value = value }
    var reducedMotion: Boolean
        get() = reducedMotionPref.value
        set(value) { reducedMotionPref.value = value }
    var notificationsEnabled: Boolean
        get() = notificationsPref.value
        set(value) { notificationsPref.value = value }

    private var difficultyState by mutableStateOf(
        Difficulty.fromRaw(prefs.getInt(SettingsKey.DIFFICULTY, Difficulty.NORMAL.rawValue)) ?: Difficulty.NORMAL,
    )
    var difficulty: Difficulty
        get() = difficultyState
        set(value) {
            difficultyState = value
            prefs.edit().putInt(SettingsKey.DIFFICULTY, value.rawValue).apply()
        }

    private var sensitivityState by mutableStateOf(prefs.getFloat(SettingsKey.SENSITIVITY, 1.2f).toDouble())
    var sensitivity: Double
        get() = sensitivityState
        set(value) {
            sensitivityState = value
            prefs.edit().putFloat(SettingsKey.SENSITIVITY, value.toFloat()).apply()
        }

    private var selectedModeState by mutableStateOf(
        GameMode.fromRaw(prefs.getString(SettingsKey.SELECTED_MODE, null)) ?: GameMode.ENDLESS,
    )
    var selectedMode: GameMode
        get() = selectedModeState
        set(value) {
            selectedModeState = value
            prefs.edit().putString(SettingsKey.SELECTED_MODE, value.rawValue).apply()
        }

    /** Snapshot of the settings the game scene cares about. */
    fun gameSettings(): GameSettings = GameSettings(
        soundEnabled = soundEnabled,
        musicEnabled = musicEnabled,
        hapticsEnabled = hapticsEnabled,
        showFPS = showFPS,
        difficulty = difficulty,
        sensitivity = sensitivity,
        colorBlindMode = colorBlindMode,
        highContrast = highContrast,
        reducedMotion = reducedMotion,
        notifications = notificationsEnabled,
    )

    companion object {
        const val FILE_NAME = "starshower"
    }
}
