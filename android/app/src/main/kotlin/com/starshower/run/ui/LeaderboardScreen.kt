//
//  LeaderboardScreen.kt
//  Starshower Run
//
//  Personal top-10 boards per mode plus global rankings (when a global
//  service such as Google Play Games is configured).
//

package com.starshower.run.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starshower.run.LocalAppGraph
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.progression.DailyChallenge
import com.starshower.run.core.progression.LeaderboardEntry
import com.starshower.run.core.progression.clockString
import com.starshower.run.core.progression.formatted
import com.starshower.run.services.GlobalScore
import com.starshower.run.services.Haptic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class Scope(val title: String) { PERSONAL("Personal"), GLOBAL("Global") }

@Composable
fun LeaderboardScreen(initialMode: GameMode, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    var mode by remember { mutableStateOf(initialMode) }
    var scope by remember { mutableStateOf(Scope.PERSONAL) }

    SubScreen("Leaderboards", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in listOf(GameMode.ENDLESS, GameMode.TIME_ATTACK, GameMode.DAILY)) {
                val selected = mode == item
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .height(60.dp)
                        .pressable {
                            graph.haptics.play(Haptic.SELECTION)
                            mode = item
                        }
                        .background(if (selected) item.accent.color else VR.card, shape)
                        .border(1.dp, if (selected) Color.Transparent else VR.stroke, shape),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val tint = if (selected) Color.Black else Color.White
                    Icon(Symbol(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(item.title, style = VR.display(12, FontWeight.Bold), color = tint, maxLines = 1)
                }
            }
        }

        SegmentedPicker(Scope.entries, scope, { it.title }) { scope = it }

        when (scope) {
            Scope.PERSONAL -> PersonalBoard(mode)
            Scope.GLOBAL -> GlobalBoard(mode)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PersonalBoard(mode: GameMode) {
    val store = LocalAppGraph.current.store
    val entries = store.profile.localLeaderboard(mode)
    if (mode == GameMode.DAILY) {
        val config = DailyChallenge.config(Instant.now())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(config.modifier.title, config.modifier.icon)
            Spacer(Modifier.weight(1f))
            Text("Resets at midnight", style = VR.display(12, FontWeight.SemiBold), color = VR.secondaryText)
        }
    }
    if (entries.isEmpty()) {
        EmptyBoard(
            mode.icon, "No runs yet",
            if (mode == GameMode.DAILY) "Play today's Daily Run to post a score." else "Play ${mode.title} to set your first record.",
        )
    } else {
        entries.forEachIndexed { index, entry ->
            LeaderboardRow(index + 1, formatDate(entry.date), subtitle(entry, mode), entry.score, index == 0, mode.accent.color)
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatDate(epochMillis: Long): String =
    dateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun subtitle(entry: LeaderboardEntry, mode: GameMode): String {
    val parts = mutableListOf(entry.duration.toInt().clockString)
    if (mode != GameMode.DAILY) parts += entry.difficulty.title
    return parts.joinToString(" · ")
}

@Composable
private fun GlobalBoard(mode: GameMode) {
    val games = LocalAppGraph.current.globalGames
    var scores by remember(mode) { mutableStateOf<List<GlobalScore>?>(null) }

    LaunchedEffect(mode, games.isSignedIn) {
        if (!games.isSignedIn) return@LaunchedEffect
        scores = null
        games.loadTopScores(mode) { scores = it }
    }

    when {
        !games.isAvailable -> EmptyBoard(
            "person.crop.circle.badge.questionmark", "Global boards are coming",
            "This build isn't connected to ${games.serviceName} yet. Your personal boards always work offline.",
        )
        !games.isSignedIn -> EmptyBoard(
            "person.crop.circle.badge.questionmark", "Not signed in",
            "Sign in to ${games.serviceName} to compete on global leaderboards. Your personal boards always work offline.",
        )
        scores == null -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        scores!!.isEmpty() -> EmptyBoard("globe", "No global scores yet", "Be the first to post a score on this board!")
        else -> for (score in scores!!) {
            LeaderboardRow(score.rank, score.name, if (score.isLocalPlayer) "You" else "", score.score, score.isLocalPlayer, mode.accent.color)
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, title: String, subtitle: String, score: Int, highlight: Boolean, accent: Color) {
    val medal = when (rank) {
        1 -> VR.gold
        2 -> Color(0.8f, 0.84f, 0.9f)
        3 -> Color(0.82f, 0.55f, 0.3f)
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().glassCard(16.dp, if (highlight) accent else null).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background((medal ?: Color.White).copy(alpha = if (medal == null) 0.08f else 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", style = VR.display(16), color = medal ?: Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = VR.display(15, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = VR.display(12, FontWeight.Medium), color = VR.secondaryText)
        }
        Text(score.formatted(), style = VR.display(20), color = if (highlight) accent else Color.White)
    }
}

@Composable
fun EmptyBoard(icon: String, title: String, message: String) {
    Column(
        Modifier.padding(top = 20.dp).fillMaxWidth().glassCard().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Symbol(icon), contentDescription = null, tint = VR.secondaryText, modifier = Modifier.size(42.dp))
        Text(title, style = VR.display(18), color = Color.White)
        Text(message, style = VR.display(13, FontWeight.Medium), color = VR.secondaryText, textAlign = TextAlign.Center)
    }
}
