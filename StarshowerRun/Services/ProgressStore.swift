//
//  ProgressStore.swift
//  StarshowerRun
//
//  Observable wrapper around the pure progression logic. Every mutation
//  is saved immediately and new achievements are announced / reported.
//

import Foundation
import SwiftUI

@MainActor
final class ProgressStore: ObservableObject {
    static let shared = ProgressStore()

    @Published private(set) var profile: PlayerProfile

    private let repository: ProfileRepository

    private init(repository: ProfileRepository = ProfileRepository()) {
        self.repository = repository
        var loaded = repository.load()
        Progression.refreshDaily(&loaded, now: Date())
        Progression.evaluateAchievements(&loaded, now: Date())
        Progression.unlockEarnedCosmetics(&loaded)
        profile = loaded
        repository.save(loaded)
    }

    // MARK: Derived

    var level: Int { profile.level }
    var claimableMissions: Int { Progression.claimableMissionCount(profile) }
    var isLoginRewardAvailable: Bool { Progression.isLoginRewardAvailable(profile, now: Date()) }
    var nextLoginStreakDay: Int { Progression.nextLoginStreakDay(profile, now: Date()) }

    func best(for mode: GameMode) -> Int {
        mode == .daily ? profile.daily.dailyRunBest : profile.stats.best(for: mode)
    }

    // MARK: Mutations

    func refreshDaily() {
        mutate { Progression.refreshDaily(&$0, now: Date()) }
    }

    /// Records a run. Achievements are shown on the results screen, so no toasts.
    func record(_ run: RunResult) -> RunRewards {
        let rewards = mutate(announce: false) { Progression.record(run, into: &$0, now: Date()) }
        GameCenterManager.shared.submit(run)
        return rewards
    }

    func purchase(_ cosmeticID: String) -> PurchaseResult {
        mutate { Progression.purchase(cosmeticID, profile: &$0, now: Date()) }
    }

    func equip(_ cosmeticID: String) {
        mutate { Progression.equip(cosmeticID, profile: &$0) }
    }

    @discardableResult
    func claimMission(_ missionID: String) -> MissionClaim {
        let claim = mutate { Progression.claimMission(missionID, profile: &$0, now: Date()) }
        if claim.allClear {
            ToastCenter.shared.show(Toast(icon: "checkmark.seal.fill", title: "All-clear bonus!",
                                          subtitle: "+\(MissionGenerator.allCompleteBonus) coins · \(profile.stats.allClearStreak)-day streak",
                                          tint: RGBColor(hex: 0x6BFF9E)))
        }
        return claim
    }

    @discardableResult
    func claimLoginReward() -> Int {
        mutate { Progression.claimLoginReward(&$0, now: Date()).coins }
    }

    func setPlayerName(_ name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        mutate { $0.playerName = String(trimmed.prefix(20)) }
    }

    func resetAllProgress() {
        repository.reset()
        var fresh = PlayerProfile()
        Progression.refreshDaily(&fresh, now: Date())
        profile = fresh
        repository.save(fresh)
    }

    // MARK: Plumbing

    @discardableResult
    private func mutate<T>(announce: Bool = true, _ body: (inout PlayerProfile) -> T) -> T {
        var updated = profile
        let before = Set(updated.unlockedAchievements.keys)
        let cosmeticsBefore = updated.unlockedCosmetics
        let result = body(&updated)
        let newIDs = Set(updated.unlockedAchievements.keys).subtracting(before)
        let newCosmetics = Cosmetic.catalog.filter {
            updated.unlockedCosmetics.contains($0.id) && !cosmeticsBefore.contains($0.id) && $0.isEarnedOnly
        }
        if updated != profile {
            profile = updated
            repository.save(updated)
        }
        if !newIDs.isEmpty {
            let ordered = AchievementCatalog.all.map(\.id).filter { newIDs.contains($0) }
            GameCenterManager.shared.report(achievementIDs: ordered)
            if announce { ToastCenter.shared.announceAchievements(ordered) }
        }
        if announce {
            for cosmetic in newCosmetics {
                ToastCenter.shared.show(Toast(icon: "sparkles", title: "\(cosmetic.rarity.title) \(cosmetic.category.singularTitle.lowercased()) unlocked!",
                                              subtitle: cosmetic.name, tint: cosmetic.rarity.color))
            }
        }
        return result
    }
}
