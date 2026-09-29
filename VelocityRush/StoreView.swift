
//
//  StoreView.swift
//  VelocityRush
//

import SwiftUI

struct StoreView: View {
    @ObservedObject private var fuelManager = FuelManager.shared
    @State private var showingPurchaseAlert = false
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            VStack {
                Text("Game Store")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .padding(.top, 20)
                
                Text("Refuel and keep playing!")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .padding(.bottom, 20)
            }
            
            ScrollView {
                VStack(spacing: 20) {
                    // Current Fuel Status
                    FuelStatusCard()
                    
                    // Fuel Purchase Option
                    VStack(spacing: 16) {
                        Text("Fuel Packages")
                            .font(.headline)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        
                        FuelPurchaseCard(
                            title: "Full Refuel",
                            description: "Instantly refill all 5 fuel tanks",
                            price: "$1.99",
                            icon: "fuelpump.fill"
                        ) {
                            showingPurchaseAlert = true
                        }
                        
                        // Future items placeholder
                        VStack(spacing: 12) {
                            Text("Coming Soon")
                                .font(.headline)
                                .foregroundColor(.secondary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            
                            ComingSoonCard(title: "Power-ups", description: "Slow motion, shields, and more!")
                            ComingSoonCard(title: "Themes", description: "New backgrounds and effects")
                            ComingSoonCard(title: "Player Skins", description: "Customize your dot")
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 10)
            }
        }
        .background(Color(.systemBackground))
        .alert("Purchase Fuel?", isPresented: $showingPurchaseAlert) {
            Button("Cancel", role: .cancel) { }
            Button("Buy $1.99") {
                // In real app, integrate with StoreKit
                fuelManager.purchaseFuel()
            }
        } message: {
            Text("Instantly refill all your fuel to keep playing!")
        }
    }
}
