package com.appbox.runtime.container

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.DisplayMetrics
import android.view.Surface

class VirtualDisplayHost(
    private val context: Context,
) {
    private var virtualDisplay: VirtualDisplay? = null
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    fun create(surface: Surface, width: Int, height: Int): Int? {
        release()
        if (width <= 0 || height <= 0) return null

        val metrics = context.resources.displayMetrics
        val density = metrics.densityDpi

        virtualDisplay = displayManager.createVirtualDisplay(
            "AppBox-${System.currentTimeMillis()}",
            width,
            height,
            density,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
        )

        return virtualDisplay?.display?.displayId
    }

    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    companion object {
        fun screenMetrics(context: Context): DisplayMetrics = context.resources.displayMetrics
    }
}
