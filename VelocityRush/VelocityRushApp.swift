//
//  VelocityRushApp.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//

import SwiftUI

@main
struct VelocityRushApp: App {
    @StateObject private var store = ProgressStore.shared
    @StateObject private var toasts = ToastCenter.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(toasts)
                .preferredColorScheme(.dark)
                .onAppear {
                    SoundManager.shared.prepare()
                    HapticsManager.shared.prepare()
                    GameCenterManager.shared.authenticate()
                }
        }
    }
}
