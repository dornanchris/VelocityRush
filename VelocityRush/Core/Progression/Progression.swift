//
//  Progression.swift
//  VelocityRush
//
//  Pure functions that apply runs, purchases and claims to a PlayerProfile.
//  `ProgressStore` (the observable wrapper) calls into these.
//

import Foundation

struct RunRewards: Equatable {
    var coinsFromStars = 0
    var coinsFromScore = 0
    var coinsFromLevelUps = 0
    var coinsFromAchievements = 0
    var xpGained = 0
    var levelBefore = 1
    var levelAfter = 1
    var newAchievements: [String] = []
    var newCosmetics: [String] = []
    var missionsReady: [String] = []
    var isNewBest = false
    var previousBest = 0
    var localRank: Int?

    var totalCoins: Int { coinsFromStars + coinsFromScore + coinsFromLevelUps + coinsFromAchievements }
    var leveledUp: Bool { levelAfter > levelBefore }
}

enum PurchaseResult: Equatable {
    case success
    case alreadyOwned
    case notEnoughCoins(needed: Int)
    case locked
}

enum Progression {
    static let leaderboardSize = 10

    // MARK: Daily refresh

    /// Rolls missions and the Daily Run board over to `now`'s day if needed.
    /// Returns true when the profile changed.
    @discardableResult
    static func refreshDaily(_ profile: inout PlayerProfile, now: Date, calendar: Calendar = .current) -> Bool {
        let today = DailyChallenge.dayKey(for: now, calendar: calendar)
        guard profile.daily.dayKey != today else { return false }
        let lastLogin = profile.daily.lastLoginRewardDay
        profile.daily = DailyState()
        profile.daily.dayKey = today
        profile.daily.missions = MissionGenerator.missions(for: today)
        profile.daily.lastLoginRewardDay = lastLogin

        // A missed day breaks the login streak.
        let yesterday = DailyChallenge.previousDayKey(before: now, calendar: calendar)
        if lastLogin != yesterday && lastLogin != today {
            profile.stats.loginStreak = 0
        }
        return true
    }

    // MARK: Login reward

    static func isLoginRewardAvailable(_ profile: PlayerProfile, now: Date, calendar: Calendar = .current) -> Bool {
        profile.daily.lastLoginRewardDay != DailyChallenge.dayKey(for: now, calendar: calendar)
    }

    /// The streak day the next claim would count as (1...).
    static func nextLoginStreakDay(_ profile: PlayerProfile, now: Date, calendar: Calendar = .current) -> Int {
        let yesterday = DailyChallenge.previousDayKey(before: now, calendar: calendar)
        return profile.daily.lastLoginRewardDay == yesterday ? profile.stats.loginStreak + 1 : 1
    }

    /// Claims today's login reward. Returns coins granted (0 if already claimed).
    @discardableResult
    static func claimLoginReward(_ profile: inout PlayerProfile, now: Date, calendar: Calendar = .current) -> (coins: Int, achievements: [String]) {
        guard isLoginRewardAvailable(profile, now: now, calendar: calendar) else { return (0, []) }
        let day = nextLoginStreakDay(profile, now: now, calendar: calendar)
        let coins = LoginRewards.reward(forStreakDay: day)
        profile.stats.loginStreak = day
        profile.stats.longestLoginStreak = max(profile.stats.longestLoginStreak, day)
        profile.daily.lastLoginRewardDay = DailyChallenge.dayKey(for: now, calendar: calendar)
        grant(coins, to: &profile)
        let unlocked = evaluateAchievements(&profile, now: now)
        return (coins, unlocked.map(\.id))
    }

    // MARK: Runs

    static func coins(for run: RunResult) -> (stars: Int, score: Int) {
        switch run.mode {
        case .zen:
            return (run.stars / 2, 0)
        case .endless, .daily:
            return (run.stars, run.score / 250)
        case .timeAttack:
            return (run.stars / 2, run.score / 600)
        }
    }

    static func xp(for run: RunResult) -> Int {
        switch run.mode {
        case .zen:
            return run.survivedSeconds / 2 + run.stars
        case .endless, .daily:
            return run.score / 20 + run.survivedSeconds * 2 + run.stars
        case .timeAttack:
            return run.score / 60 + run.survivedSeconds + run.stars
        }
    }

    /// Applies a finished run to the profile and returns what the player earned.
    static func record(_ run: RunResult, into profile: inout PlayerProfile, now: Date, calendar: Calendar = .current) -> RunRewards {
        refreshDaily(&profile, now: now, calendar: calendar)

        var rewards = RunRewards()
        rewards.levelBefore = profile.level
        rewards.previousBest = profile.stats.best(for: run.mode)

        updateStats(&profile.stats, with: run, dailyAttemptsBefore: profile.daily.dailyRunAttempts,
                    isTodaysDaily: run.dailyKey == profile.daily.dayKey)

        // Personal best (before leaderboards so the flag is accurate)
        if run.mode.isRanked {
            rewards.isNewBest = run.score > rewards.previousBest && run.score > 0
            if run.mode == .daily {
                if run.dailyKey == profile.daily.dayKey {
                    profile.daily.dailyRunAttempts += 1
                    profile.daily.dailyRunBest = max(profile.daily.dailyRunBest, run.score)
                    rewards.localRank = insert(run, into: &profile.daily.dailyRunLeaderboard)
                }
            } else {
                var board = profile.leaderboards[run.mode.rawValue] ?? []
                rewards.localRank = insert(run, into: &board)
                profile.leaderboards[run.mode.rawValue] = board
            }
        }

        // Missions
        for index in profile.daily.missions.indices {
            let wasComplete = profile.daily.missions[index].isComplete
            profile.daily.missions[index].apply(run)
            if !wasComplete && profile.daily.missions[index].isComplete {
                rewards.missionsReady.append(profile.daily.missions[index].id)
            }
        }

        // Coins & XP
        let earned = coins(for: run)
        rewards.coinsFromStars = earned.stars
        rewards.coinsFromScore = earned.score
        grant(earned.stars + earned.score, to: &profile)

        rewards.xpGained = xp(for: run)
        profile.xp += rewards.xpGained
        rewards.levelAfter = profile.level
        if rewards.levelAfter > rewards.levelBefore {
            let bonus = ((rewards.levelBefore + 1)...rewards.levelAfter).reduce(0) { $0 + Leveling.levelUpReward(for: $1) }
            rewards.coinsFromLevelUps = bonus
            grant(bonus, to: &profile)
        }
        rewards.newCosmetics.append(contentsOf: unlockLevelCosmetics(&profile))

        // Achievements
        let unlocked = evaluateAchievements(&profile, now: now)
        rewards.newAchievements = unlocked.map(\.id)
        rewards.coinsFromAchievements = unlocked.reduce(0) { $0 + $1.reward }
        rewards.newCosmetics.append(contentsOf: unlocked.compactMap { $0.cosmeticReward?.id })

        return rewards
    }

    static func updateStats(_ stats: inout PlayerStats, with run: RunResult, dailyAttemptsBefore: Int, isTodaysDaily: Bool) {
        let seconds = run.survivedSeconds
        stats.gamesPlayed += 1
        stats.totalTimeSurvived += seconds
        stats.totalStars += run.stars
        stats.totalNearMisses += run.nearMisses
        stats.totalPerfectMisses += run.perfectMisses
        stats.totalDodged += run.dodged
        stats.totalPowerUps += run.powerUps
        stats.bestStarsInRun = max(stats.bestStarsInRun, run.stars)
        stats.bestNearMissesInRun = max(stats.bestNearMissesInRun, run.nearMisses)
        stats.bestMultiplier = max(stats.bestMultiplier, run.maxMultiplier)
        stats.bestCombo = max(stats.bestCombo, run.maxCombo)
        stats.bestNovaClear = max(stats.bestNovaClear, run.bestNovaClear)

        switch run.mode {
        case .endless:
            stats.endlessGames += 1
            stats.bestEndlessScore = max(stats.bestEndlessScore, run.score)
            stats.bestEndlessTime = max(stats.bestEndlessTime, seconds)
            stats.bestLevel = max(stats.bestLevel, run.levelReached)
            if run.powerUps == 0 { stats.bestPuristTime = max(stats.bestPuristTime, seconds) }
            if run.difficulty == .expert { stats.bestExpertTime = max(stats.bestExpertTime, seconds) }
            if seconds >= 30 {
                stats.currentSurvivalStreak += 1
                stats.longestSurvivalStreak = max(stats.longestSurvivalStreak, stats.currentSurvivalStreak)
            } else {
                stats.currentSurvivalStreak = 0
            }
        case .timeAttack:
            stats.timeAttackGames += 1
            stats.bestTimeAttackScore = max(stats.bestTimeAttackScore, run.score)
        case .daily:
            stats.dailyGames += 1
            stats.bestDailyScore = max(stats.bestDailyScore, run.score)
            stats.bestLevel = max(stats.bestLevel, run.levelReached)
            if isTodaysDaily && dailyAttemptsBefore == 0 {
                stats.dailyRunsCompleted += 1
            }
        case .zen:
            stats.zenGames += 1
        }
    }

    /// Inserts into a top-N board. Returns the 1-based rank if it made the board.
    static func insert(_ run: RunResult, into board: inout [LeaderboardEntry]) -> Int? {
        let entry = LeaderboardEntry(score: run.score, duration: run.duration, date: run.date,
                                     difficulty: run.difficulty, modifier: run.modifier)
        board.append(entry)
        board.sort { $0.score != $1.score ? $0.score > $1.score : $0.date < $1.date }
        if board.count > leaderboardSize { board.removeLast(board.count - leaderboardSize) }
        return board.firstIndex { $0.id == entry.id }.map { $0 + 1 }
    }

    // MARK: Achievements & unlocks

    /// Unlocks every achievement whose goal is met. Grants coins and cosmetics.
    @discardableResult
    static func evaluateAchievements(_ profile: inout PlayerProfile, now: Date) -> [AchievementDefinition] {
        var unlocked: [AchievementDefinition] = []
        for definition in AchievementCatalog.all where profile.unlockedAchievements[definition.id] == nil {
            guard definition.isMet(by: profile.stats) else { continue }
            profile.unlockedAchievements[definition.id] = now
            grant(definition.reward, to: &profile)
            if let cosmetic = definition.cosmeticReward {
                profile.unlockedCosmetics.insert(cosmetic.id)
            }
            unlocked.append(definition)
        }
        return unlocked
    }

    static func unlockLevelCosmetics(_ profile: inout PlayerProfile) -> [String] {
        let level = profile.level
        var newlyUnlocked: [String] = []
        for cosmetic in Cosmetic.catalog {
            if case .level(let required) = cosmetic.requirement,
               level >= required,
               !profile.unlockedCosmetics.contains(cosmetic.id) {
                profile.unlockedCosmetics.insert(cosmetic.id)
                newlyUnlocked.append(cosmetic.id)
            }
        }
        return newlyUnlocked
    }

    static func grant(_ coins: Int, to profile: inout PlayerProfile) {
        guard coins > 0 else { return }
        profile.coins += coins
        profile.stats.totalCoinsEarned += coins
    }

    // MARK: Shop

    static func purchase(_ cosmeticID: String, profile: inout PlayerProfile, now: Date) -> PurchaseResult {
        guard let cosmetic = Cosmetic.find(cosmeticID) else { return .locked }
        guard !profile.isUnlocked(cosmetic) else { return .alreadyOwned }
        guard let price = cosmetic.price else { return .locked }
        guard profile.coins >= price else { return .notEnoughCoins(needed: price - profile.coins) }
        profile.coins -= price
        profile.unlockedCosmetics.insert(cosmetic.id)
        profile.stats.cosmeticsPurchased += 1
        equip(cosmetic.id, profile: &profile)
        evaluateAchievements(&profile, now: now)
        return .success
    }

    @discardableResult
    static func equip(_ cosmeticID: String, profile: inout PlayerProfile) -> Bool {
        guard let cosmetic = Cosmetic.find(cosmeticID), profile.isUnlocked(cosmetic) else { return false }
        switch cosmetic.category {
        case .skin: profile.equippedSkin = cosmetic.id
        case .trail: profile.equippedTrail = cosmetic.id
        case .theme: profile.equippedTheme = cosmetic.id
        }
        return true
    }

    // MARK: Missions

    /// Claims a completed mission. Returns coins granted.
    @discardableResult
    static func claimMission(_ missionID: String, profile: inout PlayerProfile, now: Date) -> Int {
        guard let index = profile.daily.missions.firstIndex(where: { $0.id == missionID }) else { return 0 }
        let mission = profile.daily.missions[index]
        guard mission.isComplete, !mission.claimed else { return 0 }
        profile.daily.missions[index].claimed = true
        profile.stats.missionsCompleted += 1
        grant(mission.reward, to: &profile)
        evaluateAchievements(&profile, now: now)
        return mission.reward
    }

    static func isAllMissionsBonusAvailable(_ profile: PlayerProfile) -> Bool {
        !profile.daily.missions.isEmpty
            && profile.daily.missions.allSatisfy(\.claimed)
            && !profile.daily.allMissionsBonusClaimed
    }

    @discardableResult
    static func claimAllMissionsBonus(_ profile: inout PlayerProfile) -> Int {
        guard isAllMissionsBonusAvailable(profile) else { return 0 }
        profile.daily.allMissionsBonusClaimed = true
        grant(MissionGenerator.allCompleteBonus, to: &profile)
        return MissionGenerator.allCompleteBonus
    }

    static func claimableMissionCount(_ profile: PlayerProfile) -> Int {
        profile.daily.missions.filter { $0.isComplete && !$0.claimed }.count
            + (isAllMissionsBonusAvailable(profile) ? 1 : 0)
    }
}
