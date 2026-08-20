package com.appbox.runtime.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowNode
import com.appbox.runtime.core.model.WorkflowNodeType
import com.appbox.runtime.ui.theme.AppBoxThemeColors
import kotlin.math.max
import kotlin.math.roundToInt

private const val NODE_W = 152f
private const val NODE_H = 64f

@Composable
fun DraggableWorkflowCanvas(
    workflow: WorkflowDefinition,
    editMode: Boolean,
    onNodeMoved: (nodeId: String, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val flowOrder = remember(workflow.id, workflow.edges) { computeFlowOrder(workflow) }
    val stepIndex = remember(flowOrder) { flowOrder.withIndex().associate { (i, id) -> id to i + 1 } }

    val maxX = workflow.nodes.maxOfOrNull { it.positionX + NODE_W + 40f } ?: 900f
    val maxY = workflow.nodes.maxOfOrNull { it.positionY + NODE_H + 80f } ?: 600f
    val canvasWidth = max(maxX, 720f).dp
    val canvasHeight = max(maxY, 420f).dp

    val dashAnim = rememberInfiniteTransition(label = "flowDash")
    val dashPhase by dashAnim.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "dashPhase",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C0C10), Color(0xFF12121A), Color(0xFF0A0A0E)),
                ),
            )
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll),
    ) {
        Box(modifier = Modifier.size(canvasWidth, canvasHeight)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                workflow.edges.forEachIndexed { index, edge ->
                    val from = workflow.nodes.find { it.id == edge.fromNodeId } ?: return@forEachIndexed
                    val to = workflow.nodes.find { it.id == edge.toNodeId } ?: return@forEachIndexed
                    val start = Offset(from.positionX + NODE_W * 0.85f, from.positionY + NODE_H / 2f)
                    val end = Offset(to.positionX + 12f, to.positionY + NODE_H / 2f)
                    val midX = (start.x + end.x) / 2f
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(midX, start.y, midX, end.y, end.x, end.y)
                    }
                    val accent = Color(0xFF3B82F6).copy(alpha = 0.35f + (index % 3) * 0.08f)
                    drawPath(
                        path = path,
                        color = accent,
                        style = Stroke(
                            width = 3f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), dashPhase),
                        ),
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFF60A5FA).copy(alpha = 0.55f),
                        style = Stroke(width = 1.5f, cap = StrokeCap.Round),
                    )
                    drawCircle(
                        color = Color(0xFF93C5FD),
                        radius = 5f,
                        center = end,
                    )
                }
            }

            workflow.nodes.forEach { node ->
                FlowNodeCard(
                    node = node,
                    step = stepIndex[node.id],
                    editMode = editMode,
                    onNodeMoved = onNodeMoved,
                )
            }
        }
    }
}

@Composable
private fun FlowNodeCard(
    node: WorkflowNode,
    step: Int?,
    editMode: Boolean,
    onNodeMoved: (nodeId: String, x: Float, y: Float) -> Unit,
) {
    var offsetX by remember(node.id, node.positionX) { mutableFloatStateOf(node.positionX) }
    var offsetY by remember(node.id, node.positionY) { mutableFloatStateOf(node.positionY) }

    val palette = nodePalette(node.type)
    val borderColor by animateColorAsState(
        if (editMode) AppBoxThemeColors.Accent else palette.border,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "nodeBorder",
    )

    Box(
        modifier = Modifier
            .offset(offsetX.roundToInt().dp, offsetY.roundToInt().dp)
            .width(NODE_W.dp)
            .height(NODE_H.dp)
            .shadow(if (editMode) 8.dp else 4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(palette.top, palette.bottom)),
            )
            .border(1.dp, borderColor.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step != null) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(palette.accent.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        step.toString(),
                        color = AppBoxThemeColors.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = if (step != null) 8.dp else 0.dp)
                    .weight(1f),
            ) {
                Text(
                    node.label.ifBlank { node.type.name.replace('_', ' ') },
                    color = AppBoxThemeColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    node.type.name.replace('_', ' '),
                    color = AppBoxThemeColors.TextTertiary,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private data class NodePalette(val top: Color, val bottom: Color, val accent: Color, val border: Color)

private fun nodePalette(type: WorkflowNodeType): NodePalette = when {
    type.name.startsWith("TRIGGER_") -> NodePalette(Color(0xFF1E3A5F), Color(0xFF172554), Color(0xFF3B82F6), Color(0xFF2563EB))
    type.name.contains("WHATSAPP") -> NodePalette(Color(0xFF064E3B), Color(0xFF022C22), Color(0xFF25D366), Color(0xFF10B981))
    type.name.contains("HTTP") || type.name.contains("HN") || type.name.contains("PARSE") ->
        NodePalette(Color(0xFF7C2D12), Color(0xFF431407), Color(0xFFF97316), Color(0xFFEA580C))
    type.name.contains("PLATFORM") -> NodePalette(Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF818CF8), Color(0xFF6366F1))
    type.name.contains("SPEAK") -> NodePalette(Color(0xFF4A044E), Color(0xFF2E1065), Color(0xFFC084FC), Color(0xFFA855F7))
    type.name.contains("LAUNCH") || type.name.contains("OPEN_SYSTEM") ->
        NodePalette(Color(0xFF134E4A), Color(0xFF042F2E), Color(0xFF2DD4BF), Color(0xFF14B8A6))
    else -> NodePalette(Color(0xFF27272A), Color(0xFF18181B), Color(0xFF71717A), Color(0xFF52525B))
}

private fun computeFlowOrder(workflow: WorkflowDefinition): List<String> {
    if (workflow.nodes.isEmpty()) return emptyList()
    val triggers = workflow.nodes.filter { it.type.name.startsWith("TRIGGER_") }.map { it.id }
    val start = triggers.firstOrNull() ?: workflow.nodes.first().id
    val outgoing = workflow.edges.groupBy { it.fromNodeId }.mapValues { e -> e.value.map { it.toNodeId } }
    val visited = mutableSetOf<String>()
    val order = mutableListOf<String>()
    fun walk(id: String) {
        if (!visited.add(id)) return
        order += id
        outgoing[id]?.forEach { walk(it) }
    }
    walk(start)
    workflow.nodes.map { it.id }.filter { it !in visited }.forEach { walk(it) }
    return order
}
