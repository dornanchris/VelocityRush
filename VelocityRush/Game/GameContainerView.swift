//
//  GameContainerView.swift
//  VelocityRush
//
//  Full-screen host for a play session: SpriteKit scene + SwiftUI HUD,
//  pause menu and results.
//

import SwiftUI
import SpriteKit

struct GameContainerView: View {
    @StateObject private var session: GameSessionModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    init(config: RunConfig) {
        _session = StateObject(wrappedValue: GameSessionModel(config: config))
    }

    var body: some View {
        ZStack {
            GeometryReader { proxy in
                Group {
                    if let scene = session.scene {
                        SpriteView(scene: scene,
                                   preferredFramesPerSecond: 60,
                                   options: [.ignoresSiblingOrder],
                                   debugOptions: session.settings.showFPS ? [.showsFPS, .showsNodeCount] : [])
                    } else {
                        Color.black
                            .onAppear { session.makeScene(size: proxy.size) }
                    }
                }
            }
            .ignoresSafeArea()

            if session.phase != .finished {
                HUDView(session: session, hudModel: session.hudModel)
                    .transition(.opacity)
            }

            if session.phase == .paused {
                PauseMenu(session: session, onQuit: quit)
                    .transition(.opacity.combined(with: .scale(scale: 1.05)))
            }

            if session.phase == .finished, let summary = session.summary {
                GameOverView(summary: summary,
                             onReplay: { session.restart() },
                             onHome: quit)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .background(Color.black)
        .statusBarHidden()
        .persistentSystemOverlays(.hidden)
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase != .active { session.pause() }
        }
        .onDisappear { session.teardown() }
    }

    private func quit() {
        session.teardown()
        dismiss()
    }
}

// MARK: - HUD

struct HUDView: View {
    let session: GameSessionModel
    @ObservedObject var hudModel: HUDModel

    private var hud: HUDState { hudModel.state }
    private var mode: GameMode { session.config.mode }

    var body: some View {
        VStack(spacing: 10) {
            HStack(alignment: .top) {
                scoreBlock
                Spacer(minLength: 8)
                centerBlock
                Spacer(minLength: 8)
                pauseButton
            }
            powerUpRow
            Spacer()
            if hud.isCountdown {
                countdownHint
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 8)
        .padding(.bottom, 24)
    }

    private var scoreBlock: some View {
        VStack(alignment: .leading, spacing: 6) {
            if mode == .zen {
                Label("\(hud.score)", systemImage: "leaf.fill")
                    .font(VR.display(22))
                    .foregroundStyle(VR.green)
            } else {
                Text(hud.score.formatted())
                    .font(VR.display(34))
                    .foregroundStyle(.white)
                    .contentTransition(.numericText(value: Double(hud.score)))
                    .shadow(color: .black.opacity(0.6), radius: 4)
                if session.personalBest > 0 {
                    Text(hud.score > session.personalBest ? "NEW BEST!" : "BEST \(session.personalBest.formatted())")
                        .font(VR.display(11, weight: .bold))
                        .foregroundStyle(hud.score > session.personalBest ? VR.gold : VR.secondaryText)
                }
            }
            if hud.multiplier > 1 || hud.comboProgress > 0 {
                HStack(spacing: 6) {
                    Text("×\(hud.multiplier)")
                        .font(VR.display(16))
                        .foregroundStyle(VR.gold)
                        .neonGlow(VR.gold, radius: 6)
                    ProgressBar(value: hud.comboProgress, tint: VR.gold, height: 5)
                        .frame(width: 60)
                }
                .transition(.opacity.combined(with: .move(edge: .leading)))
            }
        }
        .animation(.spring(response: 0.3), value: hud.multiplier)
        .allowsHitTesting(false)
    }

    @ViewBuilder
    private var centerBlock: some View {
        VStack(spacing: 2) {
            switch mode {
            case .timeAttack:
                let seconds = Double(hud.clockTenths) / 10
                Text(seconds > 10 ? "\(Int(seconds))" : String(format: "%.1f", seconds))
                    .font(VR.display(40))
                    .monospacedDigit()
                    .foregroundStyle(seconds <= 10 ? VR.pink : .white)
                    .shadow(color: (seconds <= 10 ? VR.pink : .black).opacity(0.7), radius: 6)
                Text("SECONDS")
                    .font(VR.display(10, weight: .bold))
                    .foregroundStyle(VR.secondaryText)
            case .zen:
                Text(hud.elapsed.clockString)
                    .font(VR.display(26))
                    .monospacedDigit()
                    .foregroundStyle(.white.opacity(0.85))
            default:
                Text(hud.elapsed.clockString)
                    .font(VR.display(28))
                    .monospacedDigit()
                    .foregroundStyle(.white)
                Text("LEVEL \(hud.level)")
                    .font(VR.display(10, weight: .bold))
                    .foregroundStyle(VR.cyan)
            }
        }
        .allowsHitTesting(false)
    }

    private var pauseButton: some View {
        Button {
            HapticsManager.shared.play(.selection)
            session.pause()
        } label: {
            Image(systemName: "pause.fill")
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(Circle().fill(.ultraThinMaterial))
                .overlay(Circle().strokeBorder(VR.stroke))
        }
        .buttonStyle(PressableStyle())
        .accessibilityLabel("Pause")
    }

    private var powerUpRow: some View {
        HStack(spacing: 8) {
            if hud.hasShield {
                PowerUpChip(kind: .shield, fraction: 1)
            }
            ForEach(hud.powerUps) { powerUp in
                PowerUpChip(kind: powerUp.kind, fraction: powerUp.fraction)
            }
            Spacer()
        }
        .animation(.spring(response: 0.3), value: hud.powerUps.map(\.kind))
        .animation(.spring(response: 0.3), value: hud.hasShield)
        .allowsHitTesting(false)
    }

    private var countdownHint: some View {
        VStack(spacing: 8) {
            if session.showsTutorialHint {
                Label("Drag anywhere to move", systemImage: "hand.draw.fill")
                    .font(VR.display(16, weight: .bold))
                Text("Dodge the hazards · grab the stars · skim close for bonus points")
                    .font(VR.display(13, weight: .semibold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(VR.secondaryText)
            }
            if session.config.modifier != .none {
                Chip(text: session.config.modifier.title, icon: session.config.modifier.icon, tint: VR.cyan)
                Text(session.config.modifier.detail)
                    .font(VR.display(13, weight: .semibold))
                    .foregroundStyle(VR.secondaryText)
                    .multilineTextAlignment(.center)
            }
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 24)
        .transition(.opacity)
        .allowsHitTesting(false)
    }
}

struct PowerUpChip: View {
    let kind: PickupKind
    let fraction: Double

    var body: some View {
        ZStack {
            Circle().fill(kind.color.color.opacity(0.18))
            Circle()
                .trim(from: 0, to: fraction)
                .stroke(kind.color.color, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.linear(duration: 0.25), value: fraction)
            Image(systemName: kind.icon)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(kind.color.color)
        }
        .frame(width: 36, height: 36)
        .shadow(color: kind.color.color.opacity(0.6), radius: 6)
        .transition(.scale.combined(with: .opacity))
    }
}

// MARK: - Pause

struct PauseMenu: View {
    @ObservedObject var session: GameSessionModel
    let onQuit: () -> Void

    @AppStorage(SettingsKey.soundEnabled) private var soundEnabled = true
    @AppStorage(SettingsKey.musicEnabled) private var musicEnabled = true
    @AppStorage(SettingsKey.hapticsEnabled) private var hapticsEnabled = true

    var body: some View {
        ZStack {
            Color.black.opacity(0.65).ignoresSafeArea()
                .background(.ultraThinMaterial)

            VStack(spacing: 22) {
                Text("PAUSED")
                    .font(VR.display(44))
                    .foregroundStyle(VR.brandGradient)

                HStack(spacing: 16) {
                    Chip(text: session.config.mode.title, icon: session.config.mode.icon, tint: session.config.mode.accent.color)
                    if session.config.modifier != .none {
                        Chip(text: session.config.modifier.title, icon: session.config.modifier.icon)
                    }
                }

                VStack(spacing: 12) {
                    Button("Resume") { session.resume() }
                        .buttonStyle(NeonButtonStyle(color: VR.cyan))
                    Button("Restart") { session.restart() }
                        .buttonStyle(GhostButtonStyle())
                    if session.config.mode == .zen {
                        Button("Finish & Collect Stars") { session.finishZenSession() }
                            .buttonStyle(GhostButtonStyle())
                    }
                    Button("Quit to Menu", role: .destructive) { onQuit() }
                        .buttonStyle(GhostButtonStyle())
                }
                .frame(maxWidth: 320)

                HStack(spacing: 18) {
                    toggle(icon: soundEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill", isOn: $soundEnabled)
                    toggle(icon: musicEnabled ? "music.note" : "music.note.list", isOn: $musicEnabled, crossed: !musicEnabled)
                    toggle(icon: "iphone.radiowaves.left.and.right", isOn: $hapticsEnabled, crossed: !hapticsEnabled)
                }
                .onChange(of: musicEnabled) { _, enabled in
                    if !enabled { SoundManager.shared.stopMusic() }
                }

                if session.config.mode.isRanked {
                    Text("Quitting forfeits this run.")
                        .font(VR.display(12, weight: .medium))
                        .foregroundStyle(VR.secondaryText)
                }
            }
            .padding(28)
        }
    }

    private func toggle(icon: String, isOn: Binding<Bool>, crossed: Bool = false) -> some View {
        Button {
            isOn.wrappedValue.toggle()
            HapticsManager.shared.play(.selection)
        } label: {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(isOn.wrappedValue ? .white : VR.secondaryText)
                .frame(width: 54, height: 54)
                .background(Circle().fill(isOn.wrappedValue ? VR.cardStrong : VR.card))
                .overlay(Circle().strokeBorder(isOn.wrappedValue ? VR.cyan.opacity(0.6) : VR.stroke))
                .overlay {
                    if crossed {
                        Rectangle().fill(VR.secondaryText).frame(width: 30, height: 2).rotationEffect(.degrees(-45))
                    }
                }
        }
        .buttonStyle(PressableStyle())
    }
}
