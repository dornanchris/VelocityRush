//
//  GameScene.swift
//  VelocityRush
//
//  Created by Christopher Dornan on 9/1/25.
//

import SwiftUI
import SpriteKit

// MARK: - Game Scene
class GameScene: SKScene, SKPhysicsContactDelegate {
    
    // MARK: - Game Objects
    var player: SKShapeNode!
    var gameArea: CGRect
    var lastUpdateTime: TimeInterval = 0
    var deltaFrameTime: TimeInterval = 0
    
    // MARK: - Game State
    var gameState = GameState.preGame
    var gameScore = 0
    var gameStartTime: TimeInterval = 0
    var objectsDodgedThisGame = 0
    
    // MARK: - Difficulty Progression
    var lastEnemySpawn: TimeInterval = 0
    
    // MARK: - UI Labels
    var scoreLabel: SKLabelNode!
    var timerLabel: SKLabelNode!
    var tapToStartLabel: SKLabelNode!
    
    // MARK: - Physics Categories
    struct PhysicsCategories {
        static let None: UInt32 = 0
        static let Player: UInt32 = 0b1        // 1
        static let Enemy: UInt32 = 0b10        // 2
    }
    
    enum GameState {
        case preGame
        case inGame
        case afterGame
    }
    
    override init(size: CGSize) {
        let maxAspectRatio: CGFloat = 16.0/9.0
        let playableWidth = size.height / maxAspectRatio
        let margin = (size.width - playableWidth) / 2
        gameArea = CGRect(x: margin, y: 0, width: playableWidth, height: size.height)
        
        super.init(size: size)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func didMove(to view: SKView) {
        setupGame()
    }
    
    func setupGame() {
        // Physics
        physicsWorld.contactDelegate = self
        physicsWorld.gravity = CGVector(dx: 0, dy: 0)  // No gravity, we'll move dots manually
        
        // Background
        let background = SKSpriteNode(color: UIColor.black, size: self.size)
        background.position = CGPoint(x: self.size.width/2, y: self.size.height/2)
        background.zPosition = 0
        self.addChild(background)
        
        // Create player
        createPlayer()
        
        // Create UI
        createUI()
        
        gameState = .preGame
    }
    
    func createPlayer() {
        // Simple white circle for player
        player = SKShapeNode(circleOfRadius: 15)
        player.fillColor = UIColor.white
        player.strokeColor = UIColor.white
        player.position = CGPoint(x: self.size.width/2, y: 100)
        player.zPosition = 2
        player.name = "player"
        
        // Physics body
        player.physicsBody = SKPhysicsBody(circleOfRadius: 15)
        player.physicsBody!.affectedByGravity = false
        player.physicsBody!.categoryBitMask = PhysicsCategories.Player
        player.physicsBody!.collisionBitMask = PhysicsCategories.None
        player.physicsBody!.contactTestBitMask = PhysicsCategories.Enemy
        
        self.addChild(player)
    }
    
    func createUI() {
        // Score in top left
        scoreLabel = SKLabelNode(fontNamed: "Helvetica-Bold")
        scoreLabel.text = "0"
        scoreLabel.fontSize = 24
        scoreLabel.fontColor = SKColor.white
        scoreLabel.horizontalAlignmentMode = .left
        scoreLabel.position = CGPoint(x: gameArea.minX + 20, y: self.size.height - 50)
        scoreLabel.zPosition = 100
        self.addChild(scoreLabel)
        
        // Timer in top center
        timerLabel = SKLabelNode(fontNamed: "Helvetica-Bold")
        timerLabel.text = "0:00"
        timerLabel.fontSize = 28
        timerLabel.fontColor = SKColor.white
        timerLabel.horizontalAlignmentMode = .center
        timerLabel.position = CGPoint(x: self.size.width/2, y: self.size.height - 45)
        timerLabel.zPosition = 100
        self.addChild(timerLabel)
        
        // Start instruction
        tapToStartLabel = SKLabelNode(fontNamed: "Helvetica-Bold")
        tapToStartLabel.text = "Tap to Start"
        tapToStartLabel.fontSize = 32
        tapToStartLabel.fontColor = SKColor.white
        tapToStartLabel.position = CGPoint(x: self.size.width/2, y: self.size.height/2)
        tapToStartLabel.zPosition = 100
        self.addChild(tapToStartLabel)
    }
    
    func startGame() {
        gameState = .inGame
        gameStartTime = lastUpdateTime
        lastEnemySpawn = lastUpdateTime
        objectsDodgedThisGame = 0
        tapToStartLabel.removeFromParent()
        
        // Track game start
        GameDataManager.shared.gameStarted()
    }
    
    func spawnDot() {
        let gameTime = lastUpdateTime - gameStartTime
        
        // Calculate dot size based on game time (grows over 90 seconds)
        let progress = min(gameTime / 90.0, 1.0)  // Cap at 90 seconds
        let minSize: CGFloat = 8
        let maxSize: CGFloat = 40
        let dotSize = minSize + (maxSize - minSize) * progress
        
        // Random X position
        let randomX = random(min: gameArea.minX + dotSize, max: gameArea.maxX - dotSize)
        
        // Create dot
        let dot = SKShapeNode(circleOfRadius: dotSize)
        dot.fillColor = UIColor.red
        dot.strokeColor = UIColor.red
        dot.position = CGPoint(x: randomX, y: self.size.height + dotSize)
        dot.zPosition = 2
        dot.name = "enemy"
        
        // Physics body
        dot.physicsBody = SKPhysicsBody(circleOfRadius: dotSize)
        dot.physicsBody!.affectedByGravity = false
        dot.physicsBody!.categoryBitMask = PhysicsCategories.Enemy
        dot.physicsBody!.collisionBitMask = PhysicsCategories.None
        dot.physicsBody!.contactTestBitMask = PhysicsCategories.Player
        
        self.addChild(dot)
        
        // Move dot down at consistent speed
        let fallSpeed: CGFloat = 200 + (progress * 100)  // Speed increases slightly
        let fallDuration = (self.size.height + dotSize * 2) / fallSpeed
        
        let moveAction = SKAction.moveBy(x: 0, y: -(self.size.height + dotSize * 2), duration: TimeInterval(fallDuration))
        let removeAction = SKAction.removeFromParent()
        dot.run(SKAction.sequence([moveAction, removeAction]))
    }
    
    func getSpawnRate(gameTime: TimeInterval) -> TimeInterval {
        // Start with 1 dot every 1.5 seconds
        // End with 1 dot every 0.1 seconds at 90 seconds
        let progress = min(gameTime / 90.0, 1.0)
        return 1.5 - (1.4 * progress)  // Goes from 1.5 to 0.1
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        if gameState == .preGame {
            startGame()
        } else if gameState == .afterGame {
            restartGame()
        }
    }
    
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard gameState == .inGame else { return }
        
        for touch in touches {
            let pointOfTouch = touch.location(in: self)
            
            // Move player to touch X position, keeping Y constant
            let newX = max(gameArea.minX + 15, min(gameArea.maxX - 15, pointOfTouch.x))
            player.position.x = newX
        }
    }
    
    func didBegin(_ contact: SKPhysicsContact) {
        var body1 = SKPhysicsBody()
        var body2 = SKPhysicsBody()
        
        if contact.bodyA.categoryBitMask < contact.bodyB.categoryBitMask {
            body1 = contact.bodyA
            body2 = contact.bodyB
        } else {
            body1 = contact.bodyB
            body2 = contact.bodyA
        }
        
        // Player hit dot - game over
        if body1.categoryBitMask == PhysicsCategories.Player && body2.categoryBitMask == PhysicsCategories.Enemy {
            gameOver()
        }
    }
    
    func gameOver() {
        gameState = .afterGame
        
        // Calculate final stats
        let survivalTime = lastUpdateTime - gameStartTime
        
        // Track game end with stats
        GameDataManager.shared.gameEnded(survivalTime: survivalTime, objectsDodged: objectsDodgedThisGame)
        
        // Stop all actions
        self.removeAllActions()
        
        // Remove all dots
        enumerateChildNodes(withName: "enemy") { node, _ in
            node.removeFromParent()
        }
        
        // Show game over screen
        let gameOverLabel = SKLabelNode(fontNamed: "Helvetica-Bold")
        gameOverLabel.text = "Game Over"
        gameOverLabel.fontSize = 40
        gameOverLabel.fontColor = SKColor.white
        gameOverLabel.position = CGPoint(x: self.size.width/2, y: self.size.height/2 + 50)
        gameOverLabel.zPosition = 100
        self.addChild(gameOverLabel)
        
        let finalScoreLabel = SKLabelNode(fontNamed: "Helvetica")
        finalScoreLabel.text = "Score: \(gameScore)"
        finalScoreLabel.fontSize = 24
        finalScoreLabel.fontColor = SKColor.white
        finalScoreLabel.position = CGPoint(x: self.size.width/2, y: self.size.height/2)
        finalScoreLabel.zPosition = 100
        self.addChild(finalScoreLabel)
        
        let restartLabel = SKLabelNode(fontNamed: "Helvetica")
        restartLabel.text = "Tap to Restart"
        restartLabel.fontSize = 20
        restartLabel.fontColor = SKColor.gray
        restartLabel.position = CGPoint(x: self.size.width/2, y: self.size.height/2 - 50)
        restartLabel.zPosition = 100
        self.addChild(restartLabel)
    }
    
    func restartGame() {
        // Remove all children except background
        removeAllChildren()
        
        // Reset game state
        gameScore = 0
        objectsDodgedThisGame = 0
        
        // Recreate the game
        setupGame()
    }
    
    override func update(_ currentTime: TimeInterval) {
        if lastUpdateTime == 0 {
            lastUpdateTime = currentTime
        } else {
            deltaFrameTime = currentTime - lastUpdateTime
            lastUpdateTime = currentTime
        }
        
        guard gameState == .inGame else { return }
        
        let gameTime = currentTime - gameStartTime
        
        // Update score (1 point per second survived)
        gameScore = Int(gameTime)
        scoreLabel.text = "\(gameScore)"
        
        // Update timer
        let minutes = Int(gameTime) / 60
        let seconds = Int(gameTime) % 60
        timerLabel.text = String(format: "%d:%02d", minutes, seconds)
        
        // Spawn dots based on time
        let spawnRate = getSpawnRate(gameTime: gameTime)
        if currentTime - lastEnemySpawn >= spawnRate {
            spawnDot()
            lastEnemySpawn = currentTime
            objectsDodgedThisGame += 1 // Count each dot that spawns as potentially dodged
        }
    }
    
    func random(min: CGFloat, max: CGFloat) -> CGFloat {
        return CGFloat(Float(arc4random()) / Float(RAND_MAX)) * (max - min) + min
    }
}
