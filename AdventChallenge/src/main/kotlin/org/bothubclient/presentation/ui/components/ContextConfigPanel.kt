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
import org.bothubclient.domain.entity.*
import org.bothubclient.domain.memory.MemoryItem

@Composable
fun ContextConfigPanel(
    config: ContextConfig,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onStrategyChanged: (ContextStrategy) -> Unit,
    stmCount: Int,
    workingMemory: Map<WmCategory, Map<String, FactEntry>>,
    longTermMemory: List<MemoryItem>,
    onLoadLongTermMemory: () -> Unit,
    branches: List<String>,
    activeBranchId: String,
    checkpointSize: Int,
    maxCheckpointSize: Int,
    onCheckpointSizeChanged: (Int) -> Unit,
    onBranchSelected: (String) -> Unit,
    onCreateBranchFromCheckpoint: () -> Unit,
    onKeepLastNChanged: (Int) -> Unit,
    onCompressionBlockSizeChanged: (Int) -> Unit,
    onAutoCompressionToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val contentScrollState = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚙️ Настройки контекста",
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(
                    imageVector =
                        if (isExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colors.secondary
                )
            }

            if (isExpanded) {
                Column(
                    modifier =
                        Modifier.padding(top = 12.dp)
                            .heightIn(max = 260.dp)
                            .verticalScroll(contentScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StrategySelector(
                        selected = config.strategy,
                        onSelected = onStrategyChanged,
                        enabled = enabled
                    )

                    ShortTermMemoryPanel(stmCount = stmCount, enabled = enabled)

                    WorkingMemoryPanel(
                        enabled = enabled,
                        isEnabledInConfig = config.enableFactsMemory,
                        workingMemory = workingMemory
                    )

                    LongTermMemoryPanel(
                        enabled = enabled,
                        items = longTermMemory,
                        onLoad = onLoadLongTermMemory
                    )

                    if (config.strategy == ContextStrategy.BRANCHING) {
                        BranchingControls(
                            branches = branches,
                            activeBranchId = activeBranchId,
                            checkpointSize = checkpointSize,
                            maxCheckpointSize = maxCheckpointSize,
                            onCheckpointSizeChanged = onCheckpointSizeChanged,
                            onBranchSelected = onBranchSelected,
                            onCreateBranchFromCheckpoint = onCreateBranchFromCheckpoint,
                            enabled = enabled
                        )
                    }

                    ConfigSlider(
                        label = "Сохранять сообщений",
                        value = config.keepLastN,
                        valueRange = 2f..50f,
                        onValueChange = { onKeepLastNChanged(it.toInt()) },
                        enabled = enabled
                    )

                    ConfigSlider(
                        label = "Размер блока сжатия",
                        value = config.compressionBlockSize,
                        valueRange = 2f..20f,
                        onValueChange = { onCompressionBlockSizeChanged(it.toInt()) },
                        enabled = enabled
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth().clickable(enabled = enabled) {
                                onAutoCompressionToggled(!config.enableAutoCompression)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Автоматическое сжатие",
                            fontSize = 12.sp,
                            color = if (enabled) Color.White else Color.Gray
                        )
                        Checkbox(
                            checked = config.enableAutoCompression,
                            onCheckedChange = { if (enabled) onAutoCompressionToggled(it) },
                            colors =
                                CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colors.secondary,
                                    uncheckedColor = Color.Gray
                                ),
                            enabled = enabled
                        )
                    }

                    ConfigInfoRow(
                        label = "Макс. блоков сжатия",
                        value = config.maxSummaryBlocks.toString()
                    )

                    ConfigInfoRow(
                        label = "Порог сжатия",
                        value = "${(config.compressionThreshold * 100).toInt()}%"
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategySelector(
    selected: ContextStrategy,
    onSelected: (ContextStrategy) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Стратегия контекста",
            fontSize = 12.sp,
            color = if (enabled) Color.White else Color.Gray
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StrategyRadio(
                label = "Sliding",
                selected = selected == ContextStrategy.SLIDING_WINDOW,
                onClick = { if (enabled) onSelected(ContextStrategy.SLIDING_WINDOW) },
                enabled = enabled
            )
            StrategyRadio(
                label = "Facts",
                selected = selected == ContextStrategy.STICKY_FACTS,
                onClick = { if (enabled) onSelected(ContextStrategy.STICKY_FACTS) },
                enabled = enabled
            )
            StrategyRadio(
                label = "Branching",
                selected = selected == ContextStrategy.BRANCHING,
                onClick = { if (enabled) onSelected(ContextStrategy.BRANCHING) },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun ShortTermMemoryPanel(stmCount: Int, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STM (сообщения)",
                fontSize = 12.sp,
                color = if (enabled) Color.White else Color.Gray
            )
            Text(
                text = stmCount.toString(),
                fontSize = 11.sp,
                color = MaterialTheme.colors.secondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WorkingMemoryPanel(
    enabled: Boolean,
    isEnabledInConfig: Boolean,
    workingMemory: Map<WmCategory, Map<String, FactEntry>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val totalFacts = workingMemory.values.sumOf { it.size }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "WM (факты)",
                fontSize = 12.sp,
                color = if (enabled) Color.White else Color.Gray
            )
            Text(
                text = totalFacts.toString(),
                fontSize = 11.sp,
                color = MaterialTheme.colors.secondary,
                fontWeight = FontWeight.Medium
            )
        }

        if (!isEnabledInConfig) {
            Text(text = "Выключено в настройках", fontSize = 11.sp, color = Color.Gray)
            return@Column
        }

        if (workingMemory.isEmpty()) {
            Text(text = "Фактов нет", fontSize = 11.sp, color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                workingMemory.toSortedMap().forEach { (category, group) ->
                    if (group.isEmpty()) return@forEach
                    Text(
                        text = category.name,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    group.toSortedMap().forEach { (key, entry) ->
                        val c = "%.2f".format(entry.confidence)
                        Text(
                            text = "$key: ${entry.value} (c=$c, u=${entry.useCount})",
                            fontSize = 11.sp,
                            color = Color(0xFFB0B0B0)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LongTermMemoryPanel(enabled: Boolean, items: List<MemoryItem>, onLoad: () -> Unit) {
    val expanded = remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier =
                Modifier.fillMaxWidth().clickable(enabled = enabled) {
                    val next = !expanded.value
                    expanded.value = next
                    if (next) onLoad()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded.value) "LTM (показать меньше)" else "LTM (показать)",
                fontSize = 12.sp,
                color = if (enabled) Color.White else Color.Gray
            )
            Text(
                text = items.size.toString(),
                fontSize = 11.sp,
                color = MaterialTheme.colors.secondary,
                fontWeight = FontWeight.Medium
            )
        }

        if (expanded.value) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Обновить",
                    fontSize = 11.sp,
                    color = if (enabled) MaterialTheme.colors.secondary else Color.Gray,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(enabled = enabled) { onLoad() }
                )
            }
        }

        AnimatedVisibility(
            visible = expanded.value,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            if (items.isEmpty()) {
                Text(text = "Долгосрочной памяти нет", fontSize = 11.sp, color = Color.Gray)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.groupBy { it.category }.toSortedMap().forEach { (cat, group) ->
                        Text(
                            text = cat.name,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        group.sortedBy { it.key }.forEach { item ->
                            val c = "%.2f".format(item.entry.confidence)
                            Text(
                                text =
                                    "${item.key}: ${item.entry.value} (c=$c, u=${item.entry.useCount})",
                                fontSize = 11.sp,
                                color = Color(0xFFB0B0B0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyRadio(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    ) {
        RadioButton(
            selected = selected,
            onClick = { if (enabled) onClick() },
            enabled = enabled,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colors.secondary,
                    unselectedColor = Color.Gray
                )
        )
        Text(text = label, fontSize = 11.sp, color = if (enabled) Color.White else Color.Gray)
    }
}

@Composable
private fun BranchingControls(
    branches: List<String>,
    activeBranchId: String,
    checkpointSize: Int,
    maxCheckpointSize: Int,
    onCheckpointSizeChanged: (Int) -> Unit,
    onBranchSelected: (String) -> Unit,
    onCreateBranchFromCheckpoint: () -> Unit,
    enabled: Boolean
) {
    val expanded = remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Ветка", fontSize = 12.sp, color = if (enabled) Color.White else Color.Gray)
            Box {
                Text(
                    text = activeBranchId,
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.secondary,
                    modifier =
                        Modifier.clickable(enabled = enabled) { expanded.value = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                DropdownMenu(
                    expanded = expanded.value,
                    onDismissRequest = { expanded.value = false }
                ) {
                    branches.forEach { id ->
                        DropdownMenuItem(
                            onClick = {
                                expanded.value = false
                                if (enabled) onBranchSelected(id)
                            }
                        ) { Text(text = id) }
                    }
                }
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Чекпоинт (сообщений)", fontSize = 11.sp, color = Color.Gray)
                Text(
                    text = checkpointSize.toString(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
            Slider(
                value = checkpointSize.toFloat(),
                onValueChange = { if (enabled) onCheckpointSizeChanged(it.toInt()) },
                valueRange = 0f..maxCheckpointSize.toFloat(),
                steps = (maxCheckpointSize - 1).coerceAtLeast(0),
                enabled = enabled,
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colors.secondary,
                        activeTrackColor = MaterialTheme.colors.secondary,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = { if (enabled) onCreateBranchFromCheckpoint() },
            enabled = enabled,
            colors =
                ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.secondary
                )
        ) { Text(text = "Создать ветку", fontSize = 12.sp, color = Color.Black) }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, fontSize = 12.sp, color = if (enabled) Color.White else Color.Gray)
            Text(
                text = value.toString(),
                fontSize = 12.sp,
                color = MaterialTheme.colors.secondary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { if (enabled) onValueChange(it) },
            valueRange = valueRange,
            steps = ((valueRange.endInclusive - valueRange.start) / 1f).toInt() - 1,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colors.secondary,
                    activeTrackColor = MaterialTheme.colors.secondary,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ConfigInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
        Text(text = value, fontSize = 11.sp, color = Color(0xFFB0B0B0))
    }
}

@Composable
fun ContextMessagesPanel(
    messages: List<Message>,
    keepLastN: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val estimatedTokens = messages.sumOf { it.content.length / 4 }

    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📋 Контекст LLM",
                    fontSize = 13.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${messages.size} / $keepLastN",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.secondary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector =
                            if (isExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colors.secondary
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column {
                    if (messages.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Сообщений в контексте нет",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier.padding(top = 8.dp)
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            messages.forEachIndexed { index, message ->
                                ContextMessageItem(message = message, index = index + 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Оценка токенов", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = "~$estimatedTokens",
                                fontSize = 10.sp,
                                color = MaterialTheme.colors.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextMessageItem(message: Message, index: Int) {
    val backgroundColor =
        when (message.role) {
            MessageRole.USER -> MaterialTheme.colors.secondary.copy(alpha = 0.15f)
            MessageRole.ASSISTANT -> Color(0xFF2A2A3A)
            else -> Color(0xFF1A1A2A)
        }

    val roleColor =
        when (message.role) {
            MessageRole.USER -> MaterialTheme.colors.secondary
            MessageRole.ASSISTANT -> Color(0xFF81C784)
            else -> Color.Gray
        }

    val roleLabel =
        when (message.role) {
            MessageRole.USER -> "USER"
            MessageRole.ASSISTANT -> "ASSISTANT"
            MessageRole.SYSTEM -> "SYSTEM"
            MessageRole.ERROR -> "ERROR"
        }

    val displayContent =
        if (message.content.length > 100) {
            message.content.take(100) + "..."
        } else {
            message.content
        }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$index.",
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp)
        )
        Card(
            backgroundColor = backgroundColor,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = roleLabel,
                        fontSize = 9.sp,
                        color = roleColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = message.timestamp, fontSize = 9.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = displayContent, fontSize = 11.sp, color = Color(0xFFE0E0E0))
            }
        }
    }
}
