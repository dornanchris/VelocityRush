//
//  ProfileRepository.swift
//  StarshowerRun
//
//  Loads and saves the PlayerProfile, and migrates stats from the original
//  prototype so early players keep their progress.
//

import Foundation

struct ProfileRepository {
    static let profileKey = "vr.profile.v2"
    static let legacyStatsKey = "VelocityRushStats"

    let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> PlayerProfile {
        if let data = defaults.data(forKey: Self.profileKey),
           let profile = Self.decodeMergingDefaults(data) {
            return sanitized(migrated(profile))
        }
        var profile = PlayerProfile()
        migrateLegacy(into: &profile)
        return profile
    }

    func save(_ profile: PlayerProfile) {
        if let data = try? JSONEncoder().encode(profile) {
            defaults.set(data, forKey: Self.profileKey)
        }
    }

    func reset() {
        defaults.removeObject(forKey: Self.profileKey)
        defaults.removeObject(forKey: Self.legacyStatsKey)
    }

    // MARK: Forward-compatible decoding

    /// Decodes a saved profile, filling any fields added since it was saved
    /// with their default values (synthesised Codable would otherwise fail).
    static func decodeMergingDefaults(_ data: Data) -> PlayerProfile? {
        guard let stored = try? JSONSerialization.jsonObject(with: data),
              let defaultsData = try? JSONEncoder().encode(PlayerProfile()),
              let defaultObject = try? JSONSerialization.jsonObject(with: defaultsData) else {
            return nil
        }
        let merged = merge(defaultObject, with: stored)
        guard JSONSerialization.isValidJSONObject(merged),
              let mergedData = try? JSONSerialization.data(withJSONObject: merged) else { return nil }
        return try? JSONDecoder().decode(PlayerProfile.self, from: mergedData)
    }

    /// Recursively overlays `stored` on `base`. Arrays and scalars from `stored` win.
    private static func merge(_ base: Any, with stored: Any) -> Any {
        guard let baseDict = base as? [String: Any], let storedDict = stored as? [String: Any] else {
            return stored
        }
        var result = baseDict
        for (key, value) in storedDict {
            if let existing = baseDict[key] {
                result[key] = merge(existing, with: value)
            } else {
                result[key] = value
            }
        }
        return result
    }

    // MARK: Version migration

    private func migrated(_ profile: PlayerProfile) -> PlayerProfile {
        var profile = profile
        if profile.version < 3 {
            // v3 rebalanced the economy: cosmetics are re-locked and must be
            // earned again under the new rules (coins and stats are kept).
            profile.unlockedCosmetics = Cosmetic.defaultUnlocked
            Progression.unlockEarnedCosmetics(&profile)
        }
        profile.version = PlayerProfile.currentVersion
        return profile
    }

    /// Makes sure defaults are present even if the catalog changed between versions.
    private func sanitized(_ profile: PlayerProfile) -> PlayerProfile {
        var profile = profile
        profile.unlockedCosmetics.formUnion(Cosmetic.defaultUnlocked)
        if Cosmetic.find(profile.equippedSkin) == nil || !profile.unlockedCosmetics.contains(profile.equippedSkin) {
            profile.equippedSkin = Cosmetic.defaultSkin
        }
        if Cosmetic.find(profile.equippedTrail) == nil || !profile.unlockedCosmetics.contains(profile.equippedTrail) {
            profile.equippedTrail = Cosmetic.defaultTrail
        }
        if Cosmetic.find(profile.equippedTheme) == nil || !profile.unlockedCosmetics.contains(profile.equippedTheme) {
            profile.equippedTheme = Cosmetic.defaultTheme
        }
        return profile
    }

    // MARK: Legacy (v1 prototype) migration

    private struct LegacyStats: Decodable {
        var totalTimeSurvived: Double?
        var totalObjectsDodged: Int?
        var totalGamesPlayed: Int?
        var bestSurvivalTime: Double?
        var longestStreak: Int?
    }

    private func migrateLegacy(into profile: inout PlayerProfile) {
        guard let data = defaults.data(forKey: Self.legacyStatsKey),
              let legacy = try? JSONDecoder().decode(LegacyStats.self, from: data) else { return }
        profile.stats.gamesPlayed = legacy.totalGamesPlayed ?? 0
        profile.stats.endlessGames = legacy.totalGamesPlayed ?? 0
        profile.stats.totalTimeSurvived = Int(legacy.totalTimeSurvived ?? 0)
        profile.stats.totalDodged = legacy.totalObjectsDodged ?? 0
        profile.stats.bestEndlessTime = Int(legacy.bestSurvivalTime ?? 0)
        profile.stats.longestSurvivalStreak = legacy.longestStreak ?? 0
        // Veterans get a little welcome-back gift.
        if profile.stats.gamesPlayed > 0 {
            Progression.grant(250, to: &profile)
        }
    }
}
