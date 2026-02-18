package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.MessageRole
import org.bothubclient.presentation.ui.theme.AppColors
import org.bothubclient.presentation.viewmodel.ChatPanelViewModel

@Composable
fun ChatPanel(
    viewModel: ChatPanelViewModel,
    coroutineScope: CoroutineScope,
    panelTitle: String,
    modifier: Modifier = Modifier
) {
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var promptDropdownExpanded by remember { mutableStateOf(false) }
    var promptSectionHeight by remember { mutableStateOf(80.dp) }

    val minPromptHeight = 60.dp
    val maxPromptHeight = 200.dp

    Card(
        modifier = modifier,
        backgroundColor = AppColors.Surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            PanelHeader(
                title = panelTitle,
                status = viewModel.statusMessage,
                showReset = viewModel.messages.isNotEmpty(),
                onReset = { viewModel.resetSession() }
            )

            Spacer(modifier = Modifier.height(4.dp))

            CompactDropdownSelector(
                label = "Model",
                selectedValue = viewModel.selectedModel,
                displayValue = { it },
                items = viewModel.availableModels,
                onSelected = { viewModel.onModelSelected(it) },
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it }
            )

            val canChangePrompt = viewModel.messages.isEmpty() && !viewModel.isLoading

            CompactDropdownSelector(
                label = "Prompt",
                selectedValue = viewModel.selectedPrompt,
                displayValue = { it.name },
                items = viewModel.availablePrompts,
                onSelected = { viewModel.onPromptSelected(it) },
                expanded = promptDropdownExpanded,
                onExpandedChange = { promptDropdownExpanded = it },
                enabled = canChangePrompt
            )

            Box(modifier = Modifier.height(promptSectionHeight)) {
                if (viewModel.selectedPrompt.isCustom) {
                    CompactCustomPromptSection(
                        customText = viewModel.customPromptText,
                        onCustomTextChanged = { viewModel.onCustomPromptTextChanged(it) },
                        enabled = canChangePrompt,
                        onOptimizeClick = { viewModel.optimizeCustomPrompt(coroutineScope) },
                        isOptimizing = viewModel.isOptimizingPrompt,
                        optimizedPrompt = viewModel.optimizedPromptText,
                        hasOptimizedPrompt = viewModel.hasOptimizedPrompt,
                        onUseOriginalClick = { viewModel.useOriginalPrompt() }
                    )
                } else {
                    CompactPromptDisplay(
                        prompt = viewModel.selectedPrompt.text
                    )
                }
            }

            HorizontalPanelResizer(
                onDrag = { delta ->
                    promptSectionHeight = (promptSectionHeight + delta.dp).coerceIn(minPromptHeight, maxPromptHeight)
                }
            )

            CompactMessagesContainer(
                messages = viewModel.messages,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            CompactInputRow(
                inputText = viewModel.inputText,
                onInputTextChanged = { viewModel.onInputTextChanged(it) },
                isLoading = viewModel.isLoading,
                onSendClick = { viewModel.sendMessage(coroutineScope) }
            )
        }
    }
}

@Composable
private fun HorizontalPanelResizer(
    onDrag: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(2.dp)
                .background(AppColors.ResizerColor, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun PanelHeader(
    title: String,
    status: String,
    showReset: Boolean,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Primary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status,
                fontSize = 10.sp,
                color = Color.Gray
            )
            if (showReset) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Сброс",
                        tint = AppColors.Secondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactDropdownSelector(
    label: String,
    selectedValue: String,
    displayValue: (String) -> String,
    items: List<String>,
    onSelected: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onExpandedChange(true) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                fontSize = 10.sp,
                color = Color.Gray
            )
            Text(
                text = displayValue(selectedValue),
                fontSize = 10.sp,
                color = if (enabled) AppColors.Secondary else Color.Gray,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Выбрать",
                tint = if (enabled) AppColors.Secondary else Color.Gray,
                modifier = Modifier.size(12.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(AppColors.Surface)
        ) {
            items.forEach { item ->
                DropdownMenuItem(onClick = {
                    onSelected(item)
                    onExpandedChange(false)
                }) {
                    Text(
                        text = item,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> CompactDropdownSelector(
    label: String,
    selectedValue: T,
    displayValue: (T) -> String,
    items: List<T>,
    onSelected: (T) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onExpandedChange(true) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                fontSize = 10.sp,
                color = Color.Gray
            )
            Text(
                text = displayValue(selectedValue),
                fontSize = 10.sp,
                color = if (enabled) AppColors.Secondary else Color.Gray,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Выбрать",
                tint = if (enabled) AppColors.Secondary else Color.Gray,
                modifier = Modifier.size(12.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier
                .background(AppColors.Surface)
                .width(200.dp)
        ) {
            items.forEach { item ->
                DropdownMenuItem(onClick = {
                    onSelected(item)
                    onExpandedChange(false)
                }) {
                    Text(
                        text = displayValue(item),
                        fontSize = 11.sp,
                        color = if (displayValue(item) == displayValue(selectedValue))
                            AppColors.Secondary
                        else
                            Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPromptDisplay(
    prompt: String
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(AppColors.Surface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .verticalScroll(scrollState)
            .padding(4.dp)
    ) {
        Text(
            text = prompt,
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun CompactCustomPromptSection(
    customText: String,
    onCustomTextChanged: (String) -> Unit,
    enabled: Boolean,
    onOptimizeClick: () -> Unit,
    isOptimizing: Boolean,
    optimizedPrompt: String?,
    hasOptimizedPrompt: Boolean,
    onUseOriginalClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasOptimizedPrompt) "Оптимизирован:" else "Свой промпт:",
                fontSize = 9.sp,
                color = Color.Gray
            )
            Row {
                if (hasOptimizedPrompt) {
                    TextButton(
                        onClick = onUseOriginalClick,
                        enabled = enabled,
                        contentPadding = PaddingValues(4.dp),
                        modifier = Modifier.height(20.dp)
                    ) {
                        Text(
                            text = "Оригинал",
                            fontSize = 9.sp,
                            color = AppColors.Secondary
                        )
                    }
                }
                Button(
                    onClick = onOptimizeClick,
                    enabled = enabled && customText.isNotBlank() && !isOptimizing,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AppColors.Primary,
                        disabledBackgroundColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    if (isOptimizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOptimizing) "..." else "LLM",
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }
            }
        }

        if (hasOptimizedPrompt) {
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(AppColors.Surface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .verticalScroll(scrollState)
                    .padding(4.dp)
            ) {
                Text(
                    text = optimizedPrompt ?: "",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        } else {
            OutlinedTextField(
                value = customText,
                onValueChange = onCustomTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                placeholder = { Text("Введите промпт...", fontSize = 9.sp, color = Color.Gray) },
                enabled = enabled,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = Color.White),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    backgroundColor = AppColors.Surface.copy(alpha = 0.5f),
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(4.dp)
            )
        }
    }
}

@Composable
private fun CompactMessagesContainer(
    messages: List<Message>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = AppColors.Surface.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp)
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Пусто",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                messages.forEach { message ->
                    CompactMessageBubble(message)
                }
            }
        }
    }
}

@Composable
private fun CompactMessageBubble(message: Message) {
    val isUser = message.role == MessageRole.USER
    val isError = message.role == MessageRole.ERROR

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier.align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart),
            backgroundColor = when {
                isError -> AppColors.ErrorCard
                isUser -> AppColors.Primary
                else -> AppColors.Surface
            },
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = message.content,
                fontSize = 9.sp,
                color = Color.White,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@Composable
private fun CompactInputRow(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    isLoading: Boolean,
    onSendClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Сообщение...", fontSize = 9.sp, color = Color.Gray) },
            enabled = !isLoading,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = Color.White),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = AppColors.Surface.copy(alpha = 0.5f),
                focusedBorderColor = AppColors.Primary,
                unfocusedBorderColor = Color.Gray
            ),
            shape = RoundedCornerShape(4.dp)
        )

        Button(
            onClick = onSendClick,
            enabled = inputText.isNotBlank() && !isLoading,
            modifier = Modifier.size(28.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = AppColors.Primary,
                disabledBackgroundColor = Color.Gray.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Send,
                    contentDescription = "Отправить",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
