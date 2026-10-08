//
//  ProfileScreen.kt
//  Starshower Run
//
//  Player card, personal bests, lifetime stats and achievements.
//

package com.starshower.run.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starshower.run.LocalAppGraph
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.progression.AchievementCatalog
import com.starshower.run.core.progression.AchievementCategory
import com.starshower.run.core.progression.AchievementDefinition
import com.starshower.run.core.progression.Cosmetic
import com.starshower.run.core.progression.Leveling
import com.starshower.run.core.progression.PlayerStats
import com.starshower.run.core.progression.clockString
import com.starshower.run.core.progression.formatted
import com.starshower.run.services.Haptic
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class ProfileTab(val title: String) { STATS("Stats"), ACHIEVEMENTS("Achievements") }

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val store = LocalAppGraph.current.store
    var tab by remember { mutableStateOf(ProfileTab.STATS) }
    var editingName by remember { mutableStateOf(false) }

    SubScreen("Profile", onBack) {
        PlayerCard(onEditName = { editingName = true })
        SegmentedPicker(ProfileTab.entries, tab, { it.title }) { tab = it }
        when (tab) {
            ProfileTab.STATS -> StatsSection(store.profile.stats)
            ProfileTab.ACHIEVEMENTS -> AchievementsSection()
        }
    }

    if (editingName) {
        var draft by remember { mutableStateOf(store.profile.playerName) }
        AlertDialog(
            onDismissRequest = { editingName = false },
            title = { Text("Player name") },
            text = {
                OutlinedTextField(draft, { draft = it.take(20) }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    store.setPlayerName(draft)
                    editingName = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingName = false }) { Text("Cancel") } },
            containerColor = Color(0xFF1B1530),
        )
    }
}

private fun levelTitle(level: Int): String = when {
    level < 5 -> "Rookie"
    level < 10 -> "Dodger"
    level < 20 -> "Speedster"
    level < 35 -> "Velocity Ace"
    level < 50 -> "Rush Master"
    else -> "Legend"
}

@Composable
private fun PlayerCard(onEditName: () -> Unit) {
    val profile = LocalAppGraph.current.store.profile
    val (current, needed) = Leveling.xpIntoLevel(profile.xp)
    Column(Modifier.fillMaxWidth().glassCard(tint = VR.purple).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(76.dp)
                    .background(Cosmetic.themeLook(profile.equippedTheme).backgroundTop.color, CircleShape)
                    .border(3.dp, VR.brandGradient, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SkinPreview(Cosmetic.skinLook(profile.equippedSkin), 40.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.pressable(onClick = onEditName), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.playerName, style = VR.display(22), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Icon(Symbol("pencil"), contentDescription = "Edit name", tint = VR.secondaryText, modifier = Modifier.size(15.dp))
                }
                Text("Level ${profile.level} · ${levelTitle(profile.level)}", style = VR.display(13, FontWeight.Bold), color = VR.cyan)
                ProgressBar(Leveling.progress(profile.xp), tint = VR.cyan, height = 7.dp)
                Text("${current.formatted()} / ${needed.formatted()} XP", style = VR.display(11, FontWeight.SemiBold), color = VR.secondaryText)
            }
        }
        Row {
            MiniStat(profile.stats.gamesPlayed.formatted(), "Runs", Modifier.weight(1f))
            MiniStat("${profile.unlockedAchievements.size}/${AchievementCatalog.all.size}", "Achievements", Modifier.weight(1f))
            MiniStat(profile.stats.totalCoinsEarned.formatted(), "Coins earned", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = VR.display(17), color = Color.White, maxLines = 1)
        Text(label, style = VR.display(10, FontWeight.SemiBold), color = VR.secondaryText)
    }
}

// MARK: - Stats

@Composable
private fun StatsSection(stats: PlayerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Personal bests", "trophy.fill")
            StatGrid(listOf(
                StatCardData("infinity", "Endless score", stats.bestEndlessScore.formatted(), GameMode.ENDLESS.accent.color),
                StatCardData("clock.fill", "Endless time", stats.bestEndlessTime.clockString, GameMode.ENDLESS.accent.color),
                StatCardData("stopwatch.fill", "Time Attack", stats.bestTimeAttackScore.formatted(), GameMode.TIME_ATTACK.accent.color),
                StatCardData("calendar", "Daily Run", stats.bestDailyScore.formatted(), GameMode.DAILY.accent.color),
                StatCardData("multiply.circle.fill", "Best multiplier", "×${stats.bestMultiplier}", VR.gold),
                StatCardData("gauge.with.dots.needle.67percent", "Highest level", "${stats.bestLevel}", VR.cyan),
                StatCardData("scope", "Near misses (run)", "${stats.bestNearMissesInRun}", VR.pink),
                StatCardData("star.fill", "Stars (run)", "${stats.bestStarsInRun}", VR.gold),
            ))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Lifetime", "chart.bar.fill")
            StatGrid(listOf(
                StatCardData("gamecontroller.fill", "Runs played", stats.gamesPlayed.formatted(), VR.cyan),
                StatCardData("hourglass", "Time survived", formatDuration(stats.totalTimeSurvived), VR.cyan),
                StatCardData("star.fill", "Stars collected", stats.totalStars.formatted(), VR.gold),
                StatCardData("arrow.left.and.right", "Hazards dodged", stats.totalDodged.formatted(), VR.green),
                StatCardData("scope", "Near misses", stats.totalNearMisses.formatted(), VR.pink),
                StatCardData("viewfinder", "Perfect misses", stats.totalPerfectMisses.formatted(), VR.pink),
                StatCardData("bolt.fill", "Power-ups", stats.totalPowerUps.formatted(), VR.purple),
                StatCardData("checklist", "Missions done", stats.missionsCompleted.formatted(), VR.green),
                StatCardData("flame.fill", "Login streak", "${stats.loginStreak} (best ${stats.longestLoginStreak})", VR.pink),
                StatCardData("calendar.badge.checkmark", "Daily Runs", stats.dailyRunsCompleted.formatted(), GameMode.DAILY.accent.color),
            ))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Modes played", "square.grid.2x2.fill")
            for (mode in GameMode.entries) {
                val games = when (mode) {
                    GameMode.ENDLESS -> stats.endlessGames
                    GameMode.TIME_ATTACK -> stats.timeAttackGames
                    GameMode.DAILY -> stats.dailyGames
                    GameMode.ZEN -> stats.zenGames
                }
                Row(Modifier.fillMaxWidth().glassCard(14.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconText(mode.icon, mode.title, mode.accent.color, 15)
                    Spacer(Modifier.weight(1f))
                    Text(games.formatted(), style = VR.display(15, FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}

private data class StatCardData(val icon: String, val title: String, val value: String, val tint: Color)

@Composable
private fun StatGrid(cards: List<StatCardData>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in cards.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (card in row) StatCard(card, Modifier.weight(1f))
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCard(card: StatCardData, modifier: Modifier) {
    Row(modifier.glassCard(16.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(card.tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Symbol(card.icon), contentDescription = null, tint = card.tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.value, style = VR.display(17), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.title, style = VR.display(11, FontWeight.SemiBold), color = VR.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m ${seconds % 60}s"
}

// MARK: - Achievements

@Composable
private fun AchievementsSection() {
    val graph = LocalAppGraph.current
    val profile = graph.store.profile
    var filter by remember { mutableStateOf<AchievementCategory?>(null) }
    val unlockedCount = profile.unlockedAchievements.size
    val total = AchievementCatalog.all.size
    val fraction = unlockedCount.toFloat() / maxOf(total, 1)

    // Unlocked last so there's always something to chase at the top.
    val definitions = AchievementCatalog.all
        .filter { filter == null || it.category == filter }
        .sortedWith(
            compareBy<AchievementDefinition> { profile.unlockedAchievements.containsKey(it.id) }
                .thenByDescending { it.fraction(profile.stats) },
        )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().glassCard(tint = VR.gold).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    drawCircle(Color.White.copy(alpha = 0.1f), size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
                    drawArc(VR.gold, -90f, 360f * fraction, false, Offset(stroke / 2, stroke / 2),
                        Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${(fraction * 100).toInt()}%", style = VR.display(16), color = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$unlockedCount of $total unlocked", style = VR.display(18), color = Color.White)
                Text("Achievements pay out coins – some unlock exclusive cosmetics.", style = VR.display(12, FontWeight.Medium), color = VR.secondaryText)
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(null, "All", "square.grid.2x2", filter) { filter = it; graph.haptics.play(Haptic.SELECTION) }
            for (category in AchievementCategory.entries) {
                FilterChip(category, category.title, category.icon, filter) { filter = it; graph.haptics.play(Haptic.SELECTION) }
            }
        }

        for (definition in definitions) {
            AchievementRow(definition, profile.unlockedAchievements[definition.id], profile.stats)
        }
    }
}

@Composable
private fun FilterChip(category: AchievementCategory?, title: String, icon: String, selection: AchievementCategory?, onSelect: (AchievementCategory?) -> Unit) {
    val selected = category == selection
    Row(
        Modifier
            .height(34.dp)
            .pressable { onSelect(category) }
            .background(if (selected) VR.gold else VR.card, CircleShape)
            .border(1.dp, if (selected) Color.Transparent else VR.stroke, CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (selected) Color.Black else Color.White
        Icon(Symbol(icon), contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = VR.display(13, FontWeight.Bold), color = tint)
    }
}

private val unlockDateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun AchievementRow(definition: AchievementDefinition, unlockedAt: Long?, stats: PlayerStats) {
    val isUnlocked = unlockedAt != null
    val isHidden = definition.secret && !isUnlocked
    val tierColor = definition.tier.color.color
    Row(
        Modifier.fillMaxWidth().alpha(if (isUnlocked || !isHidden) 1f else 0.7f)
            .glassCard(18.dp, if (isUnlocked) tierColor else null).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(if (isUnlocked) tierColor.copy(alpha = 0.2f) else VR.card, CircleShape)
                .border(1.dp, if (isUnlocked) tierColor.copy(alpha = 0.6f) else VR.stroke, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Symbol(if (isHidden) "questionmark" else definition.icon), contentDescription = null,
                tint = if (isUnlocked) tierColor else VR.secondaryText, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isHidden) "Secret achievement" else definition.title, style = VR.display(15, FontWeight.Bold), color = Color.White,
                    modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text(definition.tier.title.uppercase(), style = VR.display(9, FontWeight.Black), color = tierColor)
            }
            Text(if (isHidden) "Keep playing to discover it." else definition.detail, style = VR.display(12, FontWeight.Medium), color = VR.secondaryText)
            if (unlockedAt != null) {
                val date = unlockDateFormatter.format(Instant.ofEpochMilli(unlockedAt).atZone(ZoneId.systemDefault()))
                Text("Unlocked $date", style = VR.display(11, FontWeight.SemiBold), color = VR.green)
            } else if (!isHidden) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(definition.fraction(stats), Modifier.weight(1f), tint = tierColor, height = 6.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${definition.progress(stats).formatted()}/${definition.goal.formatted()}", style = VR.display(10, FontWeight.SemiBold), color = VR.secondaryText)
                }
            }
            val reward = definition.cosmeticReward
            if (reward != null && !isHidden) IconText("sparkles", "Unlocks ${reward.name}", VR.purple, 11)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Symbol("star.circle.fill"), contentDescription = null, tint = VR.gold, modifier = Modifier.size(18.dp))
            Text("${definition.reward}", style = VR.display(12, FontWeight.Bold), color = if (isUnlocked) VR.secondaryText else Color.White)
        }
    }
}
