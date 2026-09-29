//
//  FuelManager.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//

import Foundation
import Combine

class FuelManager: ObservableObject {
    static let shared = FuelManager()
    
    @Published var currentFuel: Int = 5
    @Published var lastRefillTime: Date = Date()
    @Published var timeUntilNextRefill: TimeInterval = 0
    
    private let maxFuel = 5
    private let refillInterval: TimeInterval = 30 * 60 // 30 minutes
    private let fuelKey = "VelocityRushFuel"
    private let lastRefillKey = "VelocityRushLastRefill"
    
    private var timer: Timer?
    
    init() {
        loadFuelData()
        startRefillTimer()
        calculateRefills()
    }
    
    private func loadFuelData() {
        currentFuel = UserDefaults.standard.integer(forKey: fuelKey)
        if currentFuel == 0 { currentFuel = maxFuel } // First launch
        
        if let savedTime = UserDefaults.standard.object(forKey: lastRefillKey) as? Date {
            lastRefillTime = savedTime
        }
    }
    
    private func saveFuelData() {
        UserDefaults.standard.set(currentFuel, forKey: fuelKey)
        UserDefaults.standard.set(lastRefillTime, forKey: lastRefillKey)
    }
    
    private func calculateRefills() {
        let now = Date()
        let timePassed = now.timeIntervalSince(lastRefillTime)
        let refillsEarned = Int(timePassed / refillInterval)
        
        if refillsEarned > 0 && currentFuel < maxFuel {
            let newFuel = min(maxFuel, currentFuel + refillsEarned)
            currentFuel = newFuel
            lastRefillTime = now.addingTimeInterval(-timePassed.truncatingRemainder(dividingBy: refillInterval))
            saveFuelData()
        }
        
        updateTimeUntilNextRefill()
    }
    
    private func updateTimeUntilNextRefill() {
        if currentFuel >= maxFuel {
            timeUntilNextRefill = 0
        } else {
            let timeSinceLastRefill = Date().timeIntervalSince(lastRefillTime)
            timeUntilNextRefill = max(0, refillInterval - timeSinceLastRefill)
        }
    }
    
    private func startRefillTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            self.calculateRefills()
        }
    }
    
    func canPlayGame() -> Bool {
        return currentFuel > 0
    }
    
    func consumeFuel() {
        guard currentFuel > 0 else { return }
        currentFuel -= 1
        
        if currentFuel == maxFuel - 1 { // Started refill cycle
            lastRefillTime = Date()
        }
        
        saveFuelData()
    }
    
    func purchaseFuel() {
        currentFuel = maxFuel
        lastRefillTime = Date()
        saveFuelData()
        GameDataManager.shared.purchaseMade()
    }
    
    func formatTimeUntilRefill() -> String {
        let minutes = Int(timeUntilNextRefill) / 60
        let seconds = Int(timeUntilNextRefill) % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
    
    deinit {
        timer?.invalidate()
    }
}
