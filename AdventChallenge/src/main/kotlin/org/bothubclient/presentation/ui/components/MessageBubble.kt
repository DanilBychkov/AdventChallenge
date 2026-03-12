package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bothubclient.config.ModelPricing
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.MessageRole

@Composable
fun MessageBubble(message: Message) {
    val isUser = message.role == MessageRole.USER
    val isError = message.role == MessageRole.ERROR
    val isMcpToolCall = message.mcpToolCall != null

    val backgroundColor = when {
        isMcpToolCall && message.mcpToolCall!!.isError -> Color(0xFF8B1A1A)
        isMcpToolCall -> Color(0xFF1A5276)
        isError -> Color(0xFFB71C1C)
        isUser -> MaterialTheme.colors.primary
        else -> MaterialTheme.colors.surface
    }

    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            backgroundColor = backgroundColor,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isUser) 12.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 12.dp
            ),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .widthIn(max = 450.dp)
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (isMcpToolCall) {
                        val info = message.mcpToolCall!!
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "MCP: ${info.serverName}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF85C1E9)
                            )
                            Card(
                                backgroundColor = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = info.toolName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val displayContent = if (message.content.length > 500)
                            message.content.take(500) + "..." else message.content
                        Text(
                            text = displayContent,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        if (info.durationMs != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${info.durationMs} мс",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        Text(
                            text = getRoleLabel(message.role),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) Color.White.copy(alpha = 0.7f) else MaterialTheme.colors.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.content,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        if (!isUser && !isError && message.metrics != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = buildString {
                                    val cost = message.metrics.cost
                                    append("Время: ${message.metrics.responseTimeMs} мс")
                                    if (cost != null) {
                                        append(" • Стоимость: ${ModelPricing.formatCostRub(cost)}")
                                    } else {
                                        append(" • Стоимость: неизвестна")
                                    }
                                },
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.timestamp,
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private fun getRoleLabel(role: MessageRole): String {
    return when (role) {
        MessageRole.USER -> "Вы"
        MessageRole.ERROR -> "Ошибка"
        MessageRole.ASSISTANT -> "Ассистент"
        MessageRole.SYSTEM -> "Система"
    }
}
