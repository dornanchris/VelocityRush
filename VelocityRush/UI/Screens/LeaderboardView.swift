//
//  LeaderboardView.swift
//  VelocityRush
//
//  Personal top-10 boards per mode plus global Game Center rankings.
//

import SwiftUI

struct LeaderboardView: View {
    enum Scope: String, CaseIterable, Identifiable {
        case personal = "Personal"
        case global = "Global"
        var id: String { rawValue }
    }

    @EnvironmentObject private var store: ProgressStore
    @ObservedObject private var gameCenter = GameCenterManager.shared

    @State private var mode: GameMode
    @State private var scope: Scope = .personal
    @State private var globalScores: [GlobalScore] = []
    @State private var isLoading = false

    private let modes: [GameMode] = [.endless, .timeAttack, .daily]

    init(initialMode: GameMode = .endless) {
        _mode = State(initialValue: initialMode)
    }

    var body: some View {
        ZStack {
            ScreenBackground()
            VStack(spacing: 14) {
                modePicker
                Picker("Scope", selection: $scope) {
                    ForEach(Scope.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 10) {
                        switch scope {
                        case .personal: personalList
                        case .global: globalList
                        }
                    }
                    .padding(.bottom, 30)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }
        .navigationTitle("Leaderboards")
        .navigationBarTitleDisplayMode(.large)
        .onChange(of: mode) { _, _ in loadGlobalIfNeeded() }
        .onChange(of: scope) { _, _ in loadGlobalIfNeeded() }
    }

    private var modePicker: some View {
        HStack(spacing: 8) {
            ForEach(modes) { item in
                Button {
                    HapticsManager.shared.play(.selection)
                    withAnimation(.spring(response: 0.3)) { mode = item }
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: item.icon)
                            .font(.system(size: 18, weight: .bold))
                        Text(item.title)
                            .font(VR.display(12, weight: .bold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .foregroundStyle(mode == item ? .black : .white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 60)
                    .background(RoundedRectangle(cornerRadius: 16).fill(mode == item ? item.accent.color : VR.card))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(mode == item ? .clear : VR.stroke))
                }
                .buttonStyle(PressableStyle())
            }
        }
    }

    // MARK: Personal

    @ViewBuilder
    private var personalList: some View {
        let entries = store.profile.localLeaderboard(for: mode)
        if mode == .daily {
            let config = DailyChallenge.config(for: Date())
            HStack {
                Chip(text: config.modifier.title, icon: config.modifier.icon)
                Spacer()
                Text("Resets at midnight")
                    .font(VR.display(12, weight: .semibold))
                    .foregroundStyle(VR.secondaryText)
            }
            .padding(.top, 4)
        }
        if entries.isEmpty {
            EmptyBoard(icon: mode.icon,
                       title: "No runs yet",
                       message: mode == .daily ? "Play today's Daily Run to post a score." : "Play \(mode.title) to set your first record.")
        } else {
            ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                LeaderboardRow(rank: index + 1,
                               title: entry.date.formatted(date: .abbreviated, time: .shortened),
                               subtitle: subtitle(for: entry),
                               score: entry.score,
                               highlight: index == 0,
                               accent: mode.accent.color)
            }
        }
    }

    private func subtitle(for entry: LeaderboardEntry) -> String {
        var parts = [Int(entry.duration).clockString]
        if mode != .daily { parts.append(entry.difficulty.title) }
        return parts.joined(separator: " · ")
    }

    // MARK: Global

    @ViewBuilder
    private var globalList: some View {
        if !gameCenter.isAuthenticated {
            EmptyBoard(icon: "person.crop.circle.badge.questionmark",
                       title: "Game Center not signed in",
                       message: "Sign in to Game Center in the Settings app to compete on global leaderboards. Your personal boards always work offline.")
        } else if isLoading {
            ProgressView()
                .tint(.white)
                .padding(.top, 40)
        } else if globalScores.isEmpty {
            EmptyBoard(icon: "globe", title: "No global scores yet",
                       message: "Be the first to post a score on this board!")
        } else {
            ForEach(globalScores) { score in
                LeaderboardRow(rank: score.rank, title: score.name,
                               subtitle: score.isLocalPlayer ? "You" : "",
                               score: score.score,
                               highlight: score.isLocalPlayer,
                               accent: mode.accent.color)
            }
        }
        if gameCenter.isAuthenticated {
            Button {
                gameCenter.showDashboard()
            } label: {
                Label("Open Game Center", systemImage: "gamecontroller.fill")
            }
            .buttonStyle(GhostButtonStyle())
            .padding(.top, 8)
        }
    }

    private func loadGlobalIfNeeded() {
        guard scope == .global, gameCenter.isAuthenticated else { return }
        isLoading = true
        let requested = mode
        gameCenter.loadTopScores(for: requested) { scores in
            guard requested == mode else { return }
            globalScores = scores
            isLoading = false
        }
    }
}

struct LeaderboardRow: View {
    let rank: Int
    let title: String
    let subtitle: String
    let score: Int
    let highlight: Bool
    let accent: Color

    private var medal: Color? {
        switch rank {
        case 1: return VR.gold
        case 2: return Color(red: 0.8, green: 0.84, blue: 0.9)
        case 3: return Color(red: 0.82, green: 0.55, blue: 0.3)
        default: return nil
        }
    }

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill((medal ?? .white).opacity(medal == nil ? 0.08 : 0.2))
                Text("\(rank)")
                    .font(VR.display(16))
                    .foregroundStyle(medal ?? .white)
            }
            .frame(width: 40, height: 40)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(VR.display(15, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(VR.display(12, weight: .medium))
                        .foregroundStyle(VR.secondaryText)
                }
            }
            Spacer()
            Text(score.formatted())
                .font(VR.display(20))
                .foregroundStyle(highlight ? accent : .white)
                .monospacedDigit()
        }
        .padding(14)
        .glassCard(cornerRadius: 16, tint: highlight ? accent : nil)
    }
}

struct EmptyBoard: View {
    let icon: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 40, weight: .bold))
                .foregroundStyle(VR.secondaryText)
            Text(title)
                .font(VR.display(18))
                .foregroundStyle(.white)
            Text(message)
                .font(VR.display(13, weight: .medium))
                .foregroundStyle(VR.secondaryText)
                .multilineTextAlignment(.center)
        }
        .padding(28)
        .frame(maxWidth: .infinity)
        .glassCard()
        .padding(.top, 20)
    }
}
