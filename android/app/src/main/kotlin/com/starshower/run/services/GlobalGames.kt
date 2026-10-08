//
//  GlobalGames.kt
//  Starshower Run
//
//  Seam for global leaderboards and achievements – the Android stand-in for
//  iOS Game Center. The default implementation is offline: the game keeps
//  working with personal leaderboards only. To go global, implement this
//  with Google Play Games Services (see android/README.md) and swap it in
//  `AppGraph`.
//

package com.starshower.run.services

import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.RunResult

data class GlobalScore(
    val id: String,
    val rank: Int,
    val name: String,
    val score: Int,
    val isLocalPlayer: Boolean,
)

interface GlobalGames {
    /** Name of the service shown in the UI, e.g. "Google Play Games". */
    val serviceName: String
    val isAvailable: Boolean
    val isSignedIn: Boolean
    val playerName: String?

    fun submit(run: RunResult)
    fun report(achievementIDs: List<String>)
    fun loadTopScores(mode: GameMode, completion: (List<GlobalScore>) -> Unit)
}

/** Used until Play Games Services is configured for this app. */
object OfflineGlobalGames : GlobalGames {
    override val serviceName = "Google Play Games"
    override val isAvailable = false
    override val isSignedIn = false
    override val playerName: String? = null

    override fun submit(run: RunResult) = Unit
    override fun report(achievementIDs: List<String>) = Unit
    override fun loadTopScores(mode: GameMode, completion: (List<GlobalScore>) -> Unit) = completion(emptyList())
}
