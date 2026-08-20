package com.appbox.runtime.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.appbox.runtime.core.model.RemoteMonitorEvent
import com.appbox.runtime.ui.components.GlassSurface
import com.appbox.runtime.ui.theme.OsColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(events: List<RemoteMonitorEvent>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Monitoring",
            style = MaterialTheme.typography.headlineMedium,
            color = OsColors.TextPrimary,
        )
        Text(
            text = "Journal local — prêt pour synchronisation serveur",
            style = MaterialTheme.typography.bodyMedium,
            color = OsColors.TextSecondary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        GlassSurface(modifier = Modifier.fillMaxSize(), cornerRadius = 28.dp) {
            if (events.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Aucun événement enregistré", color = OsColors.TextSecondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(events.reversed(), key = { "${it.timestamp}_${it.type}_${it.message}" }) { event ->
                        MonitorEventCard(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonitorEventCard(event: RemoteMonitorEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    GlassSurface(cornerRadius = 16.dp, backgroundAlpha = 0.1f, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = event.type.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    color = OsColors.AccentViolet,
                )
                Text(
                    text = timeFormat.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.labelMedium,
                    color = OsColors.TextMuted,
                )
            }
            event.packageName?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = OsColors.AccentCyan)
            }
            Text(text = event.message, style = MaterialTheme.typography.bodyMedium, color = OsColors.TextPrimary)
        }
    }
}
