//
//  VelocityRushTests.swift
//  VelocityRushTests
//
//  Created by Christopher Dornan on 9/1/25.
//

import Foundation
import Testing
@testable import VelocityRush

// MARK: - Helpers

private var utcCalendar: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    return calendar
}

private func date(_ year: Int, _ month: Int, _ day: Int, hour: Int = 12) -> Date {
    utcCalendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour))!
}

private let phonePlayfield = Playfield.fitting(viewWidth: 390, viewHeight: 844)

private func run(_ engine: GameEngine, seconds: Double, step: Double = 1.0 / 60.0) {
    var t = 0.0
    while t < seconds && !engine.isFinished {
        engine.step(step)
        _ = engine.drainEvents()
        t += step
    }
}

private func sampleRun(mode: GameMode = .endless, score: Int = 1_500, duration: Double = 45,
                       stars: Int = 20, date: Date = date(2026, 9, 29), dailyKey: String? = nil) -> RunResult {
    RunResult(mode: mode, difficulty: .normal, modifier: .none, dailyKey: dailyKey, score: score,
              duration: duration, stars: stars, nearMisses: 6, perfectMisses: 1, dodged: 80,
              powerUps: 2, hits: 1, maxCombo: 12, maxMultiplier: 2, bestNovaClear: 0,
              levelReached: 4, date: date)
}

// MARK: - Randomness

struct RandomTests {
    @Test func seededRandomIsDeterministic() {
        var a = SeededRandom(seed: 42)
        var b = SeededRandom(seed: 42)
        for _ in 0..<100 { #expect(a.next() == b.next()) }
    }

    @Test func unitStaysInRange() {
        var rng = SeededRandom(seed: 7)
        for _ in 0..<10_000 {
            let value = rng.unit()
            #expect(value >= 0 && value < 1)
        }
    }

    @Test func stableHashMatchesFNV1a() {
        #expect(StableHash.fnv1a("") == 0xCBF2_9CE4_8422_2325)
        #expect(StableHash.fnv1a("a") == 0xAF63_DC4C_8601_EC8C)
    }
}

// MARK: - Engine

struct EngineTests {
    @Test func countdownThenRunning() {
        let engine = GameEngine(config: RunConfig(mode: .endless, difficulty: .normal, seed: 1), playfield: phonePlayfield)
        var ticks: [Int] = []
        var sawGo = false
        for _ in 0..<(60 * 4) {
            engine.step(1.0 / 60.0)
            for event in engine.drainEvents() {
                if case .countdownTick(let n) = event { ticks.append(n) }
                if case .go = event { sawGo = true }
            }
        }
        #expect(ticks == [3, 2, 1])
        #expect(sawGo)
        #expect(engine.phase == .running)
    }

    @Test func sameSeedProducesSameHazards() {
        let config = RunConfig(mode: .daily, difficulty: .normal, modifier: .none, seed: 123, dailyKey: "x")
        let a = GameEngine(config: config, playfield: phonePlayfield)
        let b = GameEngine(config: config, playfield: phonePlayfield)
        // Keep the player out of the way so both runs last the full window.
        a.setTarget(Vec2(0, 70))
        b.setTarget(Vec2(0, 70))
        run(a, seconds: 8)
        run(b, seconds: 8)
        #expect(a.hazards.map(\.position) == b.hazards.map(\.position))
        #expect(a.pickups.map(\.kind) == b.pickups.map(\.kind))
    }

    @Test func standingStillEventuallyEndsEndless() {
        let engine = GameEngine(config: RunConfig(mode: .endless, difficulty: .normal, seed: 99), playfield: phonePlayfield)
        run(engine, seconds: 600)
        #expect(engine.isFinished)
        #expect(engine.makeResult().hits == 1)
    }

    @Test func timeAttackEndsWhenClockRunsOut() {
        let engine = GameEngine(config: RunConfig(mode: .timeAttack, difficulty: .normal, seed: 5), playfield: phonePlayfield)
        run(engine, seconds: 400)
        #expect(engine.isFinished)
        #expect(engine.clock == 0)
    }

    @Test func zenNeverEnds() {
        let engine = GameEngine(config: RunConfig(mode: .zen, difficulty: .normal, seed: 5), playfield: phonePlayfield)
        run(engine, seconds: 180)
        #expect(!engine.isFinished)
        engine.endRun()
        #expect(engine.isFinished)
    }

    @Test func targetIsClampedToPlayfield() {
        let engine = GameEngine(config: RunConfig(mode: .zen, difficulty: .normal, seed: 5), playfield: phonePlayfield)
        engine.setTarget(Vec2(-500, 5_000))
        run(engine, seconds: 1)
        #expect(engine.playerPosition.x >= engine.playerRadius)
        #expect(engine.playerPosition.y <= phonePlayfield.playerMaxY + 0.001)
    }

    @Test func runStartsAtBaseMultiplier() {
        let engine = GameEngine(config: RunConfig(mode: .endless, difficulty: .normal, seed: 5), playfield: phonePlayfield)
        #expect(engine.multiplier == 1)
        #expect(engine.combo == 0)
        #expect(engine.displayScore == 0)
    }

    @Test func playfieldClampsExtremeAspectRatios() {
        #expect(Playfield.fitting(viewWidth: 820, viewHeight: 1180).height == 720)
        #expect(Playfield.fitting(viewWidth: 300, viewHeight: 1000).height == 880)
    }
}

// MARK: - Daily

struct DailyTests {
    @Test func dayKeyFormatting() {
        #expect(DailyChallenge.dayKey(for: date(2026, 1, 5), calendar: utcCalendar) == "2026-01-05")
    }

    @Test func dailyConfigIsStableForADay() {
        let morning = DailyChallenge.config(for: date(2026, 9, 29, hour: 1), calendar: utcCalendar)
        let evening = DailyChallenge.config(for: date(2026, 9, 29, hour: 23), calendar: utcCalendar)
        #expect(morning.seed == evening.seed)
        #expect(morning.modifier == evening.modifier)
        #expect(morning.modifier != .none)
        #expect(morning.difficulty == .normal)
    }

    @Test func dailySeedsDifferAcrossDays() {
        let seeds = Set((1...28).map { DailyChallenge.seed(for: String(format: "2026-02-%02d", $0)) })
        #expect(seeds.count == 28)
    }

    @Test func missionsAreDeterministicAndDistinct() {
        for day in 1...60 {
            let key = "2026-03-\(day)"
            let missions = MissionGenerator.missions(for: key)
            #expect(missions == MissionGenerator.missions(for: key))
            #expect(missions.count == 3)
            #expect(Set(missions.map(\.kind)).count == 3)
            for (slot, mission) in missions.enumerated() where slot > 0 {
                #expect(mission.kind != .playDaily)
            }
        }
    }

    @Test func singleRunMissionsTrackBest() {
        var mission = MissionProgress(id: "m", kind: .surviveEndless, goal: 60, reward: 100)
        mission.apply(sampleRun(duration: 40))
        mission.apply(sampleRun(duration: 20))
        #expect(mission.progress == 40)
        mission.apply(sampleRun(duration: 75))
        #expect(mission.isComplete)
        #expect(mission.progress == 60)
    }

    @Test func cumulativeMissionsAddUp() {
        var mission = MissionProgress(id: "m", kind: .collectStars, goal: 30, reward: 60)
        mission.apply(sampleRun(stars: 12))
        mission.apply(sampleRun(stars: 12))
        #expect(mission.progress == 24)
        mission.apply(sampleRun(stars: 12))
        #expect(mission.progress == 30)
    }
}

// MARK: - Progression

struct ProgressionTests {
    @Test func levelCurveRoundTrips() {
        for level in 1...60 {
            #expect(Leveling.level(forXP: Leveling.totalXP(forLevel: level)) == level)
            #expect(Leveling.level(forXP: Leveling.totalXP(forLevel: level) - 1) == max(1, level - 1))
        }
        #expect(Leveling.progress(forXP: 0) == 0)
    }

    @Test func recordingARunUpdatesEverything() {
        var profile = PlayerProfile()
        let now = date(2026, 9, 29)
        let rewards = Progression.record(sampleRun(duration: 45), into: &profile, now: now, calendar: utcCalendar)
        #expect(profile.stats.gamesPlayed == 1)
        #expect(profile.stats.bestEndlessTime == 45)
        #expect(profile.stats.bestEndlessScore == 1_500)
        #expect(rewards.isNewBest)
        #expect(rewards.localRank == 1)
        #expect(rewards.newAchievements.contains("survive_30"))
        #expect(rewards.newAchievements.contains("endless_1k"))
        #expect(profile.coins == rewards.totalCoins)
        #expect(profile.daily.missions.count == 3)
        #expect(profile.localLeaderboard(for: .endless).count == 1)
    }

    @Test func secondWorseRunIsNotABest() {
        var profile = PlayerProfile()
        let now = date(2026, 9, 29)
        _ = Progression.record(sampleRun(score: 2_000), into: &profile, now: now, calendar: utcCalendar)
        let rewards = Progression.record(sampleRun(score: 1_000), into: &profile, now: now, calendar: utcCalendar)
        #expect(!rewards.isNewBest)
        #expect(rewards.localRank == 2)
    }

    @Test func leaderboardKeepsTopTen() {
        var board: [LeaderboardEntry] = []
        for score in 1...15 {
            _ = Progression.insert(sampleRun(score: score * 100), into: &board)
        }
        #expect(board.count == 10)
        #expect(board.first?.score == 1_500)
        #expect(Progression.insert(sampleRun(score: 1), into: &board) == nil)
    }

    @Test func dailyRunCountsOncePerDay() {
        var profile = PlayerProfile()
        let now = date(2026, 9, 29)
        let key = DailyChallenge.dayKey(for: now, calendar: utcCalendar)
        _ = Progression.record(sampleRun(mode: .daily, score: 900, dailyKey: key), into: &profile, now: now, calendar: utcCalendar)
        _ = Progression.record(sampleRun(mode: .daily, score: 1_200, dailyKey: key), into: &profile, now: now, calendar: utcCalendar)
        #expect(profile.stats.dailyRunsCompleted == 1)
        #expect(profile.daily.dailyRunAttempts == 2)
        #expect(profile.daily.dailyRunBest == 1_200)
        #expect(profile.localLeaderboard(for: .daily).count == 2)

        // Next day the board resets.
        Progression.refreshDaily(&profile, now: date(2026, 9, 30), calendar: utcCalendar)
        #expect(profile.localLeaderboard(for: .daily).isEmpty)
        #expect(profile.daily.dailyRunBest == 0)
    }

    @Test func loginStreakGrowsAndResets() {
        var profile = PlayerProfile()
        let day1 = date(2026, 9, 1)
        Progression.refreshDaily(&profile, now: day1, calendar: utcCalendar)
        #expect(Progression.claimLoginReward(&profile, now: day1, calendar: utcCalendar).coins == 25)
        #expect(Progression.claimLoginReward(&profile, now: day1, calendar: utcCalendar).coins == 0)

        let day2 = date(2026, 9, 2)
        Progression.refreshDaily(&profile, now: day2, calendar: utcCalendar)
        #expect(Progression.claimLoginReward(&profile, now: day2, calendar: utcCalendar).coins == 50)
        #expect(profile.stats.loginStreak == 2)

        let day4 = date(2026, 9, 4)
        Progression.refreshDaily(&profile, now: day4, calendar: utcCalendar)
        #expect(Progression.nextLoginStreakDay(profile, now: day4, calendar: utcCalendar) == 1)
        #expect(Progression.claimLoginReward(&profile, now: day4, calendar: utcCalendar).coins == 25)
        #expect(profile.stats.longestLoginStreak == 2)
    }

    @Test func purchasingCosmetics() {
        var profile = PlayerProfile()
        let now = date(2026, 9, 29)
        #expect(Progression.purchase("skin.neon", profile: &profile, now: now) == .notEnoughCoins(needed: 150))
        profile.coins = 200
        #expect(Progression.purchase("skin.neon", profile: &profile, now: now) == .success)
        #expect(profile.equippedSkin == "skin.neon")
        #expect(profile.stats.cosmeticsPurchased == 1)
        #expect(profile.unlockedAchievements["shop_1"] != nil)
        // 200 - 150 + 50 (achievement reward)
        #expect(profile.coins == 100)
        #expect(Progression.purchase("skin.neon", profile: &profile, now: now) == .alreadyOwned)
        #expect(Progression.purchase("skin.gold", profile: &profile, now: now) == .locked)
    }

    @Test func claimingMissionsAndBonus() {
        var profile = PlayerProfile()
        let now = date(2026, 9, 29)
        Progression.refreshDaily(&profile, now: now, calendar: utcCalendar)
        for index in profile.daily.missions.indices {
            profile.daily.missions[index].progress = profile.daily.missions[index].goal
        }
        #expect(Progression.claimableMissionCount(profile) == 3)
        for mission in profile.daily.missions {
            #expect(Progression.claimMission(mission.id, profile: &profile, now: now) == mission.reward)
        }
        #expect(Progression.isAllMissionsBonusAvailable(profile))
        #expect(Progression.claimAllMissionsBonus(&profile) == MissionGenerator.allCompleteBonus)
        #expect(Progression.claimableMissionCount(profile) == 0)
    }

    @Test func achievementUnlocksCosmetic() {
        var profile = PlayerProfile()
        profile.stats.bestEndlessTime = 125
        let unlocked = Progression.evaluateAchievements(&profile, now: Date())
        #expect(unlocked.contains { $0.id == "survive_120" })
        #expect(profile.unlockedCosmetics.contains("skin.gold"))
    }
}

// MARK: - Catalog integrity

struct CatalogTests {
    @Test func idsAreUnique() {
        #expect(Set(AchievementCatalog.all.map(\.id)).count == AchievementCatalog.all.count)
        #expect(Set(Cosmetic.catalog.map(\.id)).count == Cosmetic.catalog.count)
    }

    @Test func achievementCosmeticsReferenceRealAchievements() {
        for cosmetic in Cosmetic.catalog {
            if case .achievement(let id) = cosmetic.requirement {
                #expect(AchievementCatalog.find(id) != nil, "Missing achievement \(id)")
            }
        }
    }

    @Test func defaultsExistAndAreFree() {
        for id in [Cosmetic.defaultSkin, Cosmetic.defaultTrail, Cosmetic.defaultTheme] {
            #expect(Cosmetic.find(id)?.requirement == .free)
        }
    }

    @Test func everyCosmeticHasItsLook() {
        for cosmetic in Cosmetic.catalog {
            switch cosmetic.category {
            case .skin: #expect(cosmetic.skin != nil)
            case .trail: #expect(cosmetic.trail != nil)
            case .theme: #expect(cosmetic.theme != nil)
            }
        }
    }
}

// MARK: - Persistence

struct PersistenceTests {
    private func freshDefaults() -> UserDefaults {
        let name = "vr.tests.\(UUID().uuidString)"
        return UserDefaults(suiteName: name)!
    }

    @Test func profileRoundTrips() {
        let repo = ProfileRepository(defaults: freshDefaults())
        var profile = repo.load()
        profile.coins = 321
        profile.unlockedCosmetics.insert("skin.neon")
        profile.equippedSkin = "skin.neon"
        repo.save(profile)
        let loaded = repo.load()
        #expect(loaded.coins == 321)
        #expect(loaded.equippedSkin == "skin.neon")
    }

    @Test func legacyStatsAreMigrated() throws {
        let defaults = freshDefaults()
        let legacy = #"{"totalTimeSurvived":321.5,"totalObjectsDodged":400,"totalStarsCollected":0,"totalGamesPlayed":12,"bestSurvivalTime":75.2,"totalPurchasesMade":0,"longestStreak":2,"currentStreak":0}"#
        defaults.set(Data(legacy.utf8), forKey: ProfileRepository.legacyStatsKey)
        let profile = ProfileRepository(defaults: defaults).load()
        #expect(profile.stats.gamesPlayed == 12)
        #expect(profile.stats.bestEndlessTime == 75)
        #expect(profile.stats.totalDodged == 400)
        #expect(profile.coins == 250)
    }
}

// MARK: - Audio

struct SynthTests {
    @Test func everyEffectProducesAudibleClippingFreeSamples() {
        for effect in SoundEffect.allCases {
            let samples = SynthSamples.samples(for: effect, sampleRate: 22_050)
            #expect(!samples.isEmpty, "\(effect) is empty")
            #expect(samples.allSatisfy { abs($0) <= 1 }, "\(effect) clips")
            #expect((samples.map { abs($0) }.max() ?? 0) > 0.01, "\(effect) is silent")
        }
    }

    @Test func musicLoopIsEightSeconds() {
        let loop = SynthSamples.musicLoop(sampleRate: 8_000)
        #expect(loop.count == 64_000)
        #expect(loop.allSatisfy { abs($0) <= 1 })
    }
}
