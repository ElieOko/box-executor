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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    selectedWorkflow: WorkflowDefinition?,
    editMode: Boolean,
    onSelectWorkflow: (WorkflowDefinition) -> Unit,
    onRunWorkflow: (String) -> Unit,
    onToggleEditMode: () -> Unit,
    onNodeMoved: (String, Float, Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Flows HOSHI", color = AppBoxThemeColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Automatisations visuelles", color = AppBoxThemeColors.TextSecondary, fontSize = 12.sp)
            }
            AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.clickable(onClick = onToggleEditMode)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = if (editMode) AppBoxThemeColors.Accent else AppBoxThemeColors.TextTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (editMode) "Édition" else "Éditer",
                        color = if (editMode) AppBoxThemeColors.Accent else AppBoxThemeColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(0.32f)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(workflows, key = { it.id }) { workflow ->
                        FlowListItem(
                            workflow = workflow,
                            selected = selectedWorkflow?.id == workflow.id,
                            onClick = { onSelectWorkflow(workflow) },
                            onRun = { onRunWorkflow(workflow.id) },
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(0.68f)) {
                if (selectedWorkflow != null) {
                    AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 16.dp) {
                        DraggableWorkflowCanvas(
                            workflow = selectedWorkflow,
                            editMode = editMode,
                            onNodeMoved = onNodeMoved,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                        )
                    }
                } else {
                    AppBoxPanel(modifier = Modifier.fillMaxSize(), cornerRadius = 16.dp) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sélectionnez un flow", color = AppBoxThemeColors.TextTertiary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowListItem(
    workflow: WorkflowDefinition,
    selected: Boolean,
    onClick: () -> Unit,
    onRun: () -> Unit,
) {
    AppBoxPanel(
        cornerRadius = 12.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    workflow.name,
                    color = if (selected) AppBoxThemeColors.Accent else AppBoxThemeColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${workflow.nodes.size} nœuds · ${workflow.edges.size} liens",
                    color = AppBoxThemeColors.TextTertiary,
                    fontSize = 10.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRun),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}
