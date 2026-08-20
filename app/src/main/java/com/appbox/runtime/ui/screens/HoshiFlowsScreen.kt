package com.appbox.runtime.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun HoshiFlowsScreen(
    workflows: List<WorkflowDefinition>,
    onOpenFlow: (WorkflowDefinition) -> Unit,
    onRunWorkflow: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Flows HOSHI", color = AppBoxThemeColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Chaque flow s'ouvre sur sa propre page — grande zone, flèches et boucles d'événements.",
            color = AppBoxThemeColors.TextSecondary,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(workflows, key = { it.id }) { workflow ->
                FlowListItem(
                    workflow = workflow,
                    onOpen = { onOpenFlow(workflow) },
                    onRun = { onRunWorkflow(workflow.id) },
                )
            }
        }
    }
}

@Composable
private fun FlowListItem(
    workflow: WorkflowDefinition,
    onOpen: () -> Unit,
    onRun: () -> Unit,
) {
    AppBoxPanel(
        cornerRadius = 14.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    workflow.name,
                    color = AppBoxThemeColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    workflow.description.ifBlank { "Automatisation visuelle" },
                    color = AppBoxThemeColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${workflow.nodes.size} étapes · ${workflow.edges.size} liens · ${workflow.canvasWidth.toInt()}×${workflow.canvasHeight.toInt()}",
                    color = AppBoxThemeColors.TextTertiary,
                    fontSize = 10.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRun),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.size(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = AppBoxThemeColors.TextTertiary)
        }
    }
}
