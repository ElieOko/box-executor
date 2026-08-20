package com.appbox.runtime.ui.system

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object ImmersiveMode {

    fun apply(activity: ComponentActivity) {
        apply(activity.window, activity.window.decorView)
    }

    fun apply(window: Window, rootView: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val controller = WindowCompat.getInsetsController(window, rootView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            WindowInsetsCompat.CONSUMED
        }
    }
}

class ImmersiveModeHost(
    private val activity: ComponentActivity,
) {
    private val reapply = Runnable {
        ImmersiveMode.apply(activity.window, activity.window.decorView)
    }

    fun attach() {
        ImmersiveMode.apply(activity)
        activity.window.decorView.setOnSystemUiVisibilityChangeListener {
            activity.window.decorView.post(reapply)
        }
    }

    fun reapply() {
        activity.window.decorView.post(reapply)
    }
}
