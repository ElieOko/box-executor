package com.appbox.runtime.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.appbox.runtime.AppBoxRuntimeApplication
import com.appbox.runtime.service.ProcessWatchdogService
import com.appbox.runtime.ui.screens.OsShell
import com.appbox.runtime.ui.system.ImmersiveMode
import com.appbox.runtime.ui.theme.AppBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ImmersiveMode.apply(this)
        ProcessWatchdogService.start(this)

        val container = (application as AppBoxRuntimeApplication).container

        setContent {
            AppBoxTheme {
                val viewModel = remember { RuntimeViewModel(container, applicationContext) }
                DisposableEffect(Unit) {
                    onDispose { }
                }
                OsShell(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ImmersiveMode.apply(this)
    }
}
