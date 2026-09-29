//
//  Cosmetics.swift
//  VelocityRush
//
//  Skins, trails and themes. Everything is earned in-game with coins,
//  levels or achievements.
//

import Foundation

enum CosmeticCategory: String, Codable, CaseIterable, Identifiable {
    case skin
    case trail
    case theme

    var id: String { rawValue }

    var title: String {
        switch self {
        case .skin: return "Skins"
        case .trail: return "Trails"
        case .theme: return "Themes"
        }
    }

    var icon: String {
        switch self {
        case .skin: return "circle.hexagongrid.fill"
        case .trail: return "wind"
        case .theme: return "paintpalette.fill"
        }
    }
}

enum UnlockRequirement: Equatable {
    case free
    case coins(Int)
    case level(Int)
    case achievement(String)
}

enum SkinStyle: String, Codable {
    case solid, ring, core, prism, void
}

enum TrailStyle: String, Codable {
    case none, spark, comet, bubbles, afterimage, rainbow, stardust
}

struct SkinLook: Equatable {
    var primary: RGBColor
    var glow: RGBColor
    var style: SkinStyle
}

struct TrailLook: Equatable {
    var style: TrailStyle
    var color: RGBColor
}

struct ThemeLook: Equatable {
    var backgroundTop: RGBColor
    var backgroundBottom: RGBColor
    var star: RGBColor
    var hazard: RGBColor
    var hazardAlt: RGBColor
    var accent: RGBColor
    var grid: Bool
}

struct Cosmetic: Identifiable, Equatable {
    let id: String
    let name: String
    let detail: String
    let category: CosmeticCategory
    let requirement: UnlockRequirement
    var skin: SkinLook? = nil
    var trail: TrailLook? = nil
    var theme: ThemeLook? = nil

    var price: Int? {
        if case .coins(let amount) = requirement { return amount }
        return nil
    }

    /// Main swatch colour for previews.
    var swatch: RGBColor {
        skin?.primary ?? trail?.color ?? theme?.hazard ?? .white
    }
}

extension Cosmetic {
    static let defaultSkin = "skin.classic"
    static let defaultTrail = "trail.spark"
    static let defaultTheme = "theme.deepSpace"

    static var defaultUnlocked: Set<String> {
        Set(catalog.filter { $0.requirement == .free }.map(\.id))
    }

    static func find(_ id: String) -> Cosmetic? { catalog.first { $0.id == id } }

    static func items(in category: CosmeticCategory) -> [Cosmetic] {
        catalog.filter { $0.category == category }
    }

    static func skinLook(_ id: String) -> SkinLook {
        find(id)?.skin ?? find(defaultSkin)!.skin!
    }

    static func trailLook(_ id: String) -> TrailLook {
        find(id)?.trail ?? find(defaultTrail)!.trail!
    }

    static func themeLook(_ id: String) -> ThemeLook {
        find(id)?.theme ?? find(defaultTheme)!.theme!
    }

    static let catalog: [Cosmetic] = [
        // MARK: Skins
        Cosmetic(id: "skin.classic", name: "Classic", detail: "Where it all began.", category: .skin,
                 requirement: .free,
                 skin: SkinLook(primary: .white, glow: RGBColor(hex: 0xBFD9FF), style: .solid)),
        Cosmetic(id: "skin.neon", name: "Neon", detail: "Electric cyan with a halo.", category: .skin,
                 requirement: .coins(150),
                 skin: SkinLook(primary: RGBColor(hex: 0x38E1FF), glow: RGBColor(hex: 0x38E1FF), style: .ring)),
        Cosmetic(id: "skin.ember", name: "Ember", detail: "Burning bright at the core.", category: .skin,
                 requirement: .coins(250),
                 skin: SkinLook(primary: RGBColor(hex: 0xFF7A1A), glow: RGBColor(hex: 0xFFB36B), style: .core)),
        Cosmetic(id: "skin.toxic", name: "Toxic", detail: "Radioactive green.", category: .skin,
                 requirement: .coins(250),
                 skin: SkinLook(primary: RGBColor(hex: 0x9DFF3D), glow: RGBColor(hex: 0x9DFF3D), style: .solid)),
        Cosmetic(id: "skin.plasma", name: "Plasma", detail: "Contained lightning.", category: .skin,
                 requirement: .coins(400),
                 skin: SkinLook(primary: RGBColor(hex: 0xD14DFF), glow: RGBColor(hex: 0xF0A6FF), style: .ring)),
        Cosmetic(id: "skin.frost", name: "Frost", detail: "Cool under pressure.", category: .skin,
                 requirement: .coins(400),
                 skin: SkinLook(primary: RGBColor(hex: 0xBFEFFF), glow: RGBColor(hex: 0x7FD8FF), style: .core)),
        Cosmetic(id: "skin.phoenix", name: "Phoenix", detail: "Rise from every near miss.", category: .skin,
                 requirement: .coins(1200),
                 skin: SkinLook(primary: RGBColor(hex: 0xFF3D3D), glow: RGBColor(hex: 0xFFC53D), style: .core)),
        Cosmetic(id: "skin.gold", name: "Gold", detail: "Unlock: survive 2 minutes in Endless.", category: .skin,
                 requirement: .achievement("survive_120"),
                 skin: SkinLook(primary: RGBColor(hex: 0xFFD84D), glow: RGBColor(hex: 0xFFF1A8), style: .ring)),
        Cosmetic(id: "skin.void", name: "Void", detail: "Unlock: survive 3 minutes in Endless.", category: .skin,
                 requirement: .achievement("survive_180"),
                 skin: SkinLook(primary: RGBColor(hex: 0x0A0A12), glow: .white, style: .void)),
        Cosmetic(id: "skin.prism", name: "Prism", detail: "Unlock: reach level 20.", category: .skin,
                 requirement: .level(20),
                 skin: SkinLook(primary: .white, glow: .white, style: .prism)),

        // MARK: Trails
        Cosmetic(id: "trail.none", name: "None", detail: "Clean and minimal.", category: .trail,
                 requirement: .free,
                 trail: TrailLook(style: .none, color: RGBColor(hex: 0x808080))),
        Cosmetic(id: "trail.spark", name: "Spark", detail: "A light sprinkle of sparks.", category: .trail,
                 requirement: .free,
                 trail: TrailLook(style: .spark, color: RGBColor(hex: 0xBFD9FF))),
        Cosmetic(id: "trail.comet", name: "Comet", detail: "A long blazing tail.", category: .trail,
                 requirement: .coins(200),
                 trail: TrailLook(style: .comet, color: RGBColor(hex: 0xFF9A3D))),
        Cosmetic(id: "trail.bubbles", name: "Bubbles", detail: "Pop pop pop.", category: .trail,
                 requirement: .coins(300),
                 trail: TrailLook(style: .bubbles, color: RGBColor(hex: 0x6FD6FF))),
        Cosmetic(id: "trail.afterimage", name: "Afterimage", detail: "Too fast to see.", category: .trail,
                 requirement: .coins(450),
                 trail: TrailLook(style: .afterimage, color: RGBColor(hex: 0xFFFFFF))),
        Cosmetic(id: "trail.rainbow", name: "Rainbow", detail: "Every colour at once.", category: .trail,
                 requirement: .coins(900),
                 trail: TrailLook(style: .rainbow, color: RGBColor(hex: 0xFF4FD8))),
        Cosmetic(id: "trail.stardust", name: "Stardust", detail: "Unlock: reach a x6 multiplier.", category: .trail,
                 requirement: .achievement("multiplier_6"),
                 trail: TrailLook(style: .stardust, color: RGBColor(hex: 0xFFE27A))),

        // MARK: Themes
        Cosmetic(id: "theme.deepSpace", name: "Deep Space", detail: "The classic void.", category: .theme,
                 requirement: .free,
                 theme: ThemeLook(backgroundTop: RGBColor(hex: 0x0E0B2E), backgroundBottom: RGBColor(hex: 0x000000),
                                  star: .white, hazard: RGBColor(hex: 0xFF3D5A), hazardAlt: RGBColor(hex: 0xFF8A3D),
                                  accent: RGBColor(hex: 0x38E1FF), grid: false)),
        Cosmetic(id: "theme.synthwave", name: "Synthwave", detail: "Retro neon highway.", category: .theme,
                 requirement: .coins(500),
                 theme: ThemeLook(backgroundTop: RGBColor(hex: 0x2B0B3F), backgroundBottom: RGBColor(hex: 0x0D0221),
                                  star: RGBColor(hex: 0xFF9CEB), hazard: RGBColor(hex: 0xFF7B00), hazardAlt: RGBColor(hex: 0xFF2E97),
                                  accent: RGBColor(hex: 0x00F0FF), grid: true)),
        Cosmetic(id: "theme.abyss", name: "Abyss", detail: "The deep ocean.", category: .theme,
                 requirement: .coins(500),
                 theme: ThemeLook(backgroundTop: RGBColor(hex: 0x00345C), backgroundBottom: RGBColor(hex: 0x000814),
                                  star: RGBColor(hex: 0x9BE7FF), hazard: RGBColor(hex: 0xFF6F61), hazardAlt: RGBColor(hex: 0xFFB347),
                                  accent: RGBColor(hex: 0x4DFFDF), grid: false)),
        Cosmetic(id: "theme.matrix", name: "Mainframe", detail: "Follow the white dot.", category: .theme,
                 requirement: .coins(600),
                 theme: ThemeLook(backgroundTop: RGBColor(hex: 0x002010), backgroundBottom: RGBColor(hex: 0x000000),
                                  star: RGBColor(hex: 0x39FF14), hazard: RGBColor(hex: 0xFF3131), hazardAlt: RGBColor(hex: 0xFFE14D),
                                  accent: RGBColor(hex: 0x39FF14), grid: true)),
        Cosmetic(id: "theme.sunset", name: "Sunset Drive", detail: "Warm skies, hot hazards.", category: .theme,
                 requirement: .coins(700),
                 theme: ThemeLook(backgroundTop: RGBColor(hex: 0x5A1F4F), backgroundBottom: RGBColor(hex: 0x160A2A),
                                  star: RGBColor(hex: 0xFFD1A6), hazard: RGBColor(hex: 0xFF2E63), hazardAlt: RGBColor(hex: 0xFF9F1C),
                                  accent: RGBColor(hex: 0xFFC857), grid: false)),
        Cosmetic(id: "theme.aurora", name: "Aurora", detail: "Unlock: reach level 15.", category: .theme,
                 requirement: .level(15),
                 theme: ThemeLook(backgroundTop: RGBColor(hex: 0x03324A), backgroundBottom: RGBColor(hex: 0x05000F),
                                  star: RGBColor(hex: 0xA6FFCB), hazard: RGBColor(hex: 0xFF4FA3), hazardAlt: RGBColor(hex: 0xB37BFF),
                                  accent: RGBColor(hex: 0x6BFFB8), grid: false)),
        Cosmetic(id: "theme.noir", name: "Noir", detail: "Unlock: survive 60s on Expert.", category: .theme,
                 requirement: .achievement("expert_60"),
                 theme: ThemeLook(backgroundTop: RGBColor(hex: 0x1C1C1C), backgroundBottom: RGBColor(hex: 0x000000),
                                  star: RGBColor(hex: 0xD9D9D9), hazard: RGBColor(hex: 0xFF1E1E), hazardAlt: RGBColor(hex: 0xB30000),
                                  accent: RGBColor(hex: 0xFF1E1E), grid: false))
    ]
}
