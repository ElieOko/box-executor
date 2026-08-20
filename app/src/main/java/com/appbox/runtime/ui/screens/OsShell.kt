package com.appbox.runtime.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.ui.OsScreen
import com.appbox.runtime.ui.RuntimeViewModel
import com.appbox.runtime.ui.components.AppBoxBackground
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.components.AppIcon
import com.appbox.runtime.ui.components.EnvironmentExitButton
import com.appbox.runtime.ui.components.StatusIndicator
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun OsShell(
    viewModel: RuntimeViewModel,
    onExitEnvironment: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEnvironmentState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppBoxBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            AppBoxHeader(
                screen = uiState.currentScreen,
                appCount = uiState.apps.size,
                processCount = uiState.trackedProcesses.size,
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = uiState.currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
                modifier = Modifier.weight(1f),
            ) { screen ->
                when (screen) {
                    OsScreen.HOME -> HomeScreen(
                        apps = uiState.apps,
                        onLaunchApp = viewModel::launchAppInBox,
                        onAddApp = viewModel::openAppPicker,
                    )
                    OsScreen.LIBRARY -> LibraryScreen(
                        apps = uiState.apps,
                        onLaunchApp = viewModel::launchAppInBox,
                        onRemoveApp = viewModel::removeApp,
                        onAddApp = viewModel::openAppPicker,
                    )
                    OsScreen.PERMISSIONS -> PermissionsScreen(
                        apps = uiState.apps,
                        selectedApp = uiState.selectedApp,
                        permissions = uiState.appPermissions,
                        onSelectApp = viewModel::selectApp,
                        onGrant = viewModel::grantPermission,
                        onRevoke = viewModel::revokePermission,
                    )
                    OsScreen.MONITOR -> MonitorScreen(
                        events = uiState.monitorEvents,
                        processes = uiState.trackedProcesses,
                        hasUsageAccess = uiState.hasUsageAccess,
                        canDrawOverlay = uiState.canDrawOverlay,
                        isLockTaskActive = uiState.isLockTaskActive,
                        isDeviceOwner = uiState.isDeviceOwner,
                        onRequestUsageAccess = viewModel::openUsageAccessSettings,
                        onRequestOverlay = viewModel::openOverlaySettings,
                    )
                    OsScreen.AGENT -> AgentScreen(
                        lastVoiceText = uiState.lastVoiceText,
                        conversationTurns = uiState.conversationTurns,
                        hoshiConfig = uiState.hoshiConfig,
                        openAiKeyConfigured = uiState.openAiKeyConfigured,
                        accessibilityEnabled = uiState.accessibilityEnabled,
                        schedules = uiState.schedules,
                        runs = uiState.workflowRuns,
                        logs = uiState.agentLogs,
                        onReloadInstructions = viewModel::reloadAgentInstructions,
                        onConfigChange = viewModel::updateHoshiConfig,
                        onSaveConfig = viewModel::saveHoshiConfig,
                        onOpenAccessibility = viewModel::openAccessibilitySettings,
                    )
                    OsScreen.FLOWS -> HoshiFlowsScreen(
                        workflows = uiState.workflows,
                        selectedWorkflow = uiState.selectedWorkflow,
                        editMode = uiState.workflowEditMode,
                        onSelectWorkflow = viewModel::selectWorkflow,
                        onRunWorkflow = viewModel::runWorkflow,
                        onToggleEditMode = viewModel::toggleWorkflowEditMode,
                        onNodeMoved = viewModel::onWorkflowNodeMoved,
                    )
                    OsScreen.APP_PICKER -> AppPickerScreen(
                        candidates = viewModel.filteredCandidates(),
                        query = uiState.appPickerQuery,
                        isLoading = uiState.isLoadingPicker,
                        onQueryChange = viewModel::updateAppPickerQuery,
                        onAddApp = viewModel::addAppFromDevice,
                        onClose = viewModel::closeAppPicker,
                    )
                }
            }

            if (uiState.currentScreen != OsScreen.APP_PICKER) {
                Spacer(modifier = Modifier.height(8.dp))
                AppBoxTabBar(
                    current = uiState.currentScreen,
                    onNavigate = viewModel::navigateTo,
                )
            }

            Spacer(modifier = Modifier.height(bottomInset.coerceAtLeast(8.dp)))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
        )

        EnvironmentExitButton(
            onClick = onExitEnvironment,
            compact = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp),
        )
    }
}

@Composable
private fun AppBoxHeader(screen: OsScreen, appCount: Int, processCount: Int) {
    val title = when (screen) {
        OsScreen.HOME -> "Espace de travail"
        OsScreen.LIBRARY -> "Bibliothèque"
        OsScreen.PERMISSIONS -> "Contrôle d'accès"
        OsScreen.MONITOR -> "Processus"
        OsScreen.AGENT -> "HOSHI"
        OsScreen.FLOWS -> "Flows"
        OsScreen.APP_PICKER -> "Ajouter"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                text = "AppBox",
                style = MaterialTheme.typography.labelMedium,
                color = AppBoxThemeColors.TextTertiary,
                letterSpacing = 1.sp,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = AppBoxThemeColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        AppBoxPanel(cornerRadius = 10.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIndicator(active = processCount > 0)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$appCount apps · $processCount proc.",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppBoxThemeColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AppBoxTabBar(current: OsScreen, onNavigate: (OsScreen) -> Unit) {
    val tabs = listOf(
        OsScreen.HOME to "Accueil",
        OsScreen.LIBRARY to "Apps",
        OsScreen.AGENT to "HOSHI",
        OsScreen.FLOWS to "Flows",
        OsScreen.MONITOR to "Proc.",
    )

    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { (screen, label) ->
                val selected = current == screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipTab(selected)
                        .clickable { onNavigate(screen) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) AppBoxThemeColors.TextPrimary else AppBoxThemeColors.TextTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Modifier.clipTab(selected: Boolean): Modifier {
    return if (selected) {
        this
            .padding(2.dp)
            .background(AppBoxThemeColors.SurfaceHover, RoundedCornerShape(10.dp))
    } else {
        this
    }
}

@Composable
fun HomeScreen(
    apps: List<AppBoxApp>,
    onLaunchApp: (String) -> Unit,
    onAddApp: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = gridColumns(maxWidth)

        AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 20.dp) {
            if (apps.isEmpty()) {
                EmptyState(onAddApp = onAddApp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppTile(app = app, onClick = { onLaunchApp(app.packageName) })
                    }
                    item {
                        AddTile(onClick = onAddApp)
                    }
                }
            }
        }
    }
}

private fun gridColumns(maxWidth: Dp): Int = when {
    maxWidth < 360.dp -> 3
    maxWidth < 600.dp -> 4
    maxWidth < 900.dp -> 5
    else -> 6
}

@Composable
private fun EmptyState(onAddApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Aucune application encadrée",
            color = AppBoxThemeColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ajoutez des apps installées sur l'appareil pour les exécuter dans la box AppBox.",
            color = AppBoxThemeColors.TextSecondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        AppBoxPanel(
            cornerRadius = 10.dp,
            modifier = Modifier.clickable(onClick = onAddApp),
        ) {
            Text(
                text = "Parcourir les applications",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = AppBoxThemeColors.Accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AppTile(app: AppBoxApp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box {
            AppIcon(packageName = app.packageName, drawable = null, size = 56.dp)
            StatusIndicator(
                active = app.state == AppLifecycleState.ACTIVE,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.displayName,
            fontSize = 12.sp,
            color = AppBoxThemeColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        AppBoxPanel(modifier = Modifier.size(56.dp), cornerRadius = 14.dp) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Ajouter", fontSize = 12.sp, color = AppBoxThemeColors.Accent)
    }
}
