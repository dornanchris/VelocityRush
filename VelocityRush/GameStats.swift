//
//  GameStats.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//


//
//  GameDataManager.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//

import Foundation

// MARK: - Game Statistics
struct GameStats: Codable {
    var totalTimeSurvived: TimeInterval = 0
    var totalObjectsDodged: Int = 0
    var totalStarsCollected: Int = 0
    var totalGamesPlayed: Int = 0
    var bestSurvivalTime: TimeInterval = 0
    var totalPurchasesMade: Int = 0
    var longestStreak: Int = 0
    
    // Session stats (not persistent)
    var currentStreak: Int = 0
}

// MARK: - Achievement Definition
struct Achievement: Identifiable, Codable {
    let id: String
    let title: String
    let description: String
    let icon: String
    let requirement: Int
    var isUnlocked: Bool = false
    var progress: Int = 0
    let category: AchievementCategory
    
    enum AchievementCategory: String, Codable, CaseIterable {
        case survival = "Survival"
        case dodging = "Dodging"
        case collection = "Collection"
        case milestone = "Milestone"
        case purchase = "Purchase"
    }
    
    var progressPercentage: Double {
        return min(Double(progress) / Double(requirement), 1.0)
    }
}

// MARK: - Game Data Manager
class GameDataManager: ObservableObject {
    static let shared = GameDataManager()
    
    @Published var stats = GameStats()
    @Published var achievements: [Achievement] = []
    
    private let statsKey = "VelocityRushStats"
    private let achievementsKey = "VelocityRushAchievements"
    
    init() {
        loadStats()
        initializeAchievements()
        loadAchievements()
    }
    
    // MARK: - Stats Management
    func loadStats() {
        if let data = UserDefaults.standard.data(forKey: statsKey),
           let decodedStats = try? JSONDecoder().decode(GameStats.self, from: data) {
            stats = decodedStats
        }
    }
    
    func saveStats() {
        if let data = try? JSONEncoder().encode(stats) {
            UserDefaults.standard.set(data, forKey: statsKey)
        }
    }
    
    // MARK: - Game Events
    func gameStarted() {
        stats.totalGamesPlayed += 1
        saveStats()
    }
    
    func gameEnded(survivalTime: TimeInterval, objectsDodged: Int) {
        stats.totalTimeSurvived += survivalTime
        stats.totalObjectsDodged += objectsDodged
        
        if survivalTime > stats.bestSurvivalTime {
            stats.bestSurvivalTime = survivalTime
        }
        
        // Update current streak
        if survivalTime >= 30 { // Survived at least 30 seconds
            stats.currentStreak += 1
            if stats.currentStreak > stats.longestStreak {
                stats.longestStreak = stats.currentStreak
            }
        } else {
            stats.currentStreak = 0
        }
        
        saveStats()
        checkAchievements()
    }
    
    func starsCollected(_ count: Int) {
        stats.totalStarsCollected += count
        saveStats()
        checkAchievements()
    }
    
    func purchaseMade() {
        stats.totalPurchasesMade += 1
        saveStats()
        checkAchievements()
    }
    
    // MARK: - Achievements
    func initializeAchievements() {
        achievements = [
            // Survival Achievements
            Achievement(id: "survive_30", title: "First Steps", description: "Survive for 30 seconds", icon: "timer", requirement: 30, category: .survival),
            Achievement(id: "survive_60", title: "One Minute", description: "Survive for 1 minute", icon: "clock", requirement: 60, category: .survival),
            Achievement(id: "survive_90", title: "Master Dodger", description: "Survive for 1.5 minutes", icon: "crown", requirement: 90, category: .survival),
            Achievement(id: "survive_120", title: "Legendary", description: "Survive for 2 minutes", icon: "star.circle", requirement: 120, category: .survival),
            
            // Total Survival Time
            Achievement(id: "total_5min", title: "5 Minutes Total", description: "Survive 5 minutes total across all games", icon: "stopwatch", requirement: 300, category: .survival),
            Achievement(id: "total_30min", title: "Half Hour Hero", description: "Survive 30 minutes total", icon: "clock.arrow.circlepath", requirement: 1800, category: .survival),
            Achievement(id: "total_1hour", title: "Hour Master", description: "Survive 1 hour total", icon: "hourglass", requirement: 3600, category: .survival),
            
            // Dodging Achievements
            Achievement(id: "dodge_100", title: "Dodger", description: "Dodge 100 objects", icon: "shield", requirement: 100, category: .dodging),
            Achievement(id: "dodge_500", title: "Evasive", description: "Dodge 500 objects", icon: "shield.lefthalf.filled", requirement: 500, category: .dodging),
            Achievement(id: "dodge_1000", title: "Untouchable", description: "Dodge 1000 objects", icon: "shield.fill", requirement: 1000, category: .dodging),
            Achievement(id: "dodge_5000", title: "Neo", description: "Dodge 5000 objects", icon: "person.fill.checkmark", requirement: 5000, category: .dodging),
            
            // Collection Achievements (for future stars feature)
            Achievement(id: "stars_50", title: "Star Collector", description: "Collect 50 stars", icon: "star", requirement: 50, category: .collection),
            Achievement(id: "stars_200", title: "Stellar", description: "Collect 200 stars", icon: "star.fill", requirement: 200, category: .collection),
            Achievement(id: "stars_1000", title: "Cosmic", description: "Collect 1000 stars", icon: "sparkles", requirement: 1000, category: .collection),
            
            // Milestone Achievements
            Achievement(id: "games_10", title: "Getting Started", description: "Play 10 games", icon: "gamecontroller", requirement: 10, category: .milestone),
            Achievement(id: "games_50", title: "Dedicated", description: "Play 50 games", icon: "gamecontroller.fill", requirement: 50, category: .milestone),
            Achievement(id: "games_100", title: "Addicted", description: "Play 100 games", icon: "flame", requirement: 100, category: .milestone),
            
            // Streak Achievements
            Achievement(id: "streak_3", title: "On Fire", description: "Win 3 games in a row (30+ seconds)", icon: "flame.fill", requirement: 3, category: .survival),
            Achievement(id: "streak_5", title: "Unstoppable", description: "Win 5 games in a row", icon: "bolt.fill", requirement: 5, category: .survival),
            Achievement(id: "streak_10", title: "Legendary Streak", description: "Win 10 games in a row", icon: "crown.fill", requirement: 10, category: .survival),
            
            // Purchase Achievements (for future store)
            Achievement(id: "purchase_1", title: "First Purchase", description: "Make your first store purchase", icon: "cart", requirement: 1, category: .purchase),
            Achievement(id: "purchase_5", title: "Shopaholic", description: "Make 5 store purchases", icon: "cart.fill", requirement: 5, category: .purchase),
        ]
    }
    
    func loadAchievements() {
        if let data = UserDefaults.standard.data(forKey: achievementsKey),
           let savedAchievements = try? JSONDecoder().decode([Achievement].self, from: data) {
            
            // Merge saved progress with current achievement definitions
            for (index, achievement) in achievements.enumerated() {
                if let saved = savedAchievements.first(where: { $0.id == achievement.id }) {
                    achievements[index].isUnlocked = saved.isUnlocked
                    achievements[index].progress = saved.progress
                }
            }
        }
    }
    
    func saveAchievements() {
        if let data = try? JSONEncoder().encode(achievements) {
            UserDefaults.standard.set(data, forKey: achievementsKey)
        }
    }
    
    func checkAchievements() {
        var hasNewAchievement = false
        
        for (index, achievement) in achievements.enumerated() {
            guard !achievement.isUnlocked else { continue }
            
            var currentProgress = 0
            
            switch achievement.id {
            // Survival time achievements (single game)
            case "survive_30": currentProgress = Int(stats.bestSurvivalTime)
            case "survive_60": currentProgress = Int(stats.bestSurvivalTime)
            case "survive_90": currentProgress = Int(stats.bestSurvivalTime)
            case "survive_120": currentProgress = Int(stats.bestSurvivalTime)
            
            // Total survival time
            case "total_5min": currentProgress = Int(stats.totalTimeSurvived)
            case "total_30min": currentProgress = Int(stats.totalTimeSurvived)
            case "total_1hour": currentProgress = Int(stats.totalTimeSurvived)
            
            // Dodging
            case "dodge_100", "dodge_500", "dodge_1000", "dodge_5000":
                currentProgress = stats.totalObjectsDodged
            
            // Stars (future feature)
            case "stars_50", "stars_200", "stars_1000":
                currentProgress = stats.totalStarsCollected
            
            // Games played
            case "games_10", "games_50", "games_100":
                currentProgress = stats.totalGamesPlayed
            
            // Streaks
            case "streak_3", "streak_5", "streak_10":
                currentProgress = stats.longestStreak
            
            // Purchases
            case "purchase_1", "purchase_5":
                currentProgress = stats.totalPurchasesMade
            
            default: break
            }
            
            achievements[index].progress = currentProgress
            
            if currentProgress >= achievement.requirement {
                achievements[index].isUnlocked = true
                hasNewAchievement = true
                // TODO: Show achievement notification
            }
        }
        
        if hasNewAchievement {
            saveAchievements()
        }
    }
    
    // MARK: - Helper Methods
    func formatTime(_ timeInterval: TimeInterval) -> String {
        let hours = Int(timeInterval) / 3600
        let minutes = Int(timeInterval) % 3600 / 60
        let seconds = Int(timeInterval) % 60
        
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "%d:%02d", minutes, seconds)
        }
    }
    
    func getUnlockedAchievements() -> [Achievement] {
        return achievements.filter { $0.isUnlocked }
    }
    
    func getAchievementsByCategory(_ category: Achievement.AchievementCategory) -> [Achievement] {
        return achievements.filter { $0.category == category }
    }
}