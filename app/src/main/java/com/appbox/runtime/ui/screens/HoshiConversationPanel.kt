package com.appbox.runtime.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.AgentStatus
import com.appbox.runtime.core.model.ConversationTurn
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.theme.AppBoxThemeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HoshiConversationPanel(
    conversationTurns: List<ConversationTurn>,
    lastVoiceText: String?,
    agentStatus: AgentStatus,
    jarvisMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(conversationTurns.size, lastVoiceText) {
        if (conversationTurns.isNotEmpty() || lastVoiceText != null) {
            listState.animateScrollToItem(maxOf(0, conversationTurns.size + 1))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ListeningHeader(agentStatus = agentStatus, jarvisMode = jarvisMode, lastVoiceText = lastVoiceText)

        Spacer(modifier = Modifier.height(8.dp))

        AppBoxPanel(
            cornerRadius = 16.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (conversationTurns.isEmpty() && lastVoiceText.isNullOrBlank()) {
                EmptyConversationHint(jarvisMode = jarvisMode)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(conversationTurns.size) { index ->
                        val turn = conversationTurns[index]
                        ChatBubble(turn = turn)
                    }
                    if (!lastVoiceText.isNullOrBlank() && agentStatus == AgentStatus.LISTENING) {
                        item(key = "partial") {
                            PartialBubble(text = lastVoiceText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningHeader(
    agentStatus: AgentStatus,
    jarvisMode: Boolean,
    lastVoiceText: String?,
) {
    val pulse = rememberInfiniteTransition(label = "micPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val isActive = agentStatus == AgentStatus.LISTENING || agentStatus == AgentStatus.EXECUTING

    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .alpha(if (isActive) 1f else 0.6f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (isActive) {
                                listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                            } else {
                                listOf(Color(0xFF27272A), Color(0xFF18181B))
                            },
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(if (isActive) scale.coerceIn(0.9f, 1.1f) else 1f),
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    when (agentStatus) {
                        AgentStatus.LISTENING -> "J'écoute…"
                        AgentStatus.EXECUTING -> "Exécution en cours"
                        AgentStatus.WAITING_SCHEDULE -> if (jarvisMode) "HOSHI en veille active" else "En attente"
                        AgentStatus.ERROR -> "Anomalie détectée"
                        else -> if (jarvisMode) "Dites « HOSHI » ou parlez" else "Micro actif"
                    },
                    color = AppBoxThemeColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    statusSubtitle(agentStatus, jarvisMode),
                    color = AppBoxThemeColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(turn: ConversationTurn) {
    val isAssistant = turn.role == "assistant"
    val time = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(turn.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End,
    ) {
        if (isAssistant) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AppBoxThemeColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.SmartToy, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.size(8.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .animateContentSize()
                .clip(
                    RoundedCornerShape(
                        topStart = if (isAssistant) 4.dp else 16.dp,
                        topEnd = if (isAssistant) 16.dp else 4.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                )
                .background(
                    if (isAssistant) {
                        Brush.linearGradient(listOf(Color(0xFF1E3A5F), Color(0xFF172554)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF27272A), Color(0xFF1F1F23)))
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                if (isAssistant) "HOSHI" else "Vous",
                color = if (isAssistant) AppBoxThemeColors.Accent else AppBoxThemeColors.TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                turn.text,
                color = AppBoxThemeColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Text(time, color = AppBoxThemeColors.TextTertiary, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PartialBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF3F3F46).copy(alpha = 0.6f))
                .padding(10.dp),
        ) {
            Text("… $text", color = AppBoxThemeColors.TextSecondary, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
    }
}

@Composable
private fun EmptyConversationHint(jarvisMode: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Conversation HOSHI", color = AppBoxThemeColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (jarvisMode) {
                    "Parlez naturellement. HOSHI termine sa réponse avant d'écouter à nouveau."
                } else {
                    "Dites « HOSHI » puis votre commande."
                },
                color = AppBoxThemeColors.TextTertiary,
                fontSize = 12.sp,
            )
        }
    }
}

private fun statusSubtitle(status: AgentStatus, jarvisMode: Boolean): String = when (status) {
    AgentStatus.LISTENING -> "Parole détectée — traitement…"
    AgentStatus.EXECUTING -> "Workflow ou réponse en cours"
    AgentStatus.ERROR -> "Réessayez ou vérifiez la configuration"
    AgentStatus.WAITING_SCHEDULE -> "Briefings et tâches planifiées actifs"
    else -> if (jarvisMode) "Micro continu — écoute après la réponse HOSHI" else "Écoute permanente"
}
