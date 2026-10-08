//
//  SynthSamples.kt
//  Starshower Run
//
//  Every sound in the game is synthesised at launch – no audio assets
//  needed. This file only produces raw mono samples; the app's
//  `SoundManager` streams them through AudioTrack.
//

package com.starshower.run.core.audio

import com.starshower.run.core.engine.SeededRandom
import com.starshower.run.core.engine.clamp
import com.starshower.run.core.engine.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

enum class SoundEffect {
    STAR, CRYSTAL, NEAR_MISS, PERFECT, POWER_UP, SHIELD_BREAK, HIT, GAME_OVER,
    LEVEL_UP, MULTIPLIER, COUNTDOWN, GO, TAP, COIN, UNLOCK, NOVA, WARNING, TICK,
}

object SynthSamples {
    enum class Wave { SINE, TRIANGLE, SQUARE, SAW }

    // MARK: Building blocks

    fun oscillator(wave: Wave, phase: Double): Double {
        val p = phase - floor(phase)
        return when (wave) {
            Wave.SINE -> sin(2 * PI * p)
            Wave.TRIANGLE -> 1 - 4 * abs(p - 0.5)
            Wave.SQUARE -> if (p < 0.5) 1.0 else -1.0
            Wave.SAW -> 2 * p - 1
        }
    }

    /** A tone that glides from [from] to [to] Hz with a fast attack and exponential decay. */
    fun tone(
        wave: Wave, from: Double, to: Double? = null, duration: Double,
        volume: Double, attack: Double = 0.004, decay: Double = 6.0,
        sampleRate: Double,
    ): FloatArray {
        val count = maxOf(1, (duration * sampleRate).toInt())
        val end = to ?: from
        val samples = FloatArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i / sampleRate
            val progress = i.toDouble() / count
            val frequency = from * (end / from).pow(progress)
            phase += frequency / sampleRate
            val attackGain = minOf(1.0, t / maxOf(attack, 0.0001))
            val decayGain = exp(-decay * progress)
            val tail = minOf(1.0, (count - i) / (0.004 * sampleRate))
            samples[i] = (oscillator(wave, phase) * volume * attackGain * decayGain * tail).toFloat()
        }
        return samples
    }

    /** Filtered noise burst. [brightness] 0..1 controls the low-pass (1 = raw noise). */
    fun noise(
        duration: Double, volume: Double, decay: Double = 8.0,
        brightness: Double = 0.5, brightnessEnd: Double? = null,
        seed: Long = 1, sampleRate: Double,
    ): FloatArray {
        val count = maxOf(1, (duration * sampleRate).toInt())
        val rng = SeededRandom(seed)
        val samples = FloatArray(count)
        var filtered = 0.0
        for (i in 0 until count) {
            val progress = i.toDouble() / count
            val cutoff = lerp(brightness, brightnessEnd ?: brightness, progress)
            val white = rng.range(-1.0, 1.0)
            filtered += (white - filtered) * clamp(cutoff, 0.01, 1.0)
            val attack = minOf(1.0, i / (0.002 * sampleRate))
            samples[i] = (filtered * volume * exp(-decay * progress) * attack).toFloat()
        }
        return samples
    }

    fun silence(duration: Double, sampleRate: Double): FloatArray = FloatArray(maxOf(0, (duration * sampleRate).toInt()))

    /** Sums tracks, padding shorter ones. */
    fun mix(tracks: List<FloatArray>): FloatArray {
        val out = FloatArray(tracks.maxOfOrNull { it.size } ?: 0)
        for (track in tracks) {
            for (i in track.indices) out[i] += track[i]
        }
        return out
    }

    /** Plays each part after the previous one, optionally overlapping by [overlap] seconds. */
    fun sequence(parts: List<FloatArray>, overlap: Double = 0.0, sampleRate: Double): FloatArray {
        var out = FloatArray(0)
        val overlapCount = (overlap * sampleRate).toInt()
        for (part in parts) {
            val start = maxOf(0, out.size - overlapCount)
            val needed = start + part.size
            if (needed > out.size) out = out.copyOf(needed)
            for (i in part.indices) out[start + i] += part[i]
        }
        return out
    }

    fun arpeggio(
        wave: Wave, notes: List<Double>, noteLength: Double, volume: Double,
        tail: Double = 0.1, sampleRate: Double,
    ): FloatArray = sequence(
        notes.map { tone(wave, from = it, duration = noteLength + tail, volume = volume, decay = 5.0, sampleRate = sampleRate) },
        overlap = tail, sampleRate = sampleRate,
    )

    fun normalized(samples: FloatArray, peak: Float = 0.9f): FloatArray {
        val maxValue = samples.maxOfOrNull { abs(it) } ?: 0f
        if (maxValue <= peak) return samples
        val scale = peak / maxValue
        return FloatArray(samples.size) { samples[it] * scale }
    }

    // MARK: Effects

    fun samples(effect: SoundEffect, sampleRate: Double = 44_100.0): FloatArray {
        val sr = sampleRate
        val result: FloatArray = when (effect) {
            SoundEffect.STAR -> sequence(listOf(
                tone(Wave.SINE, from = 1320.0, duration = 0.05, volume = 0.32, decay = 3.0, sampleRate = sr),
                tone(Wave.SINE, from = 1760.0, duration = 0.09, volume = 0.32, decay = 5.0, sampleRate = sr),
            ), sampleRate = sr)
            SoundEffect.CRYSTAL -> arpeggio(Wave.TRIANGLE, listOf(880.0, 1175.0, 1568.0), noteLength = 0.05, volume = 0.3, sampleRate = sr)
            SoundEffect.NEAR_MISS -> mix(listOf(
                noise(duration = 0.2, volume = 0.35, decay = 5.0, brightness = 0.05, brightnessEnd = 0.6, seed = 3, sampleRate = sr),
                tone(Wave.SINE, from = 660.0, to = 990.0, duration = 0.14, volume = 0.1, sampleRate = sr),
            ))
            SoundEffect.PERFECT -> mix(listOf(
                noise(duration = 0.2, volume = 0.35, decay = 5.0, brightness = 0.05, brightnessEnd = 0.6, seed = 4, sampleRate = sr),
                sequence(listOf(
                    silence(0.05, sr),
                    tone(Wave.SINE, from = 1760.0, duration = 0.18, volume = 0.25, decay = 4.0, sampleRate = sr),
                ), sampleRate = sr),
            ))
            SoundEffect.POWER_UP -> mix(listOf(
                arpeggio(Wave.SQUARE, listOf(523.0, 659.0, 784.0, 1047.0), noteLength = 0.045, volume = 0.1, sampleRate = sr),
                arpeggio(Wave.SINE, listOf(523.0, 659.0, 784.0, 1047.0), noteLength = 0.045, volume = 0.2, sampleRate = sr),
            ))
            SoundEffect.SHIELD_BREAK -> mix(listOf(
                noise(duration = 0.3, volume = 0.4, decay = 7.0, brightness = 0.8, brightnessEnd = 0.1, seed = 5, sampleRate = sr),
                tone(Wave.TRIANGLE, from = 500.0, to = 150.0, duration = 0.25, volume = 0.25, sampleRate = sr),
            ))
            SoundEffect.HIT -> mix(listOf(
                noise(duration = 0.45, volume = 0.55, decay = 6.0, brightness = 0.4, brightnessEnd = 0.02, seed = 6, sampleRate = sr),
                tone(Wave.SAW, from = 220.0, to = 50.0, duration = 0.4, volume = 0.25, decay = 4.0, sampleRate = sr),
            ))
            SoundEffect.GAME_OVER -> arpeggio(Wave.TRIANGLE, listOf(440.0, 349.0, 294.0, 220.0), noteLength = 0.13, volume = 0.3, tail = 0.25, sampleRate = sr)
            SoundEffect.LEVEL_UP -> arpeggio(Wave.SINE, listOf(523.0, 784.0, 1047.0, 1568.0), noteLength = 0.07, volume = 0.28, tail = 0.3, sampleRate = sr)
            SoundEffect.MULTIPLIER -> mix(listOf(
                tone(Wave.SQUARE, from = 880.0, to = 1320.0, duration = 0.12, volume = 0.08, decay = 3.0, sampleRate = sr),
                tone(Wave.SINE, from = 880.0, to = 1320.0, duration = 0.14, volume = 0.2, decay = 3.0, sampleRate = sr),
            ))
            SoundEffect.COUNTDOWN -> tone(Wave.SINE, from = 660.0, duration = 0.14, volume = 0.35, decay = 4.0, sampleRate = sr)
            SoundEffect.GO -> mix(listOf(
                tone(Wave.SINE, from = 1320.0, duration = 0.35, volume = 0.3, decay = 3.0, sampleRate = sr),
                tone(Wave.TRIANGLE, from = 660.0, duration = 0.35, volume = 0.2, decay = 3.0, sampleRate = sr),
            ))
            SoundEffect.TAP -> tone(Wave.SINE, from = 1100.0, to = 900.0, duration = 0.035, volume = 0.2, decay = 6.0, sampleRate = sr)
            SoundEffect.COIN -> sequence(listOf(
                tone(Wave.SINE, from = 1568.0, duration = 0.06, volume = 0.28, decay = 2.0, sampleRate = sr),
                tone(Wave.SINE, from = 2093.0, duration = 0.18, volume = 0.28, decay = 5.0, sampleRate = sr),
            ), sampleRate = sr)
            SoundEffect.UNLOCK -> mix(listOf(
                arpeggio(Wave.TRIANGLE, listOf(784.0, 988.0, 1175.0, 1568.0), noteLength = 0.08, volume = 0.25, tail = 0.35, sampleRate = sr),
                arpeggio(Wave.SINE, listOf(392.0, 494.0, 587.0, 784.0), noteLength = 0.08, volume = 0.15, tail = 0.35, sampleRate = sr),
            ))
            SoundEffect.NOVA -> mix(listOf(
                noise(duration = 0.7, volume = 0.5, decay = 5.0, brightness = 0.6, brightnessEnd = 0.02, seed = 7, sampleRate = sr),
                tone(Wave.SINE, from = 140.0, to = 40.0, duration = 0.6, volume = 0.55, decay = 3.0, sampleRate = sr),
            ))
            SoundEffect.WARNING -> sequence(listOf(
                tone(Wave.SQUARE, from = 1250.0, duration = 0.04, volume = 0.07, decay = 1.0, sampleRate = sr),
                silence(0.04, sr),
                tone(Wave.SQUARE, from = 1250.0, duration = 0.04, volume = 0.07, decay = 1.0, sampleRate = sr),
            ), sampleRate = sr)
            SoundEffect.TICK -> tone(Wave.SINE, from = 1800.0, duration = 0.04, volume = 0.22, decay = 5.0, sampleRate = sr)
        }
        return normalized(result)
    }

    // MARK: Music

    /** An 8 second, seamlessly looping synthwave groove (120 bpm, Am–F–C–G). */
    fun musicLoop(sampleRate: Double = 44_100.0): FloatArray {
        val sr = sampleRate
        val beat = 0.5
        val barLength = beat * 4
        val total = (barLength * 4 * sr).toInt()
        val out = FloatArray(total)

        fun add(samples: FloatArray, time: Double, gain: Float = 1f) {
            val start = (time * sr).toInt()
            for (i in samples.indices) {
                // Wrap around so tails continue at the start of the loop.
                out[(start + i) % total] += samples[i] * gain
            }
        }

        val chords = listOf(
            listOf(220.00, 261.63, 329.63),   // Am
            listOf(174.61, 220.00, 261.63),   // F
            listOf(196.00, 261.63, 329.63),   // C (inversion)
            listOf(196.00, 246.94, 293.66),   // G
        )
        val roots = listOf(110.00, 87.31, 130.81, 98.00)

        for ((bar, chord) in chords.withIndex()) {
            val barStart = bar * barLength

            // Pad
            for (note in chord) {
                for (detune in listOf(0.997, 1.003)) {
                    add(padTone(note * detune, barLength, 0.05, sr), barStart)
                }
            }

            for (step in 0 until 4) {
                val t = barStart + step * beat
                // Kick on every beat
                add(tone(Wave.SINE, from = 130.0, to = 42.0, duration = 0.18, volume = 0.5, attack = 0.001, decay = 5.0, sampleRate = sr), t)
                // Bass on the off-beat eighth
                add(tone(Wave.TRIANGLE, from = roots[bar], duration = 0.22, volume = 0.28, attack = 0.005, decay = 3.0, sampleRate = sr), t + beat / 2)
                // Hi-hat
                add(noise(duration = 0.035, volume = 0.06, decay = 10.0, brightness = 1.0, seed = (bar * 4 + step + 11).toLong(), sampleRate = sr), t + beat / 2)
            }

            // Sixteenth-note arpeggio
            val arpNotes = chord.map { it * 2 } + chord[1] * 4
            for (sixteenth in 0 until 16) {
                val note = arpNotes[sixteenth % arpNotes.size]
                add(tone(Wave.TRIANGLE, from = note, duration = 0.11, volume = 0.045, attack = 0.002, decay = 5.0, sampleRate = sr),
                    barStart + sixteenth * beat / 4)
            }
        }
        return normalized(out, peak = 0.8f)
    }

    private fun padTone(frequency: Double, duration: Double, volume: Double, sampleRate: Double): FloatArray {
        val count = (duration * sampleRate).toInt()
        val samples = FloatArray(count)
        var phase = 0.0
        val fade = 0.25 * sampleRate
        for (i in 0 until count) {
            phase += frequency / sampleRate
            val fadeIn = minOf(1.0, i / fade)
            val fadeOut = minOf(1.0, (count - i) / fade)
            val value = oscillator(Wave.TRIANGLE, phase) * 0.7 + oscillator(Wave.SINE, phase * 2) * 0.3
            samples[i] = (value * volume * fadeIn * fadeOut).toFloat()
        }
        return samples
    }
}
