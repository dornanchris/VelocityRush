//
//  SeededRandom.swift
//  VelocityRush
//
//  Deterministic random number generation. Daily Runs use a seed derived
//  from the calendar date so every player faces the exact same run.
//

import Foundation

/// SplitMix64 – tiny, fast and good enough for gameplay randomness.
struct SeededRandom: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// Uniform value in `0..<1`.
    mutating func unit() -> Double {
        Double(next() >> 11) * (1.0 / 9_007_199_254_740_992.0)
    }

    mutating func range(_ lower: Double, _ upper: Double) -> Double {
        lower + (upper - lower) * unit()
    }

    mutating func int(_ range: ClosedRange<Int>) -> Int {
        let span = UInt64(range.upperBound - range.lowerBound + 1)
        return range.lowerBound + Int(next() % span)
    }

    mutating func chance(_ probability: Double) -> Bool {
        unit() < probability
    }

    /// Picks an element using relative weights. Returns nil if all weights are zero.
    mutating func weighted<T>(_ options: [(T, Double)]) -> T? {
        let total = options.reduce(0) { $0 + max(0, $1.1) }
        guard total > 0 else { return nil }
        var roll = unit() * total
        for (value, weight) in options where weight > 0 {
            roll -= weight
            if roll < 0 { return value }
        }
        return options.last(where: { $0.1 > 0 })?.0
    }

    /// Derives an independent generator (e.g. one stream for hazards, one for pickups).
    mutating func fork(salt: UInt64) -> SeededRandom {
        SeededRandom(seed: next() ^ salt)
    }
}

enum StableHash {
    /// FNV-1a. Unlike `Hasher`, this is stable across launches and devices.
    static func fnv1a(_ string: String) -> UInt64 {
        var hash: UInt64 = 0xCBF2_9CE4_8422_2325
        for byte in string.utf8 {
            hash ^= UInt64(byte)
            hash = hash &* 0x0000_0100_0000_01B3
        }
        return hash
    }
}
