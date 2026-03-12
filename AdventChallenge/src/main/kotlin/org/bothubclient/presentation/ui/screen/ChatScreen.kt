package org.bothubclient.presentation.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import org.bothubclient.domain.entity.ContextStrategy
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.UserProfileDefaults
import org.bothubclient.presentation.config.PanelSizePreferences
import org.bothubclient.presentation.ui.components.*
import org.bothubclient.presentation.ui.theme.BothubTheme
import org.bothubclient.presentation.viewmodel.ChatViewModel
import org.bothubclient.presentation.viewmodel.McpSettingsViewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel, coroutineScope: CoroutineScope) {
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var promptDropdownExpanded by remember { mutableStateOf(false) }
    var profileDropdownExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val promptScrollState = rememberScrollState()
    var isProfileEditorOpen by rememberSaveable { mutableStateOf(false) }
    var isMcpSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isResultsOpen by rememberSaveable { mutableStateOf(false) }
    val mcpSettingsViewModel = remember { McpSettingsViewModel.create() }

    var promptPanelHeight by remember {
        mutableStateOf(PanelSizePreferences.promptPanelHeight.dp)
    }

    var isStatsExpanded by remember { mutableStateOf(true) }
    var isPromptExpanded by remember { mutableStateOf(true) }
    var isContextMessagesExpanded by remember { mutableStateOf(true) }

    var isSidePanelVisible by rememberSaveable { mutableStateOf(true) }

    val minPromptHeight = 120.dp
    val maxPromptHeight = 400.dp

    LaunchedEffect(Unit) {
        viewModel.loadHistory(this)
        viewModel.loadLongTermMemory(this)
        viewModel.loadUserProfile(this)
        viewModel.collectToolCallEvents(this)
    }

    LaunchedEffect(viewModel.messages.size) { scrollState.scrollTo(scrollState.maxValue) }

    BothubTheme {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .padding(16.dp)
        ) {
            if (isProfileEditorOpen) {
                ProfileEditorDialog(
                    profile = viewModel.userProfile
                        ?: UserProfileDefaults.DEFAULT_PROFILE,
                    isSaving = viewModel.isSavingUserProfile,
                    error = viewModel.userProfileError,
                    onSave = {
                        viewModel.saveUserProfile(coroutineScope, it)
                        isProfileEditorOpen = false
                    },
                    onClose = { isProfileEditorOpen = false }
                )
            }
            if (isMcpSettingsOpen) {
                McpSettingsDialog(
                    viewModel = mcpSettingsViewModel,
                    coroutineScope = coroutineScope,
                    onClose = { isMcpSettingsOpen = false }
                )
            }
            if (isResultsOpen) {
                ResultsDialog(
                    jobs = viewModel.backgroundJobs,
                    reports = viewModel.boredReports,
                    onToggleJob = { jobId, enabled -> viewModel.toggleJob(coroutineScope, jobId, enabled) },
                    onRunJobNow = { jobId -> viewModel.runJobNow(coroutineScope, jobId) },
                    onUpdateInterval = { jobId, interval ->
                        viewModel.updateJobInterval(
                            coroutineScope,
                            jobId,
                            interval
                        )
                    },
                    onClose = { isResultsOpen = false }
                )
            }
            Header(
                title = "Bothub Chat Client",
                showReset = viewModel.messages.isNotEmpty(),
                onReset = { viewModel.resetSession(coroutineScope) },
                isSidePanelVisible = isSidePanelVisible,
                onToggleSidePanel = { isSidePanelVisible = !isSidePanelVisible },
                profileItems = viewModel.userProfileDropdownItems,
                selectedProfileItem = viewModel.selectedUserProfileDropdownItem,
                profileDropdownExpanded = profileDropdownExpanded,
                onProfileDropdownExpandedChange = { profileDropdownExpanded = it },
                onProfileSelected = { item ->
                    viewModel.onUserProfileSelected(coroutineScope, item.id)
                },
                onOpenProfile = { isProfileEditorOpen = true },
                onOpenMcpSettings = { isMcpSettingsOpen = true },
                onOpenResults = {
                    viewModel.loadBackgroundJobs(coroutineScope)
                    viewModel.loadBoredReports(coroutineScope)
                    isResultsOpen = true
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                ChatMainPane(
                    viewModel = viewModel,
                    coroutineScope = coroutineScope,
                    scrollState = scrollState,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                AnimatedVisibility(
                    visible = isSidePanelVisible,
                    enter =
                        expandHorizontally(expandFrom = Alignment.End) +
                                fadeIn(),
                    exit =
                        shrinkHorizontally(shrinkTowards = Alignment.End) +
                                fadeOut()
                ) {
                    Row(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Divider(
                            modifier =
                                Modifier.fillMaxHeight()
                                    .width(1.dp),
                            color =
                                MaterialTheme.colors.onSurface.copy(
                                    alpha = 0.12f
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Surface(
                            modifier =
                                Modifier.width(360.dp)
                                    .fillMaxHeight(),
                            color = MaterialTheme.colors.surface,
                            shape = RoundedCornerShape(12.dp),
                            elevation = 2.dp
                        ) {
                            ChatSidePanel(
                                viewModel = viewModel,
                                coroutineScope = coroutineScope,
                                modelDropdownExpanded =
                                    modelDropdownExpanded,
                                onModelDropdownExpandedChange = {
                                    modelDropdownExpanded = it
                                },
                                promptDropdownExpanded =
                                    promptDropdownExpanded,
                                onPromptDropdownExpandedChange = {
                                    promptDropdownExpanded = it
                                },
                                promptScrollState =
                                    promptScrollState,
                                promptPanelHeight =
                                    promptPanelHeight,
                                onPromptPanelDrag = { delta ->
                                    val newHeight =
                                        promptPanelHeight +
                                                delta.dp
                                    promptPanelHeight =
                                        newHeight.coerceIn(
                                            minPromptHeight,
                                            maxPromptHeight
                                        )
                                    PanelSizePreferences
                                        .promptPanelHeight =
                                        promptPanelHeight
                                            .value
                                            .toInt()
                                },
                                isPromptExpanded = isPromptExpanded,
                                onPromptToggle = {
                                    isPromptExpanded =
                                        !isPromptExpanded
                                },
                                isStatsExpanded = isStatsExpanded,
                                onStatsToggle = {
                                    isStatsExpanded =
                                        !isStatsExpanded
                                },
                                isContextMessagesExpanded =
                                    isContextMessagesExpanded,
                                onContextMessagesToggle = {
                                    isContextMessagesExpanded =
                                        !isContextMessagesExpanded
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ChatSidePanelTab(val title: String) {
    Settings("Settings"),
    State("State")
}

@Composable
private fun ChatMainPane(
    viewModel: ChatViewModel,
    coroutineScope: CoroutineScope,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        MessagesContainer(
            messages = viewModel.messages,
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        org.bothubclient.presentation.ui.components.CommandPaletteBar(
            commands = remember { defaultCommands() },
            enabled = !viewModel.isLoading,
            onCommandSelected = { viewModel.onInputTextChanged(it) },
            modifier = Modifier.fillMaxWidth()
        )

        InputRow(
            inputText = viewModel.inputText,
            onInputTextChanged = { viewModel.onInputTextChanged(it) },
            isLoading = viewModel.isLoading,
            onSendClick = { viewModel.sendMessage(coroutineScope) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (viewModel.mcpErrorMessage != null) {
            Text(
                text = "MCP: ${viewModel.mcpErrorMessage}",
                fontSize = 12.sp,
                color = MaterialTheme.colors.error,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
        }

        StatusText(
            message = viewModel.statusMessage,
            isError = viewModel.statusMessage.contains("Ошибка")
        )
    }
}

private fun defaultCommands(): List<org.bothubclient.presentation.ui.components.CommandItem> {
    return listOf(
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!wm set",
            command = "!wm set USER_INFO key=value 1.0",
            description =
                "Записать факт в рабочую память (WM). CATEGORY: USER_INFO/TASK/CONTEXT/PROGRESS. confidence опционален.",
            example = "!wm set CONTEXT stack=Compose Desktop, Ktor 0.95"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!wm get",
            command = "!wm get USER_INFO key",
            description = "Получить значение из WM по категории и ключу.",
            example = "!wm get CONTEXT stack"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!wm delete",
            command = "!wm delete USER_INFO key",
            description = "Удалить запись из WM по категории и ключу.",
            example = "!wm delete PROGRESS old_blocker"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!wm list",
            command = "!wm list",
            description = "Показать количество записей WM и открыть панель памяти.",
            example = "!wm list"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!wm list <CAT>",
            command = "!wm list TASK",
            description = "Открыть панель памяти на WM выбранной категории.",
            example = "!wm list USER_INFO"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!ltm save",
            command = "!ltm save key=value CONTEXT",
            description =
                "Сохранить запись в LTM. CATEGORY опциональна (по умолчанию CONTEXT).",
            example = "!ltm save coding_style=Prefer sealed classes USER_INFO"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!ltm find",
            command = "!ltm find query",
            description = "Поиск по LTM по ключам/значениям (топ результатов).",
            example = "!ltm find architecture"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!ltm delete",
            command = "!ltm delete key",
            description = "Удалить из LTM записи с заданным ключом.",
            example = "!ltm delete coding_style"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!stm clear",
            command = "!stm clear",
            description = "Очистить краткосрочную память (STM) текущей сессии.",
            example = "!stm clear"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!stm last",
            command = "!stm last 10",
            description = "Показать последние N сообщений из STM.",
            example = "!stm last 30"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!memory panel show",
            command = "!memory panel show",
            description = "Показать Memory Panel в правой панели.",
            example = "!memory panel show"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!memory panel hide",
            command = "!memory panel hide",
            description = "Скрыть Memory Panel в правой панели.",
            example = "!memory panel hide"
        ),
        org.bothubclient.presentation.ui.components.CommandItem(
            label = "!memory panel toggle",
            command = "!memory panel toggle",
            description = "Переключить видимость Memory Panel.",
            example = "!memory panel toggle"
        )
    )
}

@Composable
private fun ChatSidePanel(
    viewModel: ChatViewModel,
    coroutineScope: CoroutineScope,
    modelDropdownExpanded: Boolean,
    onModelDropdownExpandedChange: (Boolean) -> Unit,
    promptDropdownExpanded: Boolean,
    onPromptDropdownExpandedChange: (Boolean) -> Unit,
    promptScrollState: ScrollState,
    promptPanelHeight: Dp,
    onPromptPanelDrag: (Float) -> Unit,
    isPromptExpanded: Boolean,
    onPromptToggle: () -> Unit,
    isStatsExpanded: Boolean,
    onStatsToggle: () -> Unit,
    isContextMessagesExpanded: Boolean,
    onContextMessagesToggle: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(ChatSidePanelTab.Settings) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            backgroundColor = MaterialTheme.colors.surface
        ) {
            Tab(
                selected = selectedTab == ChatSidePanelTab.Settings,
                onClick = { selectedTab = ChatSidePanelTab.Settings },
                text = { Text(ChatSidePanelTab.Settings.title) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            )
            Tab(
                selected = selectedTab == ChatSidePanelTab.State,
                onClick = { selectedTab = ChatSidePanelTab.State },
                text = { Text(ChatSidePanelTab.State.title) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "State"
                    )
                }
            )
        }

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f))

        when (selectedTab) {
            ChatSidePanelTab.Settings ->
                ChatSidePanelSettings(
                    viewModel = viewModel,
                    coroutineScope = coroutineScope,
                    modelDropdownExpanded = modelDropdownExpanded,
                    onModelDropdownExpandedChange =
                        onModelDropdownExpandedChange,
                    promptDropdownExpanded = promptDropdownExpanded,
                    onPromptDropdownExpandedChange =
                        onPromptDropdownExpandedChange,
                    promptScrollState = promptScrollState,
                    promptPanelHeight = promptPanelHeight,
                    onPromptPanelDrag = onPromptPanelDrag,
                    isPromptExpanded = isPromptExpanded,
                    onPromptToggle = onPromptToggle
                )

            ChatSidePanelTab.State ->
                ChatSidePanelState(
                    viewModel = viewModel,
                    coroutineScope = coroutineScope,
                    isStatsExpanded = isStatsExpanded,
                    onStatsToggle = onStatsToggle,
                    isContextMessagesExpanded = isContextMessagesExpanded,
                    onContextMessagesToggle = onContextMessagesToggle
                )
        }
    }
}

@Composable
private fun ChatSidePanelSettings(
    viewModel: ChatViewModel,
    coroutineScope: CoroutineScope,
    modelDropdownExpanded: Boolean,
    onModelDropdownExpandedChange: (Boolean) -> Unit,
    promptDropdownExpanded: Boolean,
    onPromptDropdownExpandedChange: (Boolean) -> Unit,
    promptScrollState: ScrollState,
    promptPanelHeight: Dp,
    onPromptPanelDrag: (Float) -> Unit,
    isPromptExpanded: Boolean,
    onPromptToggle: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DropdownSelector(
            label = "Model",
            selectedValue = viewModel.selectedModel,
            displayValue = { it },
            items = viewModel.availableModels,
            onSelected = { viewModel.onModelSelected(it) },
            expanded = modelDropdownExpanded,
            onExpandedChange = onModelDropdownExpandedChange
        )

        if (viewModel.messages.isEmpty()) {
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
            onExpandedChange = onPromptDropdownExpandedChange,
            enabled = canChangePrompt
        )

        if (viewModel.selectedPrompt.isCustom) {
            CollapsiblePromptCard(
                title =
                    if (viewModel.hasOptimizedPrompt) "Оптимизированный промпт"
                    else "Ваш системный промпт",
                isExpanded = isPromptExpanded,
                onToggle = onPromptToggle,
                modifier = Modifier.height(promptPanelHeight)
            ) {
                CustomPromptContent(
                    customText = viewModel.customPromptText,
                    onCustomTextChanged = {
                        viewModel.onCustomPromptTextChanged(it)
                    },
                    enabled = canChangePrompt,
                    onOptimizeClick = {
                        viewModel.optimizeCustomPrompt(coroutineScope)
                    },
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
                onToggle = onPromptToggle,
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

        ResizableDivider(onDrag = onPromptPanelDrag)

        viewModel.apiKeyError?.let { error -> ErrorCard(message = error) }
    }
}

@Composable
private fun ChatSidePanelState(
    viewModel: ChatViewModel,
    coroutineScope: CoroutineScope,
    isStatsExpanded: Boolean,
    onStatsToggle: () -> Unit,
    isContextMessagesExpanded: Boolean,
    onContextMessagesToggle: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (viewModel.messages.isEmpty()) {
            Text(
                text = "Состояние и метрики появятся после начала диалога",
                fontSize = 12.sp,
                color = Color.Gray
            )
            return
        }

                TokenStatisticsPanel(
                    statistics = viewModel.tokenStatistics,
                    isExpanded = isStatsExpanded,
                    onToggle = onStatsToggle
                )

        TaskStatePanel(
            taskContext = viewModel.taskContext,
            stateMachineTemplate = viewModel.contextConfig.stateMachineTemplate,
            enabled = !viewModel.isLoading,
            onApprovePlan = { viewModel.approveTaskPlan(coroutineScope) },
            onApproveValidation = { viewModel.approveTaskValidation(coroutineScope) },
            onReset = { viewModel.resetTask(coroutineScope) }
        )

                if (viewModel.summaryBlocks.isNotEmpty()) {
                    SummaryPanel(summaryBlocks = viewModel.summaryBlocks)
                }

        if (viewModel.isMemoryPanelVisible) {
            AgentMemoryPanel(
                stmMessages = viewModel.shortTermMessages,
                workingMemory = viewModel.workingMemory,
                longTermMemory = viewModel.longTermMemory,
                metrics = viewModel.agentMetrics,
                onRefreshLongTermMemory = {
                    viewModel.loadLongTermMemory(coroutineScope)
                },
                enabled = !viewModel.isLoading
            )
        }

        LLMStateMachinePanel(
            config = viewModel.contextConfig,
            onTaskStateMachineToggled = { viewModel.onTaskStateMachineToggled(it) },
            onStateMachineTemplateSelected = { viewModel.onStateMachineTemplateSelected(it) },
            enabled = !viewModel.isLoading
        )

                ContextConfigPanel(
                    config = viewModel.contextConfig,
                    isExpanded = viewModel.isContextConfigExpanded,
                    onToggle = { viewModel.toggleContextConfigExpanded() },
                    onStrategyChanged = { viewModel.onStrategySelected(it) },
                    stmCount = viewModel.stmCount,
                    workingMemory = viewModel.workingMemory,
                    longTermMemory = viewModel.longTermMemory,
                    onLoadLongTermMemory = { viewModel.loadLongTermMemory(coroutineScope) },
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
                    ContextMessagesPanel(
                        messages = viewModel.contextMessages,
                        keepLastN = viewModel.contextConfig.keepLastN,
                        isExpanded = isContextMessagesExpanded,
                        onToggle = onContextMessagesToggle
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
                    contentDescription =
                        if (isExpanded) "Свернуть" else "Развернуть",
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
                        contentPadding =
                            PaddingValues(
                                horizontal = 8.dp,
                                vertical = 4.dp
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription =
                                "Восстановить оригинал",
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
                    enabled =
                        enabled && customText.isNotBlank() && !isOptimizing,
                    contentPadding =
                        PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            backgroundColor =
                                MaterialTheme.colors.primary,
                            disabledBackgroundColor =
                                Color.Gray.copy(alpha = 0.3f)
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
private fun Header(
    title: String,
    showReset: Boolean = false,
    onReset: () -> Unit = {},
    isSidePanelVisible: Boolean,
    onToggleSidePanel: () -> Unit,
    profileItems: List<org.bothubclient.presentation.viewmodel.ProfileDropdownItem>,
    selectedProfileItem: org.bothubclient.presentation.viewmodel.ProfileDropdownItem,
    profileDropdownExpanded: Boolean,
    onProfileDropdownExpandedChange: (Boolean) -> Unit,
    onProfileSelected: (org.bothubclient.presentation.viewmodel.ProfileDropdownItem) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenResults: () -> Unit = {}
) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileDropdown(
                items = profileItems,
                selected = selectedProfileItem,
                expanded = profileDropdownExpanded,
                onExpandedChange = onProfileDropdownExpandedChange,
                onSelected = onProfileSelected
            )
            IconButton(onClick = onOpenProfile) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Открыть профиль",
                    tint = MaterialTheme.colors.secondary
                )
            }
            IconButton(onClick = onOpenResults) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Результаты",
                    tint = MaterialTheme.colors.secondary
                )
            }
            IconButton(onClick = onOpenMcpSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "MCP Servers",
                    tint = MaterialTheme.colors.secondary
                )
            }
            IconButton(onClick = onToggleSidePanel) {
                Icon(
                    imageVector =
                        if (isSidePanelVisible)
                            Icons.Default.KeyboardArrowRight
                        else Icons.Default.KeyboardArrowLeft,
                    contentDescription =
                        if (isSidePanelVisible) "Hide side panel"
                        else "Show side panel",
                    tint = MaterialTheme.colors.secondary
                )
            }
            if (showReset) {
                ResetButton(enabled = true, onClick = onReset)
            }
        }
    }
}

@Composable
private fun ProfileDropdown(
    items: List<org.bothubclient.presentation.viewmodel.ProfileDropdownItem>,
    selected: org.bothubclient.presentation.viewmodel.ProfileDropdownItem,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (org.bothubclient.presentation.viewmodel.ProfileDropdownItem) -> Unit,
    enabled: Boolean = true
) {
    Box {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "Профиль: ${selected.title}",
                fontSize = 12.sp,
                color = if (enabled) MaterialTheme.colors.secondary else Color.Gray
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Выбрать профиль",
                tint = if (enabled) MaterialTheme.colors.secondary else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(MaterialTheme.colors.surface).width(280.dp)
        ) {
            items.forEach { item ->
                val isSelected = item.id == selected.id
                DropdownMenuItem(
                    onClick = {
                        onSelected(item)
                        onExpandedChange(false)
                    },
                    modifier =
                        Modifier.background(
                            if (isSelected)
                                MaterialTheme.colors.primary.copy(
                                    alpha = 0.2f
                                )
                            else MaterialTheme.colors.surface
                        )
                ) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        color =
                            if (isSelected)
                                MaterialTheme.colors.secondary
                            else MaterialTheme.colors.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(message: String, isError: Boolean) {
    Text(
        text = message,
        fontSize = 12.sp,
        color = if (isError) Color(0xFFFF6B6B) else Color.Gray
    )
}
