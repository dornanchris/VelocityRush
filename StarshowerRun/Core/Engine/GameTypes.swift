//
//  GameTypes.swift
//  StarshowerRun
//
//  Modes, difficulties, modifiers and entity kinds shared by the engine,
//  the renderer and the progression systems.
//

import Foundation

// MARK: - Game Mode

enum GameMode: String, Codable, CaseIterable, Identifiable {
    case endless
    case timeAttack
    case daily
    case zen

    var id: String { rawValue }

    var title: String {
        switch self {
        case .endless: return "Endless"
        case .timeAttack: return "Time Attack"
        case .daily: return "Daily Run"
        case .zen: return "Zen"
        }
    }

    var tagline: String {
        switch self {
        case .endless: return "One life. How long can you last?"
        case .timeAttack: return "60 seconds. Grab every star."
        case .daily: return "Same run for everyone, every day."
        case .zen: return "No score. No pressure. Just flow."
        }
    }

    var rules: [String] {
        switch self {
        case .endless:
            return [
                "Dodge everything. One hit ends the run.",
                "Near misses and stars build your multiplier.",
                "The rush speeds up every 15 seconds."
            ]
        case .timeAttack:
            return [
                "You have 60 seconds on the clock.",
                "Stars are worth big points. Crystals add +3s.",
                "Getting hit costs you 5 seconds."
            ]
        case .daily:
            return [
                "A hand-picked twist changes the rules each day.",
                "Everyone gets the exact same hazards.",
                "Your best score today goes on the board."
            ]
        case .zen:
            return [
                "Hits never end the run.",
                "Collect stars for coins at your own pace.",
                "Perfect for warming up or winding down."
            ]
        }
    }

    /// SF Symbol name.
    var icon: String {
        switch self {
        case .endless: return "infinity"
        case .timeAttack: return "stopwatch.fill"
        case .daily: return "calendar.badge.clock"
        case .zen: return "leaf.fill"
        }
    }

    var accent: RGBColor {
        switch self {
        case .endless: return RGBColor(hex: 0xFF3D6E)
        case .timeAttack: return RGBColor(hex: 0xFFB800)
        case .daily: return RGBColor(hex: 0x38E1FF)
        case .zen: return RGBColor(hex: 0x6BFF9E)
        }
    }

    /// Ranked modes have leaderboards and personal bests.
    var isRanked: Bool { self != .zen }

    /// Game Center leaderboard identifier (configure these in App Store Connect).
    var leaderboardID: String? {
        switch self {
        case .endless: return "vr.leaderboard.endless"
        case .timeAttack: return "vr.leaderboard.timeattack"
        case .daily: return "vr.leaderboard.daily"
        case .zen: return nil
        }
    }

    var usesClock: Bool { self == .timeAttack }
    var hasSingleLife: Bool { self == .endless || self == .daily }
}

// MARK: - Difficulty

enum Difficulty: Int, Codable, CaseIterable, Identifiable {
    case easy = 0
    case normal = 1
    case hard = 2
    case expert = 3

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .easy: return "Easy"
        case .normal: return "Normal"
        case .hard: return "Hard"
        case .expert: return "Expert"
        }
    }

    var speedMultiplier: Double {
        switch self {
        case .easy: return 0.82
        case .normal: return 1.0
        case .hard: return 1.14
        case .expert: return 1.28
        }
    }

    /// Multiplies the spawn interval (lower = more hazards).
    var spawnIntervalMultiplier: Double {
        switch self {
        case .easy: return 1.25
        case .normal: return 1.0
        case .hard: return 0.86
        case .expert: return 0.74
        }
    }

    var scoreMultiplier: Double {
        switch self {
        case .easy: return 0.75
        case .normal: return 1.0
        case .hard: return 1.3
        case .expert: return 1.6
        }
    }
}

// MARK: - Run Modifier (Daily twists)

enum RunModifier: String, Codable, CaseIterable, Identifiable {
    case none
    case giantDots
    case speedDemon
    case starStorm
    case wobbleWorld
    case tinyHero
    case blackout
    case meteorShower
    case purist

    var id: String { rawValue }

    static var dailyPool: [RunModifier] { allCases.filter { $0 != .none } }

    var title: String {
        switch self {
        case .none: return "Standard"
        case .giantDots: return "Giant Dots"
        case .speedDemon: return "Speed Demon"
        case .starStorm: return "Star Storm"
        case .wobbleWorld: return "Wobble World"
        case .tinyHero: return "Tiny Hero"
        case .blackout: return "Blackout"
        case .meteorShower: return "Meteor Shower"
        case .purist: return "Purist"
        }
    }

    var detail: String {
        switch self {
        case .none: return "No twist today."
        case .giantDots: return "Every hazard is 50% bigger."
        case .speedDemon: return "Everything falls 35% faster."
        case .starStorm: return "Stars rain from the sky. Greed is dangerous."
        case .wobbleWorld: return "Every hazard sways side to side."
        case .tinyHero: return "You're tiny, but the sky is crowded."
        case .blackout: return "The lights are out. You only see what's near you."
        case .meteorShower: return "Blazing meteors from the very first second."
        case .purist: return "No power-ups. Just you and your reflexes."
        }
    }

    var icon: String {
        switch self {
        case .none: return "circle"
        case .giantDots: return "circle.circle.fill"
        case .speedDemon: return "hare.fill"
        case .starStorm: return "sparkles"
        case .wobbleWorld: return "water.waves"
        case .tinyHero: return "smallcircle.filled.circle"
        case .blackout: return "moon.fill"
        case .meteorShower: return "flame.fill"
        case .purist: return "hand.raised.fill"
        }
    }

    var scoreMultiplier: Double {
        switch self {
        case .none: return 1.0
        case .giantDots: return 1.2
        case .speedDemon: return 1.3
        case .starStorm: return 0.9
        case .wobbleWorld: return 1.15
        case .tinyHero: return 1.1
        case .blackout: return 1.4
        case .meteorShower: return 1.25
        case .purist: return 1.2
        }
    }
}

// MARK: - Run Configuration

struct RunConfig: Identifiable, Equatable {
    let id: UUID
    let mode: GameMode
    let difficulty: Difficulty
    let modifier: RunModifier
    let seed: UInt64
    /// Set for Daily Runs, e.g. "2026-09-29".
    let dailyKey: String?

    init(mode: GameMode,
         difficulty: Difficulty,
         modifier: RunModifier = .none,
         seed: UInt64 = UInt64.random(in: 1...UInt64.max),
         dailyKey: String? = nil) {
        self.id = UUID()
        self.mode = mode
        self.difficulty = difficulty
        self.modifier = modifier
        self.seed = seed
        self.dailyKey = dailyKey
    }

    /// Returns a fresh config for "Play Again" – same rules, same seed for dailies.
    func replay() -> RunConfig {
        RunConfig(mode: mode,
                  difficulty: difficulty,
                  modifier: modifier,
                  seed: mode == .daily ? seed : UInt64.random(in: 1...UInt64.max),
                  dailyKey: dailyKey)
    }

    var scoreMultiplier: Double { difficulty.scoreMultiplier * modifier.scoreMultiplier }
}

// MARK: - Entities

enum HazardKind: String, Codable, CaseIterable {
    /// Plain falling dot.
    case drop
    /// Small, very fast meteor that is telegraphed by a warning.
    case speeder
    /// Sways left and right while falling.
    case wobbler
    /// Huge and slow.
    case giant
    /// Splits into two fragments halfway down.
    case splitter
    /// Fragment produced by a splitter.
    case fragment
}

enum PickupKind: String, Codable, CaseIterable {
    case star
    case shield
    case slowMo
    case magnet
    case shrink
    case nova
    case timeCrystal

    var isPowerUp: Bool {
        switch self {
        case .star, .timeCrystal: return false
        default: return true
        }
    }

    var title: String {
        switch self {
        case .star: return "Star"
        case .shield: return "Shield"
        case .slowMo: return "Slow-Mo"
        case .magnet: return "Magnet"
        case .shrink: return "Shrink"
        case .nova: return "Nova"
        case .timeCrystal: return "+3s"
        }
    }

    var icon: String {
        switch self {
        case .star: return "star.fill"
        case .shield: return "shield.fill"
        case .slowMo: return "tortoise.fill"
        case .magnet: return "dot.radiowaves.left.and.right"
        case .shrink: return "arrow.down.right.and.arrow.up.left"
        case .nova: return "burst.fill"
        case .timeCrystal: return "hourglass"
        }
    }

    var color: RGBColor {
        switch self {
        case .star: return RGBColor(hex: 0xFFD84D)
        case .shield: return RGBColor(hex: 0x4DA6FF)
        case .slowMo: return RGBColor(hex: 0xB07CFF)
        case .magnet: return RGBColor(hex: 0xFF6BD6)
        case .shrink: return RGBColor(hex: 0x5CFFB0)
        case .nova: return RGBColor(hex: 0xFF8A3D)
        case .timeCrystal: return RGBColor(hex: 0x3DF5FF)
        }
    }
}

/// Power-ups that stay active for a duration.
enum TimedPowerUp: String, Codable, CaseIterable {
    case slowMo
    case magnet
    case shrink

    var duration: Double {
        switch self {
        case .slowMo: return 5.0
        case .magnet: return 8.0
        case .shrink: return 7.0
        }
    }

    var pickup: PickupKind {
        switch self {
        case .slowMo: return .slowMo
        case .magnet: return .magnet
        case .shrink: return .shrink
        }
    }
}
