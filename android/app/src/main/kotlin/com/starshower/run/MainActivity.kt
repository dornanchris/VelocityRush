//
//  MainActivity.kt
//  Starshower Run
//

package com.starshower.run

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.starshower.run.services.DailyReminder
import com.starshower.run.ui.StarshowerApp

class MainActivity : ComponentActivity(), Platform {

    private val graph: AppGraph get() = (application as StarshowerApplication).graph
    private var pendingPermissionResult: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(
                LocalAppGraph provides graph,
                LocalPlatform provides this,
            ) {
                StarshowerApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        graph.isForeground = true
    }

    override fun onPause() {
        graph.isForeground = false
        super.onPause()
    }

    // MARK: Platform

    override val appVersion: String by lazy {
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName} ($build)"
        }.getOrDefault("1.0 (1)")
    }

    override fun setDailyReminder(enabled: Boolean, onResult: (Boolean) -> Unit) {
        if (!enabled) {
            DailyReminder.setEnabled(this, false)
            onResult(false)
            return
        }
        val finish = { granted: Boolean ->
            DailyReminder.setEnabled(this, granted)
            onResult(granted)
        }
        if (DailyReminder.hasPermission(this)) {
            finish(true)
        } else {
            pendingPermissionResult = finish
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    @Deprecated("Framework callback; the Activity Result API would pull in extra dependencies for one prompt.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            pendingPermissionResult?.invoke(granted)
            pendingPermissionResult = null
        }
    }

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, null))
    }

    override fun setImmersive(immersive: Boolean) {
        if (immersive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            if (immersive) {
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            } else {
                controller.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (immersive) {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
        }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 42
    }
}
