//
//  RunResult.kt
//  Starshower Run
//

package com.starshower.run.core.engine

import kotlinx.serialization.Serializable

/**
 * Everything that happened in a finished run. Feeds stats, achievements,
 * missions, leaderboards and rewards.
 */
@Serializable
data class RunResult(
    val mode: GameMode,
    val difficulty: Difficulty,
    val modifier: RunModifier,
    val dailyKey: String?,
    val score: Int,
    val duration: Double,
    val stars: Int,
    val nearMisses: Int,
    val perfectMisses: Int,
    val dodged: Int,
    val powerUps: Int,
    val hits: Int,
    val maxCombo: Int,
    val maxMultiplier: Int,
    val bestNovaClear: Int,
    val levelReached: Int,
    /** Epoch milliseconds. */
    val date: Long,
) {
    val survivedSeconds: Int get() = duration.toInt()
}
