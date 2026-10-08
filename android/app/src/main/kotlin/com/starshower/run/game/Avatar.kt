//
//  Avatar.kt
//  Starshower Run
//
//  The player's look, shared by the game and the Armory's live preview:
//  • AvatarRenderer – the skin plus animated effects (orbiters, aura…)
//  • TrailRenderer  – a Fruit Ninja–style tapered blade trail
//

package com.starshower.run.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.starshower.run.core.engine.GameEngine
import com.starshower.run.core.engine.RGBColor
import com.starshower.run.core.engine.Vec2
import com.starshower.run.core.progression.SkinEffect
import com.starshower.run.core.progression.SkinLook
import com.starshower.run.core.progression.SkinStyle
import com.starshower.run.core.progression.TrailColorMode
import com.starshower.run.core.progression.TrailLook
import com.starshower.run.core.progression.TrailParticles
import com.starshower.run.ui.color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Draws [image] centred on [center] with a square side of [sidePx]. */
fun DrawScope.drawSprite(
    image: androidx.compose.ui.graphics.ImageBitmap,
    center: Offset,
    sidePx: Float,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    if (sidePx < 1f || alpha <= 0f) return
    val side = sidePx.toInt().coerceAtLeast(1)
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset((center.x - side / 2f).toInt(), (center.y - side / 2f).toInt()),
        dstSize = IntSize(side, side),
        alpha = alpha.coerceIn(0f, 1f),
        colorFilter = colorFilter,
        blendMode = blendMode,
    )
}

/** A soft radial glow, like the iOS `softDot` texture. */
fun DrawScope.drawGlow(center: Offset, radiusPx: Float, color: Color, alpha: Float, blendMode: BlendMode = BlendMode.Plus) {
    if (radiusPx < 0.5f || alpha <= 0f) return
    drawCircle(
        Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radiusPx),
        radiusPx, center, alpha = alpha.coerceIn(0f, 1f), blendMode = blendMode,
    )
}

// MARK: - Player avatar

class AvatarRenderer(
    val look: SkinLook,
    /** Base radius in world units. */
    val radius: Double = GameEngine.BASE_PLAYER_RADIUS,
    private val reducedMotion: Boolean = false,
) {
    val body = Sprites.skin(look)
    val bodySide: Double get() = radius * 2 * Sprites.GLOW_RATIO

    var position = Vec2.ZERO
    var scale = 1.0
    var alpha = 1f
    var isHidden = false
    private var time = 0.0

    private val accent = look.accentColor.color
    private var hue: Color? = null

    private val aura: Emitter? = if (look.has(SkinEffect.AURA)) Emitter(EmitterConfig(
        birthRate = if (reducedMotion) 25.0 else 60.0,
        lifetime = 0.5,
        speed = 70.0, speedRange = 30.0,
        angle = -PI / 2, angleRange = 0.9,
        positionRangeX = radius, positionRangeY = radius,
        scale = 0.55, scaleRange = 0.2, scaleSpeed = -0.9,
        alphaSpeed = -2.0,
        color = accent,
    )) else null

    private val sparkles = ParticleSystem()
    private val sparkle: Emitter? = if (look.has(SkinEffect.SPARKLE)) Emitter(EmitterConfig(
        birthRate = 7.0,
        lifetime = 0.8,
        speed = 8.0, angleRange = 2 * PI,
        positionRangeX = radius * 3.2, positionRangeY = radius * 3.2,
        scale = 0.4, scaleRange = 0.2,
        rotationSpeed = 3.0,
        twinkle = true,
        shape = ParticleShape.TINY_STAR,
        color = if (look.has(SkinEffect.HUE_CYCLE)) Color.White else accent,
    )) else null

    fun setEmitting(emitting: Boolean) {
        aura?.isEmitting = emitting
        sparkle?.isEmitting = emitting
    }

    /** Call once per frame. Aura particles are left behind in [world]. */
    fun update(time: Double, dt: Double, world: ParticleSystem) {
        this.time = time
        hue = if (look.has(SkinEffect.HUE_CYCLE)) RGBColor.hsb(time * 0.35, 0.75, 1.0).color else null
        if (!isHidden) {
            aura?.advance(dt, position.x, position.y, world, hue ?: accent)
            sparkle?.advance(dt, 0.0, 0.0, sparkles)
        }
        sparkles.update(dt)
    }

    fun draw(scope: DrawScope, frame: WorldFrame) {
        if (isHidden) return
        val center = frame.point(position.x, position.y)
        val r = (radius * scale * frame.scale).toFloat()
        val accent = hue ?: accent

        // Breathing glow
        if (look.has(SkinEffect.PULSE)) {
            val phase = if (reducedMotion) 0.0 else (1 - cos(2 * PI * time / 1.4)) / 2
            val glowScale = 1 + 0.3 * phase
            val glowAlpha = 0.45 - 0.25 * phase
            scope.drawGlow(center, r * 2.5f * glowScale.toFloat(), look.glow.color, glowAlpha.toFloat() * alpha)
        }

        // RGB-split ghosts – pseudo-random glitch bursts, ~15% of the time.
        if (look.has(SkinEffect.GLITCH)) {
            val slot = (time * 18).toInt()
            val roll = abs(sin(slot * 12.9898) * 43_758.5453) % 1
            if (roll > (if (reducedMotion) 0.95 else 0.82)) {
                for ((index, tint) in GLITCH_TINTS.withIndex()) {
                    val direction = if (index == 0) -1f else 1f
                    val ghostCenter = Offset(
                        center.x + direction * r * (0.25 + roll * 0.3).toFloat(),
                        center.y - ((roll - 0.9) * r).toFloat(),
                    )
                    scope.drawSprite(body, ghostCenter, (bodySide * scale * frame.scale).toFloat(), 0.7f * alpha,
                        ColorFilter.tint(tint, BlendMode.Modulate), BlendMode.Plus)
                }
            }
        }

        // Body
        val tint = if (look.style == SkinStyle.PRISM && hue != null) ColorFilter.tint(hue!!, BlendMode.Modulate) else null
        scope.drawSprite(body, center, (bodySide * scale * frame.scale).toFloat(), alpha, tint)

        // Rotating dashed halo
        if (look.has(SkinEffect.SPIN_RING)) {
            val degrees = if (reducedMotion) 0.0 else -(time / 3.2) * 360
            val ringRadius = r * 1.75f
            val unit = (scale * frame.scale).toFloat()
            scope.rotate(-degrees.toFloat(), center) {
                drawCircle(accent.copy(alpha = 0.35f * alpha), ringRadius, center, style = Stroke(3.5f * unit,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * unit, 5f * unit))), blendMode = BlendMode.Plus)
                drawCircle(accent.copy(alpha = alpha), ringRadius, center, style = Stroke(2f * unit,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * unit, 5f * unit))))
            }
        }

        // Moons
        if (look.has(SkinEffect.ORBITERS)) {
            val spin = time / (if (reducedMotion) 5.0 else 2.2) * 2 * PI
            for (index in 0 until 3) {
                // y-up rotation: counter-clockwise in world space.
                val angle = spin + index * 2 * PI / 3
                val moon = Offset(center.x + (cos(angle) * r * 2).toFloat(), center.y - (sin(angle) * r * 2).toFloat())
                scope.drawGlow(moon, r * 0.375f, accent, alpha)
                scope.drawCircle(Color.White, r * 0.12f, moon, alpha = alpha)
            }
        }

        // Sparkles ride along with the player.
        sparkles.draw(scope, frame, position.x, position.y, alpha)
    }

    private companion object {
        val GLITCH_TINTS = listOf(RGBColor.hex(0xFF2E6A).color, RGBColor.hex(0x2EF2FF).color)
    }
}

// MARK: - Blade trail

class TrailRenderer(
    val look: TrailLook,
    /** Player radius in world units (the blade is as wide as the dot × look.width). */
    playerRadius: Double = GameEngine.BASE_PLAYER_RADIUS,
    private val reducedMotion: Boolean = false,
) {
    private val baseWidth = playerRadius * 2
    private val points = ArrayList<Vec2>()
    private val colors = look.colors.ifEmpty { listOf(RGBColor.WHITE) }
    private var time = 0.0

    /** Fades the whole blade out (on death). */
    var opacity = 1f

    /** Body sprite and size used for Phantom afterimages. */
    var ghostImage: androidx.compose.ui.graphics.ImageBitmap? = null
    var ghostSide: Double = 0.0
    private class Ghost(var x: Double, var y: Double, val drift: Double) {
        var age = 0.0
    }
    private val ghosts = ArrayList<Ghost>()
    private var ghostTimer = 0.0
    private val ghostTint = ColorFilter.tint(
        (look.colors.lastOrNull() ?: RGBColor.WHITE).color.copy(alpha = 0.6f), BlendMode.SrcAtop,
    )

    private val emitter: Emitter? = makeEmitter()

    private fun makeEmitter(): Emitter? {
        val c = colors.map { it.color }
        val config = when (look.particles) {
            TrailParticles.NONE -> return null
            TrailParticles.SPARKS -> EmitterConfig(
                birthRate = 45.0, lifetime = 0.4, speed = 110.0, speedRange = 50.0,
                angle = -PI / 2, angleRange = 0.8, scale = 0.3, scaleRange = 0.15, alphaSpeed = -2.2,
                color = look.primary.color,
            )
            TrailParticles.EMBERS -> EmitterConfig(
                birthRate = 55.0, lifetime = 0.7, speed = 70.0, speedRange = 40.0,
                angle = -PI / 2, angleRange = 1.0, scale = 0.4, scaleSpeed = -0.5, alphaSpeed = -1.4,
                color = look.primary.color, colorSequence = c,
            )
            TrailParticles.SNOW -> EmitterConfig(
                birthRate = 18.0, lifetime = 1.0, speed = 50.0, speedRange = 30.0,
                angle = -PI / 2, angleRange = 1.3, scale = 0.35, scaleRange = 0.2, rotationSpeed = 2.0,
                alphaSpeed = -1.0, shape = ParticleShape.TINY_STAR,
                color = if (c.size > 1) c[1] else Color.White,
            )
            TrailParticles.STARS -> EmitterConfig(
                birthRate = 26.0, lifetime = 0.8, speed = 60.0, speedRange = 30.0,
                angleRange = 2 * PI, scale = 0.45, scaleRange = 0.2, rotationSpeed = 4.0,
                alphaSpeed = -1.2, shape = ParticleShape.TINY_STAR,
                color = if (c.size > 1) c[1] else c[0],
            )
            TrailParticles.BUBBLES -> EmitterConfig(
                birthRate = 12.0, lifetime = 1.2, speed = 40.0, angle = -PI / 2, angleRange = 1.2,
                scale = 0.35, scaleSpeed = 0.3, alphaSpeed = -0.9, shape = ParticleShape.BUBBLE,
                color = look.primary.color, additive = false,
            )
            TrailParticles.ZAPS -> EmitterConfig(
                birthRate = 70.0, lifetime = 0.18, speed = 260.0, speedRange = 120.0,
                angleRange = 2 * PI, scale = 0.25, alphaSpeed = -5.0,
                color = colors[0].lighter(0.5).color,
            )
        }
        val rate = if (reducedMotion) config.birthRate * 0.5 else config.birthRate
        return Emitter(config.copy(birthRate = rate))
    }

    fun setEmitting(emitting: Boolean) {
        emitter?.isEmitting = emitting
    }

    fun reset(at: Vec2) {
        points.clear()
        repeat(maxOf(look.length, 1)) { points += at }
        ghosts.clear()
        emitter?.reset()
        opacity = 1f
    }

    /**
     * Call once per frame with the head position. [drift] (units/s) pushes
     * the tail downward so the blade streams behind the dot like it's flying.
     */
    fun update(head: Vec2, dt: Double, time: Double, drift: Double, world: ParticleSystem) {
        this.time = time
        emitter?.let { emitter ->
            val color = if (look.mode == TrailColorMode.RAINBOW) RGBColor.hsb(time * 0.45, 0.85, 1.0).color else emitter.config.color
            emitter.advance(dt, head.x, head.y, world, color)
        }
        updateGhosts(head, dt, drift)
        if (!look.hasRibbon) return

        if (points.isEmpty()) reset(head)
        val fall = drift * dt
        for (index in points.indices) points[index] = Vec2(points[index].x, points[index].y - fall)
        points.add(0, head)
        while (points.size > look.length) points.removeAt(points.size - 1)
    }

    private fun updateGhosts(head: Vec2, dt: Double, drift: Double) {
        if (!look.afterimage || ghostImage == null) return
        var index = 0
        while (index < ghosts.size) {
            val ghost = ghosts[index]
            ghost.age += dt
            ghost.y -= ghost.drift * dt
            if (ghost.age >= 0.3) ghosts.removeAt(index) else index += 1
        }
        if (emitter?.isEmitting == false || opacity < 1f) return
        ghostTimer -= dt
        if (ghostTimer > 0) return
        ghostTimer = if (reducedMotion) 0.1 else 0.045
        ghosts += Ghost(head.x, head.y, drift)
    }

    fun draw(scope: DrawScope, frame: WorldFrame) {
        drawGhosts(scope, frame)
        if (!look.hasRibbon || opacity <= 0f) return
        val count = points.size
        val blend = if (look.glow) BlendMode.Plus else BlendMode.SrcOver
        for (index in 0 until look.length - 1) {
            if (index + 1 >= count) break
            var a = points[index]
            var b = points[index + 1]
            val t = index.toDouble() / maxOf(count - 1, 1)
            val width = baseWidth * look.width * (1 - t).pow(0.75) + 1

            if (look.jitter > 0 && index > 0) {
                val amplitude = look.jitter * width * 0.9
                a = Vec2(a.x + sin(time * 53 + index * 2.3) * amplitude, a.y)
                b = Vec2(b.x + sin(time * 53 + (index + 1) * 2.3) * amplitude, b.y)
            }
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = sqrt(dx * dx + dy * dy)
            if (length <= 0.01) continue

            // Extend each segment slightly so the round caps overlap smoothly.
            val extend = width * 0.45 / length
            val start = frame.point(a.x - dx * extend, a.y - dy * extend)
            val end = frame.point(b.x + dx * extend, b.y + dy * extend)
            val color = look.color(t, index, time).color
            val alpha = ((1 - t).pow(1.1) * 0.95).toFloat() * opacity
            val widthPx = (width * frame.scale).toFloat()
            // Soft edges: a wide faint stroke under a narrower bright core.
            scope.drawLine(color, start, end, widthPx, StrokeCap.Round, alpha = alpha * 0.45f, blendMode = blend)
            scope.drawLine(color, start, end, widthPx * 0.55f, StrokeCap.Round, alpha = alpha, blendMode = blend)
        }
    }

    private fun drawGhosts(scope: DrawScope, frame: WorldFrame) {
        val image = ghostImage ?: return
        for (ghost in ghosts) {
            val progress = ghost.age / 0.3
            val side = ghostSide * (1 - 0.2 * progress) * frame.scale
            scope.drawSprite(image, frame.point(ghost.x, ghost.y), side.toFloat(),
                (0.45 * (1 - progress)).toFloat() * opacity, ghostTint, BlendMode.Plus)
        }
    }
}
