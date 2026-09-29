//
//  ProfileView.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//
//  Player card, personal bests, lifetime stats and achievements.
//

import SwiftUI

struct ProfileView: View {
    enum Tab: String, CaseIterable, Identifiable {
        case stats = "Stats"
        case achievements = "Achievements"
        var id: String { rawValue }
    }

    @EnvironmentObject private var store: ProgressStore
    @State private var tab: Tab = .stats
    @State private var editingName = false
    @State private var draftName = ""

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    playerCard
                    Picker("Section", selection: $tab) {
                        ForEach(Tab.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    switch tab {
                    case .stats: StatsSection(stats: store.profile.stats)
                    case .achievements: AchievementsSection()
                    }
                }
                .padding(20)
            }
        }
        .navigationTitle("Profile")
        .navigationBarTitleDisplayMode(.inline)
        .alert("Player name", isPresented: $editingName) {
            TextField("Name", text: $draftName)
                .textInputAutocapitalization(.words)
            Button("Save") { store.setPlayerName(draftName) }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var playerCard: some View {
        let profile = store.profile
        let into = Leveling.xpIntoLevel(forXP: profile.xp)
        let unlocked = profile.unlockedAchievements.count
        return VStack(spacing: 16) {
            HStack(spacing: 16) {
                ZStack {
                    Circle().fill(Cosmetic.themeLook(profile.equippedTheme).backgroundTop.color)
                    SkinPreview(skin: Cosmetic.skinLook(profile.equippedSkin), size: 40)
                }
                .frame(width: 76, height: 76)
                .overlay(Circle().strokeBorder(VR.brandGradient, lineWidth: 3))

                VStack(alignment: .leading, spacing: 6) {
                    Button {
                        draftName = profile.playerName
                        editingName = true
                    } label: {
                        HStack(spacing: 6) {
                            Text(profile.playerName)
                                .font(VR.display(22))
                                .foregroundStyle(.white)
                                .lineLimit(1)
                            Image(systemName: "pencil")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(VR.secondaryText)
                        }
                    }
                    .buttonStyle(PressableStyle())

                    Text("Level \(profile.level) · \(title(for: profile.level))")
                        .font(VR.display(13, weight: .bold))
                        .foregroundStyle(VR.cyan)
                    ProgressBar(value: Leveling.progress(forXP: profile.xp), tint: VR.cyan, height: 7)
                    Text("\(into.current.formatted()) / \(into.needed.formatted()) XP")
                        .font(VR.display(11, weight: .semibold))
                        .foregroundStyle(VR.secondaryText)
                }
            }
            HStack(spacing: 10) {
                miniStat(value: profile.stats.gamesPlayed.formatted(), label: "Runs")
                miniStat(value: "\(unlocked)/\(AchievementCatalog.all.count)", label: "Achievements")
                miniStat(value: profile.stats.totalCoinsEarned.formatted(), label: "Coins earned")
            }
        }
        .padding(18)
        .glassCard(tint: VR.purple)
    }

    private func miniStat(value: String, label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(VR.display(17))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(VR.display(10, weight: .semibold))
                .foregroundStyle(VR.secondaryText)
        }
        .frame(maxWidth: .infinity)
    }

    private func title(for level: Int) -> String {
        switch level {
        case ..<5: return "Rookie"
        case 5..<10: return "Dodger"
        case 10..<20: return "Speedster"
        case 20..<35: return "Velocity Ace"
        case 35..<50: return "Rush Master"
        default: return "Legend"
        }
    }
}

// MARK: - Stats

struct StatsSection: View {
    let stats: PlayerStats
    private let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        VStack(spacing: 18) {
            VStack(spacing: 10) {
                SectionTitle(text: "Personal bests", icon: "trophy.fill")
                LazyVGrid(columns: columns, spacing: 10) {
                    StatCard(icon: "infinity", title: "Endless score", value: stats.bestEndlessScore.formatted(), tint: GameMode.endless.accent.color)
                    StatCard(icon: "clock.fill", title: "Endless time", value: stats.bestEndlessTime.clockString, tint: GameMode.endless.accent.color)
                    StatCard(icon: "stopwatch.fill", title: "Time Attack", value: stats.bestTimeAttackScore.formatted(), tint: GameMode.timeAttack.accent.color)
                    StatCard(icon: "calendar", title: "Daily Run", value: stats.bestDailyScore.formatted(), tint: GameMode.daily.accent.color)
                    StatCard(icon: "multiply.circle.fill", title: "Best multiplier", value: "×\(stats.bestMultiplier)", tint: VR.gold)
                    StatCard(icon: "gauge.with.dots.needle.67percent", title: "Highest level", value: "\(stats.bestLevel)", tint: VR.cyan)
                    StatCard(icon: "scope", title: "Near misses (run)", value: "\(stats.bestNearMissesInRun)", tint: VR.pink)
                    StatCard(icon: "star.fill", title: "Stars (run)", value: "\(stats.bestStarsInRun)", tint: VR.gold)
                }
            }

            VStack(spacing: 10) {
                SectionTitle(text: "Lifetime", icon: "chart.bar.fill")
                LazyVGrid(columns: columns, spacing: 10) {
                    StatCard(icon: "gamecontroller.fill", title: "Runs played", value: stats.gamesPlayed.formatted(), tint: VR.cyan)
                    StatCard(icon: "hourglass", title: "Time survived", value: formatDuration(stats.totalTimeSurvived), tint: VR.cyan)
                    StatCard(icon: "star.fill", title: "Stars collected", value: stats.totalStars.formatted(), tint: VR.gold)
                    StatCard(icon: "arrow.left.and.right", title: "Hazards dodged", value: stats.totalDodged.formatted(), tint: VR.green)
                    StatCard(icon: "scope", title: "Near misses", value: stats.totalNearMisses.formatted(), tint: VR.pink)
                    StatCard(icon: "viewfinder", title: "Perfect misses", value: stats.totalPerfectMisses.formatted(), tint: VR.pink)
                    StatCard(icon: "bolt.fill", title: "Power-ups", value: stats.totalPowerUps.formatted(), tint: VR.purple)
                    StatCard(icon: "checklist", title: "Missions done", value: stats.missionsCompleted.formatted(), tint: VR.green)
                    StatCard(icon: "flame.fill", title: "Login streak", value: "\(stats.loginStreak) (best \(stats.longestLoginStreak))", tint: VR.pink)
                    StatCard(icon: "calendar.badge.checkmark", title: "Daily Runs", value: stats.dailyRunsCompleted.formatted(), tint: GameMode.daily.accent.color)
                }
            }

            VStack(spacing: 10) {
                SectionTitle(text: "Modes played", icon: "square.grid.2x2.fill")
                ForEach(GameMode.allCases) { mode in
                    HStack {
                        Label(mode.title, systemImage: mode.icon)
                            .foregroundStyle(mode.accent.color)
                        Spacer()
                        Text(games(for: mode).formatted())
                            .foregroundStyle(.white)
                    }
                    .font(VR.display(15, weight: .bold))
                    .padding(14)
                    .glassCard(cornerRadius: 14)
                }
            }
        }
    }

    private func games(for mode: GameMode) -> Int {
        switch mode {
        case .endless: return stats.endlessGames
        case .timeAttack: return stats.timeAttackGames
        case .daily: return stats.dailyGames
        case .zen: return stats.zenGames
        }
    }

    private func formatDuration(_ seconds: Int) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        return hours > 0 ? "\(hours)h \(minutes)m" : "\(minutes)m \(seconds % 60)s"
    }
}

struct StatCard: View {
    let icon: String
    let title: String
    let value: String
    var tint: Color = VR.cyan

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(tint)
                .frame(width: 34, height: 34)
                .background(Circle().fill(tint.opacity(0.15)))
            VStack(alignment: .leading, spacing: 2) {
                Text(value)
                    .font(VR.display(17))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text(title)
                    .font(VR.display(11, weight: .semibold))
                    .foregroundStyle(VR.secondaryText)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .glassCard(cornerRadius: 16)
    }
}

// MARK: - Achievements

struct AchievementsSection: View {
    @EnvironmentObject private var store: ProgressStore
    @State private var filter: AchievementCategory?

    private var definitions: [AchievementDefinition] {
        let all = AchievementCatalog.all.filter { filter == nil || $0.category == filter }
        // Unlocked last so there's always something to chase at the top.
        return all.sorted { lhs, rhs in
            let l = store.profile.unlockedAchievements[lhs.id] != nil
            let r = store.profile.unlockedAchievements[rhs.id] != nil
            if l != r { return !l }
            return lhs.fraction(in: store.profile.stats) > rhs.fraction(in: store.profile.stats)
        }
    }

    var body: some View {
        let unlockedCount = store.profile.unlockedAchievements.count
        let total = AchievementCatalog.all.count
        VStack(spacing: 14) {
            HStack(spacing: 14) {
                ZStack {
                    Circle().stroke(Color.white.opacity(0.1), lineWidth: 6)
                    Circle()
                        .trim(from: 0, to: Double(unlockedCount) / Double(max(total, 1)))
                        .stroke(VR.gold, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text("\(Int(Double(unlockedCount) / Double(max(total, 1)) * 100))%")
                        .font(VR.display(16))
                        .foregroundStyle(.white)
                }
                .frame(width: 64, height: 64)
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(unlockedCount) of \(total) unlocked")
                        .font(VR.display(18))
                        .foregroundStyle(.white)
                    Text("Achievements pay out coins – some unlock exclusive cosmetics.")
                        .font(VR.display(12, weight: .medium))
                        .foregroundStyle(VR.secondaryText)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .glassCard(tint: VR.gold)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    filterChip(nil, title: "All", icon: "square.grid.2x2")
                    ForEach(AchievementCategory.allCases) { category in
                        filterChip(category, title: category.rawValue, icon: category.icon)
                    }
                }
            }

            ForEach(definitions) { definition in
                AchievementRow(definition: definition,
                               unlockedAt: store.profile.unlockedAchievements[definition.id],
                               stats: store.profile.stats)
            }
        }
    }

    private func filterChip(_ category: AchievementCategory?, title: String, icon: String) -> some View {
        Button {
            HapticsManager.shared.play(.selection)
            withAnimation(.spring(response: 0.3)) { filter = category }
        } label: {
            Label(title, systemImage: icon)
                .font(VR.display(13, weight: .bold))
                .foregroundStyle(filter == category ? .black : .white)
                .padding(.horizontal, 14)
                .frame(height: 34)
                .background(Capsule().fill(filter == category ? VR.gold : VR.card))
                .overlay(Capsule().strokeBorder(filter == category ? .clear : VR.stroke))
        }
        .buttonStyle(PressableStyle())
    }
}

struct AchievementRow: View {
    let definition: AchievementDefinition
    let unlockedAt: Date?
    let stats: PlayerStats

    private var isUnlocked: Bool { unlockedAt != nil }
    private var isHidden: Bool { definition.secret && !isUnlocked }

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: isHidden ? "questionmark" : definition.icon)
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(isUnlocked ? definition.tier.color.color : VR.secondaryText)
                .frame(width: 48, height: 48)
                .background(Circle().fill(isUnlocked ? definition.tier.color.color.opacity(0.2) : VR.card))
                .overlay(Circle().strokeBorder(isUnlocked ? definition.tier.color.color.opacity(0.6) : VR.stroke))

            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    Text(isHidden ? "Secret achievement" : definition.title)
                        .font(VR.display(15, weight: .bold))
                        .foregroundStyle(.white)
                    Text(definition.tier.title.uppercased())
                        .font(VR.display(9, weight: .heavy))
                        .foregroundStyle(definition.tier.color.color)
                }
                Text(isHidden ? "Keep playing to discover it." : definition.detail)
                    .font(VR.display(12, weight: .medium))
                    .foregroundStyle(VR.secondaryText)
                if let unlockedAt {
                    Text("Unlocked \(unlockedAt.formatted(date: .abbreviated, time: .omitted))")
                        .font(VR.display(11, weight: .semibold))
                        .foregroundStyle(VR.green)
                } else if !isHidden {
                    HStack(spacing: 8) {
                        ProgressBar(value: definition.fraction(in: stats), tint: definition.tier.color.color, height: 6)
                        Text("\(definition.progress(in: stats).formatted())/\(definition.goal.formatted())")
                            .font(VR.display(10, weight: .semibold))
                            .foregroundStyle(VR.secondaryText)
                            .fixedSize()
                    }
                }
                if let cosmetic = definition.cosmeticReward, !isHidden {
                    Label("Unlocks \(cosmetic.name)", systemImage: "sparkles")
                        .font(VR.display(11, weight: .bold))
                        .foregroundStyle(VR.purple)
                }
            }
            Spacer(minLength: 0)
            VStack(spacing: 2) {
                Image(systemName: "star.circle.fill").foregroundStyle(VR.gold)
                Text("\(definition.reward)")
                    .font(VR.display(12, weight: .bold))
                    .foregroundStyle(isUnlocked ? VR.secondaryText : .white)
            }
        }
        .padding(14)
        .glassCard(cornerRadius: 18, tint: isUnlocked ? definition.tier.color.color : nil)
        .opacity(isUnlocked || !isHidden ? 1 : 0.7)
    }
}

#Preview {
    NavigationStack { ProfileView() }
        .environmentObject(ProgressStore.shared)
}
