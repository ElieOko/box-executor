package com.appbox.runtime.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import com.appbox.runtime.R
import com.appbox.runtime.container.ProcessTracker
import com.appbox.runtime.ui.MainActivity

/**
 * Overlay système affiché quand une app sort de la box AppBox pendant une session active.
 * Nécessite la permission « afficher par-dessus les autres apps ».
 */
class ReturnOverlayService : Service() {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private lateinit var processTracker: ProcessTracker

    override fun onCreate() {
        super.onCreate()
        processTracker = ProcessTracker(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> removeOverlay()
            ACTION_SHOW -> showOverlayIfAllowed()
            else -> updateOverlayState(
                allowedPackages = intent?.getStringArrayListExtra(EXTRA_ALLOWED_PACKAGES).orEmpty(),
            )
        }
        return START_STICKY
    }

    private fun updateOverlayState(allowedPackages: List<String>) {
        if (!canDrawOverlays()) {
            removeOverlay()
            return
        }

        val foreground = processTracker.getForegroundPackage()
        val appBoxPackage = packageName
        val isInsideBox = foreground == null ||
            foreground == appBoxPackage ||
            allowedPackages.contains(foreground)

        if (isInsideBox) {
            removeOverlay()
        } else {
            showOverlayIfAllowed()
        }
    }

    private fun showOverlayIfAllowed() {
        if (!canDrawOverlays() || overlayView != null) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_return_button, null)
        view.findViewById<ImageButton>(R.id.btn_return_appbox).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            removeOverlay()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        windowManager?.addView(view, params)
        overlayView = view
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "com.appbox.runtime.overlay.SHOW"
        const val ACTION_HIDE = "com.appbox.runtime.overlay.HIDE"
        const val EXTRA_ALLOWED_PACKAGES = "extra_allowed_packages"

        fun update(context: Context, allowedPackages: List<String>) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, ReturnOverlayService::class.java).apply {
                putStringArrayListExtra(EXTRA_ALLOWED_PACKAGES, ArrayList(allowedPackages))
            }
            context.startService(intent)
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, ReturnOverlayService::class.java).apply { action = ACTION_HIDE },
            )
        }

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun openOverlaySettings(context: Context) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
