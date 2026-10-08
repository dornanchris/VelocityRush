//
//  Progression.kt
//  Starshower Run
//
//  Rules that apply runs, purchases and claims to a PlayerProfile. Every
//  function mutates the profile it is given; `ProgressStore` (the observable
//  wrapper in the app) works on a copy and publishes the result.
//

package com.starshower.run.core.progression

import com.starshower.run.core.engine.Difficulty
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.RunResult
import java.time.Instant
import java.time.ZoneId

data class RunRewards(
    var coinsFromStars: Int = 0,
    var coinsFromScore: Int = 0,
    var coinsFromLevelUps: Int = 0,
    var coinsFromAchievements: Int = 0,
    var xpGained: Int = 0,
    var levelBefore: Int = 1,
    var levelAfter: Int = 1,
    var newAchievements: List<String> = emptyList(),
    var newCosmetics: List<String> = emptyList(),
    var missionsReady: List<String> = emptyList(),
    var isNewBest: Boolean = false,
    var previousBest: Int = 0,
    var localRank: Int? = null,
) {
    val totalCoins: Int get() = coinsFromStars + coinsFromScore + coinsFromLevelUps + coinsFromAchievements
    val leveledUp: Boolean get() = levelAfter > levelBefore
}

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data class NotEnoughCoins(val needed: Int) : PurchaseResult
    data object Locked : PurchaseResult
}

/** What a mission claim paid out. */
data class MissionClaim(
    var coins: Int = 0,
    var xp: Int = 0,
    /** True if this claim cleared the day (all-clear bonus included in `coins`). */
    var allClear: Boolean = false,
)

/** What a login reward claim paid out. */
data class LoginClaim(val coins: Int, val achievements: List<String>)

object Progression {
    const val LEADERBOARD_SIZE = 10

    // MARK: Daily refresh

    /**
     * Rolls missions and the Daily Run board over to [now]'s day if needed.
     * Returns true when the profile changed.
     */
    fun refreshDaily(profile: PlayerProfile, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val today = DailyChallenge.dayKey(now, zone)
        if (profile.daily.dayKey == today) return false
        val lastLogin = profile.daily.lastLoginRewardDay
        profile.daily = DailyState(
            dayKey = today,
            missions = MissionGenerator.missions(today),
            lastLoginRewardDay = lastLogin,
        )

        // Missed days break streaks.
        val yesterday = DailyChallenge.previousDayKey(now, zone)
        if (lastLogin != yesterday && lastLogin != today) {
            profile.stats.loginStreak = 0
        }
        if (profile.lastAllClearDay != yesterday && profile.lastAllClearDay != today) {
            profile.stats.allClearStreak = 0
        }
        return true
    }

    // MARK: Login reward

    fun isLoginRewardAvailable(profile: PlayerProfile, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        profile.daily.lastLoginRewardDay != DailyChallenge.dayKey(now, zone)

    /** The streak day the next claim would count as (1...). */
    fun nextLoginStreakDay(profile: PlayerProfile, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int {
        val yesterday = DailyChallenge.previousDayKey(now, zone)
        return if (profile.daily.lastLoginRewardDay == yesterday) profile.stats.loginStreak + 1 else 1
    }

    /** Claims today's login reward. Pays 0 coins if already claimed. */
    fun claimLoginReward(profile: PlayerProfile, now: Instant, zone: ZoneId = ZoneId.systemDefault()): LoginClaim {
        if (!isLoginRewardAvailable(profile, now, zone)) return LoginClaim(0, emptyList())
        val day = nextLoginStreakDay(profile, now, zone)
        val coins = LoginRewards.reward(day)
        profile.stats.loginStreak = day
        profile.stats.longestLoginStreak = maxOf(profile.stats.longestLoginStreak, day)
        profile.daily.lastLoginRewardDay = DailyChallenge.dayKey(now, zone)
        grant(coins, profile)
        val unlocked = evaluateAchievements(profile, now)
        unlockEarnedCosmetics(profile)
        return LoginClaim(coins, unlocked.map { it.id })
    }

    // MARK: Runs

    /** (coins from stars, coins from score) */
    fun coins(run: RunResult): Pair<Int, Int> = when (run.mode) {
        GameMode.ZEN -> run.stars / 2 to 0
        GameMode.ENDLESS, GameMode.DAILY -> run.stars to run.score / 250
        GameMode.TIME_ATTACK -> run.stars / 2 to run.score / 600
    }

    fun xp(run: RunResult): Int = when (run.mode) {
        GameMode.ZEN -> run.survivedSeconds / 2 + run.stars
        GameMode.ENDLESS, GameMode.DAILY -> run.score / 20 + run.survivedSeconds * 2 + run.stars
        GameMode.TIME_ATTACK -> run.score / 60 + run.survivedSeconds + run.stars
    }

    /** Applies a finished run to the profile and returns what the player earned. */
    fun record(run: RunResult, profile: PlayerProfile, now: Instant, zone: ZoneId = ZoneId.systemDefault()): RunRewards {
        refreshDaily(profile, now, zone)

        val rewards = RunRewards()
        rewards.levelBefore = profile.level
        rewards.previousBest = profile.stats.best(run.mode)
        val cosmeticsBefore = profile.unlockedCosmetics

        updateStats(
            profile.stats, run,
            dailyAttemptsBefore = profile.daily.dailyRunAttempts,
            isTodaysDaily = run.dailyKey == profile.daily.dayKey,
        )

        // Personal best (before leaderboards so the flag is accurate)
        if (run.mode.isRanked) {
            rewards.isNewBest = run.score > rewards.previousBest && run.score > 0
            if (run.mode == GameMode.DAILY) {
                if (run.dailyKey == profile.daily.dayKey) {
                    profile.daily.dailyRunAttempts += 1
                    profile.daily.dailyRunBest = maxOf(profile.daily.dailyRunBest, run.score)
                    val (board, rank) = insert(run, profile.daily.dailyRunLeaderboard)
                    profile.daily.dailyRunLeaderboard = board
                    rewards.localRank = rank
                }
            } else {
                val (board, rank) = insert(run, profile.leaderboards[run.mode.rawValue].orEmpty())
                profile.leaderboards = profile.leaderboards + (run.mode.rawValue to board)
                rewards.localRank = rank
            }
        }

        // Missions
        val ready = mutableListOf<String>()
        for (mission in profile.daily.missions) {
            val wasComplete = mission.isComplete
            mission.apply(run)
            if (!wasComplete && mission.isComplete) ready += mission.id
        }
        rewards.missionsReady = ready

        // Coins & XP
        val (fromStars, fromScore) = coins(run)
        rewards.coinsFromStars = fromStars
        rewards.coinsFromScore = fromScore
        grant(fromStars + fromScore, profile)

        rewards.xpGained = xp(run)
        rewards.coinsFromLevelUps = addXP(rewards.xpGained, profile)
        rewards.levelAfter = profile.level

        // Achievements, then anything they (or the new stats) unlock
        val unlocked = evaluateAchievements(profile, now)
        rewards.newAchievements = unlocked.map { it.id }
        rewards.coinsFromAchievements = unlocked.sumOf { it.reward }
        unlockEarnedCosmetics(profile)
        rewards.newCosmetics = Cosmetic.catalog.map { it.id }.filter {
            it in profile.unlockedCosmetics && it !in cosmeticsBefore
        }
        return rewards
    }

    fun updateStats(stats: PlayerStats, run: RunResult, dailyAttemptsBefore: Int, isTodaysDaily: Boolean) {
        val seconds = run.survivedSeconds
        stats.gamesPlayed += 1
        stats.totalTimeSurvived += seconds
        stats.totalStars += run.stars
        stats.totalNearMisses += run.nearMisses
        stats.totalPerfectMisses += run.perfectMisses
        stats.totalDodged += run.dodged
        stats.totalPowerUps += run.powerUps
        stats.bestStarsInRun = maxOf(stats.bestStarsInRun, run.stars)
        stats.bestNearMissesInRun = maxOf(stats.bestNearMissesInRun, run.nearMisses)
        stats.bestMultiplier = maxOf(stats.bestMultiplier, run.maxMultiplier)
        stats.bestCombo = maxOf(stats.bestCombo, run.maxCombo)
        stats.bestNovaClear = maxOf(stats.bestNovaClear, run.bestNovaClear)

        when (run.mode) {
            GameMode.ENDLESS -> {
                stats.endlessGames += 1
                stats.bestEndlessScore = maxOf(stats.bestEndlessScore, run.score)
                stats.bestEndlessTime = maxOf(stats.bestEndlessTime, seconds)
                stats.bestLevel = maxOf(stats.bestLevel, run.levelReached)
                if (run.powerUps == 0) stats.bestPuristTime = maxOf(stats.bestPuristTime, seconds)
                if (run.difficulty == Difficulty.EXPERT) stats.bestExpertTime = maxOf(stats.bestExpertTime, seconds)
                if (seconds >= 30) {
                    stats.currentSurvivalStreak += 1
                    stats.longestSurvivalStreak = maxOf(stats.longestSurvivalStreak, stats.currentSurvivalStreak)
                } else {
                    stats.currentSurvivalStreak = 0
                }
            }
            GameMode.TIME_ATTACK -> {
                stats.timeAttackGames += 1
                stats.bestTimeAttackScore = maxOf(stats.bestTimeAttackScore, run.score)
            }
            GameMode.DAILY -> {
                stats.dailyGames += 1
                stats.bestDailyScore = maxOf(stats.bestDailyScore, run.score)
                stats.bestLevel = maxOf(stats.bestLevel, run.levelReached)
                if (isTodaysDaily && dailyAttemptsBefore == 0) {
                    stats.dailyRunsCompleted += 1
                }
            }
            GameMode.ZEN -> stats.zenGames += 1
        }
    }

    /**
     * Inserts into a top-N board. Returns the new board and the 1-based rank
     * if the run made it.
     */
    fun insert(run: RunResult, board: List<LeaderboardEntry>): Pair<List<LeaderboardEntry>, Int?> {
        val entry = LeaderboardEntry(
            score = run.score, duration = run.duration, date = run.date,
            difficulty = run.difficulty, modifier = run.modifier,
        )
        val sorted = (board + entry)
            .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.date })
            .take(LEADERBOARD_SIZE)
        val index = sorted.indexOfFirst { it.id == entry.id }
        return sorted to (if (index >= 0) index + 1 else null)
    }

    // MARK: XP

    /** Adds XP and pays level-up coins. Returns the coins granted. */
    fun addXP(amount: Int, profile: PlayerProfile): Int {
        if (amount <= 0) return 0
        val before = profile.level
        profile.xp += amount
        val after = profile.level
        if (after <= before) return 0
        val bonus = ((before + 1)..after).sumOf { Leveling.levelUpReward(it) }
        grant(bonus, profile)
        return bonus
    }

    // MARK: Achievements & unlocks

    /** Unlocks every achievement whose goal is met and grants its coins. */
    fun evaluateAchievements(profile: PlayerProfile, now: Instant): List<AchievementDefinition> {
        val unlocked = mutableListOf<AchievementDefinition>()
        for (definition in AchievementCatalog.all) {
            if (profile.unlockedAchievements.containsKey(definition.id)) continue
            if (!definition.isMet(profile.stats)) continue
            profile.unlockedAchievements = profile.unlockedAchievements + (definition.id to now.toEpochMilli())
            grant(definition.reward, profile)
            unlocked += definition
        }
        return unlocked
    }

    fun gatesMet(cosmetic: Cosmetic, profile: PlayerProfile): Boolean = cosmetic.gates.all { it.isMet(profile) }

    /** True if the item is for sale right now (gates met, not owned). Ignores the coin balance. */
    fun isPurchasable(cosmetic: Cosmetic, profile: PlayerProfile): Boolean =
        cosmetic.price != null && !profile.isUnlocked(cosmetic) && gatesMet(cosmetic, profile)

    /** Grants every earned-only cosmetic whose gates are now met. Returns the new ids. */
    fun unlockEarnedCosmetics(profile: PlayerProfile): List<String> {
        val newlyUnlocked = mutableListOf<String>()
        for (cosmetic in Cosmetic.catalog) {
            if (!cosmetic.isEarnedOnly || cosmetic.id in profile.unlockedCosmetics) continue
            if (gatesMet(cosmetic, profile)) {
                profile.unlockedCosmetics = profile.unlockedCosmetics + cosmetic.id
                newlyUnlocked += cosmetic.id
            }
        }
        return newlyUnlocked
    }

    fun grant(coins: Int, profile: PlayerProfile) {
        if (coins <= 0) return
        profile.coins += coins
        profile.stats.totalCoinsEarned += coins
    }

    // MARK: Shop

    fun purchase(cosmeticID: String, profile: PlayerProfile, now: Instant): PurchaseResult {
        val cosmetic = Cosmetic.find(cosmeticID) ?: return PurchaseResult.Locked
        if (profile.isUnlocked(cosmetic)) return PurchaseResult.AlreadyOwned
        val price = cosmetic.price
        if (price == null || !gatesMet(cosmetic, profile)) return PurchaseResult.Locked
        if (profile.coins < price) return PurchaseResult.NotEnoughCoins(price - profile.coins)
        profile.coins -= price
        profile.unlockedCosmetics = profile.unlockedCosmetics + cosmetic.id
        profile.stats.cosmeticsPurchased += 1
        equip(cosmetic.id, profile)
        evaluateAchievements(profile, now)
        unlockEarnedCosmetics(profile)
        return PurchaseResult.Success
    }

    fun equip(cosmeticID: String, profile: PlayerProfile): Boolean {
        val cosmetic = Cosmetic.find(cosmeticID) ?: return false
        if (!profile.isUnlocked(cosmetic)) return false
        when (cosmetic.category) {
            CosmeticCategory.SKIN -> profile.equippedSkin = cosmetic.id
            CosmeticCategory.TRAIL -> profile.equippedTrail = cosmetic.id
            CosmeticCategory.THEME -> profile.equippedTheme = cosmetic.id
        }
        return true
    }

    // MARK: Missions

    /**
     * Claims a completed mission (coins + XP). Clearing all three pays the
     * all-clear bonus automatically and extends the all-clear streak.
     */
    fun claimMission(missionID: String, profile: PlayerProfile, now: Instant, zone: ZoneId = ZoneId.systemDefault()): MissionClaim {
        val mission = profile.daily.missions.firstOrNull { it.id == missionID } ?: return MissionClaim()
        if (!mission.isComplete || mission.claimed) return MissionClaim()

        val claim = MissionClaim()
        mission.claimed = true
        profile.stats.missionsCompleted += 1
        claim.coins = mission.reward
        claim.xp = MissionGenerator.xpReward(mission)
        grant(mission.reward, profile)

        if (profile.daily.missions.all { it.claimed } && !profile.daily.allMissionsBonusClaimed) {
            profile.daily.allMissionsBonusClaimed = true
            claim.allClear = true
            claim.coins += MissionGenerator.ALL_COMPLETE_BONUS
            claim.xp += MissionGenerator.ALL_COMPLETE_XP
            grant(MissionGenerator.ALL_COMPLETE_BONUS, profile)
            registerAllClear(profile, now, zone)
        }

        claim.coins += addXP(claim.xp, profile)
        evaluateAchievements(profile, now)
        unlockEarnedCosmetics(profile)
        return claim
    }

    private fun registerAllClear(profile: PlayerProfile, now: Instant, zone: ZoneId) {
        val today = DailyChallenge.dayKey(now, zone)
        if (profile.lastAllClearDay == today) return
        val yesterday = DailyChallenge.previousDayKey(now, zone)
        profile.stats.allClearStreak = if (profile.lastAllClearDay == yesterday) profile.stats.allClearStreak + 1 else 1
        profile.stats.longestAllClearStreak = maxOf(profile.stats.longestAllClearStreak, profile.stats.allClearStreak)
        profile.stats.allClearDays += 1
        profile.lastAllClearDay = today
    }

    fun claimableMissionCount(profile: PlayerProfile): Int =
        profile.daily.missions.count { it.isComplete && !it.claimed }
}
