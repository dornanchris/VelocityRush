//
//  ToastCenter.swift
//  StarshowerRun
//
//  Queue of little banners ("Achievement unlocked!", "+150 coins").
//

import SwiftUI

struct Toast: Identifiable, Equatable {
    let id = UUID()
    let icon: String
    let title: String
    let subtitle: String
    let tint: RGBColor
}

@MainActor
final class ToastCenter: ObservableObject {
    static let shared = ToastCenter()

    @Published private(set) var current: Toast?
    private var queue: [Toast] = []
    private var isShowing = false

    private init() {}

    func show(_ toast: Toast) {
        queue.append(toast)
        if !isShowing { advance() }
    }

    func announceAchievements(_ ids: [String]) {
        for id in ids {
            guard let definition = AchievementCatalog.find(id) else { continue }
            show(Toast(icon: definition.icon,
                       title: "Achievement unlocked",
                       subtitle: "\(definition.title)  ·  +\(definition.reward) coins",
                       tint: definition.tier.color))
        }
        if !ids.isEmpty {
            SoundManager.shared.play(.unlock)
            HapticsManager.shared.play(.success)
        }
    }

    private func advance() {
        guard !queue.isEmpty else {
            isShowing = false
            return
        }
        isShowing = true
        current = queue.removeFirst()
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 2_600_000_000)
            self?.current = nil
            try? await Task.sleep(nanoseconds: 400_000_000)
            self?.advance()
        }
    }
}
