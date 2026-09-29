//
//  DesignSystem.swift
//  VelocityRush
//
//  Shared colours, fonts, buttons and cards for the neon look.
//

import SwiftUI

enum VR {
    static let background = Color(red: 0.03, green: 0.02, blue: 0.07)
    static let backgroundTop = Color(red: 0.08, green: 0.05, blue: 0.2)
    static let card = Color.white.opacity(0.06)
    static let cardStrong = Color.white.opacity(0.1)
    static let stroke = Color.white.opacity(0.12)
    static let cyan = Color(red: 0.22, green: 0.88, blue: 1.0)
    static let pink = Color(red: 1.0, green: 0.24, blue: 0.43)
    static let gold = Color(red: 1.0, green: 0.85, blue: 0.3)
    static let green = Color(red: 0.42, green: 1.0, blue: 0.62)
    static let purple = Color(red: 0.69, green: 0.49, blue: 1.0)
    static let secondaryText = Color.white.opacity(0.6)

    static func display(_ size: CGFloat, weight: Font.Weight = .heavy) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }

    static let brandGradient = LinearGradient(colors: [cyan, purple, pink],
                                              startPoint: .leading, endPoint: .trailing)
}

// MARK: - Backgrounds

struct ScreenBackground: View {
    var body: some View {
        LinearGradient(colors: [VR.backgroundTop, VR.background], startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea()
    }
}

/// Slowly falling glowing dots, drawn with Canvas for the menus.
struct AnimatedBackdrop: View {
    var tint: Color = VR.pink
    @AppStorage(SettingsKey.reducedMotion) private var reducedMotion = false

    var body: some View {
        ZStack {
            ScreenBackground()
            TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: reducedMotion)) { timeline in
                Canvas { context, size in
                    let time = timeline.date.timeIntervalSinceReferenceDate
                    for index in 0..<26 {
                        let seed = Double(index) * 12.9898
                        let fraction = (sin(seed) * 43_758.5453).truncatingRemainder(dividingBy: 1)
                        let xFraction = abs(fraction)
                        let speed = 18 + Double(index % 5) * 9
                        let radius = 2.0 + Double(index % 4) * 2.2
                        let travel = size.height + 60
                        let y = (time * speed + Double(index) * 97).truncatingRemainder(dividingBy: travel) - 30
                        let point = CGPoint(x: xFraction * size.width, y: y)
                        let rect = CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)
                        let color = index % 3 == 0 ? VR.cyan : tint
                        context.opacity = 0.25 + Double(index % 3) * 0.12
                        context.fill(Path(ellipseIn: rect.insetBy(dx: -radius, dy: -radius)), with: .color(color.opacity(0.25)))
                        context.fill(Path(ellipseIn: rect), with: .color(color))
                    }
                }
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
    }
}

// MARK: - Cards

struct GlassCard: ViewModifier {
    var cornerRadius: CGFloat = 20
    var tint: Color? = nil

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(VR.card)
                    .overlay(
                        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                            .fill(LinearGradient(colors: [(tint ?? .white).opacity(0.14), .clear],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing))
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder((tint ?? .white).opacity(tint == nil ? 0.12 : 0.35), lineWidth: 1)
            )
    }
}

extension View {
    func glassCard(cornerRadius: CGFloat = 20, tint: Color? = nil) -> some View {
        modifier(GlassCard(cornerRadius: cornerRadius, tint: tint))
    }

    func neonGlow(_ color: Color, radius: CGFloat = 12) -> some View {
        shadow(color: color.opacity(0.7), radius: radius)
    }
}

// MARK: - Buttons

struct NeonButtonStyle: ButtonStyle {
    var color: Color = VR.cyan
    var secondary: Color? = nil
    var height: CGFloat = 58

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(VR.display(20))
            .foregroundStyle(.black.opacity(0.85))
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(
                Capsule().fill(LinearGradient(colors: [color, secondary ?? color.opacity(0.75)],
                                              startPoint: .topLeading, endPoint: .bottomTrailing))
            )
            .overlay(Capsule().strokeBorder(.white.opacity(0.35), lineWidth: 1))
            .shadow(color: color.opacity(configuration.isPressed ? 0.3 : 0.6), radius: configuration.isPressed ? 6 : 16)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

struct GhostButtonStyle: ButtonStyle {
    var height: CGFloat = 52

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(VR.display(17, weight: .bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(Capsule().fill(VR.cardStrong))
            .overlay(Capsule().strokeBorder(VR.stroke, lineWidth: 1))
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

// MARK: - Badges

struct CoinBadge: View {
    let amount: Int
    var compact = false

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "star.circle.fill")
                .foregroundStyle(VR.gold)
                .neonGlow(VR.gold, radius: 6)
            Text(amount.formatted())
                .font(VR.display(compact ? 15 : 18, weight: .bold))
                .foregroundStyle(.white)
                .contentTransition(.numericText(value: Double(amount)))
                .animation(.spring, value: amount)
        }
        .padding(.horizontal, compact ? 10 : 14)
        .padding(.vertical, compact ? 6 : 8)
        .background(Capsule().fill(VR.cardStrong))
        .overlay(Capsule().strokeBorder(VR.gold.opacity(0.35), lineWidth: 1))
    }
}

struct LevelRing: View {
    let level: Int
    let progress: Double
    var size: CGFloat = 48

    var body: some View {
        ZStack {
            Circle().stroke(Color.white.opacity(0.12), lineWidth: 4)
            Circle()
                .trim(from: 0, to: max(0.02, progress))
                .stroke(VR.brandGradient, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring, value: progress)
            Text("\(level)")
                .font(VR.display(size * 0.36))
                .foregroundStyle(.white)
        }
        .frame(width: size, height: size)
    }
}

struct ProgressBar: View {
    let value: Double
    var tint: Color = VR.cyan
    var height: CGFloat = 8

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.1))
                Capsule()
                    .fill(LinearGradient(colors: [tint, tint.opacity(0.7)], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(height, proxy.size.width * CGFloat(min(max(value, 0), 1))))
                    .shadow(color: tint.opacity(0.6), radius: 4)
            }
        }
        .frame(height: height)
        .animation(.spring, value: value)
    }
}

struct SectionTitle: View {
    let text: String
    var icon: String? = nil

    var body: some View {
        HStack(spacing: 8) {
            if let icon {
                Image(systemName: icon).foregroundStyle(VR.cyan)
            }
            Text(text.uppercased())
                .font(VR.display(13, weight: .bold))
                .tracking(1.5)
                .foregroundStyle(VR.secondaryText)
            Spacer()
        }
    }
}

struct Chip: View {
    let text: String
    var icon: String? = nil
    var tint: Color = VR.cyan

    var body: some View {
        HStack(spacing: 5) {
            if let icon { Image(systemName: icon) }
            Text(text)
        }
        .font(VR.display(12, weight: .bold))
        .foregroundStyle(tint)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(tint.opacity(0.15)))
        .overlay(Capsule().strokeBorder(tint.opacity(0.4), lineWidth: 1))
    }
}

/// Glowing orb preview for skins in menus (a lightweight stand-in for the
/// SpriteKit avatar, including its signature effects).
struct SkinPreview: View {
    let skin: SkinLook
    var size: CGFloat = 44
    @State private var spin = false
    @State private var hue: Double = 0

    var body: some View {
        ZStack {
            Circle()
                .fill(skin.glow.color.opacity(skin.has(.pulse) || skin.has(.aura) ? 0.5 : 0.35))
                .frame(width: size * 1.7, height: size * 1.7)
                .blur(radius: size * 0.28)

            base

            if skin.has(.spinRing) {
                Circle()
                    .stroke(skin.accentColor.color, style: StrokeStyle(lineWidth: max(1.5, size * 0.05), dash: [size * 0.16, size * 0.11]))
                    .frame(width: size * 1.75, height: size * 1.75)
                    .rotationEffect(.degrees(spin ? -360 : 0))
            }
            if skin.has(.orbiters) {
                ZStack {
                    ForEach(0..<3, id: \.self) { index in
                        Circle()
                            .fill(skin.accentColor.color)
                            .frame(width: size * 0.2, height: size * 0.2)
                            .shadow(color: skin.accentColor.color, radius: 3)
                            .offset(x: size * 1.0)
                            .rotationEffect(.degrees(Double(index) * 120))
                    }
                }
                .rotationEffect(.degrees(spin ? 360 : 0))
            }
            if skin.has(.sparkle) {
                Image(systemName: "sparkle")
                    .font(.system(size: size * 0.3, weight: .bold))
                    .foregroundStyle(skin.accentColor.color)
                    .offset(x: size * 0.55, y: -size * 0.55)
            }
        }
        .hueRotation(.degrees(skin.has(.hueCycle) ? hue : 0))
        .frame(width: size, height: size)
        .onAppear {
            withAnimation(.linear(duration: 3).repeatForever(autoreverses: false)) { spin = true }
            if skin.has(.hueCycle) {
                withAnimation(.linear(duration: 4).repeatForever(autoreverses: false)) { hue = 360 }
            }
        }
    }

    @ViewBuilder
    private var base: some View {
        switch skin.style {
        case .solid:
            Circle().fill(skin.primary.color)
        case .ring:
            Circle().fill(skin.primary.color.opacity(0.9))
            Circle().strokeBorder(.white.opacity(0.9), lineWidth: size * 0.08).padding(-size * 0.12)
        case .core:
            Circle().fill(RadialGradient(colors: [.white, skin.primary.color], center: .center,
                                         startRadius: 0, endRadius: size * 0.5))
        case .prism:
            Circle().fill(AngularGradient(colors: [.red, .orange, .yellow, .green, .cyan, .blue, .purple, .red],
                                          center: .center))
        case .void:
            Circle().fill(Color.black)
            Circle().strokeBorder(.white, lineWidth: size * 0.1)
        }
    }
}

extension Int {
    /// "1:05" style formatting for seconds.
    var clockString: String {
        String(format: "%d:%02d", self / 60, self % 60)
    }
}

extension Double {
    /// 1.25 → "1.25", 1.3 → "1.3", 1.0 → "1"
    var compactString: String {
        var text = String(format: "%.2f", self)
        while text.hasSuffix("0") { text.removeLast() }
        if text.hasSuffix(".") { text.removeLast() }
        return text
    }
}

extension TimeInterval {
    var countdownString: String {
        let total = Int(self)
        return String(format: "%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
    }
}
