//
//  Sprites.kt
//  Starshower Run
//
//  Procedurally draws every sprite (glowing orbs, stars, crystals, power-up
//  shells) into cached bitmaps so the game ships with zero image assets –
//  the Android counterpart of the iOS TextureFactory.
//

package com.starshower.run.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.starshower.run.core.engine.PickupKind
import com.starshower.run.core.engine.RGBColor
import com.starshower.run.core.progression.SkinLook
import com.starshower.run.core.progression.SkinStyle
import com.starshower.run.ui.color
import com.starshower.run.ui.starPath

enum class OrbDecoration { PLAIN, RING, CORE, HOLLOW, DASHED }

object Sprites {
    /** Canvas size (units) for orb sprites and the core radius inside it. */
    const val ORB_CANVAS = 128f
    const val ORB_CORE = 40f
    /** Sprite size multiplier so the *core* matches the entity radius. */
    const val GLOW_RATIO = ORB_CANVAS / (ORB_CORE * 2)

    /** Bitmap pixels per canvas unit. */
    private const val RESOLUTION = 2f

    private val cache = HashMap<String, ImageBitmap>()

    private fun cached(key: String, canvasSize: Float, draw: DrawScope.(Size) -> Unit): ImageBitmap =
        cache.getOrPut(key) {
            val pixels = (canvasSize * RESOLUTION).toInt()
            val bitmap = ImageBitmap(pixels, pixels)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(pixels.toFloat(), pixels.toFloat())) {
                scale(RESOLUTION, RESOLUTION, pivot = Offset.Zero) {
                    draw(Size(canvasSize, canvasSize))
                }
            }
            bitmap
        }

    private fun DrawScope.radialGlow(center: Offset, inner: Float, outer: Float, color: Color, alpha: Float) {
        val brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            (inner / outer) to color.copy(alpha = alpha),
            1f to color.copy(alpha = 0f),
            center = center, radius = outer,
        )
        drawCircle(brush, outer, center)
    }

    // MARK: Hazards

    fun hazard(color: RGBColor, decoration: OrbDecoration, outline: Boolean): ImageBitmap =
        cached("hazard|$color|$decoration|$outline", ORB_CANVAS) { size ->
            val c = Offset(size.width / 2, size.height / 2)
            val base = color.color
            radialGlow(c, ORB_CORE * 0.7f, size.width / 2, base, 0.6f)

            // Body with a subtle top-left highlight
            val highlight = Offset(c.x - ORB_CORE * 0.35f, c.y - ORB_CORE * 0.35f)
            val body = Brush.radialGradient(
                0f to color.lighter(0.35).color,
                0.55f to base,
                1f to color.darker(0.25).color,
                center = highlight, radius = ORB_CORE * 1.35f,
            )
            drawCircle(body, ORB_CORE, c)

            when (decoration) {
                OrbDecoration.PLAIN -> Unit
                OrbDecoration.RING -> drawCircle(Color.White.copy(alpha = 0.55f), ORB_CORE - 9, c, style = Stroke(4f))
                OrbDecoration.CORE -> drawCircle(color.lighter(0.7).color, ORB_CORE * 0.35f, c)
                OrbDecoration.HOLLOW -> drawCircle(color.darker(0.55).color, ORB_CORE * 0.55f, c)
                OrbDecoration.DASHED -> drawCircle(
                    Color.White.copy(alpha = 0.7f), ORB_CORE - 8, c,
                    style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))),
                )
            }

            if (outline) drawCircle(Color.White, ORB_CORE - 2.5f, c, style = Stroke(5f))
        }

    // MARK: Player skins

    fun skin(look: SkinLook): ImageBitmap =
        cached("skin|${look.primary}|${look.glow}|${look.style}", ORB_CANVAS) { size ->
            val c = Offset(size.width / 2, size.height / 2)
            radialGlow(c, ORB_CORE * 0.8f, size.width / 2, look.glow.color, 0.75f)
            when (look.style) {
                SkinStyle.SOLID -> drawCircle(look.primary.color, ORB_CORE, c)
                SkinStyle.RING -> {
                    drawCircle(look.primary.color, ORB_CORE * 0.78f, c)
                    drawCircle(Color.White.copy(alpha = 0.95f), ORB_CORE - 2.5f, c, style = Stroke(5f))
                }
                SkinStyle.CORE -> drawCircle(
                    Brush.radialGradient(0.1f to Color.White, 1f to look.primary.color, center = c, radius = ORB_CORE),
                    ORB_CORE, c,
                )
                SkinStyle.PRISM -> {
                    // White body – tinted at runtime.
                    drawCircle(Color.White, ORB_CORE, c)
                    drawCircle(Color.White.copy(alpha = 0.6f), ORB_CORE * 0.6f, c, style = Stroke(3f))
                }
                SkinStyle.VOID -> {
                    drawCircle(look.primary.color, ORB_CORE, c)
                    drawCircle(Color.White, ORB_CORE - 3, c, style = Stroke(6f))
                }
            }
        }

    // MARK: Pickups

    const val PICKUP_CANVAS = 96f

    fun star(color: RGBColor): ImageBitmap = cached("star|$color", PICKUP_CANVAS) { size ->
        val c = Offset(size.width / 2, size.height / 2)
        radialGlow(c, 10f, size.width / 2, color.color, 0.7f)
        drawPath(starPath(c, 30f, 13f, 5), color.color)
        drawPath(starPath(c, 16f, 7f, 5), color.lighter(0.6).color)
    }

    fun crystal(color: RGBColor): ImageBitmap = cached("crystal|$color", PICKUP_CANVAS) { size ->
        val c = Offset(size.width / 2, size.height / 2)
        radialGlow(c, 10f, size.width / 2, color.color, 0.7f)
        val diamond = Path().apply {
            moveTo(c.x, c.y - 32)
            lineTo(c.x + 20, c.y)
            lineTo(c.x, c.y + 32)
            lineTo(c.x - 20, c.y)
            close()
        }
        drawPath(diamond, color.color)
        val line = Color.White.copy(alpha = 0.7f)
        drawLine(line, Offset(c.x, c.y - 32), Offset(c.x, c.y + 32), 2.5f)
        drawLine(line, Offset(c.x - 20, c.y), Offset(c.x + 20, c.y), 2.5f)
    }

    /** Power-up shell; the icon is drawn on top at runtime. */
    fun powerUp(kind: PickupKind): ImageBitmap = cached("powerup|${kind.name}", ORB_CANVAS) { size ->
        val c = Offset(size.width / 2, size.height / 2)
        val color = kind.color.color
        radialGlow(c, ORB_CORE * 0.8f, size.width / 2, color, 0.7f)
        drawCircle(color.copy(alpha = 0.35f), ORB_CORE, c)
        drawCircle(color, ORB_CORE - 2.5f, c, style = Stroke(5f))
    }

    /** Soft glow behind the meteor warning icon. */
    fun warningGlow(color: RGBColor): ImageBitmap = cached("warning|$color", 64f) { size ->
        val c = Offset(size.width / 2, size.height / 2)
        radialGlow(c, 6f, 32f, color.color, 0.6f)
    }
}
