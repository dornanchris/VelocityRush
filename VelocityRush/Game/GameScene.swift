//
//  GameScene.swift
//  VelocityRush
//
//  Renders a GameEngine: syncs sprites with the simulation every frame and
//  turns engine events into particles, sounds, haptics and screen shake.
//

import SpriteKit
import UIKit

final class GameScene: SKScene {

    // MARK: Dependencies

    private weak var session: GameSessionModel?
    private let settings: GameSettings
    private let skin: SkinLook
    private let trail: TrailLook
    private let theme: ThemeLook
    private(set) var engine: GameEngine
    private var config: RunConfig

    // MARK: Colours

    private var hazardColor: RGBColor {
        if settings.colorBlindMode { return RGBColor(hex: 0xFF8C00) }
        return settings.highContrast ? RGBColor(hex: 0xFF2020) : theme.hazard
    }

    private var hazardAltColor: RGBColor {
        if settings.colorBlindMode { return RGBColor(hex: 0xFFE14D) }
        return settings.highContrast ? RGBColor(hex: 0xFF7A00) : theme.hazardAlt
    }

    private var outlineHazards: Bool { settings.colorBlindMode || settings.highContrast }

    // MARK: Nodes

    private let backgroundNode = SKSpriteNode()
    private let starfieldNode = SKNode()
    private let shakeNode = SKNode()
    private let worldNode = SKNode()
    private let pickupLayer = SKNode()
    private let hazardLayer = SKNode()
    private let effectLayer = SKNode()
    private let warningLayer = SKNode()
    private let bannerLayer = SKNode()
    private let playerNode = SKSpriteNode()
    private let shieldNode = SKShapeNode(circleOfRadius: 25)
    private let slowMoOverlay = SKSpriteNode(color: .clear, size: .zero)
    private let flashNode = SKSpriteNode(color: .white, size: .zero)
    private var trailEmitter: SKEmitterNode?
    private var vignetteNode: SKSpriteNode?
    private var edgeNodes: [SKSpriteNode] = []
    private var gridLines: [SKSpriteNode] = []
    private var stars: [(node: SKSpriteNode, speed: CGFloat)] = []

    private var hazardNodes: [Int: SKSpriteNode] = [:]
    private var pickupNodes: [Int: SKSpriteNode] = [:]
    private var warningNodes: [Int: SKNode] = [:]

    // MARK: State

    private var lastUpdateTime: TimeInterval = 0
    private var visualTime: Double = 0
    private var isRunPaused = false
    private var didReportFinish = false
    private var pendingFinishAt: Double?
    private var activeTouch: UITouch?
    private var lastTouchPoint: CGPoint = .zero
    private var worldScale: CGFloat = 1
    private var lastHUD = HUDState()

    // MARK: Init

    init(size: CGSize, config: RunConfig, session: GameSessionModel, settings: GameSettings, profile: PlayerProfile) {
        self.session = session
        self.settings = settings
        self.config = config
        self.skin = Cosmetic.skinLook(profile.equippedSkin)
        self.trail = Cosmetic.trailLook(profile.equippedTrail)
        self.theme = Cosmetic.themeLook(profile.equippedTheme)
        self.engine = GameEngine(config: config,
                                 playfield: Playfield.fitting(viewWidth: Double(size.width), viewHeight: Double(size.height)))
        super.init(size: size)
        scaleMode = .resizeFill
        backgroundColor = theme.backgroundBottom.uiColor
        buildScene()
        layoutScene()
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func didMove(to view: SKView) {
        view.isMultipleTouchEnabled = false
        HapticsManager.shared.prepare()
    }

    override func didChangeSize(_ oldSize: CGSize) {
        super.didChangeSize(oldSize)
        layoutScene()
    }

    // MARK: Scene graph

    private func buildScene() {
        backgroundNode.texture = TextureFactory.verticalGradient(top: settings.highContrast ? .black : theme.backgroundTop,
                                                                 bottom: theme.backgroundBottom)
        backgroundNode.zPosition = -100
        addChild(backgroundNode)

        starfieldNode.zPosition = -90
        addChild(starfieldNode)
        buildStarfield()
        if theme.grid && !settings.reducedMotion { buildGrid() }

        shakeNode.zPosition = 0
        addChild(shakeNode)
        shakeNode.addChild(worldNode)

        for x in [0.0, engine.playfield.width] {
            let edge = SKSpriteNode(color: theme.accent.uiColor.withAlphaComponent(0.25),
                                    size: CGSize(width: 2, height: engine.playfield.height))
            edge.anchorPoint = CGPoint(x: 0.5, y: 0)
            edge.position = CGPoint(x: x, y: 0)
            edge.zPosition = -10
            edge.isHidden = true
            worldNode.addChild(edge)
            edgeNodes.append(edge)
        }

        pickupLayer.zPosition = 10
        hazardLayer.zPosition = 20
        effectLayer.zPosition = 40
        warningLayer.zPosition = 60
        [pickupLayer, hazardLayer, effectLayer, warningLayer].forEach { worldNode.addChild($0) }

        // Player
        playerNode.texture = TextureFactory.skin(skin)
        let playerSide = CGFloat(GameEngine.basePlayerRadius) * 2 * TextureFactory.glowRatio
        playerNode.size = CGSize(width: playerSide, height: playerSide)
        playerNode.zPosition = 30
        playerNode.position = engine.playerPosition.cgPoint
        if skin.style == .prism { playerNode.colorBlendFactor = 1 }
        worldNode.addChild(playerNode)

        shieldNode.strokeColor = PickupKind.shield.color.uiColor
        shieldNode.fillColor = PickupKind.shield.color.uiColor.withAlphaComponent(0.12)
        shieldNode.lineWidth = 3
        shieldNode.glowWidth = 4
        shieldNode.zPosition = 31
        shieldNode.isHidden = true
        shieldNode.run(.repeatForever(.sequence([.scale(to: 1.08, duration: 0.45), .scale(to: 0.96, duration: 0.45)])))
        worldNode.addChild(shieldNode)

        buildTrail()

        if config.modifier == .blackout {
            buildVignette()
        }

        slowMoOverlay.color = PickupKind.slowMo.color.uiColor
        slowMoOverlay.alpha = 0
        slowMoOverlay.zPosition = 80
        slowMoOverlay.blendMode = .add
        addChild(slowMoOverlay)

        flashNode.alpha = 0
        flashNode.zPosition = 90
        addChild(flashNode)

        bannerLayer.zPosition = 100
        addChild(bannerLayer)
    }

    private func buildStarfield() {
        let layers: [(count: Int, speed: CGFloat, size: CGFloat, alpha: CGFloat)] = [
            (40, 14, 1.5, 0.35), (24, 32, 2.2, 0.55), (12, 60, 3.0, 0.8)
        ]
        for layer in layers {
            for _ in 0..<layer.count {
                let star = SKSpriteNode(texture: TextureFactory.softDot)
                star.size = CGSize(width: layer.size * 2, height: layer.size * 2)
                star.color = theme.star.uiColor
                star.colorBlendFactor = 1
                star.alpha = layer.alpha * CGFloat.random(in: 0.6...1)
                star.position = CGPoint(x: CGFloat.random(in: 0...max(size.width, 1)),
                                        y: CGFloat.random(in: 0...max(size.height, 1)))
                starfieldNode.addChild(star)
                stars.append((star, layer.speed * CGFloat.random(in: 0.8...1.2)))
            }
        }
    }

    private func buildGrid() {
        for index in 0..<14 {
            let line = SKSpriteNode(color: theme.accent.uiColor.withAlphaComponent(0.12),
                                    size: CGSize(width: max(size.width, 1), height: 1.5))
            line.anchorPoint = CGPoint(x: 0, y: 0.5)
            line.position = CGPoint(x: 0, y: CGFloat(index) * max(size.height, 1) / 14)
            starfieldNode.addChild(line)
            gridLines.append(line)
        }
    }

    private func buildTrail() {
        trailEmitter?.removeFromParent()
        trailEmitter = nil
        guard trail.style != .none else { return }

        let emitter = SKEmitterNode()
        emitter.particleTexture = TextureFactory.softDot
        emitter.particleColor = trail.color.uiColor
        emitter.particleColorBlendFactor = 1
        emitter.particleBlendMode = .add
        emitter.emissionAngle = -.pi / 2
        emitter.particlePositionRange = CGVector(dx: 6, dy: 6)
        emitter.targetNode = worldNode
        emitter.zPosition = 29

        let reduce: CGFloat = settings.reducedMotion ? 0.5 : 1
        switch trail.style {
        case .none:
            break
        case .spark:
            emitter.particleBirthRate = 45 * reduce
            emitter.particleLifetime = 0.5
            emitter.particleSpeed = 90
            emitter.particleSpeedRange = 40
            emitter.emissionAngleRange = 0.6
            emitter.particleScale = 0.35
            emitter.particleScaleRange = 0.2
            emitter.particleAlphaSpeed = -2
        case .comet:
            emitter.particleBirthRate = 140 * reduce
            emitter.particleLifetime = 0.55
            emitter.particleSpeed = 160
            emitter.particleSpeedRange = 20
            emitter.emissionAngleRange = 0.12
            emitter.particleScale = 0.9
            emitter.particleScaleSpeed = -1.5
            emitter.particleAlphaSpeed = -1.6
            emitter.particleColorSequence = SKKeyframeSequence(
                keyframeValues: [UIColor.white, trail.color.uiColor, RGBColor(hex: 0xFF2E2E).uiColor],
                times: [0, 0.3, 1])
        case .bubbles:
            emitter.particleTexture = TextureFactory.bubble
            emitter.particleBirthRate = 14 * reduce
            emitter.particleLifetime = 1.1
            emitter.particleSpeed = 70
            emitter.particleSpeedRange = 30
            emitter.emissionAngleRange = 0.9
            emitter.particleScale = 0.35
            emitter.particleScaleRange = 0.25
            emitter.particleScaleSpeed = 0.3
            emitter.particleAlphaSpeed = -0.9
            emitter.particleBlendMode = .alpha
        case .afterimage:
            emitter.particleTexture = TextureFactory.skin(skin)
            emitter.particleBirthRate = 32 * reduce
            emitter.particleLifetime = 0.3
            emitter.particleSpeed = 0
            emitter.particlePositionRange = .zero
            emitter.particleScale = CGFloat(GameEngine.basePlayerRadius * 2) * TextureFactory.glowRatio / TextureFactory.orbCanvas
            emitter.particleAlpha = 0.45
            emitter.particleAlphaSpeed = -1.6
            emitter.particleColor = skin.glow.uiColor
        case .rainbow:
            emitter.particleBirthRate = 110 * reduce
            emitter.particleLifetime = 0.6
            emitter.particleSpeed = 130
            emitter.particleSpeedRange = 20
            emitter.emissionAngleRange = 0.2
            emitter.particleScale = 0.6
            emitter.particleScaleSpeed = -0.8
            emitter.particleAlphaSpeed = -1.5
        case .stardust:
            emitter.particleTexture = TextureFactory.tinyStar
            emitter.particleBirthRate = 50 * reduce
            emitter.particleLifetime = 0.9
            emitter.particleSpeed = 70
            emitter.particleSpeedRange = 50
            emitter.emissionAngleRange = 1.4
            emitter.particleScale = 0.5
            emitter.particleScaleRange = 0.3
            emitter.particleRotationSpeed = 4
            emitter.particleAlphaSpeed = -1.1
        }
        emitter.position = playerNode.position
        worldNode.addChild(emitter)
        trailEmitter = emitter
    }

    private func buildVignette() {
        // Texture is 512px: clear radius ≈ 115pt, fully dark by ≈ 200pt at 2400pt size.
        let side: CGFloat = 2400
        let vignette = SKSpriteNode(texture: TextureFactory.vignette(clearFraction: 115 / 1200, darkFraction: 200 / 1200))
        vignette.size = CGSize(width: side, height: side)
        vignette.zPosition = 50
        vignette.alpha = 0.96
        worldNode.addChild(vignette)
        vignetteNode = vignette
    }

    private func layoutScene() {
        guard size.width > 1, size.height > 1 else { return }
        let playfield = engine.playfield
        worldScale = min(size.width / CGFloat(playfield.width), size.height / CGFloat(playfield.height))
        worldNode.setScale(worldScale)
        worldNode.position = CGPoint(x: (size.width - CGFloat(playfield.width) * worldScale) / 2,
                                     y: (size.height - CGFloat(playfield.height) * worldScale) / 2)

        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        backgroundNode.size = size
        backgroundNode.position = center
        slowMoOverlay.size = size
        slowMoOverlay.position = center
        flashNode.size = size
        flashNode.position = center
        bannerLayer.position = CGPoint(x: size.width / 2, y: size.height * 0.62)

        let showEdges = size.width - CGFloat(playfield.width) * worldScale > 24
        edgeNodes.forEach { $0.isHidden = !showEdges }
        for line in gridLines {
            line.size.width = size.width
        }
    }

    // MARK: Run lifecycle

    /// Starts a fresh run (Play Again) reusing the scene.
    func startRun(config newConfig: RunConfig) {
        config = newConfig
        engine = GameEngine(config: newConfig,
                            playfield: Playfield.fitting(viewWidth: Double(size.width), viewHeight: Double(size.height)))
        hazardNodes.values.forEach { $0.removeFromParent() }
        pickupNodes.values.forEach { $0.removeFromParent() }
        warningNodes.values.forEach { $0.removeFromParent() }
        hazardNodes.removeAll()
        pickupNodes.removeAll()
        warningNodes.removeAll()
        effectLayer.removeAllChildren()
        bannerLayer.removeAllChildren()
        pendingFinishAt = nil

        playerNode.removeAllActions()
        playerNode.isHidden = false
        playerNode.alpha = 1
        playerNode.setScale(1)
        playerNode.position = engine.playerPosition.cgPoint
        buildTrail()
        vignetteNode?.removeFromParent()
        vignetteNode = nil
        if newConfig.modifier == .blackout { buildVignette() }

        lastHUD = HUDState()
        didReportFinish = false
        activeTouch = nil
        lastUpdateTime = 0
        setRunPaused(false)
        layoutScene()
    }

    func setRunPaused(_ paused: Bool) {
        isRunPaused = paused
        worldNode.isPaused = paused
        trailEmitter?.isPaused = paused
        lastUpdateTime = 0
        activeTouch = nil
    }

    func endRunEarly() {
        engine.endRun()
    }

    // MARK: Touch input (relative drag, works anywhere on screen)

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard activeTouch == nil, let touch = touches.first else { return }
        activeTouch = touch
        lastTouchPoint = touch.location(in: self)
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch), !isRunPaused else { return }
        let point = touch.location(in: self)
        let dx = Double(point.x - lastTouchPoint.x)
        let dy = Double(point.y - lastTouchPoint.y)
        lastTouchPoint = point
        let factor = settings.sensitivity / Double(max(worldScale, 0.01))
        let target = engine.playerTarget
        engine.setTarget(Vec2(target.x + dx * factor, target.y + dy * factor))
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let touch = activeTouch, touches.contains(touch) { activeTouch = nil }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let touch = activeTouch, touches.contains(touch) { activeTouch = nil }
    }

    // MARK: Frame update

    override func update(_ currentTime: TimeInterval) {
        guard lastUpdateTime > 0 else {
            lastUpdateTime = currentTime
            return
        }
        let delta = min(currentTime - lastUpdateTime, GameEngine.maxDeltaTime)
        lastUpdateTime = currentTime
        guard !isRunPaused, delta > 0 else { return }

        visualTime += delta
        engine.step(delta)
        handle(engine.drainEvents())
        syncHazards()
        syncPickups()
        syncWarnings()
        updatePlayer()
        updateBackground(delta)
        pushHUD()

        // Let the final explosion play out before showing results.
        if engine.isFinished && !didReportFinish {
            didReportFinish = true
            pendingFinishAt = visualTime + (engine.config.mode.hasSingleLife ? 1.1 : 0.6)
        }
        if let finishAt = pendingFinishAt, visualTime >= finishAt {
            pendingFinishAt = nil
            session?.runFinished(engine.makeResult())
        }
    }

    // MARK: Syncing sprites

    private func syncHazards() {
        var alive = Set<Int>()
        for hazard in engine.hazards {
            alive.insert(hazard.id)
            let node = hazardNodes[hazard.id] ?? makeHazardNode(for: hazard)
            node.position = hazard.position.cgPoint
        }
        for (id, node) in hazardNodes where !alive.contains(id) {
            node.removeFromParent()
            hazardNodes[id] = nil
        }
    }

    private func makeHazardNode(for hazard: Hazard) -> SKSpriteNode {
        let texture: SKTexture
        switch hazard.kind {
        case .drop:
            texture = TextureFactory.hazard(color: hazardColor, decoration: .plain, outline: outlineHazards)
        case .wobbler:
            texture = TextureFactory.hazard(color: hazardColor.mixed(with: hazardAltColor, amount: 0.5),
                                            decoration: .dashed, outline: outlineHazards)
        case .speeder:
            texture = TextureFactory.hazard(color: hazardAltColor.lighter(0.2), decoration: .core, outline: outlineHazards)
        case .giant:
            texture = TextureFactory.hazard(color: hazardColor.darker(0.15), decoration: .ring, outline: outlineHazards)
        case .splitter:
            texture = TextureFactory.hazard(color: hazardAltColor, decoration: .hollow, outline: outlineHazards)
        case .fragment:
            texture = TextureFactory.hazard(color: hazardAltColor, decoration: .plain, outline: outlineHazards)
        }
        let node = SKSpriteNode(texture: texture)
        let side = CGFloat(hazard.radius) * 2 * TextureFactory.glowRatio
        node.size = CGSize(width: side, height: side)
        node.position = hazard.position.cgPoint
        hazardLayer.addChild(node)
        hazardNodes[hazard.id] = node

        if !settings.reducedMotion {
            node.setScale(0.3)
            node.run(.scale(to: 1, duration: 0.18))
        }

        if hazard.kind == .speeder {
            let streak = SKEmitterNode()
            streak.particleTexture = TextureFactory.softDot
            streak.particleColor = hazardAltColor.uiColor
            streak.particleColorBlendFactor = 1
            streak.particleBlendMode = .add
            streak.particleBirthRate = settings.reducedMotion ? 40 : 90
            streak.particleLifetime = 0.35
            streak.particleSpeed = 0
            streak.particleScale = CGFloat(hazard.radius) / 10
            streak.particleScaleSpeed = -2
            streak.particleAlphaSpeed = -2.5
            streak.targetNode = worldNode
            streak.zPosition = -1
            node.addChild(streak)
        } else if hazard.kind == .wobbler && !settings.reducedMotion {
            node.run(.repeatForever(.rotate(byAngle: .pi, duration: 1.2)))
        }
        return node
    }

    private func syncPickups() {
        var alive = Set<Int>()
        for pickup in engine.pickups {
            alive.insert(pickup.id)
            let node = pickupNodes[pickup.id] ?? makePickupNode(for: pickup)
            node.position = pickup.position.cgPoint
        }
        for (id, node) in pickupNodes where !alive.contains(id) {
            node.removeFromParent()
            pickupNodes[id] = nil
        }
    }

    private func makePickupNode(for pickup: Pickup) -> SKSpriteNode {
        let node: SKSpriteNode
        let radius = CGFloat(pickup.radius)
        switch pickup.kind {
        case .star:
            node = SKSpriteNode(texture: TextureFactory.star(color: pickup.kind.color))
            node.size = CGSize(width: radius * 2 * 1.6 * 1.3, height: radius * 2 * 1.6 * 1.3)
            node.run(.repeatForever(.rotate(byAngle: .pi * 2, duration: 3)))
        case .timeCrystal:
            node = SKSpriteNode(texture: TextureFactory.crystal(color: pickup.kind.color))
            node.size = CGSize(width: radius * 2 * 1.6 * 1.4, height: radius * 2 * 1.6 * 1.4)
            node.run(.repeatForever(.sequence([.scale(to: 1.15, duration: 0.4), .scale(to: 0.95, duration: 0.4)])))
        default:
            node = SKSpriteNode(texture: TextureFactory.powerUp(pickup.kind))
            let side = radius * 2 * TextureFactory.glowRatio
            node.size = CGSize(width: side, height: side)
            node.run(.repeatForever(.sequence([.scale(to: 1.12, duration: 0.5), .scale(to: 0.94, duration: 0.5)])))
        }
        node.position = pickup.position.cgPoint
        pickupLayer.addChild(node)
        pickupNodes[pickup.id] = node
        return node
    }

    private func syncWarnings() {
        var alive = Set<Int>()
        let playfield = engine.playfield
        for warning in engine.warnings {
            alive.insert(warning.id)
            guard warningNodes[warning.id] == nil else { continue }
            let container = SKNode()
            container.position = CGPoint(x: warning.x, y: 0)

            let lane = SKSpriteNode(color: hazardAltColor.uiColor.withAlphaComponent(0.13),
                                    size: CGSize(width: warning.radius * 2 + 8, height: playfield.height))
            lane.anchorPoint = CGPoint(x: 0.5, y: 0)
            lane.blendMode = .add
            container.addChild(lane)

            let icon = SKSpriteNode(texture: TextureFactory.warningIcon(color: hazardAltColor))
            icon.size = CGSize(width: 34, height: 34)
            icon.position = CGPoint(x: 0, y: playfield.height - 34)
            icon.run(.repeatForever(.sequence([.fadeAlpha(to: 0.25, duration: 0.09), .fadeAlpha(to: 1, duration: 0.09)])))
            container.addChild(icon)

            warningLayer.addChild(container)
            warningNodes[warning.id] = container
        }
        for (id, node) in warningNodes where !alive.contains(id) {
            node.removeFromParent()
            warningNodes[id] = nil
        }
    }

    private func updatePlayer() {
        guard !playerNode.isHidden else { return }
        let position = engine.playerPosition.cgPoint
        playerNode.position = position
        let scale = CGFloat(engine.playerRadius / GameEngine.basePlayerRadius)
        playerNode.xScale = scale
        playerNode.yScale = scale

        if engine.invulnerability > 0 {
            playerNode.alpha = sin(visualTime * 32) > 0 ? 1 : 0.3
        } else {
            playerNode.alpha = 1
        }

        if skin.style == .prism {
            playerNode.color = RGBColor.hsb(visualTime * 0.35, 0.75, 1).uiColor
        }

        shieldNode.isHidden = !engine.hasShield
        shieldNode.position = position

        if let trailEmitter {
            trailEmitter.position = position
            if trail.style == .rainbow {
                trailEmitter.particleColor = RGBColor.hsb(visualTime * 0.8, 0.85, 1).uiColor
            }
        }

        vignetteNode?.position = position

        let slowAlpha: CGFloat = engine.isSlowMotion ? 0.14 : 0
        if abs(slowMoOverlay.alpha - slowAlpha) > 0.001 {
            slowMoOverlay.alpha += (slowAlpha - slowMoOverlay.alpha) * 0.15
        }
    }

    private func updateBackground(_ delta: Double) {
        guard !settings.reducedMotion || engine.phase == .running else { return }
        let intensityBoost = CGFloat(1 + engine.intensity * 2.2)
        let slow: CGFloat = engine.isSlowMotion ? 0.35 : 1
        let running: CGFloat = engine.phase == .finished ? 0.2 : 1
        let factor = CGFloat(delta) * intensityBoost * slow * running * (settings.reducedMotion ? 0.4 : 1)
        let height = max(size.height, 1)
        for star in stars {
            var y = star.node.position.y - star.speed * factor
            if y < -4 {
                y += height + 8
                star.node.position.x = CGFloat.random(in: 0...max(size.width, 1))
            }
            star.node.position.y = y
        }
        for line in gridLines {
            var y = line.position.y - 70 * factor
            if y < 0 { y += height }
            line.position.y = y
        }
    }

    // MARK: HUD

    private func pushHUD() {
        var hud = HUDState()
        hud.score = engine.displayScore
        hud.multiplier = engine.multiplier
        hud.comboProgress = (engine.comboProgress * 20).rounded() / 20
        hud.elapsed = Int(engine.elapsed)
        let tenths = Int((engine.clock * 10).rounded(.up))
        hud.clockTenths = engine.clock > 10 ? Int((engine.clock).rounded(.up)) * 10 : tenths
        hud.level = engine.level
        hud.hasShield = engine.hasShield
        hud.isCountdown = engine.phase == .countdown
        hud.powerUps = TimedPowerUp.allCases.compactMap { powerUp -> ActivePowerUpDisplay? in
            guard let remaining = engine.activePowerUps[powerUp] else { return nil }
            let fraction = ((remaining / powerUp.duration) * 20).rounded(.up) / 20
            return ActivePowerUpDisplay(kind: powerUp.pickup, fraction: fraction)
        }
        if hud != lastHUD {
            lastHUD = hud
            session?.updateHUD(hud)
        }
    }

    // MARK: Events → feedback

    private func handle(_ events: [GameEvent]) {
        for event in events {
            switch event {
            case .countdownTick(let number):
                showBanner("\(number)", size: 96, color: .white, duration: 0.8)
                SoundManager.shared.play(.countdown)
                HapticsManager.shared.play(.light)

            case .go:
                showBanner("GO!", size: 84, color: theme.accent, duration: 0.7)
                SoundManager.shared.play(.go)
                HapticsManager.shared.play(.medium)

            case .starCollected(let position, let points):
                burst(at: position, color: PickupKind.star.color, count: 14, speed: 120)
                floatingText("+\(points)", at: position, color: PickupKind.star.color, size: 20)
                SoundManager.shared.play(.star)
                HapticsManager.shared.play(.soft)

            case .timeCrystal(let position, let seconds):
                burst(at: position, color: PickupKind.timeCrystal.color, count: 18, speed: 140)
                floatingText("+\(Int(seconds))s", at: position, color: PickupKind.timeCrystal.color, size: 26)
                SoundManager.shared.play(.crystal)
                HapticsManager.shared.play(.light)

            case .powerUpCollected(let kind, let position):
                burst(at: position, color: kind.color, count: 26, speed: 180)
                shockwave(at: position, color: kind.color, radius: 90)
                showBanner(kind.title.uppercased(), size: 40, color: kind.color, duration: 1.0)
                SoundManager.shared.play(.powerUp)
                HapticsManager.shared.play(.medium)

            case .powerUpExpired(let powerUp):
                floatingText("\(powerUp.pickup.title) ended", at: engine.playerPosition + Vec2(0, 40),
                             color: RGBColor(hex: 0xAAAAAA), size: 14)

            case .nearMiss(let position, let points, let perfect):
                let color = perfect ? RGBColor(hex: 0xFF4FD8) : theme.accent
                let midpoint = Vec2((position.x + engine.playerPosition.x) / 2, engine.playerPosition.y + 30)
                floatingText(perfect ? "PERFECT +\(points)" : "CLOSE +\(points)", at: midpoint, color: color,
                             size: perfect ? 22 : 18)
                burst(at: midpoint, color: color, count: perfect ? 12 : 6, speed: 90)
                SoundManager.shared.play(perfect ? .perfect : .nearMiss)
                HapticsManager.shared.play(perfect ? .rigid : .light)

            case .shieldBroken(let position):
                shockwave(at: engine.playerPosition, color: PickupKind.shield.color, radius: 120)
                burst(at: position, color: PickupKind.shield.color, count: 30, speed: 220)
                shake(intensity: 8, duration: 0.25)
                showBanner("SHIELD BROKEN", size: 30, color: PickupKind.shield.color, duration: 0.9)
                SoundManager.shared.play(.shieldBreak)
                HapticsManager.shared.play(.heavy)

            case .hit(let position, let fatal, let penalty):
                if fatal {
                    playerDeath(at: position)
                } else {
                    flash(color: hazardColor, alpha: 0.35)
                    shake(intensity: 10, duration: 0.3)
                    burst(at: engine.playerPosition, color: hazardColor, count: 24, speed: 200)
                    if penalty > 0 {
                        floatingText("-\(Int(penalty))s", at: engine.playerPosition + Vec2(0, 50),
                                     color: hazardColor, size: 30)
                    }
                    SoundManager.shared.play(.hit)
                    HapticsManager.shared.play(.heavy)
                }

            case .hazardDestroyed(let position, let radius, let kind):
                let color = (kind == .speeder || kind == .splitter || kind == .fragment) ? hazardAltColor : hazardColor
                burst(at: position, color: color, count: max(8, Int(radius)), speed: 160)

            case .nova(let position, let cleared, let points):
                shockwave(at: position, color: PickupKind.nova.color, radius: 700)
                flash(color: PickupKind.nova.color, alpha: 0.4)
                shake(intensity: 12, duration: 0.4)
                if cleared > 0 {
                    showBanner("NOVA ×\(cleared)  +\(points)", size: 36, color: PickupKind.nova.color, duration: 1.2)
                }
                SoundManager.shared.play(.nova)
                HapticsManager.shared.play(.heavy)

            case .split(let position):
                burst(at: position, color: hazardAltColor, count: 10, speed: 120)

            case .warning:
                SoundManager.shared.play(.warning)

            case .levelUp(let level):
                showBanner("LEVEL \(level)", size: 52, color: theme.accent, duration: 1.3)
                SoundManager.shared.play(.levelUp)
                HapticsManager.shared.play(.success)

            case .multiplierUp(let multiplier):
                floatingText("×\(multiplier) MULTIPLIER", at: engine.playerPosition + Vec2(0, 70),
                             color: RGBColor(hex: 0xFFD84D), size: 22)
                SoundManager.shared.play(.multiplier)
                HapticsManager.shared.play(.medium)

            case .clockTick:
                SoundManager.shared.play(.tick)
                HapticsManager.shared.play(.light)

            case .finished:
                if engine.config.mode == .timeAttack {
                    showBanner("TIME!", size: 72, color: theme.accent, duration: 1.0)
                    SoundManager.shared.play(.gameOver)
                    HapticsManager.shared.play(.warning)
                } else if engine.config.mode == .zen {
                    showBanner("NICE FLOW", size: 56, color: theme.accent, duration: 1.0)
                    SoundManager.shared.play(.levelUp)
                }
            }
        }
    }

    private func playerDeath(at hazardPosition: Vec2) {
        let position = engine.playerPosition
        playerNode.isHidden = true
        shieldNode.isHidden = true
        trailEmitter?.particleBirthRate = 0
        burst(at: position, color: skin.glow, count: 60, speed: 320, lifetime: 0.9)
        burst(at: position, color: hazardColor, count: 40, speed: 240, lifetime: 0.7)
        shockwave(at: position, color: hazardColor, radius: 220)
        flash(color: hazardColor, alpha: 0.55)
        shake(intensity: 16, duration: 0.5)
        SoundManager.shared.play(.hit)
        SoundManager.shared.play(.gameOver)
        HapticsManager.shared.play(.error)
    }

    // MARK: Effects

    private func burst(at position: Vec2, color: RGBColor, count: Int, speed: CGFloat, lifetime: CGFloat = 0.55) {
        let particles = settings.reducedMotion ? max(4, count / 2) : count
        let emitter = SKEmitterNode()
        emitter.particleTexture = TextureFactory.softDot
        emitter.particleColor = color.uiColor
        emitter.particleColorBlendFactor = 1
        emitter.particleBlendMode = .add
        emitter.numParticlesToEmit = particles
        emitter.particleBirthRate = CGFloat(particles) * 40
        emitter.particleLifetime = lifetime
        emitter.particleLifetimeRange = lifetime * 0.4
        emitter.particleSpeed = speed
        emitter.particleSpeedRange = speed * 0.6
        emitter.emissionAngleRange = .pi * 2
        emitter.particleScale = 0.55
        emitter.particleScaleRange = 0.3
        emitter.particleScaleSpeed = -0.7
        emitter.particleAlphaSpeed = -1.4
        emitter.position = position.cgPoint
        effectLayer.addChild(emitter)
        emitter.run(.sequence([.wait(forDuration: Double(lifetime) * 1.6 + 0.2), .removeFromParent()]))
    }

    private func floatingText(_ text: String, at position: Vec2, color: RGBColor, size: CGFloat) {
        let label = SKLabelNode(text: text)
        label.fontName = "AvenirNext-Heavy"
        label.fontSize = size
        label.fontColor = color.uiColor
        label.position = position.cgPoint
        label.verticalAlignmentMode = .center
        label.setScale(0.6)
        effectLayer.addChild(label)
        let rise = SKAction.moveBy(x: 0, y: 46, duration: 0.8)
        rise.timingMode = .easeOut
        label.run(.sequence([
            .group([rise, .sequence([.scale(to: 1.1, duration: 0.12), .scale(to: 1, duration: 0.1)]),
                    .sequence([.wait(forDuration: 0.45), .fadeOut(withDuration: 0.35)])]),
            .removeFromParent()
        ]))
    }

    private func showBanner(_ text: String, size: CGFloat, color: RGBColor, duration: TimeInterval) {
        bannerLayer.children.filter { $0.name == "banner" }.forEach { $0.removeFromParent() }
        let label = SKLabelNode(text: text)
        label.name = "banner"
        label.fontName = "AvenirNext-Heavy"
        label.fontSize = size
        label.fontColor = color.uiColor
        label.verticalAlignmentMode = .center
        label.alpha = 0
        label.setScale(1.6)

        let glow = SKLabelNode(text: text)
        glow.fontName = "AvenirNext-Heavy"
        glow.fontSize = size
        glow.fontColor = color.uiColor
        glow.verticalAlignmentMode = .center
        glow.alpha = 0.35
        glow.setScale(1.08)
        glow.zPosition = -1
        label.addChild(glow)

        bannerLayer.addChild(label)
        label.run(.sequence([
            .group([.fadeIn(withDuration: 0.12), .scale(to: 1, duration: 0.18)]),
            .wait(forDuration: max(0.1, duration - 0.4)),
            .group([.fadeOut(withDuration: 0.25), .scale(to: 0.85, duration: 0.25)]),
            .removeFromParent()
        ]))
    }

    private func shockwave(at position: Vec2, color: RGBColor, radius: CGFloat) {
        guard !settings.reducedMotion || radius < 300 else { return }
        let ring = SKShapeNode(circleOfRadius: 10)
        ring.strokeColor = color.uiColor
        ring.lineWidth = 3
        ring.glowWidth = 3
        ring.fillColor = .clear
        ring.position = position.cgPoint
        effectLayer.addChild(ring)
        let expand = SKAction.scale(to: radius / 10, duration: 0.45)
        expand.timingMode = .easeOut
        ring.run(.sequence([.group([expand, .fadeOut(withDuration: 0.45)]), .removeFromParent()]))
    }

    private func flash(color: RGBColor, alpha: CGFloat) {
        let strength = settings.reducedMotion ? alpha * 0.4 : alpha
        flashNode.removeAllActions()
        flashNode.color = color.uiColor
        flashNode.alpha = strength
        flashNode.run(.fadeOut(withDuration: 0.35))
    }

    private func shake(intensity: CGFloat, duration: TimeInterval) {
        guard !settings.reducedMotion else { return }
        shakeNode.removeAction(forKey: "shake")
        let steps = max(2, Int(duration / 0.04))
        var actions: [SKAction] = []
        for step in 0..<steps {
            let falloff = 1 - CGFloat(step) / CGFloat(steps)
            let offset = CGPoint(x: CGFloat.random(in: -1...1) * intensity * falloff,
                                 y: CGFloat.random(in: -1...1) * intensity * falloff)
            actions.append(.move(to: offset, duration: 0.04))
        }
        actions.append(.move(to: .zero, duration: 0.04))
        shakeNode.run(.sequence(actions), withKey: "shake")
    }
}
