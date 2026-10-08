//
//  Cosmetics.kt
//  Starshower Run
//
//  Skins, blade trails and themes. Items have a rarity and are unlocked by
//  some mix of coins and gates (levels, personal bests, mission streaks…).
//  The rarest items can't be bought at all – they have to be earned.
//

package com.starshower.run.core.progression

import com.starshower.run.core.engine.RGBColor
import com.starshower.run.core.engine.clamp
import kotlin.math.sin

// MARK: - Categories & rarity

enum class CosmeticCategory {
    SKIN, TRAIL, THEME;

    val title: String
        get() = when (this) {
            SKIN -> "Skins"
            TRAIL -> "Trails"
            THEME -> "Themes"
        }

    val singularTitle: String
        get() = when (this) {
            SKIN -> "Skin"
            TRAIL -> "Trail"
            THEME -> "Theme"
        }

    val icon: String
        get() = when (this) {
            SKIN -> "circle.hexagongrid.fill"
            TRAIL -> "scribble.variable"
            THEME -> "paintpalette.fill"
        }
}

enum class CosmeticRarity {
    COMMON, RARE, EPIC, LEGENDARY, MYTHIC;

    val title: String
        get() = when (this) {
            COMMON -> "Common"
            RARE -> "Rare"
            EPIC -> "Epic"
            LEGENDARY -> "Legendary"
            MYTHIC -> "Mythic"
        }

    val color: RGBColor
        get() = when (this) {
            COMMON -> RGBColor.hex(0xB8C4D6)
            RARE -> RGBColor.hex(0x3DA5FF)
            EPIC -> RGBColor.hex(0xB45CFF)
            LEGENDARY -> RGBColor.hex(0xFFB020)
            MYTHIC -> RGBColor.hex(0xFF3D8B)
        }
}

// MARK: - Unlock requirements

enum class StatFormat { NUMBER, SECONDS }

data class StatGoal(
    val metric: (PlayerStats) -> Int,
    val goal: Int,
    /** e.g. "Best Endless score" */
    val label: String,
    val format: StatFormat = StatFormat.NUMBER,
) {
    fun formatted(value: Int): String = when (format) {
        StatFormat.NUMBER -> value.formatted()
        StatFormat.SECONDS -> value.clockString
    }
}

/** A condition that must be met before an item can be bought or is granted. */
sealed interface UnlockGate {
    data class Level(val level: Int) : UnlockGate
    data class Achievement(val id: String) : UnlockGate
    data class Stat(val goal: StatGoal) : UnlockGate

    fun isMet(profile: PlayerProfile): Boolean {
        val (current, goal) = progress(profile)
        return current >= goal
    }

    /** (current, goal) */
    fun progress(profile: PlayerProfile): Pair<Int, Int> = when (this) {
        is Level -> minOf(profile.level, level) to level
        is Achievement -> (if (profile.unlockedAchievements.containsKey(id)) 1 else 0) to 1
        is Stat -> minOf(goal.metric(profile.stats), goal.goal) to goal.goal
    }

    val summary: String
        get() = when (this) {
            is Level -> "Reach level $level"
            is Achievement -> "Unlock “${AchievementCatalog.find(id)?.title ?: id}”"
            is Stat -> "${goal.label}: ${goal.formatted(goal.goal)}"
        }

    fun progressText(profile: PlayerProfile): String {
        val (current, goalValue) = progress(profile)
        return when (this) {
            is Level -> "Level ${profile.level} / $goalValue"
            is Achievement -> if (current > 0) "Unlocked" else "Locked"
            is Stat -> "${goal.formatted(goal.metric(profile.stats))} / ${goal.formatted(goal.goal)}"
        }
    }
}

// MARK: - Looks

enum class SkinStyle { SOLID, RING, CORE, PRISM, VOID }

/** Animated extras layered on top of a skin by the renderer. */
enum class SkinEffect {
    /** Breathing glow. */
    PULSE,
    /** Little moons circling the dot. */
    ORBITERS,
    /** Particles streaming off the dot. */
    AURA,
    /** A rotating dashed halo. */
    SPIN_RING,
    /** RGB-split jitter. */
    GLITCH,
    /** Twinkling sparkles around the dot. */
    SPARKLE,
    /** Cycles through every hue. */
    HUE_CYCLE,
}

data class SkinLook(
    val primary: RGBColor,
    val glow: RGBColor,
    val style: SkinStyle,
    val effects: List<SkinEffect> = emptyList(),
    val accent: RGBColor? = null,
) {
    val accentColor: RGBColor get() = accent ?: glow
    fun has(effect: SkinEffect): Boolean = effect in effects
}

enum class TrailColorMode {
    /** One colour. */
    SOLID,
    /** Blends through `colors` from head to tail. */
    GRADIENT,
    /** Cycles through the rainbow over time and along the blade. */
    RAINBOW,
    /** Alternates between the first two colours. */
    STRIPES,
    /** Gradient with electric flicker. */
    FLICKER,
}

enum class TrailParticles { NONE, SPARKS, EMBERS, SNOW, STARS, BUBBLES, ZAPS }

/** A Fruit Ninja–style blade trail: a tapered ribbon plus optional particles. */
data class TrailLook(
    val colors: List<RGBColor>,
    val mode: TrailColorMode,
    /** Ribbon width relative to the player's diameter (0 = no ribbon). */
    val width: Double,
    /** Number of ribbon points (longer = longer blade). */
    val length: Int,
    /** Sideways electric wobble, 0..1. */
    val jitter: Double = 0.0,
    val particles: TrailParticles = TrailParticles.NONE,
    /** Additive glow blending. */
    val glow: Boolean = true,
    /** Leaves fading copies of the player behind. */
    val afterimage: Boolean = false,
) {
    val hasRibbon: Boolean get() = width > 0 && length > 1

    /** Colour of the ribbon at `t` (0 = head, 1 = tail) for segment [index] at [time]. */
    fun color(t: Double, index: Int, time: Double): RGBColor {
        val tt = clamp(t, 0.0, 1.0)
        return when (mode) {
            TrailColorMode.SOLID -> colors.firstOrNull() ?: RGBColor.WHITE
            TrailColorMode.GRADIENT -> sample(colors, tt)
            TrailColorMode.RAINBOW -> RGBColor.hsb(time * 0.45 + tt * 0.9, 0.85, 1.0)
            TrailColorMode.STRIPES -> {
                val palette = colors.ifEmpty { listOf(RGBColor.WHITE) }
                palette[(index / 2) % palette.size]
            }
            TrailColorMode.FLICKER -> {
                val base = sample(colors, tt)
                val flash = (sin(time * 47 + index * 1.9) + 1) / 2
                base.lighter(flash * 0.6)
            }
        }
    }

    /** Main colour for previews and particles. */
    val primary: RGBColor
        get() = if (mode == TrailColorMode.RAINBOW) RGBColor.hex(0xFF4FD8) else colors.firstOrNull() ?: RGBColor.WHITE

    private fun sample(colors: List<RGBColor>, t: Double): RGBColor {
        if (colors.size <= 1) return colors.firstOrNull() ?: RGBColor.WHITE
        val scaled = t * (colors.size - 1)
        val index = minOf(scaled.toInt(), colors.size - 2)
        return colors[index].mixed(colors[index + 1], scaled - index)
    }
}

data class ThemeLook(
    val backgroundTop: RGBColor,
    val backgroundBottom: RGBColor,
    val star: RGBColor,
    val hazard: RGBColor,
    val hazardAlt: RGBColor,
    val accent: RGBColor,
    val grid: Boolean,
)

// MARK: - Cosmetic

data class Cosmetic(
    val id: String,
    val name: String,
    val detail: String,
    val category: CosmeticCategory,
    val rarity: CosmeticRarity,
    /** Coin price, or null if it can't be bought. */
    val price: Int? = null,
    /** Conditions that must be met first (to buy, or to be granted if there's no price). */
    val gates: List<UnlockGate> = emptyList(),
    val skin: SkinLook? = null,
    val trail: TrailLook? = null,
    val theme: ThemeLook? = null,
) {
    val isFree: Boolean get() = price == null && gates.isEmpty()

    /** Earned-only items are granted automatically once every gate is met. */
    val isEarnedOnly: Boolean get() = price == null && gates.isNotEmpty()

    /** Main swatch colour for previews. */
    val swatch: RGBColor get() = skin?.primary ?: trail?.primary ?: theme?.hazard ?: RGBColor.WHITE

    companion object {
        const val DEFAULT_SKIN = "skin.classic"
        const val DEFAULT_TRAIL = "trail.streak"
        const val DEFAULT_THEME = "theme.deepSpace"

        val defaultUnlocked: Set<String> get() = catalog.filter { it.isFree }.map { it.id }.toSet()

        fun find(id: String): Cosmetic? = catalog.firstOrNull { it.id == id }

        fun items(category: CosmeticCategory): List<Cosmetic> = catalog.filter { it.category == category }

        fun skinLook(id: String): SkinLook = find(id)?.skin ?: find(DEFAULT_SKIN)!!.skin!!
        fun trailLook(id: String): TrailLook = find(id)?.trail ?: find(DEFAULT_TRAIL)!!.trail!!
        fun themeLook(id: String): ThemeLook = find(id)?.theme ?: find(DEFAULT_THEME)!!.theme!!

        // Gate shorthands
        private fun stat(metric: (PlayerStats) -> Int, goal: Int, label: String, format: StatFormat = StatFormat.NUMBER) =
            UnlockGate.Stat(StatGoal(metric, goal, label, format))

        private fun level(level: Int) = UnlockGate.Level(level)
        private fun c(hex: Long) = RGBColor.hex(hex)
        private val white = RGBColor.WHITE
        private val black = RGBColor.BLACK
        private val SECONDS = StatFormat.SECONDS

        private val SKIN = CosmeticCategory.SKIN
        private val TRAIL = CosmeticCategory.TRAIL
        private val THEME = CosmeticCategory.THEME
        private val COMMON = CosmeticRarity.COMMON
        private val RARE = CosmeticRarity.RARE
        private val EPIC = CosmeticRarity.EPIC
        private val LEGENDARY = CosmeticRarity.LEGENDARY
        private val MYTHIC = CosmeticRarity.MYTHIC

        // MARK: Catalog

        val skins: List<Cosmetic> = listOf(
            Cosmetic("skin.classic", "Classic", "Where it all began.", SKIN, COMMON,
                skin = SkinLook(white, c(0xBFD9FF), SkinStyle.SOLID)),
            Cosmetic("skin.neon", "Neon", "Electric cyan with a breathing halo.", SKIN, COMMON, price = 1_500,
                skin = SkinLook(c(0x38E1FF), c(0x38E1FF), SkinStyle.RING, listOf(SkinEffect.PULSE))),
            Cosmetic("skin.ember", "Ember", "A hot core that sheds sparks.", SKIN, COMMON, price = 2_000,
                skin = SkinLook(c(0xFF7A1A), c(0xFFB36B), SkinStyle.CORE, listOf(SkinEffect.AURA), accent = c(0xFF9A3D))),
            Cosmetic("skin.toxic", "Toxic", "Radioactive and proud of it.", SKIN, COMMON, price = 2_500, gates = listOf(level(5)),
                skin = SkinLook(c(0x9DFF3D), c(0x9DFF3D), SkinStyle.SOLID, listOf(SkinEffect.PULSE))),
            Cosmetic("skin.plasma", "Plasma", "Contained lightning in a spinning cage.", SKIN, RARE, price = 5_000, gates = listOf(level(10)),
                skin = SkinLook(c(0xD14DFF), c(0xF0A6FF), SkinStyle.RING, listOf(SkinEffect.SPIN_RING), accent = c(0xF0A6FF))),
            Cosmetic("skin.frost", "Frost", "Cool under pressure. Earned by surviving 90s in Endless.", SKIN, RARE,
                gates = listOf(stat(PlayerStats::bestEndlessTime, 90, "Survive in Endless", SECONDS)),
                skin = SkinLook(c(0xBFEFFF), c(0x7FD8FF), SkinStyle.CORE, listOf(SkinEffect.SPARKLE), accent = c(0xE6F9FF))),
            Cosmetic("skin.sakura", "Sakura", "Petals in orbit. Earned by clearing every daily mission 7 days in a row.", SKIN, RARE,
                gates = listOf(stat(PlayerStats::longestAllClearStreak, 7, "All-clear streak (days)")),
                skin = SkinLook(c(0xFF8FC8), c(0xFFD1E8), SkinStyle.CORE, listOf(SkinEffect.ORBITERS, SkinEffect.SPARKLE), accent = c(0xFFD1E8))),
            Cosmetic("skin.supernova", "Supernova", "A star on the edge of exploding.", SKIN, EPIC, price = 12_000,
                gates = listOf(stat(PlayerStats::bestEndlessScore, 8_000, "Best Endless score")),
                skin = SkinLook(c(0xFFD24D), c(0xFF9A3D), SkinStyle.CORE, listOf(SkinEffect.AURA, SkinEffect.PULSE), accent = c(0xFF6A00))),
            Cosmetic("skin.orbital", "Orbital", "Three moons and a spinning halo.", SKIN, EPIC,
                gates = listOf(level(25)),
                skin = SkinLook(c(0x4DFFD2), c(0x4DFFD2), SkinStyle.RING, listOf(SkinEffect.ORBITERS, SkinEffect.SPIN_RING), accent = c(0x4DA6FF))),
            Cosmetic("skin.glitch", "Glitch", "Reality can't keep up. Earned with 400 perfect misses.", SKIN, EPIC,
                gates = listOf(stat(PlayerStats::totalPerfectMisses, 400, "Perfect misses")),
                skin = SkinLook(white, c(0xFF2E97), SkinStyle.SOLID, listOf(SkinEffect.GLITCH, SkinEffect.PULSE), accent = c(0x2EF2FF))),
            Cosmetic("skin.gold", "Gold", "For true legends. Survive 3 minutes in Endless and reach level 15.", SKIN, LEGENDARY,
                gates = listOf(stat(PlayerStats::bestEndlessTime, 180, "Survive in Endless", SECONDS), level(15)),
                skin = SkinLook(c(0xFFD84D), c(0xFFF1A8), SkinStyle.RING, listOf(SkinEffect.ORBITERS, SkinEffect.SPARKLE, SkinEffect.PULSE), accent = c(0xFFF1A8))),
            Cosmetic("skin.void", "Void", "Light bends around it. Survive 90s on Expert and reach level 20.", SKIN, LEGENDARY,
                gates = listOf(stat(PlayerStats::bestExpertTime, 90, "Survive on Expert", SECONDS), level(20)),
                skin = SkinLook(c(0x0A0A12), white, SkinStyle.VOID, listOf(SkinEffect.AURA, SkinEffect.SPIN_RING), accent = c(0x9B5CFF))),
            Cosmetic("skin.prism", "Prism", "Every colour at once.", SKIN, LEGENDARY,
                gates = listOf(level(40)),
                skin = SkinLook(white, white, SkinStyle.PRISM, listOf(SkinEffect.HUE_CYCLE, SkinEffect.ORBITERS, SkinEffect.SPARKLE))),
            Cosmetic("skin.phoenix", "Phoenix", "Reborn in fire, every run.", SKIN, LEGENDARY, price = 40_000, gates = listOf(level(30)),
                skin = SkinLook(c(0xFF3D3D), c(0xFFC53D), SkinStyle.CORE, listOf(SkinEffect.AURA, SkinEffect.PULSE, SkinEffect.SPARKLE), accent = c(0xFFC53D))),
            Cosmetic("skin.singularity", "Singularity", "The rarest thing in the sky. Clear every daily mission on 30 days and reach level 50.", SKIN, MYTHIC,
                gates = listOf(stat(PlayerStats::allClearDays, 30, "All-clear days"), level(50)),
                skin = SkinLook(black, white, SkinStyle.VOID,
                    listOf(SkinEffect.SPIN_RING, SkinEffect.ORBITERS, SkinEffect.AURA, SkinEffect.GLITCH, SkinEffect.HUE_CYCLE), accent = white)),
        )

        val trails: List<Cosmetic> = listOf(
            Cosmetic("trail.none", "None", "Clean and minimal.", TRAIL, COMMON,
                trail = TrailLook(listOf(c(0x808080)), TrailColorMode.SOLID, width = 0.0, length = 0)),
            Cosmetic("trail.streak", "Streak", "A simple white blade.", TRAIL, COMMON,
                trail = TrailLook(listOf(white, c(0xBFD9FF)), TrailColorMode.GRADIENT, width = 0.55, length = 12)),
            Cosmetic("trail.neon", "Neon Line", "Cyan blade with a spray of sparks.", TRAIL, COMMON, price = 1_000,
                trail = TrailLook(listOf(c(0x9CF6FF), c(0x38E1FF), c(0x0066FF)), TrailColorMode.GRADIENT,
                    width = 0.7, length = 16, particles = TrailParticles.SPARKS)),
            Cosmetic("trail.ember", "Ember", "Glowing coals falling off the blade.", TRAIL, COMMON, price = 2_000,
                trail = TrailLook(listOf(c(0xFFE08A), c(0xFF7A1A), c(0xFF2E2E)), TrailColorMode.GRADIENT,
                    width = 0.8, length = 16, particles = TrailParticles.EMBERS)),
            Cosmetic("trail.candy", "Candy Cane", "Sweet, striped and sharp.", TRAIL, RARE, price = 3_500, gates = listOf(level(8)),
                trail = TrailLook(listOf(c(0xFF4F8B), white), TrailColorMode.STRIPES, width = 0.8, length = 18, glow = false)),
            Cosmetic("trail.frostbite", "Frostbite", "Leaves snow in its wake. Earned with 20,000 in Time Attack.", TRAIL, RARE,
                gates = listOf(stat(PlayerStats::bestTimeAttackScore, 20_000, "Best Time Attack score")),
                trail = TrailLook(listOf(white, c(0x7FD8FF), c(0x2E6BFF)), TrailColorMode.GRADIENT,
                    width = 0.8, length = 18, particles = TrailParticles.SNOW)),
            Cosmetic("trail.inferno", "Inferno", "A roaring blade of fire.", TRAIL, EPIC, price = 10_000, gates = listOf(level(15)),
                trail = TrailLook(listOf(white, c(0xFFD24D), c(0xFF6A00), c(0xB30000)), TrailColorMode.GRADIENT,
                    width = 1.0, length = 22, particles = TrailParticles.EMBERS)),
            Cosmetic("trail.electric", "Electric", "Crackling, flickering voltage. Earned with 2,500 near misses in total.", TRAIL, EPIC,
                gates = listOf(stat(PlayerStats::totalNearMisses, 2_500, "Near misses")),
                trail = TrailLook(listOf(white, c(0xB07CFF), c(0x4D4DFF)), TrailColorMode.FLICKER,
                    width = 0.6, length = 18, jitter = 0.9, particles = TrailParticles.ZAPS)),
            Cosmetic("trail.galaxy", "Galaxy", "A slice of the night sky. Earned by completing 14 Daily Runs.", TRAIL, EPIC,
                gates = listOf(stat(PlayerStats::dailyRunsCompleted, 14, "Daily Runs completed")),
                trail = TrailLook(listOf(c(0xFF6BD6), c(0x7B2EFF), c(0x1A0B5E)), TrailColorMode.GRADIENT,
                    width = 1.0, length = 22, particles = TrailParticles.STARS)),
            Cosmetic("trail.rainbow", "Rainbow Blade", "Every colour, always moving.", TRAIL, LEGENDARY,
                gates = listOf(level(35)),
                trail = TrailLook(emptyList(), TrailColorMode.RAINBOW, width = 0.9, length = 24, particles = TrailParticles.SPARKS)),
            Cosmetic("trail.katana", "Golden Katana", "A long, razor-thin golden edge. Score 40,000 in Time Attack and reach level 20.", TRAIL, LEGENDARY,
                gates = listOf(stat(PlayerStats::bestTimeAttackScore, 40_000, "Best Time Attack score"), level(20)),
                trail = TrailLook(listOf(white, c(0xFFE27A), c(0xC99A00)), TrailColorMode.GRADIENT,
                    width = 0.45, length = 28, particles = TrailParticles.STARS)),
            Cosmetic("trail.phantom", "Phantom", "Too fast to see. Score 20,000 in Endless and reach level 20.", TRAIL, LEGENDARY,
                gates = listOf(stat(PlayerStats::bestEndlessScore, 20_000, "Best Endless score"), level(20)),
                trail = TrailLook(listOf(white, c(0x9BA8FF)), TrailColorMode.GRADIENT, width = 0.7, length = 16, afterimage = true)),
            Cosmetic("trail.prismStorm", "Prism Storm", "Rainbow lightning. Earned by clearing every daily mission 30 days in a row.", TRAIL, MYTHIC,
                gates = listOf(stat(PlayerStats::longestAllClearStreak, 30, "All-clear streak (days)")),
                trail = TrailLook(emptyList(), TrailColorMode.RAINBOW, width = 1.1, length = 26, jitter = 0.5, particles = TrailParticles.ZAPS)),
        )

        val themes: List<Cosmetic> = listOf(
            Cosmetic("theme.deepSpace", "Deep Space", "The classic void.", THEME, COMMON,
                theme = ThemeLook(c(0x0E0B2E), c(0x000000), white, c(0xFF3D5A), c(0xFF8A3D), c(0x38E1FF), grid = false)),
            Cosmetic("theme.synthwave", "Synthwave", "Retro neon highway.", THEME, COMMON, price = 3_000,
                theme = ThemeLook(c(0x2B0B3F), c(0x0D0221), c(0xFF9CEB), c(0xFF7B00), c(0xFF2E97), c(0x00F0FF), grid = true)),
            Cosmetic("theme.abyss", "Abyss", "The deep ocean.", THEME, COMMON, price = 3_000,
                theme = ThemeLook(c(0x00345C), c(0x000814), c(0x9BE7FF), c(0xFF6F61), c(0xFFB347), c(0x4DFFDF), grid = false)),
            Cosmetic("theme.matrix", "Mainframe", "Follow the white dot.", THEME, RARE, price = 6_000, gates = listOf(level(12)),
                theme = ThemeLook(c(0x002010), c(0x000000), c(0x39FF14), c(0xFF3131), c(0xFFE14D), c(0x39FF14), grid = true)),
            Cosmetic("theme.sunset", "Sunset Drive", "Warm skies, hot hazards. Earned with 5,000 in a Daily Run.", THEME, RARE,
                gates = listOf(stat(PlayerStats::bestDailyScore, 5_000, "Best Daily Run score")),
                theme = ThemeLook(c(0x5A1F4F), c(0x160A2A), c(0xFFD1A6), c(0xFF2E63), c(0xFF9F1C), c(0xFFC857), grid = false)),
            Cosmetic("theme.noir", "Noir", "Black, white and red all over. Survive 60s on Expert and reach level 10.", THEME, EPIC,
                gates = listOf(stat(PlayerStats::bestExpertTime, 60, "Survive on Expert", SECONDS), level(10)),
                theme = ThemeLook(c(0x1C1C1C), c(0x000000), c(0xD9D9D9), c(0xFF1E1E), c(0xB30000), c(0xFF1E1E), grid = false)),
            Cosmetic("theme.aurora", "Aurora", "Northern lights over a frozen sky.", THEME, EPIC,
                gates = listOf(level(30)),
                theme = ThemeLook(c(0x03324A), c(0x05000F), c(0xA6FFCB), c(0xFF4FA3), c(0xB37BFF), c(0x6BFFB8), grid = false)),
            Cosmetic("theme.solarFlare", "Solar Flare", "Inside the sun – with ice-cold hazards.", THEME, LEGENDARY, price = 30_000,
                gates = listOf(stat(PlayerStats::allClearDays, 14, "All-clear days")),
                theme = ThemeLook(c(0x4A0E00), c(0x0A0000), c(0xFFB36B), c(0x00E5FF), c(0x7DF9FF), c(0xFF6A00), grid = true)),
        )

        val catalog: List<Cosmetic> = skins + trails + themes
    }
}
