package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bothubclient.domain.entity.ContextConfig

@Composable
fun ContextConfigPanel(
    config: ContextConfig,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onKeepLastNChanged: (Int) -> Unit,
    onCompressionBlockSizeChanged: (Int) -> Unit,
    onAutoCompressionToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
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
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colors.secondary
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
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
                            onCheckedChange = {
                                if (enabled) onAutoCompressionToggled(it)
                            },
                            colors = CheckboxDefaults.colors(
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
private fun ConfigSlider(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    enabled: Boolean
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (enabled) Color.White else Color.Gray
            )
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
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colors.secondary,
                activeTrackColor = MaterialTheme.colors.secondary,
                inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ConfigInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = Color(0xFFB0B0B0)
        )
    }
}
