package org.bothubclient.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bothubclient.domain.entity.ContextConfig
import org.bothubclient.domain.entity.StateMachineTemplate

@Composable
fun LLMStateMachinePanel(
    config: ContextConfig,
    onTaskStateMachineToggled: (Boolean) -> Unit,
    onStateMachineTemplateSelected: (StateMachineTemplate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isExpanded = remember { mutableStateOf(true) }
    val templateDropdownExpanded = remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded.value = !isExpanded.value },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LLM State Machine",
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector =
                        if (isExpanded.value) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded.value) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colors.secondary
                )
            }

            AnimatedVisibility(
                visible = isExpanded.value,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                onTaskStateMachineToggled(!config.enableTaskStateMachine)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Включить LLM State Machine",
                            fontSize = 12.sp,
                            color = if (enabled) Color.White else Color.Gray
                        )
                        Checkbox(
                            checked = config.enableTaskStateMachine,
                            onCheckedChange = { if (enabled) onTaskStateMachineToggled(it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colors.secondary,
                                uncheckedColor = Color.Gray
                            ),
                            enabled = enabled
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Шаблон поведения",
                            fontSize = 12.sp,
                            color = if (enabled) Color.White else Color.Gray
                        )
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) {
                                        templateDropdownExpanded.value = true
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = config.stateMachineTemplate.displayName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colors.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Выбрать шаблон",
                                    tint = MaterialTheme.colors.secondary
                                )
                            }
                            DropdownMenu(
                                expanded = templateDropdownExpanded.value,
                                onDismissRequest = { templateDropdownExpanded.value = false }
                            ) {
                                StateMachineTemplate.entries.forEach { template ->
                                    DropdownMenuItem(
                                        onClick = {
                                            templateDropdownExpanded.value = false
                                            if (enabled) onStateMachineTemplateSelected(template)
                                        }
                                    ) {
                                        Column {
                                            Text(
                                                text = template.displayName,
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = template.description,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = config.stateMachineTemplate.description,
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    if (config.enableTaskStateMachine) {
                        Text(
                            text = "Обязательное поведение: Уточнение + Реализация",
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.secondary.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}
