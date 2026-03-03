package org.bothubclient.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import org.bothubclient.domain.entity.FactEntry
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.MessageRole
import org.bothubclient.domain.entity.WmCategory
import org.bothubclient.domain.memory.MemoryItem

@Composable
fun AgentMemoryPanel(
    stmMessages: List<Message>,
    workingMemory: Map<WmCategory, Map<String, FactEntry>>,
    longTermMemory: List<MemoryItem>,
    onRefreshLongTermMemory: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isExpanded = remember { mutableStateOf(false) }
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
                    text = "Память агента",
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
                    modifier =
                        Modifier.padding(top = 12.dp)
                            .heightIn(max = 320.dp)
                            .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MemorySectionHeader(
                        title = "STM (краткосрочная)",
                        value = stmMessages.size.toString(),
                        enabled = enabled
                    )
                    StmMessagesList(messages = stmMessages)

                    MemorySectionHeader(
                        title = "WM (рабочая)",
                        value = workingMemory.values.sumOf { it.size }.toString(),
                        enabled = enabled
                    )
                    WorkingMemoryList(workingMemory = workingMemory)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemorySectionHeader(
                            title = "LTM (долгосрочная)",
                            value = longTermMemory.size.toString(),
                            enabled = enabled
                        )
                        Text(
                            text = "Обновить",
                            fontSize = 11.sp,
                            color = if (enabled) MaterialTheme.colors.secondary else Color.Gray,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable(enabled = enabled) { onRefreshLongTermMemory() }
                        )
                    }
                    LongTermMemoryList(items = longTermMemory)
                }
            }
        }
    }
}

@Composable
private fun MemorySectionHeader(title: String, value: String, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = if (enabled) Color.White else Color.Gray
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = MaterialTheme.colors.secondary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StmMessagesList(messages: List<Message>) {
    if (messages.isEmpty()) {
        Text(text = "Пусто", fontSize = 11.sp, color = Color.Gray)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        messages.takeLast(30).forEachIndexed { index, message ->
            val roleLabel =
                when (message.role) {
                    MessageRole.USER -> "USER"
                    MessageRole.ASSISTANT -> "ASSISTANT"
                    MessageRole.SYSTEM -> "SYSTEM"
                    MessageRole.ERROR -> "ERROR"
                }
            val roleColor =
                when (message.role) {
                    MessageRole.USER -> MaterialTheme.colors.secondary
                    MessageRole.ASSISTANT -> Color(0xFF81C784)
                    MessageRole.SYSTEM -> Color(0xFF64B5F6)
                    MessageRole.ERROR -> Color(0xFFE57373)
                }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${index + 1}.", fontSize = 9.sp, color = Color.Gray)
                    Text(text = roleLabel, fontSize = 9.sp, color = roleColor, fontWeight = FontWeight.Bold)
                    Text(text = message.timestamp, fontSize = 9.sp, color = Color.Gray)
                }
                val content =
                    if (message.content.length > 140) message.content.take(140) + "..." else message.content
                Text(text = content, fontSize = 11.sp, color = Color(0xFFB0B0B0))
            }
        }
    }
}

@Composable
private fun WorkingMemoryList(workingMemory: Map<WmCategory, Map<String, FactEntry>>) {
    if (workingMemory.isEmpty()) {
        Text(text = "Пусто", fontSize = 11.sp, color = Color.Gray)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        workingMemory.toSortedMap().forEach { (category, group) ->
            if (group.isEmpty()) return@forEach
            Text(
                text = category.name,
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable
private fun LongTermMemoryList(items: List<MemoryItem>) {
    if (items.isEmpty()) {
        Text(text = "Пусто", fontSize = 11.sp, color = Color.Gray)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.groupBy { it.category }.toSortedMap().forEach { (category, group) ->
            Text(
                text = category.name,
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.sortedBy { it.key }.forEach { item ->
                    val c = "%.2f".format(item.entry.confidence)
                    Text(
                        text = "${item.key}: ${item.entry.value} (c=$c, u=${item.entry.useCount})",
                        fontSize = 11.sp,
                        color = Color(0xFFB0B0B0)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

