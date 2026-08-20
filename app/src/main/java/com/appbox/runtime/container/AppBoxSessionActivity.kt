package com.appbox.runtime.container

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.appbox.runtime.AppBoxRuntimeApplication
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.core.model.TrackedProcess
import com.appbox.runtime.ui.system.ImmersiveModeHost
import com.appbox.runtime.ui.theme.AppBoxTheme
import com.appbox.runtime.ui.theme.AppBoxThemeColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppBoxSessionActivity : ComponentActivity() {

    private lateinit var processTracker: ProcessTracker
    private lateinit var virtualDisplayHost: VirtualDisplayHost
    private lateinit var immersiveHost: ImmersiveModeHost
    private var monitorJob: Job? = null
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersiveHost = ImmersiveModeHost(this)
        immersiveHost.attach()
        LockTaskManager.enterLockTask(this)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?: run { finish(); return }

        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: packageName
        processTracker = ProcessTracker(this)
        virtualDisplayHost = VirtualDisplayHost(this)

        val container = (application as AppBoxRuntimeApplication).container

        setContent {
            AppBoxTheme {
                var processInfo by remember { mutableStateOf<TrackedProcess?>(null) }
                var launchMode by remember { mutableStateOf("initialisation") }
                var surfaceReady by remember { mutableStateOf(false) }
                var retryKey by remember { mutableIntStateOf(0) }

                DisposableEffect(packageName, retryKey) {
                    monitorJob?.cancel()
                    monitorJob = lifecycleScope.launch {
                        container.appRegistry.updateAppState(packageName, AppLifecycleState.ACTIVE)
                        while (isActive) {
                            processInfo = processTracker.getProcessForPackage(packageName)?.copy(
                                displayName = displayName,
                            )
                            delay(800)
                        }
                    }
                    onDispose { monitorJob?.cancel() }
                }

                SessionScreen(
                    displayName = displayName,
                    packageName = packageName,
                    processInfo = processInfo,
                    launchMode = launchMode,
                    onBack = {
                        processTracker.stopProcess(packageName)
                        virtualDisplayHost.release()
                        lifecycleScope.launch {
                            container.appRegistry.updateAppState(packageName, AppLifecycleState.SUSPENDED)
                        }
                        finish()
                    },
                    onStopProcess = {
                        processTracker.stopProcess(packageName)
                        lifecycleScope.launch {
                            container.appRegistry.updateAppState(packageName, AppLifecycleState.STOPPED)
                        }
                        finish()
                    },
                    surfaceHost = { modifier ->
                        AndroidView(
                            modifier = modifier,
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    addView(
                                        SurfaceView(ctx).apply {
                                            holder.addCallback(object : SurfaceHolder.Callback {
                                                override fun surfaceCreated(holder: SurfaceHolder) {
                                                    surfaceReady = true
                                                }

                                                override fun surfaceChanged(
                                                    holder: SurfaceHolder,
                                                    format: Int,
                                                    width: Int,
                                                    height: Int,
                                                ) {
                                                    if (launched || width <= 0 || height <= 0) return
                                                    val displayId = virtualDisplayHost.create(
                                                        holder.surface,
                                                        width,
                                                        height,
                                                    )
                                                    if (displayId != null &&
                                                        launchInVirtualDisplay(packageName, displayId)
                                                    ) {
                                                        launched = true
                                                        launchMode = "conteneur VirtualDisplay"
                                                    } else {
                                                        launchFallback(packageName)
                                                        launched = true
                                                        launchMode = "surveillance ActivityManager"
                                                    }
                                                }

                                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                                    virtualDisplayHost.release()
                                                    launched = false
                                                }
                                            })
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        immersiveHost.reapply()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersiveHost.reapply()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        virtualDisplayHost.release()
        super.onDestroy()
    }

    private fun launchInVirtualDisplay(packageName: String, displayId: Int): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        return runCatching {
            startActivity(launchIntent, options.toBundle())
            true
        }.getOrDefault(false)
    }

    private fun launchFallback(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_DISPLAY_NAME = "extra_display_name"

        fun createIntent(context: Context, packageName: String, displayName: String): Intent {
            return Intent(context, AppBoxSessionActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}

@Composable
private fun SessionScreen(
    displayName: String,
    packageName: String,
    processInfo: TrackedProcess?,
    launchMode: String,
    onBack: () -> Unit,
    onStopProcess: () -> Unit,
    surfaceHost: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBoxThemeColors.Background),
    ) {
        SessionHeader(
            displayName = displayName,
            packageName = packageName,
            processInfo = processInfo,
            launchMode = launchMode,
            onBack = onBack,
            onStopProcess = onStopProcess,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppBoxThemeColors.Surface)
                .padding(2.dp),
        ) {
            surfaceHost(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
            )

            if (processInfo == null && launchMode.contains("surveillance")) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppBoxThemeColors.Background.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Application lancée sous surveillance AppBox",
                        color = AppBoxThemeColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionHeader(
    displayName: String,
    packageName: String,
    processInfo: TrackedProcess?,
    launchMode: String,
    onBack: () -> Unit,
    onStopProcess: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBoxThemeColors.SurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AppBoxThemeColors.Surface)
                .clickable(onClick = onBack)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = AppBoxThemeColors.TextPrimary,
                modifier = Modifier
                    .width(20.dp)
                    .height(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                color = AppBoxThemeColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text(
                text = "$packageName · $launchMode",
                color = AppBoxThemeColors.TextTertiary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            processInfo?.let {
                Text(
                    text = "PID ${it.pid} · ${it.importanceLabel} · ${it.memoryPssKb} Ko",
                    color = AppBoxThemeColors.Accent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderAction(label = "Retour", onClick = onBack)
            HeaderAction(label = "Stop", icon = Icons.Default.Stop, onClick = onStopProcess, destructive = true)
        }
    }
}

@Composable
private fun HeaderAction(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    destructive: Boolean = false,
) {
    val bg = if (destructive) AppBoxThemeColors.Error.copy(alpha = 0.15f) else AppBoxThemeColors.Surface
    val fg = if (destructive) AppBoxThemeColors.Error else AppBoxThemeColors.TextPrimary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, tint = fg, modifier = Modifier.width(16.dp).height(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
