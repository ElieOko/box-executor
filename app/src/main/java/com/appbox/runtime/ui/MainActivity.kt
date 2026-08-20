package com.appbox.runtime.ui

import android.app.AlertDialog
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
import com.appbox.runtime.ui.system.ImmersiveModeHost
import com.appbox.runtime.ui.theme.AppBoxTheme

class MainActivity : ComponentActivity() {

    private lateinit var immersiveHost: ImmersiveModeHost
    private lateinit var viewModel: RuntimeViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        immersiveHost = ImmersiveModeHost(this)
        immersiveHost.attach()

        val container = (application as AppBoxRuntimeApplication).container
        viewModel = RuntimeViewModel(container, applicationContext)

        if (intent?.getBooleanExtra(
                com.appbox.runtime.service.overlay.HoshiFloatingOverlayService.EXTRA_OPEN_HOSHI,
                false,
            ) == true
        ) {
            viewModel.navigateTo(com.appbox.runtime.ui.OsScreen.AGENT)
        }

        ProcessWatchdogService.start(this)
        viewModel.enterEnvironment(this)

        setContent {
            AppBoxTheme {
                val vm = remember { viewModel }
                OsShell(
                    viewModel = vm,
                    onExitEnvironment = { showExitConfirmation() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        immersiveHost.reapply()
        if (::viewModel.isInitialized) {
            viewModel.refreshEnvironmentState()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::immersiveHost.isInitialized) {
            immersiveHost.reapply()
        }
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(com.appbox.runtime.R.string.exit_environment_title))
            .setMessage(getString(com.appbox.runtime.R.string.exit_environment_message))
            .setPositiveButton(getString(com.appbox.runtime.R.string.exit_confirm)) { _, _ ->
                viewModel.exitEnvironment(this)
                immersiveHost.reapply()
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }
}
