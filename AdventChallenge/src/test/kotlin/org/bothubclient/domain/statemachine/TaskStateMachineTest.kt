package org.bothubclient.domain.statemachine

import kotlinx.coroutines.test.runTest
import org.bothubclient.domain.entity.BranchState
import org.bothubclient.domain.entity.StateMachineTemplate
import org.bothubclient.domain.entity.TaskState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TaskStateMachineTest {

    @Test
    fun `maybeStartPlanning starts planning only from IDLE`() = runTest {
        val storage = InMemoryTaskContextStorage()
        val branch = BranchState()
        val fsm =
            TaskStateMachine(
                sessionId = "s",
                branchId = "b",
                branchState = branch,
                storage = storage
            )

        assertEquals(TaskState.IDLE, fsm.getState())

        val started = fsm.maybeStartPlanning("Сделай задачу: добавить кнопку")
        assertNotNull(started)
        assertEquals(TaskState.PLANNING, fsm.getState())

        val second = fsm.maybeStartPlanning("Сделай задачу: ещё одну")
        assertNull(second)
        assertEquals(TaskState.PLANNING, fsm.getState())
    }

    @Test
    fun `advance in PLANNING creates plan and waits for approval`() = runTest {
        val storage = InMemoryTaskContextStorage()
        val branch = BranchState()
        val fsm =
            TaskStateMachine(
                sessionId = "s",
                branchId = "b",
                branchState = branch,
                storage = storage,
                template = StateMachineTemplate.FULL_PIPELINE
            )

        fsm.maybeStartPlanning("Реализуй фичу.\nС шагами.\nПроверь результат.")
        val ctx = fsm.advance()
        assertNotNull(ctx)
        assertEquals(TaskState.PLANNING, ctx!!.state)
        assertTrue(ctx.plan.isNotEmpty())
        assertEquals("true", ctx.artifacts["planValidated"])

        fsm.handleUserMessage("Утверждаю план, начинай")
        val afterApprove = fsm.advance()
        assertNotNull(afterApprove)
        assertEquals(TaskState.EXECUTION, afterApprove!!.state)
    }

    @Test
    fun `EXECUTION completes step based on last assistant message and moves to VALIDATION`() =
        runTest {
            val storage = InMemoryTaskContextStorage()
            val branch = BranchState()
            val fsm =
                TaskStateMachine(
                    sessionId = "s",
                    branchId = "b",
                    branchState = branch,
                    storage = storage,
                    template = StateMachineTemplate.FULL_PIPELINE
                )

            fsm.maybeStartPlanning("Сделай задачу: один шаг")
            fsm.advance()
            fsm.handleUserMessage("Утверждаю, начинай")
            val ctx1 = fsm.advance()
            assertEquals(TaskState.EXECUTION, ctx1!!.state)

            val ctx2 = fsm.advance()
            assertEquals(TaskState.EXECUTION, ctx2!!.state)
            assertTrue(ctx2.plan.isNotEmpty())

            val ctx3 = fsm.advance(assistantMessage = "Готово.")
            assertEquals(TaskState.VALIDATION, ctx3!!.state)
            assertTrue(ctx3.plan.all { it.result != null })
        }

    @Test
    fun `VALIDATION moves to DONE when all steps completed`() = runTest {
        val storage = InMemoryTaskContextStorage()
        val branch = BranchState()
        val fsm =
            TaskStateMachine(
                sessionId = "s",
                branchId = "b",
                branchState = branch,
                storage = storage,
                template = StateMachineTemplate.FULL_PIPELINE
            )

        fsm.maybeStartPlanning("Сделай задачу: один шаг")
        fsm.advance()
        fsm.handleUserMessage("Утверждаю, начинай")
        fsm.advance()
        fsm.advance()
        fsm.advance(assistantMessage = "Результат шага")
        val afterExec = fsm.getTaskContext()
        assertEquals(TaskState.VALIDATION, afterExec!!.state)

        val waiting = fsm.advance()
        assertEquals(TaskState.VALIDATION, waiting!!.state)
        assertTrue(waiting.artifacts.containsKey("validationReport"))
        assertTrue(waiting.artifacts.containsKey("finalResultCandidate"))

        fsm.handleUserMessage("Заверши и зафиксируй результат")
        val done = fsm.advance()
        assertEquals(TaskState.DONE, done!!.state)
        assertTrue(done.artifacts.containsKey("finalResult"))
    }

    @Test
    fun `reset clears task context`() = runTest {
        val storage = InMemoryTaskContextStorage()
        val branch = BranchState()
        val fsm =
            TaskStateMachine(
                sessionId = "s",
                branchId = "b",
                branchState = branch,
                storage = storage
            )

        fsm.maybeStartPlanning("Сделай задачу: очистка")
        assertEquals(TaskState.PLANNING, fsm.getState())

        fsm.reset()
        assertEquals(TaskState.IDLE, fsm.getState())
        assertNull(synchronized(branch) { branch.taskContext })
    }
}
