//
//  ShopView.swift
//  VelocityRush
//
//  The Armory: skins, blade trails and themes. Commons can be bought, rarer
//  items need levels, personal bests or streaks, and the best are earn-only.
//

import SwiftUI

struct ShopView: View {
    @EnvironmentObject private var store: ProgressStore
    @State private var category: CosmeticCategory = .skin
    @State private var selected: Cosmetic?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    private var items: [Cosmetic] {
        Cosmetic.items(in: category).sorted { lhs, rhs in
            if lhs.rarity != rhs.rarity { return lhs.rarity < rhs.rarity }
            return (lhs.price ?? .max) < (rhs.price ?? .max)
        }
    }

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    LoadoutPreviewView(skinID: store.profile.equippedSkin,
                                       trailID: store.profile.equippedTrail,
                                       themeID: store.profile.equippedTheme)
                        .frame(height: 190)
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(VR.stroke))
                        .overlay(alignment: .bottomLeading) {
                            Text("YOUR LOADOUT")
                                .font(VR.display(11, weight: .heavy))
                                .tracking(1.5)
                                .foregroundStyle(.white.opacity(0.75))
                                .padding(12)
                        }

                    categoryPicker
                    collectionSummary

                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(items) { cosmetic in
                            CosmeticCard(cosmetic: cosmetic) {
                                HapticsManager.shared.play(.selection)
                                selected = cosmetic
                            }
                        }
                    }

                    Text("Coins come from stars, high scores, daily missions and login rewards. The rarest gear can't be bought – you have to earn it.")
                        .font(VR.display(12, weight: .medium))
                        .foregroundStyle(VR.secondaryText)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                }
                .padding(20)
            }
        }
        .navigationTitle("Armory")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { CoinBadge(amount: store.profile.coins, compact: true) }
        }
        .sheet(item: $selected) { cosmetic in
            CosmeticDetailSheet(cosmetic: cosmetic)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }

    private var categoryPicker: some View {
        HStack(spacing: 8) {
            ForEach(CosmeticCategory.allCases) { item in
                Button {
                    HapticsManager.shared.play(.selection)
                    withAnimation(.spring(response: 0.3)) { category = item }
                } label: {
                    Label(item.title, systemImage: item.icon)
                        .font(VR.display(14, weight: .bold))
                        .foregroundStyle(category == item ? .black : .white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(Capsule().fill(category == item ? VR.cyan : VR.card))
                        .overlay(Capsule().strokeBorder(category == item ? .clear : VR.stroke))
                }
                .buttonStyle(PressableStyle())
            }
        }
    }

    private var collectionSummary: some View {
        let all = Cosmetic.items(in: category)
        let owned = all.filter { store.profile.isUnlocked($0) }.count
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                Text("\(owned) of \(all.count) \(category.title.lowercased()) collected")
                    .font(VR.display(14, weight: .bold))
                    .foregroundStyle(.white)
                ProgressBar(value: Double(owned) / Double(max(all.count, 1)), tint: VR.cyan, height: 6)
            }
            HStack(spacing: 4) {
                ForEach(CosmeticRarity.allCases) { rarity in
                    let total = all.filter { $0.rarity == rarity }.count
                    if total > 0 {
                        let have = all.filter { $0.rarity == rarity && store.profile.isUnlocked($0) }.count
                        Circle()
                            .fill(have == total ? rarity.color.color : rarity.color.color.opacity(0.25))
                            .frame(width: 10, height: 10)
                    }
                }
            }
        }
        .padding(14)
        .glassCard(cornerRadius: 16)
    }
}

// MARK: - Card

struct CosmeticCard: View {
    let cosmetic: Cosmetic
    let action: () -> Void
    @EnvironmentObject private var store: ProgressStore

    private var profile: PlayerProfile { store.profile }
    private var isOwned: Bool { profile.isUnlocked(cosmetic) }

    private var isEquipped: Bool {
        switch cosmetic.category {
        case .skin: return profile.equippedSkin == cosmetic.id
        case .trail: return profile.equippedTrail == cosmetic.id
        case .theme: return profile.equippedTheme == cosmetic.id
        }
    }

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                CosmeticThumbnail(cosmetic: cosmetic, size: 60)
                    .frame(height: 80)
                    .opacity(isOwned ? 1 : 0.6)
                    .overlay(alignment: .topTrailing) {
                        if !isOwned && !Progression.gatesMet(cosmetic, profile: profile) {
                            Image(systemName: "lock.fill")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(6)
                                .background(Circle().fill(Color.black.opacity(0.55)))
                        }
                    }
                Text(cosmetic.name)
                    .font(VR.display(15, weight: .bold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(cosmetic.rarity.title.uppercased())
                    .font(VR.display(9, weight: .heavy))
                    .tracking(1.2)
                    .foregroundStyle(cosmetic.rarity.color.color)
                status
                    .frame(minHeight: 30)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .padding(.horizontal, 10)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(RadialGradient(colors: [cosmetic.rarity.color.color.opacity(0.18), .clear],
                                         center: .top, startRadius: 0, endRadius: 140))
            )
            .glassCard(cornerRadius: 18, tint: isEquipped ? VR.cyan : cosmetic.rarity.color.color.opacity(0.6))
        }
        .buttonStyle(PressableStyle())
    }

    @ViewBuilder
    private var status: some View {
        if isEquipped {
            Chip(text: "Equipped", icon: "checkmark", tint: VR.cyan)
        } else if isOwned {
            Chip(text: "Owned", tint: .white)
        } else if let gate = cosmetic.gates.first(where: { !$0.isMet(by: profile) }) {
            let progress = gate.progress(in: profile)
            VStack(spacing: 4) {
                Text(gate.summary)
                    .font(VR.display(10, weight: .semibold))
                    .foregroundStyle(VR.secondaryText)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .minimumScaleFactor(0.8)
                ProgressBar(value: Double(progress.current) / Double(max(progress.goal, 1)),
                            tint: cosmetic.rarity.color.color, height: 4)
                    .padding(.horizontal, 8)
            }
        } else if let price = cosmetic.price {
            HStack(spacing: 4) {
                Image(systemName: "star.circle.fill").foregroundStyle(VR.gold)
                Text(price.formatted())
                    .foregroundStyle(profile.coins >= price ? .white : VR.secondaryText)
            }
            .font(VR.display(14, weight: .bold))
        }
    }
}

// MARK: - Detail sheet

struct CosmeticDetailSheet: View {
    let cosmetic: Cosmetic
    @EnvironmentObject private var store: ProgressStore
    @Environment(\.dismiss) private var dismiss
    @State private var confirmPurchase = false

    private var profile: PlayerProfile { store.profile }
    private var isOwned: Bool { profile.isUnlocked(cosmetic) }
    private var gatesMet: Bool { Progression.gatesMet(cosmetic, profile: profile) }

    private var isEquipped: Bool {
        switch cosmetic.category {
        case .skin: return profile.equippedSkin == cosmetic.id
        case .trail: return profile.equippedTrail == cosmetic.id
        case .theme: return profile.equippedTheme == cosmetic.id
        }
    }

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    LoadoutPreviewView(skinID: cosmetic.category == .skin ? cosmetic.id : profile.equippedSkin,
                                       trailID: cosmetic.category == .trail ? cosmetic.id : profile.equippedTrail,
                                       themeID: cosmetic.category == .theme ? cosmetic.id : profile.equippedTheme)
                        .frame(height: 240)
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .strokeBorder(cosmetic.rarity.color.color.opacity(0.6), lineWidth: 1.5))
                        .shadow(color: cosmetic.rarity.color.color.opacity(0.35), radius: 18)

                    VStack(spacing: 8) {
                        Text(cosmetic.rarity.title.uppercased() + " " + cosmetic.category.singularTitle.uppercased())
                            .font(VR.display(12, weight: .heavy))
                            .tracking(2)
                            .foregroundStyle(cosmetic.rarity.color.color)
                        Text(cosmetic.name)
                            .font(VR.display(34))
                            .foregroundStyle(.white)
                        Text(cosmetic.detail)
                            .font(VR.display(14, weight: .medium))
                            .foregroundStyle(VR.secondaryText)
                            .multilineTextAlignment(.center)
                    }

                    if !cosmetic.isFree { requirements }

                    actionButton
                }
                .padding(20)
                .padding(.top, 12)
            }
        }
        .alert("Buy \(cosmetic.name)?", isPresented: $confirmPurchase) {
            Button("Buy for \((cosmetic.price ?? 0).formatted())") { buy() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("You have \(profile.coins.formatted()) coins.")
        }
    }

    private var requirements: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionTitle(text: cosmetic.isEarnedOnly ? "How to earn" : "Requirements", icon: "list.bullet.rectangle")
            ForEach(Array(cosmetic.gates.enumerated()), id: \.offset) { _, gate in
                let met = gate.isMet(by: profile)
                let progress = gate.progress(in: profile)
                HStack(spacing: 12) {
                    Image(systemName: met ? "checkmark.circle.fill" : "circle.dashed")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(met ? VR.green : VR.secondaryText)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(gate.summary)
                            .font(VR.display(14, weight: .bold))
                            .foregroundStyle(.white)
                        if !met {
                            ProgressBar(value: Double(progress.current) / Double(max(progress.goal, 1)),
                                        tint: cosmetic.rarity.color.color, height: 5)
                        }
                        Text(gate.progressText(in: profile))
                            .font(VR.display(11, weight: .semibold))
                            .foregroundStyle(VR.secondaryText)
                    }
                }
            }
            if let price = cosmetic.price {
                HStack(spacing: 12) {
                    Image(systemName: profile.coins >= price ? "checkmark.circle.fill" : "star.circle.fill")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(profile.coins >= price ? VR.green : VR.gold)
                    VStack(alignment: .leading, spacing: 5) {
                        Text("\(price.formatted()) coins")
                            .font(VR.display(14, weight: .bold))
                            .foregroundStyle(.white)
                        if profile.coins < price && !isOwned {
                            ProgressBar(value: Double(profile.coins) / Double(price), tint: VR.gold, height: 5)
                        }
                        Text("You have \(profile.coins.formatted())")
                            .font(VR.display(11, weight: .semibold))
                            .foregroundStyle(VR.secondaryText)
                    }
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard(tint: cosmetic.rarity.color.color)
    }

    @ViewBuilder
    private var actionButton: some View {
        if isEquipped {
            Button("Equipped") {}
                .buttonStyle(GhostButtonStyle())
                .disabled(true)
        } else if isOwned {
            Button("Equip") {
                store.equip(cosmetic.id)
                SoundManager.shared.play(.tap)
                HapticsManager.shared.play(.success)
                dismiss()
            }
            .buttonStyle(NeonButtonStyle(color: VR.cyan))
        } else if !gatesMet {
            Label(cosmetic.isEarnedOnly ? "Earn it to unlock" : "Meet the requirements to buy", systemImage: "lock.fill")
                .font(VR.display(15, weight: .bold))
                .foregroundStyle(VR.secondaryText)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Capsule().fill(VR.card))
        } else if let price = cosmetic.price {
            if profile.coins >= price {
                Button {
                    confirmPurchase = true
                } label: {
                    Label("Buy for \(price.formatted())", systemImage: "star.circle.fill")
                }
                .buttonStyle(NeonButtonStyle(color: VR.gold))
            } else {
                Label("Need \((price - profile.coins).formatted()) more coins", systemImage: "star.circle")
                    .font(VR.display(15, weight: .bold))
                    .foregroundStyle(VR.secondaryText)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(Capsule().fill(VR.card))
            }
        }
    }

    private func buy() {
        guard store.purchase(cosmetic.id) == .success else {
            HapticsManager.shared.play(.warning)
            return
        }
        SoundManager.shared.play(.unlock)
        HapticsManager.shared.play(.success)
        ToastCenter.shared.show(Toast(icon: "bag.fill", title: "Purchased & equipped",
                                      subtitle: cosmetic.name, tint: cosmetic.rarity.color))
        dismiss()
    }
}

// MARK: - Thumbnails

struct CosmeticThumbnail: View {
    let cosmetic: Cosmetic
    var size: CGFloat = 56

    var body: some View {
        switch cosmetic.category {
        case .skin:
            SkinPreview(skin: cosmetic.skin ?? Cosmetic.skinLook(Cosmetic.defaultSkin), size: size * 0.6)
                .frame(width: size, height: size)
        case .trail:
            TrailPreview(trail: cosmetic.trail ?? Cosmetic.trailLook(Cosmetic.defaultTrail), size: size)
        case .theme:
            ThemePreview(theme: cosmetic.theme ?? Cosmetic.themeLook(Cosmetic.defaultTheme), size: size)
        }
    }
}

/// A static blade swoosh drawn with the trail's real colours.
struct TrailPreview: View {
    let trail: TrailLook
    var size: CGFloat = 56

    var body: some View {
        ZStack {
            if !trail.hasRibbon && trail.particles == .none && !trail.afterimage {
                Image(systemName: "nosign")
                    .font(.system(size: size * 0.4, weight: .bold))
                    .foregroundStyle(VR.secondaryText)
            } else {
                Canvas { context, canvasSize in
                    let count = max(trail.length, 10)
                    let points: [CGPoint] = (0..<count).map { index in
                        let t = CGFloat(index) / CGFloat(count - 1)
                        // A curved swipe from top-right to bottom-left.
                        let x = canvasSize.width * (0.82 - 0.7 * t)
                        let y = canvasSize.height * (0.2 + 0.6 * t) + sin(t * .pi) * canvasSize.height * 0.12
                        return CGPoint(x: x, y: y)
                    }
                    for index in 0..<(count - 1) {
                        let t = Double(index) / Double(count - 1)
                        let width = size * 0.34 * CGFloat(max(trail.width, 0.4)) * CGFloat(pow(1 - t, 0.75)) + 1
                        var path = Path()
                        path.move(to: points[index])
                        path.addLine(to: points[index + 1])
                        let color = trail.hasRibbon ? trail.color(at: t, index: index, time: 0.8) : trail.primary
                        context.stroke(path, with: .color(color.color.opacity(pow(1 - t, 1.1))),
                                       style: StrokeStyle(lineWidth: width, lineCap: .round))
                    }
                    let head = points[0]
                    let dot = CGRect(x: head.x - size * 0.13, y: head.y - size * 0.13, width: size * 0.26, height: size * 0.26)
                    context.fill(Path(ellipseIn: dot), with: .color(.white))
                }
                .frame(width: size * 1.3, height: size)
                .shadow(color: trail.primary.color.opacity(trail.glow ? 0.8 : 0.3), radius: 6)
            }
        }
        .frame(width: size * 1.3, height: size)
    }
}

struct ThemePreview: View {
    let theme: ThemeLook
    var size: CGFloat = 56

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(LinearGradient(colors: [theme.backgroundTop.color, theme.backgroundBottom.color],
                                     startPoint: .top, endPoint: .bottom))
            if theme.grid {
                VStack(spacing: size * 0.14) {
                    ForEach(0..<5, id: \.self) { _ in
                        Rectangle().fill(theme.accent.color.opacity(0.25)).frame(height: 1)
                    }
                }
            }
            Circle().fill(theme.hazard.color).frame(width: size * 0.22).offset(x: -size * 0.2, y: -size * 0.18)
            Circle().fill(theme.hazardAlt.color).frame(width: size * 0.16).offset(x: size * 0.22, y: -size * 0.05)
            Image(systemName: "star.fill").font(.system(size: size * 0.14)).foregroundStyle(theme.star.color)
                .offset(x: size * 0.05, y: -size * 0.28)
            Circle().fill(.white).frame(width: size * 0.18).offset(y: size * 0.26)
                .shadow(color: theme.accent.color, radius: 4)
        }
        .frame(width: size * 1.3, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(VR.stroke))
    }
}
