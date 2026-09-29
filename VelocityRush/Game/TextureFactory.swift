//
//  TextureFactory.swift
//  VelocityRush
//
//  Procedurally draws every sprite (glowing orbs, stars, crystals, power-up
//  icons, vignettes) so the game ships with zero image assets.
//

import SpriteKit
import UIKit

@MainActor
enum TextureFactory {
    /// Canvas size (points) for orb textures and the core radius inside it.
    static let orbCanvas: CGFloat = 128
    static let orbCore: CGFloat = 40
    /// Sprite size multiplier so the *core* matches the entity radius.
    static var glowRatio: CGFloat { orbCanvas / (orbCore * 2) }

    private static var cache: [String: SKTexture] = [:]

    private static func cached(_ key: String, size: CGSize, scale: CGFloat = 2,
                               draw: (CGContext, CGSize) -> Void) -> SKTexture {
        if let texture = cache[key] { return texture }
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let image = renderer.image { context in
            draw(context.cgContext, size)
        }
        let texture = SKTexture(image: image)
        cache[key] = texture
        return texture
    }

    private static func key(_ parts: Any...) -> String {
        parts.map { "\($0)" }.joined(separator: "|")
    }

    private static func radialGlow(_ context: CGContext, center: CGPoint, from inner: CGFloat, to outer: CGFloat,
                                   color: UIColor, alpha: CGFloat) {
        let colors = [color.withAlphaComponent(alpha).cgColor, color.withAlphaComponent(0).cgColor] as CFArray
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) else { return }
        context.drawRadialGradient(gradient, startCenter: center, startRadius: inner,
                                   endCenter: center, endRadius: outer, options: [])
    }

    private static func circle(_ center: CGPoint, _ radius: CGFloat) -> CGRect {
        CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
    }

    // MARK: Hazards

    enum OrbDecoration: String {
        case plain, ring, core, hollow, dashed
    }

    static func hazard(color: RGBColor, decoration: OrbDecoration, outline: Bool) -> SKTexture {
        let size = CGSize(width: orbCanvas, height: orbCanvas)
        return cached(key("hazard", color, decoration, outline), size: size) { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let base = color.uiColor
            radialGlow(context, center: center, from: orbCore * 0.7, to: size.width / 2, color: base, alpha: 0.6)

            // Body with a subtle highlight
            let bodyColors = [color.lighter(0.35).uiColor.cgColor, base.cgColor, color.darker(0.25).uiColor.cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: bodyColors, locations: [0, 0.55, 1]) {
                context.saveGState()
                context.addEllipse(in: circle(center, orbCore))
                context.clip()
                context.drawRadialGradient(gradient,
                                           startCenter: CGPoint(x: center.x - orbCore * 0.35, y: center.y - orbCore * 0.35),
                                           startRadius: 0, endCenter: center, endRadius: orbCore, options: [.drawsAfterEndLocation])
                context.restoreGState()
            }

            switch decoration {
            case .plain:
                break
            case .ring:
                context.setStrokeColor(UIColor.white.withAlphaComponent(0.55).cgColor)
                context.setLineWidth(4)
                context.strokeEllipse(in: circle(center, orbCore - 9))
            case .core:
                context.setFillColor(color.lighter(0.7).uiColor.cgColor)
                context.fillEllipse(in: circle(center, orbCore * 0.35))
            case .hollow:
                context.setFillColor(color.darker(0.55).uiColor.cgColor)
                context.fillEllipse(in: circle(center, orbCore * 0.55))
            case .dashed:
                context.setStrokeColor(UIColor.white.withAlphaComponent(0.7).cgColor)
                context.setLineWidth(4)
                context.setLineDash(phase: 0, lengths: [8, 7])
                context.strokeEllipse(in: circle(center, orbCore - 8))
                context.setLineDash(phase: 0, lengths: [])
            }

            if outline {
                context.setStrokeColor(UIColor.white.cgColor)
                context.setLineWidth(5)
                context.strokeEllipse(in: circle(center, orbCore - 2.5))
            }
        }
    }

    // MARK: Player skins

    static func skin(_ look: SkinLook) -> SKTexture {
        let size = CGSize(width: orbCanvas, height: orbCanvas)
        return cached(key("skin", look.primary, look.glow, look.style), size: size) { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            radialGlow(context, center: center, from: orbCore * 0.8, to: size.width / 2, color: look.glow.uiColor, alpha: 0.75)

            switch look.style {
            case .solid:
                context.setFillColor(look.primary.uiColor.cgColor)
                context.fillEllipse(in: circle(center, orbCore))
            case .ring:
                context.setFillColor(look.primary.uiColor.cgColor)
                context.fillEllipse(in: circle(center, orbCore * 0.78))
                context.setStrokeColor(UIColor.white.withAlphaComponent(0.95).cgColor)
                context.setLineWidth(5)
                context.strokeEllipse(in: circle(center, orbCore - 2.5))
            case .core:
                let colors = [UIColor.white.cgColor, look.primary.uiColor.cgColor] as CFArray
                if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0.1, 1]) {
                    context.saveGState()
                    context.addEllipse(in: circle(center, orbCore))
                    context.clip()
                    context.drawRadialGradient(gradient, startCenter: center, startRadius: 0,
                                               endCenter: center, endRadius: orbCore, options: [])
                    context.restoreGState()
                }
            case .prism:
                // White body – tinted at runtime through colorBlendFactor.
                context.setFillColor(UIColor.white.cgColor)
                context.fillEllipse(in: circle(center, orbCore))
                context.setStrokeColor(UIColor.white.withAlphaComponent(0.6).cgColor)
                context.setLineWidth(3)
                context.strokeEllipse(in: circle(center, orbCore * 0.6))
            case .void:
                context.setFillColor(look.primary.uiColor.cgColor)
                context.fillEllipse(in: circle(center, orbCore))
                context.setStrokeColor(UIColor.white.cgColor)
                context.setLineWidth(6)
                context.strokeEllipse(in: circle(center, orbCore - 3))
            }
        }
    }

    // MARK: Pickups

    static func star(color: RGBColor) -> SKTexture {
        let size = CGSize(width: 96, height: 96)
        return cached(key("star", color), size: size) { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            radialGlow(context, center: center, from: 10, to: size.width / 2, color: color.uiColor, alpha: 0.7)
            let path = starPath(center: center, outer: 30, inner: 13, points: 5)
            context.addPath(path)
            context.setFillColor(color.uiColor.cgColor)
            context.fillPath()
            context.addPath(starPath(center: center, outer: 16, inner: 7, points: 5))
            context.setFillColor(color.lighter(0.6).uiColor.cgColor)
            context.fillPath()
        }
    }

    static func crystal(color: RGBColor) -> SKTexture {
        let size = CGSize(width: 96, height: 96)
        return cached(key("crystal", color), size: size) { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            radialGlow(context, center: center, from: 10, to: size.width / 2, color: color.uiColor, alpha: 0.7)
            let path = CGMutablePath()
            path.move(to: CGPoint(x: center.x, y: center.y - 32))
            path.addLine(to: CGPoint(x: center.x + 20, y: center.y))
            path.addLine(to: CGPoint(x: center.x, y: center.y + 32))
            path.addLine(to: CGPoint(x: center.x - 20, y: center.y))
            path.closeSubpath()
            context.addPath(path)
            context.setFillColor(color.uiColor.cgColor)
            context.fillPath()
            context.move(to: CGPoint(x: center.x, y: center.y - 32))
            context.addLine(to: CGPoint(x: center.x, y: center.y + 32))
            context.move(to: CGPoint(x: center.x - 20, y: center.y))
            context.addLine(to: CGPoint(x: center.x + 20, y: center.y))
            context.setStrokeColor(UIColor.white.withAlphaComponent(0.7).cgColor)
            context.setLineWidth(2.5)
            context.strokePath()
        }
    }

    static func powerUp(_ kind: PickupKind) -> SKTexture {
        let size = CGSize(width: orbCanvas, height: orbCanvas)
        return cached(key("powerup", kind.rawValue), size: size) { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let color = kind.color.uiColor
            radialGlow(context, center: center, from: orbCore * 0.8, to: size.width / 2, color: color, alpha: 0.7)
            context.setFillColor(color.withAlphaComponent(0.35).cgColor)
            context.fillEllipse(in: circle(center, orbCore))
            context.setStrokeColor(color.cgColor)
            context.setLineWidth(5)
            context.strokeEllipse(in: circle(center, orbCore - 2.5))

            let configuration = UIImage.SymbolConfiguration(pointSize: 30, weight: .black)
            if let symbol = UIImage(systemName: kind.icon, withConfiguration: configuration)?
                .withTintColor(.white, renderingMode: .alwaysOriginal) {
                let fit = min(40 / symbol.size.width, 40 / symbol.size.height, 1)
                let symbolSize = CGSize(width: symbol.size.width * fit, height: symbol.size.height * fit)
                symbol.draw(in: CGRect(x: center.x - symbolSize.width / 2, y: center.y - symbolSize.height / 2,
                                       width: symbolSize.width, height: symbolSize.height))
            }
        }
    }

    static func warningIcon(color: RGBColor) -> SKTexture {
        let size = CGSize(width: 64, height: 64)
        return cached(key("warning", color), size: size) { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            radialGlow(context, center: center, from: 6, to: 32, color: color.uiColor, alpha: 0.6)
            let configuration = UIImage.SymbolConfiguration(pointSize: 30, weight: .black)
            if let symbol = UIImage(systemName: "exclamationmark.triangle.fill", withConfiguration: configuration)?
                .withTintColor(color.uiColor, renderingMode: .alwaysOriginal) {
                let rect = CGRect(x: center.x - symbol.size.width / 2, y: center.y - symbol.size.height / 2,
                                  width: symbol.size.width, height: symbol.size.height)
                symbol.draw(in: rect)
            }
        }
    }

    // MARK: Particles & effects

    static var softDot: SKTexture {
        cached("softDot", size: CGSize(width: 24, height: 24)) { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            radialGlow(context, center: center, from: 0, to: 12, color: .white, alpha: 1)
        }
    }

    static var hardDot: SKTexture {
        cached("hardDot", size: CGSize(width: 16, height: 16)) { context, size in
            context.setFillColor(UIColor.white.cgColor)
            context.fillEllipse(in: CGRect(origin: .zero, size: size).insetBy(dx: 2, dy: 2))
        }
    }

    static var bubble: SKTexture {
        cached("bubble", size: CGSize(width: 32, height: 32)) { context, size in
            context.setStrokeColor(UIColor.white.cgColor)
            context.setLineWidth(2.5)
            context.strokeEllipse(in: CGRect(origin: .zero, size: size).insetBy(dx: 3, dy: 3))
            context.setFillColor(UIColor.white.withAlphaComponent(0.8).cgColor)
            context.fillEllipse(in: CGRect(x: 9, y: 8, width: 5, height: 5))
        }
    }

    static var tinyStar: SKTexture {
        cached("tinyStar", size: CGSize(width: 24, height: 24)) { context, size in
            context.addPath(starPath(center: CGPoint(x: 12, y: 12), outer: 11, inner: 4.5, points: 4))
            context.setFillColor(UIColor.white.cgColor)
            context.fillPath()
        }
    }

    static func verticalGradient(top: RGBColor, bottom: RGBColor) -> SKTexture {
        cached(key("gradient", top, bottom), size: CGSize(width: 8, height: 256), scale: 1) { context, size in
            let colors = [top.uiColor.cgColor, bottom.uiColor.cgColor] as CFArray
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) else { return }
            context.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
        }
    }

    /// Dark overlay with a clear hole in the middle (Blackout modifier).
    /// `clearFraction`/`darkFraction` are radii relative to the texture half-width.
    static func vignette(clearFraction: CGFloat, darkFraction: CGFloat) -> SKTexture {
        let size = CGSize(width: 512, height: 512)
        return cached(key("vignette", clearFraction, darkFraction), size: size, scale: 1) { context, size in
            context.setFillColor(UIColor.black.cgColor)
            context.fill(CGRect(origin: .zero, size: size))
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let half = size.width / 2
            context.setBlendMode(.clear)
            context.fillEllipse(in: circle(center, half * clearFraction))
            context.setBlendMode(.normal)
            // Soft edge between clear and dark.
            let colors = [UIColor.black.withAlphaComponent(0).cgColor, UIColor.black.cgColor] as CFArray
            if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
                context.setBlendMode(.copy)
                context.drawRadialGradient(gradient, startCenter: center, startRadius: half * clearFraction,
                                           endCenter: center, endRadius: half * darkFraction, options: [])
                context.setBlendMode(.normal)
            }
        }
    }

    private static func starPath(center: CGPoint, outer: CGFloat, inner: CGFloat, points: Int) -> CGPath {
        let path = CGMutablePath()
        let count = points * 2
        for i in 0..<count {
            let radius = i % 2 == 0 ? outer : inner
            let angle = CGFloat(i) * .pi / CGFloat(points) - .pi / 2
            let point = CGPoint(x: center.x + cos(angle) * radius, y: center.y + sin(angle) * radius)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}
