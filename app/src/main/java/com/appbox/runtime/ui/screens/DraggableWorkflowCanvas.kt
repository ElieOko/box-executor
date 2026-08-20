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

private const val NODE_W = 168f
private const val NODE_H = 72f
private const val GRID = 20f

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

    val contentMaxX = workflow.nodes.maxOfOrNull { it.positionX + NODE_W + 80f } ?: 0f
    val contentMaxY = workflow.nodes.maxOfOrNull { it.positionY + NODE_H + 80f } ?: 0f
    val canvasWidthPx = max(workflow.canvasWidth, contentMaxX + 200f, 2400f)
    val canvasHeightPx = max(workflow.canvasHeight, contentMaxY + 200f, 1600f)

    val dashAnim = rememberInfiniteTransition(label = "flowDash")
    val dashPhase by dashAnim.animateFloat(
        initialValue = 0f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "dashPhase",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF07070A))
            .horizontalScroll(hScroll)
            .verticalScroll(vScroll),
    ) {
        Box(modifier = Modifier.size(canvasWidthPx.dp, canvasHeightPx.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawGrid(canvasWidthPx, canvasHeightPx)
                workflow.edges.forEachIndexed { index, edge ->
                    drawFlowEdge(workflow, edge.fromNodeId, edge.toNodeId, index, dashPhase)
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(width: Float, height: Float) {
    val gridColor = Color(0xFF1E293B).copy(alpha = 0.45f)
    var x = 0f
    while (x <= width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
        x += GRID * 4
    }
    var y = 0f
    while (y <= height) {
        drawLine(gridColor, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
        y += GRID * 4
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlowEdge(
    workflow: WorkflowDefinition,
    fromId: String,
    toId: String,
    index: Int,
    dashPhase: Float,
) {
    val from = workflow.nodes.find { it.id == fromId } ?: return
    val to = workflow.nodes.find { it.id == toId } ?: return
    val start = Offset(from.positionX + NODE_W * 0.92f, from.positionY + NODE_H / 2f)
    val end = Offset(to.positionX + 16f, to.positionY + NODE_H / 2f)
    val dx = (end.x - start.x) * 0.45f
    val path = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(start.x + dx, start.y, end.x - dx, end.y, end.x, end.y)
    }
    drawPath(
        path = path,
        color = Color(0xFF2563EB).copy(alpha = 0.18f),
        style = Stroke(width = 10f, cap = StrokeCap.Round),
    )
    drawPath(
        path = path,
        color = Color(0xFF60A5FA).copy(alpha = 0.55f + (index % 2) * 0.1f),
        style = Stroke(
            width = 3.5f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f), dashPhase),
        ),
    )
    drawPath(
        path = path,
        color = Color(0xFFBFDBFE).copy(alpha = 0.85f),
        style = Stroke(width = 1.5f, cap = StrokeCap.Round),
    )
    val arrowAngle = kotlin.math.atan2(end.y - start.y, end.x - (start.x + dx))
    val arrowLen = 14f
    val tip = end
    val left = Offset(
        tip.x - arrowLen * kotlin.math.cos(arrowAngle - 0.45f),
        tip.y - arrowLen * kotlin.math.sin(arrowAngle - 0.45f),
    )
    val right = Offset(
        tip.x - arrowLen * kotlin.math.cos(arrowAngle + 0.45f),
        tip.y - arrowLen * kotlin.math.sin(arrowAngle + 0.45f),
    )
    drawLine(Color(0xFF93C5FD), left, tip, strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(Color(0xFF93C5FD), right, tip, strokeWidth = 3f, cap = StrokeCap.Round)
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
            .shadow(if (editMode) 10.dp else 5.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(palette.top, palette.bottom)))
            .border(1.5.dp, borderColor.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .then(
                if (editMode) {
                    Modifier.pointerInput(node.id) {
                        detectDragGestures(
                            onDragEnd = {
                                val snappedX = snap(offsetX)
                                val snappedY = snap(offsetY)
                                offsetX = snappedX
                                offsetY = snappedY
                                onNodeMoved(node.id, snappedX, snappedY)
                            },
                        ) { _, dragAmount ->
                            offsetX = (offsetX + dragAmount.x).coerceIn(0f, 5000f)
                            offsetY = (offsetY + dragAmount.y).coerceIn(0f, 3400f)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step != null) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(palette.accent.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(step.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = if (step != null) 10.dp else 0.dp)
                    .weight(1f),
            ) {
                Text(
                    node.label.ifBlank { node.type.name.replace('_', ' ') },
                    color = AppBoxThemeColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (editMode) "Glisser · grille ${GRID.toInt()}px" else node.type.name.replace('_', ' '),
                    color = AppBoxThemeColors.TextTertiary,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun snap(value: Float): Float = kotlin.math.round(value / GRID) * GRID

private data class NodePalette(val top: Color, val bottom: Color, val accent: Color, val border: Color)

private fun nodePalette(type: WorkflowNodeType): NodePalette = when {
    type.name.startsWith("TRIGGER_") -> NodePalette(Color(0xFF1E3A5F), Color(0xFF172554), Color(0xFF3B82F6), Color(0xFF2563EB))
    type.name.contains("WHATSAPP") -> NodePalette(Color(0xFF064E3B), Color(0xFF022C22), Color(0xFF25D366), Color(0xFF10B981))
    type.name.contains("HTTP") || type.name.contains("HN") || type.name.contains("PARSE") ->
        NodePalette(Color(0xFF7C2D12), Color(0xFF431407), Color(0xFFF97316), Color(0xFFEA580C))
    type.name.contains("PLATFORM") -> NodePalette(Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF818CF8), Color(0xFF6366F1))
    type.name.contains("SPEAK") -> NodePalette(Color(0xFF4A044E), Color(0xFF2E1065), Color(0xFFC084FC), Color(0xFFA855F7))
    type.name.contains("PUBLISH") || type.name.contains("SEND_EMAIL") ->
        NodePalette(Color(0xFF713F12), Color(0xFF422006), Color(0xFFFBBF24), Color(0xFFF59E0B))
    type.name.contains("LAUNCH") || type.name.contains("OPEN_SYSTEM") ->
        NodePalette(Color(0xFF134E4A), Color(0xFF042F2E), Color(0xFF2DD4BF), Color(0xFF14B8A6))
    else -> NodePalette(Color(0xFF27272A), Color(0xFF18181B), Color(0xFF71717A), Color(0xFF52525B))
}

private fun computeFlowOrder(workflow: WorkflowDefinition): List<String> {
    if (workflow.nodes.isEmpty()) return emptyList()
    val start = workflow.nodes.firstOrNull { it.type.name.startsWith("TRIGGER_") }?.id
        ?: workflow.nodes.first().id
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

private fun max(a: Float, b: Float, c: Float): Float = maxOf(a, b, c)
