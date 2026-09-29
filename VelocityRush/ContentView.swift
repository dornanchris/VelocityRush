//
//  ContentView.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//
//  Home screen: mode carousel, big Play button and navigation hub.
//

import SwiftUI

enum Route: Hashable {
    case missions
    case leaderboards
    case shop
    case profile
    case settings
}

struct ContentView: View {
    @EnvironmentObject private var store: ProgressStore
    @EnvironmentObject private var toasts: ToastCenter
    @Environment(\.scenePhase) private var scenePhase

    @AppStorage(SettingsKey.difficulty) private var difficultyRaw = Difficulty.normal.rawValue
    @AppStorage("selectedMode") private var selectedModeRaw = GameMode.endless.rawValue

    @State private var path: [Route] = []
    @State private var activeRun: RunConfig?
    @State private var showLoginReward = false
    @State private var didCheckLogin = false

    private var selectedMode: GameMode { GameMode(rawValue: selectedModeRaw) ?? .endless }
    private var difficulty: Difficulty { Difficulty(rawValue: difficultyRaw) ?? .normal }

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                AnimatedBackdrop(tint: selectedMode.accent.color)

                VStack(spacing: 0) {
                    HomeHeader()
                        .padding(.horizontal, 20)
                        .padding(.top, 8)

                    Spacer(minLength: 8)

                    Logo()

                    Spacer(minLength: 8)

                    ModeCarousel(selection: Binding(get: { selectedMode },
                                                    set: { selectedModeRaw = $0.rawValue }))

                    playControls
                        .padding(.horizontal, 24)
                        .padding(.top, 12)

                    Spacer(minLength: 12)

                    NavigationGrid(path: $path)
                        .padding(.horizontal, 20)
                        .padding(.bottom, 8)
                }
            }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .missions: MissionsView()
                case .leaderboards: LeaderboardView(initialMode: selectedMode == .zen ? .endless : selectedMode)
                case .shop: ShopView()
                case .profile: ProfileView()
                case .settings: SettingsView()
                }
            }
            .toolbar(.hidden, for: .navigationBar)
        }
        .tint(VR.cyan)
        .overlay(alignment: .top) { ToastOverlay() }
        .fullScreenCover(item: $activeRun) { config in
            GameContainerView(config: config)
                .environmentObject(store)
        }
        .sheet(isPresented: $showLoginReward) {
            LoginRewardSheet()
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
                .presentationBackground(.ultraThinMaterial)
        }
        .onAppear(perform: checkLoginReward)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                store.refreshDaily()
                checkLoginReward()
            }
        }
    }

    private var playControls: some View {
        VStack(spacing: 12) {
            Button {
                start(selectedMode)
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "play.fill")
                    Text(selectedMode == .daily ? "PLAY DAILY RUN" : "PLAY \(selectedMode.title.uppercased())")
                }
            }
            .buttonStyle(NeonButtonStyle(color: selectedMode.accent.color, secondary: selectedMode.accent.lighter(0.25).color, height: 62))

            if selectedMode == .endless || selectedMode == .timeAttack {
                DifficultyPicker(selection: Binding(get: { difficulty }, set: { difficultyRaw = $0.rawValue }))
            } else {
                Text(selectedMode == .daily ? "Daily Runs are always played on Normal." : "Zen ignores difficulty.")
                    .font(VR.display(12, weight: .semibold))
                    .foregroundStyle(VR.secondaryText)
                    .frame(height: 36)
            }
        }
    }

    private func start(_ mode: GameMode) {
        HapticsManager.shared.play(.medium)
        SoundManager.shared.play(.go)
        switch mode {
        case .daily:
            activeRun = DailyChallenge.config(for: Date())
        case .zen:
            activeRun = RunConfig(mode: .zen, difficulty: .easy)
        default:
            activeRun = RunConfig(mode: mode, difficulty: difficulty)
        }
    }

    private func checkLoginReward() {
        guard store.isLoginRewardAvailable, activeRun == nil else { return }
        // Small delay so the home screen settles first.
        let delay: UInt64 = didCheckLogin ? 300_000_000 : 800_000_000
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: delay)
            didCheckLogin = true
            if store.isLoginRewardAvailable && activeRun == nil { showLoginReward = true }
        }
    }
}

// MARK: - Header

struct HomeHeader: View {
    @EnvironmentObject private var store: ProgressStore

    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(value: Route.profile) {
                HStack(spacing: 10) {
                    LevelRing(level: store.profile.level, progress: Leveling.progress(forXP: store.profile.xp), size: 46)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(store.profile.playerName)
                            .font(VR.display(16, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text("Level \(store.profile.level)")
                            .font(VR.display(12, weight: .semibold))
                            .foregroundStyle(VR.secondaryText)
                    }
                }
            }
            .buttonStyle(PressableStyle())

            Spacer()

            NavigationLink(value: Route.shop) {
                CoinBadge(amount: store.profile.coins)
            }
            .buttonStyle(PressableStyle())
        }
    }
}

struct Logo: View {
    @State private var glow = false

    var body: some View {
        VStack(spacing: -6) {
            Text("VELOCITY")
                .font(.system(size: 46, weight: .black, design: .rounded))
                .italic()
                .foregroundStyle(.white)
            Text("RUSH")
                .font(.system(size: 58, weight: .black, design: .rounded))
                .italic()
                .foregroundStyle(VR.brandGradient)
                .shadow(color: VR.pink.opacity(glow ? 0.8 : 0.35), radius: glow ? 22 : 10)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.6).repeatForever(autoreverses: true)) { glow = true }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Velocity Rush")
    }
}

// MARK: - Mode carousel

struct ModeCarousel: View {
    @Binding var selection: GameMode

    var body: some View {
        VStack(spacing: 10) {
            TabView(selection: $selection) {
                ForEach(GameMode.allCases) { mode in
                    ModeCard(mode: mode)
                        .padding(.horizontal, 24)
                        .tag(mode)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            .frame(height: 232)
            .onChange(of: selection) { _, _ in
                HapticsManager.shared.play(.selection)
            }

            HStack(spacing: 8) {
                ForEach(GameMode.allCases) { mode in
                    Capsule()
                        .fill(mode == selection ? mode.accent.color : Color.white.opacity(0.25))
                        .frame(width: mode == selection ? 22 : 8, height: 8)
                        .onTapGesture { withAnimation(.spring) { selection = mode } }
                }
            }
            .animation(.spring(response: 0.3), value: selection)
        }
    }
}

struct ModeCard: View {
    let mode: GameMode
    @EnvironmentObject private var store: ProgressStore

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                Image(systemName: mode.icon)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(mode.accent.color)
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(mode.accent.color.opacity(0.16)))
                    .neonGlow(mode.accent.color, radius: 8)
                VStack(alignment: .leading, spacing: 2) {
                    Text(mode.title)
                        .font(VR.display(24))
                        .foregroundStyle(.white)
                    Text(mode.tagline)
                        .font(VR.display(13, weight: .semibold))
                        .foregroundStyle(VR.secondaryText)
                        .lineLimit(2)
                }
                Spacer(minLength: 0)
            }

            if mode == .daily {
                DailyDetails()
            } else {
                VStack(alignment: .leading, spacing: 5) {
                    ForEach(mode.rules, id: \.self) { rule in
                        HStack(alignment: .top, spacing: 8) {
                            Circle().fill(mode.accent.color).frame(width: 5, height: 5).padding(.top, 6)
                            Text(rule)
                                .font(VR.display(13, weight: .medium))
                                .foregroundStyle(.white.opacity(0.8))
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }

            Spacer(minLength: 0)

            if mode.isRanked && mode != .daily {
                HStack {
                    Label("Best \(store.best(for: mode).formatted())", systemImage: "trophy.fill")
                        .font(VR.display(13, weight: .bold))
                        .foregroundStyle(VR.gold)
                    Spacer()
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .glassCard(cornerRadius: 24, tint: mode.accent.color)
    }
}

struct DailyDetails: View {
    @EnvironmentObject private var store: ProgressStore

    var body: some View {
        let config = DailyChallenge.config(for: Date())
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Chip(text: config.modifier.title, icon: config.modifier.icon, tint: VR.cyan)
                Text("×\(config.modifier.scoreMultiplier.compactString) score")
                    .font(VR.display(12, weight: .bold))
                    .foregroundStyle(VR.secondaryText)
            }
            Text(config.modifier.detail)
                .font(VR.display(13, weight: .medium))
                .foregroundStyle(.white.opacity(0.8))
                .fixedSize(horizontal: false, vertical: true)
            HStack {
                Label("Today \(store.profile.daily.dailyRunBest.formatted())", systemImage: "trophy.fill")
                    .foregroundStyle(VR.gold)
                Spacer()
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Label(DailyChallenge.timeUntilReset(from: context.date).countdownString, systemImage: "clock")
                        .monospacedDigit()
                        .foregroundStyle(VR.secondaryText)
                }
            }
            .font(VR.display(13, weight: .bold))
        }
    }
}

struct DifficultyPicker: View {
    @Binding var selection: Difficulty

    var body: some View {
        HStack(spacing: 6) {
            ForEach(Difficulty.allCases) { level in
                Button {
                    HapticsManager.shared.play(.selection)
                    withAnimation(.spring(response: 0.3)) { selection = level }
                } label: {
                    VStack(spacing: 1) {
                        Text(level.title)
                            .font(VR.display(13, weight: .bold))
                        Text("×\(level.scoreMultiplier.compactString)")
                            .font(VR.display(9, weight: .semibold))
                            .opacity(0.7)
                    }
                    .foregroundStyle(selection == level ? .black : .white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 36)
                    .background(Capsule().fill(selection == level ? VR.cyan : VR.card))
                    .overlay(Capsule().strokeBorder(selection == level ? .clear : VR.stroke))
                }
                .buttonStyle(PressableStyle())
            }
        }
    }
}

// MARK: - Navigation grid

struct NavigationGrid: View {
    @Binding var path: [Route]
    @EnvironmentObject private var store: ProgressStore

    var body: some View {
        HStack(spacing: 10) {
            tile(.missions, icon: "checklist", title: "Missions", badge: store.claimableMissions)
            tile(.leaderboards, icon: "trophy.fill", title: "Ranks")
            tile(.shop, icon: "bag.fill", title: "Armory")
            tile(.profile, icon: "person.fill", title: "Profile")
            tile(.settings, icon: "gearshape.fill", title: "Settings")
        }
    }

    private func tile(_ route: Route, icon: String, title: String, badge: Int = 0) -> some View {
        Button {
            HapticsManager.shared.play(.selection)
            SoundManager.shared.play(.tap)
            path.append(route)
        } label: {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(.white)
                Text(title)
                    .font(VR.display(11, weight: .bold))
                    .foregroundStyle(VR.secondaryText)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 64)
            .glassCard(cornerRadius: 16)
            .overlay(alignment: .topTrailing) {
                if badge > 0 {
                    Text("\(badge)")
                        .font(VR.display(11, weight: .heavy))
                        .foregroundStyle(.black)
                        .frame(minWidth: 20, minHeight: 20)
                        .background(Circle().fill(VR.pink))
                        .offset(x: 4, y: -6)
                }
            }
        }
        .buttonStyle(PressableStyle())
    }
}

// MARK: - Toasts

struct ToastOverlay: View {
    @EnvironmentObject private var toasts: ToastCenter

    var body: some View {
        Group {
            if let toast = toasts.current {
                HStack(spacing: 12) {
                    Image(systemName: toast.icon)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(toast.tint.color)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(toast.tint.color.opacity(0.2)))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(toast.title)
                            .font(VR.display(12, weight: .bold))
                            .foregroundStyle(VR.secondaryText)
                        Text(toast.subtitle)
                            .font(VR.display(15, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    Spacer(minLength: 0)
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(.ultraThinMaterial))
                .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(toast.tint.color.opacity(0.5)))
                .shadow(color: toast.tint.color.opacity(0.4), radius: 12)
                .padding(.horizontal, 16)
                .padding(.top, 4)
                .transition(.move(edge: .top).combined(with: .opacity))
                .id(toast.id)
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.8), value: toasts.current)
    }
}

#Preview {
    ContentView()
        .environmentObject(ProgressStore.shared)
        .environmentObject(ToastCenter.shared)
}
