//
//  ShopView.swift
//  VelocityRush
//
//  Spend coins on skins, trails and themes. Some items can only be earned
//  through achievements or levels.
//

import SwiftUI

struct ShopView: View {
    @EnvironmentObject private var store: ProgressStore
    @State private var category: CosmeticCategory = .skin
    @State private var pendingPurchase: Cosmetic?
    @State private var message: String?

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ZStack {
            ScreenBackground()
            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    LoadoutPreview(profile: store.profile)
                    categoryPicker
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(Cosmetic.items(in: category)) { cosmetic in
                            CosmeticCard(cosmetic: cosmetic) { handleTap(cosmetic) }
                        }
                    }
                    Text("Earn coins from stars, high scores, missions, achievements and daily rewards.")
                        .font(VR.display(12, weight: .medium))
                        .foregroundStyle(VR.secondaryText)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)
                }
                .padding(20)
            }
        }
        .navigationTitle("Shop")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { CoinBadge(amount: store.profile.coins, compact: true) }
        }
        .alert(pendingPurchase.map { "Buy \($0.name)?" } ?? "",
               isPresented: Binding(get: { pendingPurchase != nil }, set: { if !$0 { pendingPurchase = nil } }),
               presenting: pendingPurchase) { cosmetic in
            Button("Buy for \(cosmetic.price ?? 0)") { buy(cosmetic) }
            Button("Cancel", role: .cancel) {}
        } message: { cosmetic in
            Text(cosmetic.detail)
        }
        .alert(message ?? "", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("OK", role: .cancel) {}
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

    private func handleTap(_ cosmetic: Cosmetic) {
        if store.profile.isUnlocked(cosmetic) {
            store.equip(cosmetic.id)
            SoundManager.shared.play(.tap)
            HapticsManager.shared.play(.selection)
            return
        }
        switch cosmetic.requirement {
        case .coins(let price):
            if store.profile.coins >= price {
                pendingPurchase = cosmetic
            } else {
                HapticsManager.shared.play(.warning)
                message = "You need \((price - store.profile.coins).formatted()) more coins for \(cosmetic.name)."
            }
        case .level(let level):
            message = "\(cosmetic.name) unlocks automatically at level \(level)."
        case .achievement(let id):
            let title = AchievementCatalog.find(id)?.title ?? "an achievement"
            message = "Unlock the “\(title)” achievement to earn \(cosmetic.name)."
        case .free:
            break
        }
    }

    private func buy(_ cosmetic: Cosmetic) {
        switch store.purchase(cosmetic.id) {
        case .success:
            SoundManager.shared.play(.unlock)
            HapticsManager.shared.play(.success)
            ToastCenter.shared.show(Toast(icon: "bag.fill", title: "Purchased & equipped",
                                          subtitle: cosmetic.name, tint: cosmetic.swatch))
        case .notEnoughCoins(let needed):
            message = "You need \(needed.formatted()) more coins."
        case .alreadyOwned, .locked:
            break
        }
    }
}

// MARK: - Cards

struct CosmeticCard: View {
    let cosmetic: Cosmetic
    let action: () -> Void
    @EnvironmentObject private var store: ProgressStore

    private var isOwned: Bool { store.profile.isUnlocked(cosmetic) }

    private var isEquipped: Bool {
        switch cosmetic.category {
        case .skin: return store.profile.equippedSkin == cosmetic.id
        case .trail: return store.profile.equippedTrail == cosmetic.id
        case .theme: return store.profile.equippedTheme == cosmetic.id
        }
    }

    var body: some View {
        Button(action: action) {
            VStack(spacing: 10) {
                CosmeticThumbnail(cosmetic: cosmetic, size: 64)
                    .frame(height: 84)
                    .opacity(isOwned ? 1 : 0.55)
                    .overlay(alignment: .topTrailing) {
                        if !isOwned && cosmetic.price == nil {
                            Image(systemName: "lock.fill")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(6)
                                .background(Circle().fill(Color.black.opacity(0.5)))
                        }
                    }
                Text(cosmetic.name)
                    .font(VR.display(15, weight: .bold))
                    .foregroundStyle(.white)
                status
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .padding(.horizontal, 10)
            .glassCard(cornerRadius: 18, tint: isEquipped ? VR.cyan : nil)
        }
        .buttonStyle(PressableStyle())
    }

    @ViewBuilder
    private var status: some View {
        if isEquipped {
            Chip(text: "Equipped", icon: "checkmark", tint: VR.cyan)
        } else if isOwned {
            Chip(text: "Equip", tint: .white)
        } else {
            switch cosmetic.requirement {
            case .coins(let price):
                HStack(spacing: 4) {
                    Image(systemName: "star.circle.fill").foregroundStyle(VR.gold)
                    Text(price.formatted())
                        .foregroundStyle(store.profile.coins >= price ? .white : VR.secondaryText)
                }
                .font(VR.display(14, weight: .bold))
                .padding(.vertical, 4)
            case .level(let level):
                Chip(text: "Level \(level)", icon: "lock.fill", tint: VR.purple)
            case .achievement:
                Chip(text: "Achievement", icon: "rosette", tint: VR.gold)
            case .free:
                Chip(text: "Free", tint: VR.green)
            }
        }
    }
}

struct CosmeticThumbnail: View {
    let cosmetic: Cosmetic
    var size: CGFloat = 56

    var body: some View {
        switch cosmetic.category {
        case .skin:
            SkinPreview(skin: cosmetic.skin ?? Cosmetic.skinLook(Cosmetic.defaultSkin), size: size * 0.7)
                .frame(width: size, height: size)
        case .trail:
            TrailPreview(trail: cosmetic.trail ?? Cosmetic.trailLook(Cosmetic.defaultTrail), size: size)
        case .theme:
            ThemePreview(theme: cosmetic.theme ?? Cosmetic.themeLook(Cosmetic.defaultTheme), size: size)
        }
    }
}

struct TrailPreview: View {
    let trail: TrailLook
    var size: CGFloat = 56

    var body: some View {
        ZStack {
            if trail.style == .none {
                Image(systemName: "nosign")
                    .font(.system(size: size * 0.4, weight: .bold))
                    .foregroundStyle(VR.secondaryText)
            } else {
                ForEach(0..<6, id: \.self) { index in
                    Circle()
                        .fill(color(for: index))
                        .frame(width: size * (0.3 - CGFloat(index) * 0.035), height: size * (0.3 - CGFloat(index) * 0.035))
                        .offset(y: CGFloat(index) * size * 0.13 - size * 0.15)
                        .opacity(1 - Double(index) * 0.14)
                        .blur(radius: trail.style == .comet ? 1.5 : 0)
                }
                Circle()
                    .fill(.white)
                    .frame(width: size * 0.32, height: size * 0.32)
                    .offset(y: -size * 0.28)
                    .shadow(color: trail.color.color, radius: 6)
            }
        }
        .frame(width: size, height: size)
    }

    private func color(for index: Int) -> Color {
        if trail.style == .rainbow {
            return RGBColor.hsb(Double(index) / 6, 0.85, 1).color
        }
        return trail.color.color
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

/// Shows the currently equipped skin + trail on the equipped theme.
struct LoadoutPreview: View {
    let profile: PlayerProfile
    @State private var bob = false

    var body: some View {
        let theme = Cosmetic.themeLook(profile.equippedTheme)
        let skin = Cosmetic.skinLook(profile.equippedSkin)
        let trail = Cosmetic.trailLook(profile.equippedTrail)
        ZStack {
            LinearGradient(colors: [theme.backgroundTop.color, theme.backgroundBottom.color], startPoint: .top, endPoint: .bottom)
            ForEach(0..<5, id: \.self) { index in
                Circle()
                    .fill((index % 2 == 0 ? theme.hazard : theme.hazardAlt).color)
                    .frame(width: CGFloat(14 + index * 5))
                    .shadow(color: theme.hazard.color, radius: 8)
                    .offset(x: CGFloat([-120, 90, -40, 140, 30][index]), y: CGFloat([-50, -30, -70, 20, -10][index]))
            }
            VStack(spacing: 0) {
                SkinPreview(skin: skin, size: 34)
                TrailPreview(trail: trail, size: 44)
                    .rotationEffect(.degrees(180))
                    .offset(y: -14)
            }
            .offset(y: bob ? 26 : 34)
        }
        .frame(height: 170)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(VR.stroke))
        .overlay(alignment: .bottomLeading) {
            Text("YOUR LOADOUT")
                .font(VR.display(11, weight: .heavy))
                .tracking(1.5)
                .foregroundStyle(.white.opacity(0.7))
                .padding(12)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) { bob = true }
        }
    }
}
