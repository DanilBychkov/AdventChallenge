package org.bothubclient.domain.statemachine

import org.bothubclient.domain.entity.*
import org.bothubclient.domain.repository.TaskContextStorage
import org.bothubclient.infrastructure.logging.FileLogger
import java.util.*

class TaskStateMachine(
    private val sessionId: String,
    private val branchId: String,
    private val branchState: BranchState,
    private val storage: TaskContextStorage
) {
    private val tag = "TaskStateMachine"

    fun getState(): TaskState = synchronized(branchState) { branchState.taskContext?.state } ?: TaskState.IDLE

    fun getTaskContext(): TaskContext? = synchronized(branchState) { branchState.taskContext }

    fun getCurrentStep(): TaskStep? =
        synchronized(branchState) {
            val ctx = branchState.taskContext ?: return@synchronized null
            ctx.plan.getOrNull(ctx.currentStepIndex)
        }

    fun getArtifact(key: String): String? =
        synchronized(branchState) { branchState.taskContext?.artifacts?.get(key) }

    suspend fun setArtifact(key: String, value: String): TaskContext? {
        val updated =
            synchronized(branchState) {
                val ctx = branchState.taskContext ?: return@synchronized null
                val now = System.currentTimeMillis()
                val newCtx = ctx.copy(artifacts = ctx.artifacts + (key to value), updatedAt = now)
                branchState.taskContext = newCtx
                newCtx
            }
        if (updated != null) {
            storage.save(sessionId, branchId, updated)
        }
        return updated
    }

    suspend fun reset(): TaskContext? {
        val prev = synchronized(branchState) { branchState.taskContext }
        synchronized(branchState) { branchState.taskContext = null }
        storage.save(sessionId, branchId, null)
        if (prev != null) {
            FileLogger.log(tag, "Reset taskContext: sessionId=$sessionId branchId=$branchId taskId=${prev.taskId}")
        }
        return null
    }

    suspend fun maybeStartPlanning(userMessage: String): TaskContext? {
        val hasTask = containsTask(userMessage)
        val state = getState()
        if (!hasTask || state != TaskState.IDLE) return null

        val now = System.currentTimeMillis()
        val ctx =
            TaskContext(
                taskId = UUID.randomUUID().toString(),
                state = TaskState.PLANNING,
                originalRequest = userMessage.trim(),
                plan = emptyList(),
                currentStepIndex = 0,
                artifacts =
                    mapOf(
                        "originalRequest" to userMessage.trim(),
                        "retryCount" to "0",
                        "planApproved" to "false",
                        "validationApproved" to "false"
                    ),
                error = null,
                createdAt = now,
                updatedAt = now
            )

        synchronized(branchState) { branchState.taskContext = ctx }
        storage.save(sessionId, branchId, ctx)
        FileLogger.log(tag, "Transition IDLE -> PLANNING: sessionId=$sessionId branchId=$branchId taskId=${ctx.taskId}")
        return ctx
    }

    suspend fun transitionTo(newState: TaskState): TaskContext {
        val before = synchronized(branchState) { branchState.taskContext }
        val currentState = before?.state ?: TaskState.IDLE

        val updated =
            synchronized(branchState) {
                val existing = branchState.taskContext
                val now = System.currentTimeMillis()
                val base =
                    existing ?: TaskContext(
                        taskId = UUID.randomUUID().toString(),
                        state = TaskState.IDLE,
                        originalRequest = "",
                        plan = emptyList(),
                        currentStepIndex = 0,
                        artifacts = emptyMap(),
                        error = null,
                        createdAt = now,
                        updatedAt = now
                    )

                val candidate = base.copy(state = newState, updatedAt = now)
                validateTransition(currentState, newState, candidate)
                branchState.taskContext = candidate
                candidate
            }

        storage.save(sessionId, branchId, updated)
        FileLogger.log(
            tag,
            "Transition $currentState -> $newState: sessionId=$sessionId branchId=$branchId taskId=${updated.taskId}"
        )
        return updated
    }

    suspend fun handleUserMessage(userMessage: String): TaskContext? {
        val ctx = synchronized(branchState) { branchState.taskContext } ?: return null
        val msg = userMessage.trim()
        if (msg.isBlank()) return ctx

        val lower = msg.lowercase()
        val approvePlan =
            ctx.state == TaskState.PLANNING &&
                    listOf("утверждаю", "approve", "одобряю", "ок, делай", "ок делай", "поехали", "начинай")
                        .any { lower.contains(it) }
        val approveValidation =
            ctx.state == TaskState.VALIDATION &&
                    listOf("заверши", "фиксируй", "готово", "done", "approve", "утверждаю")
                        .any { lower.contains(it) }

        val updated =
            when {
                approvePlan -> setArtifactInternal(ctx, "planApproved", "true")
                approveValidation -> setArtifactInternal(ctx, "validationApproved", "true")
                else -> ctx
            }

        if (updated !== ctx) {
            synchronized(branchState) { branchState.taskContext = updated }
            storage.save(sessionId, branchId, updated)
        }

        return updated
    }

    suspend fun advance(userMessage: String? = null, assistantMessage: String? = null): TaskContext? {
        val ctx = synchronized(branchState) { branchState.taskContext } ?: return null

        return when (ctx.state) {
            TaskState.PLANNING -> advancePlanning(ctx)
            TaskState.EXECUTION -> advanceExecution(ctx, assistantMessage)
            TaskState.VALIDATION -> advanceValidation(ctx)
            TaskState.DONE, TaskState.IDLE -> ctx
        }
    }

    private suspend fun advancePlanning(ctx: TaskContext): TaskContext {
        val plan = if (ctx.plan.isNotEmpty()) ctx.plan else generatePlan(ctx.originalRequest)
        val validated = validatePlan(plan)
        val now = System.currentTimeMillis()

        val approved = ctx.artifacts["planApproved"]?.toBooleanStrictOrNull() ?: false
        val updated =
            ctx.copy(
                plan = plan,
                artifacts = ctx.artifacts + ("planValidated" to validated.toString()),
                updatedAt = now,
                error = if (validated) null else "Plan validation failed"
            )

        val next =
            if (validated && plan.isNotEmpty() && approved) {
                updated.copy(state = TaskState.EXECUTION, currentStepIndex = 0, updatedAt = now)
            } else {
                updated
            }

        synchronized(branchState) { branchState.taskContext = next }
        storage.save(sessionId, branchId, next)

        if (next.state != ctx.state) {
            FileLogger.log(
                tag,
                "Transition PLANNING -> EXECUTION: sessionId=$sessionId branchId=$branchId taskId=${ctx.taskId}"
            )
        }
        return next
    }

    private suspend fun advanceExecution(ctx: TaskContext, assistantMessage: String?): TaskContext {
        if (ctx.plan.isEmpty()) {
            val failed =
                ctx.copy(state = TaskState.VALIDATION, error = "Empty plan", updatedAt = System.currentTimeMillis())
            synchronized(branchState) { branchState.taskContext = failed }
            storage.save(sessionId, branchId, failed)
            FileLogger.log(
                tag,
                "Transition EXECUTION -> VALIDATION (empty plan): sessionId=$sessionId branchId=$branchId taskId=${ctx.taskId}"
            )
            return failed
        }

        val idx = ctx.currentStepIndex.coerceIn(0, ctx.plan.lastIndex)
        val currentStep = ctx.plan[idx]
        val now = System.currentTimeMillis()

        val updatedStep =
            when (currentStep.status) {
                StepStatus.PENDING ->
                    currentStep.copy(status = StepStatus.IN_PROGRESS)

                StepStatus.IN_PROGRESS ->
                    assistantMessage
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { currentStep.copy(status = StepStatus.COMPLETED, result = it) }
                        ?: currentStep

                StepStatus.COMPLETED, StepStatus.FAILED -> currentStep
            }

        val updatedPlan =
            ctx.plan.toMutableList().also { it[idx] = updatedStep }
                .toList()

        val artifactsUpdate =
            if (updatedStep.status == StepStatus.COMPLETED && updatedStep.result != null) {
                ctx.artifacts + ("step.${updatedStep.id}.result" to updatedStep.result)
            } else {
                ctx.artifacts
            }

        val progressed =
            ctx.copy(
                plan = updatedPlan,
                currentStepIndex = idx,
                artifacts = artifactsUpdate,
                updatedAt = now
            )

        val next =
            if (updatedStep.status == StepStatus.COMPLETED) {
                if (idx < updatedPlan.lastIndex) {
                    progressed.copy(currentStepIndex = idx + 1, updatedAt = now)
                } else {
                    progressed.copy(state = TaskState.VALIDATION, updatedAt = now)
                }
            } else {
                progressed
            }

        synchronized(branchState) { branchState.taskContext = next }
        storage.save(sessionId, branchId, next)

        if (next.state != ctx.state) {
            FileLogger.log(
                tag,
                "Transition EXECUTION -> VALIDATION: sessionId=$sessionId branchId=$branchId taskId=${ctx.taskId}"
            )
        } else if (next.currentStepIndex != ctx.currentStepIndex || updatedStep.status != currentStep.status) {
            FileLogger.log(
                tag,
                "Stay EXECUTION: sessionId=$sessionId branchId=$branchId taskId=${ctx.taskId} stepId=${currentStep.id} status=${updatedStep.status}"
            )
        }

        return next
    }

    private suspend fun advanceValidation(ctx: TaskContext): TaskContext {
        val passed = ctx.error == null && ctx.plan.all { it.status == StepStatus.COMPLETED }
        val now = System.currentTimeMillis()

        val report = buildValidationReport(ctx, passed)
        val withReport = ctx.copy(artifacts = ctx.artifacts + ("validationReport" to report), updatedAt = now)

        if (passed) {
            val finalResult = ctx.plan.joinToString("\n\n") { it.result.orEmpty() }.trim()
            val approved = ctx.artifacts["validationApproved"]?.toBooleanStrictOrNull() ?: false
            val ready =
                withReport.copy(
                    artifacts = withReport.artifacts + ("finalResultCandidate" to finalResult),
                    updatedAt = now
                )
            if (!approved) {
                synchronized(branchState) { branchState.taskContext = ready }
                storage.save(sessionId, branchId, ready)
                return ready
            }

            val done =
                ready.copy(
                    state = TaskState.DONE,
                    artifacts = ready.artifacts + ("finalResult" to finalResult),
                    updatedAt = now
                )
            synchronized(branchState) { branchState.taskContext = done }
            storage.save(sessionId, branchId, done)
            FileLogger.log(
                tag,
                "Transition VALIDATION -> DONE (success): sessionId=$sessionId branchId=$branchId taskId=${ctx.taskId}"
            )
            return done
        }

        val retryCount = (ctx.artifacts["retryCount"]?.toIntOrNull() ?: 0)
        if (retryCount >= 3) {
            val failure =
                withReport.copy(
                    state = TaskState.DONE,
                    artifacts = withReport.artifacts + ("failureReport" to report),
                    updatedAt = now
                )
            synchronized(branchState) { branchState.taskContext = failure }
            storage.save(sessionId, branchId, failure)
            FileLogger.log(
                tag,
                "Transition VALIDATION -> DONE (failed): sessionId=$sessionId branchId=$branchId taskId=${ctx.taskId} retryCount=$retryCount"
            )
            return failure
        }

        val updatedPlan =
            ctx.plan.map {
                if (it.status == StepStatus.FAILED) it.copy(status = StepStatus.PENDING, result = null) else it
            }

        val updatedArtifacts =
            withReport.artifacts + ("retryCount" to (retryCount + 1).toString()) + ("failureReason" to (ctx.error
                ?: "Validation failed"))

        val retry =
            withReport.copy(
                state = TaskState.EXECUTION,
                plan = updatedPlan,
                currentStepIndex = firstIncompleteIndex(updatedPlan),
                artifacts = updatedArtifacts,
                error = null,
                updatedAt = now
            )

        synchronized(branchState) { branchState.taskContext = retry }
        storage.save(sessionId, branchId, retry)
        FileLogger.log(
            tag,
            "Transition VALIDATION -> EXECUTION (retry): sessionId=$sessionId branchId=$branchId taskId=${ctx.taskId} retryCount=${retryCount + 1}"
        )
        return retry
    }

    fun validateTransition(from: TaskState, to: TaskState, context: TaskContext) {
        val allowed =
            when (from) {
                TaskState.IDLE -> to == TaskState.PLANNING
                TaskState.PLANNING -> to == TaskState.EXECUTION || to == TaskState.PLANNING
                TaskState.EXECUTION -> to == TaskState.EXECUTION || to == TaskState.VALIDATION
                TaskState.VALIDATION -> to == TaskState.DONE || to == TaskState.EXECUTION || to == TaskState.VALIDATION
                TaskState.DONE -> to == TaskState.IDLE || to == TaskState.DONE
            }
        if (!allowed) {
            throw IllegalStateException("Invalid transition: $from -> $to")
        }

        if (to == TaskState.EXECUTION) {
            val validated = context.artifacts["planValidated"]?.toBooleanStrictOrNull() ?: false
            if (context.plan.isEmpty() || !validated) {
                throw IllegalStateException("Invalid transition to EXECUTION: plan.isEmpty=${context.plan.isEmpty()} validated=$validated")
            }
        }

        if (to == TaskState.VALIDATION && context.plan.isEmpty()) {
            throw IllegalStateException("Invalid transition to VALIDATION: plan is empty")
        }

        if (from == TaskState.VALIDATION && to == TaskState.DONE) {
            val approved = context.artifacts["validationApproved"]?.toBooleanStrictOrNull() ?: false
            val failed = context.artifacts.containsKey("failureReport")
            if (!approved && !failed) {
                throw IllegalStateException("Invalid transition to DONE: validationApproved=false")
            }
        }
    }

    private fun setArtifactInternal(ctx: TaskContext, key: String, value: String): TaskContext {
        val now = System.currentTimeMillis()
        return ctx.copy(artifacts = ctx.artifacts + (key to value), updatedAt = now)
    }

    private fun containsTask(userMessage: String): Boolean {
        val msg = userMessage.trim()
        if (msg.isBlank()) return false
        if (msg.startsWith("# TASK", ignoreCase = true)) return true
        if (msg.startsWith("TASK", ignoreCase = true)) return true
        if (msg.startsWith("СДЕЛАЙ", ignoreCase = true)) return true
        if (msg.startsWith("РЕАЛИЗУЙ", ignoreCase = true)) return true
        return msg.length >= 20
    }

    private fun generatePlan(originalRequest: String): List<TaskStep> {
        val chunks =
            originalRequest
                .split(Regex("[\\n\\r]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .flatMap { it.split(Regex("(?<=[.!?])\\s+")) }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(8)

        val lines =
            if (chunks.isEmpty()) listOf(originalRequest.trim()).filter { it.isNotBlank() } else chunks

        return lines.mapIndexed { i, line ->
            TaskStep(
                id = "step-${i + 1}",
                description = line,
                status = StepStatus.PENDING,
                result = null
            )
        }
    }

    private fun validatePlan(plan: List<TaskStep>): Boolean {
        if (plan.isEmpty()) return false
        if (plan.any { it.id.isBlank() || it.description.isBlank() }) return false
        if (plan.map { it.id }.distinct().size != plan.size) return false
        return true
    }

    private fun buildValidationReport(ctx: TaskContext, passed: Boolean): String {
        val header = if (passed) "VALIDATION PASSED" else "VALIDATION FAILED"
        val steps =
            ctx.plan.joinToString("\n") { s ->
                val res = s.result?.let { it.take(160).replace("\n", " ") }.orEmpty()
                "${s.id} [${s.status}] ${s.description.take(80)} ${if (res.isBlank()) "" else "=> $res"}".trim()
            }
        val errorLine = ctx.error?.let { "\nerror=$it" }.orEmpty()
        return "$header\n$steps$errorLine"
    }

    private fun firstIncompleteIndex(plan: List<TaskStep>): Int {
        val idx = plan.indexOfFirst { it.status != StepStatus.COMPLETED }
        return if (idx >= 0) idx else (plan.lastIndex.coerceAtLeast(0))
    }
}
