package com.appbox.runtime.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowNode
import com.appbox.runtime.ui.theme.AppBoxThemeColors
import kotlin.math.roundToInt

@Composable
fun DraggableWorkflowCanvas(
    workflow: WorkflowDefinition,
    editMode: Boolean,
    onNodeMoved: (nodeId: String, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val nodePositions = remember(workflow.id, workflow.nodes) {
        workflow.nodes.associate { it.id to (it.positionX to it.positionY) }
    }

    val maxY = workflow.nodes.maxOfOrNull { nodePositions[it.id]?.second ?: 0f } ?: 600f
    val canvasHeight = (maxY + 120f).dp

    BoxWithConstraints(modifier = modifier.verticalScroll(scrollState)) {
        Box(modifier = Modifier.fillMaxSize().height(canvasHeight)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                workflow.edges.forEach { edge ->
                    val from = workflow.nodes.find { it.id == edge.fromNodeId } ?: return@forEach
                    val to = workflow.nodes.find { it.id == edge.toNodeId } ?: return@forEach
                    val start = Offset(from.positionX + 70f, from.positionY + 28f)
                    val end = Offset(to.positionX + 70f, to.positionY + 28f)
                    drawLine(
                        color = Color(0xFF4A5568),
                        start = start,
                        end = end,
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    )
                }
            }

            workflow.nodes.forEach { node ->
                DraggableNodeChip(
                    node = node,
                    editMode = editMode,
                    onNodeMoved = onNodeMoved,
                )
            }
        }
    }
}

@Composable
private fun DraggableNodeChip(
    node: WorkflowNode,
    editMode: Boolean,
    onNodeMoved: (nodeId: String, x: Float, y: Float) -> Unit,
) {
    var offsetX by remember(node.id, node.positionX) { mutableFloatStateOf(node.positionX) }
    var offsetY by remember(node.id, node.positionY) { mutableFloatStateOf(node.positionY) }

    val color = when {
        node.type.name.startsWith("TRIGGER_") -> Color(0xFF2B6CB0)
        node.type.name.contains("WHATSAPP") -> Color(0xFF25D366)
        node.type.name.contains("HTTP") || node.type.name.contains("HN") -> Color(0xFFDD6B20)
        node.type.name.contains("ACCESSIBILITY") -> Color(0xFF9F7AEA)
        else -> Color(0xFF4A5568)
    }

    Box(
        modifier = Modifier
            .offset(offsetX.roundToInt().dp, offsetY.roundToInt().dp)
            .width(140.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (editMode) 0.4f else 0.25f))
            .then(
                if (editMode) {
                    Modifier.pointerInput(node.id) {
                        detectDragGestures { _, dragAmount ->
                            offsetX = (offsetX + dragAmount.x).coerceAtLeast(0f)
                            offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                            onNodeMoved(node.id, offsetX, offsetY)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(8.dp),
    ) {
        Column {
            Text(
                node.label.ifBlank { node.type.name },
                color = AppBoxThemeColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (editMode) "Glisser-déposer" else node.type.name.replace('_', ' '),
                color = AppBoxThemeColors.TextTertiary,
                fontSize = 9.sp,
            )
        }
    }
}
