//
//  SeededRandom.kt
//  Starshower Run
//
//  Deterministic random number generation. Daily Runs use a seed derived
//  from the calendar date so every player faces the exact same run.
//  Bit-for-bit identical to the iOS implementation, so a Daily Run seed
//  produces the same hazards on both platforms.
//

package com.starshower.run.core.engine

/** SplitMix64 – tiny, fast and good enough for gameplay randomness. */
class SeededRandom(seed: Long) {
    private var state: Long = seed

    /** Next 64 random bits (treat as unsigned). */
    fun next(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    /** Uniform value in `0 until 1`. */
    fun unit(): Double = (next() ushr 11).toDouble() * (1.0 / 9_007_199_254_740_992.0)

    fun range(lower: Double, upper: Double): Double = lower + (upper - lower) * unit()

    /** Uniform integer in `range` (inclusive). */
    fun int(range: IntRange): Int {
        val span = (range.last.toLong() - range.first.toLong() + 1).toULong()
        return range.first + (next().toULong() % span).toInt()
    }

    fun chance(probability: Double): Boolean = unit() < probability

    /** Picks an element using relative weights. Returns null if all weights are zero. */
    fun <T> weighted(options: List<Pair<T, Double>>): T? {
        val total = options.sumOf { maxOf(0.0, it.second) }
        if (total <= 0) return null
        var roll = unit() * total
        for ((value, weight) in options) {
            if (weight <= 0) continue
            roll -= weight
            if (roll < 0) return value
        }
        return options.lastOrNull { it.second > 0 }?.first
    }

    /** Derives an independent generator (e.g. one stream for hazards, one for pickups). */
    fun fork(salt: Long): SeededRandom = SeededRandom(next() xor salt)

    private companion object {
        val GOLDEN_GAMMA = 0x9E37_79B9_7F4A_7C15uL.toLong()
        val MIX_1 = 0xBF58_476D_1CE4_E5B9uL.toLong()
        val MIX_2 = 0x94D0_49BB_1331_11EBuL.toLong()
    }
}

object StableHash {
    /** FNV-1a over UTF-8 bytes. Stable across launches, devices and platforms. */
    fun fnv1a(string: String): Long {
        var hash = 0xCBF2_9CE4_8422_2325uL.toLong()
        for (byte in string.encodeToByteArray()) {
            hash = hash xor (byte.toLong() and 0xFF)
            hash *= 0x0000_0100_0000_01B3L
        }
        return hash
    }
}
