//
//  ProgressStore.kt
//  Starshower Run
//
//  Observable wrapper around the pure progression rules. Every mutation is
//  saved immediately and new achievements / unlocks are announced.
//

package com.starshower.run.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.RGBColor
import com.starshower.run.core.engine.RunResult
import com.starshower.run.core.progression.AchievementCatalog
import com.starshower.run.core.progression.Cosmetic
import com.starshower.run.core.progression.MissionClaim
import com.starshower.run.core.progression.MissionGenerator
import com.starshower.run.core.progression.PlayerProfile
import com.starshower.run.core.progression.ProfileRepository
import com.starshower.run.core.progression.Progression
import com.starshower.run.core.progression.PurchaseResult
import com.starshower.run.core.progression.RunRewards
import com.starshower.run.core.progression.deepCopy
import java.time.Instant

class ProgressStore(
    private val repository: ProfileRepository,
    private val toasts: ToastCenter,
    private val globalGames: GlobalGames,
) {
    /** Always replaced, never mutated in place, so Compose sees every change. */
    var profile: PlayerProfile by mutableStateOf(loadInitial())
        private set

    private fun loadInitial(): PlayerProfile {
        val loaded = repository.load()
        val now = Instant.now()
        Progression.refreshDaily(loaded, now)
        Progression.evaluateAchievements(loaded, now)
        Progression.unlockEarnedCosmetics(loaded)
        repository.save(loaded)
        return loaded
    }

    // MARK: Derived

    val level: Int get() = profile.level
    val claimableMissions: Int get() = Progression.claimableMissionCount(profile)
    val isLoginRewardAvailable: Boolean get() = Progression.isLoginRewardAvailable(profile, Instant.now())
    val nextLoginStreakDay: Int get() = Progression.nextLoginStreakDay(profile, Instant.now())

    fun best(mode: GameMode): Int =
        if (mode == GameMode.DAILY) profile.daily.dailyRunBest else profile.stats.best(mode)

    // MARK: Mutations

    fun refreshDaily() {
        mutate { Progression.refreshDaily(it, Instant.now()) }
    }

    /** Records a run. Achievements are shown on the results screen, so no toasts. */
    fun record(run: RunResult): RunRewards {
        val rewards = mutate(announce = false) { Progression.record(run, it, Instant.now()) }
        globalGames.submit(run)
        return rewards
    }

    fun purchase(cosmeticID: String): PurchaseResult =
        mutate { Progression.purchase(cosmeticID, it, Instant.now()) }

    fun equip(cosmeticID: String) {
        mutate { Progression.equip(cosmeticID, it) }
    }

    fun claimMission(missionID: String): MissionClaim {
        val claim = mutate { Progression.claimMission(missionID, it, Instant.now()) }
        if (claim.allClear) {
            toasts.show(Toast(
                icon = "checkmark.seal.fill",
                title = "All-clear bonus!",
                subtitle = "+${MissionGenerator.ALL_COMPLETE_BONUS} coins · ${profile.stats.allClearStreak}-day streak",
                tint = RGBColor.hex(0x6BFF9E),
            ))
        }
        return claim
    }

    fun claimLoginReward(): Int = mutate { Progression.claimLoginReward(it, Instant.now()).coins }

    fun setPlayerName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate { it.playerName = trimmed.take(20) }
    }

    fun resetAllProgress() {
        repository.reset()
        val fresh = PlayerProfile()
        Progression.refreshDaily(fresh, Instant.now())
        profile = fresh
        repository.save(fresh)
    }

    // MARK: Plumbing

    private fun <T> mutate(announce: Boolean = true, body: (PlayerProfile) -> T): T {
        val current = profile
        val updated = current.deepCopy()
        val before = updated.unlockedAchievements.keys
        val cosmeticsBefore = updated.unlockedCosmetics
        val result = body(updated)
        val newIDs = updated.unlockedAchievements.keys - before
        val newCosmetics = Cosmetic.catalog.filter {
            it.id in updated.unlockedCosmetics && it.id !in cosmeticsBefore && it.isEarnedOnly
        }
        if (updated != current) {
            profile = updated
            repository.save(updated)
        }
        if (newIDs.isNotEmpty()) {
            val ordered = AchievementCatalog.all.map { it.id }.filter { it in newIDs }
            globalGames.report(ordered)
            if (announce) toasts.announceAchievements(ordered)
        }
        if (announce) {
            for (cosmetic in newCosmetics) {
                toasts.show(Toast(
                    icon = "sparkles",
                    title = "${cosmetic.rarity.title} ${cosmetic.category.singularTitle.lowercase()} unlocked!",
                    subtitle = cosmetic.name,
                    tint = cosmetic.rarity.color,
                ))
            }
        }
        return result
    }
}
