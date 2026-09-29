//
//  RunResult.swift
//  VelocityRush
//

import Foundation

/// Everything that happened in a finished run. Feeds stats, achievements,
/// missions, leaderboards and rewards.
struct RunResult: Codable, Equatable {
    var mode: GameMode
    var difficulty: Difficulty
    var modifier: RunModifier
    var dailyKey: String?
    var score: Int
    var duration: Double
    var stars: Int
    var nearMisses: Int
    var perfectMisses: Int
    var dodged: Int
    var powerUps: Int
    var hits: Int
    var maxCombo: Int
    var maxMultiplier: Int
    var bestNovaClear: Int
    var levelReached: Int
    var date: Date

    var survivedSeconds: Int { Int(duration) }
}
