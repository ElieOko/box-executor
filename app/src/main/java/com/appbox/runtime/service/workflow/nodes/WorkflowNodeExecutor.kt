package com.appbox.runtime.service.workflow.nodes

import com.appbox.runtime.core.model.WorkflowExecutionContext
import com.appbox.runtime.core.model.WorkflowNode
import com.appbox.runtime.core.model.WorkflowNodeType

interface WorkflowNodeExecutor {
    val type: WorkflowNodeType
    suspend fun execute(node: WorkflowNode, context: WorkflowExecutionContext): NodeResult
}

sealed class NodeResult {
    data object Continue : NodeResult()
    data object Stop : NodeResult()
    data class Branch(val nextNodeId: String) : NodeResult()
    data class Fail(val message: String) : NodeResult()
}
