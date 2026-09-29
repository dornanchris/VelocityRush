//
//  RGBColor.swift
//  VelocityRush
//
//  Platform-neutral colour so game data (themes, skins, modes) can live in
//  the pure Swift core. UI layers convert it to Color / UIColor.
//

import Foundation

struct RGBColor: Equatable, Hashable, Codable {
    var red: Double
    var green: Double
    var blue: Double
    var alpha: Double

    init(red: Double, green: Double, blue: Double, alpha: Double = 1) {
        self.red = red
        self.green = green
        self.blue = blue
        self.alpha = alpha
    }

    init(hex: UInt32, alpha: Double = 1) {
        self.red = Double((hex >> 16) & 0xFF) / 255
        self.green = Double((hex >> 8) & 0xFF) / 255
        self.blue = Double(hex & 0xFF) / 255
        self.alpha = alpha
    }

    func withAlpha(_ alpha: Double) -> RGBColor {
        RGBColor(red: red, green: green, blue: blue, alpha: alpha)
    }

    /// Mixes towards `other` by `amount` (0...1).
    func mixed(with other: RGBColor, amount: Double) -> RGBColor {
        let t = clamp(amount, 0, 1)
        return RGBColor(red: lerp(red, other.red, t),
                        green: lerp(green, other.green, t),
                        blue: lerp(blue, other.blue, t),
                        alpha: lerp(alpha, other.alpha, t))
    }

    func lighter(_ amount: Double = 0.3) -> RGBColor { mixed(with: .white, amount: amount) }
    func darker(_ amount: Double = 0.3) -> RGBColor { mixed(with: .black, amount: amount) }

    static let white = RGBColor(red: 1, green: 1, blue: 1)
    static let black = RGBColor(red: 0, green: 0, blue: 0)

    /// Builds a colour from hue/saturation/brightness (all 0...1).
    static func hsb(_ hue: Double, _ saturation: Double, _ brightness: Double) -> RGBColor {
        let h = (hue - floor(hue)) * 6
        let c = brightness * saturation
        let x = c * (1 - abs(h.truncatingRemainder(dividingBy: 2) - 1))
        let m = brightness - c
        let (r, g, b): (Double, Double, Double)
        switch Int(h) {
        case 0: (r, g, b) = (c, x, 0)
        case 1: (r, g, b) = (x, c, 0)
        case 2: (r, g, b) = (0, c, x)
        case 3: (r, g, b) = (0, x, c)
        case 4: (r, g, b) = (x, 0, c)
        default: (r, g, b) = (c, 0, x)
        }
        return RGBColor(red: r + m, green: g + m, blue: b + m)
    }
}
