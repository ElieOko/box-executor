package com.appbox.runtime.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.ui.OsScreen
import com.appbox.runtime.ui.RuntimeViewModel
import com.appbox.runtime.ui.components.AppIcon
import com.appbox.runtime.ui.components.GlassCircle
import com.appbox.runtime.ui.components.GlassSurface
import com.appbox.runtime.ui.components.OsWallpaper
import com.appbox.runtime.ui.components.StatusDot
import com.appbox.runtime.ui.theme.OsColors

@Composable
fun OsShell(viewModel: RuntimeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
        OsWallpaper()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            OsStatusBar(
                time = uiState.currentTime,
                appCount = uiState.apps.size,
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = uiState.currentScreen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "os_screen",
                modifier = Modifier.weight(1f),
            ) { screen ->
                when (screen) {
                    OsScreen.HOME -> HomeScreen(
                        apps = uiState.apps,
                        onLaunchApp = viewModel::launchApp,
                        onAddApp = viewModel::openAppPicker,
                    )
                    OsScreen.LIBRARY -> LibraryScreen(
                        apps = uiState.apps,
                        onLaunchApp = viewModel::launchApp,
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
                    OsScreen.MONITOR -> MonitorScreen(events = uiState.monitorEvents)
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

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.currentScreen != OsScreen.APP_PICKER) {
                OsDock(
                    currentScreen = uiState.currentScreen,
                    onNavigate = viewModel::navigateTo,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
        )
    }
}

@Composable
private fun OsStatusBar(time: String, appCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "AppBox OS",
                style = MaterialTheme.typography.labelMedium,
                color = OsColors.TextSecondary,
            )
            Text(
                text = time.ifEmpty { "--:--" },
                style = MaterialTheme.typography.displayLarge,
                color = OsColors.TextPrimary,
                fontWeight = FontWeight.Light,
            )
        }

        GlassSurface(
            cornerRadius = 20.dp,
            modifier = Modifier.padding(4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(color = OsColors.StatusActive, modifier = Modifier.size(8.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$appCount apps",
                    style = MaterialTheme.typography.labelMedium,
                    color = OsColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun OsDock(
    currentScreen: OsScreen,
    onNavigate: (OsScreen) -> Unit,
) {
    GlassSurface(
        cornerRadius = 28.dp,
        backgroundAlpha = 0.18f,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DockItem(
                icon = Icons.Default.Apps,
                label = "Accueil",
                selected = currentScreen == OsScreen.HOME,
                onClick = { onNavigate(OsScreen.HOME) },
            )
            DockItem(
                icon = Icons.Default.Settings,
                label = "Bibliothèque",
                selected = currentScreen == OsScreen.LIBRARY,
                onClick = { onNavigate(OsScreen.LIBRARY) },
            )
            DockItem(
                icon = Icons.Default.Security,
                label = "Sécurité",
                selected = currentScreen == OsScreen.PERMISSIONS,
                onClick = { onNavigate(OsScreen.PERMISSIONS) },
            )
            DockItem(
                icon = Icons.Default.MonitorHeart,
                label = "Monitor",
                selected = currentScreen == OsScreen.MONITOR,
                onClick = { onNavigate(OsScreen.MONITOR) },
            )
        }
    }
}

@Composable
private fun DockItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        GlassCircle(
            modifier = Modifier.size(if (selected) 52.dp else 46.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) OsColors.AccentCyan else OsColors.TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) OsColors.TextPrimary else OsColors.TextMuted,
        )
    }
}

@Composable
fun HomeScreen(
    apps: List<AppBoxApp>,
    onLaunchApp: (String) -> Unit,
    onAddApp: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Espace de travail",
            style = MaterialTheme.typography.headlineMedium,
            color = OsColors.TextPrimary,
        )
        Text(
            text = "Vos applications métier dans un environnement sécurisé",
            style = MaterialTheme.typography.bodyMedium,
            color = OsColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(20.dp))

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            cornerRadius = 32.dp,
            backgroundAlpha = 0.14f,
        ) {
            if (apps.isEmpty()) {
                EmptyAppsPrompt(onAddApp = onAddApp)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppLauncherTile(app = app, onClick = { onLaunchApp(app.packageName) })
                    }
                    item {
                        AddAppTile(onClick = onAddApp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAppsPrompt(onAddApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassCircle(modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = OsColors.AccentCyan,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Aucune application",
            style = MaterialTheme.typography.titleLarge,
            color = OsColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ajoutez des apps déjà installées sur votre téléphone pour construire votre écosystème AppBox.",
            style = MaterialTheme.typography.bodyMedium,
            color = OsColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        GlassSurface(
            cornerRadius = 18.dp,
            modifier = Modifier.clickable(onClick = onAddApp),
        ) {
            Text(
                text = "Choisir des applications",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                color = OsColors.AccentCyan,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AppLauncherTile(app: AppBoxApp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box {
            AppIcon(
                packageName = app.packageName,
                drawable = null,
                modifier = Modifier.size(64.dp),
            )
            StatusDot(
                color = when (app.state) {
                    AppLifecycleState.ACTIVE -> OsColors.StatusActive
                    AppLifecycleState.SUSPENDED -> OsColors.StatusSuspended
                    else -> OsColors.StatusStopped
                },
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopEnd),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = OsColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddAppTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        GlassCircle(modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Ajouter",
                    tint = OsColors.AccentCyan,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ajouter",
            style = MaterialTheme.typography.bodyMedium,
            color = OsColors.AccentCyan,
            textAlign = TextAlign.Center,
        )
    }
}
