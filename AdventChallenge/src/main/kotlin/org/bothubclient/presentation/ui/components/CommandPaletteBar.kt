package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CommandItem(
    val label: String,
    val command: String,
    val description: String,
    val example: String
)

@Composable
fun CommandPaletteBar(
    commands: List<CommandItem>,
    enabled: Boolean,
    onCommandSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (commands.isEmpty()) return

    val scrollState = rememberScrollState()

    Row(
        modifier =
            modifier
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        commands.forEach { cmd ->
            CommandChip(
                item = cmd,
                enabled = enabled,
                onSelect = { onCommandSelected(cmd.command) }
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
    }
}

@Composable
private fun CommandChip(
    item: CommandItem,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 0.dp
    ) {
        Row(
            modifier =
                Modifier
                    .clickable(enabled = enabled, onClick = onSelect)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.label,
                fontSize = 11.sp,
                color = if (enabled) Color.White else Color.Gray,
                fontWeight = FontWeight.Medium
            )

            CommandInfoTooltip(
                enabled = enabled,
                title = item.command,
                description = item.description,
                example = item.example
            )
        }
    }
}

@Composable
private fun CommandInfoTooltip(
    enabled: Boolean,
    title: String,
    description: String,
    example: String
) {
    val expanded = remember { mutableStateOf(false) }

    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = { expanded.value = !expanded.value },
            enabled = enabled,
            modifier = Modifier.padding(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Описание команды",
                tint = if (enabled) MaterialTheme.colors.secondary else Color.Gray
            )
        }

        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = Color(0xFFB0B0B0),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)
            )
            Text(
                text = "Пример: $example",
                fontSize = 11.sp,
                color = Color(0xFFB0B0B0),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
            Spacer(modifier = Modifier.width(1.dp))
        }
    }
}
