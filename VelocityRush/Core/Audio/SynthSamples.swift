//
//  SynthSamples.swift
//  VelocityRush
//
//  Every sound in the game is synthesised at launch – no audio assets
//  needed. This file only produces raw mono samples; `SoundManager` turns
//  them into AVAudio buffers.
//

import Foundation

enum SoundEffect: String, CaseIterable {
    case star, crystal, nearMiss, perfect, powerUp, shieldBreak, hit, gameOver
    case levelUp, multiplier, countdown, go, tap, coin, unlock, nova, warning, tick
}

enum SynthSamples {
    enum Wave {
        case sine, triangle, square, saw
    }

    // MARK: Building blocks

    static func oscillator(_ wave: Wave, phase: Double) -> Double {
        let p = phase - floor(phase)
        switch wave {
        case .sine: return sin(2 * .pi * p)
        case .triangle: return 1 - 4 * abs(p - 0.5)
        case .square: return p < 0.5 ? 1 : -1
        case .saw: return 2 * p - 1
        }
    }

    /// A tone that glides from `from` to `to` Hz with a fast attack and exponential decay.
    static func tone(_ wave: Wave, from: Double, to: Double? = nil, duration: Double,
                     volume: Double, attack: Double = 0.004, decay: Double = 6,
                     sampleRate: Double) -> [Float] {
        let count = max(1, Int(duration * sampleRate))
        let end = to ?? from
        var samples = [Float](repeating: 0, count: count)
        var phase = 0.0
        for i in 0..<count {
            let t = Double(i) / sampleRate
            let progress = Double(i) / Double(count)
            let frequency = from * pow(end / from, progress)
            phase += frequency / sampleRate
            let attackGain = min(1, t / max(attack, 0.0001))
            let decayGain = exp(-decay * progress)
            let tail = min(1, Double(count - i) / (0.004 * sampleRate))
            samples[i] = Float(oscillator(wave, phase: phase) * volume * attackGain * decayGain * tail)
        }
        return samples
    }

    /// Filtered noise burst. `brightness` 0...1 controls the low-pass (1 = raw noise).
    static func noise(duration: Double, volume: Double, decay: Double = 8,
                      brightness: Double = 0.5, brightnessEnd: Double? = nil,
                      seed: UInt64 = 1, sampleRate: Double) -> [Float] {
        let count = max(1, Int(duration * sampleRate))
        var rng = SeededRandom(seed: seed)
        var samples = [Float](repeating: 0, count: count)
        var filtered = 0.0
        for i in 0..<count {
            let progress = Double(i) / Double(count)
            let cutoff = lerp(brightness, brightnessEnd ?? brightness, progress)
            let white = rng.range(-1, 1)
            filtered += (white - filtered) * clamp(cutoff, 0.01, 1)
            let attack = min(1, Double(i) / (0.002 * sampleRate))
            samples[i] = Float(filtered * volume * exp(-decay * progress) * attack)
        }
        return samples
    }

    static func silence(_ duration: Double, sampleRate: Double) -> [Float] {
        [Float](repeating: 0, count: max(0, Int(duration * sampleRate)))
    }

    /// Sums tracks, padding shorter ones.
    static func mix(_ tracks: [[Float]]) -> [Float] {
        let length = tracks.map(\.count).max() ?? 0
        var out = [Float](repeating: 0, count: length)
        for track in tracks {
            for (i, sample) in track.enumerated() { out[i] += sample }
        }
        return out
    }

    /// Plays each part after the previous one, optionally overlapping by `overlap` seconds.
    static func sequence(_ parts: [[Float]], overlap: Double = 0, sampleRate: Double) -> [Float] {
        var out: [Float] = []
        let overlapCount = Int(overlap * sampleRate)
        for part in parts {
            let start = max(0, out.count - overlapCount)
            let needed = start + part.count
            if needed > out.count { out.append(contentsOf: [Float](repeating: 0, count: needed - out.count)) }
            for (i, sample) in part.enumerated() { out[start + i] += sample }
        }
        return out
    }

    static func arpeggio(_ wave: Wave, notes: [Double], noteLength: Double, volume: Double,
                         tail: Double = 0.1, sampleRate: Double) -> [Float] {
        sequence(notes.map { tone(wave, from: $0, duration: noteLength + tail, volume: volume, decay: 5, sampleRate: sampleRate) },
                 overlap: tail, sampleRate: sampleRate)
    }

    static func normalized(_ samples: [Float], peak: Float = 0.9) -> [Float] {
        let maxValue = samples.map { abs($0) }.max() ?? 0
        guard maxValue > peak else { return samples }
        let scale = peak / maxValue
        return samples.map { $0 * scale }
    }

    // MARK: Effects

    static func samples(for effect: SoundEffect, sampleRate: Double = 44_100) -> [Float] {
        let sr = sampleRate
        let result: [Float]
        switch effect {
        case .star:
            result = sequence([
                tone(.sine, from: 1320, duration: 0.05, volume: 0.32, decay: 3, sampleRate: sr),
                tone(.sine, from: 1760, duration: 0.09, volume: 0.32, decay: 5, sampleRate: sr)
            ], sampleRate: sr)
        case .crystal:
            result = arpeggio(.triangle, notes: [880, 1175, 1568], noteLength: 0.05, volume: 0.3, sampleRate: sr)
        case .nearMiss:
            result = mix([
                noise(duration: 0.2, volume: 0.35, decay: 5, brightness: 0.05, brightnessEnd: 0.6, seed: 3, sampleRate: sr),
                tone(.sine, from: 660, to: 990, duration: 0.14, volume: 0.1, sampleRate: sr)
            ])
        case .perfect:
            result = mix([
                noise(duration: 0.2, volume: 0.35, decay: 5, brightness: 0.05, brightnessEnd: 0.6, seed: 4, sampleRate: sr),
                sequence([silence(0.05, sampleRate: sr),
                          tone(.sine, from: 1760, duration: 0.18, volume: 0.25, decay: 4, sampleRate: sr)], sampleRate: sr)
            ])
        case .powerUp:
            result = mix([
                arpeggio(.square, notes: [523, 659, 784, 1047], noteLength: 0.045, volume: 0.1, sampleRate: sr),
                arpeggio(.sine, notes: [523, 659, 784, 1047], noteLength: 0.045, volume: 0.2, sampleRate: sr)
            ])
        case .shieldBreak:
            result = mix([
                noise(duration: 0.3, volume: 0.4, decay: 7, brightness: 0.8, brightnessEnd: 0.1, seed: 5, sampleRate: sr),
                tone(.triangle, from: 500, to: 150, duration: 0.25, volume: 0.25, sampleRate: sr)
            ])
        case .hit:
            result = mix([
                noise(duration: 0.45, volume: 0.55, decay: 6, brightness: 0.4, brightnessEnd: 0.02, seed: 6, sampleRate: sr),
                tone(.saw, from: 220, to: 50, duration: 0.4, volume: 0.25, decay: 4, sampleRate: sr)
            ])
        case .gameOver:
            result = arpeggio(.triangle, notes: [440, 349, 294, 220], noteLength: 0.13, volume: 0.3, tail: 0.25, sampleRate: sr)
        case .levelUp:
            result = arpeggio(.sine, notes: [523, 784, 1047, 1568], noteLength: 0.07, volume: 0.28, tail: 0.3, sampleRate: sr)
        case .multiplier:
            result = mix([
                tone(.square, from: 880, to: 1320, duration: 0.12, volume: 0.08, decay: 3, sampleRate: sr),
                tone(.sine, from: 880, to: 1320, duration: 0.14, volume: 0.2, decay: 3, sampleRate: sr)
            ])
        case .countdown:
            result = tone(.sine, from: 660, duration: 0.14, volume: 0.35, decay: 4, sampleRate: sr)
        case .go:
            result = mix([
                tone(.sine, from: 1320, duration: 0.35, volume: 0.3, decay: 3, sampleRate: sr),
                tone(.triangle, from: 660, duration: 0.35, volume: 0.2, decay: 3, sampleRate: sr)
            ])
        case .tap:
            result = tone(.sine, from: 1100, to: 900, duration: 0.035, volume: 0.2, decay: 6, sampleRate: sr)
        case .coin:
            result = sequence([
                tone(.sine, from: 1568, duration: 0.06, volume: 0.28, decay: 2, sampleRate: sr),
                tone(.sine, from: 2093, duration: 0.18, volume: 0.28, decay: 5, sampleRate: sr)
            ], sampleRate: sr)
        case .unlock:
            result = mix([
                arpeggio(.triangle, notes: [784, 988, 1175, 1568], noteLength: 0.08, volume: 0.25, tail: 0.35, sampleRate: sr),
                arpeggio(.sine, notes: [392, 494, 587, 784], noteLength: 0.08, volume: 0.15, tail: 0.35, sampleRate: sr)
            ])
        case .nova:
            result = mix([
                noise(duration: 0.7, volume: 0.5, decay: 5, brightness: 0.6, brightnessEnd: 0.02, seed: 7, sampleRate: sr),
                tone(.sine, from: 140, to: 40, duration: 0.6, volume: 0.55, decay: 3, sampleRate: sr)
            ])
        case .warning:
            result = sequence([
                tone(.square, from: 1250, duration: 0.04, volume: 0.07, decay: 1, sampleRate: sr),
                silence(0.04, sampleRate: sr),
                tone(.square, from: 1250, duration: 0.04, volume: 0.07, decay: 1, sampleRate: sr)
            ], sampleRate: sr)
        case .tick:
            result = tone(.sine, from: 1800, duration: 0.04, volume: 0.22, decay: 5, sampleRate: sr)
        }
        return normalized(result)
    }

    // MARK: Music

    /// An 8 second, seamlessly looping synthwave groove (120 bpm, Am–F–C–G).
    static func musicLoop(sampleRate: Double = 44_100) -> [Float] {
        let sr = sampleRate
        let beat = 0.5
        let barLength = beat * 4
        let total = Int(barLength * 4 * sr)
        var out = [Float](repeating: 0, count: total)

        func add(_ samples: [Float], at time: Double, gain: Float = 1) {
            let start = Int(time * sr)
            for (i, sample) in samples.enumerated() {
                // Wrap around so tails continue at the start of the loop.
                out[(start + i) % total] += sample * gain
            }
        }

        let chords: [[Double]] = [
            [220.00, 261.63, 329.63],   // Am
            [174.61, 220.00, 261.63],   // F
            [196.00, 261.63, 329.63],   // C (inversion)
            [196.00, 246.94, 293.66]    // G
        ]
        let roots: [Double] = [110.00, 87.31, 130.81, 98.00]

        for (bar, chord) in chords.enumerated() {
            let barStart = Double(bar) * barLength

            // Pad
            for note in chord {
                for detune in [0.997, 1.003] {
                    let pad = padTone(frequency: note * detune, duration: barLength, volume: 0.05, sampleRate: sr)
                    add(pad, at: barStart)
                }
            }

            for step in 0..<4 {
                let t = barStart + Double(step) * beat
                // Kick on every beat
                add(tone(.sine, from: 130, to: 42, duration: 0.18, volume: 0.5, attack: 0.001, decay: 5, sampleRate: sr), at: t)
                // Bass on the off-beat eighth
                add(tone(.triangle, from: roots[bar], duration: 0.22, volume: 0.28, attack: 0.005, decay: 3, sampleRate: sr), at: t + beat / 2)
                // Hi-hat
                add(noise(duration: 0.035, volume: 0.06, decay: 10, brightness: 1.0, seed: UInt64(bar * 4 + step + 11), sampleRate: sr),
                    at: t + beat / 2)
            }

            // Sixteenth-note arpeggio
            let arpNotes = chord.map { $0 * 2 } + [chord[1] * 4]
            for sixteenth in 0..<16 {
                let note = arpNotes[sixteenth % arpNotes.count]
                add(tone(.triangle, from: note, duration: 0.11, volume: 0.045, attack: 0.002, decay: 5, sampleRate: sr),
                    at: barStart + Double(sixteenth) * beat / 4)
            }
        }
        return normalized(out, peak: 0.8)
    }

    private static func padTone(frequency: Double, duration: Double, volume: Double, sampleRate: Double) -> [Float] {
        let count = Int(duration * sampleRate)
        var samples = [Float](repeating: 0, count: count)
        var phase = 0.0
        let fade = 0.25 * sampleRate
        for i in 0..<count {
            phase += frequency / sampleRate
            let fadeIn = min(1, Double(i) / fade)
            let fadeOut = min(1, Double(count - i) / fade)
            let value = oscillator(.triangle, phase: phase) * 0.7 + oscillator(.sine, phase: phase * 2) * 0.3
            samples[i] = Float(value * volume * fadeIn * fadeOut)
        }
        return samples
    }
}
