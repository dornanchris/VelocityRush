//
//  CoreTests.kt
//  Starshower Run
//
//  Mirrors the iOS StarshowerRunTests suite so both platforms enforce the
//  same gameplay, economy and persistence rules.
//

package com.starshower.run.core

import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.audio.SynthSamples
import com.starshower.run.core.engine.Difficulty
import com.starshower.run.core.engine.GameEngine
import com.starshower.run.core.engine.GameEvent
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.Playfield
import com.starshower.run.core.engine.RunConfig
import com.starshower.run.core.engine.RunModifier
import com.starshower.run.core.engine.RunResult
import com.starshower.run.core.engine.SeededRandom
import com.starshower.run.core.engine.StableHash
import com.starshower.run.core.engine.Vec2
import com.starshower.run.core.progression.AchievementCatalog
import com.starshower.run.core.progression.Cosmetic
import com.starshower.run.core.progression.CosmeticCategory
import com.starshower.run.core.progression.CosmeticRarity
import com.starshower.run.core.progression.DailyChallenge
import com.starshower.run.core.progression.InMemoryKeyValueStore
import com.starshower.run.core.progression.LeaderboardEntry
import com.starshower.run.core.progression.Leveling
import com.starshower.run.core.progression.MissionClaim
import com.starshower.run.core.progression.MissionGenerator
import com.starshower.run.core.progression.MissionKind
import com.starshower.run.core.progression.MissionProgress
import com.starshower.run.core.progression.PlayerProfile
import com.starshower.run.core.progression.ProfileRepository
import com.starshower.run.core.progression.Progression
import com.starshower.run.core.progression.PurchaseResult
import com.starshower.run.core.progression.UnlockGate
import com.starshower.run.core.progression.deepCopy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// MARK: - Helpers

private val utc: ZoneId = ZoneOffset.UTC

private fun date(year: Int, month: Int, day: Int, hour: Int = 12): Instant =
    ZonedDateTime.of(year, month, day, hour, 0, 0, 0, utc).toInstant()

private val phonePlayfield = Playfield.fitting(viewWidth = 390.0, viewHeight = 844.0)

private fun run(engine: GameEngine, seconds: Double, step: Double = 1.0 / 60.0) {
    var t = 0.0
    while (t < seconds && !engine.isFinished) {
        engine.step(step)
        engine.drainEvents()
        t += step
    }
}

private fun sampleRun(
    mode: GameMode = GameMode.ENDLESS, score: Int = 1_500, duration: Double = 45.0,
    stars: Int = 20, date: Instant = date(2026, 9, 29), dailyKey: String? = null,
) = RunResult(
    mode = mode, difficulty = Difficulty.NORMAL, modifier = RunModifier.NONE, dailyKey = dailyKey, score = score,
    duration = duration, stars = stars, nearMisses = 6, perfectMisses = 1, dodged = 80,
    powerUps = 2, hits = 1, maxCombo = 12, maxMultiplier = 2, bestNovaClear = 0,
    levelReached = 4, date = date.toEpochMilli(),
)

// MARK: - Randomness

class RandomTests {
    @Test fun seededRandomIsDeterministic() {
        val a = SeededRandom(42)
        val b = SeededRandom(42)
        repeat(100) { assertEquals(a.next(), b.next()) }
    }

    @Test fun matchesSwiftSplitMix64() {
        // First output of SplitMix64 seeded with 0 is the canonical 0xE220A8397B1DCDAF.
        assertEquals(0xE220_A839_7B1D_CDAFuL.toLong(), SeededRandom(0).next())
    }

    @Test fun unitStaysInRange() {
        val rng = SeededRandom(7)
        repeat(10_000) {
            val value = rng.unit()
            assertTrue(value >= 0 && value < 1)
        }
    }

    @Test fun stableHashMatchesFNV1a() {
        assertEquals(0xCBF2_9CE4_8422_2325uL.toLong(), StableHash.fnv1a(""))
        assertEquals(0xAF63_DC4C_8601_EC8CuL.toLong(), StableHash.fnv1a("a"))
    }
}

// MARK: - Engine

class EngineTests {
    @Test fun countdownThenRunning() {
        val engine = GameEngine(RunConfig(GameMode.ENDLESS, Difficulty.NORMAL, seed = 1), phonePlayfield)
        val ticks = mutableListOf<Int>()
        var sawGo = false
        repeat(60 * 4) {
            engine.step(1.0 / 60.0)
            for (event in engine.drainEvents()) {
                if (event is GameEvent.CountdownTick) ticks += event.number
                if (event == GameEvent.Go) sawGo = true
            }
        }
        assertEquals(listOf(3, 2, 1), ticks)
        assertTrue(sawGo)
        assertEquals(GameEngine.Phase.RUNNING, engine.phase)
    }

    @Test fun sameSeedProducesSameHazards() {
        val config = RunConfig(GameMode.DAILY, Difficulty.NORMAL, RunModifier.NONE, seed = 123, dailyKey = "x")
        val a = GameEngine(config, phonePlayfield)
        val b = GameEngine(config, phonePlayfield)
        // Keep the player out of the way so both runs last the full window.
        a.setTarget(Vec2(0.0, 70.0))
        b.setTarget(Vec2(0.0, 70.0))
        run(a, seconds = 8.0)
        run(b, seconds = 8.0)
        assertEquals(a.hazards.map { it.position }, b.hazards.map { it.position })
        assertEquals(a.pickups.map { it.kind }, b.pickups.map { it.kind })
    }

    @Test fun standingStillEventuallyEndsEndless() {
        val engine = GameEngine(RunConfig(GameMode.ENDLESS, Difficulty.NORMAL, seed = 99), phonePlayfield)
        run(engine, seconds = 600.0)
        assertTrue(engine.isFinished)
        assertEquals(1, engine.makeResult().hits)
    }

    @Test fun timeAttackEndsWhenClockRunsOut() {
        val engine = GameEngine(RunConfig(GameMode.TIME_ATTACK, Difficulty.NORMAL, seed = 5), phonePlayfield)
        run(engine, seconds = 400.0)
        assertTrue(engine.isFinished)
        assertEquals(0.0, engine.clock)
    }

    @Test fun zenNeverEnds() {
        val engine = GameEngine(RunConfig(GameMode.ZEN, Difficulty.NORMAL, seed = 5), phonePlayfield)
        run(engine, seconds = 180.0)
        assertFalse(engine.isFinished)
        engine.endRun()
        assertTrue(engine.isFinished)
    }

    @Test fun targetIsClampedToPlayfield() {
        val engine = GameEngine(RunConfig(GameMode.ZEN, Difficulty.NORMAL, seed = 5), phonePlayfield)
        engine.setTarget(Vec2(-500.0, 5_000.0))
        run(engine, seconds = 1.0)
        assertTrue(engine.playerPosition.x >= engine.playerRadius)
        assertTrue(engine.playerPosition.y <= phonePlayfield.playerMaxY + 0.001)
    }

    @Test fun runStartsAtBaseMultiplier() {
        val engine = GameEngine(RunConfig(GameMode.ENDLESS, Difficulty.NORMAL, seed = 5), phonePlayfield)
        assertEquals(1, engine.multiplier)
        assertEquals(0, engine.combo)
        assertEquals(0, engine.displayScore)
    }

    @Test fun levelsGetLonger() {
        assertEquals(1, GameEngine.level(0.0))
        assertEquals(1, GameEngine.level(19.9))
        assertEquals(2, GameEngine.level(20.0))
        assertEquals(6, GameEngine.level(150.0))
        assertEquals(10, GameEngine.level(360.0))
        assertEquals(11, GameEngine.level(420.0))
        val starts = GameEngine.levelStartTimes
        for (i in 2 until starts.size) {
            assertTrue(starts[i] - starts[i - 1] >= starts[i - 1] - starts[i - 2])
        }
    }

    @Test fun difficultyCurveRampsFastThenPlateausThenEscalates() {
        var previous = -1.0
        for (second in 0..600) {
            val value = GameEngine.endlessIntensity(second.toDouble())
            assertTrue(value >= previous)
            previous = value
        }
        // Tougher sooner than the old linear ramp (0.3 at 45s)…
        assertTrue(GameEngine.endlessIntensity(45.0) >= 0.45)
        // …but the middle stays fair for a long time (old ramp was 1.0 at 150s)…
        assertTrue(GameEngine.endlessIntensity(150.0) < 0.75)
        assertTrue(GameEngine.endlessIntensity(240.0) < 0.9)
        // …before it goes wild.
        assertEquals(1.6, GameEngine.endlessIntensity(480.0))
    }

    @Test fun playfieldClampsExtremeAspectRatios() {
        assertEquals(720.0, Playfield.fitting(820.0, 1180.0).height)
        assertEquals(880.0, Playfield.fitting(300.0, 1000.0).height)
    }
}

// MARK: - Daily

class DailyTests {
    @Test fun dayKeyFormatting() {
        assertEquals("2026-01-05", DailyChallenge.dayKey(date(2026, 1, 5), utc))
    }

    @Test fun dailyConfigIsStableForADay() {
        val morning = DailyChallenge.config(date(2026, 9, 29, hour = 1), utc)
        val evening = DailyChallenge.config(date(2026, 9, 29, hour = 23), utc)
        assertEquals(morning.seed, evening.seed)
        assertEquals(morning.modifier, evening.modifier)
        assertNotEquals(RunModifier.NONE, morning.modifier)
        assertEquals(Difficulty.NORMAL, morning.difficulty)
    }

    @Test fun dailySeedsDifferAcrossDays() {
        val seeds = (1..28).map { DailyChallenge.seed(String.format("2026-02-%02d", it)) }.toSet()
        assertEquals(28, seeds.size)
    }

    @Test fun missionsAreDeterministicAndDistinct() {
        for (day in 1..60) {
            val key = "2026-03-$day"
            val missions = MissionGenerator.missions(key)
            assertEquals(MissionGenerator.missions(key), missions)
            assertEquals(3, missions.size)
            assertEquals(3, missions.map { it.kind }.toSet().size)
            missions.forEachIndexed { slot, mission ->
                if (slot > 0) assertNotEquals(MissionKind.PLAY_DAILY, mission.kind)
            }
        }
    }

    @Test fun singleRunMissionsTrackBest() {
        val mission = MissionProgress(id = "m", kind = MissionKind.SURVIVE_ENDLESS, goal = 60, reward = 100)
        mission.apply(sampleRun(duration = 40.0))
        mission.apply(sampleRun(duration = 20.0))
        assertEquals(40, mission.progress)
        mission.apply(sampleRun(duration = 75.0))
        assertTrue(mission.isComplete)
        assertEquals(60, mission.progress)
    }

    @Test fun cumulativeMissionsAddUp() {
        val mission = MissionProgress(id = "m", kind = MissionKind.COLLECT_STARS, goal = 30, reward = 60)
        mission.apply(sampleRun(stars = 12))
        mission.apply(sampleRun(stars = 12))
        assertEquals(24, mission.progress)
        mission.apply(sampleRun(stars = 12))
        assertEquals(30, mission.progress)
    }
}

// MARK: - Progression

class ProgressionTests {
    @Test fun levelCurveRoundTrips() {
        for (level in 1..60) {
            assertEquals(level, Leveling.level(Leveling.totalXP(level)))
            assertEquals(maxOf(1, level - 1), Leveling.level(Leveling.totalXP(level) - 1))
        }
        assertEquals(0.0, Leveling.progress(0))
    }

    @Test fun recordingARunUpdatesEverything() {
        val profile = PlayerProfile()
        val now = date(2026, 9, 29)
        val rewards = Progression.record(sampleRun(duration = 45.0), profile, now, utc)
        assertEquals(1, profile.stats.gamesPlayed)
        assertEquals(45, profile.stats.bestEndlessTime)
        assertEquals(1_500, profile.stats.bestEndlessScore)
        assertTrue(rewards.isNewBest)
        assertEquals(1, rewards.localRank)
        assertTrue("survive_30" in rewards.newAchievements)
        assertTrue("endless_1k" in rewards.newAchievements)
        assertEquals(rewards.totalCoins, profile.coins)
        assertEquals(3, profile.daily.missions.size)
        assertEquals(1, profile.localLeaderboard(GameMode.ENDLESS).size)
    }

    @Test fun secondWorseRunIsNotABest() {
        val profile = PlayerProfile()
        val now = date(2026, 9, 29)
        Progression.record(sampleRun(score = 2_000), profile, now, utc)
        val rewards = Progression.record(sampleRun(score = 1_000), profile, now, utc)
        assertFalse(rewards.isNewBest)
        assertEquals(2, rewards.localRank)
    }

    @Test fun leaderboardKeepsTopTen() {
        var board: List<LeaderboardEntry> = emptyList()
        for (score in 1..15) {
            board = Progression.insert(sampleRun(score = score * 100), board).first
        }
        assertEquals(10, board.size)
        assertEquals(1_500, board.first().score)
        assertNull(Progression.insert(sampleRun(score = 1), board).second)
    }

    @Test fun dailyRunCountsOncePerDay() {
        val profile = PlayerProfile()
        val now = date(2026, 9, 29)
        val key = DailyChallenge.dayKey(now, utc)
        Progression.record(sampleRun(mode = GameMode.DAILY, score = 900, dailyKey = key), profile, now, utc)
        Progression.record(sampleRun(mode = GameMode.DAILY, score = 1_200, dailyKey = key), profile, now, utc)
        assertEquals(1, profile.stats.dailyRunsCompleted)
        assertEquals(2, profile.daily.dailyRunAttempts)
        assertEquals(1_200, profile.daily.dailyRunBest)
        assertEquals(2, profile.localLeaderboard(GameMode.DAILY).size)

        // Next day the board resets.
        Progression.refreshDaily(profile, date(2026, 9, 30), utc)
        assertTrue(profile.localLeaderboard(GameMode.DAILY).isEmpty())
        assertEquals(0, profile.daily.dailyRunBest)
    }

    @Test fun loginStreakGrowsAndResets() {
        val profile = PlayerProfile()
        val day1 = date(2026, 9, 1)
        Progression.refreshDaily(profile, day1, utc)
        assertEquals(25, Progression.claimLoginReward(profile, day1, utc).coins)
        assertEquals(0, Progression.claimLoginReward(profile, day1, utc).coins)

        val day2 = date(2026, 9, 2)
        Progression.refreshDaily(profile, day2, utc)
        assertEquals(50, Progression.claimLoginReward(profile, day2, utc).coins)
        assertEquals(2, profile.stats.loginStreak)

        val day4 = date(2026, 9, 4)
        Progression.refreshDaily(profile, day4, utc)
        assertEquals(1, Progression.nextLoginStreakDay(profile, day4, utc))
        assertEquals(25, Progression.claimLoginReward(profile, day4, utc).coins)
        assertEquals(2, profile.stats.longestLoginStreak)
    }

    @Test fun purchasingCosmetics() {
        val profile = PlayerProfile()
        val now = date(2026, 9, 29)
        assertEquals(PurchaseResult.NotEnoughCoins(1_500), Progression.purchase("skin.neon", profile, now))
        profile.coins = 1_600
        assertEquals(PurchaseResult.Success, Progression.purchase("skin.neon", profile, now))
        assertEquals("skin.neon", profile.equippedSkin)
        assertEquals(1, profile.stats.cosmeticsPurchased)
        assertNotNull(profile.unlockedAchievements["shop_1"])
        // 1,600 - 1,500 + 25 (bronze achievement)
        assertEquals(125, profile.coins)
        assertEquals(PurchaseResult.AlreadyOwned, Progression.purchase("skin.neon", profile, now))
        // Earned-only items can never be bought.
        profile.coins = 1_000_000
        assertEquals(PurchaseResult.Locked, Progression.purchase("skin.gold", profile, now))
    }

    @Test fun gatedItemsNeedTheirGateBeforePurchase() {
        val profile = PlayerProfile()
        val now = date(2026, 9, 29)
        profile.coins = 100_000
        // Plasma needs level 10.
        assertEquals(PurchaseResult.Locked, Progression.purchase("skin.plasma", profile, now))
        profile.xp = Leveling.totalXP(10)
        assertEquals(PurchaseResult.Success, Progression.purchase("skin.plasma", profile, now))
    }

    @Test fun earnedCosmeticsUnlockAutomatically() {
        val profile = PlayerProfile()
        profile.stats.bestEndlessTime = 181
        var unlocked = Progression.unlockEarnedCosmetics(profile)
        assertEquals(listOf("skin.frost"), unlocked)
        // Gold also needs level 15.
        profile.xp = Leveling.totalXP(15)
        unlocked = Progression.unlockEarnedCosmetics(profile)
        assertTrue("skin.gold" in unlocked)
        assertFalse("skin.void" in unlocked)
    }

    @Test fun claimingAllMissionsPaysBonusAndBuildsStreak() {
        val profile = PlayerProfile()
        for (day in 1..8) {
            val now = date(2026, 9, day)
            Progression.refreshDaily(profile, now, utc)
            for (mission in profile.daily.missions) mission.progress = mission.goal
            assertEquals(3, Progression.claimableMissionCount(profile))
            val claims = mutableListOf<MissionClaim>()
            for (mission in profile.daily.missions.toList()) {
                claims += Progression.claimMission(mission.id, profile, now, utc)
            }
            assertEquals(1, claims.count { it.allClear })
            assertTrue(claims.last().allClear)
            assertEquals(0, Progression.claimableMissionCount(profile))
        }
        assertEquals(8, profile.stats.allClearStreak)
        assertEquals(8, profile.stats.allClearDays)
        assertTrue("skin.sakura" in profile.unlockedCosmetics)
        assertNotNull(profile.unlockedAchievements["allclear_streak_7"])

        // Skipping a day resets the current streak but keeps the record.
        Progression.refreshDaily(profile, date(2026, 9, 10), utc)
        assertEquals(0, profile.stats.allClearStreak)
        assertEquals(8, profile.stats.longestAllClearStreak)
    }

    /** Regression: one great run used to unlock most of the shop. */
    @Test fun oneMonsterRunCannotBuyOutTheShop() {
        val profile = PlayerProfile()
        val run = RunResult(
            mode = GameMode.ENDLESS, difficulty = Difficulty.NORMAL, modifier = RunModifier.NONE, dailyKey = null,
            score = 9_000, duration = 200.0, stars = 70, nearMisses = 40, perfectMisses = 12, dodged = 500,
            powerUps = 6, hits = 1, maxCombo = 45, maxMultiplier = 6, bestNovaClear = 8, levelReached = 7,
            date = date(2026, 9, 29).toEpochMilli(),
        )
        Progression.record(run, profile, date(2026, 9, 29), utc)
        val affordable = Cosmetic.catalog.filter {
            Progression.isPurchasable(it, profile) && (it.price ?: Int.MAX_VALUE) <= profile.coins
        }
        val owned = profile.unlockedCosmetics - Cosmetic.defaultUnlocked
        assertTrue(affordable.size <= 2, "Affordable after one run: ${affordable.map { it.id }}")
        assertTrue(owned.size <= 2, "Unlocked after one run: $owned")
    }
}

// MARK: - Catalog integrity

class CatalogTests {
    @Test fun idsAreUnique() {
        assertEquals(AchievementCatalog.all.size, AchievementCatalog.all.map { it.id }.toSet().size)
        assertEquals(Cosmetic.catalog.size, Cosmetic.catalog.map { it.id }.toSet().size)
    }

    @Test fun catalogSizesMatchIOS() {
        assertEquals(46, AchievementCatalog.all.size)
        assertEquals(15, Cosmetic.items(CosmeticCategory.SKIN).size)
        assertEquals(13, Cosmetic.items(CosmeticCategory.TRAIL).size)
        assertEquals(8, Cosmetic.items(CosmeticCategory.THEME).size)
    }

    @Test fun achievementGatesReferenceRealAchievements() {
        for (cosmetic in Cosmetic.catalog) {
            for (gate in cosmetic.gates) {
                if (gate is UnlockGate.Achievement) {
                    assertNotNull(AchievementCatalog.find(gate.id), "Missing achievement ${gate.id}")
                }
            }
        }
    }

    @Test fun defaultsExistAndAreFree() {
        for (id in listOf(Cosmetic.DEFAULT_SKIN, Cosmetic.DEFAULT_TRAIL, Cosmetic.DEFAULT_THEME)) {
            assertEquals(true, Cosmetic.find(id)?.isFree)
        }
    }

    @Test fun rarerItemsAreHarderToGet() {
        for (cosmetic in Cosmetic.catalog.filter { !it.isFree }) {
            when (cosmetic.rarity) {
                CosmeticRarity.COMMON -> assertTrue((cosmetic.price ?: 0) >= 1_000 || cosmetic.gates.isNotEmpty())
                CosmeticRarity.RARE, CosmeticRarity.EPIC ->
                    assertTrue(cosmetic.gates.isNotEmpty() || (cosmetic.price ?: 0) >= 5_000)
                CosmeticRarity.LEGENDARY, CosmeticRarity.MYTHIC ->
                    assertTrue(cosmetic.gates.isNotEmpty(), "${cosmetic.id} needs a gate")
            }
        }
        // Mythics can't be bought at all.
        assertTrue(Cosmetic.catalog.filter { it.rarity == CosmeticRarity.MYTHIC }.all { it.price == null })
    }

    @Test fun trailColoursAreValid() {
        for (cosmetic in Cosmetic.catalog) {
            val trail = cosmetic.trail ?: continue
            if (!trail.hasRibbon) continue
            for (index in 0 until trail.length) {
                val color = trail.color(index.toDouble() / trail.length, index, 1.3)
                assertTrue(color.red in 0.0..1.0 && color.green in 0.0..1.0 && color.blue in 0.0..1.0)
            }
        }
    }

    @Test fun everyCosmeticHasItsLook() {
        for (cosmetic in Cosmetic.catalog) {
            when (cosmetic.category) {
                CosmeticCategory.SKIN -> assertNotNull(cosmetic.skin)
                CosmeticCategory.TRAIL -> assertNotNull(cosmetic.trail)
                CosmeticCategory.THEME -> assertNotNull(cosmetic.theme)
            }
        }
    }
}

// MARK: - Persistence

class PersistenceTests {
    @Test fun profileRoundTrips() {
        val repo = ProfileRepository(InMemoryKeyValueStore())
        val profile = repo.load()
        profile.coins = 321
        profile.unlockedCosmetics = profile.unlockedCosmetics + "skin.neon"
        profile.equippedSkin = "skin.neon"
        repo.save(profile)
        val loaded = repo.load()
        assertEquals(321, loaded.coins)
        assertEquals("skin.neon", loaded.equippedSkin)
        assertEquals(profile, loaded)
    }

    @Test fun olderSavesMissingNewFieldsStillLoad() {
        val store = InMemoryKeyValueStore()
        val old = PlayerProfile()
        old.version = 2
        old.coins = 4_321
        old.unlockedCosmetics = old.unlockedCosmetics + "skin.phoenix"
        old.equippedSkin = "skin.phoenix"
        // Strip fields that didn't exist in v2.
        val json = ProfileRepository.json.parseToJsonElement(ProfileRepository.encode(old)).jsonObject
        val stats = json.getValue("stats").jsonObject
        val trimmedStats = JsonObject(stats - setOf("allClearDays", "allClearStreak", "longestAllClearStreak"))
        val trimmed = JsonObject(json - "lastAllClearDay" + ("stats" to trimmedStats))
        store.putString(ProfileRepository.PROFILE_KEY, trimmed.toString())

        val loaded = ProfileRepository(store).load()
        assertEquals(4_321, loaded.coins)
        assertEquals(PlayerProfile.CURRENT_VERSION, loaded.version)
        // v3 re-locks cosmetics from the old, too-generous economy.
        assertFalse("skin.phoenix" in loaded.unlockedCosmetics)
        assertEquals(Cosmetic.DEFAULT_SKIN, loaded.equippedSkin)
    }

    @Test fun unknownFieldsAndJunkAreTolerated() {
        val store = InMemoryKeyValueStore()
        val json = ProfileRepository.json.parseToJsonElement(ProfileRepository.encode(PlayerProfile().apply { coins = 77 })).jsonObject
        store.putString(ProfileRepository.PROFILE_KEY, JsonObject(json + ("fromTheFuture" to JsonPrimitive(true))).toString())
        assertEquals(77, ProfileRepository(store).load().coins)

        store.putString(ProfileRepository.PROFILE_KEY, "not json")
        assertEquals(PlayerProfile(), ProfileRepository(store).load())
    }

    @Test fun deepCopyIsIndependent() {
        val profile = PlayerProfile()
        Progression.refreshDaily(profile, date(2026, 9, 29), utc)
        val copy = profile.deepCopy()
        copy.daily.missions.first().progress = 999
        copy.stats.gamesPlayed = 5
        assertEquals(0, profile.daily.missions.first().progress)
        assertEquals(0, profile.stats.gamesPlayed)
    }
}

// MARK: - Audio

class SynthTests {
    @Test fun everyEffectProducesAudibleClippingFreeSamples() {
        for (effect in SoundEffect.entries) {
            val samples = SynthSamples.samples(effect, sampleRate = 22_050.0)
            assertTrue(samples.isNotEmpty(), "$effect is empty")
            assertTrue(samples.all { abs(it) <= 1 }, "$effect clips")
            assertTrue((samples.maxOfOrNull { abs(it) } ?: 0f) > 0.01f, "$effect is silent")
        }
    }

    @Test fun musicLoopIsEightSeconds() {
        val loop = SynthSamples.musicLoop(sampleRate = 8_000.0)
        assertEquals(64_000, loop.size)
        assertTrue(loop.all { abs(it) <= 1 })
    }
}
