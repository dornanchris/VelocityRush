//
//  GameScreen.kt
//  Starshower Run
//
//  Full-screen host for a play session: the game canvas plus the Compose
//  HUD, pause menu and results.
//

package com.starshower.run.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.starshower.run.LocalAppGraph
import com.starshower.run.LocalPlatform
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.PickupKind
import com.starshower.run.core.engine.RunConfig
import com.starshower.run.core.engine.RunModifier
import com.starshower.run.core.progression.clockString
import com.starshower.run.core.progression.formatted
import com.starshower.run.services.Haptic
import com.starshower.run.ui.Chip
import com.starshower.run.ui.GhostButton
import com.starshower.run.ui.NeonButton
import com.starshower.run.ui.ProgressBar
import com.starshower.run.ui.Symbol
import com.starshower.run.ui.VR
import com.starshower.run.ui.color
import com.starshower.run.ui.pressable

@Composable
fun GameScreen(config: RunConfig, onQuit: () -> Unit) {
    val graph = LocalAppGraph.current
    val platform = LocalPlatform.current
    val session = remember { GameSession(config, graph) }
    val density = LocalDensity.current.density

    val textMeasurer = rememberTextMeasurer(cacheSize = 64)
    val pickupIcons = PickupKind.entries.associateWith { rememberVectorPainter(Symbol(it.icon)) }
    val warningIcon = rememberVectorPainter(Symbol("exclamationmark.triangle.fill"))
    val assets = remember(textMeasurer) { SceneAssets(textMeasurer, pickupIcons, warningIcon) }
    var frameTick by remember { mutableLongStateOf(0L) }

    val quit = {
        session.teardown()
        onQuit()
    }

    DisposableEffect(Unit) {
        platform.setImmersive(true)
        onDispose {
            platform.setImmersive(false)
            session.teardown()
        }
    }

    // Leaving the app pauses the run.
    LaunchedEffect(graph.isForeground) {
        if (!graph.isForeground) session.pause()
    }

    BackHandler {
        when (session.phase) {
            GameSession.Phase.PLAYING -> session.pause()
            GameSession.Phase.PAUSED -> session.resume()
            GameSession.Phase.FINISHED -> quit()
        }
    }

    // Frame loop: step the simulation once per display frame.
    LaunchedEffect(session) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) session.scene?.update((now - last) / 1_000_000_000.0)
                last = now
                frameTick = now
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { session.makeScene(it.width.toFloat(), it.height.toFloat(), density) }
                .pointerInput(session) {
                    // Relative drag that works anywhere on screen, single finger.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        session.scene?.touchBegan()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            if (delta != Offset.Zero) session.scene?.touchMoved(delta.x, delta.y)
                            change.consume()
                        }
                        session.scene?.touchEnded()
                    }
                },
        ) {
            frameTick // Redraw every frame.
            session.scene?.draw(this, assets)
        }

        AnimatedVisibility(session.phase != GameSession.Phase.FINISHED, enter = fadeIn(), exit = fadeOut()) {
            HUDView(session)
        }

        if (session.settings.showFPS) {
            FpsOverlay(session, frameTick)
        }

        AnimatedVisibility(
            session.phase == GameSession.Phase.PAUSED,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 1.05f),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 1.05f),
        ) {
            PauseMenu(session, onQuit = quit)
        }

        val summary = session.summary
        AnimatedVisibility(
            session.phase == GameSession.Phase.FINISHED && summary != null,
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 200f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            if (summary != null) {
                GameOverScreen(summary, onReplay = { session.restart() }, onHome = quit)
            }
        }
    }
}

@Composable
private fun FpsOverlay(session: GameSession, @Suppress("UNUSED_PARAMETER") tick: Long) {
    val scene = session.scene ?: return
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.BottomEnd) {
        Text(
            "${scene.fps.toInt()} fps · ${scene.nodeCount} nodes",
            style = VR.display(11, FontWeight.Bold),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(10.dp),
        )
    }
}

// MARK: - HUD

@Composable
private fun HUDView(session: GameSession) {
    val graph = LocalAppGraph.current
    val hud = session.hud
    val mode = session.config.mode

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.weight(1f)) { ScoreBlock(session, hud, mode) }
            CenterBlock(hud, mode)
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier
                        .size(44.dp)
                        .pressable {
                            graph.haptics.play(Haptic.SELECTION)
                            session.pause()
                        }
                        .background(Color.White.copy(alpha = 0.14f), CircleShape)
                        .border(1.dp, VR.stroke, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Symbol("pause.fill"), contentDescription = "Pause", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hud.hasShield) PowerUpChip(PickupKind.SHIELD, 1.0)
            for (powerUp in hud.powerUps) PowerUpChip(powerUp.kind, powerUp.fraction)
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(hud.isCountdown, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
            CountdownHint(session)
        }
    }
}

@Composable
private fun ScoreBlock(session: GameSession, hud: HUDState, mode: GameMode) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (mode == GameMode.ZEN) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbol("leaf.fill"), contentDescription = null, tint = VR.green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("${hud.score}", style = VR.display(22), color = VR.green)
            }
        } else {
            Text(
                hud.score.formatted(),
                style = VR.display(34).copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), Offset.Zero, 8f)),
                color = Color.White,
            )
            if (session.personalBest > 0) {
                val beating = hud.score > session.personalBest
                Text(
                    if (beating) "NEW BEST!" else "BEST ${session.personalBest.formatted()}",
                    style = VR.display(11, FontWeight.Bold),
                    color = if (beating) VR.gold else VR.secondaryText,
                )
            }
        }
        AnimatedVisibility(
            hud.multiplier > 1 || hud.comboProgress > 0,
            enter = fadeIn() + slideInHorizontally { -it / 2 },
            exit = fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("×${hud.multiplier}", style = VR.display(16), color = VR.gold)
                Spacer(Modifier.width(6.dp))
                ProgressBar(hud.comboProgress, Modifier.width(60.dp), tint = VR.gold, height = 5.dp)
            }
        }
    }
}

@Composable
private fun CenterBlock(hud: HUDState, mode: GameMode) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (mode) {
            GameMode.TIME_ATTACK -> {
                val seconds = hud.clockTenths / 10.0
                val urgent = seconds <= 10
                Text(
                    if (seconds > 10) "${seconds.toInt()}" else String.format("%.1f", seconds),
                    style = VR.display(40).copy(
                        shadow = androidx.compose.ui.graphics.Shadow((if (urgent) VR.pink else Color.Black).copy(alpha = 0.7f), Offset.Zero, 12f),
                    ),
                    color = if (urgent) VR.pink else Color.White,
                )
                Text("SECONDS", style = VR.display(10, FontWeight.Bold), color = VR.secondaryText)
            }
            GameMode.ZEN -> Text(hud.elapsed.clockString, style = VR.display(26), color = Color.White.copy(alpha = 0.85f))
            else -> {
                Text(hud.elapsed.clockString, style = VR.display(28), color = Color.White)
                Text("LEVEL ${hud.level}", style = VR.display(10, FontWeight.Bold), color = VR.cyan)
            }
        }
    }
}

@Composable
private fun CountdownHint(session: GameSession) {
    Column(
        Modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (session.showsTutorialHint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbol("hand.draw.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Drag anywhere to move", style = VR.display(16, FontWeight.Bold), color = Color.White)
            }
            Text(
                "Dodge the hazards · grab the stars · skim close for bonus points",
                style = VR.display(13, FontWeight.SemiBold), color = VR.secondaryText, textAlign = TextAlign.Center,
            )
        }
        val modifier = session.config.modifier
        if (modifier != RunModifier.NONE) {
            Chip(modifier.title, modifier.icon, VR.cyan)
            Text(modifier.detail, style = VR.display(13, FontWeight.SemiBold), color = VR.secondaryText, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun PowerUpChip(kind: PickupKind, fraction: Double) {
    val color = kind.color.color
    val animated by animateFloatAsState(fraction.toFloat(), tween(250), label = "powerup")
    Box(
        Modifier
            .size(36.dp)
            .drawBehind {
                drawCircle(color.copy(alpha = 0.25f), size.minDimension / 2 + 4.dp.toPx())
                drawCircle(color.copy(alpha = 0.18f))
                val stroke = 3.dp.toPx()
                drawArc(
                    color, -90f, 360f * animated, useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Symbol(kind.icon), contentDescription = kind.title, tint = color, modifier = Modifier.size(16.dp))
    }
}

// MARK: - Pause

@Composable
private fun PauseMenu(session: GameSession, onQuit: () -> Unit) {
    val graph = LocalAppGraph.current
    val prefs = graph.preferences
    val config = session.config

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text("PAUSED", style = VR.display(44).copy(brush = VR.brandGradient))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Chip(config.mode.title, config.mode.icon, config.mode.accent.color)
                if (config.modifier != RunModifier.NONE) Chip(config.modifier.title, config.modifier.icon)
            }

            Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("Resume", color = VR.cyan) { session.resume() }
                GhostButton("Restart") { session.restart() }
                if (config.mode == GameMode.ZEN) {
                    GhostButton("Finish & Collect Stars") { session.finishZenSession() }
                }
                GhostButton("Quit to Menu", color = VR.pink) { onQuit() }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PauseToggle(if (prefs.soundEnabled) "speaker.wave.2.fill" else "speaker.slash.fill", prefs.soundEnabled, "Sound") {
                    prefs.soundEnabled = !prefs.soundEnabled
                }
                PauseToggle("music.note", prefs.musicEnabled, "Music", crossed = !prefs.musicEnabled) {
                    prefs.musicEnabled = !prefs.musicEnabled
                    if (!prefs.musicEnabled) graph.sound.stopMusic()
                }
                PauseToggle("iphone.radiowaves.left.and.right", prefs.hapticsEnabled, "Haptics", crossed = !prefs.hapticsEnabled) {
                    prefs.hapticsEnabled = !prefs.hapticsEnabled
                }
            }

            if (config.mode.isRanked) {
                Text("Quitting forfeits this run.", style = VR.display(12, FontWeight.Medium), color = VR.secondaryText)
            }
        }
    }
}

@Composable
private fun PauseToggle(icon: String, isOn: Boolean, label: String, crossed: Boolean = false, onToggle: () -> Unit) {
    val graph = LocalAppGraph.current
    Box(
        Modifier
            .size(54.dp)
            .pressable {
                onToggle()
                graph.haptics.play(Haptic.SELECTION)
            }
            .background(if (isOn) VR.cardStrong else VR.card, CircleShape)
            .border(1.dp, if (isOn) VR.cyan.copy(alpha = 0.6f) else VR.stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Symbol(icon), contentDescription = label, tint = if (isOn) Color.White else VR.secondaryText, modifier = Modifier.size(22.dp))
        if (crossed) {
            Box(Modifier.width(30.dp).height(2.dp).rotate(-45f).background(VR.secondaryText))
        }
    }
}
