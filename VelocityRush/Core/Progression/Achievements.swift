//
//  Achievements.swift
//  VelocityRush
//
//  Every achievement is "some stat ≥ goal", which keeps evaluation trivial
//  and progress bars accurate.
//

import Foundation

enum AchievementCategory: String, CaseIterable, Identifiable {
    case survival = "Survival"
    case score = "Score"
    case skill = "Skill"
    case collection = "Collection"
    case dedication = "Dedication"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .survival: return "heart.fill"
        case .score: return "chart.line.uptrend.xyaxis"
        case .skill: return "scope"
        case .collection: return "star.fill"
        case .dedication: return "flame.fill"
        }
    }
}

enum AchievementTier: Int, Comparable {
    case bronze, silver, gold, platinum

    static func < (lhs: AchievementTier, rhs: AchievementTier) -> Bool { lhs.rawValue < rhs.rawValue }

    var title: String {
        switch self {
        case .bronze: return "Bronze"
        case .silver: return "Silver"
        case .gold: return "Gold"
        case .platinum: return "Platinum"
        }
    }

    var color: RGBColor {
        switch self {
        case .bronze: return RGBColor(hex: 0xD08B4E)
        case .silver: return RGBColor(hex: 0xC9D3E0)
        case .gold: return RGBColor(hex: 0xFFD84D)
        case .platinum: return RGBColor(hex: 0x9BF6FF)
        }
    }
}

struct AchievementDefinition: Identifiable {
    let id: String
    let title: String
    let detail: String
    let icon: String
    let category: AchievementCategory
    let tier: AchievementTier
    let goal: Int
    let metric: KeyPath<PlayerStats, Int>
    let reward: Int
    /// Hidden achievements show "???" until unlocked.
    var secret: Bool = false

    /// Game Center achievement identifier.
    var gameCenterID: String { "vr.achievement.\(id)" }

    func progress(in stats: PlayerStats) -> Int { min(stats[keyPath: metric], goal) }

    func fraction(in stats: PlayerStats) -> Double {
        goal > 0 ? Double(progress(in: stats)) / Double(goal) : 1
    }

    func isMet(by stats: PlayerStats) -> Bool { stats[keyPath: metric] >= goal }

    /// Cosmetic unlocked by this achievement, if any.
    var cosmeticReward: Cosmetic? {
        Cosmetic.catalog.first { $0.requirement == .achievement(id) }
    }
}

enum AchievementCatalog {
    static func find(_ id: String) -> AchievementDefinition? { all.first { $0.id == id } }

    static let all: [AchievementDefinition] = [
        // MARK: Survival
        AchievementDefinition(id: "survive_30", title: "First Steps", detail: "Survive 30 seconds in Endless.",
                              icon: "figure.walk", category: .survival, tier: .bronze, goal: 30,
                              metric: \.bestEndlessTime, reward: 50),
        AchievementDefinition(id: "survive_60", title: "One Minute Wonder", detail: "Survive 60 seconds in Endless.",
                              icon: "clock.fill", category: .survival, tier: .bronze, goal: 60,
                              metric: \.bestEndlessTime, reward: 100),
        AchievementDefinition(id: "survive_90", title: "Master Dodger", detail: "Survive 90 seconds in Endless.",
                              icon: "crown.fill", category: .survival, tier: .silver, goal: 90,
                              metric: \.bestEndlessTime, reward: 200),
        AchievementDefinition(id: "survive_120", title: "Legendary", detail: "Survive 2 minutes in Endless.",
                              icon: "star.circle.fill", category: .survival, tier: .gold, goal: 120,
                              metric: \.bestEndlessTime, reward: 400),
        AchievementDefinition(id: "survive_180", title: "Untouchable", detail: "Survive 3 minutes in Endless.",
                              icon: "bolt.shield.fill", category: .survival, tier: .platinum, goal: 180,
                              metric: \.bestEndlessTime, reward: 1000),
        AchievementDefinition(id: "total_10min", title: "Time Traveller", detail: "Survive 10 minutes in total.",
                              icon: "hourglass", category: .survival, tier: .bronze, goal: 600,
                              metric: \.totalTimeSurvived, reward: 100),
        AchievementDefinition(id: "total_1hour", title: "Hour of Power", detail: "Survive 1 hour in total.",
                              icon: "hourglass.circle.fill", category: .survival, tier: .gold, goal: 3600,
                              metric: \.totalTimeSurvived, reward: 500),
        AchievementDefinition(id: "purist_60", title: "Purist", detail: "Survive 60s in Endless without power-ups.",
                              icon: "hand.raised.fill", category: .survival, tier: .silver, goal: 60,
                              metric: \.bestPuristTime, reward: 250),
        AchievementDefinition(id: "expert_60", title: "Expert Dodger", detail: "Survive 60s in Endless on Expert.",
                              icon: "flame.circle.fill", category: .survival, tier: .gold, goal: 60,
                              metric: \.bestExpertTime, reward: 400),
        AchievementDefinition(id: "streak_3", title: "On Fire", detail: "Survive 30s+ in 3 Endless runs in a row.",
                              icon: "flame.fill", category: .survival, tier: .silver, goal: 3,
                              metric: \.longestSurvivalStreak, reward: 150),
        AchievementDefinition(id: "level_8", title: "Into the Rush", detail: "Reach level 8 in a single run.",
                              icon: "gauge.with.dots.needle.67percent", category: .survival, tier: .gold, goal: 8,
                              metric: \.bestLevel, reward: 300),

        // MARK: Score
        AchievementDefinition(id: "endless_1k", title: "Warming Up", detail: "Score 1,000 in Endless.",
                              icon: "1.circle.fill", category: .score, tier: .bronze, goal: 1_000,
                              metric: \.bestEndlessScore, reward: 50),
        AchievementDefinition(id: "endless_5k", title: "High Roller", detail: "Score 5,000 in Endless.",
                              icon: "5.circle.fill", category: .score, tier: .silver, goal: 5_000,
                              metric: \.bestEndlessScore, reward: 200),
        AchievementDefinition(id: "endless_15k", title: "Score Attack", detail: "Score 15,000 in Endless.",
                              icon: "trophy.fill", category: .score, tier: .gold, goal: 15_000,
                              metric: \.bestEndlessScore, reward: 500),
        AchievementDefinition(id: "ta_5k", title: "Against the Clock", detail: "Score 5,000 in Time Attack.",
                              icon: "stopwatch", category: .score, tier: .bronze, goal: 5_000,
                              metric: \.bestTimeAttackScore, reward: 75),
        AchievementDefinition(id: "ta_15k", title: "Time Bandit", detail: "Score 15,000 in Time Attack.",
                              icon: "stopwatch.fill", category: .score, tier: .silver, goal: 15_000,
                              metric: \.bestTimeAttackScore, reward: 250),
        AchievementDefinition(id: "ta_35k", title: "Chrono Master", detail: "Score 35,000 in Time Attack.",
                              icon: "timer", category: .score, tier: .gold, goal: 35_000,
                              metric: \.bestTimeAttackScore, reward: 600),
        AchievementDefinition(id: "daily_3k", title: "Daily Driver", detail: "Score 3,000 in a Daily Run.",
                              icon: "calendar", category: .score, tier: .silver, goal: 3_000,
                              metric: \.bestDailyScore, reward: 200),

        // MARK: Skill
        AchievementDefinition(id: "nearmiss_run_15", title: "Thread the Needle", detail: "Get 15 near misses in one run.",
                              icon: "scope", category: .skill, tier: .silver, goal: 15,
                              metric: \.bestNearMissesInRun, reward: 200),
        AchievementDefinition(id: "nearmiss_100", title: "Daredevil", detail: "Get 100 near misses in total.",
                              icon: "wind", category: .skill, tier: .bronze, goal: 100,
                              metric: \.totalNearMisses, reward: 100),
        AchievementDefinition(id: "nearmiss_1000", title: "Adrenaline Junkie", detail: "Get 1,000 near misses in total.",
                              icon: "bolt.heart.fill", category: .skill, tier: .gold, goal: 1_000,
                              metric: \.totalNearMisses, reward: 500),
        AchievementDefinition(id: "perfect_50", title: "Pixel Perfect", detail: "Get 50 perfect misses.",
                              icon: "viewfinder", category: .skill, tier: .silver, goal: 50,
                              metric: \.totalPerfectMisses, reward: 250),
        AchievementDefinition(id: "multiplier_3", title: "Combo Starter", detail: "Reach a x3 multiplier.",
                              icon: "multiply.circle", category: .skill, tier: .bronze, goal: 3,
                              metric: \.bestMultiplier, reward: 75),
        AchievementDefinition(id: "multiplier_6", title: "Max Combo", detail: "Reach the x6 multiplier.",
                              icon: "multiply.circle.fill", category: .skill, tier: .gold, goal: 6,
                              metric: \.bestMultiplier, reward: 400),
        AchievementDefinition(id: "nova_12", title: "Supernova", detail: "Clear 12 hazards with one Nova.",
                              icon: "burst.fill", category: .skill, tier: .silver, goal: 12,
                              metric: \.bestNovaClear, reward: 200, secret: true),
        AchievementDefinition(id: "dodge_1000", title: "Evasive", detail: "Dodge 1,000 hazards.",
                              icon: "shield.lefthalf.filled", category: .skill, tier: .bronze, goal: 1_000,
                              metric: \.totalDodged, reward: 100),
        AchievementDefinition(id: "dodge_10000", title: "Neo", detail: "Dodge 10,000 hazards.",
                              icon: "figure.dance", category: .skill, tier: .platinum, goal: 10_000,
                              metric: \.totalDodged, reward: 800),

        // MARK: Collection
        AchievementDefinition(id: "stars_run_40", title: "Star Struck", detail: "Collect 40 stars in one run.",
                              icon: "star.leadinghalf.filled", category: .collection, tier: .silver, goal: 40,
                              metric: \.bestStarsInRun, reward: 150),
        AchievementDefinition(id: "stars_250", title: "Star Collector", detail: "Collect 250 stars.",
                              icon: "star", category: .collection, tier: .bronze, goal: 250,
                              metric: \.totalStars, reward: 100),
        AchievementDefinition(id: "stars_2500", title: "Stellar", detail: "Collect 2,500 stars.",
                              icon: "star.fill", category: .collection, tier: .gold, goal: 2_500,
                              metric: \.totalStars, reward: 500),
        AchievementDefinition(id: "powerups_50", title: "Powered Up", detail: "Collect 50 power-ups.",
                              icon: "bolt.fill", category: .collection, tier: .bronze, goal: 50,
                              metric: \.totalPowerUps, reward: 100),
        AchievementDefinition(id: "shop_1", title: "Fresh Look", detail: "Buy your first cosmetic.",
                              icon: "bag.fill", category: .collection, tier: .bronze, goal: 1,
                              metric: \.cosmeticsPurchased, reward: 50),
        AchievementDefinition(id: "shop_10", title: "Fashionista", detail: "Buy 10 cosmetics.",
                              icon: "tshirt.fill", category: .collection, tier: .gold, goal: 10,
                              metric: \.cosmeticsPurchased, reward: 400),

        // MARK: Dedication
        AchievementDefinition(id: "games_10", title: "Getting Started", detail: "Play 10 games.",
                              icon: "gamecontroller", category: .dedication, tier: .bronze, goal: 10,
                              metric: \.gamesPlayed, reward: 50),
        AchievementDefinition(id: "games_100", title: "Dedicated", detail: "Play 100 games.",
                              icon: "gamecontroller.fill", category: .dedication, tier: .silver, goal: 100,
                              metric: \.gamesPlayed, reward: 250),
        AchievementDefinition(id: "games_500", title: "Addicted", detail: "Play 500 games.",
                              icon: "infinity.circle.fill", category: .dedication, tier: .platinum, goal: 500,
                              metric: \.gamesPlayed, reward: 1000),
        AchievementDefinition(id: "explorer", title: "Explorer", detail: "Play every game mode.",
                              icon: "map.fill", category: .dedication, tier: .bronze, goal: 4,
                              metric: \.modesPlayed, reward: 100),
        AchievementDefinition(id: "daily_1", title: "Daily Habit", detail: "Complete a Daily Run.",
                              icon: "calendar.badge.checkmark", category: .dedication, tier: .bronze, goal: 1,
                              metric: \.dailyRunsCompleted, reward: 50),
        AchievementDefinition(id: "daily_30", title: "Creature of Habit", detail: "Complete 30 Daily Runs.",
                              icon: "calendar.circle.fill", category: .dedication, tier: .gold, goal: 30,
                              metric: \.dailyRunsCompleted, reward: 500),
        AchievementDefinition(id: "login_7", title: "Week Streak", detail: "Log in 7 days in a row.",
                              icon: "7.circle.fill", category: .dedication, tier: .silver, goal: 7,
                              metric: \.longestLoginStreak, reward: 250),
        AchievementDefinition(id: "missions_10", title: "Mission Ready", detail: "Complete 10 daily missions.",
                              icon: "checklist", category: .dedication, tier: .bronze, goal: 10,
                              metric: \.missionsCompleted, reward: 100),
        AchievementDefinition(id: "missions_100", title: "Special Agent", detail: "Complete 100 daily missions.",
                              icon: "checkmark.seal.fill", category: .dedication, tier: .gold, goal: 100,
                              metric: \.missionsCompleted, reward: 600)
    ]
}
