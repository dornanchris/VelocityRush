//
//  GameSession.kt
//  Starshower Run
//
//  Bridges the game scene and the Compose HUD / menus for one play session
//  (which may contain several runs via "Play Again").
//

package com.starshower.run.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starshower.run.AppGraph
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.engine.Difficulty
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.PickupKind
import com.starshower.run.core.engine.RunConfig
import com.starshower.run.core.engine.RunResult
import com.starshower.run.core.progression.GameSettings
import com.starshower.run.core.progression.RunRewards
import com.starshower.run.services.Haptic

data class ActivePowerUpDisplay(val kind: PickupKind, val fraction: Double)

data class HUDState(
    val score: Int = 0,
    val multiplier: Int = 1,
    val comboProgress: Double = 0.0,
    val elapsed: Int = 0,
    /** Remaining Time Attack clock in tenths of a second. */
    val clockTenths: Int = 600,
    val level: Int = 1,
    val hasShield: Boolean = false,
    val powerUps: List<ActivePowerUpDisplay> = emptyList(),
    val isCountdown: Boolean = true,
)

data class RunSummary(val config: RunConfig, val result: RunResult, val rewards: RunRewards)

class GameSession(initialConfig: RunConfig, private val graph: AppGraph) {
    enum class Phase { PLAYING, PAUSED, FINISHED }

    var phase by mutableStateOf(Phase.PLAYING)
        private set
    var summary by mutableStateOf<RunSummary?>(null)
        private set
    /** Only the HUD reads this, so 60 Hz updates don't recompose the rest of the screen. */
    var hud by mutableStateOf(HUDState())
        private set
    var config by mutableStateOf(initialConfig)
        private set
    var scene: GameScene? by mutableStateOf(null)
        private set

    val settings: GameSettings = graph.preferences.gameSettings().let {
        if (initialConfig.mode == GameMode.DAILY) it.copy(difficulty = Difficulty.NORMAL) else it
    }
    val showsTutorialHint: Boolean = graph.store.profile.stats.gamesPlayed < 3
    val personalBest: Int = graph.store.best(initialConfig.mode)

    fun makeScene(widthPx: Float, heightPx: Float, density: Float) {
        val existing = scene
        if (existing != null) {
            existing.resize(widthPx, heightPx)
            return
        }
        scene = GameScene(
            widthPx = maxOf(widthPx, 1f), heightPx = maxOf(heightPx, 1f), density = density,
            config = config, session = this, settings = settings,
            profile = graph.store.profile, sound = graph.sound, haptics = graph.haptics,
        )
        graph.sound.startMusic()
    }

    // MARK: Scene callbacks

    fun updateHUD(newValue: HUDState) {
        if (hud != newValue) hud = newValue
    }

    fun runFinished(result: RunResult) {
        if (phase == Phase.FINISHED) return
        graph.sound.stopMusic()
        val rewards = graph.store.record(result)
        summary = RunSummary(config, result, rewards)
        phase = Phase.FINISHED
        if (rewards.isNewBest || rewards.newAchievements.isNotEmpty()) {
            graph.sound.play(SoundEffect.UNLOCK)
            graph.haptics.play(Haptic.SUCCESS)
        }
    }

    // MARK: Controls

    fun pause() {
        if (phase != Phase.PLAYING || scene == null) return
        scene?.setRunPaused(true)
        graph.sound.pauseMusic()
        phase = Phase.PAUSED
    }

    fun resume() {
        if (phase != Phase.PAUSED) return
        phase = Phase.PLAYING
        scene?.setRunPaused(false)
        graph.sound.startMusic()
    }

    fun restart() {
        val next = config.replay()
        config = next
        summary = null
        hud = HUDState()
        phase = Phase.PLAYING
        scene?.startRun(next, graph.store.profile)
        graph.sound.stopMusic()
        graph.sound.startMusic()
    }

    /** Zen has no game over – this banks the session's stars. */
    fun finishZenSession() {
        if (config.mode != GameMode.ZEN) return
        phase = Phase.PLAYING
        scene?.setRunPaused(false)
        scene?.endRunEarly()
    }

    fun teardown() {
        graph.sound.stopMusic()
        scene?.setRunPaused(true)
    }
}
