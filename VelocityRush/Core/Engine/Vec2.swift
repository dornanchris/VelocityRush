//
//  Vec2.swift
//  VelocityRush
//
//  Lightweight 2D vector used by the simulation. The engine is deliberately
//  free of SpriteKit/UIKit so it can be unit tested and stays deterministic.
//

import Foundation

struct Vec2: Equatable, Codable {
    var x: Double
    var y: Double

    static let zero = Vec2(x: 0, y: 0)

    init(x: Double, y: Double) {
        self.x = x
        self.y = y
    }

    init(_ x: Double, _ y: Double) {
        self.x = x
        self.y = y
    }

    var length: Double { (x * x + y * y).squareRoot() }

    var normalized: Vec2 {
        let len = length
        return len > 0.000_001 ? Vec2(x / len, y / len) : .zero
    }

    func distance(to other: Vec2) -> Double { (self - other).length }

    static func + (lhs: Vec2, rhs: Vec2) -> Vec2 { Vec2(lhs.x + rhs.x, lhs.y + rhs.y) }
    static func - (lhs: Vec2, rhs: Vec2) -> Vec2 { Vec2(lhs.x - rhs.x, lhs.y - rhs.y) }
    static func * (lhs: Vec2, rhs: Double) -> Vec2 { Vec2(lhs.x * rhs, lhs.y * rhs) }
    static func += (lhs: inout Vec2, rhs: Vec2) { lhs = lhs + rhs }
    static func -= (lhs: inout Vec2, rhs: Vec2) { lhs = lhs - rhs }
}

@inline(__always)
func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

@inline(__always)
func clamp<T: Comparable>(_ value: T, _ lower: T, _ upper: T) -> T { min(max(value, lower), upper) }
