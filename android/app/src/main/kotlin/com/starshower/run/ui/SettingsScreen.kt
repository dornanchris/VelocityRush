//
//  SettingsScreen.kt
//  Starshower Run
//
//  Every toggle here is wired into the game.
//

package com.starshower.run.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starshower.run.LocalAppGraph
import com.starshower.run.LocalPlatform
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.engine.Difficulty
import com.starshower.run.core.progression.compactString
import com.starshower.run.services.Haptic
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val platform = LocalPlatform.current
    val prefs = graph.preferences
    val games = graph.globalGames
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    SubScreen("Settings", onBack) {
        SettingsSection("Audio", "speaker.wave.2.fill") {
            SettingsToggle("Sound Effects", "Synthesised arcade SFX", "speaker.wave.3.fill", prefs.soundEnabled) {
                prefs.soundEnabled = it
                if (it) graph.sound.play(SoundEffect.TAP)
            }
            SettingsToggle("Music", "Synthwave groove while you play", "music.note", prefs.musicEnabled) {
                prefs.musicEnabled = it
                if (!it) graph.sound.stopMusic()
            }
            SettingsToggle("Haptics", "Feel every near miss", "iphone.radiowaves.left.and.right", prefs.hapticsEnabled) {
                prefs.hapticsEnabled = it
                if (it) graph.haptics.play(Haptic.MEDIUM)
            }
        }

        SettingsSection("Gameplay", "gamecontroller.fill") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsLabel("Difficulty", "Higher difficulty = bigger score multiplier", "speedometer")
                SegmentedPicker(Difficulty.entries, prefs.difficulty, { it.title }) { prefs.difficulty = it }
                Text(
                    "Score ×${prefs.difficulty.scoreMultiplier.compactString} · Daily Runs always use Normal",
                    style = VR.display(11, FontWeight.SemiBold), color = VR.secondaryText,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsLabel("Drag Sensitivity", "How far the dot moves per swipe", "hand.draw.fill")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Symbol("tortoise.fill"), contentDescription = "Slower", tint = VR.secondaryText, modifier = Modifier.size(18.dp))
                    Slider(
                        value = prefs.sensitivity.toFloat(),
                        onValueChange = { prefs.sensitivity = (it * 10).roundToInt() / 10.0 },
                        valueRange = 0.7f..2.0f,
                        steps = 12,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = VR.cyan, inactiveTrackColor = VR.cardStrong),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Icon(Symbol("hare.fill"), contentDescription = "Faster", tint = VR.secondaryText, modifier = Modifier.size(18.dp))
                }
                Text(String.format("%.1f×", prefs.sensitivity), style = VR.display(11, FontWeight.SemiBold), color = VR.secondaryText)
            }
            SettingsToggle("Show FPS", "Performance overlay", "speedometer", prefs.showFPS) { prefs.showFPS = it }
        }

        SettingsSection("Accessibility", "accessibility") {
            SettingsToggle("Colour-Blind Friendly", "Orange/yellow hazards with outlines", "eye.fill", prefs.colorBlindMode) { prefs.colorBlindMode = it }
            SettingsToggle("High Contrast", "Bolder hazards, darker sky", "circle.lefthalf.filled", prefs.highContrast) { prefs.highContrast = it }
            SettingsToggle("Reduce Motion", "No screen shake, fewer particles", "figure.walk.motion", prefs.reducedMotion) { prefs.reducedMotion = it }
        }

        SettingsSection("Notifications", "bell.fill") {
            SettingsToggle("Daily Reminder", "A nudge at 6pm when a new Daily Run is live", "bell.badge.fill", prefs.notificationsEnabled) { enabled ->
                prefs.notificationsEnabled = enabled
                platform.setDailyReminder(enabled) { isOn ->
                    if (enabled && !isOn) prefs.notificationsEnabled = false
                }
            }
        }

        SettingsSection(games.serviceName, "person.2.fill") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SettingsLabel(
                        title = when {
                            games.isSignedIn -> games.playerName ?: "Signed in"
                            games.isAvailable -> "Not signed in"
                            else -> "Not connected"
                        },
                        subtitle = when {
                            games.isSignedIn -> "Global leaderboards & achievements are on"
                            games.isAvailable -> "Sign in to compete globally"
                            else -> "Global leaderboards aren't set up in this build. Personal boards work offline."
                        },
                        icon = "gamecontroller.fill",
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(10.dp).background(if (games.isSignedIn) VR.green else VR.secondaryText, CircleShape))
            }
        }

        SettingsSection("Data", "externaldrive.fill") {
            SettingsButton("Reset All Progress", "trash.fill", VR.pink) { showResetConfirmation = true }
        }

        SettingsSection("About", "info.circle.fill") {
            SettingsButton("About Starshower Run", "sparkles", VR.cyan) { showAbout = true }
        }

        Text(
            "Starshower Run ${platform.appVersion}",
            style = VR.display(12, FontWeight.SemiBold), color = VR.secondaryText,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset all progress?") },
            text = { Text("This permanently deletes your coins, cosmetics, stats, achievements and records. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    graph.store.resetAllProgress()
                    graph.haptics.play(Haptic.WARNING)
                    showResetConfirmation = false
                }) { Text("Reset", color = VR.pink) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") } },
            containerColor = Color(0xFF1B1530),
        )
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

// MARK: - Building blocks

@Composable
private fun SettingsSection(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title, icon)
        Column(Modifier.fillMaxWidth().glassCard(18.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun SettingsLabel(title: String, subtitle: String, icon: String, tint: Color = VR.cyan) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(Symbol(icon), contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = VR.display(15, FontWeight.Bold), color = Color.White)
            Text(subtitle, style = VR.display(12, FontWeight.Medium), color = VR.secondaryText)
        }
    }
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, icon: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().pressable { onChange(!isOn) }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SettingsLabel(title, subtitle, icon) }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = isOn,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = VR.cyan, checkedThumbColor = Color.White, uncheckedTrackColor = VR.cardStrong),
        )
    }
}

@Composable
private fun SettingsButton(title: String, icon: String, tint: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).background(tint.copy(alpha = 0.15f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(Symbol(icon), contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = VR.display(15, FontWeight.Bold), color = tint, modifier = Modifier.weight(1f))
        Icon(Symbol("chevron.right"), contentDescription = null, tint = VR.secondaryText, modifier = Modifier.size(18.dp))
    }
}

// MARK: - About

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(16.dp)
                .background(Brush.verticalGradient(listOf(VR.backgroundTop, VR.background)), RoundedCornerShape(28.dp)),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Box(Modifier.padding(top = 12.dp)) { Logo() }
                Text("The ultimate dodging challenge.", style = VR.display(16, FontWeight.SemiBold), color = VR.secondaryText)

                Column(Modifier.fillMaxWidth().glassCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("How to play", "gamecontroller.fill")
                    AboutRow("hand.draw.fill", "Drag anywhere on screen to move your dot.")
                    AboutRow("circle.fill", "Dodge the falling hazards. Watch for warnings – meteors are fast!")
                    AboutRow("scope", "Skim past hazards for near-miss points. Closer = PERFECT.")
                    AboutRow("multiply.circle.fill", "Near misses and stars build your multiplier up to ×6.")
                    AboutRow("bolt.fill", "Power-ups: Shield, Slow-Mo, Magnet, Shrink and Nova.")
                }

                Column(Modifier.fillMaxWidth().glassCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Credits", "person.fill")
                    AboutRow("person.crop.circle", "Created by Christopher Dornan")
                    AboutRow("hammer.fill", "Built with Kotlin & Jetpack Compose")
                    AboutRow("waveform", "All sound and music synthesised in code")
                }

                Text("Made with ❤️ for Android", style = VR.display(13, FontWeight.SemiBold), color = VR.secondaryText)
                NeonButton("Done", color = VR.cyan, height = 50.dp, textSize = 17, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AboutRow(icon: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Symbol(icon), contentDescription = null, tint = VR.cyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = VR.display(14, FontWeight.Medium), color = Color.White.copy(alpha = 0.85f))
    }
}
