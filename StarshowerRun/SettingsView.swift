//
//  SettingsView.swift
//  StarshowerRun
//
//  Every toggle here is wired into the game.
//

import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: ProgressStore
    @ObservedObject private var gameCenter = GameCenterManager.shared

    @AppStorage(SettingsKey.soundEnabled) private var soundEnabled = true
    @AppStorage(SettingsKey.musicEnabled) private var musicEnabled = true
    @AppStorage(SettingsKey.hapticsEnabled) private var hapticsEnabled = true
    @AppStorage(SettingsKey.showFPS) private var showFPS = false
    @AppStorage(SettingsKey.difficulty) private var difficultyRaw = Difficulty.normal.rawValue
    @AppStorage(SettingsKey.sensitivity) private var sensitivity = 1.2
    @AppStorage(SettingsKey.colorBlindMode) private var colorBlindMode = false
    @AppStorage(SettingsKey.highContrast) private var highContrast = false
    @AppStorage(SettingsKey.reducedMotion) private var reducedMotion = false
    @AppStorage(SettingsKey.notifications) private var notificationsEnabled = false

    @State private var showResetConfirmation = false
    @State private var showAbout = false

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    SettingsSection(title: "Audio", icon: "speaker.wave.2.fill") {
                        SettingsToggle(title: "Sound Effects", subtitle: "Synthesised arcade SFX", icon: "speaker.wave.3.fill", isOn: $soundEnabled)
                        SettingsToggle(title: "Music", subtitle: "Synthwave groove while you play", icon: "music.note", isOn: $musicEnabled)
                        SettingsToggle(title: "Haptics", subtitle: "Feel every near miss", icon: "iphone.radiowaves.left.and.right", isOn: $hapticsEnabled)
                    }

                    SettingsSection(title: "Gameplay", icon: "gamecontroller.fill") {
                        VStack(alignment: .leading, spacing: 10) {
                            SettingsLabel(title: "Difficulty", subtitle: "Higher difficulty = bigger score multiplier", icon: "speedometer")
                            Picker("Difficulty", selection: $difficultyRaw) {
                                ForEach(Difficulty.allCases) { level in
                                    Text(level.title).tag(level.rawValue)
                                }
                            }
                            .pickerStyle(.segmented)
                            Text("Score ×\((Difficulty(rawValue: difficultyRaw) ?? .normal).scoreMultiplier.compactString) · Daily Runs always use Normal")
                                .font(VR.display(11, weight: .semibold))
                                .foregroundStyle(VR.secondaryText)
                        }
                        VStack(alignment: .leading, spacing: 10) {
                            SettingsLabel(title: "Drag Sensitivity", subtitle: "How far the dot moves per swipe", icon: "hand.draw.fill")
                            HStack {
                                Image(systemName: "tortoise.fill").foregroundStyle(VR.secondaryText)
                                Slider(value: $sensitivity, in: 0.7...2.0, step: 0.1)
                                    .tint(VR.cyan)
                                Image(systemName: "hare.fill").foregroundStyle(VR.secondaryText)
                            }
                            Text("\(sensitivity, specifier: "%.1f")×")
                                .font(VR.display(11, weight: .semibold))
                                .foregroundStyle(VR.secondaryText)
                        }
                        SettingsToggle(title: "Show FPS", subtitle: "Performance overlay", icon: "speedometer", isOn: $showFPS)
                    }

                    SettingsSection(title: "Accessibility", icon: "accessibility") {
                        SettingsToggle(title: "Colour-Blind Friendly", subtitle: "Orange/yellow hazards with outlines", icon: "eye.fill", isOn: $colorBlindMode)
                        SettingsToggle(title: "High Contrast", subtitle: "Bolder hazards, darker sky", icon: "circle.lefthalf.filled", isOn: $highContrast)
                        SettingsToggle(title: "Reduce Motion", subtitle: "No screen shake, fewer particles", icon: "figure.walk.motion", isOn: $reducedMotion)
                    }

                    SettingsSection(title: "Notifications", icon: "bell.fill") {
                        SettingsToggle(title: "Daily Reminder", subtitle: "A nudge at 6pm when a new Daily Run is live", icon: "bell.badge.fill", isOn: $notificationsEnabled)
                    }

                    SettingsSection(title: "Game Center", icon: "person.2.fill") {
                        HStack(spacing: 12) {
                            SettingsLabel(title: gameCenter.isAuthenticated ? (gameCenter.playerName ?? "Signed in") : "Not signed in",
                                          subtitle: gameCenter.isAuthenticated ? "Global leaderboards & achievements are on"
                                                                               : "Sign in via the Settings app to compete globally",
                                          icon: "gamecontroller.fill")
                            Spacer()
                            Circle()
                                .fill(gameCenter.isAuthenticated ? VR.green : VR.secondaryText)
                                .frame(width: 10, height: 10)
                        }
                        if gameCenter.isAuthenticated {
                            SettingsButton(title: "Open Game Center", icon: "arrow.up.right.square", tint: VR.cyan) {
                                gameCenter.showDashboard()
                            }
                        }
                    }

                    SettingsSection(title: "Data", icon: "externaldrive.fill") {
                        SettingsButton(title: "Reset All Progress", icon: "trash.fill", tint: VR.pink) {
                            showResetConfirmation = true
                        }
                    }

                    SettingsSection(title: "About", icon: "info.circle.fill") {
                        SettingsButton(title: "About Starshower Run", icon: "sparkles", tint: VR.cyan) {
                            showAbout = true
                        }
                    }

                    Text("Starshower Run \(appVersion)")
                        .font(VR.display(12, weight: .semibold))
                        .foregroundStyle(VR.secondaryText)
                        .padding(.top, 4)
                }
                .padding(20)
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.large)
        .onChange(of: musicEnabled) { _, enabled in
            if !enabled { SoundManager.shared.stopMusic() }
        }
        .onChange(of: soundEnabled) { _, enabled in
            if enabled { SoundManager.shared.play(.tap) }
        }
        .onChange(of: hapticsEnabled) { _, enabled in
            if enabled { HapticsManager.shared.play(.medium) }
        }
        .onChange(of: notificationsEnabled) { _, enabled in
            NotificationManager.setDailyReminder(enabled: enabled) { granted in
                if enabled && !granted { notificationsEnabled = false }
            }
        }
        .alert("Reset all progress?", isPresented: $showResetConfirmation) {
            Button("Reset", role: .destructive) {
                store.resetAllProgress()
                HapticsManager.shared.play(.warning)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently deletes your coins, cosmetics, stats, achievements and records. This cannot be undone.")
        }
        .sheet(isPresented: $showAbout) {
            AboutView()
        }
    }

    private var appVersion: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(version) (\(build))"
    }
}

// MARK: - Building blocks

struct SettingsSection<Content: View>: View {
    let title: String
    let icon: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionTitle(text: title, icon: icon)
            VStack(spacing: 16) {
                content
            }
            .padding(16)
            .glassCard(cornerRadius: 18)
        }
    }
}

struct SettingsLabel: View {
    let title: String
    let subtitle: String
    let icon: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(VR.cyan)
                .frame(width: 32, height: 32)
                .background(RoundedRectangle(cornerRadius: 9).fill(VR.cyan.opacity(0.15)))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(VR.display(15, weight: .bold))
                    .foregroundStyle(.white)
                Text(subtitle)
                    .font(VR.display(12, weight: .medium))
                    .foregroundStyle(VR.secondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct SettingsToggle: View {
    let title: String
    let subtitle: String
    let icon: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            SettingsLabel(title: title, subtitle: subtitle, icon: icon)
        }
        .tint(VR.cyan)
    }
}

struct SettingsButton: View {
    let title: String
    let icon: String
    var tint: Color = VR.cyan
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(tint)
                    .frame(width: 32, height: 32)
                    .background(RoundedRectangle(cornerRadius: 9).fill(tint.opacity(0.15)))
                Text(title)
                    .font(VR.display(15, weight: .bold))
                    .foregroundStyle(tint)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(VR.secondaryText)
            }
        }
        .buttonStyle(PressableStyle())
    }
}

// MARK: - About

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                ScreenBackground()
                ScrollView {
                    VStack(spacing: 22) {
                        Logo().padding(.top, 20)
                        Text("The ultimate dodging challenge.")
                            .font(VR.display(16, weight: .semibold))
                            .foregroundStyle(VR.secondaryText)

                        VStack(alignment: .leading, spacing: 12) {
                            SectionTitle(text: "How to play", icon: "gamecontroller.fill")
                            aboutRow("hand.draw.fill", "Drag anywhere on screen to move your dot.")
                            aboutRow("circle.fill", "Dodge the falling hazards. Watch for warnings – meteors are fast!")
                            aboutRow("scope", "Skim past hazards for near-miss points. Closer = PERFECT.")
                            aboutRow("multiply.circle.fill", "Near misses and stars build your multiplier up to ×6.")
                            aboutRow("bolt.fill", "Power-ups: Shield, Slow-Mo, Magnet, Shrink and Nova.")
                        }
                        .padding(18)
                        .glassCard()

                        VStack(alignment: .leading, spacing: 12) {
                            SectionTitle(text: "Credits", icon: "person.fill")
                            aboutRow("person.crop.circle", "Created by Christopher Dornan")
                            aboutRow("hammer.fill", "Built with SwiftUI & SpriteKit")
                            aboutRow("waveform", "All sound and music synthesised in code")
                        }
                        .padding(18)
                        .glassCard()

                        Text("Made with ❤️ for iOS")
                            .font(VR.display(13, weight: .semibold))
                            .foregroundStyle(VR.secondaryText)
                    }
                    .padding(20)
                }
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func aboutRow(_ icon: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(VR.cyan)
                .frame(width: 22)
            Text(text)
                .font(VR.display(14, weight: .medium))
                .foregroundStyle(.white.opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview {
    NavigationStack { SettingsView() }
        .environmentObject(ProgressStore.shared)
}
