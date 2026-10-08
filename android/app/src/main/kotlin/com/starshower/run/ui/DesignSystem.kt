//
//  DesignSystem.kt
//  Starshower Run
//
//  Shared colours, type, buttons and cards for the neon look – a direct
//  translation of the iOS `VR` design system.
//

package com.starshower.run.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starshower.run.LocalAppGraph
import com.starshower.run.core.engine.RGBColor
import com.starshower.run.core.progression.SkinEffect
import com.starshower.run.core.progression.SkinLook
import com.starshower.run.core.progression.SkinStyle
import com.starshower.run.core.progression.formatted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object VR {
    val background = Color(0.03f, 0.02f, 0.07f)
    val backgroundTop = Color(0.08f, 0.05f, 0.2f)
    val card = Color.White.copy(alpha = 0.06f)
    val cardStrong = Color.White.copy(alpha = 0.1f)
    val stroke = Color.White.copy(alpha = 0.12f)
    val cyan = Color(0.22f, 0.88f, 1.0f)
    val pink = Color(1.0f, 0.24f, 0.43f)
    val gold = Color(1.0f, 0.85f, 0.3f)
    val green = Color(0.42f, 1.0f, 0.62f)
    val purple = Color(0.69f, 0.49f, 1.0f)
    val secondaryText = Color.White.copy(alpha = 0.6f)

    fun display(size: Int, weight: FontWeight = FontWeight.ExtraBold): TextStyle =
        TextStyle(fontSize = size.sp, fontWeight = weight, letterSpacing = 0.sp)

    val brandColors = listOf(cyan, purple, pink)
    val brandGradient: Brush = Brush.horizontalGradient(brandColors)
}

val RGBColor.color: Color get() = Color(red.toFloat(), green.toFloat(), blue.toFloat(), alpha.toFloat())

// MARK: - Backgrounds

@Composable
fun ScreenBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(VR.backgroundTop, VR.background))))
}

/** Slowly falling glowing dots for the menus. */
@Composable
fun AnimatedBackdrop(tint: Color = VR.pink) {
    val reducedMotion = LocalAppGraph.current.preferences.reducedMotion
    val transition = rememberInfiniteTransition(label = "backdrop")
    val time by transition.animateFloat(
        initialValue = 0f, targetValue = 1_000f,
        animationSpec = infiniteRepeatable(tween(1_000_000, easing = LinearEasing)),
        label = "time",
    )
    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        Canvas(Modifier.fillMaxSize()) {
            val t = if (reducedMotion) 0.0 else time.toDouble()
            val unit = density
            for (index in 0 until 26) {
                val seed = index * 12.9898
                val xFraction = kotlin.math.abs((sin(seed) * 43_758.5453) % 1)
                val speed = (18 + (index % 5) * 9) * unit
                val radius = (2.0 + (index % 4) * 2.2).toFloat() * unit
                val travel = size.height + 60 * unit
                val y = ((t * speed + index * 97 * unit) % travel).toFloat() - 30 * unit
                val center = Offset((xFraction * size.width).toFloat(), y)
                val color = if (index % 3 == 0) VR.cyan else tint
                val opacity = 0.25f + (index % 3) * 0.12f
                drawCircle(color.copy(alpha = 0.25f * opacity), radius * 2, center)
                drawCircle(color.copy(alpha = opacity), radius, center)
            }
        }
    }
}

// MARK: - Cards

fun Modifier.glassCard(cornerRadius: Dp = 20.dp, tint: Color? = null): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val base = tint ?: Color.White
    return this
        .clip(shape)
        .background(VR.card)
        .background(Brush.linearGradient(listOf(base.copy(alpha = base.alpha * 0.14f), Color.Transparent)))
        .border(1.dp, base.copy(alpha = base.alpha * if (tint == null) 0.12f else 0.35f), shape)
}

fun Modifier.neonGlow(color: Color, radius: Dp = 12.dp, shape: Shape = CircleShape): Modifier =
    shadow(radius, shape, clip = false, ambientColor = color, spotColor = color)

// MARK: - Buttons

/** Scales down slightly while pressed, like the iOS PressableStyle. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    return this
        .scale(scale)
        .alpha(if (pressed) 0.85f else 1f)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun NeonButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VR.cyan,
    secondary: Color? = null,
    height: Dp = 58.dp,
    icon: ImageVector? = null,
    textSize: Int = 20,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "neon")
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .shadow(if (pressed) 6.dp else 16.dp, CircleShape, ambientColor = color, spotColor = color)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(color, secondary ?: color.copy(alpha = 0.75f))))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = Color.Black.copy(alpha = 0.85f)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size((textSize + 2).dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = VR.display(textSize), color = content, maxLines = 1)
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    icon: ImageVector? = null,
    color: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .pressable(enabled, onClick)
            .clip(CircleShape)
            .background(VR.cardStrong)
            .border(1.dp, VR.stroke, CircleShape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) color else VR.secondaryText
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = VR.display(17, FontWeight.Bold), color = tint, maxLines = 1)
    }
}

/** A disabled-looking capsule that explains why an action isn't available. */
@Composable
fun InfoCapsule(text: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(VR.card),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = VR.secondaryText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = VR.display(15, FontWeight.Bold), color = VR.secondaryText)
    }
}

// MARK: - Badges

@Composable
fun CoinBadge(amount: Int, compact: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(VR.cardStrong)
            .border(1.dp, VR.gold.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Symbol("star.circle.fill"), contentDescription = "Coins", tint = VR.gold, modifier = Modifier.size(if (compact) 16.dp else 19.dp))
        Spacer(Modifier.width(6.dp))
        Text(amount.formatted(), style = VR.display(if (compact) 15 else 18, FontWeight.Bold), color = Color.White)
    }
}

@Composable
fun LevelRing(level: Int, progress: Double, size: Dp = 48.dp) {
    val animated by animateFloatAsState(maxOf(0.02f, progress.toFloat()), spring(), label = "level")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawCircle(Color.White.copy(alpha = 0.12f), radius = this.size.minDimension / 2 - inset, style = Stroke(stroke))
            drawArc(
                brush = Brush.sweepGradient(VR.brandColors + VR.cyan),
                startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text("$level", style = VR.display((size.value * 0.36f).toInt()), color = Color.White)
    }
}

@Composable
fun ProgressBar(value: Double, modifier: Modifier = Modifier, tint: Color = VR.cyan, height: Dp = 8.dp) {
    val animated by animateFloatAsState(value.coerceIn(0.0, 1.0).toFloat(), spring(), label = "progress")
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(Color.White.copy(alpha = 0.1f)))
        val fill = maxOf(height, maxWidth * animated)
        Box(
            Modifier
                .width(fill)
                .height(height)
                .shadow(4.dp, CircleShape, ambientColor = tint, spotColor = tint)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(tint, tint.copy(alpha = 0.7f)))),
        )
    }
}

@Composable
fun SectionTitle(text: String, icon: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(Symbol(icon), contentDescription = null, tint = VR.cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text.uppercase(),
            style = VR.display(13, FontWeight.Bold).copy(letterSpacing = 1.5.sp),
            color = VR.secondaryText,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun Chip(text: String, icon: String? = null, tint: Color = VR.cyan) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = tint.alpha * 0.15f))
            .border(1.dp, tint.copy(alpha = tint.alpha * 0.4f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(Symbol(icon), contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = VR.display(12, FontWeight.Bold), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A row of capsule buttons – the iOS segmented picker in neon form. */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selection: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    tint: Color = VR.cyan,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VR.card)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (option in options) {
            val selected = option == selection
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) tint.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label(option), style = VR.display(13, FontWeight.Bold), color = if (selected) Color.White else VR.secondaryText, maxLines = 1)
            }
        }
    }
}

// MARK: - Skin preview

/**
 * Glowing orb preview for skins in menus (a lightweight stand-in for the
 * in-game avatar, including its signature effects).
 */
@Composable
fun SkinPreview(skin: SkinLook, size: Dp = 44.dp) {
    val transition = rememberInfiniteTransition(label = "skin")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3_000, easing = LinearEasing)), label = "spin")
    val hue by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "hue")
    val hueColor = if (skin.has(SkinEffect.HUE_CYCLE)) RGBColor.hsb(hue.toDouble(), 0.75, 1.0).color else null

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        // Soft glow
        Box(
            Modifier
                .size(size * 1.7f)
                .blur(size * 0.28f)
                .clip(CircleShape)
                .background((hueColor ?: skin.glow.color).copy(alpha = if (skin.has(SkinEffect.PULSE) || skin.has(SkinEffect.AURA)) 0.5f else 0.35f)),
        )
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            val c = center
            when (skin.style) {
                SkinStyle.SOLID -> drawCircle(skin.primary.color, r, c)
                SkinStyle.RING -> {
                    drawCircle(skin.primary.color.copy(alpha = 0.9f), r, c)
                    drawCircle(Color.White.copy(alpha = 0.9f), r * 1.12f - r * 0.08f, c, style = Stroke(r * 0.16f))
                }
                SkinStyle.CORE -> drawCircle(Brush.radialGradient(listOf(Color.White, skin.primary.color), c, r), r, c)
                SkinStyle.PRISM -> drawCircle(
                    Brush.sweepGradient(listOf(Color.Red, Color(1f, 0.6f, 0f), Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color(0.6f, 0.2f, 1f), Color.Red), c),
                    r, c,
                )
                SkinStyle.VOID -> {
                    drawCircle(Color.Black, r, c)
                    drawCircle(Color.White, r * 0.9f, c, style = Stroke(r * 0.2f))
                }
            }
            val accent = hueColor ?: skin.accentColor.color
            if (skin.has(SkinEffect.SPIN_RING)) {
                rotate(-spin) {
                    drawCircle(
                        accent, r * 1.75f, c,
                        style = Stroke(maxOf(1.5.dp.toPx(), r * 0.1f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.32f, r * 0.22f))),
                    )
                }
            }
            if (skin.has(SkinEffect.ORBITERS)) {
                for (index in 0 until 3) {
                    val angle = Math.toRadians((spin + index * 120.0))
                    val p = Offset(c.x + (cos(angle) * r * 2).toFloat(), c.y + (sin(angle) * r * 2).toFloat())
                    drawCircle(accent.copy(alpha = 0.35f), r * 0.32f, p)
                    drawCircle(accent, r * 0.2f, p)
                }
            }
            if (skin.has(SkinEffect.SPARKLE)) {
                val p = Offset(c.x + r * 1.1f, c.y - r * 1.1f)
                drawFourPointStar(p, r * 0.32f, r * 0.1f, accent)
            }
        }
    }
}

/** Four-pointed sparkle. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFourPointStar(center: Offset, outer: Float, inner: Float, color: Color, alpha: Float = 1f) {
    val path = starPath(center, outer, inner, 4)
    drawPath(path, color, alpha = alpha)
}

fun starPath(center: Offset, outer: Float, inner: Float, points: Int): androidx.compose.ui.graphics.Path {
    val path = androidx.compose.ui.graphics.Path()
    val count = points * 2
    for (i in 0 until count) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = i * PI / points - PI / 2
        val x = center.x + (cos(angle) * radius).toFloat()
        val y = center.y + (sin(angle) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Logo used on the home and about screens. */
@Composable
fun Logo() {
    val transition = rememberInfiniteTransition(label = "logo")
    val glow by transition.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1_600), RepeatMode.Reverse), label = "glow")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-10).dp)) {
        Text(
            "VELOCITY",
            style = TextStyle(fontSize = 46.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
            color = Color.White,
        )
        Text(
            "RUSH",
            style = TextStyle(
                fontSize = 58.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                brush = VR.brandGradient,
                shadow = androidx.compose.ui.graphics.Shadow(VR.pink.copy(alpha = glow), Offset.Zero, 12f + 24f * glow),
            ),
        )
    }
}
