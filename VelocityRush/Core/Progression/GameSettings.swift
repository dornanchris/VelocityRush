//
//  GameSettings.swift
//  VelocityRush
//
//  UserDefaults keys shared by @AppStorage in the UI and the game scene.
//

import Foundation

enum SettingsKey {
    static let soundEnabled = "soundEnabled"
    static let musicEnabled = "musicEnabled"
    static let hapticsEnabled = "hapticEnabled"
    static let showFPS = "showFPS"
    static let difficulty = "difficulty"
    static let sensitivity = "controlSensitivity"
    static let colorBlindMode = "colorBlindMode"
    static let highContrast = "highContrast"
    static let reducedMotion = "reducedMotion"
    static let notifications = "notifications"
    static let hasSeenTutorial = "hasSeenTutorial"
}

/// Snapshot of the settings the game scene cares about.
struct GameSettings: Equatable {
    var soundEnabled = true
    var musicEnabled = true
    var hapticsEnabled = true
    var showFPS = false
    var difficulty: Difficulty = .normal
    var sensitivity: Double = 1.2
    var colorBlindMode = false
    var highContrast = false
    var reducedMotion = false

    static func load(from defaults: UserDefaults = .standard) -> GameSettings {
        var settings = GameSettings()
        settings.soundEnabled = defaults.object(forKey: SettingsKey.soundEnabled) as? Bool ?? true
        settings.musicEnabled = defaults.object(forKey: SettingsKey.musicEnabled) as? Bool ?? true
        settings.hapticsEnabled = defaults.object(forKey: SettingsKey.hapticsEnabled) as? Bool ?? true
        settings.showFPS = defaults.bool(forKey: SettingsKey.showFPS)
        settings.difficulty = Difficulty(rawValue: defaults.object(forKey: SettingsKey.difficulty) as? Int ?? 1) ?? .normal
        settings.sensitivity = defaults.object(forKey: SettingsKey.sensitivity) as? Double ?? 1.2
        settings.colorBlindMode = defaults.bool(forKey: SettingsKey.colorBlindMode)
        settings.highContrast = defaults.bool(forKey: SettingsKey.highContrast)
        settings.reducedMotion = defaults.bool(forKey: SettingsKey.reducedMotion)
        return settings
    }
}
