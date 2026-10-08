//
//  Particles.kt
//  Starshower Run
//
//  A small particle system modelled on SKEmitterNode: the same knobs
//  (birth rate, lifetime, speed, ranges, scale/alpha speeds, colour
//  sequences) so the iOS effect tuning carries over number for number.
//  Particles live in world units (y-up); `WorldFrame` maps them to pixels.
//

package com.starshower.run.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.starshower.run.ui.starPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Maps world coordinates (y-up) to screen pixels. */
data class WorldFrame(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    /** World height; y = height is the top of the playfield. */
    val height: Double,
) {
    fun x(worldX: Double): Float = offsetX + (worldX * scale).toFloat()
    fun y(worldY: Double): Float = offsetY + ((height - worldY) * scale).toFloat()
    fun point(worldX: Double, worldY: Double) = Offset(x(worldX), y(worldY))
}

enum class ParticleShape(
    /** Texture size in points on iOS; `scale` multiplies it. */
    val baseSize: Float,
) {
    SOFT_DOT(24f), TINY_STAR(24f), BUBBLE(32f)
}

class Particle(
    var x: Double,
    var y: Double,
    val vx: Double,
    val vy: Double,
    val life: Double,
    var scale: Double,
    val scaleSpeed: Double,
    val alphaSpeed: Double,
    var rotation: Double,
    val rotationSpeed: Double,
    val color: Color,
    val colors: List<Color>?,
    val shape: ParticleShape,
    val additive: Boolean,
    val twinkle: Boolean,
) {
    var age = 0.0
    var alpha = 1.0

    val isDead: Boolean get() = age >= life || scale <= 0 || (alpha <= 0 && !twinkle)

    fun currentColor(): Color {
        val sequence = colors ?: return color
        if (sequence.size < 2) return sequence.firstOrNull() ?: color
        val t = (age / life).coerceIn(0.0, 1.0) * (sequence.size - 1)
        val index = minOf(t.toInt(), sequence.size - 2)
        return lerp(sequence[index], sequence[index + 1], (t - index).toFloat())
    }

    fun currentAlpha(): Float {
        if (!twinkle) return alpha.toFloat().coerceIn(0f, 1f)
        // 0 → 1 at 30% of the lifetime → 0 at the end.
        val t = age / life
        return (if (t < 0.3) t / 0.3 else 1 - (t - 0.3) / 0.7).toFloat().coerceIn(0f, 1f)
    }
}

/** Emission settings, using SpriteKit's conventions (ranges are total spread). */
data class EmitterConfig(
    val birthRate: Double,
    val lifetime: Double,
    val lifetimeRange: Double = 0.0,
    val speed: Double = 0.0,
    val speedRange: Double = 0.0,
    /** Radians, 0 = +x, counter-clockwise, y-up. */
    val angle: Double = 0.0,
    val angleRange: Double = 0.0,
    val positionRangeX: Double = 0.0,
    val positionRangeY: Double = 0.0,
    val scale: Double = 1.0,
    val scaleRange: Double = 0.0,
    val scaleSpeed: Double = 0.0,
    val alphaSpeed: Double = 0.0,
    val rotationSpeed: Double = 0.0,
    val twinkle: Boolean = false,
    val shape: ParticleShape = ParticleShape.SOFT_DOT,
    val color: Color = Color.White,
    val colorSequence: List<Color>? = null,
    val additive: Boolean = true,
)

class ParticleSystem {
    val particles = ArrayList<Particle>()

    fun emit(config: EmitterConfig, x: Double, y: Double, count: Int, color: Color = config.color) {
        repeat(count) { particles += spawn(config, x, y, color) }
    }

    private fun spread(range: Double) = if (range == 0.0) 0.0 else (Random.nextDouble() - 0.5) * range

    private fun spawn(config: EmitterConfig, x: Double, y: Double, color: Color): Particle {
        val angle = config.angle + spread(config.angleRange)
        val speed = config.speed + spread(config.speedRange)
        return Particle(
            x = x + spread(config.positionRangeX),
            y = y + spread(config.positionRangeY),
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            life = maxOf(0.01, config.lifetime + spread(config.lifetimeRange)),
            scale = config.scale + spread(config.scaleRange),
            scaleSpeed = config.scaleSpeed,
            alphaSpeed = config.alphaSpeed,
            rotation = Random.nextDouble() * 2 * PI,
            rotationSpeed = config.rotationSpeed,
            color = color,
            colors = config.colorSequence,
            shape = config.shape,
            additive = config.additive,
            twinkle = config.twinkle,
        )
    }

    fun update(dt: Double) {
        var index = 0
        while (index < particles.size) {
            val p = particles[index]
            p.age += dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.scale += p.scaleSpeed * dt
            p.alpha += p.alphaSpeed * dt
            p.rotation += p.rotationSpeed * dt
            if (p.isDead) {
                particles[index] = particles[particles.size - 1]
                particles.removeAt(particles.size - 1)
            } else {
                index += 1
            }
        }
    }

    fun clear() = particles.clear()

    /** Draws every particle; [originX]/[originY] shift local systems (e.g. sparkles that ride on the player). */
    fun draw(scope: DrawScope, frame: WorldFrame, originX: Double = 0.0, originY: Double = 0.0, opacity: Float = 1f) {
        for (p in particles) drawParticle(scope, frame, p, originX, originY, opacity)
    }

    private fun drawParticle(scope: DrawScope, frame: WorldFrame, p: Particle, originX: Double, originY: Double, opacity: Float) {
        val alpha = p.currentAlpha() * opacity
        if (alpha <= 0.01f) return
        val center = frame.point(originX + p.x, originY + p.y)
        val radius = (p.shape.baseSize * p.scale / 2 * frame.scale).toFloat()
        if (radius <= 0.1f) return
        val color = p.currentColor()
        val blend = if (p.additive) BlendMode.Plus else BlendMode.SrcOver
        when (p.shape) {
            ParticleShape.SOFT_DOT -> {
                // Two layers approximate the soft radial falloff of the iOS texture.
                scope.drawCircle(color, radius, center, alpha = alpha * 0.35f, blendMode = blend)
                scope.drawCircle(color, radius * 0.5f, center, alpha = alpha, blendMode = blend)
            }
            ParticleShape.TINY_STAR -> scope.translate(center.x, center.y) {
                rotate(Math.toDegrees(-p.rotation).toFloat(), pivot = Offset.Zero) {
                    scale(radius / 12f, radius / 12f, pivot = Offset.Zero) {
                        drawPath(TINY_STAR_PATH, color, alpha = alpha, blendMode = blend)
                    }
                }
            }
            ParticleShape.BUBBLE -> {
                scope.drawCircle(color, radius * 0.82f, center, alpha = alpha, style = Stroke(radius * 0.16f), blendMode = blend)
                scope.drawCircle(color, radius * 0.16f, Offset(center.x - radius * 0.3f, center.y - radius * 0.35f), alpha = alpha * 0.8f, blendMode = blend)
            }
        }
    }

    companion object {
        /** 4-point sparkle in a 24-unit box centred on the origin. */
        val TINY_STAR_PATH: Path = starPath(Offset.Zero, 11f, 4.5f, 4)
    }
}

/** Continuous emitter (aura, sparkles, trail particles, meteor streaks). */
class Emitter(var config: EmitterConfig) {
    private var accumulator = 0.0
    var isEmitting = true

    fun advance(dt: Double, x: Double, y: Double, system: ParticleSystem, color: Color = config.color) {
        if (!isEmitting || config.birthRate <= 0) return
        accumulator += dt * config.birthRate
        val count = accumulator.toInt()
        if (count > 0) {
            accumulator -= count
            system.emit(config, x, y, count, color)
        }
    }

    fun reset() {
        accumulator = 0.0
    }
}
