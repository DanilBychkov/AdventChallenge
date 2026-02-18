package org.bothubclient.presentation.ui.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.bothubclient.domain.entity.Message
import org.bothubclient.infrastructure.config.PanelSizePreferences
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

    var promptPanelHeight by remember { mutableStateOf(PanelSizePreferences.promptPanelHeight.dp) }
    var availableHeight by remember { mutableStateOf(0) }

    val minPromptHeight = 120.dp
    val maxPromptHeight = 400.dp
    val minMessagesHeight = 200.dp

    LaunchedEffect(viewModel.messages.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    BothubTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp)
                .onSizeChanged { size ->
                    availableHeight = size.height
                }
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

            if (viewModel.selectedPrompt.isCustom) {
                CustomPromptInputSection(
                    customText = viewModel.customPromptText,
                    onCustomTextChanged = { viewModel.onCustomPromptTextChanged(it) },
                    enabled = canChangePrompt,
                    onOptimizeClick = { viewModel.optimizeCustomPrompt(coroutineScope) },
                    isOptimizing = viewModel.isOptimizingPrompt,
                    optimizedPrompt = viewModel.optimizedPromptText,
                    hasOptimizedPrompt = viewModel.hasOptimizedPrompt,
                    onUseOriginalClick = { viewModel.useOriginalPrompt() },
                    optimizeError = viewModel.optimizePromptError,
                    modifier = Modifier.height(promptPanelHeight)
                )
            } else {
                SystemPromptCard(
                    prompt = viewModel.selectedPrompt.text,
                    scrollState = promptScrollState,
                    modifier = Modifier.height(promptPanelHeight)
                )
            }

            ResizableDivider(
                onDrag = { delta ->
                    val newHeight = promptPanelHeight + delta.dp
                    promptPanelHeight = newHeight.coerceIn(minPromptHeight, maxPromptHeight)
                    PanelSizePreferences.promptPanelHeight = promptPanelHeight.value.toInt()
                }
            )

            viewModel.apiKeyError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                ErrorCard(message = error)
            }

            MessagesContainer(
                messages = viewModel.messages,
                scrollState = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = minMessagesHeight)
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
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
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

@Composable
private fun CustomPromptInputSection(
    customText: String,
    onCustomTextChanged: (String) -> Unit,
    enabled: Boolean,
    onOptimizeClick: () -> Unit,
    isOptimizing: Boolean,
    optimizedPrompt: String?,
    hasOptimizedPrompt: Boolean,
    onUseOriginalClick: () -> Unit,
    optimizeError: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasOptimizedPrompt) "Оптимизированный промпт:" else "Ваш системный промпт:",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasOptimizedPrompt) {
                        TextButton(
                            onClick = onUseOriginalClick,
                            enabled = enabled,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Восстановить оригинал",
                                tint = MaterialTheme.colors.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Оригинал",
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.secondary
                            )
                        }
                    }
                    Button(
                        onClick = onOptimizeClick,
                        enabled = enabled && customText.isNotBlank() && !isOptimizing,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            disabledBackgroundColor = Color.Gray.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isOptimizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Оптимизировать",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOptimizing) "Оптимизация..." else "Оптимизировать через LLM",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (hasOptimizedPrompt) {
                Text(
                    text = optimizedPrompt ?: "",
                    fontSize = 12.sp,
                    color = Color.White
                )
            } else {
                CustomPromptInputField(
                    value = customText,
                    onValueChange = onCustomTextChanged,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            optimizeError?.let { error ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = error,
                    fontSize = 11.sp,
                    color = Color(0xFFFF6B6B)
                )
            }
        }
    }
}
