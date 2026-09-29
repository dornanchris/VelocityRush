//
//  ProfileRepository.swift
//  VelocityRush
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
           let profile = try? JSONDecoder().decode(PlayerProfile.self, from: data) {
            return sanitized(profile)
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
