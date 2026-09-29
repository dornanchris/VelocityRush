//
//  FuelStatusCard.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//

import SwiftUICore
import SwiftUI

struct FuelStatusCard: View {
    @ObservedObject private var fuelManager = FuelManager.shared
    
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Image(systemName: "fuelpump.fill")
                    .font(.title2)
                    .foregroundColor(.blue)
                
                Text("Fuel Status")
                    .font(.headline)
                
                Spacer()
                
                Text("\(fuelManager.currentFuel)/5")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(fuelManager.currentFuel == 0 ? .red : .primary)
            }
            
            // Fuel tanks visualization
            HStack(spacing: 8) {
                ForEach(0..<5, id: \.self) { index in
                    RoundedRectangle(cornerRadius: 4)
                        .fill(index < fuelManager.currentFuel ? Color.blue : Color.secondary.opacity(0.3))
                        .frame(height: 20)
                }
            }
            
            // Refill timer
            if fuelManager.currentFuel < 5 {
                VStack(spacing: 4) {
                    Text("Next refill in:")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Text(fuelManager.formatTimeUntilRefill())
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.blue)
                }
            } else {
                Text("Fuel Full!")
                    .font(.subheadline)
                    .foregroundColor(.green)
                    .fontWeight(.medium)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}


struct FuelPurchaseCard: View {
    let title: String
    let description: String
    let price: String
    let icon: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(Color.green)
                        .frame(width: 50, height: 50)
                    
                    Image(systemName: icon)
                        .font(.title2)
                        .foregroundColor(.white)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(description)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.leading)
                }
                
                Spacer()
                
                VStack {
                    Text(price)
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                    
                    Text("BUY")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                }
            }
            .padding()
            .background(Color(.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.green.opacity(0.3), lineWidth: 2)
            )
            .cornerRadius(12)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct ComingSoonCard: View {
    let title: String
    let description: String
    
    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.secondary.opacity(0.3))
                    .frame(width: 50, height: 50)
                
                Image(systemName: "clock")
                    .font(.title2)
                    .foregroundColor(.secondary)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                    .foregroundColor(.secondary)
                
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.leading)
            }
            
            Spacer()
            
            Text("Soon")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.secondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.secondary.opacity(0.2))
                .cornerRadius(12)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}
