package org.bothubclient.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colors.surface
        } else {
            MaterialTheme.colors.surface.copy(alpha = 0.6f)
        },
        label = "chatInputBackground"
    )
    val fieldAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.9f,
        label = "chatInputAlpha"
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.alpha(fieldAlpha),
        placeholder = { Text("Введите сообщение...", color = Color.Gray) },
        enabled = enabled,
        singleLine = false,
        maxLines = 3,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.85f),
            backgroundColor = backgroundColor,
            focusedBorderColor = MaterialTheme.colors.primary,
            unfocusedBorderColor = Color.Gray,
            disabledBorderColor = Color.Gray.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun CustomPromptInputField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 80.dp, max = 200.dp),
        placeholder = { Text("Введите ваш системный промпт...", color = Color.Gray) },
        enabled = enabled,
        singleLine = false,
        maxLines = 8,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = Color.White,
            backgroundColor = MaterialTheme.colors.surface,
            focusedBorderColor = MaterialTheme.colors.primary,
            unfocusedBorderColor = Color.Gray
        ),
        shape = RoundedCornerShape(12.dp)
    )
}
