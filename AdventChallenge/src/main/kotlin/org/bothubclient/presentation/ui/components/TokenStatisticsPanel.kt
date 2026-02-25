package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
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
import org.bothubclient.domain.entity.SessionTokenStatistics
import kotlin.math.roundToInt

@Composable
fun TokenStatisticsPanel(
    statistics: SessionTokenStatistics,
    modifier: Modifier = Modifier
) {
    val usagePercent = statistics.contextUsagePercent.coerceIn(0f, 100f)

    val progressColor = when {
        usagePercent > 95f -> Color(0xFFFF4444)
        usagePercent > 80f -> Color(0xFFFFAA00)
        else -> MaterialTheme.colors.secondary
    }

    val warningText = when {
        usagePercent > 95f -> "🚨 Критический уровень контекста!"
        usagePercent > 80f -> "⚠ Приближение к лимиту контекста"
        else -> null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Статистика сессии",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.secondary
                )
                Text(
                    text = "${statistics.messageCount} сообщений",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Контекст:",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = usagePercent / 100f,
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = progressColor,
                    backgroundColor = Color.DarkGray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${formatDecimal(usagePercent.toDouble(), 1)}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = progressColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TokenStatItem(
                    label = "Запрос",
                    value = formatTokenCount(statistics.lastRequestTokens),
                    subValue = "Всего: ${formatTokenCount(statistics.totalPromptTokens)}"
                )
                TokenStatItem(
                    label = "Ответ",
                    value = formatTokenCount(statistics.lastResponseTokens),
                    subValue = "Всего: ${formatTokenCount(statistics.totalCompletionTokens)}"
                )
                TokenStatItem(
                    label = "Токенов",
                    value = formatTokenCount(statistics.totalTokens),
                    subValue = "Осталось: ${formatTokenCount(statistics.remainingTokens)}"
                )
            }

            statistics.estimatedCostRub?.let { cost ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Стоимость сессии:",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = ModelPricing.formatCostRub(cost),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.secondary
                    )
                }
            }

            warningText?.let { warning ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = progressColor
                )
            }
        }
    }
}

@Composable
private fun TokenStatItem(
    label: String,
    value: String,
    subValue: String
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
        Text(
            text = subValue,
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

private fun formatTokenCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${formatDecimal(count / 1_000_000.0, 1)}M"
        count >= 1_000 -> "${formatDecimal(count / 1_000.0, 1)}K"
        else -> count.toString()
    }
}

private fun formatDecimal(value: Double, decimalPlaces: Int): String {
    var multiplier = 1.0
    repeat(decimalPlaces) { multiplier *= 10.0 }
    val rounded = (value * multiplier).roundToInt() / multiplier
    return rounded.toString()
}
