//
//  GameEngine.swift
//  VelocityRush
//
//  The complete gameplay simulation: spawning, movement, collisions,
//  near misses, combos, power-ups, scoring and mode rules.
//
//  The engine knows nothing about rendering. `GameScene` feeds it the
//  player's target position and frame delta, then draws whatever is in
//  `hazards`, `pickups` and `warnings` and reacts to drained `GameEvent`s.
//  Because all randomness comes from seeded generators and spawning only
//  depends on elapsed time, a Daily Run seed produces the same hazards for
//  every player.
//

import Foundation

// MARK: - Playfield

struct Playfield: Equatable {
    static let logicalWidth: Double = 400

    let width: Double
    let height: Double

    /// Builds a playfield with a fixed logical width whose height follows the
    /// device aspect ratio (clamped so every device gets a fair fall distance).
    static func fitting(viewWidth: Double, viewHeight: Double) -> Playfield {
        guard viewWidth > 0, viewHeight > 0 else { return Playfield(width: logicalWidth, height: 820) }
        let height = clamp(logicalWidth * viewHeight / viewWidth, 720, 880)
        return Playfield(width: logicalWidth, height: height)
    }

    var playerMinY: Double { 70 }
    var playerMaxY: Double { height * 0.6 }
    var playerStart: Vec2 { Vec2(width / 2, 130) }
}

// MARK: - Entities

struct Hazard: Identifiable {
    let id: Int
    let kind: HazardKind
    var position: Vec2
    var velocity: Vec2
    var radius: Double
    var baseX: Double
    var wobbleAmplitude: Double = 0
    var wobbleFrequency: Double = 0
    var phase: Double = 0
    var age: Double = 0
    /// Smallest edge-to-edge distance to the player so far.
    var closestGap: Double = .infinity
    /// True once the hazard has passed the player (dodge/near miss evaluated).
    var resolved = false
    /// True if it overlapped the player while invulnerable.
    var touched = false
}

struct Pickup: Identifiable {
    let id: Int
    let kind: PickupKind
    var position: Vec2
    var velocity: Vec2
    var radius: Double
    var age: Double = 0
}

struct HazardWarning: Identifiable {
    let id: Int
    let x: Double
    var remaining: Double
    let total: Double
    let radius: Double
    let speed: Double
}

// MARK: - Events

enum GameEvent: Equatable {
    case countdownTick(Int)
    case go
    case starCollected(position: Vec2, points: Int)
    case timeCrystal(position: Vec2, seconds: Double)
    case powerUpCollected(PickupKind, position: Vec2)
    case powerUpExpired(TimedPowerUp)
    case nearMiss(position: Vec2, points: Int, perfect: Bool)
    case shieldBroken(position: Vec2)
    case hit(position: Vec2, fatal: Bool, penalty: Double)
    case hazardDestroyed(position: Vec2, radius: Double, kind: HazardKind)
    case nova(position: Vec2, cleared: Int, points: Int)
    case split(position: Vec2)
    case warning(x: Double)
    case levelUp(Int)
    case multiplierUp(Int)
    case clockTick(Int)
    case finished
}

// MARK: - Run statistics

struct RunStats: Equatable {
    var stars = 0
    var nearMisses = 0
    var perfectMisses = 0
    var dodged = 0
    var powerUps = 0
    var hits = 0
    var shieldsUsed = 0
    var crystals = 0
    var maxCombo = 0
    var maxMultiplier = 1
    var bestNovaClear = 0
}

// MARK: - Engine

final class GameEngine {

    enum Phase: Equatable {
        case countdown
        case running
        case finished
    }

    // Tunables
    static let countdownDuration: Double = 3.0
    static let comboWindow: Double = 3.0
    static let timeAttackStartClock: Double = 60
    static let timeAttackHitPenalty: Double = 5
    static let timeCrystalBonus: Double = 3
    static let levelDuration: Double = 15
    static let nearMissThreshold: Double = 18
    static let perfectMissThreshold: Double = 7
    static let basePlayerRadius: Double = 14
    static let maxDeltaTime: Double = 1.0 / 20.0

    let config: RunConfig
    let playfield: Playfield

    private(set) var phase: Phase = .countdown
    private(set) var countdownRemaining: Double = GameEngine.countdownDuration
    private(set) var elapsed: Double = 0
    private(set) var clock: Double = GameEngine.timeAttackStartClock
    private(set) var score: Double = 0
    private(set) var combo = 0
    private(set) var comboTimer: Double = 0
    private(set) var level = 1
    private(set) var stats = RunStats()

    private(set) var playerPosition: Vec2
    private(set) var playerTarget: Vec2
    private(set) var hasShield = false
    private(set) var invulnerability: Double = 0
    private(set) var activePowerUps: [TimedPowerUp: Double] = [:]

    private(set) var hazards: [Hazard] = []
    private(set) var pickups: [Pickup] = []
    private(set) var warnings: [HazardWarning] = []

    private var events: [GameEvent] = []
    private var nextID = 1
    private var lastCountdownTick = Int.max
    private var lastClockTick = Int.max

    private var hazardRNG: SeededRandom
    private var pickupRNG: SeededRandom
    private var powerRNG: SeededRandom

    private var hazardTimer: Double = 0.6
    private var starTimer: Double = 1.0
    private var powerUpTimer: Double = 8.0
    private var crystalTimer: Double = 5.0
    private var wallTimer: Double = 16.0

    init(config: RunConfig, playfield: Playfield) {
        self.config = config
        self.playfield = playfield
        var root = SeededRandom(seed: config.seed)
        hazardRNG = root.fork(salt: 0xA11CE)
        pickupRNG = root.fork(salt: 0xB0B)
        powerRNG = root.fork(salt: 0xC0FFEE)
        playerPosition = playfield.playerStart
        playerTarget = playfield.playerStart
        if config.mode == .timeAttack {
            powerUpTimer = 6.0
        }
    }

    // MARK: Derived values

    var displayScore: Int { Int(score.rounded(.down)) }

    var multiplier: Int { 1 + min(combo, 40) / 8 }

    var comboProgress: Double {
        combo > 0 ? clamp(comboTimer / GameEngine.comboWindow, 0, 1) : 0
    }

    var isSlowMotion: Bool { activePowerUps[.slowMo] != nil }

    var playerRadius: Double {
        var radius = GameEngine.basePlayerRadius
        if config.modifier == .tinyHero { radius *= 0.6 }
        if activePowerUps[.shrink] != nil { radius *= 0.55 }
        return radius
    }

    /// Collision radius is a little smaller than the visual for fairness.
    var playerHitRadius: Double { playerRadius * 0.82 }

    var isFinished: Bool { phase == .finished }

    /// 0 at the start, 1 at "full speed" and slowly beyond.
    var intensity: Double {
        var t: Double
        switch config.mode {
        case .endless, .daily:
            t = elapsed / 150
        case .timeAttack:
            t = 0.25 + elapsed / 110
        case .zen:
            t = min(elapsed / 240, 0.55)
        }
        if t > 1 { t = 1 + (t - 1) * 0.35 }
        return min(t, 1.6)
    }

    // MARK: Input

    /// Sets where the player should move to (in playfield coordinates).
    func setTarget(_ point: Vec2) {
        let r = playerRadius + 4
        playerTarget = Vec2(clamp(point.x, r, playfield.width - r),
                            clamp(point.y, playfield.playerMinY, playfield.playerMaxY))
    }

    func drainEvents() -> [GameEvent] {
        defer { events.removeAll(keepingCapacity: true) }
        return events
    }

    // MARK: Simulation

    func step(_ rawDelta: Double) {
        guard phase != .finished else { return }
        let dt = clamp(rawDelta, 0, GameEngine.maxDeltaTime)
        guard dt > 0 else { return }

        movePlayer(dt)

        switch phase {
        case .countdown:
            countdownRemaining -= dt
            let tick = Int(countdownRemaining.rounded(.up))
            if tick < lastCountdownTick && tick > 0 {
                lastCountdownTick = tick
                events.append(.countdownTick(tick))
            }
            if countdownRemaining <= 0 {
                countdownRemaining = 0
                phase = .running
                events.append(.go)
            }
        case .running:
            simulate(dt)
        case .finished:
            break
        }
    }

    private func movePlayer(_ dt: Double) {
        let follow = min(1, dt * 22)
        playerPosition += (playerTarget - playerPosition) * follow
        // Keep inside the bounds even if the radius changed (shrink ended).
        let r = playerRadius + 4
        playerPosition.x = clamp(playerPosition.x, r, playfield.width - r)
    }

    private func simulate(_ dt: Double) {
        elapsed += dt
        let worldDelta = dt * (isSlowMotion ? 0.45 : 1.0)

        updateTimers(dt)
        updateLevel()
        updateScoreAndClock(dt)
        if phase == .finished { return }

        spawnHazards(worldDelta)
        spawnPickups(worldDelta)
        updateWarnings(worldDelta)
        updateHazards(worldDelta)
        if phase == .finished { return }
        updatePickups(worldDelta, realDelta: dt)
    }

    private func updateTimers(_ dt: Double) {
        if invulnerability > 0 { invulnerability = max(0, invulnerability - dt) }

        if combo > 0 {
            comboTimer -= dt
            if comboTimer <= 0 {
                combo = 0
                comboTimer = 0
            }
        }

        for (powerUp, remaining) in activePowerUps {
            let next = remaining - dt
            if next <= 0 {
                activePowerUps[powerUp] = nil
                events.append(.powerUpExpired(powerUp))
            } else {
                activePowerUps[powerUp] = next
            }
        }
    }

    private func updateLevel() {
        let newLevel = 1 + Int(elapsed / GameEngine.levelDuration)
        if newLevel > level {
            level = newLevel
            if config.mode == .endless || config.mode == .daily {
                events.append(.levelUp(newLevel))
            }
        }
    }

    private func updateScoreAndClock(_ dt: Double) {
        switch config.mode {
        case .endless, .daily:
            score += 10 * dt * config.scoreMultiplier
        case .timeAttack:
            clock -= dt
            let whole = Int(clock.rounded(.up))
            if whole <= 5 && whole > 0 && whole < lastClockTick {
                lastClockTick = whole
                events.append(.clockTick(whole))
            }
            if whole > 5 { lastClockTick = Int.max }
            if clock <= 0 {
                clock = 0
                finish()
            }
        case .zen:
            break
        }
    }

    // MARK: Spawning – hazards

    private var hazardSpeed: Double {
        let t = intensity
        var speed = lerp(230, 460, min(t, 1))
        if t > 1 { speed += (t - 1) * 120 }
        speed *= config.difficulty.speedMultiplier
        if config.modifier == .speedDemon { speed *= 1.35 }
        return speed
    }

    private var hazardInterval: Double {
        let t = intensity
        var interval = lerp(1.05, 0.30, min(t, 1))
        if t > 1 { interval = max(0.22, 0.30 - (t - 1) * 0.1) }
        interval *= config.difficulty.spawnIntervalMultiplier
        if config.modifier == .tinyHero { interval *= 0.75 }
        return interval
    }

    private var hazardBaseRadius: Double {
        var radius = lerp(10, 22, min(intensity, 1))
        if config.modifier == .giantDots { radius *= 1.5 }
        return radius
    }

    private func spawnHazards(_ dt: Double) {
        hazardTimer -= dt
        if hazardTimer <= 0 {
            spawnRandomHazard()
            hazardTimer += hazardInterval * hazardRNG.range(0.75, 1.25)
            // Never let a long frame queue up a burst.
            hazardTimer = max(hazardTimer, 0.05)
        }

        let wallsEnabled = (config.mode == .endless || config.mode == .daily) && intensity > 0.55
        if wallsEnabled {
            wallTimer -= dt
            if wallTimer <= 0 {
                spawnWall()
                wallTimer = hazardRNG.range(14, 22)
                hazardTimer = max(hazardTimer, 0.9)
            }
        }
    }

    private func nextKind() -> HazardKind {
        let t = intensity
        var weights: [(HazardKind, Double)] = [
            (.drop, 1.0),
            (.wobbler, t > 0.08 ? 0.35 : 0),
            (.speeder, t > 0.18 ? 0.25 : 0),
            (.giant, t > 0.30 ? 0.15 : 0),
            (.splitter, t > 0.40 ? 0.18 : 0)
        ]
        switch config.modifier {
        case .wobbleWorld:
            weights[0].1 = 0
            weights[1].1 = 1.0
        case .meteorShower:
            weights[2].1 = 0.8
        default:
            break
        }
        return hazardRNG.weighted(weights) ?? .drop
    }

    private func spawnRandomHazard() {
        let kind = nextKind()
        let speed = hazardSpeed * hazardRNG.range(0.88, 1.12)
        var radius = hazardBaseRadius * hazardRNG.range(0.75, 1.3)

        switch kind {
        case .speeder:
            radius = hazardRNG.range(7, 9.5) * (config.modifier == .giantDots ? 1.5 : 1)
            let x = hazardRNG.range(radius + 8, playfield.width - radius - 8)
            let warning = HazardWarning(id: makeID(), x: x, remaining: 0.7, total: 0.7,
                                        radius: radius, speed: speed * 2.1)
            warnings.append(warning)
            events.append(.warning(x: x))
            return
        case .giant:
            radius = max(30, radius * 2.0)
        case .splitter:
            radius = max(16, radius * 1.4)
        default:
            break
        }

        let margin = radius + 4
        let x = hazardRNG.range(margin, playfield.width - margin)
        var velocity = Vec2(0, -speed)
        var hazard = Hazard(id: makeID(), kind: kind,
                            position: Vec2(x, playfield.height + radius + 10),
                            velocity: velocity, radius: radius, baseX: x)

        switch kind {
        case .wobbler:
            let amplitude = hazardRNG.range(28, 70)
            hazard.wobbleAmplitude = amplitude
            hazard.wobbleFrequency = hazardRNG.range(1.6, 3.2)
            hazard.phase = hazardRNG.range(0, 2 * .pi)
            hazard.baseX = clamp(x, amplitude + radius, playfield.width - amplitude - radius)
            hazard.position.x = hazard.baseX + amplitude * sin(hazard.phase)
        case .giant:
            velocity.y *= 0.6
            hazard.velocity = velocity
        case .splitter:
            velocity.y *= 0.85
            hazard.velocity = velocity
        default:
            break
        }
        hazards.append(hazard)
    }

    private func spawnWall() {
        let radius = 13.0 * (config.modifier == .giantDots ? 1.3 : 1)
        let spacing = radius * 2 + 10
        let gapWidth = GameEngine.basePlayerRadius * 2 * 3.4
        let gapCenter = hazardRNG.range(gapWidth, playfield.width - gapWidth)
        let speed = hazardSpeed * 0.75
        var x = radius + 2
        while x < playfield.width - radius {
            if abs(x - gapCenter) > gapWidth / 2 {
                hazards.append(Hazard(id: makeID(), kind: .drop,
                                      position: Vec2(x, playfield.height + radius + 10),
                                      velocity: Vec2(0, -speed), radius: radius, baseX: x))
            }
            x += spacing
        }
    }

    private func updateWarnings(_ dt: Double) {
        var index = 0
        while index < warnings.count {
            warnings[index].remaining -= dt
            if warnings[index].remaining <= 0 {
                let warning = warnings.remove(at: index)
                hazards.append(Hazard(id: makeID(), kind: .speeder,
                                      position: Vec2(warning.x, playfield.height + warning.radius + 10),
                                      velocity: Vec2(0, -warning.speed),
                                      radius: warning.radius, baseX: warning.x))
            } else {
                index += 1
            }
        }
    }

    // MARK: Spawning – pickups

    private func spawnPickups(_ dt: Double) {
        // Stars
        starTimer -= dt
        if starTimer <= 0 {
            var interval: Double
            switch config.mode {
            case .endless, .daily: interval = pickupRNG.range(1.6, 2.6)
            case .timeAttack: interval = pickupRNG.range(0.55, 0.9)
            case .zen: interval = pickupRNG.range(1.0, 1.6)
            }
            if config.modifier == .starStorm { interval *= 0.35 }
            starTimer = interval
            spawnPickup(.star, radius: 11, speed: 150 + intensity * 70, rng: &pickupRNG)
        }

        // Time crystals (Time Attack only)
        if config.mode == .timeAttack {
            crystalTimer -= dt
            if crystalTimer <= 0 {
                crystalTimer = pickupRNG.range(6.5, 9.5)
                spawnPickup(.timeCrystal, radius: 13, speed: 170, rng: &pickupRNG)
            }
        }

        // Power-ups
        guard config.modifier != .purist else { return }
        powerUpTimer -= dt
        if powerUpTimer <= 0 {
            powerUpTimer = config.mode == .timeAttack ? powerRNG.range(8, 12) : powerRNG.range(11, 17)
            let options: [(PickupKind, Double)] = [
                (.shield, hasShield ? 0.3 : 1.0),
                (.slowMo, 0.8),
                (.magnet, 0.9),
                (.shrink, 0.7),
                (.nova, 0.55)
            ]
            let kind = powerRNG.weighted(options) ?? .shield
            spawnPickup(kind, radius: 16, speed: 140, rng: &powerRNG)
        }
    }

    private func spawnPickup(_ kind: PickupKind, radius: Double, speed: Double, rng: inout SeededRandom) {
        let x = rng.range(radius + 10, playfield.width - radius - 10)
        pickups.append(Pickup(id: makeID(), kind: kind,
                              position: Vec2(x, playfield.height + radius + 10),
                              velocity: Vec2(0, -speed), radius: radius))
    }

    // MARK: Hazards update

    private func updateHazards(_ dt: Double) {
        let hitRadius = playerHitRadius
        let visualRadius = playerRadius
        let splitLine = playfield.height * 0.52
        var spawned: [Hazard] = []
        var index = 0

        while index < hazards.count {
            var hazard = hazards[index]
            hazard.age += dt

            switch hazard.kind {
            case .wobbler:
                hazard.position.y += hazard.velocity.y * dt
                hazard.position.x = hazard.baseX + hazard.wobbleAmplitude * sin(hazard.phase + hazard.age * hazard.wobbleFrequency)
            case .fragment:
                hazard.position += hazard.velocity * dt
                if hazard.position.x < hazard.radius || hazard.position.x > playfield.width - hazard.radius {
                    hazard.velocity.x = -hazard.velocity.x
                    hazard.position.x = clamp(hazard.position.x, hazard.radius, playfield.width - hazard.radius)
                }
            default:
                hazard.position += hazard.velocity * dt
            }

            // Splitters break apart halfway down the screen.
            if hazard.kind == .splitter && hazard.position.y < splitLine {
                let childRadius = hazard.radius * 0.6
                for direction in [-1.0, 1.0] {
                    spawned.append(Hazard(id: makeID(), kind: .fragment,
                                          position: hazard.position + Vec2(direction * childRadius, 0),
                                          velocity: Vec2(direction * 95, hazard.velocity.y * 1.1),
                                          radius: childRadius, baseX: hazard.position.x))
                }
                events.append(.split(position: hazard.position))
                hazards.remove(at: index)
                continue
            }

            // Collision / near miss
            let distance = hazard.position.distance(to: playerPosition)
            let gap = distance - hazard.radius - hitRadius
            if gap < 0 {
                if invulnerability > 0 {
                    hazard.touched = true
                } else {
                    hazards.remove(at: index)
                    handleHit(by: hazard)
                    if phase == .finished { return }
                    continue
                }
            } else {
                hazard.closestGap = min(hazard.closestGap, distance - hazard.radius - visualRadius)
            }

            if !hazard.resolved && hazard.position.y + hazard.radius < playerPosition.y - visualRadius {
                hazard.resolved = true
                if !hazard.touched {
                    stats.dodged += 1
                    if hazard.closestGap < GameEngine.nearMissThreshold {
                        registerNearMiss(at: hazard.position, gap: hazard.closestGap)
                    }
                }
            }

            if hazard.position.y < -hazard.radius - 20 {
                hazards.remove(at: index)
                continue
            }

            hazards[index] = hazard
            index += 1
        }

        hazards.append(contentsOf: spawned)
    }

    private func registerNearMiss(at position: Vec2, gap: Double) {
        let perfect = gap < GameEngine.perfectMissThreshold
        stats.nearMisses += 1
        if perfect { stats.perfectMisses += 1 }
        let base: Double
        switch config.mode {
        case .timeAttack: base = perfect ? 60 : 30
        default: base = perfect ? 80 : 40
        }
        bumpCombo()
        let points = award(base)
        events.append(.nearMiss(position: position, points: points, perfect: perfect))
    }

    private func handleHit(by hazard: Hazard) {
        let position = hazard.position
        if hasShield {
            hasShield = false
            stats.shieldsUsed += 1
            invulnerability = 1.0
            events.append(.shieldBroken(position: position))
            events.append(.hazardDestroyed(position: position, radius: hazard.radius, kind: hazard.kind))
            return
        }

        stats.hits += 1
        switch config.mode {
        case .endless, .daily:
            events.append(.hit(position: position, fatal: true, penalty: 0))
            finish()
        case .timeAttack:
            clock = max(0, clock - GameEngine.timeAttackHitPenalty)
            combo = 0
            comboTimer = 0
            invulnerability = 1.5
            events.append(.hit(position: position, fatal: false, penalty: GameEngine.timeAttackHitPenalty))
            events.append(.hazardDestroyed(position: position, radius: hazard.radius, kind: hazard.kind))
            if clock <= 0 { finish() }
        case .zen:
            combo = 0
            comboTimer = 0
            invulnerability = 1.0
            events.append(.hit(position: position, fatal: false, penalty: 0))
            events.append(.hazardDestroyed(position: position, radius: hazard.radius, kind: hazard.kind))
        }
    }

    // MARK: Pickups update

    private func updatePickups(_ dt: Double, realDelta: Double) {
        let magnetActive = activePowerUps[.magnet] != nil
        let collectRadius = max(playerRadius, 12) * 1.15
        var index = 0

        while index < pickups.count {
            var pickup = pickups[index]
            pickup.age += dt

            let toPlayer = playerPosition - pickup.position
            let distance = toPlayer.length
            let attractable = pickup.kind == .star || pickup.kind == .timeCrystal
            if magnetActive && attractable && distance < 230 {
                pickup.position += toPlayer.normalized * (560 * realDelta)
            } else {
                pickup.position += pickup.velocity * dt
                pickup.position.x += sin(pickup.age * 3 + Double(pickup.id)) * 18 * dt
            }

            if pickup.position.distance(to: playerPosition) < pickup.radius + collectRadius {
                pickups.remove(at: index)
                collect(pickup)
                continue
            }

            if pickup.position.y < -pickup.radius - 20 {
                pickups.remove(at: index)
                continue
            }

            pickups[index] = pickup
            index += 1
        }
    }

    private func collect(_ pickup: Pickup) {
        let position = pickup.position
        switch pickup.kind {
        case .star:
            stats.stars += 1
            let base: Double
            switch config.mode {
            case .timeAttack: base = 50
            case .zen: base = 10
            default: base = 25
            }
            bumpCombo()
            let points = award(base)
            events.append(.starCollected(position: position, points: points))
        case .timeCrystal:
            stats.crystals += 1
            clock = min(99, clock + GameEngine.timeCrystalBonus)
            events.append(.timeCrystal(position: position, seconds: GameEngine.timeCrystalBonus))
        case .shield:
            hasShield = true
            stats.powerUps += 1
            events.append(.powerUpCollected(.shield, position: position))
        case .slowMo:
            activate(.slowMo, at: position)
        case .magnet:
            activate(.magnet, at: position)
        case .shrink:
            activate(.shrink, at: position)
        case .nova:
            stats.powerUps += 1
            events.append(.powerUpCollected(.nova, position: position))
            triggerNova(at: position)
        }
    }

    private func activate(_ powerUp: TimedPowerUp, at position: Vec2) {
        stats.powerUps += 1
        activePowerUps[powerUp] = powerUp.duration
        events.append(.powerUpCollected(powerUp.pickup, position: position))
    }

    private func triggerNova(at position: Vec2) {
        let cleared = hazards.count
        for hazard in hazards {
            events.append(.hazardDestroyed(position: hazard.position, radius: hazard.radius, kind: hazard.kind))
        }
        hazards.removeAll()
        warnings.removeAll()
        stats.bestNovaClear = max(stats.bestNovaClear, cleared)
        let points = cleared > 0 ? award(15 * Double(cleared)) : 0
        events.append(.nova(position: playerPosition, cleared: cleared, points: points))
    }

    // MARK: Scoring helpers

    private func bumpCombo() {
        let previousMultiplier = multiplier
        combo += 1
        comboTimer = GameEngine.comboWindow
        stats.maxCombo = max(stats.maxCombo, combo)
        let newMultiplier = multiplier
        stats.maxMultiplier = max(stats.maxMultiplier, newMultiplier)
        if newMultiplier > previousMultiplier {
            events.append(.multiplierUp(newMultiplier))
        }
    }

    /// Adds points using the current multiplier and run multipliers. Returns the points added.
    @discardableResult
    private func award(_ base: Double) -> Int {
        let points = base * Double(multiplier) * config.scoreMultiplier
        score += points
        return Int(points.rounded())
    }

    private func finish() {
        guard phase != .finished else { return }
        phase = .finished
        events.append(.finished)
    }

    /// Ends the run early (e.g. quitting Zen mode from the pause menu).
    func endRun() {
        finish()
    }

    private func makeID() -> Int {
        defer { nextID += 1 }
        return nextID
    }

    // MARK: Result

    func makeResult(date: Date = Date()) -> RunResult {
        RunResult(mode: config.mode,
                  difficulty: config.difficulty,
                  modifier: config.modifier,
                  dailyKey: config.dailyKey,
                  score: displayScore,
                  duration: elapsed,
                  stars: stats.stars,
                  nearMisses: stats.nearMisses,
                  perfectMisses: stats.perfectMisses,
                  dodged: stats.dodged,
                  powerUps: stats.powerUps,
                  hits: stats.hits,
                  maxCombo: stats.maxCombo,
                  maxMultiplier: stats.maxMultiplier,
                  bestNovaClear: stats.bestNovaClear,
                  levelReached: level,
                  date: date)
    }
}
