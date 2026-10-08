//
//  ShopScreen.kt
//  Starshower Run
//
//  The Armory: skins, blade trails and themes. Commons can be bought, rarer
//  items need levels, personal bests or streaks, and the best are earn-only.
//

package com.starshower.run.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starshower.run.LocalAppGraph
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.progression.Cosmetic
import com.starshower.run.core.progression.CosmeticCategory
import com.starshower.run.core.progression.CosmeticRarity
import com.starshower.run.core.progression.PlayerProfile
import com.starshower.run.core.progression.Progression
import com.starshower.run.core.progression.PurchaseResult
import com.starshower.run.core.progression.ThemeLook
import com.starshower.run.core.progression.TrailLook
import com.starshower.run.core.progression.TrailParticles
import com.starshower.run.core.progression.formatted
import com.starshower.run.game.LoadoutPreview
import com.starshower.run.services.Haptic
import com.starshower.run.services.Toast
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

private fun PlayerProfile.isEquipped(cosmetic: Cosmetic) = when (cosmetic.category) {
    CosmeticCategory.SKIN -> equippedSkin == cosmetic.id
    CosmeticCategory.TRAIL -> equippedTrail == cosmetic.id
    CosmeticCategory.THEME -> equippedTheme == cosmetic.id
}

@Composable
fun ShopScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val profile = graph.store.profile
    var category by remember { mutableStateOf(CosmeticCategory.SKIN) }
    var selected by remember { mutableStateOf<Cosmetic?>(null) }

    val items = Cosmetic.items(category).sortedWith(
        compareBy<Cosmetic> { it.rarity.ordinal }.thenBy { it.price ?: Int.MAX_VALUE },
    )

    SubScreen("Armory", onBack, actions = { CoinBadge(profile.coins, compact = true) }) {
        val previewShape = RoundedCornerShape(24.dp)
        Box(Modifier.fillMaxWidth().height(190.dp).clip(previewShape).border(1.dp, VR.stroke, previewShape)) {
            LoadoutPreview(profile.equippedSkin, profile.equippedTrail, profile.equippedTheme, Modifier.matchParentSize())
            Text(
                "YOUR LOADOUT",
                style = VR.display(11, FontWeight.Black).copy(letterSpacing = 1.5.sp),
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in CosmeticCategory.entries) {
                val isSelected = category == item
                Row(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .pressable {
                            graph.haptics.play(Haptic.SELECTION)
                            category = item
                        }
                        .background(if (isSelected) VR.cyan else VR.card, CircleShape)
                        .border(1.dp, if (isSelected) Color.Transparent else VR.stroke, CircleShape),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (isSelected) Color.Black else Color.White
                    Icon(Symbol(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(item.title, style = VR.display(14, FontWeight.Bold), color = tint)
                }
            }
        }

        CollectionSummary(category, profile)

        for (row in items.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (cosmetic in row) {
                    CosmeticCard(cosmetic, profile, Modifier.weight(1f)) {
                        graph.haptics.play(Haptic.SELECTION)
                        selected = cosmetic
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Text(
            "Coins come from stars, high scores, daily missions and login rewards. The rarest gear can't be bought – you have to earn it.",
            style = VR.display(12, FontWeight.Medium), color = VR.secondaryText, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }

    selected?.let { cosmetic ->
        CosmeticDetailSheet(cosmetic, onDismiss = { selected = null })
    }
}

@Composable
private fun CollectionSummary(category: CosmeticCategory, profile: PlayerProfile) {
    val all = Cosmetic.items(category)
    val owned = all.count { profile.isUnlocked(it) }
    Row(Modifier.fillMaxWidth().glassCard(16.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$owned of ${all.size} ${category.title.lowercase()} collected", style = VR.display(14, FontWeight.Bold), color = Color.White)
            ProgressBar(owned.toDouble() / maxOf(all.size, 1), tint = VR.cyan, height = 6.dp)
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (rarity in CosmeticRarity.entries) {
                val total = all.count { it.rarity == rarity }
                if (total == 0) continue
                val have = all.count { it.rarity == rarity && profile.isUnlocked(it) }
                Box(Modifier.size(10.dp).background(rarity.color.color.copy(alpha = if (have == total) 1f else 0.25f), CircleShape))
            }
        }
    }
}

// MARK: - Card

@Composable
private fun CosmeticCard(cosmetic: Cosmetic, profile: PlayerProfile, modifier: Modifier, onClick: () -> Unit) {
    val isOwned = profile.isUnlocked(cosmetic)
    val isEquipped = profile.isEquipped(cosmetic)
    val rarity = cosmetic.rarity.color.color
    Column(
        modifier
            .pressable(onClick = onClick)
            .glassCard(18.dp, if (isEquipped) VR.cyan else rarity.copy(alpha = 0.6f))
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(rarity.copy(alpha = 0.18f), Color.Transparent), Offset(size.width / 2, 0f), 140.dp.toPx()))
            }
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.height(80.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.alpha(if (isOwned) 1f else 0.6f)) { CosmeticThumbnail(cosmetic, 60.dp) }
            if (!isOwned && !Progression.gatesMet(cosmetic, profile)) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(26.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Symbol("lock.fill"), contentDescription = "Locked", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
        Text(cosmetic.name, style = VR.display(15, FontWeight.Bold), color = Color.White, maxLines = 1)
        Text(cosmetic.rarity.title.uppercase(), style = VR.display(9, FontWeight.Black).copy(letterSpacing = 1.2.sp), color = rarity)
        Box(Modifier.heightIn(min = 30.dp), contentAlignment = Alignment.Center) {
            CardStatus(cosmetic, profile, isOwned, isEquipped)
        }
    }
}

@Composable
private fun CardStatus(cosmetic: Cosmetic, profile: PlayerProfile, isOwned: Boolean, isEquipped: Boolean) {
    val gate = cosmetic.gates.firstOrNull { !it.isMet(profile) }
    val price = cosmetic.price
    when {
        isEquipped -> Chip("Equipped", "checkmark", VR.cyan)
        isOwned -> Chip("Owned", tint = Color.White)
        gate != null -> {
            val (current, goal) = gate.progress(profile)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(gate.summary, style = VR.display(10, FontWeight.SemiBold), color = VR.secondaryText, maxLines = 2, textAlign = TextAlign.Center)
                ProgressBar(current.toDouble() / maxOf(goal, 1), Modifier.padding(horizontal = 8.dp), tint = cosmetic.rarity.color.color, height = 4.dp)
            }
        }
        price != null -> IconText("star.circle.fill", price.formatted(), if (profile.coins >= price) Color.White else VR.secondaryText, 14)
    }
}

// MARK: - Detail sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CosmeticDetailSheet(cosmetic: Cosmetic, onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val profile = graph.store.profile
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirmPurchase by remember { mutableStateOf(false) }
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    val isOwned = profile.isUnlocked(cosmetic)
    val isEquipped = profile.isEquipped(cosmetic)
    val gatesMet = Progression.gatesMet(cosmetic, profile)
    val rarity = cosmetic.rarity.color.color

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = VR.background, contentColor = Color.White) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .shadow(18.dp, shape, ambientColor = rarity, spotColor = rarity)
                    .clip(shape)
                    .border(1.5.dp, rarity.copy(alpha = 0.6f), shape),
            ) {
                LoadoutPreview(
                    skinID = if (cosmetic.category == CosmeticCategory.SKIN) cosmetic.id else profile.equippedSkin,
                    trailID = if (cosmetic.category == CosmeticCategory.TRAIL) cosmetic.id else profile.equippedTrail,
                    themeID = if (cosmetic.category == CosmeticCategory.THEME) cosmetic.id else profile.equippedTheme,
                    modifier = Modifier.matchParentSize(),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${cosmetic.rarity.title.uppercase()} ${cosmetic.category.singularTitle.uppercase()}",
                    style = VR.display(12, FontWeight.Black).copy(letterSpacing = 2.sp), color = rarity,
                )
                Text(cosmetic.name, style = VR.display(34), color = Color.White)
                Text(cosmetic.detail, style = VR.display(14, FontWeight.Medium), color = VR.secondaryText, textAlign = TextAlign.Center)
            }

            if (!cosmetic.isFree) Requirements(cosmetic, profile, isOwned)

            val price = cosmetic.price
            when {
                isEquipped -> GhostButton("Equipped", enabled = false) {}
                isOwned -> NeonButton("Equip", color = VR.cyan) {
                    graph.store.equip(cosmetic.id)
                    graph.sound.play(SoundEffect.TAP)
                    graph.haptics.play(Haptic.SUCCESS)
                    close()
                }
                !gatesMet -> InfoCapsule(if (cosmetic.isEarnedOnly) "Earn it to unlock" else "Meet the requirements to buy", Symbol("lock.fill"))
                price != null && profile.coins >= price -> NeonButton("Buy for ${price.formatted()}", color = VR.gold, icon = Symbol("star.circle.fill")) {
                    confirmPurchase = true
                }
                price != null -> InfoCapsule("Need ${(price - profile.coins).formatted()} more coins", Symbol("star.circle"))
            }
        }
    }

    if (confirmPurchase) {
        AlertDialog(
            onDismissRequest = { confirmPurchase = false },
            title = { Text("Buy ${cosmetic.name}?") },
            text = { Text("You have ${profile.coins.formatted()} coins.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPurchase = false
                    if (graph.store.purchase(cosmetic.id) == PurchaseResult.Success) {
                        graph.sound.play(SoundEffect.UNLOCK)
                        graph.haptics.play(Haptic.SUCCESS)
                        graph.toasts.show(Toast("bag.fill", "Purchased & equipped", cosmetic.name, cosmetic.rarity.color))
                        close()
                    } else {
                        graph.haptics.play(Haptic.WARNING)
                    }
                }) { Text("Buy for ${(cosmetic.price ?: 0).formatted()}") }
            },
            dismissButton = { TextButton(onClick = { confirmPurchase = false }) { Text("Cancel") } },
            containerColor = Color(0xFF1B1530),
        )
    }
}

@Composable
private fun Requirements(cosmetic: Cosmetic, profile: PlayerProfile, isOwned: Boolean) {
    val rarity = cosmetic.rarity.color.color
    Column(Modifier.fillMaxWidth().glassCard(tint = rarity).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(if (cosmetic.isEarnedOnly) "How to earn" else "Requirements", "list.bullet.rectangle")
        for (gate in cosmetic.gates) {
            val met = gate.isMet(profile)
            val (current, goal) = gate.progress(profile)
            RequirementRow(if (met) "checkmark.circle.fill" else "circle.dashed", if (met) VR.green else VR.secondaryText) {
                Text(gate.summary, style = VR.display(14, FontWeight.Bold), color = Color.White)
                if (!met) ProgressBar(current.toDouble() / maxOf(goal, 1), tint = rarity, height = 5.dp)
                Text(gate.progressText(profile), style = VR.display(11, FontWeight.SemiBold), color = VR.secondaryText)
            }
        }
        val price = cosmetic.price
        if (price != null) {
            val affordable = profile.coins >= price
            RequirementRow(if (affordable) "checkmark.circle.fill" else "star.circle.fill", if (affordable) VR.green else VR.gold) {
                Text("${price.formatted()} coins", style = VR.display(14, FontWeight.Bold), color = Color.White)
                if (!affordable && !isOwned) ProgressBar(profile.coins.toDouble() / price, tint = VR.gold, height = 5.dp)
                Text("You have ${profile.coins.formatted()}", style = VR.display(11, FontWeight.SemiBold), color = VR.secondaryText)
            }
        }
    }
}

@Composable
private fun RequirementRow(icon: String, tint: Color, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Symbol(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { content() }
    }
}

// MARK: - Thumbnails

@Composable
fun CosmeticThumbnail(cosmetic: Cosmetic, size: Dp = 56.dp) {
    when (cosmetic.category) {
        CosmeticCategory.SKIN -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            SkinPreview(cosmetic.skin ?: Cosmetic.skinLook(Cosmetic.DEFAULT_SKIN), size * 0.6f)
        }
        CosmeticCategory.TRAIL -> TrailPreview(cosmetic.trail ?: Cosmetic.trailLook(Cosmetic.DEFAULT_TRAIL), size)
        CosmeticCategory.THEME -> ThemePreview(cosmetic.theme ?: Cosmetic.themeLook(Cosmetic.DEFAULT_THEME), size)
    }
}

/** A static blade swoosh drawn with the trail's real colours. */
@Composable
fun TrailPreview(trail: TrailLook, size: Dp = 56.dp) {
    Box(Modifier.width(size * 1.3f).height(size), contentAlignment = Alignment.Center) {
        if (!trail.hasRibbon && trail.particles == TrailParticles.NONE && !trail.afterimage) {
            Icon(Symbol("nosign"), contentDescription = "None", tint = VR.secondaryText, modifier = Modifier.size(size * 0.4f))
            return@Box
        }
        val glow = trail.primary.color
        Canvas(Modifier.width(size * 1.3f).height(size).shadow(6.dp, CircleShape, clip = false, ambientColor = glow.copy(alpha = if (trail.glow) 0.8f else 0.3f), spotColor = glow)) {
            val count = maxOf(trail.length, 10)
            val points = (0 until count).map { index ->
                val t = index.toFloat() / (count - 1)
                // A curved swipe from top-right to bottom-left.
                Offset(
                    this.size.width * (0.82f - 0.7f * t),
                    this.size.height * (0.2f + 0.6f * t) + (sin(t * PI) * this.size.height * 0.12).toFloat(),
                )
            }
            val unit = size.toPx()
            for (index in 0 until count - 1) {
                val t = index.toDouble() / (count - 1)
                val width = (unit * 0.34f * maxOf(trail.width, 0.4).toFloat() * (1 - t).pow(0.75).toFloat()) + 1
                val color = if (trail.hasRibbon) trail.color(t, index, 0.8).color else glow
                drawLine(color, points[index], points[index + 1], width, StrokeCap.Round, alpha = (1 - t).pow(1.1).toFloat())
            }
            drawCircle(Color.White, unit * 0.13f, points[0])
        }
    }
}

@Composable
fun ThemePreview(theme: ThemeLook, size: Dp = 56.dp) {
    val shape = RoundedCornerShape(12.dp)
    Canvas(
        Modifier
            .width(size * 1.3f)
            .height(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(theme.backgroundTop.color, theme.backgroundBottom.color)))
            .border(1.dp, VR.stroke, shape),
    ) {
        val s = size.toPx()
        val c = center
        if (theme.grid) {
            for (index in 0 until 5) {
                val y = c.y + (index - 2) * s * 0.15f
                drawLine(theme.accent.color.copy(alpha = 0.25f), Offset(0f, y), Offset(this.size.width, y), 1.dp.toPx())
            }
        }
        drawCircle(theme.hazard.color, s * 0.11f, Offset(c.x - s * 0.2f, c.y - s * 0.18f))
        drawCircle(theme.hazardAlt.color, s * 0.08f, Offset(c.x + s * 0.22f, c.y - s * 0.05f))
        drawPath(starPath(Offset(c.x + s * 0.05f, c.y - s * 0.28f), s * 0.07f, s * 0.03f, 5), theme.star.color)
        drawCircle(theme.accent.color.copy(alpha = 0.5f), s * 0.13f, Offset(c.x, c.y + s * 0.26f))
        drawCircle(Color.White, s * 0.09f, Offset(c.x, c.y + s * 0.26f))
    }
}
