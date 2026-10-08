//
//  Daily.kt
//  Starshower Run
//
//  Daily Run seeds, daily missions and the login streak calendar.
//

package com.starshower.run.core.progression

import com.starshower.run.core.engine.Difficulty
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.RunConfig
import com.starshower.run.core.engine.RunModifier
import com.starshower.run.core.engine.RunResult
import com.starshower.run.core.engine.SeededRandom
import com.starshower.run.core.engine.StableHash
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// MARK: - Daily Run

object DailyChallenge {
    /** "yyyy-MM-dd" in the player's time zone. */
    fun dayKey(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = now.atZone(zone).toLocalDate()
        // Locale.ROOT: this is a data key and must stay ASCII in every locale.
        return String.format(Locale.ROOT, "%04d-%02d-%02d", date.year, date.monthValue, date.dayOfMonth)
    }

    fun previousDayKey(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val yesterday = now.atZone(zone).minusDays(1).toInstant()
        return dayKey(yesterday, zone)
    }

    fun seed(dayKey: String): Long = StableHash.fnv1a("velocity-rush-daily-$dayKey")

    fun modifier(dayKey: String): RunModifier {
        val pool = RunModifier.dailyPool
        val index = (StableHash.fnv1a("modifier-$dayKey").toULong() % pool.size.toULong()).toInt()
        return pool[index]
    }

    /** Daily Runs always use Normal difficulty so scores are comparable. */
    fun config(now: Instant, zone: ZoneId = ZoneId.systemDefault()): RunConfig {
        val key = dayKey(now, zone)
        return RunConfig(
            mode = GameMode.DAILY,
            difficulty = Difficulty.NORMAL,
            modifier = modifier(key),
            seed = seed(key),
            dailyKey = key,
        )
    }

    /** Seconds until local midnight. */
    fun timeUntilReset(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Long {
        val tomorrow = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        return Duration.between(now, tomorrow).seconds.coerceAtLeast(0)
    }
}

// MARK: - Login rewards

object LoginRewards {
    val schedule = listOf(25, 50, 75, 100, 125, 150, 300)

    fun reward(streakDay: Int): Int = schedule[(maxOf(streakDay, 1) - 1) % schedule.size]
}

// MARK: - Missions

@Serializable
enum class MissionKind {
    @SerialName("collectStars") COLLECT_STARS,
    @SerialName("nearMisses") NEAR_MISSES,
    @SerialName("dodge") DODGE,
    @SerialName("playRuns") PLAY_RUNS,
    @SerialName("powerUps") POWER_UPS,
    @SerialName("surviveEndless") SURVIVE_ENDLESS,
    @SerialName("scoreEndless") SCORE_ENDLESS,
    @SerialName("scoreTimeAttack") SCORE_TIME_ATTACK,
    @SerialName("reachMultiplier") REACH_MULTIPLIER,
    @SerialName("playDaily") PLAY_DAILY,
    @SerialName("starsInRun") STARS_IN_RUN;

    /** Goals for easy / medium / hard slots. */
    val goals: List<Int>
        get() = when (this) {
            COLLECT_STARS -> listOf(30, 75, 150)
            NEAR_MISSES -> listOf(10, 25, 50)
            DODGE -> listOf(150, 400, 900)
            PLAY_RUNS -> listOf(3, 6, 10)
            POWER_UPS -> listOf(4, 10, 20)
            SURVIVE_ENDLESS -> listOf(30, 60, 90)
            SCORE_ENDLESS -> listOf(1_000, 2_500, 5_000)
            SCORE_TIME_ATTACK -> listOf(4_000, 9_000, 16_000)
            REACH_MULTIPLIER -> listOf(3, 4, 6)
            PLAY_DAILY -> listOf(1, 1, 1)
            STARS_IN_RUN -> listOf(15, 30, 45)
        }

    /** Kinds that only make sense as the easy slot. */
    val easyOnly: Boolean get() = this == PLAY_DAILY

    /** True when progress is the best single run rather than a running total. */
    val isSingleRun: Boolean
        get() = when (this) {
            SURVIVE_ENDLESS, SCORE_ENDLESS, SCORE_TIME_ATTACK, REACH_MULTIPLIER, STARS_IN_RUN -> true
            else -> false
        }

    val icon: String
        get() = when (this) {
            COLLECT_STARS, STARS_IN_RUN -> "star.fill"
            NEAR_MISSES -> "scope"
            DODGE -> "arrow.left.and.right"
            PLAY_RUNS -> "play.fill"
            POWER_UPS -> "bolt.fill"
            SURVIVE_ENDLESS -> "heart.fill"
            SCORE_ENDLESS -> "infinity"
            SCORE_TIME_ATTACK -> "stopwatch.fill"
            REACH_MULTIPLIER -> "multiply.circle.fill"
            PLAY_DAILY -> "calendar"
        }

    fun title(goal: Int): String = when (this) {
        COLLECT_STARS -> "Collect $goal stars"
        NEAR_MISSES -> "Get $goal near misses"
        DODGE -> "Dodge $goal hazards"
        PLAY_RUNS -> "Play $goal runs"
        POWER_UPS -> "Grab $goal power-ups"
        SURVIVE_ENDLESS -> "Survive ${goal}s in Endless"
        SCORE_ENDLESS -> "Score ${goal.formatted()} in Endless"
        SCORE_TIME_ATTACK -> "Score ${goal.formatted()} in Time Attack"
        REACH_MULTIPLIER -> "Reach a x$goal multiplier"
        PLAY_DAILY -> "Play today's Daily Run"
        STARS_IN_RUN -> "Collect $goal stars in one run"
    }

    /** Value contributed by a single run. */
    fun value(run: RunResult): Int = when (this) {
        COLLECT_STARS, STARS_IN_RUN -> run.stars
        NEAR_MISSES -> run.nearMisses
        DODGE -> run.dodged
        PLAY_RUNS -> 1
        POWER_UPS -> run.powerUps
        SURVIVE_ENDLESS -> if (run.mode == GameMode.ENDLESS || run.mode == GameMode.DAILY) run.survivedSeconds else 0
        SCORE_ENDLESS -> if (run.mode == GameMode.ENDLESS) run.score else 0
        SCORE_TIME_ATTACK -> if (run.mode == GameMode.TIME_ATTACK) run.score else 0
        REACH_MULTIPLIER -> run.maxMultiplier
        PLAY_DAILY -> if (run.mode == GameMode.DAILY) 1 else 0
    }
}

@Serializable
data class MissionProgress(
    val id: String,
    val kind: MissionKind,
    val goal: Int,
    var progress: Int = 0,
    val reward: Int,
    var claimed: Boolean = false,
) {
    val isComplete: Boolean get() = progress >= goal
    val title: String get() = kind.title(goal)
    val fraction: Double get() = if (goal > 0) minOf(progress.toDouble() / goal, 1.0) else 1.0

    fun apply(run: RunResult) {
        if (claimed) return
        val value = kind.value(run)
        progress = if (kind.isSingleRun) {
            maxOf(progress, minOf(value, goal))
        } else {
            minOf(goal, progress + value)
        }
    }
}

object MissionGenerator {
    val rewards = listOf(50, 100, 175)
    val xpRewards = listOf(60, 120, 200)
    const val ALL_COMPLETE_BONUS = 150
    const val ALL_COMPLETE_XP = 150

    fun xpReward(mission: MissionProgress): Int {
        val slot = rewards.indexOf(mission.reward).coerceAtLeast(0)
        return xpRewards[slot]
    }

    fun missions(dayKey: String): List<MissionProgress> {
        val rng = SeededRandom(StableHash.fnv1a("missions-$dayKey"))
        val available = MissionKind.entries.toMutableList()
        val result = mutableListOf<MissionProgress>()

        for (slot in 0 until 3) {
            val candidates = available.filter { slot == 0 || !it.easyOnly }
            if (candidates.isEmpty()) break
            val kind = candidates[rng.int(0..candidates.size - 1)]
            available.remove(kind)
            // Avoid two near-identical star missions on one day.
            if (kind == MissionKind.COLLECT_STARS) available.remove(MissionKind.STARS_IN_RUN)
            if (kind == MissionKind.STARS_IN_RUN) available.remove(MissionKind.COLLECT_STARS)
            result += MissionProgress(
                id = "$dayKey-$slot", kind = kind,
                goal = kind.goals[slot], reward = rewards[slot],
            )
        }
        return result
    }
}
