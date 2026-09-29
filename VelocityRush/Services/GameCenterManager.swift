//
//  GameCenterManager.swift
//  VelocityRush
//
//  Optional Game Center integration. Everything degrades gracefully: if
//  the player isn't signed in (or the capability isn't configured yet) the
//  game keeps working with local leaderboards only.
//

import GameKit
import UIKit

struct GlobalScore: Identifiable, Equatable {
    var id: Int { rank }
    let rank: Int
    let name: String
    let score: Int
    let isLocalPlayer: Bool
}

@MainActor
final class GameCenterManager: ObservableObject {
    static let shared = GameCenterManager()

    @Published private(set) var isAuthenticated = false
    @Published private(set) var playerName: String?

    private var didStartAuthentication = false

    private init() {}

    func authenticate() {
        guard !didStartAuthentication else { return }
        didStartAuthentication = true

        GKLocalPlayer.local.authenticateHandler = { [weak self] viewController, _ in
            Task { @MainActor in
                if let viewController {
                    GameCenterManager.topViewController()?.present(viewController, animated: true)
                }
                let local = GKLocalPlayer.local
                self?.isAuthenticated = local.isAuthenticated
                self?.playerName = local.isAuthenticated ? local.displayName : nil
            }
        }
    }

    // MARK: Scores & achievements

    func submit(_ run: RunResult) {
        guard isAuthenticated, run.score > 0, let leaderboardID = run.mode.leaderboardID else { return }
        GKLeaderboard.submitScore(run.score, context: 0, player: GKLocalPlayer.local,
                                  leaderboardIDs: [leaderboardID]) { _ in }
    }

    func report(achievementIDs: [String]) {
        guard isAuthenticated, !achievementIDs.isEmpty else { return }
        let achievements: [GKAchievement] = achievementIDs.compactMap { id in
            guard let definition = AchievementCatalog.find(id) else { return nil }
            let achievement = GKAchievement(identifier: definition.gameCenterID)
            achievement.percentComplete = 100
            achievement.showsCompletionBanner = false
            return achievement
        }
        GKAchievement.report(achievements) { _ in }
    }

    /// Loads the global top 25 for a mode.
    func loadTopScores(for mode: GameMode, completion: @escaping ([GlobalScore]) -> Void) {
        guard isAuthenticated, let leaderboardID = mode.leaderboardID else {
            completion([])
            return
        }
        GKLeaderboard.loadLeaderboards(IDs: [leaderboardID]) { leaderboards, _ in
            guard let leaderboard = leaderboards?.first else {
                Task { @MainActor in completion([]) }
                return
            }
            leaderboard.loadEntries(for: .global, timeScope: .allTime,
                                    range: NSRange(location: 1, length: 25)) { localEntry, entries, _, _ in
                let localID = localEntry?.player.gamePlayerID
                let scores = (entries ?? []).map { entry in
                    GlobalScore(rank: entry.rank,
                                name: entry.player.displayName,
                                score: entry.score,
                                isLocalPlayer: entry.player.gamePlayerID == localID)
                }
                Task { @MainActor in completion(scores) }
            }
        }
    }

    func showDashboard() {
        guard isAuthenticated else { return }
        GKAccessPoint.shared.trigger(state: .leaderboards) {}
    }

    // MARK: Helpers

    static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        let window = scene?.windows.first { $0.isKeyWindow } ?? scene?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
