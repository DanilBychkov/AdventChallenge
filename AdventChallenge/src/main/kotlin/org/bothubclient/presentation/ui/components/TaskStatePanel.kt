package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bothubclient.domain.entity.StepStatus
import org.bothubclient.domain.entity.TaskContext
import org.bothubclient.domain.entity.TaskState

@Composable
fun TaskStatePanel(
    taskContext: TaskContext?,
    enabled: Boolean,
    onApprovePlan: () -> Unit,
    onApproveValidation: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val state = taskContext?.state ?: TaskState.IDLE

    CollapsibleCard(
        title = "Состояние задачи",
        isExpanded = isExpanded,
        onToggle = { isExpanded = !isExpanded },
        modifier = modifier,
        titleTrailing = {
            Text(
                text = state.name,
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        if (taskContext == null) {
            Text(
                text = "Активной задачи нет",
                fontSize = 12.sp,
                color = Color.Gray
            )
            return@CollapsibleCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "taskId",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Text(
                    text = taskContext.taskId,
                    fontSize = 11.sp,
                    color = Color.White
                )
            }

            taskContext.error?.let { err ->
                Text(
                    text = err,
                    fontSize = 11.sp,
                    color = Color(0xFFFF7777)
                )
            }

            val current = taskContext.plan.getOrNull(taskContext.currentStepIndex)
            if (current != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Текущий шаг",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${current.id} [${current.status}] ${current.description}",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }

            if (taskContext.plan.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "План",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                taskContext.plan.forEachIndexed { index, step ->
                    val color =
                        when (step.status) {
                            StepStatus.COMPLETED -> MaterialTheme.colors.secondary
                            StepStatus.FAILED -> Color(0xFFFF7777)
                            StepStatus.IN_PROGRESS -> Color(0xFFFFAA00)
                            StepStatus.PENDING -> Color.Gray
                        }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${step.id} [${step.status}] ${step.description}",
                            fontSize = 11.sp,
                            color = color
                        )
                    }
                }
            }

            val planApproved = taskContext.artifacts["planApproved"]?.toBooleanStrictOrNull() ?: false
            val planValidated = taskContext.artifacts["planValidated"]?.toBooleanStrictOrNull() ?: false
            val validationApproved =
                taskContext.artifacts["validationApproved"]?.toBooleanStrictOrNull() ?: false
            val allStepsCompleted =
                taskContext.plan.isNotEmpty() && taskContext.plan.all { it.status == StepStatus.COMPLETED }
            val canApprovePlan = state == TaskState.PLANNING && planValidated && !planApproved
            val canApproveValidation =
                state == TaskState.VALIDATION && allStepsCompleted && taskContext.error == null && !validationApproved

            if (state == TaskState.PLANNING && planValidated && !planApproved) {
                Text(
                    text = "План готов. Нажмите «Утвердить план», чтобы начать выполнение.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            if (state == TaskState.VALIDATION && allStepsCompleted && taskContext.error == null && !validationApproved) {
                Text(
                    text = "Проверки пройдены. Нажмите «Зафиксировать результат», чтобы завершить задачу.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canApprovePlan) {
                    Button(
                        onClick = onApprovePlan,
                        enabled = enabled
                    ) {
                        Text(text = "Утвердить план", fontSize = 11.sp)
                    }
                }
                if (canApproveValidation) {
                    Button(
                        onClick = onApproveValidation,
                        enabled = enabled
                    ) {
                        Text(text = "Зафиксировать результат", fontSize = 11.sp)
                    }
                }
                OutlinedButton(
                    onClick = onReset,
                    enabled = enabled && state != TaskState.IDLE
                ) {
                    Text(text = "Сбросить", fontSize = 11.sp)
                }
            }
        }
    }
}
