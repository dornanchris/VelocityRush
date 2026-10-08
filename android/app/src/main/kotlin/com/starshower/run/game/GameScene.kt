//
//  GameScene.kt
//  Starshower Run
//
//  Renders a GameEngine: draws the simulation every frame and turns engine
//  events into particles, sounds, haptics and screen shake. A line-by-line
//  port of the iOS SpriteKit scene onto a Compose Canvas.
//

package com.starshower.run.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.engine.GameEngine
import com.starshower.run.core.engine.GameEvent
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.Hazard
import com.starshower.run.core.engine.HazardKind
import com.starshower.run.core.engine.PickupKind
import com.starshower.run.core.engine.Playfield
import com.starshower.run.core.engine.RGBColor
import com.starshower.run.core.engine.RunConfig
import com.starshower.run.core.engine.RunModifier
import com.starshower.run.core.engine.TimedPowerUp
import com.starshower.run.core.engine.Vec2
import com.starshower.run.core.progression.Cosmetic
import com.starshower.run.core.progression.GameSettings
import com.starshower.run.core.progression.PlayerProfile
import com.starshower.run.core.progression.SkinLook
import com.starshower.run.core.progression.ThemeLook
import com.starshower.run.core.progression.TrailLook
import com.starshower.run.services.Haptic
import com.starshower.run.services.HapticsManager
import com.starshower.run.services.SoundManager
import com.starshower.run.ui.color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Things the scene needs from Compose to draw (text and vector icons). */
class SceneAssets(
    val textMeasurer: TextMeasurer,
    val pickupIcons: Map<PickupKind, Painter>,
    val warningIcon: Painter,
)

class GameScene(
    widthPx: Float,
    heightPx: Float,
    /** Pixels per point/dp, used for screen-space sizes (banners, stars). */
    private val density: Float,
    config: RunConfig,
    private val session: GameSession?,
    private val settings: GameSettings,
    profile: PlayerProfile,
    private val sound: SoundManager,
    private val haptics: HapticsManager,
) {
    // MARK: Look

    private var skin: SkinLook = Cosmetic.skinLook(profile.equippedSkin)
    private var trailLook: TrailLook = Cosmetic.trailLook(profile.equippedTrail)
    private var theme: ThemeLook = Cosmetic.themeLook(profile.equippedTheme)

    private val hazardColor: RGBColor
        get() = when {
            settings.colorBlindMode -> RGBColor.hex(0xFF8C00)
            settings.highContrast -> RGBColor.hex(0xFF2020)
            else -> theme.hazard
        }

    private val hazardAltColor: RGBColor
        get() = when {
            settings.colorBlindMode -> RGBColor.hex(0xFFE14D)
            settings.highContrast -> RGBColor.hex(0xFF7A00)
            else -> theme.hazardAlt
        }

    private val outlineHazards: Boolean get() = settings.colorBlindMode || settings.highContrast

    // MARK: State

    var width = widthPx
        private set
    var height = heightPx
        private set
    var engine: GameEngine = makeEngine(config)
        private set

    private var frame = WorldFrame(1f, 0f, 0f, engine.playfield.height)
    private var visualTime = 0.0
    private var frameDelta = 1.0 / 60.0
    private var isRunPaused = false
    private var didReportFinish = false
    private var pendingFinishAt: Double? = null
    private var touchActive = false
    private var lastHUD = HUDState()

    private val world = ParticleSystem()
    private var avatar = AvatarRenderer(skin, reducedMotion = settings.reducedMotion)
    private var trail = makeTrail()
    private var shieldVisible = false
    private var slowMoAlpha = 0f
    private var trailFadeStart: Double? = null

    private class HazardVisual(val born: Double, val kind: HazardKind, val streak: Emitter?)
    private val hazardVisuals = HashMap<Int, HazardVisual>()
    private val pickupBorn = HashMap<Int, Double>()
    private val warningBorn = HashMap<Int, Double>()

    private class FloatingText(val text: String, val x: Double, val y: Double, val color: Color, val size: Double, val born: Double) {
        var layout: TextLayoutResult? = null
    }
    private val floatingTexts = ArrayList<FloatingText>()

    private class Banner(val text: String, val size: Float, val color: Color, val duration: Double, val born: Double) {
        var layout: TextLayoutResult? = null
    }
    private var banner: Banner? = null

    private class Shockwave(val x: Double, val y: Double, val color: Color, val radius: Double, val born: Double)
    private val shockwaves = ArrayList<Shockwave>()

    private var flashColor = Color.White
    private var flashStrength = 0f
    private var flashBorn = 0.0

    private var shakeOffsets: List<Offset> = emptyList()
    private var shakeBorn = 0.0

    private class StarDot(var x: Float, var y: Float, val radius: Float, val alpha: Float, val speed: Float)
    private val stars = ArrayList<StarDot>()
    private val gridLines = ArrayList<Float>()

    // FPS overlay
    var fps = 0.0
        private set

    init {
        buildStarfield()
        layout()
        avatar.position = engine.playerPosition
        trail.reset(engine.playerPosition)
    }

    private fun makeEngine(config: RunConfig) =
        GameEngine(config, Playfield.fitting(width.toDouble(), height.toDouble()))

    private fun makeTrail() = TrailRenderer(trailLook, reducedMotion = settings.reducedMotion).also {
        it.ghostImage = avatar.body
        it.ghostSide = avatar.bodySide
    }

    // MARK: Layout

    private fun buildStarfield() {
        stars.clear()
        gridLines.clear()
        data class Layer(val count: Int, val speed: Float, val size: Float, val alpha: Float)
        val layers = listOf(Layer(40, 14f, 1.5f, 0.35f), Layer(24, 32f, 2.2f, 0.55f), Layer(12, 60f, 3.0f, 0.8f))
        for (layer in layers) {
            repeat(layer.count) {
                stars += StarDot(
                    x = Random.nextFloat() * max(width, 1f),
                    y = Random.nextFloat() * max(height, 1f),
                    radius = layer.size * density,
                    alpha = layer.alpha * (0.6f + Random.nextFloat() * 0.4f),
                    speed = layer.speed * (0.8f + Random.nextFloat() * 0.4f) * density,
                )
            }
        }
        if (theme.grid && !settings.reducedMotion) {
            for (index in 0 until 14) gridLines += index * max(height, 1f) / 14
        }
    }

    private fun layout() {
        val playfield = engine.playfield
        val scale = min(width / playfield.width.toFloat(), height / playfield.height.toFloat())
        frame = WorldFrame(
            scale = scale,
            offsetX = (width - playfield.width.toFloat() * scale) / 2,
            offsetY = (height - playfield.height.toFloat() * scale) / 2,
            height = playfield.height,
        )
    }

    fun resize(widthPx: Float, heightPx: Float) {
        if (widthPx == width && heightPx == height) return
        width = maxOf(widthPx, 1f)
        height = maxOf(heightPx, 1f)
        layout()
        buildStarfield()
    }

    // MARK: Run lifecycle

    /** Starts a fresh run (Play Again) reusing the scene. */
    fun startRun(config: RunConfig, profile: PlayerProfile) {
        skin = Cosmetic.skinLook(profile.equippedSkin)
        trailLook = Cosmetic.trailLook(profile.equippedTrail)
        engine = makeEngine(config)
        hazardVisuals.clear()
        pickupBorn.clear()
        warningBorn.clear()
        world.clear()
        floatingTexts.clear()
        shockwaves.clear()
        banner = null
        pendingFinishAt = null
        flashStrength = 0f
        shakeOffsets = emptyList()

        avatar = AvatarRenderer(skin, reducedMotion = settings.reducedMotion)
        avatar.position = engine.playerPosition
        trail = makeTrail()
        trail.reset(engine.playerPosition)
        trailFadeStart = null

        lastHUD = HUDState()
        didReportFinish = false
        touchActive = false
        setRunPaused(false)
        layout()
    }

    fun setRunPaused(paused: Boolean) {
        isRunPaused = paused
        touchActive = false
    }

    fun endRunEarly() = engine.endRun()

    // MARK: Touch input (relative drag, works anywhere on screen)

    fun touchBegan() {
        touchActive = true
    }

    fun touchMoved(dxPx: Float, dyPx: Float) {
        if (!touchActive || isRunPaused) return
        val factor = settings.sensitivity / max(frame.scale, 0.01f)
        val target = engine.playerTarget
        // Screen y grows downward; the world is y-up.
        engine.setTarget(Vec2(target.x + dxPx * factor, target.y - dyPx * factor))
    }

    fun touchEnded() {
        touchActive = false
    }

    // MARK: Frame update

    fun update(rawDelta: Double) {
        val delta = min(rawDelta, GameEngine.MAX_DELTA_TIME)
        if (isRunPaused || delta <= 0) return
        fps = if (fps == 0.0) 1 / rawDelta else fps * 0.92 + (1 / max(rawDelta, 0.001)) * 0.08

        visualTime += delta
        frameDelta = delta
        engine.step(delta)
        handle(engine.drainEvents())
        syncHazards(delta)
        syncPickups()
        syncWarnings()
        updatePlayer()
        updateBackground(delta)
        updateEffects(delta)
        pushHUD()

        // Let the final explosion play out before showing results.
        if (engine.isFinished && !didReportFinish) {
            didReportFinish = true
            pendingFinishAt = visualTime + if (engine.config.mode.hasSingleLife) 1.1 else 0.6
        }
        val finishAt = pendingFinishAt
        if (finishAt != null && visualTime >= finishAt) {
            pendingFinishAt = null
            session?.runFinished(engine.makeResult())
        }
    }

    // MARK: Syncing visuals

    private fun syncHazards(dt: Double) {
        val alive = HashSet<Int>(engine.hazards.size * 2)
        for (hazard in engine.hazards) {
            alive += hazard.id
            val visual = hazardVisuals.getOrPut(hazard.id) { makeHazardVisual(hazard) }
            visual.streak?.advance(dt, hazard.position.x, hazard.position.y, world)
        }
        hazardVisuals.keys.retainAll(alive)
    }

    private fun makeHazardVisual(hazard: Hazard): HazardVisual {
        val streak = if (hazard.kind == HazardKind.SPEEDER) Emitter(EmitterConfig(
            birthRate = if (settings.reducedMotion) 40.0 else 90.0,
            lifetime = 0.35,
            scale = hazard.radius / 10,
            scaleSpeed = -2.0,
            alphaSpeed = -2.5,
            color = hazardAltColor.color,
        )) else null
        return HazardVisual(visualTime, hazard.kind, streak)
    }

    // Sprites are resolved once per scene (drawing them happens every frame).
    private val hazardImages: Map<HazardKind, ImageBitmap> = HazardKind.entries.associateWith { kind ->
        when (kind) {
            HazardKind.DROP -> Sprites.hazard(hazardColor, OrbDecoration.PLAIN, outlineHazards)
            HazardKind.WOBBLER -> Sprites.hazard(hazardColor.mixed(hazardAltColor, 0.5), OrbDecoration.DASHED, outlineHazards)
            HazardKind.SPEEDER -> Sprites.hazard(hazardAltColor.lighter(0.2), OrbDecoration.CORE, outlineHazards)
            HazardKind.GIANT -> Sprites.hazard(hazardColor.darker(0.15), OrbDecoration.RING, outlineHazards)
            HazardKind.SPLITTER -> Sprites.hazard(hazardAltColor, OrbDecoration.HOLLOW, outlineHazards)
            HazardKind.FRAGMENT -> Sprites.hazard(hazardAltColor, OrbDecoration.PLAIN, outlineHazards)
        }
    }
    private val pickupImages: Map<PickupKind, ImageBitmap> = PickupKind.entries.associateWith { kind ->
        when (kind) {
            PickupKind.STAR -> Sprites.star(kind.color)
            PickupKind.TIME_CRYSTAL -> Sprites.crystal(kind.color)
            else -> Sprites.powerUp(kind)
        }
    }
    private val warningGlow: ImageBitmap = Sprites.warningGlow(hazardAltColor)

    private fun syncPickups() {
        val alive = HashSet<Int>()
        for (pickup in engine.pickups) {
            alive += pickup.id
            pickupBorn.getOrPut(pickup.id) { visualTime }
        }
        pickupBorn.keys.retainAll(alive)
    }

    private fun syncWarnings() {
        val alive = HashSet<Int>()
        for (warning in engine.warnings) {
            alive += warning.id
            warningBorn.getOrPut(warning.id) { visualTime }
        }
        warningBorn.keys.retainAll(alive)
    }

    private fun updatePlayer() {
        if (!avatar.isHidden) {
            avatar.position = engine.playerPosition
            avatar.scale = engine.playerRadius / GameEngine.BASE_PLAYER_RADIUS
            avatar.alpha = if (engine.invulnerability > 0) (if (sin(visualTime * 32) > 0) 1f else 0.3f) else 1f
            shieldVisible = engine.hasShield
        }
        avatar.update(visualTime, frameDelta, world)

        // After a fatal hit the blade freezes in place and fades out.
        trailFadeStart?.let { start ->
            trail.opacity = (1 - (visualTime - start) / 0.4).toFloat().coerceIn(0f, 1f)
            return
        }

        // The blade streams behind faster as the rush speeds up.
        val drift = (260 + engine.intensity * 260) * (if (engine.isSlowMotion) 0.45 else 1.0)
        trail.update(engine.playerPosition, frameDelta, visualTime, drift, world)

        val slowTarget = if (engine.isSlowMotion) 0.14f else 0f
        if (abs(slowMoAlpha - slowTarget) > 0.001f) slowMoAlpha += (slowTarget - slowMoAlpha) * 0.15f
    }

    private fun updateBackground(delta: Double) {
        if (settings.reducedMotion && engine.phase != GameEngine.Phase.RUNNING) return
        val intensityBoost = (1 + engine.intensity * 2.2).toFloat()
        val slow = if (engine.isSlowMotion) 0.35f else 1f
        val running = if (engine.phase == GameEngine.Phase.FINISHED) 0.2f else 1f
        val factor = delta.toFloat() * intensityBoost * slow * running * (if (settings.reducedMotion) 0.4f else 1f)
        val h = max(height, 1f)
        for (star in stars) {
            star.y += star.speed * factor
            if (star.y > h + 4 * density) {
                star.y -= h + 8 * density
                star.x = Random.nextFloat() * max(width, 1f)
            }
        }
        for (index in gridLines.indices) {
            var y = gridLines[index] + 70 * density * factor
            if (y > h) y -= h
            gridLines[index] = y
        }
    }

    private fun updateEffects(delta: Double) {
        world.update(delta)
        floatingTexts.removeAll { visualTime - it.born > 0.8 }
        shockwaves.removeAll { visualTime - it.born > 0.45 }
        banner?.let { if (visualTime - it.born > max(0.1, it.duration - 0.4) + 0.37) banner = null }
    }

    // MARK: HUD

    private fun pushHUD() {
        val clock = engine.clock
        val tenths = ceil(clock * 10).toInt()
        val hud = HUDState(
            score = engine.displayScore,
            multiplier = engine.multiplier,
            comboProgress = Math.round(engine.comboProgress * 20) / 20.0,
            elapsed = engine.elapsed.toInt(),
            clockTenths = if (clock > 10) ceil(clock).toInt() * 10 else tenths,
            level = engine.level,
            hasShield = engine.hasShield,
            isCountdown = engine.phase == GameEngine.Phase.COUNTDOWN,
            powerUps = TimedPowerUp.entries.mapNotNull { powerUp ->
                val remaining = engine.activePowerUps[powerUp] ?: return@mapNotNull null
                ActivePowerUpDisplay(powerUp.pickup, ceil(remaining / powerUp.duration * 20) / 20)
            },
        )
        if (hud != lastHUD) {
            lastHUD = hud
            session?.updateHUD(hud)
        }
    }

    // MARK: Events → feedback

    private fun handle(events: List<GameEvent>) {
        for (event in events) {
            when (event) {
                is GameEvent.CountdownTick -> {
                    showBanner("${event.number}", 96f, Color.White, 0.8)
                    sound.play(SoundEffect.COUNTDOWN)
                    haptics.play(Haptic.LIGHT)
                }
                GameEvent.Go -> {
                    showBanner("GO!", 84f, theme.accent.color, 0.7)
                    sound.play(SoundEffect.GO)
                    haptics.play(Haptic.MEDIUM)
                }
                is GameEvent.StarCollected -> {
                    burst(event.position, PickupKind.STAR.color, 14, 120.0)
                    floatingText("+${event.points}", event.position, PickupKind.STAR.color, 20.0)
                    sound.play(SoundEffect.STAR)
                    haptics.play(Haptic.SOFT)
                }
                is GameEvent.TimeCrystal -> {
                    burst(event.position, PickupKind.TIME_CRYSTAL.color, 18, 140.0)
                    floatingText("+${event.seconds.toInt()}s", event.position, PickupKind.TIME_CRYSTAL.color, 26.0)
                    sound.play(SoundEffect.CRYSTAL)
                    haptics.play(Haptic.LIGHT)
                }
                is GameEvent.PowerUpCollected -> {
                    burst(event.position, event.kind.color, 26, 180.0)
                    shockwave(event.position, event.kind.color, 90.0)
                    showBanner(event.kind.title.uppercase(), 40f, event.kind.color.color, 1.0)
                    sound.play(SoundEffect.POWER_UP)
                    haptics.play(Haptic.MEDIUM)
                }
                is GameEvent.PowerUpExpired -> {
                    floatingText("${event.powerUp.pickup.title} ended", engine.playerPosition + Vec2(0.0, 40.0),
                        RGBColor.hex(0xAAAAAA), 14.0)
                }
                is GameEvent.NearMiss -> {
                    val color = if (event.perfect) RGBColor.hex(0xFF4FD8) else theme.accent
                    val player = engine.playerPosition
                    val midpoint = Vec2((event.position.x + player.x) / 2, player.y + 30)
                    floatingText(if (event.perfect) "PERFECT +${event.points}" else "CLOSE +${event.points}", midpoint, color,
                        if (event.perfect) 22.0 else 18.0)
                    burst(midpoint, color, if (event.perfect) 12 else 6, 90.0)
                    sound.play(if (event.perfect) SoundEffect.PERFECT else SoundEffect.NEAR_MISS)
                    haptics.play(if (event.perfect) Haptic.RIGID else Haptic.LIGHT)
                }
                is GameEvent.ShieldBroken -> {
                    shockwave(engine.playerPosition, PickupKind.SHIELD.color, 120.0)
                    burst(event.position, PickupKind.SHIELD.color, 30, 220.0)
                    shake(8f, 0.25)
                    showBanner("SHIELD BROKEN", 30f, PickupKind.SHIELD.color.color, 0.9)
                    sound.play(SoundEffect.SHIELD_BREAK)
                    haptics.play(Haptic.HEAVY)
                }
                is GameEvent.Hit -> {
                    if (event.fatal) {
                        playerDeath()
                    } else {
                        flash(hazardColor, 0.35f)
                        shake(10f, 0.3)
                        burst(engine.playerPosition, hazardColor, 24, 200.0)
                        if (event.penalty > 0) {
                            floatingText("-${event.penalty.toInt()}s", engine.playerPosition + Vec2(0.0, 50.0), hazardColor, 30.0)
                        }
                        sound.play(SoundEffect.HIT)
                        haptics.play(Haptic.HEAVY)
                    }
                }
                is GameEvent.HazardDestroyed -> {
                    val alt = event.kind == HazardKind.SPEEDER || event.kind == HazardKind.SPLITTER || event.kind == HazardKind.FRAGMENT
                    burst(event.position, if (alt) hazardAltColor else hazardColor, max(8, event.radius.toInt()), 160.0)
                }
                is GameEvent.Nova -> {
                    shockwave(event.position, PickupKind.NOVA.color, 700.0)
                    flash(PickupKind.NOVA.color, 0.4f)
                    shake(12f, 0.4)
                    if (event.cleared > 0) {
                        showBanner("NOVA ×${event.cleared}  +${event.points}", 36f, PickupKind.NOVA.color.color, 1.2)
                    }
                    sound.play(SoundEffect.NOVA)
                    haptics.play(Haptic.HEAVY)
                }
                is GameEvent.Split -> burst(event.position, hazardAltColor, 10, 120.0)
                is GameEvent.Warning -> sound.play(SoundEffect.WARNING)
                is GameEvent.LevelUp -> {
                    showBanner("LEVEL ${event.level}", 52f, theme.accent.color, 1.3)
                    sound.play(SoundEffect.LEVEL_UP)
                    haptics.play(Haptic.SUCCESS)
                }
                is GameEvent.MultiplierUp -> {
                    floatingText("×${event.multiplier} MULTIPLIER", engine.playerPosition + Vec2(0.0, 70.0),
                        RGBColor.hex(0xFFD84D), 22.0)
                    sound.play(SoundEffect.MULTIPLIER)
                    haptics.play(Haptic.MEDIUM)
                }
                is GameEvent.ClockTick -> {
                    sound.play(SoundEffect.TICK)
                    haptics.play(Haptic.LIGHT)
                }
                GameEvent.Finished -> when (engine.config.mode) {
                    GameMode.TIME_ATTACK -> {
                        showBanner("TIME!", 72f, theme.accent.color, 1.0)
                        sound.play(SoundEffect.GAME_OVER)
                        haptics.play(Haptic.WARNING)
                    }
                    GameMode.ZEN -> {
                        showBanner("NICE FLOW", 56f, theme.accent.color, 1.0)
                        sound.play(SoundEffect.LEVEL_UP)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun playerDeath() {
        val position = engine.playerPosition
        avatar.isHidden = true
        avatar.setEmitting(false)
        shieldVisible = false
        trail.setEmitting(false)
        trailFadeStart = visualTime
        burst(position, skin.glow, 60, 320.0, lifetime = 0.9)
        burst(position, hazardColor, 40, 240.0, lifetime = 0.7)
        shockwave(position, hazardColor, 220.0)
        flash(hazardColor, 0.55f)
        shake(16f, 0.5)
        sound.play(SoundEffect.HIT)
        sound.play(SoundEffect.GAME_OVER)
        haptics.play(Haptic.ERROR)
    }

    // MARK: Effects

    private fun burst(position: Vec2, color: RGBColor, count: Int, speed: Double, lifetime: Double = 0.55) {
        val particles = if (settings.reducedMotion) max(4, count / 2) else count
        world.emit(EmitterConfig(
            birthRate = 0.0,
            lifetime = lifetime, lifetimeRange = lifetime * 0.4,
            speed = speed, speedRange = speed * 0.6,
            angleRange = 2 * PI,
            scale = 0.55, scaleRange = 0.3, scaleSpeed = -0.7,
            alphaSpeed = -1.4,
        ), position.x, position.y, particles, color.color)
    }

    private fun floatingText(text: String, position: Vec2, color: RGBColor, size: Double) {
        floatingTexts += FloatingText(text, position.x, position.y, color.color, size, visualTime)
    }

    private fun showBanner(text: String, size: Float, color: Color, duration: Double) {
        banner = Banner(text, size, color, duration, visualTime)
    }

    private fun shockwave(position: Vec2, color: RGBColor, radius: Double) {
        if (settings.reducedMotion && radius >= 300) return
        shockwaves += Shockwave(position.x, position.y, color.color, radius, visualTime)
    }

    private fun flash(color: RGBColor, alpha: Float) {
        flashColor = color.color
        flashStrength = if (settings.reducedMotion) alpha * 0.4f else alpha
        flashBorn = visualTime
    }

    private fun shake(intensity: Float, duration: Double) {
        if (settings.reducedMotion) return
        val steps = max(2, (duration / 0.04).toInt())
        shakeOffsets = (0 until steps).map { step ->
            val falloff = 1 - step.toFloat() / steps
            Offset((Random.nextFloat() * 2 - 1) * intensity * falloff, (Random.nextFloat() * 2 - 1) * intensity * falloff)
        } + Offset.Zero
        shakeBorn = visualTime
    }

    private fun currentShake(): Offset {
        if (shakeOffsets.isEmpty()) return Offset.Zero
        val position = (visualTime - shakeBorn) / 0.04
        val index = position.toInt()
        if (index >= shakeOffsets.size) {
            shakeOffsets = emptyList()
            return Offset.Zero
        }
        val from = if (index == 0) Offset.Zero else shakeOffsets[index - 1]
        val to = shakeOffsets[index]
        val t = (position - index).toFloat()
        return Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t) * density
    }

    // MARK: Drawing

    fun draw(scope: DrawScope, assets: SceneAssets) {
        drawBackground(scope)
        val shake = currentShake()
        scope.translate(shake.x, shake.y) {
            drawWorld(this, assets)
        }
        drawOverlays(scope, assets)
    }

    private fun drawBackground(scope: DrawScope) {
        val top = if (settings.highContrast) Color.Black else theme.backgroundTop.color
        scope.drawRect(Brush.verticalGradient(listOf(top, theme.backgroundBottom.color)))
        val starColor = theme.star.color
        for (star in stars) {
            scope.drawCircle(starColor, star.radius, Offset(star.x, star.y), alpha = star.alpha * 0.4f, blendMode = BlendMode.Plus)
            scope.drawCircle(starColor, star.radius * 0.5f, Offset(star.x, star.y), alpha = star.alpha)
        }
        if (gridLines.isNotEmpty()) {
            val gridColor = theme.accent.color.copy(alpha = 0.12f)
            for (y in gridLines) {
                scope.drawRect(gridColor, Offset(0f, y - 0.75f * density), Size(width, 1.5f * density))
            }
        }
    }

    private fun drawWorld(scope: DrawScope, assets: SceneAssets) {
        val f = frame
        val playfield = engine.playfield

        // Edges when the playfield is narrower than the screen (tablets).
        if (width - playfield.width.toFloat() * f.scale > 24 * density) {
            val edge = theme.accent.color.copy(alpha = 0.25f)
            for (x in listOf(0.0, playfield.width)) {
                scope.drawRect(edge, Offset(f.x(x) - f.scale, f.y(playfield.height)), Size(2 * f.scale, (playfield.height * f.scale).toFloat()))
            }
        }

        drawPickups(scope, f, assets)
        drawHazards(scope, f)
        trail.draw(scope, f)
        avatar.draw(scope, f)
        drawShield(scope, f)
        world.draw(scope, f)
        drawShockwaves(scope, f)
        drawFloatingTexts(scope, f, assets)
        if (engine.config.modifier == RunModifier.BLACKOUT) drawVignette(scope, f)
        drawWarnings(scope, f, assets)
    }

    private fun drawHazards(scope: DrawScope, f: WorldFrame) {
        for (hazard in engine.hazards) {
            val visual = hazardVisuals[hazard.id] ?: continue
            val age = visualTime - visual.born
            val grow = if (settings.reducedMotion) 1.0 else min(1.0, 0.3 + 0.7 * (age / 0.18))
            val side = (hazard.radius * 2 * Sprites.GLOW_RATIO * grow * f.scale).toFloat()
            val center = f.point(hazard.position.x, hazard.position.y)
            val image = hazardImages.getValue(hazard.kind)
            if (hazard.kind == HazardKind.WOBBLER && !settings.reducedMotion) {
                // Half a turn every 1.2s (counter-clockwise in SpriteKit = anticlockwise on screen).
                scope.rotate((-age / 1.2 * 180).toFloat(), center) { drawSprite(image, center, side) }
            } else {
                scope.drawSprite(image, center, side)
            }
        }
    }

    private fun drawPickups(scope: DrawScope, f: WorldFrame, assets: SceneAssets) {
        for (pickup in engine.pickups) {
            val age = visualTime - (pickupBorn[pickup.id] ?: visualTime)
            val center = f.point(pickup.position.x, pickup.position.y)
            val r = pickup.radius
            when (pickup.kind) {
                PickupKind.STAR -> {
                    val side = (r * 2 * 1.6 * 1.3 * f.scale).toFloat()
                    scope.rotate((-age / 3 * 360).toFloat(), center) {
                        drawSprite(pickupImages.getValue(pickup.kind), center, side)
                    }
                }
                PickupKind.TIME_CRYSTAL -> {
                    val pulse = pingPong(age, 0.4, 1.15, 0.95)
                    val side = (r * 2 * 1.6 * 1.4 * pulse * f.scale).toFloat()
                    scope.drawSprite(pickupImages.getValue(pickup.kind), center, side)
                }
                else -> {
                    val pulse = pingPong(age, 0.5, 1.12, 0.94)
                    val side = (r * 2 * Sprites.GLOW_RATIO * pulse * f.scale).toFloat()
                    scope.drawSprite(pickupImages.getValue(pickup.kind), center, side)
                    // Icon fits a 40-unit box on the 128-unit canvas.
                    val iconSide = side * (40f / Sprites.ORB_CANVAS) * 0.9f
                    val painter = assets.pickupIcons[pickup.kind] ?: continue
                    scope.translate(center.x - iconSide / 2, center.y - iconSide / 2) {
                        with(painter) { draw(Size(iconSide, iconSide), colorFilter = WHITE_TINT) }
                    }
                }
            }
        }
    }

    /** Linear back-and-forth between [first] and [second], [half] seconds each way (like SKAction sequences). */
    private fun pingPong(age: Double, half: Double, first: Double, second: Double): Double {
        val start = 1.0
        // SpriteKit starts at scale 1, goes to `first`, then alternates first ↔ second.
        if (age < half) return start + (first - start) * (age / half)
        val cycle = ((age - half) / half)
        val leg = cycle.toInt()
        val t = cycle - leg
        return if (leg % 2 == 0) first + (second - first) * t else second + (first - second) * t
    }

    private fun drawShield(scope: DrawScope, f: WorldFrame) {
        if (!shieldVisible || avatar.isHidden) return
        val pulse = pingPong(visualTime, 0.45, 1.08, 0.96)
        val center = f.point(engine.playerPosition.x, engine.playerPosition.y)
        val radius = (25 * pulse * f.scale).toFloat()
        val color = PickupKind.SHIELD.color.color
        scope.drawCircle(color, radius, center, alpha = 0.12f)
        scope.drawCircle(color, radius, center, alpha = 0.35f, style = Stroke(7 * f.scale), blendMode = BlendMode.Plus)
        scope.drawCircle(color, radius, center, style = Stroke(3 * f.scale))
    }

    private fun drawShockwaves(scope: DrawScope, f: WorldFrame) {
        for (wave in shockwaves) {
            val t = ((visualTime - wave.born) / 0.45).coerceIn(0.0, 1.0)
            val eased = 1 - (1 - t).pow(2)
            val scale = 1 + (wave.radius / 10 - 1) * eased
            val radius = (10 * scale * f.scale).toFloat()
            val alpha = (1 - t).toFloat()
            // SpriteKit scales the stroke with the ring, so it thickens as it grows.
            val line = (3 * scale * f.scale).toFloat()
            scope.drawCircle(wave.color, radius, f.point(wave.x, wave.y), alpha = alpha * 0.4f, style = Stroke(line * 2), blendMode = BlendMode.Plus)
            scope.drawCircle(wave.color, radius, f.point(wave.x, wave.y), alpha = alpha, style = Stroke(line))
        }
    }

    private fun drawFloatingTexts(scope: DrawScope, f: WorldFrame, assets: SceneAssets) {
        for (label in floatingTexts) {
            val age = visualTime - label.born
            val layout = label.layout ?: measure(assets.textMeasurer, label.text, (label.size * f.scale).toFloat(), label.color)
                .also { label.layout = it }
            val riseT = (age / 0.8).coerceIn(0.0, 1.0)
            val rise = 46 * (1 - (1 - riseT).pow(2))
            val scale = when {
                age < 0.12 -> 0.6 + (1.1 - 0.6) * (age / 0.12)
                age < 0.22 -> 1.1 - 0.1 * ((age - 0.12) / 0.1)
                else -> 1.0
            }
            val alpha = if (age < 0.45) 1.0 else (1 - (age - 0.45) / 0.35).coerceIn(0.0, 1.0)
            val center = f.point(label.x, label.y + rise)
            drawCentered(scope, layout, center, scale.toFloat(), alpha.toFloat())
        }
    }

    private fun drawVignette(scope: DrawScope, f: WorldFrame) {
        // Clear around the player, fully dark ~200 units away.
        val center = f.point(engine.playerPosition.x, engine.playerPosition.y)
        val outer = 200 * f.scale
        val brush = Brush.radialGradient(
            0f to Color.Transparent,
            (115f / 200f) to Color.Transparent,
            1f to Color.Black,
            center = center, radius = outer,
        )
        scope.drawRect(brush, topLeft = Offset(-width, -height), size = Size(width * 3, height * 3), alpha = 0.96f)
    }

    private fun drawWarnings(scope: DrawScope, f: WorldFrame, assets: SceneAssets) {
        val playfield = engine.playfield
        val color = hazardAltColor.color
        for (warning in engine.warnings) {
            val age = visualTime - (warningBorn[warning.id] ?: visualTime)
            val laneWidth = ((warning.radius * 2 + 8) * f.scale).toFloat()
            scope.drawRect(
                color.copy(alpha = 0.13f),
                Offset(f.x(warning.x) - laneWidth / 2, f.y(playfield.height)),
                Size(laneWidth, (playfield.height * f.scale).toFloat()),
                blendMode = BlendMode.Plus,
            )
            // Flashes between 0.25 and 1 every 0.18s.
            val phase = (age % 0.18) / 0.09
            val iconAlpha = if (phase < 1) 1 - 0.75 * phase else 0.25 + 0.75 * (phase - 1)
            val center = f.point(warning.x, playfield.height - 34)
            val side = 34 * f.scale
            scope.drawSprite(warningGlow, center, side, iconAlpha.toFloat())
            val iconSide = side * 0.62f
            scope.translate(center.x - iconSide / 2, center.y - iconSide / 2) {
                with(assets.warningIcon) {
                    draw(Size(iconSide, iconSide), alpha = iconAlpha.toFloat(), colorFilter = ColorFilter.tint(color))
                }
            }
        }
    }

    private fun drawOverlays(scope: DrawScope, assets: SceneAssets) {
        if (slowMoAlpha > 0.002f) {
            scope.drawRect(PickupKind.SLOW_MO.color.color, alpha = slowMoAlpha, blendMode = BlendMode.Plus)
        }
        val flashAlpha = flashStrength * (1 - ((visualTime - flashBorn) / 0.35).toFloat()).coerceIn(0f, 1f)
        if (flashAlpha > 0.002f) scope.drawRect(flashColor, alpha = flashAlpha)
        drawBanner(scope, assets)
    }

    private fun drawBanner(scope: DrawScope, assets: SceneAssets) {
        val banner = banner ?: return
        val age = visualTime - banner.born
        val hold = max(0.1, banner.duration - 0.4)
        val layout = banner.layout ?: measure(assets.textMeasurer, banner.text, banner.size * density, banner.color)
            .also { banner.layout = it }
        val (scale, alpha) = when {
            age < 0.18 -> (1.6 - 0.6 * (age / 0.18)) to min(1.0, age / 0.12)
            age < 0.18 + hold -> 1.0 to 1.0
            else -> {
                val t = ((age - 0.18 - hold) / 0.25).coerceIn(0.0, 1.0)
                (1 - 0.15 * t) to (1 - t)
            }
        }
        // iOS places banners at 62% of the height measured from the bottom.
        val center = Offset(width / 2, height * 0.38f)
        drawCentered(scope, layout, center, scale.toFloat() * 1.08f, alpha.toFloat() * 0.35f)
        drawCentered(scope, layout, center, scale.toFloat(), alpha.toFloat())
    }

    private fun measure(measurer: TextMeasurer, text: String, sizePx: Float, color: Color): TextLayoutResult =
        measurer.measure(
            AnnotatedString(text),
            style = TextStyle(color = color, fontSize = sizePx.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center),
            softWrap = false,
            // 1sp == 1px so sizes can be given directly in pixels.
            density = PIXEL_DENSITY,
        )

    private fun drawCentered(scope: DrawScope, layout: TextLayoutResult, center: Offset, scale: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val size = layout.size
        scope.scale(scale, scale, pivot = center) {
            drawText(layout, topLeft = Offset(center.x - size.width / 2f, center.y - size.height / 2f), alpha = alpha.coerceIn(0f, 1f))
        }
    }

    // MARK: Debug

    val nodeCount: Int
        get() = engine.hazards.size + engine.pickups.size + engine.warnings.size + world.particles.size + floatingTexts.size

    private companion object {
        val PIXEL_DENSITY = Density(1f, 1f)
        val WHITE_TINT = ColorFilter.tint(Color.White)
    }
}
