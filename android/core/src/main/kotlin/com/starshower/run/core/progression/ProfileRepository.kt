//
//  ProfileRepository.kt
//  Starshower Run
//
//  Loads and saves the PlayerProfile as JSON in a key-value store
//  (SharedPreferences on device, an in-memory map in tests).
//

package com.starshower.run.core.progression

import kotlinx.serialization.json.Json

/** Minimal string storage so the repository stays platform-neutral. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

class InMemoryKeyValueStore : KeyValueStore {
    private val values = HashMap<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

class ProfileRepository(private val store: KeyValueStore) {

    fun load(): PlayerProfile {
        val stored = store.getString(PROFILE_KEY) ?: return PlayerProfile()
        val profile = decode(stored) ?: return PlayerProfile()
        return sanitized(migrated(profile))
    }

    fun save(profile: PlayerProfile) {
        store.putString(PROFILE_KEY, encode(profile))
    }

    fun reset() {
        store.remove(PROFILE_KEY)
    }

    // MARK: Version migration

    private fun migrated(profile: PlayerProfile): PlayerProfile {
        if (profile.version < 3) {
            // v3 rebalanced the economy: cosmetics are re-locked and must be
            // earned again under the new rules (coins and stats are kept).
            profile.unlockedCosmetics = Cosmetic.defaultUnlocked
            Progression.unlockEarnedCosmetics(profile)
        }
        profile.version = PlayerProfile.CURRENT_VERSION
        return profile
    }

    /** Makes sure defaults are present even if the catalog changed between versions. */
    private fun sanitized(profile: PlayerProfile): PlayerProfile {
        profile.unlockedCosmetics = profile.unlockedCosmetics + Cosmetic.defaultUnlocked
        fun valid(id: String) = Cosmetic.find(id) != null && id in profile.unlockedCosmetics
        if (!valid(profile.equippedSkin)) profile.equippedSkin = Cosmetic.DEFAULT_SKIN
        if (!valid(profile.equippedTrail)) profile.equippedTrail = Cosmetic.DEFAULT_TRAIL
        if (!valid(profile.equippedTheme)) profile.equippedTheme = Cosmetic.DEFAULT_THEME
        return profile
    }

    companion object {
        const val PROFILE_KEY = "vr.profile.v2"

        /**
         * Missing fields fall back to their defaults and unknown ones are
         * ignored, so saves load safely across versions in both directions.
         */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        fun encode(profile: PlayerProfile): String = json.encodeToString(PlayerProfile.serializer(), profile)

        fun decode(text: String): PlayerProfile? =
            runCatching { json.decodeFromString(PlayerProfile.serializer(), text) }.getOrNull()
    }
}

/** A fully independent copy, safe to mutate without touching the original. */
fun PlayerProfile.deepCopy(): PlayerProfile =
    ProfileRepository.decode(ProfileRepository.encode(this)) ?: PlayerProfile()
