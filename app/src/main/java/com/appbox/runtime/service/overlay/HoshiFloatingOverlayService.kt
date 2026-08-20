package com.appbox.runtime.service.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.appbox.runtime.R
import com.appbox.runtime.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Bulle HOSHI flottante — déplaçable, toujours accessible */
class HoshiFloatingOverlayService : Service() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val positionStore by lazy { OverlayPositionStore(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> removeOverlay()
            ACTION_SHOW -> showOverlay()
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (!canDrawOverlays() || overlayView != null) return

        val pos = positionStore.getPosition(POS_KEY, defaultX = 24, defaultY = 200)
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_hoshi_bubble, null)

        view.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_OPEN_HOSHI, true)
                },
            )
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pos.x
            y = pos.y
        }

        var dragStartX = 0
        var dragStartY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) isDragging = true
                    params.x = dragStartX + dx
                    params.y = dragStartY + dy
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        scope.launch {
                            positionStore.savePosition(POS_KEY, params.x, params.y)
                        }
                    } else {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(view, params)
        overlayView = view
        layoutParams = params
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        layoutParams = null
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "com.appbox.runtime.hoshi.SHOW"
        const val ACTION_HIDE = "com.appbox.runtime.hoshi.HIDE"
        const val EXTRA_OPEN_HOSHI = "extra_open_hoshi"
        private const val POS_KEY = "hoshi_bubble"

        fun show(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            context.startService(Intent(context, HoshiFloatingOverlayService::class.java).apply { action = ACTION_SHOW })
        }

        fun hide(context: Context) {
            context.startService(Intent(context, HoshiFloatingOverlayService::class.java).apply { action = ACTION_HIDE })
        }
    }
}
