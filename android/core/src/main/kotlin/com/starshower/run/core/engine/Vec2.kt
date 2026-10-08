//
//  Vec2.kt
//  Starshower Run
//
//  Lightweight 2D vector used by the simulation. The engine is deliberately
//  free of Android/Compose types so it can be unit tested on the JVM and
//  stays deterministic.
//

package com.starshower.run.core.engine

import kotlin.math.sqrt

data class Vec2(val x: Double, val y: Double) {

    val length: Double get() = sqrt(x * x + y * y)

    val normalized: Vec2
        get() {
            val len = length
            return if (len > 0.000_001) Vec2(x / len, y / len) else ZERO
        }

    fun distanceTo(other: Vec2): Double = (this - other).length

    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Double) = Vec2(x * scalar, y * scalar)

    companion object {
        val ZERO = Vec2(0.0, 0.0)
    }
}

fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

fun <T : Comparable<T>> clamp(value: T, lower: T, upper: T): T = minOf(maxOf(value, lower), upper)
