//
//  HapticsManager.swift
//  StarshowerRun
//

import UIKit

enum Haptic {
    case light, medium, heavy, rigid, soft
    case success, warning, error
    case selection
}

@MainActor
final class HapticsManager {
    static let shared = HapticsManager()

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private let soft = UIImpactFeedbackGenerator(style: .soft)
    private let notification = UINotificationFeedbackGenerator()
    private let selection = UISelectionFeedbackGenerator()

    private init() {}

    var isEnabled: Bool {
        UserDefaults.standard.object(forKey: SettingsKey.hapticsEnabled) as? Bool ?? true
    }

    func prepare() {
        guard isEnabled else { return }
        light.prepare()
        medium.prepare()
        heavy.prepare()
        notification.prepare()
    }

    func play(_ haptic: Haptic) {
        guard isEnabled else { return }
        switch haptic {
        case .light: light.impactOccurred()
        case .medium: medium.impactOccurred()
        case .heavy: heavy.impactOccurred()
        case .rigid: rigid.impactOccurred()
        case .soft: soft.impactOccurred()
        case .success: notification.notificationOccurred(.success)
        case .warning: notification.notificationOccurred(.warning)
        case .error: notification.notificationOccurred(.error)
        case .selection: selection.selectionChanged()
        }
    }
}
