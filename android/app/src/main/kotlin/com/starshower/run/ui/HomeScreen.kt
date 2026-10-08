//
//  HomeScreen.kt
//  Starshower Run
//
//  Home screen: mode carousel, big Play button and navigation hub.
//

package com.starshower.run.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.starshower.run.LocalAppGraph
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.engine.Difficulty
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.RunConfig
import com.starshower.run.core.progression.DailyChallenge
import com.starshower.run.core.progression.Leveling
import com.starshower.run.core.progression.compactString
import com.starshower.run.core.progression.countdownString
import com.starshower.run.core.progression.formatted
import com.starshower.run.services.Haptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun HomeScreen(onNavigate: (Route) -> Unit, onPlay: (RunConfig) -> Unit) {
    val graph = LocalAppGraph.current
    val prefs = graph.preferences
    val selectedMode = prefs.selectedMode

    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop(tint = selectedMode.accent.color)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            HomeHeader(onNavigate, Modifier.padding(horizontal = 20.dp).padding(top = 8.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Logo() }
            Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
            ModeCarousel(selectedMode) { prefs.selectedMode = it }
            PlayControls(selectedMode, onPlay, Modifier.padding(horizontal = 24.dp).padding(top = 12.dp))
            Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
            NavigationGrid(onNavigate, Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun HomeHeader(onNavigate: (Route) -> Unit, modifier: Modifier = Modifier) {
    val profile = LocalAppGraph.current.store.profile
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.pressable { onNavigate(Route.PROFILE) }, verticalAlignment = Alignment.CenterVertically) {
            LevelRing(profile.level, Leveling.progress(profile.xp), 46.dp)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.playerName, style = VR.display(16, FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Level ${profile.level}", style = VR.display(12, FontWeight.SemiBold), color = VR.secondaryText)
            }
        }
        Spacer(Modifier.weight(1f))
        CoinBadge(profile.coins, modifier = Modifier.pressable { onNavigate(Route.SHOP) })
    }
}

// MARK: - Mode carousel

@Composable
private fun ModeCarousel(selection: GameMode, onSelect: (GameMode) -> Unit) {
    val graph = LocalAppGraph.current
    val modes = GameMode.entries
    val pager = rememberPagerState(initialPage = selection.ordinal) { modes.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager.currentPage) {
        val mode = modes[pager.currentPage]
        if (mode != selection) {
            onSelect(mode)
            graph.haptics.play(Haptic.SELECTION)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(232.dp)) { page ->
            ModeCard(modes[page], Modifier.padding(horizontal = 24.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (mode in modes) {
                val selected = mode == selection
                val width by animateDpAsState(if (selected) 22.dp else 8.dp, spring(stiffness = 500f), label = "dot")
                Box(
                    Modifier
                        .width(width)
                        .height(8.dp)
                        .background(if (selected) mode.accent.color else Color.White.copy(alpha = 0.25f), CircleShape)
                        .pressable { scope.launch { pager.animateScrollToPage(mode.ordinal) } },
                )
            }
        }
    }
}

@Composable
private fun ModeCard(mode: GameMode, modifier: Modifier = Modifier) {
    val store = LocalAppGraph.current.store
    val accent = mode.accent.color
    Column(
        modifier.fillMaxSize().glassCard(24.dp, accent).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(48.dp).neonGlow(accent, 8.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Symbol(mode.icon), contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(mode.title, style = VR.display(24), color = Color.White)
                Text(mode.tagline, style = VR.display(13, FontWeight.SemiBold), color = VR.secondaryText, maxLines = 2)
            }
        }

        if (mode == GameMode.DAILY) {
            DailyDetails()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (rule in mode.rules) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(5.dp).background(accent, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(rule, style = VR.display(13, FontWeight.Medium), color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (mode.isRanked && mode != GameMode.DAILY) {
            IconText("trophy.fill", "Best ${store.best(mode).formatted()}", VR.gold)
        }
    }
}

@Composable
private fun DailyDetails() {
    val store = LocalAppGraph.current.store
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Instant.now()
        }
    }
    val config = DailyChallenge.config(now)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(config.modifier.title, config.modifier.icon, VR.cyan)
            Spacer(Modifier.width(8.dp))
            Text("×${config.modifier.scoreMultiplier.compactString} score", style = VR.display(12, FontWeight.Bold), color = VR.secondaryText)
        }
        Text(config.modifier.detail, style = VR.display(13, FontWeight.Medium), color = Color.White.copy(alpha = 0.8f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconText("trophy.fill", "Today ${store.profile.daily.dailyRunBest.formatted()}", VR.gold)
            Spacer(Modifier.weight(1f))
            IconText("clock", countdownString(DailyChallenge.timeUntilReset(now)), VR.secondaryText)
        }
    }
}

@Composable
fun IconText(icon: String, text: String, color: Color, size: Int = 13) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Symbol(icon), contentDescription = null, tint = color, modifier = Modifier.size((size + 1).dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = VR.display(size, FontWeight.Bold), color = color, maxLines = 1)
    }
}

// MARK: - Play controls

@Composable
private fun PlayControls(selectedMode: GameMode, onPlay: (RunConfig) -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val prefs = graph.preferences
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonButton(
            if (selectedMode == GameMode.DAILY) "PLAY DAILY RUN" else "PLAY ${selectedMode.title.uppercase()}",
            color = selectedMode.accent.color,
            secondary = selectedMode.accent.lighter(0.25).color,
            height = 62.dp,
            icon = Symbol("play.fill"),
        ) {
            graph.haptics.play(Haptic.MEDIUM)
            graph.sound.play(SoundEffect.GO)
            onPlay(
                when (selectedMode) {
                    GameMode.DAILY -> DailyChallenge.config(Instant.now())
                    GameMode.ZEN -> RunConfig(GameMode.ZEN, Difficulty.EASY)
                    else -> RunConfig(selectedMode, prefs.difficulty)
                },
            )
        }

        if (selectedMode == GameMode.ENDLESS || selectedMode == GameMode.TIME_ATTACK) {
            DifficultyPicker(prefs.difficulty) { prefs.difficulty = it }
        } else {
            Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (selectedMode == GameMode.DAILY) "Daily Runs are always played on Normal." else "Zen ignores difficulty.",
                    style = VR.display(12, FontWeight.SemiBold), color = VR.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun DifficultyPicker(selection: Difficulty, onSelect: (Difficulty) -> Unit) {
    val graph = LocalAppGraph.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (level in Difficulty.entries) {
            val selected = level == selection
            Column(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .pressable {
                        graph.haptics.play(Haptic.SELECTION)
                        onSelect(level)
                    }
                    .background(if (selected) VR.cyan else VR.card, CircleShape)
                    .border(1.dp, if (selected) Color.Transparent else VR.stroke, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val color = if (selected) Color.Black else Color.White
                Text(level.title, style = VR.display(13, FontWeight.Bold), color = color)
                Text("×${level.scoreMultiplier.compactString}", style = VR.display(9, FontWeight.SemiBold), color = color, modifier = Modifier.alpha(0.7f))
            }
        }
    }
}

// MARK: - Navigation grid

@Composable
private fun NavigationGrid(onNavigate: (Route) -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val claimable = graph.store.claimableMissions
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tiles = listOf(
            Triple(Route.MISSIONS, "checklist", "Missions"),
            Triple(Route.LEADERBOARDS, "trophy.fill", "Ranks"),
            Triple(Route.SHOP, "bag.fill", "Armory"),
            Triple(Route.PROFILE, "person.fill", "Profile"),
            Triple(Route.SETTINGS, "gearshape.fill", "Settings"),
        )
        for ((route, icon, title) in tiles) {
            Box(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .pressable {
                            graph.haptics.play(Haptic.SELECTION)
                            graph.sound.play(SoundEffect.TAP)
                            onNavigate(route)
                        }
                        .glassCard(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Symbol(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(title, style = VR.display(11, FontWeight.Bold), color = VR.secondaryText, maxLines = 1)
                }
                val badge = if (route == Route.MISSIONS) claimable else 0
                if (badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-6).dp)
                            .size(20.dp)
                            .background(VR.pink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$badge", style = VR.display(11, FontWeight.Black), color = Color.Black)
                    }
                }
            }
        }
    }
}
