//
//  GameView.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//



import SwiftUI
import SpriteKit

struct GameView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> GameViewController {
        return GameViewController()
    }
    
    func updateUIViewController(_ uiViewController: GameViewController, context: Context) {}
}

class GameViewController: UIViewController {
    private let fuelManager = FuelManager.shared
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupGameView()
    }
    
    private func setupGameView() {
        // Check if player has fuel
        if !fuelManager.canPlayGame() {
            showNoFuelAlert()
            return
        }
        
        // Consume fuel and start game
        fuelManager.consumeFuel()
        
        let scene = GameScene(size: CGSize(width: 414, height: 736))
        let skView = SKView(frame: view.bounds)
        skView.presentScene(scene)
        skView.ignoresSiblingOrder = true
        
        view.addSubview(skView)
    }
    
    private func showNoFuelAlert() {
        let alert = UIAlertController(
            title: "Out of Fuel!",
            message: "Wait for fuel to refill or purchase more in the store.",
            preferredStyle: .alert
        )
        
        alert.addAction(UIAlertAction(title: "Wait", style: .cancel))
        alert.addAction(UIAlertAction(title: "Store", style: .default) { _ in
            // Switch to store tab - you'll need to implement tab switching
        })
        
        present(alert, animated: true)
    }
    
    override var prefersStatusBarHidden: Bool {
        return true
    }
}
