//
//  MissionsView.swift
//  VelocityRush
//
//  Daily missions, the all-clear bonus and the login streak calendar.
//

import SwiftUI

struct MissionsView: View {
    @EnvironmentObject private var store: ProgressStore

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    resetHeader
                    LoginStreakStrip()
                    SectionTitle(text: "Today's missions", icon: "checklist")
                    ForEach(store.profile.daily.missions) { mission in
                        MissionCard(mission: mission)
                    }
                    bonusCard
                    tips
                }
                .padding(20)
            }
        }
        .navigationTitle("Missions")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { CoinBadge(amount: store.profile.coins, compact: true) }
        }
        .onAppear { store.refreshDaily() }
    }

    private var resetHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("New missions in")
                    .font(VR.display(13, weight: .semibold))
                    .foregroundStyle(VR.secondaryText)
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Text(DailyChallenge.timeUntilReset(from: context.date).countdownString)
                        .font(VR.display(28))
                        .monospacedDigit()
                        .foregroundStyle(.white)
                }
            }
            Spacer()
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(VR.cyan)
                .neonGlow(VR.cyan, radius: 10)
        }
        .padding(18)
        .glassCard(tint: VR.cyan)
    }

    private var bonusCard: some View {
        let missions = store.profile.daily.missions
        let claimed = missions.filter(\.claimed).count
        let done = store.profile.daily.allMissionsBonusClaimed
        let stats = store.profile.stats
        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Image(systemName: done ? "checkmark.seal.fill" : "gift.fill")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundStyle(done ? VR.green : VR.gold)
                    .frame(width: 56, height: 56)
                    .background(Circle().fill((done ? VR.green : VR.gold).opacity(0.15)))
                    .symbolEffect(.bounce, value: done)
                VStack(alignment: .leading, spacing: 4) {
                    Text("All-Clear Bonus")
                        .font(VR.display(17))
                        .foregroundStyle(.white)
                    Text(done ? "Cleared today – see you tomorrow!"
                              : "Claim all 3 missions for +\(MissionGenerator.allCompleteBonus) coins  (\(claimed)/\(missions.count))")
                        .font(VR.display(12, weight: .semibold))
                        .foregroundStyle(VR.secondaryText)
                }
                Spacer(minLength: 0)
            }
            HStack(spacing: 10) {
                streakStat(value: stats.allClearStreak, label: "Current streak", icon: "flame.fill", tint: VR.pink)
                streakStat(value: stats.longestAllClearStreak, label: "Best streak", icon: "trophy.fill", tint: VR.gold)
                streakStat(value: stats.allClearDays, label: "All-clear days", icon: "calendar", tint: VR.cyan)
            }
            Text("Exclusive rewards: Sakura skin (7-day streak) · Solar Flare theme (14 days) · Prism Storm trail (30-day streak) · Singularity skin (30 days + level 50)")
                .font(VR.display(11, weight: .semibold))
                .foregroundStyle(VR.purple)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(16)
        .glassCard(tint: done ? VR.green : VR.gold)
    }

    private func streakStat(value: Int, label: String, icon: String, tint: Color) -> some View {
        VStack(spacing: 3) {
            Label("\(value)", systemImage: icon)
                .font(VR.display(17))
                .foregroundStyle(tint)
            Text(label)
                .font(VR.display(10, weight: .semibold))
                .foregroundStyle(VR.secondaryText)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: 12).fill(VR.card))
    }

    private var tips: some View {
        VStack(alignment: .leading, spacing: 6) {
            SectionTitle(text: "Tips", icon: "lightbulb.fill")
            Text("Missions track every mode, including Zen. Single-run goals keep your best attempt, so a great run counts even if later ones don't.")
                .font(VR.display(13, weight: .medium))
                .foregroundStyle(VR.secondaryText)
        }
        .padding(.top, 6)
    }
}

struct MissionCard: View {
    let mission: MissionProgress
    @EnvironmentObject private var store: ProgressStore
    @State private var justClaimed = false

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: mission.claimed ? "checkmark" : mission.kind.icon)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(mission.claimed ? .black : VR.cyan)
                .frame(width: 44, height: 44)
                .background(Circle().fill(mission.claimed ? VR.green : VR.cyan.opacity(0.15)))

            VStack(alignment: .leading, spacing: 8) {
                Text(mission.title)
                    .font(VR.display(15, weight: .bold))
                    .foregroundStyle(.white)
                    .strikethrough(mission.claimed, color: VR.secondaryText)
                ProgressBar(value: mission.fraction, tint: mission.isComplete ? VR.green : VR.cyan, height: 7)
                Text("\(min(mission.progress, mission.goal).formatted()) / \(mission.goal.formatted())")
                    .font(VR.display(11, weight: .semibold))
                    .foregroundStyle(VR.secondaryText)
            }

            Spacer(minLength: 4)

            if mission.isComplete && !mission.claimed {
                Button("Claim") {
                    store.claimMission(mission.id)
                    justClaimed = true
                    SoundManager.shared.play(.coin)
                    HapticsManager.shared.play(.success)
                }
                .buttonStyle(NeonButtonStyle(color: VR.green, height: 38))
                .frame(width: 84)
            } else {
                VStack(spacing: 2) {
                    Image(systemName: "star.circle.fill").foregroundStyle(VR.gold)
                    Text("\(mission.reward)")
                        .font(VR.display(13, weight: .bold))
                        .foregroundStyle(mission.claimed ? VR.secondaryText : .white)
                    Text("+\(MissionGenerator.xpReward(for: mission)) XP")
                        .font(VR.display(9, weight: .bold))
                        .foregroundStyle(VR.cyan.opacity(mission.claimed ? 0.5 : 1))
                }
                .frame(width: 60)
            }
        }
        .padding(16)
        .glassCard(tint: mission.isComplete && !mission.claimed ? VR.green : nil)
        .scaleEffect(justClaimed ? 1.03 : 1)
        .animation(.spring(response: 0.3, dampingFraction: 0.5), value: justClaimed)
        .onChange(of: justClaimed) { _, value in
            guard value else { return }
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 250_000_000)
                justClaimed = false
            }
        }
    }
}

struct LoginStreakStrip: View {
    @EnvironmentObject private var store: ProgressStore

    var body: some View {
        let claimedToday = !store.isLoginRewardAvailable
        let nextDay = store.nextLoginStreakDay
        let currentDay = claimedToday ? store.profile.stats.loginStreak : nextDay
        let cycleStart = ((max(currentDay, 1) - 1) / 7) * 7

        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionTitle(text: "Login streak", icon: "flame.fill")
                Text("\(store.profile.stats.loginStreak) day\(store.profile.stats.loginStreak == 1 ? "" : "s")")
                    .font(VR.display(13, weight: .bold))
                    .foregroundStyle(VR.pink)
            }
            HStack(spacing: 6) {
                ForEach(1...7, id: \.self) { index in
                    let day = cycleStart + index
                    let isToday = day == currentDay
                    let isClaimed = day < currentDay || (isToday && claimedToday)
                    VStack(spacing: 4) {
                        Text("D\(index)")
                            .font(VR.display(10, weight: .bold))
                            .foregroundStyle(VR.secondaryText)
                        Image(systemName: isClaimed ? "checkmark.circle.fill" : (index == 7 ? "gift.fill" : "star.circle.fill"))
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(isClaimed ? VR.green : VR.gold)
                        Text("\(LoginRewards.reward(forStreakDay: day))")
                            .font(VR.display(10, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 12).fill(isToday ? VR.pink.opacity(0.2) : VR.card))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(isToday ? VR.pink : .clear, lineWidth: 1.5))
                }
            }
        }
        .padding(16)
        .glassCard()
    }
}

// MARK: - Login reward sheet

struct LoginRewardSheet: View {
    @EnvironmentObject private var store: ProgressStore
    @Environment(\.dismiss) private var dismiss
    @State private var claimedAmount: Int?

    var body: some View {
        let day = store.nextLoginStreakDay
        VStack(spacing: 18) {
            Text(claimedAmount == nil ? "Daily Reward" : "Reward Claimed!")
                .font(VR.display(28))
                .foregroundStyle(VR.brandGradient)
                .padding(.top, 24)

            Text(claimedAmount == nil ? "Day \(day) of your streak. Come back tomorrow for more!"
                                      : "Streak: \(store.profile.stats.loginStreak) day\(store.profile.stats.loginStreak == 1 ? "" : "s") 🔥")
                .font(VR.display(14, weight: .semibold))
                .foregroundStyle(VR.secondaryText)
                .multilineTextAlignment(.center)

            ZStack {
                Circle()
                    .fill(VR.gold.opacity(0.15))
                    .frame(width: 120, height: 120)
                    .neonGlow(VR.gold, radius: 20)
                Image(systemName: claimedAmount == nil ? "gift.fill" : "star.circle.fill")
                    .font(.system(size: 54, weight: .bold))
                    .foregroundStyle(VR.gold)
                    .symbolEffect(.bounce, value: claimedAmount)
            }

            Text("+\(claimedAmount ?? LoginRewards.reward(forStreakDay: day)) coins")
                .font(VR.display(26))
                .foregroundStyle(.white)

            if claimedAmount == nil {
                Button("Claim") {
                    let amount = store.claimLoginReward()
                    withAnimation(.spring) { claimedAmount = amount }
                    SoundManager.shared.play(.coin)
                    HapticsManager.shared.play(.success)
                }
                .buttonStyle(NeonButtonStyle(color: VR.gold))
                .padding(.horizontal, 40)
            } else {
                Button("Let's go!") { dismiss() }
                    .buttonStyle(NeonButtonStyle(color: VR.cyan))
                    .padding(.horizontal, 40)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
    }
}
