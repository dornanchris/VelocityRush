//
//  RGBColor.kt
//  Starshower Run
//
//  Platform-neutral colour so game data (themes, skins, modes) can live in
//  the pure Kotlin core. UI layers convert it to Compose Color.
//

package com.starshower.run.core.engine

import kotlin.math.abs
import kotlin.math.floor

data class RGBColor(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double = 1.0,
) {
    fun withAlpha(alpha: Double) = copy(alpha = alpha)

    /** Mixes towards [other] by [amount] (0..1). */
    fun mixed(other: RGBColor, amount: Double): RGBColor {
        val t = clamp(amount, 0.0, 1.0)
        return RGBColor(
            red = lerp(red, other.red, t),
            green = lerp(green, other.green, t),
            blue = lerp(blue, other.blue, t),
            alpha = lerp(alpha, other.alpha, t),
        )
    }

    fun lighter(amount: Double = 0.3) = mixed(WHITE, amount)
    fun darker(amount: Double = 0.3) = mixed(BLACK, amount)

    /** Packed 0xAARRGGBB, handy for android.graphics and Compose. */
    val argb: Int
        get() {
            fun channel(v: Double) = (clamp(v, 0.0, 1.0) * 255 + 0.5).toInt()
            return (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
        }

    companion object {
        val WHITE = RGBColor(1.0, 1.0, 1.0)
        val BLACK = RGBColor(0.0, 0.0, 0.0)

        fun hex(hex: Long, alpha: Double = 1.0) = RGBColor(
            red = ((hex shr 16) and 0xFF) / 255.0,
            green = ((hex shr 8) and 0xFF) / 255.0,
            blue = (hex and 0xFF) / 255.0,
            alpha = alpha,
        )

        /** Builds a colour from hue/saturation/brightness (all 0..1). */
        fun hsb(hue: Double, saturation: Double, brightness: Double): RGBColor {
            val h = (hue - floor(hue)) * 6
            val c = brightness * saturation
            val x = c * (1 - abs(h % 2 - 1))
            val m = brightness - c
            val (r, g, b) = when (h.toInt()) {
                0 -> Triple(c, x, 0.0)
                1 -> Triple(x, c, 0.0)
                2 -> Triple(0.0, c, x)
                3 -> Triple(0.0, x, c)
                4 -> Triple(x, 0.0, c)
                else -> Triple(c, 0.0, x)
            }
            return RGBColor(r + m, g + m, b + m)
        }
    }
}
