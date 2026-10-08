//
//  PlayerProfile.kt
//  Starshower Run
//
//  All persistent player data in one serializable value.
//

package com.starshower.run.core.progression

import com.starshower.run.core.engine.Difficulty
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.RunModifier
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.math.pow

@Serializable
data class PlayerStats(
    // Lifetime totals
    var gamesPlayed: Int = 0,
    var endlessGames: Int = 0,
    var timeAttackGames: Int = 0,
    var dailyGames: Int = 0,
    var zenGames: Int = 0,
    var totalTimeSurvived: Int = 0,
    var totalStars: Int = 0,
    var totalNearMisses: Int = 0,
    var totalPerfectMisses: Int = 0,
    var totalDodged: Int = 0,
    var totalPowerUps: Int = 0,
    var totalCoinsEarned: Int = 0,
    var missionsCompleted: Int = 0,
    var dailyRunsCompleted: Int = 0,
    var cosmeticsPurchased: Int = 0,

    // Personal bests
    var bestEndlessScore: Int = 0,
    var bestEndlessTime: Int = 0,
    var bestTimeAttackScore: Int = 0,
    var bestDailyScore: Int = 0,
    var bestStarsInRun: Int = 0,
    var bestNearMissesInRun: Int = 0,
    var bestMultiplier: Int = 1,
    var bestCombo: Int = 0,
    var bestNovaClear: Int = 0,
    var bestPuristTime: Int = 0,
    var bestExpertTime: Int = 0,
    var bestLevel: Int = 1,

    // Streaks
    var currentSurvivalStreak: Int = 0,
    var longestSurvivalStreak: Int = 0,
    var loginStreak: Int = 0,
    var longestLoginStreak: Int = 0,
    /** Days on which every daily mission was cleared. */
    var allClearDays: Int = 0,
    var allClearStreak: Int = 0,
    var longestAllClearStreak: Int = 0,
) {
    /** Number of distinct modes played (for the Explorer achievement). */
    val modesPlayed: Int
        get() = listOf(endlessGames, timeAttackGames, dailyGames, zenGames).count { it > 0 }

    fun best(mode: GameMode): Int = when (mode) {
        GameMode.ENDLESS -> bestEndlessScore
        GameMode.TIME_ATTACK -> bestTimeAttackScore
        GameMode.DAILY -> bestDailyScore
        GameMode.ZEN -> 0
    }
}

@Serializable
data class LeaderboardEntry(
    val id: String = UUID.randomUUID().toString(),
    val score: Int,
    val duration: Double,
    /** Epoch milliseconds. */
    val date: Long,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val modifier: RunModifier = RunModifier.NONE,
)

@Serializable
data class DailyState(
    /** Day these missions belong to, e.g. "2026-09-29". */
    var dayKey: String = "",
    var missions: List<MissionProgress> = emptyList(),
    /** True once all three missions are claimed (the all-clear bonus is paid automatically). */
    var allMissionsBonusClaimed: Boolean = false,
    var dailyRunBest: Int = 0,
    var dailyRunAttempts: Int = 0,
    var dailyRunLeaderboard: List<LeaderboardEntry> = emptyList(),
    /** Last day the login reward was claimed. */
    var lastLoginRewardDay: String = "",
)

@Serializable
data class PlayerProfile(
    var version: Int = CURRENT_VERSION,
    var playerName: String = "Velocity Runner",
    var coins: Int = 0,
    var xp: Int = 0,
    var stats: PlayerStats = PlayerStats(),

    var unlockedCosmetics: Set<String> = Cosmetic.defaultUnlocked,
    var equippedSkin: String = Cosmetic.DEFAULT_SKIN,
    var equippedTrail: String = Cosmetic.DEFAULT_TRAIL,
    var equippedTheme: String = Cosmetic.DEFAULT_THEME,

    /** Achievement id → unlock time (epoch milliseconds). */
    var unlockedAchievements: Map<String, Long> = emptyMap(),

    var leaderboards: Map<String, List<LeaderboardEntry>> = emptyMap(),
    var daily: DailyState = DailyState(),
    /** Last day every mission was cleared (for the all-clear streak). */
    var lastAllClearDay: String = "",
) {
    val level: Int get() = Leveling.level(xp)

    fun isUnlocked(cosmetic: Cosmetic): Boolean = cosmetic.id in unlockedCosmetics

    fun localLeaderboard(mode: GameMode): List<LeaderboardEntry> =
        if (mode == GameMode.DAILY) daily.dailyRunLeaderboard else leaderboards[mode.rawValue].orEmpty()

    companion object {
        const val CURRENT_VERSION = 3
    }
}

// MARK: - Leveling

object Leveling {
    const val MAX_LEVEL = 99

    /** XP needed to go from [level] to `level + 1`. */
    fun xpToAdvance(level: Int): Int =
        100 + (level - 1) * 60 + ((level - 1).toDouble().pow(1.6) * 8).toInt()

    /** Total XP needed to reach [level]. */
    fun totalXP(level: Int): Int {
        if (level <= 1) return 0
        return (1 until level).sumOf { xpToAdvance(it) }
    }

    fun level(xp: Int): Int {
        var level = 1
        var remaining = xp
        while (level < MAX_LEVEL) {
            val needed = xpToAdvance(level)
            if (remaining < needed) break
            remaining -= needed
            level += 1
        }
        return level
    }

    /** Progress (0..1) through the current level. */
    fun progress(xp: Int): Double {
        val level = level(xp)
        if (level >= MAX_LEVEL) return 1.0
        val base = totalXP(level)
        return (xp - base).toDouble() / xpToAdvance(level)
    }

    /** XP into the current level, and XP needed for the next. */
    fun xpIntoLevel(xp: Int): Pair<Int, Int> {
        val level = level(xp)
        return (xp - totalXP(level)) to xpToAdvance(level)
    }

    /** Coins granted when reaching [level]. */
    fun levelUpReward(level: Int): Int = 20 + level * 5
}
