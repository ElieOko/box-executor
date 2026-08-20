package com.appbox.runtime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appbox.runtime.service.manager.InstalledAppCandidate
import com.appbox.runtime.ui.components.AppIcon
import com.appbox.runtime.ui.components.GlassSurface
import com.appbox.runtime.ui.theme.OsColors

@Composable
fun AppPickerScreen(
    candidates: List<InstalledAppCandidate>,
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onAddApp: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Ajouter une app",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OsColors.TextPrimary,
                )
                Text(
                    text = "Sélectionnez une app installée sur votre téléphone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OsColors.TextSecondary,
                )
            }
            TextButton(onClick = onClose) {
                Text("Fermer", color = OsColors.AccentCyan)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        GlassSurface(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text("Rechercher une application…") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OsColors.TextPrimary,
                    unfocusedTextColor = OsColors.TextPrimary,
                    focusedBorderColor = OsColors.AccentCyan,
                    unfocusedBorderColor = OsColors.GlassBorder,
                    cursorColor = OsColors.AccentCyan,
                    focusedPlaceholderColor = OsColors.TextMuted,
                    unfocusedPlaceholderColor = OsColors.TextMuted,
                ),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 28.dp,
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = OsColors.AccentCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Analyse des apps installées…", color = OsColors.TextSecondary)
                    }
                }
                candidates.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (query.isBlank()) {
                                "Toutes les apps disponibles sont déjà ajoutées"
                            } else {
                                "Aucun résultat"
                            },
                            color = OsColors.TextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(candidates, key = { it.packageName }) { candidate ->
                            AppPickerRow(candidate = candidate, onAdd = { onAddApp(candidate.packageName) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    candidate: InstalledAppCandidate,
    onAdd: () -> Unit,
) {
    GlassSurface(
        cornerRadius = 18.dp,
        backgroundAlpha = 0.1f,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                packageName = candidate.packageName,
                drawable = null,
                size = 48.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.displayName,
                    color = OsColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = candidate.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = OsColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (candidate.isSystemApp) {
                    Text(
                        text = "App système",
                        style = MaterialTheme.typography.labelMedium,
                        color = OsColors.AccentViolet,
                    )
                }
            }
            Text(
                text = "Ajouter",
                color = OsColors.AccentCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}
