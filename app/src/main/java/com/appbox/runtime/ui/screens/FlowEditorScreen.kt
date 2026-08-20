package com.appbox.runtime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.HoshiEventTopics
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowEventBinding
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun FlowEditorScreen(
    workflow: WorkflowDefinition,
    editMode: Boolean,
    eventBindings: List<WorkflowEventBinding>,
    onBack: () -> Unit,
    onToggleEdit: () -> Unit,
    onRun: () -> Unit,
    onExpandCanvas: () -> Unit,
    onNodeMoved: (String, Float, Float) -> Unit,
    onSaveEventLoop: (listenTopic: String, publishTopic: String?) -> Unit,
    onRemoveEventBinding: (String) -> Unit,
) {
    var showLoopPanel by remember { mutableStateOf(false) }
    var listenTopic by remember {
        mutableStateOf(eventBindings.firstOrNull { it.workflowId == workflow.id }?.topic ?: HoshiEventTopics.WHATSAPP_SENT)
    }
    var publishTopic by remember { mutableStateOf("") }
    val bindingForFlow = eventBindings.filter { it.workflowId == workflow.id }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.clickable(onClick = onBack)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = AppBoxThemeColors.TextPrimary,
                        modifier = Modifier.padding(10.dp).size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(workflow.name, color = AppBoxThemeColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${workflow.nodes.size} étapes · zone ${workflow.canvasWidth.toInt()}×${workflow.canvasHeight.toInt()}",
                        color = AppBoxThemeColors.TextTertiary,
                        fontSize = 11.sp,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolChip(Icons.Default.ZoomOutMap, "Étendre", onExpandCanvas)
                ToolChip(Icons.Default.Loop, "Boucle", { showLoopPanel = !showLoopPanel })
                ToolChip(Icons.Default.Edit, if (editMode) "Édition" else "Éditer", onToggleEdit)
                ToolChip(Icons.Default.PlayArrow, "Run", onRun)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FlowPathPreview(workflow = workflow)

        if (showLoopPanel) {
            Spacer(modifier = Modifier.height(8.dp))
            EventLoopPanel(
                listenTopic = listenTopic,
                publishTopic = publishTopic,
                bindings = bindingForFlow,
                onListenTopicChange = { listenTopic = it },
                onPublishTopicChange = { publishTopic = it },
                onSave = {
                    onSaveEventLoop(listenTopic, publishTopic.takeIf { it.isNotBlank() })
                    showLoopPanel = false
                },
                onRemove = onRemoveEventBinding,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AppBoxPanel(modifier = Modifier.weight(1f).fillMaxWidth(), cornerRadius = 16.dp) {
            DraggableWorkflowCanvas(
                workflow = workflow,
                editMode = editMode,
                onNodeMoved = onNodeMoved,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

@Composable
private fun ToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = AppBoxThemeColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EventLoopPanel(
    listenTopic: String,
    publishTopic: String,
    bindings: List<WorkflowEventBinding>,
    onListenTopicChange: (String) -> Unit,
    onPublishTopicChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: (String) -> Unit,
) {
    AppBoxPanel(cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Boucle d'événement", color = AppBoxThemeColors.Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "Déclenche ce flow quand un topic est publié (ex. après WhatsApp ou email).",
                color = AppBoxThemeColors.TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Presets", color = AppBoxThemeColors.TextTertiary, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                HoshiEventTopics.presets.forEach { (topic, label) ->
                    AppBoxPanel(cornerRadius = 8.dp, modifier = Modifier.clickable { onListenTopicChange(topic) }) {
                        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = AppBoxThemeColors.TextPrimary)
                    }
                }
            }
            OutlinedTextField(
                value = listenTopic,
                onValueChange = onListenTopicChange,
                label = { Text("Écouter le topic") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = publishTopic,
                onValueChange = onPublishTopicChange,
                label = { Text("Publier à la fin (optionnel)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("ex: hoshi.loop.mail.sent") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onSave)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, null, tint = AppBoxThemeColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enregistrer la boucle", color = AppBoxThemeColors.Accent, fontSize = 12.sp)
                }
            }
            bindings.forEach { binding ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("↳ ${binding.topic}", color = AppBoxThemeColors.TextSecondary, fontSize = 10.sp)
                    Text(
                        "Retirer",
                        color = AppBoxThemeColors.Error,
                        fontSize = 10.sp,
                        modifier = Modifier.clickable { onRemove(binding.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowPathPreview(workflow: WorkflowDefinition) {
    val orderedNodes = remember(workflow.id, workflow.edges) {
        val outgoing = workflow.edges.groupBy { it.fromNodeId }
        val start = workflow.nodes.firstOrNull { it.type.name.startsWith("TRIGGER_") }?.id
            ?: workflow.nodes.firstOrNull()?.id
        val path = mutableListOf<com.appbox.runtime.core.model.WorkflowNode>()
        var current = start
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current)) {
            workflow.nodes.find { it.id == current }?.let { path += it }
            current = outgoing[current]?.firstOrNull()?.toNodeId
        }
        path.ifEmpty { workflow.nodes }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        orderedNodes.forEachIndexed { index, node ->
            if (index > 0) Text("→", color = AppBoxThemeColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            AppBoxPanel(cornerRadius = 20.dp) {
                Text(
                    node.label.ifBlank { node.type.name.replace('_', ' ') },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = AppBoxThemeColors.TextPrimary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
