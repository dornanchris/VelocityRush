//
//  GameOverView.swift
//  StarshowerRun
//
//  Results screen: animated score, stats, rewards, unlocks and sharing.
//

import SwiftUI

struct GameOverView: View {
    let summary: RunSummary
    let onReplay: () -> Void
    let onHome: () -> Void

    @EnvironmentObject private var store: ProgressStore
    @State private var displayedScore = 0
    @State private var revealed = false

    private var result: RunResult { summary.result }
    private var rewards: RunRewards { summary.rewards }
    private var mode: GameMode { result.mode }

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea()
            LinearGradient(colors: [mode.accent.color.opacity(0.25), .clear], startPoint: .top, endPoint: .center)
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    header
                    scoreCard
                    statsGrid
                    rewardsCard
                    if !rewards.newAchievements.isEmpty { achievementsCard }
                    if !rewards.newCosmetics.isEmpty { cosmeticsCard }
                    if !rewards.missionsReady.isEmpty { missionsBanner }
                    buttons
                }
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 40)
                .opacity(revealed ? 1 : 0)
                .offset(y: revealed ? 0 : 30)
            }
        }
        .task {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.85)) { revealed = true }
            await countUp()
        }
    }

    // MARK: Sections

    private var header: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: mode.icon)
                Text(mode.title.uppercased())
            }
            .font(VR.display(14, weight: .bold))
            .tracking(2)
            .foregroundStyle(mode.accent.color)

            Text(headline)
                .font(VR.display(40))
                .foregroundStyle(.white)
                .multilineTextAlignment(.center)

            if result.modifier != .none {
                Chip(text: result.modifier.title, icon: result.modifier.icon, tint: VR.cyan)
            }
        }
    }

    private var headline: String {
        switch mode {
        case .zen: return "Session Complete"
        case .timeAttack: return "Time's Up!"
        default: return rewards.isNewBest ? "New Record!" : "Game Over"
        }
    }

    private var scoreCard: some View {
        VStack(spacing: 6) {
            if rewards.isNewBest {
                Text("★ NEW PERSONAL BEST ★")
                    .font(VR.display(13, weight: .heavy))
                    .tracking(1.5)
                    .foregroundStyle(VR.gold)
                    .neonGlow(VR.gold, radius: 8)
            }
            Text(displayedScore.formatted())
                .font(VR.display(64))
                .foregroundStyle(VR.brandGradient)
                .contentTransition(.numericText(value: Double(displayedScore)))
                .monospacedDigit()
                .minimumScaleFactor(0.5)
                .lineLimit(1)
            HStack(spacing: 14) {
                if mode.isRanked {
                    Label(rewards.isNewBest ? "Previous \(rewards.previousBest.formatted())"
                                            : "Best \(max(rewards.previousBest, result.score).formatted())",
                          systemImage: "trophy.fill")
                }
                if let rank = rewards.localRank {
                    Label("#\(rank) on your board", systemImage: "list.number")
                }
            }
            .font(VR.display(13, weight: .semibold))
            .foregroundStyle(VR.secondaryText)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .glassCard(tint: rewards.isNewBest ? VR.gold : nil)
    }

    private var statsGrid: some View {
        let columns = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]
        return LazyVGrid(columns: columns, spacing: 10) {
            StatTile(icon: "clock.fill", value: result.survivedSeconds.clockString, label: "Time", tint: VR.cyan)
            StatTile(icon: "star.fill", value: "\(result.stars)", label: "Stars", tint: VR.gold)
            StatTile(icon: "scope", value: "\(result.nearMisses)", label: "Near misses", tint: VR.pink)
            StatTile(icon: "multiply.circle.fill", value: "×\(result.maxMultiplier)", label: "Best combo", tint: VR.gold)
            StatTile(icon: "bolt.fill", value: "\(result.powerUps)", label: "Power-ups", tint: VR.purple)
            StatTile(icon: "arrow.left.and.right", value: "\(result.dodged)", label: "Dodged", tint: VR.green)
        }
    }

    private var rewardsCard: some View {
        let xp = store.profile.xp
        let into = Leveling.xpIntoLevel(forXP: xp)
        return VStack(alignment: .leading, spacing: 12) {
            SectionTitle(text: "Rewards", icon: "gift.fill")
            HStack {
                CoinBadge(amount: rewards.totalCoins)
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    rewardLine("Stars", rewards.coinsFromStars)
                    rewardLine("Score bonus", rewards.coinsFromScore)
                    rewardLine("Level up", rewards.coinsFromLevelUps)
                    rewardLine("Achievements", rewards.coinsFromAchievements)
                }
            }
            HStack(spacing: 12) {
                LevelRing(level: store.profile.level, progress: Leveling.progress(forXP: xp), size: 44)
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("+\(rewards.xpGained) XP")
                            .font(VR.display(16))
                            .foregroundStyle(VR.cyan)
                        Spacer()
                        Text("\(into.current) / \(into.needed)")
                            .font(VR.display(12, weight: .semibold))
                            .foregroundStyle(VR.secondaryText)
                    }
                    ProgressBar(value: Leveling.progress(forXP: xp), tint: VR.cyan)
                }
            }
            if rewards.leveledUp {
                HStack {
                    Image(systemName: "arrow.up.circle.fill")
                    Text("LEVEL UP! You reached level \(rewards.levelAfter)")
                }
                .font(VR.display(15))
                .foregroundStyle(VR.green)
            }
        }
        .padding(18)
        .glassCard()
    }

    @ViewBuilder
    private func rewardLine(_ title: String, _ value: Int) -> some View {
        if value > 0 {
            Text("\(title) +\(value)")
                .font(VR.display(12, weight: .semibold))
                .foregroundStyle(VR.secondaryText)
        }
    }

    private var achievementsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle(text: "Achievements unlocked", icon: "rosette")
            ForEach(rewards.newAchievements, id: \.self) { id in
                if let definition = AchievementCatalog.find(id) {
                    HStack(spacing: 12) {
                        Image(systemName: definition.icon)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(definition.tier.color.color)
                            .frame(width: 40, height: 40)
                            .background(Circle().fill(definition.tier.color.color.opacity(0.18)))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(definition.title).font(VR.display(16)).foregroundStyle(.white)
                            Text(definition.detail).font(VR.display(12, weight: .medium)).foregroundStyle(VR.secondaryText)
                        }
                        Spacer()
                        Text("+\(definition.reward)")
                            .font(VR.display(14))
                            .foregroundStyle(VR.gold)
                    }
                }
            }
        }
        .padding(18)
        .glassCard(tint: VR.gold)
    }

    private var cosmeticsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionTitle(text: "New unlocks", icon: "sparkles")
            ForEach(rewards.newCosmetics, id: \.self) { id in
                if let cosmetic = Cosmetic.find(id) {
                    HStack(spacing: 12) {
                        CosmeticThumbnail(cosmetic: cosmetic, size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(cosmetic.name).font(VR.display(16)).foregroundStyle(cosmetic.rarity.color.color)
                            Text("\(cosmetic.rarity.title) \(cosmetic.category.singularTitle.lowercased()) · equip it in the Shop")
                                .font(VR.display(12, weight: .medium))
                                .foregroundStyle(VR.secondaryText)
                        }
                        Spacer()
                    }
                }
            }
        }
        .padding(18)
        .glassCard(tint: VR.purple)
    }

    private var missionsBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "checklist.checked")
            Text(rewards.missionsReady.count == 1 ? "A daily mission is ready to claim!"
                                                  : "\(rewards.missionsReady.count) daily missions are ready to claim!")
            Spacer()
        }
        .font(VR.display(14, weight: .bold))
        .foregroundStyle(VR.green)
        .padding(16)
        .glassCard(tint: VR.green)
    }

    private var buttons: some View {
        VStack(spacing: 12) {
            Button {
                HapticsManager.shared.play(.medium)
                onReplay()
            } label: {
                Label(mode == .daily ? "Try Again" : "Play Again", systemImage: "arrow.counterclockwise")
            }
            .buttonStyle(NeonButtonStyle(color: mode.accent.color))

            HStack(spacing: 12) {
                Button {
                    onHome()
                } label: {
                    Label("Home", systemImage: "house.fill")
                }
                .buttonStyle(GhostButtonStyle())

                ShareLink(item: shareText) {
                    Label("Share", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(GhostButtonStyle())
            }
        }
        .padding(.top, 6)
    }

    private var shareText: String {
        var text = "I scored \(result.score.formatted()) in Starshower Run \(mode.title)"
        if mode == .daily, result.modifier != .none { text += " (\(result.modifier.title))" }
        text += " and survived \(result.survivedSeconds.clockString) ⚡️ Can you beat it?"
        return text
    }

    // MARK: Animation

    private func countUp() async {
        let target = result.score
        guard target > 0 else { return }
        try? await Task.sleep(nanoseconds: 250_000_000)
        let steps = 30
        for step in 1...steps {
            let eased = 1 - pow(1 - Double(step) / Double(steps), 3)
            withAnimation(.linear(duration: 0.03)) {
                displayedScore = Int(Double(target) * eased)
            }
            if step % 4 == 0 { SoundManager.shared.play(.tick) }
            try? await Task.sleep(nanoseconds: 30_000_000)
        }
        displayedScore = target
    }
}

struct StatTile: View {
    let icon: String
    let value: String
    let label: String
    var tint: Color = VR.cyan

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(tint)
            Text(value)
                .font(VR.display(20))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(label)
                .font(VR.display(11, weight: .semibold))
                .foregroundStyle(VR.secondaryText)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .glassCard(cornerRadius: 16)
    }
}
