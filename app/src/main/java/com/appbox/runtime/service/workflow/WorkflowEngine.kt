package com.appbox.runtime.service.workflow

import android.content.Context
import com.appbox.runtime.core.contract.WorkflowServiceContract
import com.appbox.runtime.core.model.WorkflowDefinition
import com.appbox.runtime.core.model.WorkflowExecutionContext
import com.appbox.runtime.core.model.WorkflowNodeType
import com.appbox.runtime.core.model.WorkflowRun
import com.appbox.runtime.core.model.WorkflowRunStatus
import com.appbox.runtime.service.RuntimeContainer
import com.appbox.runtime.service.workflow.nodes.NodeResult
import com.appbox.runtime.service.workflow.nodes.WorkflowNodeExecutor
import com.appbox.runtime.service.workflow.nodes.createNodeExecutors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class WorkflowEngine(
    context: Context,
    container: RuntimeContainer,
    onSpeak: (String) -> Unit,
) : WorkflowServiceContract {

    private val appContext = context.applicationContext
    private val store = WorkflowStore(appContext)
    private val executors: Map<WorkflowNodeType, WorkflowNodeExecutor> =
        createNodeExecutors(appContext, container, onSpeak)

    private val mutex = Mutex()
    private val workflows = mutableMapOf<String, WorkflowDefinition>()
    private val _runs = MutableStateFlow<List<WorkflowRun>>(emptyList())
    override fun runs(): Flow<List<WorkflowRun>> = _runs.asStateFlow()

    suspend fun initialize() {
        store.loadWorkflows().forEach { workflows[it.id] = it }
        _runs.value = store.loadRuns().takeLast(50)
    }

    override suspend fun registerWorkflow(definition: WorkflowDefinition): Result<Unit> = mutex.withLock {
        runCatching {
            workflows[definition.id] = definition
            store.saveWorkflows(workflows.values.toList())
        }
    }

    override suspend fun unregisterWorkflow(workflowId: String): Result<Unit> = mutex.withLock {
        runCatching {
            workflows.remove(workflowId)
            store.saveWorkflows(workflows.values.toList())
        }
    }

    override suspend fun getWorkflow(workflowId: String): WorkflowDefinition? =
        workflows[workflowId]

    override suspend fun getAllWorkflows(): List<WorkflowDefinition> =
        workflows.values.toList()

    suspend fun updateNodePosition(
        workflowId: String,
        nodeId: String,
        positionX: Float,
        positionY: Float,
    ): Result<Unit> = mutex.withLock {
        runCatching {
            val def = workflows[workflowId] ?: throw IllegalArgumentException("Workflow inconnu")
            val updatedNodes = def.nodes.map { node ->
                if (node.id == nodeId) node.copy(positionX = positionX, positionY = positionY) else node
            }
            val updated = def.copy(nodes = updatedNodes)
            workflows[workflowId] = updated
            store.saveWorkflows(workflows.values.toList())
        }
    }

    override suspend fun execute(
        workflowId: String,
        initialContext: Map<String, String>,
    ): Result<WorkflowRun> = mutex.withLock {
        val definition = workflows[workflowId]
            ?: return@withLock Result.failure(IllegalArgumentException("Workflow inconnu: $workflowId"))
        if (!definition.enabled) {
            return@withLock Result.failure(IllegalStateException("Workflow désactivé: $workflowId"))
        }

        val runId = UUID.randomUUID().toString()
        var run = WorkflowRun(
            id = runId,
            workflowId = workflowId,
            status = WorkflowRunStatus.RUNNING,
        )

        val execContext = WorkflowExecutionContext().apply {
            variables.putAll(initialContext)
            log("Démarrage workflow: ${definition.name}")
        }

        try {
            val startNode = findStartNode(definition)
                ?: throw IllegalStateException("Aucun nœud de départ trouvé")

            var currentId: String? = startNode.id
            val visited = mutableSetOf<String>()

            while (currentId != null && currentId !in visited) {
                visited += currentId
                val node = definition.nodes.find { it.id == currentId }
                    ?: throw IllegalStateException("Nœud introuvable: $currentId")

                run = run.copy(currentNodeId = node.id)
                updateRun(run)

                val executor = executors[node.type]
                    ?: throw IllegalStateException("Exécuteur absent pour ${node.type}")

                when (val result = executor.execute(node, execContext)) {
                    is NodeResult.Continue -> {
                        currentId = nextNode(definition, node.id)
                    }
                    is NodeResult.Branch -> {
                        currentId = result.nextNodeId
                    }
                    is NodeResult.Stop -> {
                        currentId = null
                    }
                    is NodeResult.Fail -> {
                        throw IllegalStateException(result.message)
                    }
                }
            }

            run = run.copy(
                status = WorkflowRunStatus.COMPLETED,
                finishedAt = System.currentTimeMillis(),
                context = execContext.variables.toMap(),
                logs = execContext.logs.toList(),
            )
            updateRun(run)
            Result.success(run)
        } catch (e: Exception) {
            run = run.copy(
                status = WorkflowRunStatus.FAILED,
                finishedAt = System.currentTimeMillis(),
                context = execContext.variables.toMap(),
                logs = execContext.logs.toList(),
                error = e.message,
            )
            updateRun(run)
            Result.failure(e)
        }
    }

    private fun findStartNode(definition: WorkflowDefinition) =
        definition.nodes.firstOrNull { it.type.name.startsWith("TRIGGER_") }
            ?: definition.nodes.minByOrNull { it.positionX + it.positionY }

    private fun nextNode(definition: WorkflowDefinition, fromId: String): String? {
        val edge = definition.edges.firstOrNull { it.fromNodeId == fromId }
        return edge?.toNodeId
    }

    private suspend fun updateRun(run: WorkflowRun) {
        val updated = (_runs.value.filter { it.id != run.id } + run).takeLast(50)
        _runs.value = updated
        store.saveRuns(updated)
    }
}
