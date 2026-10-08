//
//  StarshowerApp.kt
//  Starshower Run
//
//  Root composable: a tiny back stack for the menu screens, the full-screen
//  game, toasts and the daily login reward.
//

package com.starshower.run.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.starshower.run.LocalAppGraph
import com.starshower.run.core.engine.GameMode
import com.starshower.run.core.engine.RunConfig
import com.starshower.run.game.GameScreen
import kotlinx.coroutines.delay

enum class Route { MISSIONS, LEADERBOARDS, SHOP, PROFILE, SETTINGS }

@Composable
fun StarshowerApp() {
    val graph = LocalAppGraph.current
    val store = graph.store
    var stack by remember { mutableStateOf(listOf<Route>()) }
    var activeRun by remember { mutableStateOf<RunConfig?>(null) }
    var showLoginReward by remember { mutableStateOf(false) }
    var didCheckLogin by remember { mutableStateOf(false) }

    var forward by remember { mutableStateOf(true) }
    val navigate: (Route) -> Unit = {
        forward = true
        stack = stack + it
    }
    val back: () -> Unit = {
        forward = false
        stack = stack.dropLast(1)
    }

    // Daily rollover and the login reward whenever the app comes to the front.
    LaunchedEffect(graph.isForeground, activeRun) {
        if (!graph.isForeground || activeRun != null) return@LaunchedEffect
        store.refreshDaily()
        if (!store.isLoginRewardAvailable) return@LaunchedEffect
        // Small delay so the home screen settles first.
        delay(if (didCheckLogin) 300 else 800)
        didCheckLogin = true
        if (store.isLoginRewardAvailable && activeRun == null) showLoginReward = true
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = VR.cyan, secondary = VR.pink, background = VR.background, surface = VR.background)) {
        Box(Modifier.fillMaxSize().background(VR.background)) {
            AnimatedContent(
                targetState = stack.lastOrNull(),
                transitionSpec = {
                    if (forward) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "navigation",
            ) { route ->
                when (route) {
                    null -> HomeScreen(onNavigate = navigate, onPlay = { activeRun = it })
                    Route.MISSIONS -> MissionsScreen(onBack = back)
                    Route.LEADERBOARDS -> LeaderboardScreen(
                        initialMode = graph.preferences.selectedMode.let { if (it == GameMode.ZEN) GameMode.ENDLESS else it },
                        onBack = back,
                    )
                    Route.SHOP -> ShopScreen(onBack = back)
                    Route.PROFILE -> ProfileScreen(onBack = back)
                    Route.SETTINGS -> SettingsScreen(onBack = back)
                }
            }

            BackHandler(enabled = stack.isNotEmpty() && activeRun == null) { back() }

            ToastOverlay()

            val run = activeRun
            AnimatedVisibility(
                run != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                if (run != null) {
                    key(run.id) {
                        GameScreen(run, onQuit = { activeRun = null })
                    }
                }
            }

            if (showLoginReward) {
                LoginRewardSheet(onDismiss = { showLoginReward = false })
            }
        }
    }
}

// MARK: - Sub-screen chrome

/** Large-title page with a back button, matching the iOS navigation look. */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ScreenBackground()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).pressable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Symbol("chevron.left"), contentDescription = "Back", tint = VR.cyan, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                actions()
            }
            Text(title, style = VR.display(32), color = Color.White, modifier = Modifier.padding(horizontal = 20.dp))
            val body = Modifier
                .fillMaxSize()
                .let { if (scrollable) it.verticalScroll(rememberScrollState()) else it }
                .padding(20.dp)
            Column(body, verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
        }
    }
}

// MARK: - Toasts

@Composable
private fun ToastOverlay() {
    val toasts = LocalAppGraph.current.toasts
    val toast = toasts.current
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            toast != null,
            enter = slideInVertically(spring(dampingRatio = 0.8f)) { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val shown = remember(toast?.id) { toast } ?: return@AnimatedVisibility
            val tint = shown.tint.color
            val shape = RoundedCornerShape(20.dp)
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .shadow(12.dp, shape, ambientColor = tint, spotColor = tint)
                    .clip(shape)
                    .background(Color(0xF0161226))
                    .border(1.dp, tint.copy(alpha = 0.5f), shape)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Symbol(shown.icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(shown.title, style = VR.display(12, FontWeight.Bold), color = VR.secondaryText)
                    Text(shown.subtitle, style = VR.display(15, FontWeight.Bold), color = Color.White)
                }
            }
        }
    }
}
