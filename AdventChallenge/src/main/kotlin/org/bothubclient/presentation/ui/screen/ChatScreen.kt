package org.bothubclient.presentation.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.bothubclient.domain.entity.ContextStrategy
import org.bothubclient.domain.entity.Message
import org.bothubclient.infrastructure.config.PanelSizePreferences
import org.bothubclient.presentation.ui.components.*
import org.bothubclient.presentation.ui.theme.BothubTheme
import org.bothubclient.presentation.viewmodel.ChatViewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel, coroutineScope: CoroutineScope) {
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var promptDropdownExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val promptScrollState = rememberScrollState()

    var promptPanelHeight by remember { mutableStateOf(PanelSizePreferences.promptPanelHeight.dp) }

    var isStatsExpanded by remember { mutableStateOf(true) }
    var isPromptExpanded by remember { mutableStateOf(true) }
    var isContextMessagesExpanded by remember { mutableStateOf(true) }

    val minPromptHeight = 120.dp
    val maxPromptHeight = 400.dp

    LaunchedEffect(Unit) { viewModel.loadHistory(this) }

    LaunchedEffect(viewModel.messages.size) { scrollState.scrollTo(scrollState.maxValue) }

    BothubTheme {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .padding(16.dp)
        ) {
            Header(
                title = "Bothub Chat Client",
                showReset = viewModel.messages.isNotEmpty(),
                onReset = { viewModel.resetSession(coroutineScope) }
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

            if (viewModel.messages.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = viewModel.temperatureText,
                    onValueChange = { viewModel.onTemperatureTextChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Температура") },
                    placeholder = { Text("0.7", color = Color.Gray) },
                    enabled = !viewModel.isLoading,
                    singleLine = true,
                    colors =
                        TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            backgroundColor = MaterialTheme.colors.surface,
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                    shape = RoundedCornerShape(12.dp),
                    isError = viewModel.temperatureError != null
                )
                viewModel.temperatureError?.let { error ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = error, fontSize = 12.sp, color = Color(0xFFFF6B6B))
                }
            }

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
                CollapsiblePromptCard(
                    title =
                        if (viewModel.hasOptimizedPrompt) "Оптимизированный промпт"
                        else "Ваш системный промпт",
                    isExpanded = isPromptExpanded,
                    onToggle = { isPromptExpanded = !isPromptExpanded },
                    modifier = Modifier.height(promptPanelHeight)
                ) {
                    CustomPromptContent(
                        customText = viewModel.customPromptText,
                        onCustomTextChanged = { viewModel.onCustomPromptTextChanged(it) },
                        enabled = canChangePrompt,
                        onOptimizeClick = { viewModel.optimizeCustomPrompt(coroutineScope) },
                        isOptimizing = viewModel.isOptimizingPrompt,
                        scrollState = promptScrollState,
                        optimizedPrompt = viewModel.optimizedPromptText,
                        hasOptimizedPrompt = viewModel.hasOptimizedPrompt,
                        onUseOriginalClick = { viewModel.useOriginalPrompt() },
                        optimizeError = viewModel.optimizePromptError
                    )
                }
            } else {
                CollapsiblePromptCard(
                    title = "Текст системного промта",
                    isExpanded = isPromptExpanded,
                    onToggle = { isPromptExpanded = !isPromptExpanded },
                    modifier = Modifier.height(promptPanelHeight)
                ) {
                    Column(modifier = Modifier.verticalScroll(promptScrollState)) {
                        Text(
                            text = viewModel.selectedPrompt.text,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
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

            if (viewModel.messages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TokenStatisticsPanel(
                    statistics = viewModel.tokenStatistics,
                    isExpanded = isStatsExpanded,
                    onToggle = { isStatsExpanded = !isStatsExpanded }
                )

                if (viewModel.summaryBlocks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryPanel(summaryBlocks = viewModel.summaryBlocks)
                }

                Spacer(modifier = Modifier.height(8.dp))
                ContextConfigPanel(
                    config = viewModel.contextConfig,
                    isExpanded = viewModel.isContextConfigExpanded,
                    onToggle = { viewModel.toggleContextConfigExpanded() },
                    onStrategyChanged = { viewModel.onStrategySelected(it) },
                    facts = viewModel.facts,
                    branches = viewModel.branches,
                    activeBranchId = viewModel.activeBranchId,
                    checkpointSize = viewModel.branchCheckpointSize,
                    maxCheckpointSize = viewModel.messages.size,
                    onCheckpointSizeChanged = { viewModel.onBranchCheckpointSizeChanged(it) },
                    onBranchSelected = { branchId ->
                        viewModel.onBranchSelected(coroutineScope, branchId)
                    },
                    onCreateBranchFromCheckpoint = {
                        viewModel.createBranchFromCheckpoint(coroutineScope)
                    },
                    onKeepLastNChanged = { viewModel.onKeepLastNChanged(it) },
                    onCompressionBlockSizeChanged = {
                        viewModel.onCompressionBlockSizeChanged(it)
                    },
                    onAutoCompressionToggled = { viewModel.onAutoCompressionToggled(it) },
                    enabled = !viewModel.isLoading
                )

                if (viewModel.contextConfig.strategy == ContextStrategy.SLIDING_WINDOW) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ContextMessagesPanel(
                        messages = viewModel.contextMessages,
                        keepLastN = viewModel.contextConfig.keepLastN,
                        isExpanded = isContextMessagesExpanded,
                        onToggle = { isContextMessagesExpanded = !isContextMessagesExpanded }
                    )
                }
            }

            MessagesContainer(
                messages = viewModel.messages,
                scrollState = scrollState,
                modifier = Modifier.fillMaxWidth().weight(1f)
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
private fun CollapsiblePromptCard(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                    tint = MaterialTheme.colors.secondary
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun CustomPromptContent(
    customText: String,
    onCustomTextChanged: (String) -> Unit,
    enabled: Boolean,
    onOptimizeClick: () -> Unit,
    isOptimizing: Boolean,
    scrollState: ScrollState,
    optimizedPrompt: String?,
    hasOptimizedPrompt: Boolean,
    onUseOriginalClick: () -> Unit,
    optimizeError: String?
) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        colors =
                            ButtonDefaults.buttonColors(
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
                            text =
                                if (isOptimizing) "Оптимизация..."
                                else "Оптимизировать через LLM",
                            fontSize = 11.sp,
                            color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (hasOptimizedPrompt) {
            Text(text = optimizedPrompt ?: "", fontSize = 12.sp, color = Color.White)
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
            Text(text = error, fontSize = 11.sp, color = Color(0xFFFF6B6B))
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
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { messages.forEach { message -> MessageBubble(message) } }
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
            enabled = true,
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
private fun Header(title: String, showReset: Boolean = false, onReset: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
            ResetButton(enabled = true, onClick = onReset)
        }
    }
}

@Composable
private fun StatusText(message: String, isError: Boolean) {
    Text(text = message, fontSize = 12.sp, color = if (isError) Color(0xFFFF6B6B) else Color.Gray)
}
