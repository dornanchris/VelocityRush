//
//  GameSessionModel.swift
//  VelocityRush
//
//  Bridges the SpriteKit scene and the SwiftUI HUD / menus for one play
//  session (which may contain several runs via "Play Again").
//

import SwiftUI
import SpriteKit

struct ActivePowerUpDisplay: Equatable, Identifiable {
    var id: String { kind.rawValue }
    let kind: PickupKind
    let fraction: Double
}

struct HUDState: Equatable {
    var score = 0
    var multiplier = 1
    var comboProgress: Double = 0
    var elapsed = 0
    /// Remaining Time Attack clock in tenths of a second.
    var clockTenths = 600
    var level = 1
    var hasShield = false
    var powerUps: [ActivePowerUpDisplay] = []
    var isCountdown = true
}

/// Separate observable so 60 Hz HUD updates only re-render the HUD,
/// not the whole container (and its SpriteView).
@MainActor
final class HUDModel: ObservableObject {
    @Published var state = HUDState()
}

struct RunSummary: Equatable {
    let config: RunConfig
    let result: RunResult
    let rewards: RunRewards
}

@MainActor
final class GameSessionModel: ObservableObject {
    enum Phase: Equatable {
        case playing
        case paused
        case finished
    }

    let hudModel = HUDModel()
    @Published private(set) var phase: Phase = .playing
    @Published private(set) var summary: RunSummary?
    @Published private(set) var scene: GameScene?

    private(set) var config: RunConfig
    let settings: GameSettings
    let showsTutorialHint: Bool
    let personalBest: Int

    init(config: RunConfig) {
        self.config = config
        var settings = GameSettings.load()
        if config.mode == .daily { settings.difficulty = .normal }
        self.settings = settings
        let store = ProgressStore.shared
        showsTutorialHint = store.profile.stats.gamesPlayed < 3
        personalBest = store.best(for: config.mode)
    }

    func makeScene(size: CGSize) {
        guard scene == nil else { return }
        let safeSize = CGSize(width: max(size.width, 1), height: max(size.height, 1))
        let newScene = GameScene(size: safeSize, config: config, session: self,
                                 settings: settings, profile: ProgressStore.shared.profile)
        scene = newScene
        SoundManager.shared.startMusic()
    }

    // MARK: Scene callbacks

    var hud: HUDState { hudModel.state }

    func updateHUD(_ newValue: HUDState) {
        if hudModel.state != newValue { hudModel.state = newValue }
    }

    func runFinished(_ result: RunResult) {
        guard phase != .finished else { return }
        SoundManager.shared.stopMusic()
        let rewards = ProgressStore.shared.record(result)
        summary = RunSummary(config: config, result: result, rewards: rewards)
        withAnimation(.spring(response: 0.45, dampingFraction: 0.85)) {
            phase = .finished
        }
        if rewards.isNewBest || !rewards.newAchievements.isEmpty {
            SoundManager.shared.play(.unlock)
            HapticsManager.shared.play(.success)
        }
    }

    // MARK: Controls

    func pause() {
        guard phase == .playing, scene != nil else { return }
        scene?.setRunPaused(true)
        SoundManager.shared.pauseMusic()
        withAnimation(.easeOut(duration: 0.2)) { phase = .paused }
    }

    func resume() {
        guard phase == .paused else { return }
        withAnimation(.easeOut(duration: 0.2)) { phase = .playing }
        scene?.setRunPaused(false)
        SoundManager.shared.startMusic()
    }

    func restart() {
        let next = config.replay()
        config = next
        summary = nil
        hudModel.state = HUDState()
        withAnimation(.easeOut(duration: 0.2)) { phase = .playing }
        scene?.startRun(config: next)
        SoundManager.shared.stopMusic()
        SoundManager.shared.startMusic()
    }

    /// Zen has no game over – this banks the session's stars.
    func finishZenSession() {
        guard config.mode == .zen else { return }
        withAnimation(.easeOut(duration: 0.2)) { phase = .playing }
        scene?.setRunPaused(false)
        scene?.endRunEarly()
    }

    func teardown() {
        SoundManager.shared.stopMusic()
        scene?.setRunPaused(true)
        scene?.removeAllActions()
    }
}
