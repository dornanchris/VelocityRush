//
//  PlayerProfile.swift
//  StarshowerRun
//
//  All persistent player data in one Codable value.
//

import Foundation

struct PlayerStats: Codable, Equatable {
    // Lifetime totals
    var gamesPlayed = 0
    var endlessGames = 0
    var timeAttackGames = 0
    var dailyGames = 0
    var zenGames = 0
    var totalTimeSurvived = 0
    var totalStars = 0
    var totalNearMisses = 0
    var totalPerfectMisses = 0
    var totalDodged = 0
    var totalPowerUps = 0
    var totalCoinsEarned = 0
    var missionsCompleted = 0
    var dailyRunsCompleted = 0
    var cosmeticsPurchased = 0

    // Personal bests
    var bestEndlessScore = 0
    var bestEndlessTime = 0
    var bestTimeAttackScore = 0
    var bestDailyScore = 0
    var bestStarsInRun = 0
    var bestNearMissesInRun = 0
    var bestMultiplier = 1
    var bestCombo = 0
    var bestNovaClear = 0
    var bestPuristTime = 0
    var bestExpertTime = 0
    var bestLevel = 1

    // Streaks
    var currentSurvivalStreak = 0
    var longestSurvivalStreak = 0
    var loginStreak = 0
    var longestLoginStreak = 0
    /// Days on which every daily mission was cleared.
    var allClearDays = 0
    var allClearStreak = 0
    var longestAllClearStreak = 0

    /// Number of distinct modes played (for the Explorer achievement).
    var modesPlayed: Int {
        [endlessGames, timeAttackGames, dailyGames, zenGames].filter { $0 > 0 }.count
    }

    func best(for mode: GameMode) -> Int {
        switch mode {
        case .endless: return bestEndlessScore
        case .timeAttack: return bestTimeAttackScore
        case .daily: return bestDailyScore
        case .zen: return 0
        }
    }
}

struct LeaderboardEntry: Codable, Equatable, Identifiable {
    var id: UUID = UUID()
    var score: Int
    var duration: Double
    var date: Date
    var difficulty: Difficulty
    var modifier: RunModifier
}

struct DailyState: Codable, Equatable {
    /// Day these missions belong to, e.g. "2026-09-29".
    var dayKey: String = ""
    var missions: [MissionProgress] = []
    /// True once all three missions are claimed (the all-clear bonus is paid automatically).
    var allMissionsBonusClaimed = false
    var dailyRunBest = 0
    var dailyRunAttempts = 0
    var dailyRunLeaderboard: [LeaderboardEntry] = []
    /// Last day the login reward was claimed.
    var lastLoginRewardDay: String = ""
}

struct PlayerProfile: Codable, Equatable {
    static let currentVersion = 3

    var version = PlayerProfile.currentVersion
    var playerName = "Velocity Runner"
    var coins = 0
    var xp = 0
    var stats = PlayerStats()

    var unlockedCosmetics: Set<String> = Cosmetic.defaultUnlocked
    var equippedSkin = Cosmetic.defaultSkin
    var equippedTrail = Cosmetic.defaultTrail
    var equippedTheme = Cosmetic.defaultTheme

    /// Achievement id → unlock date.
    var unlockedAchievements: [String: Date] = [:]

    var leaderboards: [String: [LeaderboardEntry]] = [:]
    var daily = DailyState()
    /// Last day every mission was cleared (for the all-clear streak).
    var lastAllClearDay: String = ""

    var level: Int { Leveling.level(forXP: xp) }

    func isUnlocked(_ cosmetic: Cosmetic) -> Bool { unlockedCosmetics.contains(cosmetic.id) }

    func localLeaderboard(for mode: GameMode) -> [LeaderboardEntry] {
        mode == .daily ? daily.dailyRunLeaderboard : (leaderboards[mode.rawValue] ?? [])
    }
}

// MARK: - Leveling

enum Leveling {
    static let maxLevel = 99

    /// XP needed to go from `level` to `level + 1`.
    static func xpToAdvance(from level: Int) -> Int {
        100 + (level - 1) * 60 + Int(pow(Double(level - 1), 1.6) * 8)
    }

    /// Total XP needed to reach `level`.
    static func totalXP(forLevel level: Int) -> Int {
        guard level > 1 else { return 0 }
        return (1..<level).reduce(0) { $0 + xpToAdvance(from: $1) }
    }

    static func level(forXP xp: Int) -> Int {
        var level = 1
        var remaining = xp
        while level < maxLevel {
            let needed = xpToAdvance(from: level)
            if remaining < needed { break }
            remaining -= needed
            level += 1
        }
        return level
    }

    /// Progress (0...1) through the current level.
    static func progress(forXP xp: Int) -> Double {
        let level = level(forXP: xp)
        guard level < maxLevel else { return 1 }
        let base = totalXP(forLevel: level)
        return Double(xp - base) / Double(xpToAdvance(from: level))
    }

    static func xpIntoLevel(forXP xp: Int) -> (current: Int, needed: Int) {
        let level = level(forXP: xp)
        return (xp - totalXP(forLevel: level), xpToAdvance(from: level))
    }

    /// Coins granted when reaching `level`.
    static func levelUpReward(for level: Int) -> Int { 20 + level * 5 }
}
