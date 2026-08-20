package com.appbox.runtime.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appbox.runtime.AppBoxRuntimeApplication
import com.appbox.runtime.core.model.AppBoxApp
import com.appbox.runtime.core.model.AppLifecycleState
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.core.model.RuntimePermission
import com.appbox.runtime.ui.theme.AppBoxTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as AppBoxRuntimeApplication).container
        val viewModel = RuntimeViewModel(container)

        setContent {
            AppBoxTheme {
                RuntimeDashboard(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeDashboard(viewModel: RuntimeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AppBox Runtime") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Apps, contentDescription = "Apps") },
                    label = { Text("Apps") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Permissions") },
                    label = { Text("Permissions") },
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.MonitorHeart, contentDescription = "Monitor") },
                    label = { Text("Monitor") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (selectedTab) {
            0 -> AppsTab(
                apps = uiState.apps,
                modifier = Modifier.padding(padding),
                onSelect = viewModel::selectApp,
                onSuspend = viewModel::suspendApp,
                onActivate = viewModel::activateApp,
            )
            1 -> PermissionsTab(
                apps = uiState.apps,
                selectedApp = uiState.selectedApp,
                modifier = Modifier.padding(padding),
                onSelect = viewModel::selectApp,
                onGrant = viewModel::grantPermission,
                onRevoke = viewModel::revokePermission,
            )
            2 -> MonitorTab(
                events = uiState.monitorEvents,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
fun AppsTab(
    apps: List<AppBoxApp>,
    modifier: Modifier = Modifier,
    onSelect: (AppBoxApp) -> Unit,
    onSuspend: (String) -> Unit,
    onActivate: (String) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Applications enregistrées",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Les apps métier se connectent via le SDK AppBox pour rejoindre l'écosystème.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (apps.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Text(
                    text = "Aucune application connectée.\nIntégrez le SDK dans vos apps métier.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    AppCard(
                        app = app,
                        onClick = { onSelect(app) },
                        onSuspend = { onSuspend(app.packageName) },
                        onActivate = { onActivate(app.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
fun AppCard(
    app: AppBoxApp,
    onClick: () -> Unit,
    onSuspend: () -> Unit,
    onActivate: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(stateColor(app.state)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "v${app.versionName} · ${app.state.name}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun PermissionsTab(
    apps: List<AppBoxApp>,
    selectedApp: AppBoxApp?,
    modifier: Modifier = Modifier,
    onSelect: (AppBoxApp?) -> Unit,
    onGrant: (String, RuntimePermission) -> Unit,
    onRevoke: (String, RuntimePermission) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Gestion des permissions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (apps.isEmpty()) {
            Text("Aucune application à configurer.")
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(apps, key = { it.packageName }) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(app) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedApp?.packageName == app.packageName) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Text(
                        text = app.displayName,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        selectedApp?.let { app ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Permissions — ${app.displayName}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(RuntimePermission.entries.toList()) { permission ->
                    PermissionRow(
                        permission = permission,
                        granted = app.permissions.contains(permission),
                        onToggle = { granted ->
                            if (granted) {
                                onGrant(app.packageName, permission)
                            } else {
                                onRevoke(app.packageName, permission)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRow(
    permission: RuntimePermission,
    granted: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = permission.name.replace('_', ' '),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = granted, onCheckedChange = onToggle)
    }
}

@Composable
fun MonitorTab(
    events: List<RemoteMonitorEvent>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Monitoring local",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Événements en attente d'envoi vers le serveur central (à venir).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (events.isEmpty()) {
            Text("Aucun événement pour le moment.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(events.reversed(), key = { "${it.timestamp}_${it.type}" }) { event ->
                    MonitorEventCard(event)
                }
            }
        }
    }
}

@Composable
fun MonitorEventCard(event: RemoteMonitorEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = event.type.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = timeFormat.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            event.packageName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = event.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun stateColor(state: AppLifecycleState) = when (state) {
    AppLifecycleState.ACTIVE -> MaterialTheme.colorScheme.primary
    AppLifecycleState.REGISTERED -> MaterialTheme.colorScheme.tertiary
    AppLifecycleState.SUSPENDED -> MaterialTheme.colorScheme.error
    AppLifecycleState.STOPPED -> MaterialTheme.colorScheme.outline
    AppLifecycleState.UNINSTALLED -> MaterialTheme.colorScheme.outlineVariant
}
