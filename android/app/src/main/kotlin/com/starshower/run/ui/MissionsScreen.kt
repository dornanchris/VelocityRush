//
//  MissionsScreen.kt
//  Starshower Run
//
//  Daily missions, the all-clear bonus and the login streak calendar.
//

package com.starshower.run.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.starshower.run.LocalAppGraph
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.progression.DailyChallenge
import com.starshower.run.core.progression.LoginRewards
import com.starshower.run.core.progression.MissionGenerator
import com.starshower.run.core.progression.MissionProgress
import com.starshower.run.core.progression.countdownString
import com.starshower.run.core.progression.formatted
import com.starshower.run.services.Haptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun MissionsScreen(onBack: () -> Unit) {
    val store = LocalAppGraph.current.store
    LaunchedEffect(Unit) { store.refreshDaily() }

    SubScreen("Missions", onBack, actions = { CoinBadge(store.profile.coins, compact = true) }) {
        ResetHeader()
        LoginStreakStrip()
        SectionTitle("Today's missions", "checklist")
        for (mission in store.profile.daily.missions) {
            MissionCard(mission)
        }
        BonusCard()
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Tips", "lightbulb.fill")
            Text(
                "Missions track every mode, including Zen. Single-run goals keep your best attempt, so a great run counts even if later ones don't.",
                style = VR.display(13, FontWeight.Medium), color = VR.secondaryText,
            )
        }
    }
}

@Composable
private fun ResetHeader() {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Instant.now()
        }
    }
    Row(Modifier.fillMaxWidth().glassCard(tint = VR.cyan).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("New missions in", style = VR.display(13, FontWeight.SemiBold), color = VR.secondaryText)
            Text(countdownString(DailyChallenge.timeUntilReset(now)), style = VR.display(28), color = Color.White)
        }
        Icon(Symbol("calendar.badge.clock"), contentDescription = null, tint = VR.cyan, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun BonusCard() {
    val profile = LocalAppGraph.current.store.profile
    val missions = profile.daily.missions
    val claimed = missions.count { it.claimed }
    val done = profile.daily.allMissionsBonusClaimed
    val stats = profile.stats
    val tint = if (done) VR.green else VR.gold
    Column(Modifier.fillMaxWidth().glassCard(tint = tint).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Symbol(if (done) "checkmark.seal.fill" else "gift.fill"), contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("All-Clear Bonus", style = VR.display(17), color = Color.White)
                Text(
                    if (done) "Cleared today – see you tomorrow!"
                    else "Claim all 3 missions for +${MissionGenerator.ALL_COMPLETE_BONUS} coins  ($claimed/${missions.size})",
                    style = VR.display(12, FontWeight.SemiBold), color = VR.secondaryText,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StreakStat(stats.allClearStreak, "Current streak", "flame.fill", VR.pink, Modifier.weight(1f))
            StreakStat(stats.longestAllClearStreak, "Best streak", "trophy.fill", VR.gold, Modifier.weight(1f))
            StreakStat(stats.allClearDays, "All-clear days", "calendar", VR.cyan, Modifier.weight(1f))
        }
        Text(
            "Exclusive rewards: Sakura skin (7-day streak) · Solar Flare theme (14 days) · Prism Storm trail (30-day streak) · Singularity skin (30 days + level 50)",
            style = VR.display(11, FontWeight.SemiBold), color = VR.purple,
        )
    }
}

@Composable
private fun StreakStat(value: Int, label: String, icon: String, tint: Color, modifier: Modifier) {
    Column(
        modifier.background(VR.card, RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        IconText(icon, "$value", tint, size = 17)
        Text(label, style = VR.display(10, FontWeight.SemiBold), color = VR.secondaryText, maxLines = 1)
    }
}

@Composable
private fun MissionCard(mission: MissionProgress) {
    val graph = LocalAppGraph.current
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val ready = mission.isComplete && !mission.claimed

    Row(
        Modifier.fillMaxWidth().scale(bounce.value).glassCard(tint = if (ready) VR.green else null).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(if (mission.claimed) VR.green else VR.cyan.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Symbol(if (mission.claimed) "checkmark" else mission.kind.icon), contentDescription = null,
                tint = if (mission.claimed) Color.Black else VR.cyan, modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                mission.title,
                style = VR.display(15, FontWeight.Bold).copy(textDecoration = if (mission.claimed) TextDecoration.LineThrough else null),
                color = Color.White,
            )
            ProgressBar(mission.fraction, tint = if (mission.isComplete) VR.green else VR.cyan, height = 7.dp)
            Text("${minOf(mission.progress, mission.goal).formatted()} / ${mission.goal.formatted()}", style = VR.display(11, FontWeight.SemiBold), color = VR.secondaryText)
        }
        Spacer(Modifier.width(4.dp))
        if (ready) {
            NeonButton("Claim", Modifier.width(84.dp), color = VR.green, height = 38.dp, textSize = 16) {
                graph.store.claimMission(mission.id)
                graph.sound.play(SoundEffect.COIN)
                graph.haptics.play(Haptic.SUCCESS)
                scope.launch {
                    bounce.animateTo(1.03f, spring(dampingRatio = 0.5f, stiffness = 800f))
                    bounce.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 800f))
                }
            }
        } else {
            Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Symbol("star.circle.fill"), contentDescription = null, tint = VR.gold, modifier = Modifier.size(18.dp))
                Text("${mission.reward}", style = VR.display(13, FontWeight.Bold), color = if (mission.claimed) VR.secondaryText else Color.White)
                Text(
                    "+${MissionGenerator.xpReward(mission)} XP", style = VR.display(9, FontWeight.Bold),
                    color = VR.cyan.copy(alpha = if (mission.claimed) 0.5f else 1f),
                )
            }
        }
    }
}

@Composable
private fun LoginStreakStrip() {
    val store = LocalAppGraph.current.store
    val profile = store.profile
    val claimedToday = !store.isLoginRewardAvailable
    val currentDay = if (claimedToday) profile.stats.loginStreak else store.nextLoginStreakDay
    val cycleStart = ((maxOf(currentDay, 1) - 1) / 7) * 7
    val streak = profile.stats.loginStreak

    Column(Modifier.fillMaxWidth().glassCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Login streak", "flame.fill") {
            Text("$streak day${if (streak == 1) "" else "s"}", style = VR.display(13, FontWeight.Bold), color = VR.pink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (index in 1..7) {
                val day = cycleStart + index
                val isToday = day == currentDay
                val isClaimed = day < currentDay || (isToday && claimedToday)
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (isToday) VR.pink.copy(alpha = 0.2f) else VR.card, shape)
                        .border(1.5.dp, if (isToday) VR.pink else Color.Transparent, shape)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("D$index", style = VR.display(10, FontWeight.Bold), color = VR.secondaryText)
                    Icon(
                        Symbol(if (isClaimed) "checkmark.circle.fill" else if (index == 7) "gift.fill" else "star.circle.fill"),
                        contentDescription = null, tint = if (isClaimed) VR.green else VR.gold, modifier = Modifier.size(19.dp),
                    )
                    Text("${LoginRewards.reward(day)}", style = VR.display(10, FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}

// MARK: - Login reward sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginRewardSheet(onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val store = graph.store
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var claimedAmount by remember { mutableStateOf<Int?>(null) }
    val day = remember { store.nextLoginStreakDay }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF151027),
        contentColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val streak = store.profile.stats.loginStreak
            Text(if (claimedAmount == null) "Daily Reward" else "Reward Claimed!", style = VR.display(28).copy(brush = VR.brandGradient))
            Text(
                if (claimedAmount == null) "Day $day of your streak. Come back tomorrow for more!"
                else "Streak: $streak day${if (streak == 1) "" else "s"} 🔥",
                style = VR.display(14, FontWeight.SemiBold), color = VR.secondaryText, textAlign = TextAlign.Center,
            )
            Box(
                Modifier.size(120.dp).neonGlow(VR.gold, 20.dp).background(VR.gold.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Symbol(if (claimedAmount == null) "gift.fill" else "star.circle.fill"), contentDescription = null, tint = VR.gold, modifier = Modifier.size(56.dp))
            }
            Text("+${claimedAmount ?: LoginRewards.reward(day)} coins", style = VR.display(26), color = Color.White)

            Box(Modifier.padding(horizontal = 40.dp)) {
                if (claimedAmount == null) {
                    NeonButton("Claim", color = VR.gold) {
                        claimedAmount = store.claimLoginReward()
                        graph.sound.play(SoundEffect.COIN)
                        graph.haptics.play(Haptic.SUCCESS)
                    }
                } else {
                    NeonButton("Let's go!", color = VR.cyan) {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }
                }
            }
        }
    }
}
