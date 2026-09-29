//
//  ProfileView.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//

import SwiftUI

struct ProfileView: View {
    @ObservedObject private var dataManager = GameDataManager.shared
    @State private var selectedTab = 0
    
    var body: some View {
        VStack(spacing: 0) {
            // Custom header since we're inside a TabView
            VStack {
                Text("Profile")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .padding(.top, 20)
                
                // Profile Header
                VStack(spacing: 12) {
                    Image(systemName: "person.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(.blue)
                    
                    Text("Velocity Runner")
                        .font(.title2)
                        .fontWeight(.bold)
                    
                    Text("Level \(calculateLevel())")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .padding(.bottom, 20)
                
                // Tab Selector
                Picker("Profile Tabs", selection: $selectedTab) {
                    Text("Stats").tag(0)
                    Text("Achievements").tag(1)
                }
                .pickerStyle(SegmentedPickerStyle())
                .padding(.horizontal, 20)
                .padding(.bottom, 10)
            }
            
            // Content based on selected tab
            ScrollView {
                VStack(spacing: 20) {
                    if selectedTab == 0 {
                        StatsContentView()
                    } else {
                        AchievementsContentView()
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
            }
        }
        .background(Color(.systemBackground))
    }
    
    private func calculateLevel() -> Int {
        let totalScore = Int(dataManager.stats.totalTimeSurvived) +
                        (dataManager.stats.totalObjectsDodged / 10) +
                        (dataManager.stats.totalStarsCollected * 2)
        return max(1, totalScore / 100 + 1)
    }
}

struct StatsContentView: View {
    @ObservedObject private var dataManager = GameDataManager.shared
    
    var body: some View {
        VStack(spacing: 16) {
            // Main Stats Grid
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 8),
                GridItem(.flexible(), spacing: 8)
            ], spacing: 12) {
                StatCard(
                    title: "Best Time",
                    value: dataManager.formatTime(dataManager.stats.bestSurvivalTime),
                    icon: "crown.fill",
                    color: .yellow
                )
                
                StatCard(
                    title: "Total Time",
                    value: dataManager.formatTime(dataManager.stats.totalTimeSurvived),
                    icon: "clock.fill",
                    color: .blue
                )
                
                StatCard(
                    title: "Objects Dodged",
                    value: "\(dataManager.stats.totalObjectsDodged)",
                    icon: "shield.fill",
                    color: .green
                )
                
                StatCard(
                    title: "Games Played",
                    value: "\(dataManager.stats.totalGamesPlayed)",
                    icon: "gamecontroller.fill",
                    color: .purple
                )
                
                StatCard(
                    title: "Stars Collected",
                    value: "\(dataManager.stats.totalStarsCollected)",
                    icon: "star.fill",
                    color: .orange
                )
                
                StatCard(
                    title: "Current Streak",
                    value: "\(dataManager.stats.currentStreak)",
                    icon: "flame.fill",
                    color: .red
                )
            }
            
            // Milestones Section
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Milestones")
                        .font(.headline)
                    Spacer()
                }
                
                VStack(spacing: 8) {
                    MilestoneRow(
                        title: "Longest Streak",
                        value: "\(dataManager.stats.longestStreak) games"
                    )
                    
                    MilestoneRow(
                        title: "Store Purchases",
                        value: "\(dataManager.stats.totalPurchasesMade)"
                    )
                    
                    if dataManager.stats.totalGamesPlayed > 0 {
                        let avgSurvival = dataManager.stats.totalTimeSurvived / Double(dataManager.stats.totalGamesPlayed)
                        MilestoneRow(
                            title: "Average Survival",
                            value: dataManager.formatTime(avgSurvival)
                        )
                    } else {
                        MilestoneRow(
                            title: "Average Survival",
                            value: "Play a game first!"
                        )
                    }
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }
        }
    }
}

struct AchievementsContentView: View {
    @ObservedObject private var dataManager = GameDataManager.shared
    @State private var selectedCategory: Achievement.AchievementCategory? = nil
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Achievement Summary
            HStack {
                VStack(alignment: .leading) {
                    Text("Achievements")
                        .font(.headline)
                    Text("\(dataManager.getUnlockedAchievements().count)/\(dataManager.achievements.count) Unlocked")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // Progress Ring
                ZStack {
                    Circle()
                        .stroke(Color.secondary.opacity(0.3), lineWidth: 4)
                        .frame(width: 50, height: 50)
                    
                    Circle()
                        .trim(from: 0, to: dataManager.achievements.isEmpty ? 0 : CGFloat(dataManager.getUnlockedAchievements().count) / CGFloat(dataManager.achievements.count))
                        .stroke(Color.blue, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                        .frame(width: 50, height: 50)
                        .rotationEffect(.degrees(-90))
                    
                    Text("\(dataManager.achievements.isEmpty ? 0 : Int((Double(dataManager.getUnlockedAchievements().count) / Double(dataManager.achievements.count)) * 100))%")
                        .font(.caption)
                        .fontWeight(.bold)
                }
            }
            
            // Category Filter
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    CategoryChip(
                        title: "All",
                        isSelected: selectedCategory == nil,
                        action: { selectedCategory = nil }
                    )
                    
                    ForEach(Achievement.AchievementCategory.allCases, id: \.self) { category in
                        CategoryChip(
                            title: category.rawValue,
                            isSelected: selectedCategory == category,
                            action: { selectedCategory = category }
                        )
                    }
                }
                .padding(.horizontal, 4)
            }
            
            // Achievements List
            LazyVStack(spacing: 12) {
                ForEach(filteredAchievements()) { achievement in
                    AchievementCard(achievement: achievement)
                }
            }
        }
    }
    
    private func filteredAchievements() -> [Achievement] {
        if let category = selectedCategory {
            return dataManager.getAchievementsByCategory(category)
        }
        return dataManager.achievements
    }
}

struct StatCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
            
            Text(value)
                .font(.title3)
                .fontWeight(.bold)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 100)
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}

struct MilestoneRow: View {
    let title: String
    let value: String
    
    var body: some View {
        HStack {
            Text(title)
                .foregroundColor(.secondary)
            Spacer()
            Text(value)
                .fontWeight(.medium)
        }
        .padding(.vertical, 2)
    }
}

struct AchievementCard: View {
    let achievement: Achievement
    
    var body: some View {
        HStack(spacing: 12) {
            // Icon
            ZStack {
                Circle()
                    .fill(achievement.isUnlocked ? Color.blue : Color.secondary.opacity(0.3))
                    .frame(width: 40, height: 40)
                
                Image(systemName: achievement.icon)
                    .font(.system(size: 18))
                    .foregroundColor(achievement.isUnlocked ? .white : .secondary)
            }
            
            // Content
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(achievement.title)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(achievement.isUnlocked ? .primary : .secondary)
                    
                    Spacer()
                    
                    if achievement.isUnlocked {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.system(size: 16))
                    }
                }
                
                Text(achievement.description)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.leading)
                
                if !achievement.isUnlocked && achievement.requirement > 0 {
                    // Progress bar
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text("\(achievement.progress)/\(achievement.requirement)")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            
                            Spacer()
                            
                            Text("\(Int(achievement.progressPercentage * 100))%")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                        
                        GeometryReader { geometry in
                            ZStack(alignment: .leading) {
                                Rectangle()
                                    .fill(Color.secondary.opacity(0.3))
                                    .frame(height: 3)
                                    .cornerRadius(1.5)
                                
                                Rectangle()
                                    .fill(Color.blue)
                                    .frame(width: geometry.size.width * achievement.progressPercentage, height: 3)
                                    .cornerRadius(1.5)
                            }
                        }
                        .frame(height: 3)
                    }
                }
            }
        }
        .padding(12)
        .background(Color(.systemGray6))
        .cornerRadius(8)
    }
}

struct CategoryChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.caption)
                .fontWeight(.medium)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.blue : Color(.systemGray5))
                .foregroundColor(isSelected ? .white : .primary)
                .cornerRadius(16)
        }
    }
}
