package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun <T> DropdownSelector(
    label: String,
    selectedValue: T,
    displayValue: (T) -> String,
    items: List<T>,
    onSelected: (T) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable(enabled = enabled) { onExpandedChange(true) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = displayValue(selectedValue),
                fontSize = 12.sp,
                color = if (enabled) MaterialTheme.colors.secondary else Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Выбрать",
                tint = if (enabled) MaterialTheme.colors.secondary else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .background(MaterialTheme.colors.surface)
                .width(260.dp)
        ) {
            items.forEach { item ->
                val isSelected = displayValue(item) == displayValue(selectedValue)
                DropdownMenuItem(
                    onClick = {
                        onSelected(item)
                        onExpandedChange(false)
                    },
                    modifier = Modifier.background(
                        if (isSelected)
                            MaterialTheme.colors.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colors.surface
                    )
                ) {
                    Text(
                        text = displayValue(item),
                        fontSize = 13.sp,
                        color = if (isSelected)
                            MaterialTheme.colors.secondary
                        else
                            Color.White
                    )
                }
            }
        }
    }
}
