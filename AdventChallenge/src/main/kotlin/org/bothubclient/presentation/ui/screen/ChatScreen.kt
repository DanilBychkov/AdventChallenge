package org.bothubclient.presentation.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.bothubclient.domain.entity.Message
import org.bothubclient.presentation.ui.components.*
import org.bothubclient.presentation.ui.theme.BothubTheme
import org.bothubclient.presentation.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    coroutineScope: CoroutineScope
) {
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var promptDropdownExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val promptScrollState = rememberScrollState()

    LaunchedEffect(viewModel.messages.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    BothubTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp)
        ) {
            Header(
                title = "Bothub Chat Client",
                showReset = viewModel.messages.isNotEmpty(),
                onReset = { viewModel.resetSession() }
            )

            DropdownSelector(
                label = "Model",
                selectedValue = viewModel.selectedModel,
                displayValue = { it },
                items = viewModel.availableModels,
                onSelected = { viewModel.onModelSelected(it) },
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it }
            )

            val canChangePrompt = viewModel.messages.isEmpty() && !viewModel.isLoading

            DropdownSelector(
                label = "System prompt",
                selectedValue = viewModel.selectedPrompt,
                displayValue = { it.name },
                items = viewModel.availablePrompts,
                onSelected = { viewModel.onPromptSelected(it) },
                expanded = promptDropdownExpanded,
                onExpandedChange = { promptDropdownExpanded = it },
                enabled = canChangePrompt
            )

            SystemPromptCard(
                prompt = viewModel.selectedPrompt.text,
                scrollState = promptScrollState
            )

            viewModel.apiKeyError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                ErrorCard(message = error)
            }

            MessagesContainer(
                messages = viewModel.messages,
                scrollState = scrollState,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            InputRow(
                inputText = viewModel.inputText,
                onInputTextChanged = { viewModel.onInputTextChanged(it) },
                isLoading = viewModel.isLoading,
                onSendClick = { viewModel.sendMessage(coroutineScope) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            StatusText(
                message = viewModel.statusMessage,
                isError = viewModel.statusMessage.contains("Ошибка")
            )
        }
    }
}

@Composable
private fun Header(
    title: String,
    showReset: Boolean = false,
    onReset: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
        if (showReset) {
            ResetButton(
                enabled = true,
                onClick = onReset
            )
        }
    }
}

@Composable
private fun SystemPromptCard(
    prompt: String,
    scrollState: ScrollState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .heightIn(max = 140.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Текст системного промта:",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = prompt,
                fontSize = 12.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MessagesContainer(
    messages: List<Message>,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Введите сообщение для начала общения",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    messages.forEach { message ->
                        MessageBubble(message)
                    }
                }
            }
        }
    }
}

@Composable
private fun InputRow(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    isLoading: Boolean,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChatInputField(
            value = inputText,
            onValueChange = onInputTextChanged,
            enabled = !isLoading,
            modifier = Modifier.weight(1f)
        )

        SendButton(
            enabled = inputText.isNotBlank() && !isLoading,
            isLoading = isLoading,
            onClick = onSendClick
        )
    }
}

@Composable
private fun StatusText(
    message: String,
    isError: Boolean
) {
    Text(
        text = message,
        fontSize = 12.sp,
        color = if (isError) Color(0xFFFF6B6B) else Color.Gray
    )
}
