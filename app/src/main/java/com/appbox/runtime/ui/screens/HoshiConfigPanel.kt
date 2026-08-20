package com.appbox.runtime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.HoshiUserConfig
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun HoshiConfigPanel(
    config: HoshiUserConfig,
    accessibilityEnabled: Boolean,
    onConfigChange: (HoshiUserConfig) -> Unit,
    onSave: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Configuration HOSHI", color = AppBoxThemeColors.TextPrimary, fontSize = 14.sp)

            OutlinedTextField(
                value = config.whatsappPhone,
                onValueChange = { onConfigChange(config.copy(whatsappPhone = it)) },
                label = { Text("Numéro WhatsApp") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            OutlinedTextField(
                value = config.whatsappMessage,
                onValueChange = { onConfigChange(config.copy(whatsappMessage = it)) },
                label = { Text("Message WhatsApp") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.whatsappHour.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.coerceIn(0, 23)?.let {
                            onConfigChange(config.copy(whatsappHour = it))
                        }
                    },
                    label = { Text("Heure WA") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = config.whatsappMinute.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.coerceIn(0, 59)?.let {
                            onConfigChange(config.copy(whatsappMinute = it))
                        }
                    },
                    label = { Text("Min WA") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Envoi auto (Accessibilité)", color = AppBoxThemeColors.TextSecondary, fontSize = 12.sp)
                Switch(
                    checked = config.whatsappAutoSend,
                    onCheckedChange = { onConfigChange(config.copy(whatsappAutoSend = it)) },
                )
            }

            if (config.whatsappAutoSend && !accessibilityEnabled) {
                Text(
                    "Activez HOSHI dans Paramètres → Accessibilité",
                    color = AppBoxThemeColors.Accent,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppBoxPanel(
                    cornerRadius = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAccessibility),
                ) {
                    Text(
                        "Ouvrir Accessibilité",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        color = AppBoxThemeColors.Accent,
                        fontSize = 12.sp,
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.hnHour.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.coerceIn(0, 23)?.let { onConfigChange(config.copy(hnHour = it)) }
                    },
                    label = { Text("Heure HN") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = config.hnMinute.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.coerceIn(0, 59)?.let { onConfigChange(config.copy(hnMinute = it)) }
                    },
                    label = { Text("Min HN") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Écoute continue HOSHI", color = AppBoxThemeColors.TextSecondary, fontSize = 12.sp)
                Switch(
                    checked = config.voiceContinuous,
                    onCheckedChange = { onConfigChange(config.copy(voiceContinuous = it)) },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            AppBoxPanel(
                cornerRadius = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSave),
            ) {
                Text(
                    "Enregistrer la configuration",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    color = AppBoxThemeColors.Accent,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
