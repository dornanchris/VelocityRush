//
//  GameEngine.kt
//  Starshower Run
//
//  The complete gameplay simulation: spawning, movement, collisions,
//  near misses, combos, power-ups, scoring and mode rules.
//
//  The engine knows nothing about rendering. The game renderer feeds it the
//  player's target position and frame delta, then draws whatever is in
//  `hazards`, `pickups` and `warnings` and reacts to drained `GameEvent`s.
//  Because all randomness comes from seeded generators and spawning only
//  depends on elapsed time, a Daily Run seed produces the same hazards for
//  every player.
//
//  Coordinates are y-up (like SpriteKit): hazards spawn above `height` and
//  fall towards y = 0. The renderer flips the axis when drawing.
//

package com.starshower.run.core.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

// MARK: - Playfield

data class Playfield(val width: Double, val height: Double) {
    val playerMinY: Double get() = 70.0
    val playerMaxY: Double get() = height * 0.6
    val playerStart: Vec2 get() = Vec2(width / 2, 130.0)

    companion object {
        const val LOGICAL_WIDTH = 400.0

        /**
         * Builds a playfield with a fixed logical width whose height follows the
         * device aspect ratio (clamped so every device gets a fair fall distance).
         */
        fun fitting(viewWidth: Double, viewHeight: Double): Playfield {
            if (viewWidth <= 0 || viewHeight <= 0) return Playfield(LOGICAL_WIDTH, 820.0)
            val height = clamp(LOGICAL_WIDTH * viewHeight / viewWidth, 720.0, 880.0)
            return Playfield(LOGICAL_WIDTH, height)
        }
    }
}

// MARK: - Entities

class Hazard(
    val id: Int,
    val kind: HazardKind,
    var position: Vec2,
    var velocity: Vec2,
    var radius: Double,
    var baseX: Double,
) {
    var wobbleAmplitude: Double = 0.0
    var wobbleFrequency: Double = 0.0
    var phase: Double = 0.0
    var age: Double = 0.0
    /** Smallest edge-to-edge distance to the player so far. */
    var closestGap: Double = Double.POSITIVE_INFINITY
    /** True once the hazard has passed the player (dodge/near miss evaluated). */
    var resolved: Boolean = false
    /** True if it overlapped the player while invulnerable. */
    var touched: Boolean = false
}

class Pickup(
    val id: Int,
    val kind: PickupKind,
    var position: Vec2,
    var velocity: Vec2,
    var radius: Double,
) {
    var age: Double = 0.0
}

class HazardWarning(
    val id: Int,
    val x: Double,
    var remaining: Double,
    val total: Double,
    val radius: Double,
    val speed: Double,
)

// MARK: - Events

sealed interface GameEvent {
    data class CountdownTick(val number: Int) : GameEvent
    data object Go : GameEvent
    data class StarCollected(val position: Vec2, val points: Int) : GameEvent
    data class TimeCrystal(val position: Vec2, val seconds: Double) : GameEvent
    data class PowerUpCollected(val kind: PickupKind, val position: Vec2) : GameEvent
    data class PowerUpExpired(val powerUp: TimedPowerUp) : GameEvent
    data class NearMiss(val position: Vec2, val points: Int, val perfect: Boolean) : GameEvent
    data class ShieldBroken(val position: Vec2) : GameEvent
    data class Hit(val position: Vec2, val fatal: Boolean, val penalty: Double) : GameEvent
    data class HazardDestroyed(val position: Vec2, val radius: Double, val kind: HazardKind) : GameEvent
    data class Nova(val position: Vec2, val cleared: Int, val points: Int) : GameEvent
    data class Split(val position: Vec2) : GameEvent
    data class Warning(val x: Double) : GameEvent
    data class LevelUp(val level: Int) : GameEvent
    data class MultiplierUp(val multiplier: Int) : GameEvent
    data class ClockTick(val seconds: Int) : GameEvent
    data object Finished : GameEvent
}

// MARK: - Run statistics

data class RunStats(
    var stars: Int = 0,
    var nearMisses: Int = 0,
    var perfectMisses: Int = 0,
    var dodged: Int = 0,
    var powerUps: Int = 0,
    var hits: Int = 0,
    var shieldsUsed: Int = 0,
    var crystals: Int = 0,
    var maxCombo: Int = 0,
    var maxMultiplier: Int = 1,
    var bestNovaClear: Int = 0,
)

// MARK: - Engine

class GameEngine(val config: RunConfig, val playfield: Playfield) {

    enum class Phase { COUNTDOWN, RUNNING, FINISHED }

    companion object {
        const val COUNTDOWN_DURATION = 3.0
        const val COMBO_WINDOW = 3.0
        const val TIME_ATTACK_START_CLOCK = 60.0
        const val TIME_ATTACK_HIT_PENALTY = 5.0
        const val TIME_CRYSTAL_BONUS = 3.0
        const val NEAR_MISS_THRESHOLD = 18.0
        const val PERFECT_MISS_THRESHOLD = 7.0
        const val BASE_PLAYER_RADIUS = 14.0
        const val MAX_DELTA_TIME = 1.0 / 20.0

        /**
         * When each level starts (seconds). Levels get longer as you go; after
         * the table runs out a new level starts every 60 seconds.
         */
        val levelStartTimes: List<Double> = listOf(0.0, 20.0, 45.0, 75.0, 110.0, 150.0, 195.0, 245.0, 300.0, 360.0)

        /**
         * Endless/Daily difficulty curve as (time, intensity) knots: a quick
         * warm-up, a long demanding-but-fair middle, then it gets wild after ~5 min.
         */
        val endlessCurve: List<Pair<Double, Double>> = listOf(
            0.0 to 0.12, 20.0 to 0.26, 45.0 to 0.45, 75.0 to 0.55, 110.0 to 0.63, 150.0 to 0.70,
            195.0 to 0.77, 245.0 to 0.85, 300.0 to 0.97, 360.0 to 1.12,
        )

        fun level(elapsed: Double): Int {
            val last = levelStartTimes.last()
            if (elapsed >= last) {
                return levelStartTimes.size + ((elapsed - last) / 60).toInt()
            }
            return levelStartTimes.indexOfLast { elapsed >= it }.coerceAtLeast(0) + 1
        }

        fun endlessIntensity(elapsed: Double): Double {
            val curve = endlessCurve
            val last = curve.last()
            if (elapsed >= last.first) {
                // Beyond the curve: +0.25 per minute until it caps out.
                return minOf(1.6, last.second + (elapsed - last.first) / 60 * 0.25)
            }
            for (index in 1 until curve.size) {
                if (elapsed < curve[index].first) {
                    val a = curve[index - 1]
                    val b = curve[index]
                    return lerp(a.second, b.second, (elapsed - a.first) / (b.first - a.first))
                }
            }
            return last.second
        }
    }

    var phase: Phase = Phase.COUNTDOWN
        private set
    var countdownRemaining: Double = COUNTDOWN_DURATION
        private set
    var elapsed: Double = 0.0
        private set
    var clock: Double = TIME_ATTACK_START_CLOCK
        private set
    var score: Double = 0.0
        private set
    var combo: Int = 0
        private set
    var comboTimer: Double = 0.0
        private set
    var level: Int = 1
        private set
    val stats = RunStats()

    var playerPosition: Vec2 = playfield.playerStart
        private set
    var playerTarget: Vec2 = playfield.playerStart
        private set
    var hasShield: Boolean = false
        private set
    var invulnerability: Double = 0.0
        private set

    private val _activePowerUps = LinkedHashMap<TimedPowerUp, Double>()
    val activePowerUps: Map<TimedPowerUp, Double> get() = _activePowerUps

    private val _hazards = ArrayList<Hazard>()
    private val _pickups = ArrayList<Pickup>()
    private val _warnings = ArrayList<HazardWarning>()
    val hazards: List<Hazard> get() = _hazards
    val pickups: List<Pickup> get() = _pickups
    val warnings: List<HazardWarning> get() = _warnings

    private val events = ArrayList<GameEvent>()
    private var nextID = 1
    private var lastCountdownTick = Int.MAX_VALUE
    private var lastClockTick = Int.MAX_VALUE

    private val hazardRNG: SeededRandom
    private val pickupRNG: SeededRandom
    private val powerRNG: SeededRandom

    private var hazardTimer = 0.6
    private var starTimer = 1.0
    private var powerUpTimer = 8.0
    private var crystalTimer = 5.0
    private var wallTimer = 16.0

    init {
        val root = SeededRandom(config.seed)
        hazardRNG = root.fork(0xA11CE)
        pickupRNG = root.fork(0xB0B)
        powerRNG = root.fork(0xC0FFEE)
        if (config.mode == GameMode.TIME_ATTACK) powerUpTimer = 6.0
    }

    // MARK: Derived values

    val displayScore: Int get() = floor(score).toInt()

    val multiplier: Int get() = 1 + minOf(combo, 40) / 8

    val comboProgress: Double
        get() = if (combo > 0) clamp(comboTimer / COMBO_WINDOW, 0.0, 1.0) else 0.0

    val isSlowMotion: Boolean get() = _activePowerUps.containsKey(TimedPowerUp.SLOW_MO)

    val playerRadius: Double
        get() {
            var radius = BASE_PLAYER_RADIUS
            if (config.modifier == RunModifier.TINY_HERO) radius *= 0.6
            if (_activePowerUps.containsKey(TimedPowerUp.SHRINK)) radius *= 0.55
            return radius
        }

    /** Collision radius is a little smaller than the visual for fairness. */
    val playerHitRadius: Double get() = playerRadius * 0.82

    val isFinished: Boolean get() = phase == Phase.FINISHED

    /** 0 at the start, 1 at "full speed" and slowly beyond. */
    val intensity: Double
        get() = when (config.mode) {
            GameMode.ENDLESS, GameMode.DAILY -> endlessIntensity(elapsed)
            GameMode.TIME_ATTACK -> minOf(0.3 + elapsed / 100, 1.1)
            GameMode.ZEN -> minOf(0.12 + elapsed / 300, 0.55)
        }

    /**
     * Which hazard families are in play. Endless/Daily introduce one new
     * family per level; other modes follow intensity.
     */
    private val hazardStage: Int
        get() = when (config.mode) {
            GameMode.ENDLESS, GameMode.DAILY -> level
            GameMode.TIME_ATTACK, GameMode.ZEN -> 1 + (intensity * 7).toInt()
        }

    // MARK: Input

    /** Sets where the player should move to (in playfield coordinates). */
    fun setTarget(point: Vec2) {
        val r = playerRadius + 4
        playerTarget = Vec2(
            clamp(point.x, r, playfield.width - r),
            clamp(point.y, playfield.playerMinY, playfield.playerMaxY),
        )
    }

    fun drainEvents(): List<GameEvent> {
        if (events.isEmpty()) return emptyList()
        val drained = ArrayList(events)
        events.clear()
        return drained
    }

    // MARK: Simulation

    fun step(rawDelta: Double) {
        if (phase == Phase.FINISHED) return
        val dt = clamp(rawDelta, 0.0, MAX_DELTA_TIME)
        if (dt <= 0) return

        movePlayer(dt)

        when (phase) {
            Phase.COUNTDOWN -> {
                countdownRemaining -= dt
                val tick = ceil(countdownRemaining).toInt()
                if (tick < lastCountdownTick && tick > 0) {
                    lastCountdownTick = tick
                    events += GameEvent.CountdownTick(tick)
                }
                if (countdownRemaining <= 0) {
                    countdownRemaining = 0.0
                    phase = Phase.RUNNING
                    events += GameEvent.Go
                }
            }
            Phase.RUNNING -> simulate(dt)
            Phase.FINISHED -> Unit
        }
    }

    private fun movePlayer(dt: Double) {
        val follow = minOf(1.0, dt * 22)
        playerPosition += (playerTarget - playerPosition) * follow
        // Keep inside the bounds even if the radius changed (shrink ended).
        val r = playerRadius + 4
        playerPosition = playerPosition.copy(x = clamp(playerPosition.x, r, playfield.width - r))
    }

    private fun simulate(dt: Double) {
        elapsed += dt
        val worldDelta = dt * (if (isSlowMotion) 0.45 else 1.0)

        updateTimers(dt)
        updateLevel()
        updateScoreAndClock(dt)
        if (phase == Phase.FINISHED) return

        spawnHazards(worldDelta)
        spawnPickups(worldDelta)
        updateWarnings(worldDelta)
        updateHazards(worldDelta)
        if (phase == Phase.FINISHED) return
        updatePickups(worldDelta, realDelta = dt)
    }

    private fun updateTimers(dt: Double) {
        if (invulnerability > 0) invulnerability = maxOf(0.0, invulnerability - dt)

        if (combo > 0) {
            comboTimer -= dt
            if (comboTimer <= 0) {
                combo = 0
                comboTimer = 0.0
            }
        }

        for ((powerUp, remaining) in _activePowerUps.entries.toList()) {
            val next = remaining - dt
            if (next <= 0) {
                _activePowerUps.remove(powerUp)
                events += GameEvent.PowerUpExpired(powerUp)
            } else {
                _activePowerUps[powerUp] = next
            }
        }
    }

    private fun updateLevel() {
        val newLevel = level(elapsed)
        if (newLevel > level) {
            level = newLevel
            if (config.mode == GameMode.ENDLESS || config.mode == GameMode.DAILY) {
                events += GameEvent.LevelUp(newLevel)
            }
        }
    }

    private fun updateScoreAndClock(dt: Double) {
        when (config.mode) {
            GameMode.ENDLESS, GameMode.DAILY -> score += 10 * dt * config.scoreMultiplier
            GameMode.TIME_ATTACK -> {
                clock -= dt
                val whole = ceil(clock).toInt()
                if (whole <= 5 && whole > 0 && whole < lastClockTick) {
                    lastClockTick = whole
                    events += GameEvent.ClockTick(whole)
                }
                if (whole > 5) lastClockTick = Int.MAX_VALUE
                if (clock <= 0) {
                    clock = 0.0
                    finish()
                }
            }
            GameMode.ZEN -> Unit
        }
    }

    // MARK: Spawning – hazards

    private val hazardSpeed: Double
        get() {
            val t = intensity
            var speed = lerp(230.0, 460.0, minOf(t, 1.0))
            if (t > 1) speed += (t - 1) * 120
            speed *= config.difficulty.speedMultiplier
            if (config.modifier == RunModifier.SPEED_DEMON) speed *= 1.35
            return speed
        }

    private val hazardInterval: Double
        get() {
            val t = intensity
            var interval = lerp(1.05, 0.30, minOf(t, 1.0))
            if (t > 1) interval = maxOf(0.22, 0.30 - (t - 1) * 0.1)
            interval *= config.difficulty.spawnIntervalMultiplier
            if (config.modifier == RunModifier.TINY_HERO) interval *= 0.75
            return interval
        }

    private val hazardBaseRadius: Double
        get() {
            var radius = lerp(10.0, 22.0, minOf(intensity, 1.0))
            if (config.modifier == RunModifier.GIANT_DOTS) radius *= 1.5
            return radius
        }

    private fun spawnHazards(dt: Double) {
        hazardTimer -= dt
        if (hazardTimer <= 0) {
            spawnRandomHazard()
            hazardTimer += hazardInterval * hazardRNG.range(0.75, 1.25)
            // Never let a long frame queue up a burst.
            hazardTimer = maxOf(hazardTimer, 0.05)
        }

        val wallsEnabled = (config.mode == GameMode.ENDLESS || config.mode == GameMode.DAILY) && hazardStage >= 6
        if (wallsEnabled) {
            wallTimer -= dt
            if (wallTimer <= 0) {
                spawnWall()
                // Walls come more often the deeper you get.
                val deeper = maxOf(0, level - 6).toDouble()
                wallTimer = hazardRNG.range(maxOf(8.0, 16 - deeper * 1.5), maxOf(12.0, 24 - deeper * 2))
                hazardTimer = maxOf(hazardTimer, 0.9)
            }
        }
    }

    private fun nextKind(): HazardKind {
        val t = intensity
        val stage = hazardStage
        val weights = mutableListOf(
            HazardKind.DROP to 1.0,
            HazardKind.WOBBLER to (if (stage >= 2) 0.35 else 0.0),
            HazardKind.SPEEDER to (if (stage >= 3) 0.2 + minOf(t, 1.2) * 0.12 else 0.0),
            HazardKind.GIANT to (if (stage >= 4) 0.15 else 0.0),
            HazardKind.SPLITTER to (if (stage >= 5) 0.14 + minOf(t, 1.2) * 0.08 else 0.0),
        )
        when (config.modifier) {
            RunModifier.WOBBLE_WORLD -> {
                weights[0] = HazardKind.DROP to 0.0
                weights[1] = HazardKind.WOBBLER to 1.0
            }
            RunModifier.METEOR_SHOWER -> weights[2] = HazardKind.SPEEDER to 0.8
            else -> Unit
        }
        return hazardRNG.weighted(weights) ?: HazardKind.DROP
    }

    private fun spawnRandomHazard() {
        val kind = nextKind()
        val speed = hazardSpeed * hazardRNG.range(0.88, 1.12)
        var radius = hazardBaseRadius * hazardRNG.range(0.75, 1.3)

        when (kind) {
            HazardKind.SPEEDER -> {
                radius = hazardRNG.range(7.0, 9.5) * (if (config.modifier == RunModifier.GIANT_DOTS) 1.5 else 1.0)
                val x = hazardRNG.range(radius + 8, playfield.width - radius - 8)
                _warnings += HazardWarning(
                    id = makeID(), x = x, remaining = 0.7, total = 0.7,
                    radius = radius, speed = speed * 2.1,
                )
                events += GameEvent.Warning(x)
                return
            }
            HazardKind.GIANT -> radius = maxOf(30.0, radius * 2.0)
            HazardKind.SPLITTER -> radius = maxOf(16.0, radius * 1.4)
            else -> Unit
        }

        val margin = radius + 4
        val x = hazardRNG.range(margin, playfield.width - margin)
        var velocity = Vec2(0.0, -speed)
        val hazard = Hazard(
            id = makeID(), kind = kind,
            position = Vec2(x, playfield.height + radius + 10),
            velocity = velocity, radius = radius, baseX = x,
        )

        when (kind) {
            HazardKind.WOBBLER -> {
                val amplitude = hazardRNG.range(28.0, 70.0)
                hazard.wobbleAmplitude = amplitude
                hazard.wobbleFrequency = hazardRNG.range(1.6, 3.2)
                hazard.phase = hazardRNG.range(0.0, 2 * PI)
                hazard.baseX = clamp(x, amplitude + radius, playfield.width - amplitude - radius)
                hazard.position = hazard.position.copy(x = hazard.baseX + amplitude * sin(hazard.phase))
            }
            HazardKind.GIANT -> {
                velocity = velocity.copy(y = velocity.y * 0.6)
                hazard.velocity = velocity
            }
            HazardKind.SPLITTER -> {
                velocity = velocity.copy(y = velocity.y * 0.85)
                hazard.velocity = velocity
            }
            else -> Unit
        }
        _hazards += hazard
    }

    private fun spawnWall() {
        val radius = 13.0 * (if (config.modifier == RunModifier.GIANT_DOTS) 1.3 else 1.0)
        val spacing = radius * 2 + 10
        val gapWidth = BASE_PLAYER_RADIUS * 2 * 3.4
        val gapCenter = hazardRNG.range(gapWidth, playfield.width - gapWidth)
        val speed = hazardSpeed * 0.75
        var x = radius + 2
        while (x < playfield.width - radius) {
            if (abs(x - gapCenter) > gapWidth / 2) {
                _hazards += Hazard(
                    id = makeID(), kind = HazardKind.DROP,
                    position = Vec2(x, playfield.height + radius + 10),
                    velocity = Vec2(0.0, -speed), radius = radius, baseX = x,
                )
            }
            x += spacing
        }
    }

    private fun updateWarnings(dt: Double) {
        var index = 0
        while (index < _warnings.size) {
            val warning = _warnings[index]
            warning.remaining -= dt
            if (warning.remaining <= 0) {
                _warnings.removeAt(index)
                _hazards += Hazard(
                    id = makeID(), kind = HazardKind.SPEEDER,
                    position = Vec2(warning.x, playfield.height + warning.radius + 10),
                    velocity = Vec2(0.0, -warning.speed),
                    radius = warning.radius, baseX = warning.x,
                )
            } else {
                index += 1
            }
        }
    }

    // MARK: Spawning – pickups

    private fun spawnPickups(dt: Double) {
        // Stars
        starTimer -= dt
        if (starTimer <= 0) {
            var interval = when (config.mode) {
                GameMode.ENDLESS, GameMode.DAILY -> pickupRNG.range(1.6, 2.6)
                GameMode.TIME_ATTACK -> pickupRNG.range(0.55, 0.9)
                GameMode.ZEN -> pickupRNG.range(1.0, 1.6)
            }
            if (config.modifier == RunModifier.STAR_STORM) interval *= 0.35
            starTimer = interval
            spawnPickup(PickupKind.STAR, radius = 11.0, speed = 150 + intensity * 70, rng = pickupRNG)
        }

        // Time crystals (Time Attack only)
        if (config.mode == GameMode.TIME_ATTACK) {
            crystalTimer -= dt
            if (crystalTimer <= 0) {
                crystalTimer = pickupRNG.range(6.5, 9.5)
                spawnPickup(PickupKind.TIME_CRYSTAL, radius = 13.0, speed = 170.0, rng = pickupRNG)
            }
        }

        // Power-ups
        if (config.modifier == RunModifier.PURIST) return
        powerUpTimer -= dt
        if (powerUpTimer <= 0) {
            powerUpTimer = if (config.mode == GameMode.TIME_ATTACK) powerRNG.range(8.0, 12.0) else powerRNG.range(11.0, 17.0)
            val options = listOf(
                PickupKind.SHIELD to (if (hasShield) 0.3 else 1.0),
                PickupKind.SLOW_MO to 0.8,
                PickupKind.MAGNET to 0.9,
                PickupKind.SHRINK to 0.7,
                PickupKind.NOVA to 0.55,
            )
            val kind = powerRNG.weighted(options) ?: PickupKind.SHIELD
            spawnPickup(kind, radius = 16.0, speed = 140.0, rng = powerRNG)
        }
    }

    private fun spawnPickup(kind: PickupKind, radius: Double, speed: Double, rng: SeededRandom) {
        val x = rng.range(radius + 10, playfield.width - radius - 10)
        _pickups += Pickup(
            id = makeID(), kind = kind,
            position = Vec2(x, playfield.height + radius + 10),
            velocity = Vec2(0.0, -speed), radius = radius,
        )
    }

    // MARK: Hazards update

    private fun updateHazards(dt: Double) {
        val hitRadius = playerHitRadius
        val visualRadius = playerRadius
        val splitLine = playfield.height * 0.52
        val spawned = ArrayList<Hazard>()
        var index = 0

        while (index < _hazards.size) {
            val hazard = _hazards[index]
            hazard.age += dt

            when (hazard.kind) {
                HazardKind.WOBBLER -> {
                    hazard.position = Vec2(
                        hazard.baseX + hazard.wobbleAmplitude * sin(hazard.phase + hazard.age * hazard.wobbleFrequency),
                        hazard.position.y + hazard.velocity.y * dt,
                    )
                }
                HazardKind.FRAGMENT -> {
                    hazard.position += hazard.velocity * dt
                    if (hazard.position.x < hazard.radius || hazard.position.x > playfield.width - hazard.radius) {
                        hazard.velocity = hazard.velocity.copy(x = -hazard.velocity.x)
                        hazard.position = hazard.position.copy(
                            x = clamp(hazard.position.x, hazard.radius, playfield.width - hazard.radius),
                        )
                    }
                }
                else -> hazard.position += hazard.velocity * dt
            }

            // Splitters break apart halfway down the screen.
            if (hazard.kind == HazardKind.SPLITTER && hazard.position.y < splitLine) {
                val childRadius = hazard.radius * 0.6
                for (direction in doubleArrayOf(-1.0, 1.0)) {
                    spawned += Hazard(
                        id = makeID(), kind = HazardKind.FRAGMENT,
                        position = hazard.position + Vec2(direction * childRadius, 0.0),
                        velocity = Vec2(direction * 95, hazard.velocity.y * 1.1),
                        radius = childRadius, baseX = hazard.position.x,
                    )
                }
                events += GameEvent.Split(hazard.position)
                _hazards.removeAt(index)
                continue
            }

            // Collision / near miss
            val distance = hazard.position.distanceTo(playerPosition)
            val gap = distance - hazard.radius - hitRadius
            if (gap < 0) {
                if (invulnerability > 0) {
                    hazard.touched = true
                } else {
                    _hazards.removeAt(index)
                    handleHit(hazard)
                    if (phase == Phase.FINISHED) return
                    continue
                }
            } else {
                hazard.closestGap = minOf(hazard.closestGap, distance - hazard.radius - visualRadius)
            }

            if (!hazard.resolved && hazard.position.y + hazard.radius < playerPosition.y - visualRadius) {
                hazard.resolved = true
                if (!hazard.touched) {
                    stats.dodged += 1
                    if (hazard.closestGap < NEAR_MISS_THRESHOLD) {
                        registerNearMiss(hazard.position, hazard.closestGap)
                    }
                }
            }

            if (hazard.position.y < -hazard.radius - 20) {
                _hazards.removeAt(index)
                continue
            }

            index += 1
        }

        _hazards += spawned
    }

    private fun registerNearMiss(position: Vec2, gap: Double) {
        val perfect = gap < PERFECT_MISS_THRESHOLD
        stats.nearMisses += 1
        if (perfect) stats.perfectMisses += 1
        val base = when (config.mode) {
            GameMode.TIME_ATTACK -> if (perfect) 60.0 else 30.0
            else -> if (perfect) 80.0 else 40.0
        }
        bumpCombo()
        val points = award(base)
        events += GameEvent.NearMiss(position, points, perfect)
    }

    private fun handleHit(hazard: Hazard) {
        val position = hazard.position
        if (hasShield) {
            hasShield = false
            stats.shieldsUsed += 1
            invulnerability = 1.0
            events += GameEvent.ShieldBroken(position)
            events += GameEvent.HazardDestroyed(position, hazard.radius, hazard.kind)
            return
        }

        stats.hits += 1
        when (config.mode) {
            GameMode.ENDLESS, GameMode.DAILY -> {
                events += GameEvent.Hit(position, fatal = true, penalty = 0.0)
                finish()
            }
            GameMode.TIME_ATTACK -> {
                clock = maxOf(0.0, clock - TIME_ATTACK_HIT_PENALTY)
                combo = 0
                comboTimer = 0.0
                invulnerability = 1.5
                events += GameEvent.Hit(position, fatal = false, penalty = TIME_ATTACK_HIT_PENALTY)
                events += GameEvent.HazardDestroyed(position, hazard.radius, hazard.kind)
                if (clock <= 0) finish()
            }
            GameMode.ZEN -> {
                combo = 0
                comboTimer = 0.0
                invulnerability = 1.0
                events += GameEvent.Hit(position, fatal = false, penalty = 0.0)
                events += GameEvent.HazardDestroyed(position, hazard.radius, hazard.kind)
            }
        }
    }

    // MARK: Pickups update

    private fun updatePickups(dt: Double, realDelta: Double) {
        val magnetActive = _activePowerUps.containsKey(TimedPowerUp.MAGNET)
        val collectRadius = maxOf(playerRadius, 12.0) * 1.15
        var index = 0

        while (index < _pickups.size) {
            val pickup = _pickups[index]
            pickup.age += dt

            val toPlayer = playerPosition - pickup.position
            val distance = toPlayer.length
            val attractable = pickup.kind == PickupKind.STAR || pickup.kind == PickupKind.TIME_CRYSTAL
            if (magnetActive && attractable && distance < 230) {
                pickup.position += toPlayer.normalized * (560 * realDelta)
            } else {
                pickup.position += pickup.velocity * dt
                pickup.position = pickup.position.copy(
                    x = pickup.position.x + sin(pickup.age * 3 + pickup.id.toDouble()) * 18 * dt,
                )
            }

            if (pickup.position.distanceTo(playerPosition) < pickup.radius + collectRadius) {
                _pickups.removeAt(index)
                collect(pickup)
                continue
            }

            if (pickup.position.y < -pickup.radius - 20) {
                _pickups.removeAt(index)
                continue
            }

            index += 1
        }
    }

    private fun collect(pickup: Pickup) {
        val position = pickup.position
        when (pickup.kind) {
            PickupKind.STAR -> {
                stats.stars += 1
                val base = when (config.mode) {
                    GameMode.TIME_ATTACK -> 50.0
                    GameMode.ZEN -> 10.0
                    else -> 25.0
                }
                bumpCombo()
                val points = award(base)
                events += GameEvent.StarCollected(position, points)
            }
            PickupKind.TIME_CRYSTAL -> {
                stats.crystals += 1
                clock = minOf(99.0, clock + TIME_CRYSTAL_BONUS)
                events += GameEvent.TimeCrystal(position, TIME_CRYSTAL_BONUS)
            }
            PickupKind.SHIELD -> {
                hasShield = true
                stats.powerUps += 1
                events += GameEvent.PowerUpCollected(PickupKind.SHIELD, position)
            }
            PickupKind.SLOW_MO -> activate(TimedPowerUp.SLOW_MO, position)
            PickupKind.MAGNET -> activate(TimedPowerUp.MAGNET, position)
            PickupKind.SHRINK -> activate(TimedPowerUp.SHRINK, position)
            PickupKind.NOVA -> {
                stats.powerUps += 1
                events += GameEvent.PowerUpCollected(PickupKind.NOVA, position)
                triggerNova()
            }
        }
    }

    private fun activate(powerUp: TimedPowerUp, position: Vec2) {
        stats.powerUps += 1
        _activePowerUps[powerUp] = powerUp.duration
        events += GameEvent.PowerUpCollected(powerUp.pickup, position)
    }

    private fun triggerNova() {
        val cleared = _hazards.size
        for (hazard in _hazards) {
            events += GameEvent.HazardDestroyed(hazard.position, hazard.radius, hazard.kind)
        }
        _hazards.clear()
        _warnings.clear()
        stats.bestNovaClear = maxOf(stats.bestNovaClear, cleared)
        val points = if (cleared > 0) award(15.0 * cleared) else 0
        events += GameEvent.Nova(playerPosition, cleared, points)
    }

    // MARK: Scoring helpers

    private fun bumpCombo() {
        val previousMultiplier = multiplier
        combo += 1
        comboTimer = COMBO_WINDOW
        stats.maxCombo = maxOf(stats.maxCombo, combo)
        val newMultiplier = multiplier
        stats.maxMultiplier = maxOf(stats.maxMultiplier, newMultiplier)
        if (newMultiplier > previousMultiplier) {
            events += GameEvent.MultiplierUp(newMultiplier)
        }
    }

    /** Adds points using the current multiplier and run multipliers. Returns the points added. */
    private fun award(base: Double): Int {
        val points = base * multiplier * config.scoreMultiplier
        score += points
        return Math.round(points).toInt()
    }

    private fun finish() {
        if (phase == Phase.FINISHED) return
        phase = Phase.FINISHED
        events += GameEvent.Finished
    }

    /** Ends the run early (e.g. quitting Zen mode from the pause menu). */
    fun endRun() = finish()

    private fun makeID(): Int = nextID++

    // MARK: Result

    fun makeResult(date: Long = System.currentTimeMillis()): RunResult = RunResult(
        mode = config.mode,
        difficulty = config.difficulty,
        modifier = config.modifier,
        dailyKey = config.dailyKey,
        score = displayScore,
        duration = elapsed,
        stars = stats.stars,
        nearMisses = stats.nearMisses,
        perfectMisses = stats.perfectMisses,
        dodged = stats.dodged,
        powerUps = stats.powerUps,
        hits = stats.hits,
        maxCombo = stats.maxCombo,
        maxMultiplier = stats.maxMultiplier,
        bestNovaClear = stats.bestNovaClear,
        levelReached = level,
        date = date,
    )
}
