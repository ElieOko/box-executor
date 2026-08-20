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
import androidx.compose.ui.unit.sp
import com.appbox.runtime.service.manager.InstalledAppCandidate
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.components.AppIcon
import com.appbox.runtime.ui.theme.AppBoxThemeColors

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
            Text(
                text = "Applications installées sur l'appareil",
                color = AppBoxThemeColors.TextSecondary,
                fontSize = 14.sp,
            )
            TextButton(onClick = onClose) {
                Text("Fermer", color = AppBoxThemeColors.Accent)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AppBoxPanel(cornerRadius = 12.dp, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text("Rechercher…") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppBoxThemeColors.TextPrimary,
                    unfocusedTextColor = AppBoxThemeColors.TextPrimary,
                    focusedBorderColor = AppBoxThemeColors.Accent,
                    unfocusedBorderColor = AppBoxThemeColors.Border,
                    cursorColor = AppBoxThemeColors.Accent,
                    focusedPlaceholderColor = AppBoxThemeColors.TextTertiary,
                    unfocusedPlaceholderColor = AppBoxThemeColors.TextTertiary,
                ),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 20.dp) {
            when {
                isLoading -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = AppBoxThemeColors.Accent)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Scan PackageManager…", color = AppBoxThemeColors.TextSecondary, fontSize = 13.sp)
                }
                candidates.isEmpty() -> BoxCenterText(
                    if (query.isBlank()) "Toutes les apps sont déjà dans AppBox" else "Aucun résultat",
                )
                else -> LazyColumn(contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(candidates, key = { it.packageName }) { c ->
                        PickerRow(candidate = c, onAdd = { onAddApp(c.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxCenterText(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, color = AppBoxThemeColors.TextSecondary)
    }
}

@Composable
private fun PickerRow(candidate: InstalledAppCandidate, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = candidate.packageName, drawable = null, size = 44.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.displayName, color = AppBoxThemeColors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(candidate.packageName, color = AppBoxThemeColors.TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Ajouter", color = AppBoxThemeColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
