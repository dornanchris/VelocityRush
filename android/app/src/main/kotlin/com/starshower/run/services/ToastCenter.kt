//
//  ToastCenter.kt
//  Starshower Run
//
//  Queue of little banners ("Achievement unlocked!", "+150 coins").
//

package com.starshower.run.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.starshower.run.core.audio.SoundEffect
import com.starshower.run.core.engine.RGBColor
import com.starshower.run.core.progression.AchievementCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

data class Toast(
    /** SF Symbol-style icon key, mapped by `SymbolIcons`. */
    val icon: String,
    val title: String,
    val subtitle: String,
    val tint: RGBColor,
    val id: String = UUID.randomUUID().toString(),
)

class ToastCenter(
    private val sound: SoundManager,
    private val haptics: HapticsManager,
) {
    var current by mutableStateOf<Toast?>(null)
        private set

    private val queue = ArrayDeque<Toast>()
    private var isShowing = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show(toast: Toast) {
        queue.addLast(toast)
        if (!isShowing) advance()
    }

    fun announceAchievements(ids: List<String>) {
        for (id in ids) {
            val definition = AchievementCatalog.find(id) ?: continue
            show(Toast(
                icon = definition.icon,
                title = "Achievement unlocked",
                subtitle = "${definition.title}  ·  +${definition.reward} coins",
                tint = definition.tier.color,
            ))
        }
        if (ids.isNotEmpty()) {
            sound.play(SoundEffect.UNLOCK)
            haptics.play(Haptic.SUCCESS)
        }
    }

    private fun advance() {
        val next = queue.removeFirstOrNull()
        if (next == null) {
            isShowing = false
            return
        }
        isShowing = true
        current = next
        scope.launch {
            delay(2_600)
            current = null
            delay(400)
            advance()
        }
    }
}
