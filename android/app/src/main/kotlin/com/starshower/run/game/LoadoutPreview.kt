//
//  LoadoutPreview.kt
//  Starshower Run
//
//  A tiny live scene for the Armory: your dot swooping around with its real
//  skin effects and blade trail over the selected theme.
//

package com.starshower.run.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.starshower.run.core.engine.GameEngine
import com.starshower.run.core.engine.Vec2
import com.starshower.run.core.progression.Cosmetic
import com.starshower.run.ui.color
import kotlin.math.sin
import kotlin.random.Random

private class PreviewScene(skinID: String, trailID: String, themeID: String) {
    private val theme = Cosmetic.themeLook(themeID)
    private val avatar = AvatarRenderer(Cosmetic.skinLook(skinID)).apply { scale = 1.3 }
    private val trail = TrailRenderer(Cosmetic.trailLook(trailID), playerRadius = GameEngine.BASE_PLAYER_RADIUS * 1.3).apply {
        ghostImage = avatar.body
        ghostSide = avatar.bodySide * 1.3
    }
    private val particles = ParticleSystem()
    private val hazardX = listOf(0.15, 0.85, 0.3, 0.7)
    private val hazardImages = (0 until 4).map { index ->
        Sprites.hazard(if (index % 2 == 0) theme.hazard else theme.hazardAlt, OrbDecoration.PLAIN, false)
    }
    private val hazardY = DoubleArray(4) { Random.nextDouble() }
    private var time = 0.0
    private var started = false

    // Sizes in dp, y-up, like the iOS preview scene in points.
    private var width = 360.0
    private var height = 200.0

    private fun pathPoint(t: Double) = Vec2(
        width / 2 + sin(t * 1.1) * width * 0.3,
        height * 0.55 + sin(t * 2.2) * height * 0.18,
    )

    fun update(dt: Double) {
        time += dt
        val head = pathPoint(time)
        if (!started) {
            trail.reset(head)
            started = true
        }
        avatar.position = head
        avatar.update(time, dt, particles)
        trail.update(head, dt, time, 240.0, particles)
        particles.update(dt)
        for (index in hazardY.indices) {
            hazardY[index] -= dt * (60 + index * 25) / height
            if (hazardY[index] * height < -30) hazardY[index] = (height + 30) / height
        }
    }

    fun draw(scope: DrawScope) {
        val density = scope.density
        width = scope.size.width / density.toDouble()
        height = scope.size.height / density.toDouble()
        val frame = WorldFrame(density, 0f, 0f, height)

        scope.drawRect(Brush.verticalGradient(listOf(theme.backgroundTop.color, theme.backgroundBottom.color)))
        for (index in 0 until 4) {
            val radius = 8.0 + index * 3
            val center = frame.point(width * hazardX[index], hazardY[index] * height)
            scope.drawSprite(hazardImages[index], center,
                (radius * 2 * Sprites.GLOW_RATIO * density).toFloat(), 0.8f)
        }
        trail.draw(scope, frame)
        avatar.draw(scope, frame)
        particles.draw(scope, frame)
    }
}

@Composable
fun LoadoutPreview(skinID: String, trailID: String, themeID: String, modifier: Modifier = Modifier) {
    // Rebuilt only when the loadout changes, not on every recomposition.
    val scene = remember(skinID, trailID, themeID) { PreviewScene(skinID, trailID, themeID) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(scene) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1.0 / 60 else minOf((now - last) / 1_000_000_000.0, 1.0 / 20)
                last = now
                scene.update(dt)
                tick = now
            }
        }
    }
    Canvas(modifier) {
        tick
        scene.draw(this)
    }
}
