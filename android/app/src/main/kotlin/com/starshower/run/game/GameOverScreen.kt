//
//  GameOverScreen.kt
//  Starshower Run
//
//  Results screen: animated score, stats, rewards, unlocks and sharing.
//

package com.starshower.run.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starshower.run.LocalAppGraph
import com.starshower.run.LocalPlatform
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.RunModifier
import com.starshower.run.core.progression.AchievementCatalog
import com.starshower.run.core.progression.Cosmetic
import com.starshower.run.core.progression.Leveling
import com.starshower.run.core.progression.clockString
import com.starshower.run.core.progression.formatted
import com.starshower.run.services.Haptic
import com.starshower.run.ui.Chip
import com.starshower.run.ui.CoinBadge
import com.starshower.run.ui.CosmeticThumbnail
import com.starshower.run.ui.GhostButton
import com.starshower.run.ui.LevelRing
import com.starshower.run.ui.NeonButton
import com.starshower.run.ui.ProgressBar
import com.starshower.run.ui.SectionTitle
import com.starshower.run.ui.Symbol
import com.starshower.run.ui.VR
import com.starshower.run.ui.color
import com.starshower.run.ui.glassCard
import kotlinx.coroutines.delay
import kotlin.math.pow

@Composable
fun GameOverScreen(summary: RunSummary, onReplay: () -> Unit, onHome: () -> Unit) {
    val graph = LocalAppGraph.current
    val platform = LocalPlatform.current
    val result = summary.result
    val rewards = summary.rewards
    val mode = result.mode

    var displayedScore by remember(summary) { mutableIntStateOf(0) }
    val reveal = remember(summary) { Animatable(0f) }

    LaunchedEffect(summary) {
        reveal.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 160f))
    }
    LaunchedEffect(summary) {
        val target = result.score
        if (target <= 0) return@LaunchedEffect
        delay(250)
        val steps = 30
        for (step in 1..steps) {
            val eased = 1 - (1 - step.toDouble() / steps).pow(3)
            displayedScore = (target * eased).toInt()
            if (step % 4 == 0) graph.sound.play(SoundEffect.TICK)
            delay(30)
        }
        displayedScore = target
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .background(Brush.verticalGradient(0f to mode.accent.color.copy(alpha = 0.25f), 0.5f to Color.Transparent)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 40.dp)
                .graphicsLayer {
                    alpha = reveal.value
                    translationY = (1 - reveal.value) * 30.dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Symbol(mode.icon), contentDescription = null, tint = mode.accent.color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(mode.title.uppercase(), style = VR.display(14, FontWeight.Bold).copy(letterSpacing = 2.sp), color = mode.accent.color)
                }
                val headline = when (mode) {
                    GameMode.ZEN -> "Session Complete"
                    GameMode.TIME_ATTACK -> "Time's Up!"
                    else -> if (rewards.isNewBest) "New Record!" else "Game Over"
                }
                Text(headline, style = VR.display(40), color = Color.White, textAlign = TextAlign.Center)
                if (result.modifier != RunModifier.NONE) Chip(result.modifier.title, result.modifier.icon, VR.cyan)
            }

            // Score
            Column(
                Modifier.fillMaxWidth().glassCard(tint = if (rewards.isNewBest) VR.gold else null).padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (rewards.isNewBest) {
                    Text("★ NEW PERSONAL BEST ★", style = VR.display(13, FontWeight.Black).copy(letterSpacing = 1.5.sp), color = VR.gold)
                }
                Text(
                    displayedScore.formatted(),
                    style = VR.display(64).copy(brush = VR.brandGradient),
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mode.isRanked) {
                        val text = if (rewards.isNewBest) "Previous ${rewards.previousBest.formatted()}"
                        else "Best ${maxOf(rewards.previousBest, result.score).formatted()}"
                        IconLabel("trophy.fill", text, VR.secondaryText)
                    }
                    rewards.localRank?.let { IconLabel("list.number", "#$it on your board", VR.secondaryText) }
                }
            }

            // Stats
            val tiles = listOf(
                Triple("clock.fill", result.survivedSeconds.clockString, "Time") to VR.cyan,
                Triple("star.fill", "${result.stars}", "Stars") to VR.gold,
                Triple("scope", "${result.nearMisses}", "Near misses") to VR.pink,
                Triple("multiply.circle.fill", "×${result.maxMultiplier}", "Best combo") to VR.gold,
                Triple("bolt.fill", "${result.powerUps}", "Power-ups") to VR.purple,
                Triple("arrow.left.and.right", "${result.dodged}", "Dodged") to VR.green,
            )
            for (row in tiles.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((tile, tint) in row) {
                        StatTile(tile.first, tile.second, tile.third, tint, Modifier.weight(1f))
                    }
                }
            }

            RewardsCard(summary)

            if (rewards.newAchievements.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().glassCard(tint = VR.gold).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Achievements unlocked", "rosette")
                    for (id in rewards.newAchievements) {
                        val definition = AchievementCatalog.find(id) ?: continue
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(definition.tier.color.color.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Symbol(definition.icon), contentDescription = null, tint = definition.tier.color.color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(definition.title, style = VR.display(16), color = Color.White)
                                Text(definition.detail, style = VR.display(12, FontWeight.Medium), color = VR.secondaryText)
                            }
                            Text("+${definition.reward}", style = VR.display(14), color = VR.gold)
                        }
                    }
                }
            }

            if (rewards.newCosmetics.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().glassCard(tint = VR.purple).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("New unlocks", "sparkles")
                    for (id in rewards.newCosmetics) {
                        val cosmetic = Cosmetic.find(id) ?: continue
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CosmeticThumbnail(cosmetic, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cosmetic.name, style = VR.display(16), color = cosmetic.rarity.color.color)
                                Text(
                                    "${cosmetic.rarity.title} ${cosmetic.category.singularTitle.lowercase()} · equip it in the Armory",
                                    style = VR.display(12, FontWeight.Medium), color = VR.secondaryText,
                                )
                            }
                        }
                    }
                }
            }

            if (rewards.missionsReady.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().glassCard(tint = VR.green).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Symbol("checklist.checked"), contentDescription = null, tint = VR.green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (rewards.missionsReady.size == 1) "A daily mission is ready to claim!"
                        else "${rewards.missionsReady.size} daily missions are ready to claim!",
                        style = VR.display(14, FontWeight.Bold), color = VR.green,
                    )
                }
            }

            // Buttons
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton(
                    if (mode == GameMode.DAILY) "Try Again" else "Play Again",
                    color = mode.accent.color,
                    icon = Symbol("arrow.counterclockwise"),
                ) {
                    graph.haptics.play(Haptic.MEDIUM)
                    onReplay()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GhostButton("Home", Modifier.weight(1f), icon = Symbol("house.fill"), onClick = onHome)
                    GhostButton("Share", Modifier.weight(1f), icon = Symbol("square.and.arrow.up")) {
                        platform.share(shareText(summary))
                    }
                }
            }
        }
    }
}

private fun shareText(summary: RunSummary): String {
    val result = summary.result
    var text = "I scored ${result.score.formatted()} in Starshower Run ${result.mode.title}"
    if (result.mode == GameMode.DAILY && result.modifier != RunModifier.NONE) text += " (${result.modifier.title})"
    text += " and survived ${result.survivedSeconds.clockString} ⚡️ Can you beat it?"
    return text
}

@Composable
private fun RewardsCard(summary: RunSummary) {
    val profile = LocalAppGraph.current.store.profile
    val rewards = summary.rewards
    val (current, needed) = Leveling.xpIntoLevel(profile.xp)
    Column(Modifier.fillMaxWidth().glassCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Rewards", "gift.fill")
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoinBadge(rewards.totalCoins)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RewardLine("Stars", rewards.coinsFromStars)
                RewardLine("Score bonus", rewards.coinsFromScore)
                RewardLine("Level up", rewards.coinsFromLevelUps)
                RewardLine("Achievements", rewards.coinsFromAchievements)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelRing(profile.level, Leveling.progress(profile.xp), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+${rewards.xpGained} XP", style = VR.display(16), color = VR.cyan, modifier = Modifier.weight(1f))
                    Text("$current / $needed", style = VR.display(12, FontWeight.SemiBold), color = VR.secondaryText)
                }
                ProgressBar(Leveling.progress(profile.xp), tint = VR.cyan)
            }
        }
        if (rewards.leveledUp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbol("arrow.up.circle.fill"), contentDescription = null, tint = VR.green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("LEVEL UP! You reached level ${rewards.levelAfter}", style = VR.display(15), color = VR.green)
            }
        }
    }
}

@Composable
private fun RewardLine(title: String, value: Int) {
    if (value > 0) Text("$title +$value", style = VR.display(12, FontWeight.SemiBold), color = VR.secondaryText)
}

@Composable
fun IconLabel(icon: String, text: String, color: Color, size: Int = 13) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Symbol(icon), contentDescription = null, tint = color, modifier = Modifier.size((size + 1).dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = VR.display(size, FontWeight.SemiBold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StatTile(icon: String, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.glassCard(16.dp).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Symbol(icon), contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Text(value, style = VR.display(20), color = Color.White, maxLines = 1)
        Text(label, style = VR.display(11, FontWeight.SemiBold), color = VR.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
