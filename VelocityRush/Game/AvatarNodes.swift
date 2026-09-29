//
//  AvatarNodes.swift
//  VelocityRush
//
//  The player's look, shared by the game and the shop's live preview:
//  • PlayerAvatarNode – the skin plus animated effects (orbiters, aura…)
//  • RibbonTrailNode  – a Fruit Ninja–style tapered blade trail
//

import SpriteKit
import UIKit

// MARK: - Player avatar

final class PlayerAvatarNode: SKNode {
    let look: SkinLook
    let radius: CGFloat

    private let body = SKSpriteNode()
    private var glowNode: SKSpriteNode?
    private var orbitNode: SKNode?
    private var orbiters: [SKSpriteNode] = []
    private var ringNode: SKShapeNode?
    private var auraEmitter: SKEmitterNode?
    private var sparkleEmitter: SKEmitterNode?
    private var ghosts: [SKSpriteNode] = []
    private let reducedMotion: Bool

    var bodyTexture: SKTexture? { body.texture }
    var bodySize: CGSize { body.size }

    init(look: SkinLook, radius: CGFloat = CGFloat(GameEngine.basePlayerRadius), reducedMotion: Bool = false) {
        self.look = look
        self.radius = radius
        self.reducedMotion = reducedMotion
        super.init()
        build()
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func build() {
        let side = radius * 2 * TextureFactory.glowRatio
        body.texture = TextureFactory.skin(look)
        body.size = CGSize(width: side, height: side)
        body.zPosition = 2
        if look.style == .prism { body.colorBlendFactor = 1 }
        addChild(body)

        let accent = look.accentColor.uiColor

        if look.has(.pulse) {
            let glow = SKSpriteNode(texture: TextureFactory.softDot)
            glow.size = CGSize(width: radius * 5, height: radius * 5)
            glow.color = look.glow.uiColor
            glow.colorBlendFactor = 1
            glow.blendMode = .add
            glow.alpha = 0.45
            glow.zPosition = 0
            addChild(glow)
            if !reducedMotion {
                let grow = SKAction.group([.scale(to: 1.3, duration: 0.7), .fadeAlpha(to: 0.2, duration: 0.7)])
                let shrink = SKAction.group([.scale(to: 1.0, duration: 0.7), .fadeAlpha(to: 0.45, duration: 0.7)])
                grow.timingMode = .easeInEaseOut
                shrink.timingMode = .easeInEaseOut
                glow.run(.repeatForever(.sequence([grow, shrink])))
            }
            glowNode = glow
        }

        if look.has(.spinRing) {
            let ringRadius = radius * 1.75
            let circle = CGPath(ellipseIn: CGRect(x: -ringRadius, y: -ringRadius, width: ringRadius * 2, height: ringRadius * 2),
                                transform: nil)
            let ring = SKShapeNode(path: circle.copy(dashingWithPhase: 0, lengths: [7, 5]))
            ring.strokeColor = accent
            ring.lineWidth = 2
            ring.glowWidth = 1.5
            ring.zPosition = 3
            if !reducedMotion { ring.run(.repeatForever(.rotate(byAngle: -.pi * 2, duration: 3.2))) }
            addChild(ring)
            ringNode = ring
        }

        if look.has(.orbiters) {
            let orbit = SKNode()
            orbit.zPosition = 4
            for index in 0..<3 {
                let moon = SKSpriteNode(texture: TextureFactory.softDot)
                moon.size = CGSize(width: radius * 0.75, height: radius * 0.75)
                moon.color = accent
                moon.colorBlendFactor = 1
                moon.blendMode = .add
                let angle = CGFloat(index) * 2 * .pi / 3
                moon.position = CGPoint(x: cos(angle) * radius * 2.0, y: sin(angle) * radius * 2.0)
                let core = SKSpriteNode(texture: TextureFactory.hardDot)
                core.size = CGSize(width: radius * 0.3, height: radius * 0.3)
                moon.addChild(core)
                orbit.addChild(moon)
                orbiters.append(moon)
            }
            orbit.run(.repeatForever(.rotate(byAngle: .pi * 2, duration: reducedMotion ? 5 : 2.2)))
            addChild(orbit)
            orbitNode = orbit
        }

        if look.has(.aura) {
            let emitter = SKEmitterNode()
            emitter.particleTexture = TextureFactory.softDot
            emitter.particleColor = accent
            emitter.particleColorBlendFactor = 1
            emitter.particleBlendMode = .add
            emitter.particleBirthRate = reducedMotion ? 25 : 60
            emitter.particleLifetime = 0.5
            emitter.particleSpeed = 70
            emitter.particleSpeedRange = 30
            emitter.emissionAngle = -.pi / 2
            emitter.emissionAngleRange = 0.9
            emitter.particlePositionRange = CGVector(dx: radius, dy: radius)
            emitter.particleScale = 0.55
            emitter.particleScaleRange = 0.2
            emitter.particleScaleSpeed = -0.9
            emitter.particleAlphaSpeed = -2
            emitter.zPosition = 1
            addChild(emitter)
            auraEmitter = emitter
        }

        if look.has(.sparkle) {
            let emitter = SKEmitterNode()
            emitter.particleTexture = TextureFactory.tinyStar
            emitter.particleColor = look.has(.hueCycle) ? .white : accent
            emitter.particleColorBlendFactor = 1
            emitter.particleBlendMode = .add
            emitter.particleBirthRate = 7
            emitter.particleLifetime = 0.8
            emitter.particleSpeed = 8
            emitter.emissionAngleRange = .pi * 2
            emitter.particlePositionRange = CGVector(dx: radius * 3.2, dy: radius * 3.2)
            emitter.particleScale = 0.4
            emitter.particleScaleRange = 0.2
            emitter.particleRotationSpeed = 3
            emitter.particleAlphaSequence = SKKeyframeSequence(keyframeValues: [0, 1, 0], times: [0, 0.3, 1])
            emitter.zPosition = 5
            addChild(emitter)
            sparkleEmitter = emitter
        }

        if look.has(.glitch) {
            for tint in [RGBColor(hex: 0xFF2E6A), RGBColor(hex: 0x2EF2FF)] {
                let ghost = SKSpriteNode(texture: body.texture)
                ghost.size = body.size
                ghost.color = tint.uiColor
                ghost.colorBlendFactor = 1
                ghost.blendMode = .add
                ghost.alpha = 0.7
                ghost.zPosition = 1
                ghost.isHidden = true
                addChild(ghost)
                ghosts.append(ghost)
            }
        }
    }

    /// Particles that should be left behind in the world (aura) go into `node`.
    func setParticleTarget(_ node: SKNode?) {
        auraEmitter?.targetNode = node
    }

    func setEmitting(_ emitting: Bool) {
        auraEmitter?.particleBirthRate = emitting ? (reducedMotion ? 25 : 60) : 0
        sparkleEmitter?.particleBirthRate = emitting ? 7 : 0
    }

    /// Call once per frame.
    func update(time: Double) {
        if look.has(.hueCycle) {
            let hue = RGBColor.hsb(time * 0.35, 0.75, 1).uiColor
            if look.style == .prism { body.color = hue }
            ringNode?.strokeColor = hue
            orbiters.forEach { $0.color = hue }
            auraEmitter?.particleColor = hue
        }

        if !ghosts.isEmpty {
            // Pseudo-random glitch bursts, ~15% of the time.
            let slot = Int(time * 18)
            let roll = abs(sin(Double(slot) * 12.9898) * 43_758.5453).truncatingRemainder(dividingBy: 1)
            let active = roll > (reducedMotion ? 0.95 : 0.82)
            for (index, ghost) in ghosts.enumerated() {
                ghost.isHidden = !active
                guard active else { continue }
                let direction: CGFloat = index == 0 ? -1 : 1
                ghost.position = CGPoint(x: direction * radius * CGFloat(0.25 + roll * 0.3),
                                         y: CGFloat(roll - 0.9) * radius)
            }
        }
    }
}

// MARK: - Blade trail

final class RibbonTrailNode: SKNode {
    let look: TrailLook
    private let baseWidth: CGFloat
    private var points: [CGPoint] = []
    private var segments: [SKSpriteNode] = []
    private var emitter: SKEmitterNode?
    private weak var particleTarget: SKNode?
    private var ghostTimer: Double = 0
    private var emitterBirthRate: CGFloat = 0
    private let reducedMotion: Bool

    /// Texture/size used for Phantom afterimages.
    var ghostTexture: SKTexture?
    var ghostSize: CGSize = .zero

    init(look: TrailLook, playerRadius: CGFloat = CGFloat(GameEngine.basePlayerRadius), reducedMotion: Bool = false) {
        self.look = look
        self.baseWidth = playerRadius * 2
        self.reducedMotion = reducedMotion
        super.init()
        buildSegments()
        buildEmitter()
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func buildSegments() {
        guard look.hasRibbon else { return }
        for index in 0..<(look.length - 1) {
            let segment = SKSpriteNode(texture: TextureFactory.ribbonSegment)
            segment.colorBlendFactor = 1
            segment.blendMode = look.glow ? .add : .alpha
            segment.zPosition = CGFloat(-index) * 0.01
            segment.isHidden = true
            addChild(segment)
            segments.append(segment)
        }
    }

    private func buildEmitter() {
        guard look.particles != .none else { return }
        let emitter = SKEmitterNode()
        emitter.particleTexture = TextureFactory.softDot
        emitter.particleColor = look.primary.uiColor
        emitter.particleColorBlendFactor = 1
        emitter.particleBlendMode = .add
        emitter.emissionAngle = -.pi / 2
        let colors = look.colors.isEmpty ? [RGBColor.white] : look.colors

        switch look.particles {
        case .none:
            break
        case .sparks:
            emitter.particleBirthRate = 45
            emitter.particleLifetime = 0.4
            emitter.particleSpeed = 110
            emitter.particleSpeedRange = 50
            emitter.emissionAngleRange = 0.8
            emitter.particleScale = 0.3
            emitter.particleScaleRange = 0.15
            emitter.particleAlphaSpeed = -2.2
        case .embers:
            emitter.particleBirthRate = 55
            emitter.particleLifetime = 0.7
            emitter.particleSpeed = 70
            emitter.particleSpeedRange = 40
            emitter.emissionAngleRange = 1.0
            emitter.particleScale = 0.4
            emitter.particleScaleSpeed = -0.5
            emitter.particleAlphaSpeed = -1.4
            emitter.particleColorSequence = SKKeyframeSequence(
                keyframeValues: colors.map(\.uiColor),
                times: colors.indices.map { NSNumber(value: Double($0) / Double(max(colors.count - 1, 1))) })
        case .snow:
            emitter.particleTexture = TextureFactory.tinyStar
            emitter.particleBirthRate = 18
            emitter.particleLifetime = 1.0
            emitter.particleSpeed = 50
            emitter.particleSpeedRange = 30
            emitter.emissionAngleRange = 1.3
            emitter.particleScale = 0.35
            emitter.particleScaleRange = 0.2
            emitter.particleRotationSpeed = 2
            emitter.particleAlphaSpeed = -1.0
            emitter.particleColor = colors.count > 1 ? colors[1].uiColor : .white
        case .stars:
            emitter.particleTexture = TextureFactory.tinyStar
            emitter.particleBirthRate = 26
            emitter.particleLifetime = 0.8
            emitter.particleSpeed = 60
            emitter.particleSpeedRange = 30
            emitter.emissionAngleRange = .pi * 2
            emitter.particleScale = 0.45
            emitter.particleScaleRange = 0.2
            emitter.particleRotationSpeed = 4
            emitter.particleAlphaSpeed = -1.2
            emitter.particleColor = colors.count > 1 ? colors[1].uiColor : colors[0].uiColor
        case .bubbles:
            emitter.particleTexture = TextureFactory.bubble
            emitter.particleBirthRate = 12
            emitter.particleLifetime = 1.2
            emitter.particleSpeed = 40
            emitter.emissionAngleRange = 1.2
            emitter.particleScale = 0.35
            emitter.particleScaleSpeed = 0.3
            emitter.particleAlphaSpeed = -0.9
            emitter.particleBlendMode = .alpha
        case .zaps:
            emitter.particleBirthRate = 70
            emitter.particleLifetime = 0.18
            emitter.particleSpeed = 260
            emitter.particleSpeedRange = 120
            emitter.emissionAngleRange = .pi * 2
            emitter.particleScale = 0.25
            emitter.particleAlphaSpeed = -5
            emitter.particleColor = colors[0].lighter(0.5).uiColor
        }
        if reducedMotion { emitter.particleBirthRate *= 0.5 }
        emitterBirthRate = emitter.particleBirthRate
        emitter.zPosition = -1
        addChild(emitter)
        self.emitter = emitter
    }

    /// Particles and afterimages are left behind in `node` (usually the world).
    func setParticleTarget(_ node: SKNode?) {
        particleTarget = node
        emitter?.targetNode = node
    }

    func setEmitting(_ emitting: Bool) {
        emitter?.particleBirthRate = emitting ? emitterBirthRate : 0
    }

    func reset(at point: CGPoint) {
        points = Array(repeating: point, count: max(look.length, 1))
        segments.forEach { $0.isHidden = true }
        emitter?.resetSimulation()
        emitter?.position = point
    }

    /// Call once per frame with the head position. `drift` (points/s) pushes
    /// the tail downward so the blade streams behind the dot like it's flying.
    func update(head: CGPoint, dt: Double, time: Double, drift: CGFloat) {
        emitter?.position = head
        if look.mode == .rainbow {
            emitter?.particleColor = RGBColor.hsb(time * 0.45, 0.85, 1).uiColor
        }
        spawnAfterimage(at: head, dt: dt, drift: drift)
        guard look.hasRibbon else { return }

        if points.isEmpty { reset(at: head) }
        let fall = drift * CGFloat(dt)
        for index in points.indices { points[index].y -= fall }
        points.insert(head, at: 0)
        if points.count > look.length { points.removeLast(points.count - look.length) }

        let count = points.count
        for (index, segment) in segments.enumerated() {
            guard index + 1 < count else {
                segment.isHidden = true
                continue
            }
            var a = points[index]
            var b = points[index + 1]
            let t = Double(index) / Double(max(count - 1, 1))
            let width = baseWidth * CGFloat(look.width) * CGFloat(pow(1 - t, 0.75)) + 1

            if look.jitter > 0 && index > 0 {
                let amplitude = CGFloat(look.jitter) * width * 0.9
                a.x += CGFloat(sin(time * 53 + Double(index) * 2.3)) * amplitude
                b.x += CGFloat(sin(time * 53 + Double(index + 1) * 2.3)) * amplitude
            }

            let dx = b.x - a.x
            let dy = b.y - a.y
            let length = (dx * dx + dy * dy).squareRoot()
            guard length > 0.01 else {
                segment.isHidden = true
                continue
            }
            segment.isHidden = false
            segment.position = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
            segment.zRotation = atan2(dy, dx)
            segment.size = CGSize(width: length + width * 0.9, height: width)
            segment.color = look.color(at: t, index: index, time: time).uiColor
            segment.alpha = CGFloat(pow(1 - t, 1.1)) * 0.95
        }
    }

    private func spawnAfterimage(at head: CGPoint, dt: Double, drift: CGFloat) {
        guard look.afterimage, let texture = ghostTexture, let target = particleTarget else { return }
        ghostTimer -= dt
        guard ghostTimer <= 0 else { return }
        ghostTimer = reducedMotion ? 0.1 : 0.045
        let ghost = SKSpriteNode(texture: texture)
        ghost.size = ghostSize
        ghost.color = (look.colors.last ?? .white).uiColor
        ghost.colorBlendFactor = 0.6
        ghost.blendMode = .add
        ghost.alpha = 0.45
        ghost.position = convert(head, to: target)
        ghost.zPosition = zPosition - 0.5
        target.addChild(ghost)
        let fade = SKAction.group([.fadeOut(withDuration: 0.3),
                                   .moveBy(x: 0, y: -drift * 0.3, duration: 0.3),
                                   .scale(to: 0.8, duration: 0.3)])
        ghost.run(.sequence([fade, .removeFromParent()]))
    }
}
