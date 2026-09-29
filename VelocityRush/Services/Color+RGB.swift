//
//  Color+RGB.swift
//  VelocityRush
//

import SwiftUI
import UIKit

extension RGBColor {
    var uiColor: UIColor {
        UIColor(red: CGFloat(red), green: CGFloat(green), blue: CGFloat(blue), alpha: CGFloat(alpha))
    }

    var color: Color {
        Color(red: red, green: green, blue: blue, opacity: alpha)
    }
}

extension Vec2 {
    var cgPoint: CGPoint { CGPoint(x: x, y: y) }
}
