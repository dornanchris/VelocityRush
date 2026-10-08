//
//  StarshowerApplication.kt
//  Starshower Run
//
//  Builds the app-wide services once (the Android counterpart of the iOS
//  `.shared` singletons) and hands them to Compose through `LocalAppGraph`.
//

package com.starshower.run

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.starshower.run.core.progression.ProfileRepository
import com.starshower.run.services.GlobalGames
import com.starshower.run.services.HapticsManager
import com.starshower.run.services.OfflineGlobalGames
import com.starshower.run.services.Preferences
import com.starshower.run.services.ProgressStore
import com.starshower.run.services.SoundManager
import com.starshower.run.services.ToastCenter

class AppGraph(application: Application) {
    val preferences = Preferences(application)
    val sound = SoundManager(application, preferences)
    val haptics = HapticsManager(application, preferences)
    val toasts = ToastCenter(sound, haptics)
    val globalGames: GlobalGames = OfflineGlobalGames
    val store = ProgressStore(ProfileRepository(preferences.keyValueStore), toasts, globalGames)

    /** False while the activity is in the background (pauses runs, refreshes dailies on return). */
    var isForeground by mutableStateOf(true)
}

/** Things only the Activity can do, so UI code stays free of Android plumbing. */
interface Platform {
    /** e.g. "1.0 (1)" */
    val appVersion: String
    /** Turns the 6pm reminder on or off, asking for permission if needed. Reports whether it ended up on. */
    fun setDailyReminder(enabled: Boolean, onResult: (Boolean) -> Unit)
    fun share(text: String)
    /** Hides the system bars and keeps the screen awake during a run. */
    fun setImmersive(immersive: Boolean)
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph not provided") }
val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform not provided") }

class StarshowerApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.sound.prepare()
    }
}
