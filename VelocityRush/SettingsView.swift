//
//  SettingsView.swift
//  VelocityRush
//
//  Complete Settings Page Concept
//

import SwiftUI

struct SettingsView: View {
    @AppStorage("soundEnabled") private var soundEnabled = true
    @AppStorage("musicEnabled") private var musicEnabled = true
    @AppStorage("hapticEnabled") private var hapticEnabled = true
    @AppStorage("showFPS") private var showFPS = false
    @AppStorage("difficulty") private var difficultyLevel = 1
    @AppStorage("colorBlindMode") private var colorBlindMode = false
    @AppStorage("highContrast") private var highContrast = false
    @AppStorage("reducedMotion") private var reducedMotion = false
    @AppStorage("notifications") private var notificationsEnabled = true
    
    @State private var showingResetAlert = false
    @State private var showingAbout = false
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            VStack {
                Text("Settings")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .padding(.top, 20)
                
                Text("Customize your experience")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding(.bottom, 20)
            }
            
            ScrollView {
                VStack(spacing: 24) {
                    // Audio Settings
                    SettingsSection(title: "Audio", icon: "speaker.wave.2.fill") {
                        SettingsToggle(
                            title: "Sound Effects",
                            subtitle: "Game sounds and feedback",
                            isOn: $soundEnabled,
                            icon: "speaker.2.fill"
                        )
                        
                        SettingsToggle(
                            title: "Background Music",
                            subtitle: "Menu and game music",
                            isOn: $musicEnabled,
                            icon: "music.note"
                        )
                        
                        SettingsToggle(
                            title: "Haptic Feedback",
                            subtitle: "Touch vibration responses",
                            isOn: $hapticEnabled,
                            icon: "iphone.radiowaves.left.and.right"
                        )
                    }
                    
                    // Gameplay Settings
                    SettingsSection(title: "Gameplay", icon: "gamecontroller.fill") {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Image(systemName: "speedometer")
                                    .foregroundColor(.purple)
                                    .frame(width: 24)
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Difficulty")
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                    Text("How challenging the game starts")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                
                                Spacer()
                            }
                            
                            Picker("Difficulty", selection: $difficultyLevel) {
                                Text("Easy").tag(0)
                                Text("Normal").tag(1)
                                Text("Hard").tag(2)
                                Text("Expert").tag(3)
                            }
                            .pickerStyle(SegmentedPickerStyle())
                        }
                        .padding(.vertical, 4)
                        
                        SettingsToggle(
                            title: "Show FPS Counter",
                            subtitle: "Display frame rate (for debugging)",
                            isOn: $showFPS,
                            icon: "gauge"
                        )
                    }
                    
                    // Accessibility Settings
                    SettingsSection(title: "Accessibility", icon: "accessibility") {
                        SettingsToggle(
                            title: "Color Blind Support",
                            subtitle: "Enhanced color contrast for dots",
                            isOn: $colorBlindMode,
                            icon: "eyeglasses"
                        )
                        
                        SettingsToggle(
                            title: "High Contrast Mode",
                            subtitle: "Stronger visual distinction",
                            isOn: $highContrast,
                            icon: "circle.lefthalf.filled"
                        )
                        
                        SettingsToggle(
                            title: "Reduce Motion",
                            subtitle: "Minimize animations and effects",
                            isOn: $reducedMotion,
                            icon: "slowmo"
                        )
                    }
                    
                    // Notifications Settings
                    SettingsSection(title: "Notifications", icon: "bell.fill") {
                        SettingsToggle(
                            title: "Fuel Notifications",
                            subtitle: "Alert when fuel is refilled",
                            isOn: $notificationsEnabled,
                            icon: "bell"
                        )
                    }
                    
                    // Data & Privacy
                    SettingsSection(title: "Data & Privacy", icon: "lock.shield") {
                        SettingsButton(
                            title: "Reset All Statistics",
                            subtitle: "Clear all game progress and stats",
                            icon: "trash",
                            color: .red
                        ) {
                            showingResetAlert = true
                        }
                        
                        SettingsButton(
                            title: "Export Game Data",
                            subtitle: "Save your stats to Files app",
                            icon: "square.and.arrow.up",
                            color: .blue
                        ) {
                            exportGameData()
                        }
                    }
                    
                    // Support & Info
                    SettingsSection(title: "Support & Info", icon: "questionmark.circle") {
                        SettingsButton(
                            title: "About Velocity Rush",
                            subtitle: "Version info and credits",
                            icon: "info.circle",
                            color: .gray
                        ) {
                            showingAbout = true
                        }
                        
                        SettingsButton(
                            title: "Rate This Game",
                            subtitle: "Leave a review on the App Store",
                            icon: "star",
                            color: .orange
                        ) {
                            rateApp()
                        }
                        
                        SettingsButton(
                            title: "Contact Support",
                            subtitle: "Get help or report issues",
                            icon: "envelope",
                            color: .green
                        ) {
                            contactSupport()
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
                .padding(.bottom, 30)
            }
        }
        .background(Color(.systemBackground))
        .alert("Reset All Data?", isPresented: $showingResetAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Reset", role: .destructive) {
                resetAllData()
            }
        } message: {
            Text("This will permanently delete all your game statistics, achievements, and progress. This cannot be undone.")
        }
        .sheet(isPresented: $showingAbout) {
            AboutView()
        }
    }
    
    private func resetAllData() {
        // Reset UserDefaults
        let domain = Bundle.main.bundleIdentifier!
        UserDefaults.standard.removePersistentDomain(forName: domain)
        UserDefaults.standard.synchronize()
        
        // Reset managers
        GameDataManager.shared.stats = GameStats()
        GameDataManager.shared.achievements = []
        GameDataManager.shared.initializeAchievements()
        FuelManager.shared.currentFuel = 5
    }
    
    private func exportGameData() {
        // Implementation for exporting game data as JSON
    }
    
    private func rateApp() {
        // Implementation for App Store rating
    }
    
    private func contactSupport() {
        // Implementation for email support
    }
}

struct SettingsSection<Content: View>: View {
    let title: String
    let icon: String
    @ViewBuilder let content: Content
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(.blue)
                    .font(.headline)
                
                Text(title)
                    .font(.headline)
                    .fontWeight(.semibold)
                
                Spacer()
            }
            
            VStack(spacing: 12) {
                content
            }
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(12)
        }
    }
}

struct SettingsToggle: View {
    let title: String
    let subtitle: String
    @Binding var isOn: Bool
    let icon: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.blue)
                .frame(width: 24)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.medium)
                
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Toggle("", isOn: $isOn)
                .labelsHidden()
        }
        .padding(.vertical, 4)
    }
}

struct SettingsButton: View {
    let title: String
    let subtitle: String
    let icon: String
    let color: Color
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                    .frame(width: 24)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.primary)
                    
                    Text(subtitle)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
                    .font(.caption)
            }
            .padding(.vertical, 4)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct AboutView: View {
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // App Icon and Info
                    VStack(spacing: 12) {
                        Image(systemName: "gamecontroller.fill")
                            .font(.system(size: 80))
                            .foregroundColor(.blue)
                        
                        Text("Velocity Rush")
                            .font(.title)
                            .fontWeight(.bold)
                        
                        Text("Version 1.0.0")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        
                        Text("The ultimate dodging challenge")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 20)
                    
                    // Credits
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Credits")
                            .font(.headline)
                        
                        VStack(spacing: 8) {
                            CreditRow(role: "Developer", name: "Christopher Dornan")
                            CreditRow(role: "Design", name: "Built with SwiftUI")
                            CreditRow(role: "Engine", name: "SpriteKit")
                        }
                    }
                    
                    // Technical Info
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Technical")
                            .font(.headline)
                        
                        VStack(spacing: 8) {
                            InfoRow(label: "Framework", value: "SwiftUI + SpriteKit")
                            InfoRow(label: "Platform", value: "iOS 15.0+")
                            InfoRow(label: "Build", value: "2025.09.01")
                        }
                    }
                    
                    Spacer()
                    
                    Text("Made with ❤️ for iOS")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding(20)
            }
            .navigationTitle("About")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        // Dismiss
                    }
                }
            }
        }
    }
}

struct CreditRow: View {
    let role: String
    let name: String
    
    var body: some View {
        HStack {
            Text(role)
                .foregroundColor(.secondary)
            Spacer()
            Text(name)
                .fontWeight(.medium)
        }
        .font(.subheadline)
    }
}

struct InfoRow: View {
    let label: String
    let value: String
    
    var body: some View {
        HStack {
            Text(label)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.medium)
        }
        .font(.subheadline)
    }
}
