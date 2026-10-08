//
//  Achievements.kt
//  Starshower Run
//
//  Every achievement is "some stat ≥ goal", which keeps evaluation trivial
//  and progress bars accurate.
//

package com.starshower.run.core.progression

import com.starshower.run.core.engine.RGBColor

enum class AchievementCategory(val title: String) {
    SURVIVAL("Survival"),
    SCORE("Score"),
    SKILL("Skill"),
    COLLECTION("Collection"),
    DEDICATION("Dedication");

    val icon: String
        get() = when (this) {
            SURVIVAL -> "heart.fill"
            SCORE -> "chart.line.uptrend.xyaxis"
            SKILL -> "scope"
            COLLECTION -> "star.fill"
            DEDICATION -> "flame.fill"
        }
}

enum class AchievementTier {
    BRONZE, SILVER, GOLD, PLATINUM;

    val title: String
        get() = when (this) {
            BRONZE -> "Bronze"
            SILVER -> "Silver"
            GOLD -> "Gold"
            PLATINUM -> "Platinum"
        }

    /**
     * Coins paid when unlocked. Deliberately modest – achievements are about
     * bragging rights and unlocking gear, not buying out the shop.
     */
    val reward: Int
        get() = when (this) {
            BRONZE -> 25
            SILVER -> 60
            GOLD -> 150
            PLATINUM -> 400
        }

    val color: RGBColor
        get() = when (this) {
            BRONZE -> RGBColor.hex(0xD08B4E)
            SILVER -> RGBColor.hex(0xC9D3E0)
            GOLD -> RGBColor.hex(0xFFD84D)
            PLATINUM -> RGBColor.hex(0x9BF6FF)
        }
}

data class AchievementDefinition(
    val id: String,
    val title: String,
    val detail: String,
    val icon: String,
    val category: AchievementCategory,
    val tier: AchievementTier,
    val goal: Int,
    val metric: (PlayerStats) -> Int,
    /** Hidden achievements show "???" until unlocked. */
    val secret: Boolean = false,
) {
    /** Global achievement key (mapped to a Play Games achievement id in the app). */
    val globalID: String get() = "vr.achievement.$id"

    val reward: Int get() = tier.reward

    fun progress(stats: PlayerStats): Int = minOf(metric(stats), goal)

    fun fraction(stats: PlayerStats): Double =
        if (goal > 0) progress(stats).toDouble() / goal else 1.0

    fun isMet(stats: PlayerStats): Boolean = metric(stats) >= goal

    /** Cosmetic gated behind this achievement, if any. */
    val cosmeticReward: Cosmetic?
        get() = Cosmetic.catalog.firstOrNull { cosmetic -> cosmetic.gates.contains(UnlockGate.Achievement(id)) }
}

object AchievementCatalog {
    fun find(id: String): AchievementDefinition? = all.firstOrNull { it.id == id }

    private fun a(
        id: String, title: String, detail: String, icon: String,
        category: AchievementCategory, tier: AchievementTier, goal: Int,
        metric: (PlayerStats) -> Int, secret: Boolean = false,
    ) = AchievementDefinition(id, title, detail, icon, category, tier, goal, metric, secret)

    private val S = AchievementCategory.SURVIVAL
    private val SC = AchievementCategory.SCORE
    private val SK = AchievementCategory.SKILL
    private val C = AchievementCategory.COLLECTION
    private val D = AchievementCategory.DEDICATION
    private val BRONZE = AchievementTier.BRONZE
    private val SILVER = AchievementTier.SILVER
    private val GOLD = AchievementTier.GOLD
    private val PLATINUM = AchievementTier.PLATINUM

    val all: List<AchievementDefinition> = listOf(
        // Survival
        a("survive_30", "First Steps", "Survive 30 seconds in Endless.", "figure.walk", S, BRONZE, 30, PlayerStats::bestEndlessTime),
        a("survive_60", "One Minute Wonder", "Survive 60 seconds in Endless.", "clock.fill", S, BRONZE, 60, PlayerStats::bestEndlessTime),
        a("survive_90", "Master Dodger", "Survive 90 seconds in Endless.", "crown.fill", S, SILVER, 90, PlayerStats::bestEndlessTime),
        a("survive_120", "Legendary", "Survive 2 minutes in Endless.", "star.circle.fill", S, GOLD, 120, PlayerStats::bestEndlessTime),
        a("survive_180", "Untouchable", "Survive 3 minutes in Endless.", "bolt.shield.fill", S, PLATINUM, 180, PlayerStats::bestEndlessTime),
        a("total_10min", "Time Traveller", "Survive 10 minutes in total.", "hourglass", S, BRONZE, 600, PlayerStats::totalTimeSurvived),
        a("total_1hour", "Hour of Power", "Survive 1 hour in total.", "hourglass.circle.fill", S, GOLD, 3600, PlayerStats::totalTimeSurvived),
        a("purist_60", "Purist", "Survive 60s in Endless without power-ups.", "hand.raised.fill", S, SILVER, 60, PlayerStats::bestPuristTime),
        a("expert_60", "Expert Dodger", "Survive 60s in Endless on Expert.", "flame.circle.fill", S, GOLD, 60, PlayerStats::bestExpertTime),
        a("streak_3", "On Fire", "Survive 30s+ in 3 Endless runs in a row.", "flame.fill", S, SILVER, 3, PlayerStats::longestSurvivalStreak),
        a("level_6", "Into the Rush", "Reach level 6 in a single run.", "gauge.with.dots.needle.50percent", S, SILVER, 6, PlayerStats::bestLevel),
        a("level_9", "Deep Rush", "Reach level 9 in a single run.", "gauge.with.dots.needle.100percent", S, PLATINUM, 9, PlayerStats::bestLevel),

        // Score
        a("endless_1k", "Warming Up", "Score 1,000 in Endless.", "1.circle.fill", SC, BRONZE, 1_000, PlayerStats::bestEndlessScore),
        a("endless_5k", "High Roller", "Score 5,000 in Endless.", "5.circle.fill", SC, SILVER, 5_000, PlayerStats::bestEndlessScore),
        a("endless_15k", "Score Attack", "Score 15,000 in Endless.", "trophy.fill", SC, GOLD, 15_000, PlayerStats::bestEndlessScore),
        a("ta_5k", "Against the Clock", "Score 5,000 in Time Attack.", "stopwatch", SC, BRONZE, 5_000, PlayerStats::bestTimeAttackScore),
        a("ta_15k", "Time Bandit", "Score 15,000 in Time Attack.", "stopwatch.fill", SC, SILVER, 15_000, PlayerStats::bestTimeAttackScore),
        a("ta_35k", "Chrono Master", "Score 35,000 in Time Attack.", "timer", SC, GOLD, 35_000, PlayerStats::bestTimeAttackScore),
        a("daily_3k", "Daily Driver", "Score 3,000 in a Daily Run.", "calendar", SC, SILVER, 3_000, PlayerStats::bestDailyScore),

        // Skill
        a("nearmiss_run_15", "Thread the Needle", "Get 15 near misses in one run.", "scope", SK, SILVER, 15, PlayerStats::bestNearMissesInRun),
        a("nearmiss_100", "Daredevil", "Get 100 near misses in total.", "wind", SK, BRONZE, 100, PlayerStats::totalNearMisses),
        a("nearmiss_1000", "Adrenaline Junkie", "Get 1,000 near misses in total.", "bolt.heart.fill", SK, GOLD, 1_000, PlayerStats::totalNearMisses),
        a("perfect_50", "Pixel Perfect", "Get 50 perfect misses.", "viewfinder", SK, SILVER, 50, PlayerStats::totalPerfectMisses),
        a("multiplier_3", "Combo Starter", "Reach a x3 multiplier.", "multiply.circle", SK, BRONZE, 3, PlayerStats::bestMultiplier),
        a("multiplier_6", "Max Combo", "Reach the x6 multiplier.", "multiply.circle.fill", SK, GOLD, 6, PlayerStats::bestMultiplier),
        a("nova_12", "Supernova", "Clear 12 hazards with one Nova.", "burst.fill", SK, SILVER, 12, PlayerStats::bestNovaClear, secret = true),
        a("dodge_1000", "Evasive", "Dodge 1,000 hazards.", "shield.lefthalf.filled", SK, BRONZE, 1_000, PlayerStats::totalDodged),
        a("dodge_10000", "Neo", "Dodge 10,000 hazards.", "figure.dance", SK, PLATINUM, 10_000, PlayerStats::totalDodged),

        // Collection
        a("stars_run_40", "Star Struck", "Collect 40 stars in one run.", "star.leadinghalf.filled", C, SILVER, 40, PlayerStats::bestStarsInRun),
        a("stars_250", "Star Collector", "Collect 250 stars.", "star", C, BRONZE, 250, PlayerStats::totalStars),
        a("stars_2500", "Stellar", "Collect 2,500 stars.", "star.fill", C, GOLD, 2_500, PlayerStats::totalStars),
        a("powerups_50", "Powered Up", "Collect 50 power-ups.", "bolt.fill", C, BRONZE, 50, PlayerStats::totalPowerUps),
        a("shop_1", "Fresh Look", "Buy your first cosmetic.", "bag.fill", C, BRONZE, 1, PlayerStats::cosmeticsPurchased),
        a("shop_10", "Fashionista", "Buy 10 cosmetics.", "tshirt.fill", C, GOLD, 10, PlayerStats::cosmeticsPurchased),

        // Dedication
        a("games_10", "Getting Started", "Play 10 games.", "gamecontroller", D, BRONZE, 10, PlayerStats::gamesPlayed),
        a("games_100", "Dedicated", "Play 100 games.", "gamecontroller.fill", D, SILVER, 100, PlayerStats::gamesPlayed),
        a("games_500", "Addicted", "Play 500 games.", "infinity.circle.fill", D, PLATINUM, 500, PlayerStats::gamesPlayed),
        a("explorer", "Explorer", "Play every game mode.", "map.fill", D, BRONZE, 4, PlayerStats::modesPlayed),
        a("daily_1", "Daily Habit", "Complete a Daily Run.", "calendar.badge.checkmark", D, BRONZE, 1, PlayerStats::dailyRunsCompleted),
        a("daily_30", "Creature of Habit", "Complete 30 Daily Runs.", "calendar.circle.fill", D, GOLD, 30, PlayerStats::dailyRunsCompleted),
        a("login_7", "Week Streak", "Log in 7 days in a row.", "7.circle.fill", D, SILVER, 7, PlayerStats::longestLoginStreak),
        a("allclear_1", "Clean Sweep", "Clear every daily mission in one day.", "checkmark.circle.fill", D, BRONZE, 1, PlayerStats::allClearDays),
        a("allclear_streak_7", "Week of Wins", "Clear every daily mission 7 days in a row.", "calendar.badge.checkmark", D, GOLD, 7, PlayerStats::longestAllClearStreak),
        a("allclear_30", "Month of Mastery", "Clear every daily mission on 30 days.", "crown.fill", D, PLATINUM, 30, PlayerStats::allClearDays),
        a("missions_10", "Mission Ready", "Complete 10 daily missions.", "checklist", D, BRONZE, 10, PlayerStats::missionsCompleted),
        a("missions_100", "Special Agent", "Complete 100 daily missions.", "checkmark.seal.fill", D, GOLD, 100, PlayerStats::missionsCompleted),
    )
}
