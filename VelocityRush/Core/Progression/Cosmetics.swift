//
//  Cosmetics.swift
//  VelocityRush
//
//  Skins, blade trails and themes. Items have a rarity and are unlocked by
//  some mix of coins and gates (levels, personal bests, mission streaks…).
//  The rarest items can't be bought at all – they have to be earned.
//

import Foundation

// MARK: - Categories & rarity

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

    var singularTitle: String {
        switch self {
        case .skin: return "Skin"
        case .trail: return "Trail"
        case .theme: return "Theme"
        }
    }

    var icon: String {
        switch self {
        case .skin: return "circle.hexagongrid.fill"
        case .trail: return "scribble.variable"
        case .theme: return "paintpalette.fill"
        }
    }
}

enum CosmeticRarity: Int, Comparable, CaseIterable, Identifiable {
    case common, rare, epic, legendary, mythic

    var id: Int { rawValue }

    static func < (lhs: CosmeticRarity, rhs: CosmeticRarity) -> Bool { lhs.rawValue < rhs.rawValue }

    var title: String {
        switch self {
        case .common: return "Common"
        case .rare: return "Rare"
        case .epic: return "Epic"
        case .legendary: return "Legendary"
        case .mythic: return "Mythic"
        }
    }

    var color: RGBColor {
        switch self {
        case .common: return RGBColor(hex: 0xB8C4D6)
        case .rare: return RGBColor(hex: 0x3DA5FF)
        case .epic: return RGBColor(hex: 0xB45CFF)
        case .legendary: return RGBColor(hex: 0xFFB020)
        case .mythic: return RGBColor(hex: 0xFF3D8B)
        }
    }
}

// MARK: - Unlock requirements

enum StatFormat: Equatable {
    case number
    case seconds
}

struct StatGoal: Equatable {
    let metric: KeyPath<PlayerStats, Int>
    let goal: Int
    /// e.g. "Best Endless score"
    let label: String
    var format: StatFormat = .number

    func formatted(_ value: Int) -> String {
        switch format {
        case .number: return value.formatted()
        case .seconds: return String(format: "%d:%02d", value / 60, value % 60)
        }
    }
}

/// A condition that must be met before an item can be bought or is granted.
enum UnlockGate: Equatable {
    case level(Int)
    case achievement(String)
    case stat(StatGoal)

    func isMet(by profile: PlayerProfile) -> Bool {
        let progress = self.progress(in: profile)
        return progress.current >= progress.goal
    }

    func progress(in profile: PlayerProfile) -> (current: Int, goal: Int) {
        switch self {
        case .level(let level):
            return (min(profile.level, level), level)
        case .achievement(let id):
            return (profile.unlockedAchievements[id] != nil ? 1 : 0, 1)
        case .stat(let goal):
            return (min(profile.stats[keyPath: goal.metric], goal.goal), goal.goal)
        }
    }

    var summary: String {
        switch self {
        case .level(let level):
            return "Reach level \(level)"
        case .achievement(let id):
            return "Unlock “\(AchievementCatalog.find(id)?.title ?? id)”"
        case .stat(let goal):
            return "\(goal.label): \(goal.formatted(goal.goal))"
        }
    }

    func progressText(in profile: PlayerProfile) -> String {
        let progress = self.progress(in: profile)
        switch self {
        case .level:
            return "Level \(profile.level) / \(progress.goal)"
        case .achievement:
            return progress.current > 0 ? "Unlocked" : "Locked"
        case .stat(let goal):
            return "\(goal.formatted(profile.stats[keyPath: goal.metric])) / \(goal.formatted(goal.goal))"
        }
    }
}

// MARK: - Looks

enum SkinStyle: String, Codable {
    case solid, ring, core, prism, void
}

/// Animated extras layered on top of a skin by the renderer.
enum SkinEffect: String, Codable, CaseIterable {
    /// Breathing glow.
    case pulse
    /// Little moons circling the dot.
    case orbiters
    /// Particles streaming off the dot.
    case aura
    /// A rotating dashed halo.
    case spinRing
    /// RGB-split jitter.
    case glitch
    /// Twinkling sparkles around the dot.
    case sparkle
    /// Cycles through every hue.
    case hueCycle
}

struct SkinLook: Equatable {
    var primary: RGBColor
    var glow: RGBColor
    var style: SkinStyle
    var effects: [SkinEffect] = []
    var accent: RGBColor? = nil

    var accentColor: RGBColor { accent ?? glow }
    func has(_ effect: SkinEffect) -> Bool { effects.contains(effect) }
}

enum TrailColorMode: String, Codable {
    /// One colour.
    case solid
    /// Blends through `colors` from head to tail.
    case gradient
    /// Cycles through the rainbow over time and along the blade.
    case rainbow
    /// Alternates between the first two colours.
    case stripes
    /// Gradient with electric flicker.
    case flicker
}

enum TrailParticles: String, Codable {
    case none, sparks, embers, snow, stars, bubbles, zaps
}

/// A Fruit Ninja–style blade trail: a tapered ribbon plus optional particles.
struct TrailLook: Equatable {
    var colors: [RGBColor]
    var mode: TrailColorMode
    /// Ribbon width relative to the player's diameter (0 = no ribbon).
    var width: Double
    /// Number of ribbon points (longer = longer blade).
    var length: Int
    /// Sideways electric wobble, 0...1.
    var jitter: Double = 0
    var particles: TrailParticles = .none
    /// Additive glow blending.
    var glow: Bool = true
    /// Leaves fading copies of the player behind.
    var afterimage: Bool = false

    var hasRibbon: Bool { width > 0 && length > 1 }

    /// Colour of the ribbon at `t` (0 = head, 1 = tail) for segment `index` at `time`.
    func color(at t: Double, index: Int, time: Double) -> RGBColor {
        let t = clamp(t, 0, 1)
        switch mode {
        case .solid:
            return colors.first ?? .white
        case .gradient:
            return Self.sample(colors, t)
        case .rainbow:
            return RGBColor.hsb(time * 0.45 + t * 0.9, 0.85, 1)
        case .stripes:
            let palette = colors.isEmpty ? [RGBColor.white] : colors
            return palette[(index / 2) % palette.count]
        case .flicker:
            let base = Self.sample(colors, t)
            let flash = (sin(time * 47 + Double(index) * 1.9) + 1) / 2
            return base.lighter(flash * 0.6)
        }
    }

    /// Main colour for previews and particles.
    var primary: RGBColor {
        mode == .rainbow ? RGBColor(hex: 0xFF4FD8) : (colors.first ?? .white)
    }

    private static func sample(_ colors: [RGBColor], _ t: Double) -> RGBColor {
        guard colors.count > 1 else { return colors.first ?? .white }
        let scaled = t * Double(colors.count - 1)
        let index = min(Int(scaled), colors.count - 2)
        return colors[index].mixed(with: colors[index + 1], amount: scaled - Double(index))
    }
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

// MARK: - Cosmetic

struct Cosmetic: Identifiable, Equatable {
    let id: String
    let name: String
    let detail: String
    let category: CosmeticCategory
    let rarity: CosmeticRarity
    /// Coin price, or nil if it can't be bought.
    var price: Int? = nil
    /// Conditions that must be met first (to buy, or to be granted if there's no price).
    var gates: [UnlockGate] = []
    var skin: SkinLook? = nil
    var trail: TrailLook? = nil
    var theme: ThemeLook? = nil

    var isFree: Bool { price == nil && gates.isEmpty }
    /// Earned-only items are granted automatically once every gate is met.
    var isEarnedOnly: Bool { price == nil && !gates.isEmpty }

    /// Main swatch colour for previews.
    var swatch: RGBColor {
        skin?.primary ?? trail?.primary ?? theme?.hazard ?? .white
    }
}

extension Cosmetic {
    static let defaultSkin = "skin.classic"
    static let defaultTrail = "trail.streak"
    static let defaultTheme = "theme.deepSpace"

    static var defaultUnlocked: Set<String> {
        Set(catalog.filter(\.isFree).map(\.id))
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

    // MARK: Gate shorthands

    private static func stat(_ metric: KeyPath<PlayerStats, Int>, _ goal: Int, _ label: String,
                             _ format: StatFormat = .number) -> UnlockGate {
        .stat(StatGoal(metric: metric, goal: goal, label: label, format: format))
    }

    private static func c(_ hex: UInt32) -> RGBColor { RGBColor(hex: hex) }

    // MARK: Catalog

    static let catalog: [Cosmetic] = skins + trails + themes

    static let skins: [Cosmetic] = [
        Cosmetic(id: "skin.classic", name: "Classic", detail: "Where it all began.",
                 category: .skin, rarity: .common,
                 skin: SkinLook(primary: .white, glow: c(0xBFD9FF), style: .solid)),
        Cosmetic(id: "skin.neon", name: "Neon", detail: "Electric cyan with a breathing halo.",
                 category: .skin, rarity: .common, price: 1_500,
                 skin: SkinLook(primary: c(0x38E1FF), glow: c(0x38E1FF), style: .ring, effects: [.pulse])),
        Cosmetic(id: "skin.ember", name: "Ember", detail: "A hot core that sheds sparks.",
                 category: .skin, rarity: .common, price: 2_000,
                 skin: SkinLook(primary: c(0xFF7A1A), glow: c(0xFFB36B), style: .core, effects: [.aura],
                                accent: c(0xFF9A3D))),
        Cosmetic(id: "skin.toxic", name: "Toxic", detail: "Radioactive and proud of it.",
                 category: .skin, rarity: .common, price: 2_500, gates: [.level(5)],
                 skin: SkinLook(primary: c(0x9DFF3D), glow: c(0x9DFF3D), style: .solid, effects: [.pulse])),
        Cosmetic(id: "skin.plasma", name: "Plasma", detail: "Contained lightning in a spinning cage.",
                 category: .skin, rarity: .rare, price: 5_000, gates: [.level(10)],
                 skin: SkinLook(primary: c(0xD14DFF), glow: c(0xF0A6FF), style: .ring, effects: [.spinRing],
                                accent: c(0xF0A6FF))),
        Cosmetic(id: "skin.frost", name: "Frost", detail: "Cool under pressure. Earned by surviving 90s in Endless.",
                 category: .skin, rarity: .rare,
                 gates: [stat(\.bestEndlessTime, 90, "Survive in Endless", .seconds)],
                 skin: SkinLook(primary: c(0xBFEFFF), glow: c(0x7FD8FF), style: .core, effects: [.sparkle],
                                accent: c(0xE6F9FF))),
        Cosmetic(id: "skin.sakura", name: "Sakura", detail: "Petals in orbit. Earned by clearing every daily mission 7 days in a row.",
                 category: .skin, rarity: .rare,
                 gates: [stat(\.longestAllClearStreak, 7, "All-clear streak (days)")],
                 skin: SkinLook(primary: c(0xFF8FC8), glow: c(0xFFD1E8), style: .core, effects: [.orbiters, .sparkle],
                                accent: c(0xFFD1E8))),
        Cosmetic(id: "skin.supernova", name: "Supernova", detail: "A star on the edge of exploding.",
                 category: .skin, rarity: .epic, price: 12_000,
                 gates: [stat(\.bestEndlessScore, 8_000, "Best Endless score")],
                 skin: SkinLook(primary: c(0xFFD24D), glow: c(0xFF9A3D), style: .core, effects: [.aura, .pulse],
                                accent: c(0xFF6A00))),
        Cosmetic(id: "skin.orbital", name: "Orbital", detail: "Three moons and a spinning halo.",
                 category: .skin, rarity: .epic,
                 gates: [.level(25)],
                 skin: SkinLook(primary: c(0x4DFFD2), glow: c(0x4DFFD2), style: .ring, effects: [.orbiters, .spinRing],
                                accent: c(0x4DA6FF))),
        Cosmetic(id: "skin.glitch", name: "Glitch", detail: "Reality can't keep up. Earned with 400 perfect misses.",
                 category: .skin, rarity: .epic,
                 gates: [stat(\.totalPerfectMisses, 400, "Perfect misses")],
                 skin: SkinLook(primary: .white, glow: c(0xFF2E97), style: .solid, effects: [.glitch, .pulse],
                                accent: c(0x2EF2FF))),
        Cosmetic(id: "skin.gold", name: "Gold", detail: "For true legends. Survive 3 minutes in Endless and reach level 15.",
                 category: .skin, rarity: .legendary,
                 gates: [stat(\.bestEndlessTime, 180, "Survive in Endless", .seconds), .level(15)],
                 skin: SkinLook(primary: c(0xFFD84D), glow: c(0xFFF1A8), style: .ring, effects: [.orbiters, .sparkle, .pulse],
                                accent: c(0xFFF1A8))),
        Cosmetic(id: "skin.void", name: "Void", detail: "Light bends around it. Survive 90s on Expert and reach level 20.",
                 category: .skin, rarity: .legendary,
                 gates: [stat(\.bestExpertTime, 90, "Survive on Expert", .seconds), .level(20)],
                 skin: SkinLook(primary: c(0x0A0A12), glow: .white, style: .void, effects: [.aura, .spinRing],
                                accent: c(0x9B5CFF))),
        Cosmetic(id: "skin.prism", name: "Prism", detail: "Every colour at once.",
                 category: .skin, rarity: .legendary,
                 gates: [.level(40)],
                 skin: SkinLook(primary: .white, glow: .white, style: .prism, effects: [.hueCycle, .orbiters, .sparkle])),
        Cosmetic(id: "skin.phoenix", name: "Phoenix", detail: "Reborn in fire, every run.",
                 category: .skin, rarity: .legendary, price: 40_000, gates: [.level(30)],
                 skin: SkinLook(primary: c(0xFF3D3D), glow: c(0xFFC53D), style: .core, effects: [.aura, .pulse, .sparkle],
                                accent: c(0xFFC53D))),
        Cosmetic(id: "skin.singularity", name: "Singularity", detail: "The rarest thing in the sky. Clear every daily mission on 30 days and reach level 50.",
                 category: .skin, rarity: .mythic,
                 gates: [stat(\.allClearDays, 30, "All-clear days"), .level(50)],
                 skin: SkinLook(primary: .black, glow: .white, style: .void,
                                effects: [.spinRing, .orbiters, .aura, .glitch, .hueCycle], accent: .white))
    ]

    static let trails: [Cosmetic] = [
        Cosmetic(id: "trail.none", name: "None", detail: "Clean and minimal.",
                 category: .trail, rarity: .common,
                 trail: TrailLook(colors: [c(0x808080)], mode: .solid, width: 0, length: 0)),
        Cosmetic(id: "trail.streak", name: "Streak", detail: "A simple white blade.",
                 category: .trail, rarity: .common,
                 trail: TrailLook(colors: [.white, c(0xBFD9FF)], mode: .gradient, width: 0.55, length: 12)),
        Cosmetic(id: "trail.neon", name: "Neon Line", detail: "Cyan blade with a spray of sparks.",
                 category: .trail, rarity: .common, price: 1_000,
                 trail: TrailLook(colors: [c(0x9CF6FF), c(0x38E1FF), c(0x0066FF)], mode: .gradient,
                                  width: 0.7, length: 16, particles: .sparks)),
        Cosmetic(id: "trail.ember", name: "Ember", detail: "Glowing coals falling off the blade.",
                 category: .trail, rarity: .common, price: 2_000,
                 trail: TrailLook(colors: [c(0xFFE08A), c(0xFF7A1A), c(0xFF2E2E)], mode: .gradient,
                                  width: 0.8, length: 16, particles: .embers)),
        Cosmetic(id: "trail.candy", name: "Candy Cane", detail: "Sweet, striped and sharp.",
                 category: .trail, rarity: .rare, price: 3_500, gates: [.level(8)],
                 trail: TrailLook(colors: [c(0xFF4F8B), .white], mode: .stripes,
                                  width: 0.8, length: 18, glow: false)),
        Cosmetic(id: "trail.frostbite", name: "Frostbite", detail: "Leaves snow in its wake. Earned with 20,000 in Time Attack.",
                 category: .trail, rarity: .rare,
                 gates: [stat(\.bestTimeAttackScore, 20_000, "Best Time Attack score")],
                 trail: TrailLook(colors: [.white, c(0x7FD8FF), c(0x2E6BFF)], mode: .gradient,
                                  width: 0.8, length: 18, particles: .snow)),
        Cosmetic(id: "trail.inferno", name: "Inferno", detail: "A roaring blade of fire.",
                 category: .trail, rarity: .epic, price: 10_000, gates: [.level(15)],
                 trail: TrailLook(colors: [.white, c(0xFFD24D), c(0xFF6A00), c(0xB30000)], mode: .gradient,
                                  width: 1.0, length: 22, particles: .embers)),
        Cosmetic(id: "trail.electric", name: "Electric", detail: "Crackling, flickering voltage. Earned with 2,500 near misses in total.",
                 category: .trail, rarity: .epic,
                 gates: [stat(\.totalNearMisses, 2_500, "Near misses")],
                 trail: TrailLook(colors: [.white, c(0xB07CFF), c(0x4D4DFF)], mode: .flicker,
                                  width: 0.6, length: 18, jitter: 0.9, particles: .zaps)),
        Cosmetic(id: "trail.galaxy", name: "Galaxy", detail: "A slice of the night sky. Earned by completing 14 Daily Runs.",
                 category: .trail, rarity: .epic,
                 gates: [stat(\.dailyRunsCompleted, 14, "Daily Runs completed")],
                 trail: TrailLook(colors: [c(0xFF6BD6), c(0x7B2EFF), c(0x1A0B5E)], mode: .gradient,
                                  width: 1.0, length: 22, particles: .stars)),
        Cosmetic(id: "trail.rainbow", name: "Rainbow Blade", detail: "Every colour, always moving.",
                 category: .trail, rarity: .legendary,
                 gates: [.level(35)],
                 trail: TrailLook(colors: [], mode: .rainbow, width: 0.9, length: 24, particles: .sparks)),
        Cosmetic(id: "trail.katana", name: "Golden Katana", detail: "A long, razor-thin golden edge. Score 40,000 in Time Attack and reach level 20.",
                 category: .trail, rarity: .legendary,
                 gates: [stat(\.bestTimeAttackScore, 40_000, "Best Time Attack score"), .level(20)],
                 trail: TrailLook(colors: [.white, c(0xFFE27A), c(0xC99A00)], mode: .gradient,
                                  width: 0.45, length: 28, particles: .stars)),
        Cosmetic(id: "trail.phantom", name: "Phantom", detail: "Too fast to see. Score 20,000 in Endless and reach level 20.",
                 category: .trail, rarity: .legendary,
                 gates: [stat(\.bestEndlessScore, 20_000, "Best Endless score"), .level(20)],
                 trail: TrailLook(colors: [.white, c(0x9BA8FF)], mode: .gradient,
                                  width: 0.7, length: 16, afterimage: true)),
        Cosmetic(id: "trail.prismStorm", name: "Prism Storm", detail: "Rainbow lightning. Earned by clearing every daily mission 30 days in a row.",
                 category: .trail, rarity: .mythic,
                 gates: [stat(\.longestAllClearStreak, 30, "All-clear streak (days)")],
                 trail: TrailLook(colors: [], mode: .rainbow, width: 1.1, length: 26, jitter: 0.5, particles: .zaps))
    ]

    static let themes: [Cosmetic] = [
        Cosmetic(id: "theme.deepSpace", name: "Deep Space", detail: "The classic void.",
                 category: .theme, rarity: .common,
                 theme: ThemeLook(backgroundTop: c(0x0E0B2E), backgroundBottom: c(0x000000),
                                  star: .white, hazard: c(0xFF3D5A), hazardAlt: c(0xFF8A3D),
                                  accent: c(0x38E1FF), grid: false)),
        Cosmetic(id: "theme.synthwave", name: "Synthwave", detail: "Retro neon highway.",
                 category: .theme, rarity: .common, price: 3_000,
                 theme: ThemeLook(backgroundTop: c(0x2B0B3F), backgroundBottom: c(0x0D0221),
                                  star: c(0xFF9CEB), hazard: c(0xFF7B00), hazardAlt: c(0xFF2E97),
                                  accent: c(0x00F0FF), grid: true)),
        Cosmetic(id: "theme.abyss", name: "Abyss", detail: "The deep ocean.",
                 category: .theme, rarity: .common, price: 3_000,
                 theme: ThemeLook(backgroundTop: c(0x00345C), backgroundBottom: c(0x000814),
                                  star: c(0x9BE7FF), hazard: c(0xFF6F61), hazardAlt: c(0xFFB347),
                                  accent: c(0x4DFFDF), grid: false)),
        Cosmetic(id: "theme.matrix", name: "Mainframe", detail: "Follow the white dot.",
                 category: .theme, rarity: .rare, price: 6_000, gates: [.level(12)],
                 theme: ThemeLook(backgroundTop: c(0x002010), backgroundBottom: c(0x000000),
                                  star: c(0x39FF14), hazard: c(0xFF3131), hazardAlt: c(0xFFE14D),
                                  accent: c(0x39FF14), grid: true)),
        Cosmetic(id: "theme.sunset", name: "Sunset Drive", detail: "Warm skies, hot hazards. Earned with 5,000 in a Daily Run.",
                 category: .theme, rarity: .rare,
                 gates: [stat(\.bestDailyScore, 5_000, "Best Daily Run score")],
                 theme: ThemeLook(backgroundTop: c(0x5A1F4F), backgroundBottom: c(0x160A2A),
                                  star: c(0xFFD1A6), hazard: c(0xFF2E63), hazardAlt: c(0xFF9F1C),
                                  accent: c(0xFFC857), grid: false)),
        Cosmetic(id: "theme.noir", name: "Noir", detail: "Black, white and red all over. Survive 60s on Expert and reach level 10.",
                 category: .theme, rarity: .epic,
                 gates: [stat(\.bestExpertTime, 60, "Survive on Expert", .seconds), .level(10)],
                 theme: ThemeLook(backgroundTop: c(0x1C1C1C), backgroundBottom: c(0x000000),
                                  star: c(0xD9D9D9), hazard: c(0xFF1E1E), hazardAlt: c(0xB30000),
                                  accent: c(0xFF1E1E), grid: false)),
        Cosmetic(id: "theme.aurora", name: "Aurora", detail: "Northern lights over a frozen sky.",
                 category: .theme, rarity: .epic,
                 gates: [.level(30)],
                 theme: ThemeLook(backgroundTop: c(0x03324A), backgroundBottom: c(0x05000F),
                                  star: c(0xA6FFCB), hazard: c(0xFF4FA3), hazardAlt: c(0xB37BFF),
                                  accent: c(0x6BFFB8), grid: false)),
        Cosmetic(id: "theme.solarFlare", name: "Solar Flare", detail: "Inside the sun – with ice-cold hazards.",
                 category: .theme, rarity: .legendary, price: 30_000,
                 gates: [stat(\.allClearDays, 14, "All-clear days")],
                 theme: ThemeLook(backgroundTop: c(0x4A0E00), backgroundBottom: c(0x0A0000),
                                  star: c(0xFFB36B), hazard: c(0x00E5FF), hazardAlt: c(0x7DF9FF),
                                  accent: c(0xFF6A00), grid: true))
    ]
}
