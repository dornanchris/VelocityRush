//
//  GameTypes.kt
//  Starshower Run
//
//  Modes, difficulties, modifiers and entity kinds shared by the engine,
//  the renderer and the progression systems.
//
//  `icon` values are SF Symbol names (matching the iOS app); the Android UI
//  maps them to Material icons in one place (`SymbolIcons`).
//

package com.starshower.run.core.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.random.Random

// MARK: - Game Mode

@Serializable
enum class GameMode(val rawValue: String) {
    @SerialName("endless") ENDLESS("endless"),
    @SerialName("timeAttack") TIME_ATTACK("timeAttack"),
    @SerialName("daily") DAILY("daily"),
    @SerialName("zen") ZEN("zen");

    val title: String
        get() = when (this) {
            ENDLESS -> "Endless"
            TIME_ATTACK -> "Time Attack"
            DAILY -> "Daily Run"
            ZEN -> "Zen"
        }

    val tagline: String
        get() = when (this) {
            ENDLESS -> "One life. How long can you last?"
            TIME_ATTACK -> "60 seconds. Grab every star."
            DAILY -> "Same run for everyone, every day."
            ZEN -> "No score. No pressure. Just flow."
        }

    val rules: List<String>
        get() = when (this) {
            ENDLESS -> listOf(
                "Dodge everything. One hit ends the run.",
                "Near misses and stars build your multiplier.",
                "The rush speeds up every 15 seconds.",
            )
            TIME_ATTACK -> listOf(
                "You have 60 seconds on the clock.",
                "Stars are worth big points. Crystals add +3s.",
                "Getting hit costs you 5 seconds.",
            )
            DAILY -> listOf(
                "A hand-picked twist changes the rules each day.",
                "Everyone gets the exact same hazards.",
                "Your best score today goes on the board.",
            )
            ZEN -> listOf(
                "Hits never end the run.",
                "Collect stars for coins at your own pace.",
                "Perfect for warming up or winding down.",
            )
        }

    val icon: String
        get() = when (this) {
            ENDLESS -> "infinity"
            TIME_ATTACK -> "stopwatch.fill"
            DAILY -> "calendar.badge.clock"
            ZEN -> "leaf.fill"
        }

    val accent: RGBColor
        get() = when (this) {
            ENDLESS -> RGBColor.hex(0xFF3D6E)
            TIME_ATTACK -> RGBColor.hex(0xFFB800)
            DAILY -> RGBColor.hex(0x38E1FF)
            ZEN -> RGBColor.hex(0x6BFF9E)
        }

    /** Ranked modes have leaderboards and personal bests. */
    val isRanked: Boolean get() = this != ZEN

    /** Global leaderboard key (mapped to a Play Games leaderboard id in the app). */
    val leaderboardID: String?
        get() = when (this) {
            ENDLESS -> "vr.leaderboard.endless"
            TIME_ATTACK -> "vr.leaderboard.timeattack"
            DAILY -> "vr.leaderboard.daily"
            ZEN -> null
        }

    val usesClock: Boolean get() = this == TIME_ATTACK
    val hasSingleLife: Boolean get() = this == ENDLESS || this == DAILY

    companion object {
        fun fromRaw(raw: String?): GameMode? = entries.firstOrNull { it.rawValue == raw }
    }
}

// MARK: - Difficulty

@Serializable
enum class Difficulty(val rawValue: Int) {
    @SerialName("0") EASY(0),
    @SerialName("1") NORMAL(1),
    @SerialName("2") HARD(2),
    @SerialName("3") EXPERT(3);

    val title: String
        get() = when (this) {
            EASY -> "Easy"
            NORMAL -> "Normal"
            HARD -> "Hard"
            EXPERT -> "Expert"
        }

    val speedMultiplier: Double
        get() = when (this) {
            EASY -> 0.82
            NORMAL -> 1.0
            HARD -> 1.14
            EXPERT -> 1.28
        }

    /** Multiplies the spawn interval (lower = more hazards). */
    val spawnIntervalMultiplier: Double
        get() = when (this) {
            EASY -> 1.25
            NORMAL -> 1.0
            HARD -> 0.86
            EXPERT -> 0.74
        }

    val scoreMultiplier: Double
        get() = when (this) {
            EASY -> 0.75
            NORMAL -> 1.0
            HARD -> 1.3
            EXPERT -> 1.6
        }

    companion object {
        fun fromRaw(raw: Int): Difficulty? = entries.firstOrNull { it.rawValue == raw }
    }
}

// MARK: - Run Modifier (Daily twists)

@Serializable
enum class RunModifier(val rawValue: String) {
    @SerialName("none") NONE("none"),
    @SerialName("giantDots") GIANT_DOTS("giantDots"),
    @SerialName("speedDemon") SPEED_DEMON("speedDemon"),
    @SerialName("starStorm") STAR_STORM("starStorm"),
    @SerialName("wobbleWorld") WOBBLE_WORLD("wobbleWorld"),
    @SerialName("tinyHero") TINY_HERO("tinyHero"),
    @SerialName("blackout") BLACKOUT("blackout"),
    @SerialName("meteorShower") METEOR_SHOWER("meteorShower"),
    @SerialName("purist") PURIST("purist");

    val title: String
        get() = when (this) {
            NONE -> "Standard"
            GIANT_DOTS -> "Giant Dots"
            SPEED_DEMON -> "Speed Demon"
            STAR_STORM -> "Star Storm"
            WOBBLE_WORLD -> "Wobble World"
            TINY_HERO -> "Tiny Hero"
            BLACKOUT -> "Blackout"
            METEOR_SHOWER -> "Meteor Shower"
            PURIST -> "Purist"
        }

    val detail: String
        get() = when (this) {
            NONE -> "No twist today."
            GIANT_DOTS -> "Every hazard is 50% bigger."
            SPEED_DEMON -> "Everything falls 35% faster."
            STAR_STORM -> "Stars rain from the sky. Greed is dangerous."
            WOBBLE_WORLD -> "Every hazard sways side to side."
            TINY_HERO -> "You're tiny, but the sky is crowded."
            BLACKOUT -> "The lights are out. You only see what's near you."
            METEOR_SHOWER -> "Blazing meteors from the very first second."
            PURIST -> "No power-ups. Just you and your reflexes."
        }

    val icon: String
        get() = when (this) {
            NONE -> "circle"
            GIANT_DOTS -> "circle.circle.fill"
            SPEED_DEMON -> "hare.fill"
            STAR_STORM -> "sparkles"
            WOBBLE_WORLD -> "water.waves"
            TINY_HERO -> "smallcircle.filled.circle"
            BLACKOUT -> "moon.fill"
            METEOR_SHOWER -> "flame.fill"
            PURIST -> "hand.raised.fill"
        }

    val scoreMultiplier: Double
        get() = when (this) {
            NONE -> 1.0
            GIANT_DOTS -> 1.2
            SPEED_DEMON -> 1.3
            STAR_STORM -> 0.9
            WOBBLE_WORLD -> 1.15
            TINY_HERO -> 1.1
            BLACKOUT -> 1.4
            METEOR_SHOWER -> 1.25
            PURIST -> 1.2
        }

    companion object {
        /** Same order as the iOS `allCases` minus `.none` – keeps Daily twists in sync across platforms. */
        val dailyPool: List<RunModifier> get() = entries.filter { it != NONE }
    }
}

// MARK: - Run Configuration

data class RunConfig(
    val mode: GameMode,
    val difficulty: Difficulty,
    val modifier: RunModifier = RunModifier.NONE,
    /** 64 random bits (unsigned on iOS). */
    val seed: Long = randomSeed(),
    /** Set for Daily Runs, e.g. "2026-09-29". */
    val dailyKey: String? = null,
    val id: String = UUID.randomUUID().toString(),
) {
    /** Returns a fresh config for "Play Again" – same rules, same seed for dailies. */
    fun replay(): RunConfig = RunConfig(
        mode = mode,
        difficulty = difficulty,
        modifier = modifier,
        seed = if (mode == GameMode.DAILY) seed else randomSeed(),
        dailyKey = dailyKey,
    )

    val scoreMultiplier: Double get() = difficulty.scoreMultiplier * modifier.scoreMultiplier

    companion object {
        fun randomSeed(): Long {
            val value = Random.nextLong()
            return if (value == 0L) 1L else value
        }
    }
}

// MARK: - Entities

@Serializable
enum class HazardKind {
    /** Plain falling dot. */
    DROP,
    /** Small, very fast meteor that is telegraphed by a warning. */
    SPEEDER,
    /** Sways left and right while falling. */
    WOBBLER,
    /** Huge and slow. */
    GIANT,
    /** Splits into two fragments halfway down. */
    SPLITTER,
    /** Fragment produced by a splitter. */
    FRAGMENT,
}

enum class PickupKind(val rawValue: String) {
    STAR("star"),
    SHIELD("shield"),
    SLOW_MO("slowMo"),
    MAGNET("magnet"),
    SHRINK("shrink"),
    NOVA("nova"),
    TIME_CRYSTAL("timeCrystal");

    val isPowerUp: Boolean get() = this != STAR && this != TIME_CRYSTAL

    val title: String
        get() = when (this) {
            STAR -> "Star"
            SHIELD -> "Shield"
            SLOW_MO -> "Slow-Mo"
            MAGNET -> "Magnet"
            SHRINK -> "Shrink"
            NOVA -> "Nova"
            TIME_CRYSTAL -> "+3s"
        }

    val icon: String
        get() = when (this) {
            STAR -> "star.fill"
            SHIELD -> "shield.fill"
            SLOW_MO -> "tortoise.fill"
            MAGNET -> "dot.radiowaves.left.and.right"
            SHRINK -> "arrow.down.right.and.arrow.up.left"
            NOVA -> "burst.fill"
            TIME_CRYSTAL -> "hourglass"
        }

    val color: RGBColor
        get() = when (this) {
            STAR -> RGBColor.hex(0xFFD84D)
            SHIELD -> RGBColor.hex(0x4DA6FF)
            SLOW_MO -> RGBColor.hex(0xB07CFF)
            MAGNET -> RGBColor.hex(0xFF6BD6)
            SHRINK -> RGBColor.hex(0x5CFFB0)
            NOVA -> RGBColor.hex(0xFF8A3D)
            TIME_CRYSTAL -> RGBColor.hex(0x3DF5FF)
        }
}

/** Power-ups that stay active for a duration. */
enum class TimedPowerUp {
    SLOW_MO,
    MAGNET,
    SHRINK;

    val duration: Double
        get() = when (this) {
            SLOW_MO -> 5.0
            MAGNET -> 8.0
            SHRINK -> 7.0
        }

    val pickup: PickupKind
        get() = when (this) {
            SLOW_MO -> PickupKind.SLOW_MO
            MAGNET -> PickupKind.MAGNET
            SHRINK -> PickupKind.SHRINK
        }
}
