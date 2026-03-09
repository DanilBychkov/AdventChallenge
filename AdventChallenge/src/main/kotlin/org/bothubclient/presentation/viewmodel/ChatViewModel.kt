package org.bothubclient.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bothubclient.application.usecase.*
import org.bothubclient.config.ModelPricing
import org.bothubclient.config.SystemPrompt
import org.bothubclient.domain.agent.ChatAgentIntrospection
import org.bothubclient.domain.entity.*
import org.bothubclient.domain.logging.Logger
import org.bothubclient.domain.logging.NoOpLogger
import org.bothubclient.domain.memory.MemoryItem
import org.bothubclient.domain.repository.UserProfileRepository
import org.bothubclient.presentation.commands.MemoryCommand
import org.bothubclient.presentation.commands.MemoryCommandParser
import org.bothubclient.presentation.commands.MemoryParseResult
import kotlin.math.abs
import kotlin.math.roundToInt

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase,
    private val getSystemPromptsUseCase: GetSystemPromptsUseCase,
    private val validateApiKeyUseCase: ValidateApiKeyUseCase,
    private val optimizePromptUseCase: OptimizePromptUseCase,
    private val resetChatSessionUseCase: ResetChatSessionUseCase,
    private val getChatHistoryUseCase: GetChatHistoryUseCase,
    private val getSessionMessagesUseCase: GetSessionMessagesUseCase,
    private val getTokenStatisticsUseCase: GetTokenStatisticsUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val logger: Logger = NoOpLogger,
    private val agentIntrospection: ChatAgentIntrospection? = null
) {
    private val memoryCommandParser = MemoryCommandParser()

    private fun log(message: String) = logger.log("ChatViewModel", message)

    var messages by mutableStateOf<List<Message>>(emptyList())
        private set

    var inputText by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf("Готов к работе")
        private set

    var apiKeyError by mutableStateOf<String?>(null)
        private set

    var selectedModel by mutableStateOf(getAvailableModelsUseCase.getDefault())
        private set

    var selectedPrompt by mutableStateOf(getSystemPromptsUseCase.getDefault())
        private set

    var customPromptText by mutableStateOf("")
        private set

    var optimizedPromptText by mutableStateOf<String?>(null)
        private set

    var isOptimizingPrompt by mutableStateOf(false)
        private set

    var optimizePromptError by mutableStateOf<String?>(null)
        private set

    var temperatureText by mutableStateOf("0.7")
        private set

    var temperatureError by mutableStateOf<String?>(null)
        private set

    var tokenStatistics by mutableStateOf(SessionTokenStatistics.EMPTY)
        private set

    var contextConfig by mutableStateOf(ContextConfig.DEFAULT)
        private set

    var summaryBlocks by mutableStateOf<List<SummaryBlock>>(emptyList())
        private set

    var workingMemory by mutableStateOf<Map<WmCategory, Map<String, FactEntry>>>(emptyMap())
        private set

    var agentMetrics by mutableStateOf(AgentMetricsSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0))
        private set

    var stmCount by mutableStateOf(0)
        private set

    var shortTermMessages by mutableStateOf<List<Message>>(emptyList())
        private set

    var longTermMemory by mutableStateOf<List<MemoryItem>>(emptyList())
        private set

    var contextMessages by mutableStateOf<List<Message>>(emptyList())
        private set

    var isContextConfigExpanded by mutableStateOf(false)
        private set

    var lastCompressionError by mutableStateOf<String?>(null)
        private set

    var userProfile by mutableStateOf<UserProfile?>(null)
        private set

    var userProfiles by mutableStateOf<List<UserProfile>>(emptyList())
        private set

    var taskContext by mutableStateOf<TaskContext?>(null)
        private set

    /** null означает режим "Без профиля" */
    var activeUserProfileId by mutableStateOf<String?>(null)
        private set

    var isSavingUserProfile by mutableStateOf(false)
        private set

    var userProfileError by mutableStateOf<String?>(null)
        private set

    val effectivePromptText: String
        get() {
            return if (selectedPrompt.isCustom) {
                optimizedPromptText ?: customPromptText
            } else {
                selectedPrompt.text
            }
        }

    val hasOptimizedPrompt: Boolean
        get() = selectedPrompt.isCustom && optimizedPromptText != null

    val availableModels: List<String>
        get() = getAvailableModelsUseCase()
    val availablePrompts: List<SystemPrompt>
        get() = getSystemPromptsUseCase()

    var isHistoryLoaded by mutableStateOf(false)
        private set

    var isMemoryPanelVisible by mutableStateOf(true)
        private set

    var branches by mutableStateOf<List<String>>(emptyList())
        private set

    var activeBranchId by mutableStateOf("main")
        private set

    var branchCheckpointSize by mutableStateOf(0)
        private set

    fun loadHistory(scope: CoroutineScope) {
        if (isHistoryLoaded) return

        scope.launch {
            statusMessage = "Загрузка истории..."
            val sessionMessages = getSessionMessagesUseCase()
            messages = sessionMessages
            updateTokenStatistics()
            updateSummaryBlocks()
            refreshBranchState()
            refreshContextMessages()
            statusMessage =
                if (sessionMessages.isNotEmpty()) {
                    "Восстановлено ${sessionMessages.size} сообщений"
                } else {
                    "Готов к работе"
                }
            isHistoryLoaded = true
        }
    }

    fun loadUserProfile(scope: CoroutineScope) {
        scope.launch {
            runCatching { userProfileRepository.loadProfiles() }
                .onSuccess { profiles ->
                    userProfiles = profiles
                    val active = profiles.firstOrNull { it.isActive }
                    activeUserProfileId = active?.id
                    userProfile = active
                    userProfileError = null
                }
                .onFailure { e -> userProfileError = e.message }
        }
    }

    fun saveUserProfile(scope: CoroutineScope, profile: UserProfile) {
        if (isSavingUserProfile) return
        scope.launch {
            isSavingUserProfile = true
            userProfileError = null
            runCatching { userProfileRepository.upsertAndActivate(profile) }
                .onSuccess {
                    userProfile = it
                    activeUserProfileId = it.id
                    statusMessage = "Профиль сохранён"
                    // Обновляем список профилей/флаги активного.
                    runCatching { userProfileRepository.loadProfiles() }.onSuccess { profiles ->
                        userProfiles = profiles
                    }
                }
                .onFailure { e ->
                    userProfileError = e.message
                    statusMessage = "Ошибка сохранения профиля"
                }
            isSavingUserProfile = false
        }
    }

    fun onUserProfileSelected(scope: CoroutineScope, profileId: String?) {
        if (isSavingUserProfile) return

        // Защита от "битого" выбора из UI: если id не найден, лучше не менять состояние.
        if (profileId != null && userProfiles.none { it.id == profileId }) {
            statusMessage = "Ошибка: профиль не найден"
            return
        }

        scope.launch {
            runCatching { userProfileRepository.setActiveProfile(profileId) }
                .onSuccess {
                    // Рефрешим список, чтобы UI корректно подсветил активный флаг.
                    runCatching { userProfileRepository.loadProfiles() }
                        .onSuccess { profiles ->
                            userProfiles = profiles
                            val active = profiles.firstOrNull { it.isActive }
                            activeUserProfileId = active?.id
                            userProfile = active
                            statusMessage =
                                if (active == null) "Режим: Без профиля"
                                else "Профиль: ${active.name}"
                            refreshContextMessages()
                        }
                        .onFailure { e -> userProfileError = e.message }
                }
                .onFailure { e ->
                    userProfileError = e.message
                    statusMessage = "Ошибка переключения профиля"
                }
        }
    }

    val userProfileDropdownItems: List<ProfileDropdownItem>
        get() {
            val base = listOf(ProfileDropdownItem(id = null, title = "Без профиля"))
            val rest =
                userProfiles.sortedBy { it.name.lowercase() }.map {
                    ProfileDropdownItem(
                        id = it.id,
                        title = it.name.ifBlank { "(без названия)" }
                    )
                }
            return base + rest
        }

    val selectedUserProfileDropdownItem: ProfileDropdownItem
        get() {
            return userProfileDropdownItems.firstOrNull { it.id == activeUserProfileId }
                ?: userProfileDropdownItems.first()
        }

    private fun updateTokenStatistics() {
        tokenStatistics = getTokenStatisticsUseCase(selectedModel)
    }

    private fun updateSummaryBlocks() {
        agentIntrospection?.let { agent ->
            summaryBlocks = agent.getSummaryBlocks("chat-ui")
            log("Updated summary blocks: ${summaryBlocks.size}")
        }
    }

    fun updateContextConfig(newConfig: ContextConfig) {
        log("CONFIG UPDATE: old=$contextConfig -> new=$newConfig")
        contextConfig = newConfig
        agentIntrospection?.updateConfig(newConfig)
        refreshContextMessages()
    }

    fun toggleContextConfigExpanded() {
        isContextConfigExpanded = !isContextConfigExpanded
        log("Context config expanded: $isContextConfigExpanded")
    }

    fun onKeepLastNChanged(value: Int) {
        log("onKeepLastNChanged: $value")
        updateContextConfig(contextConfig.withKeepLastN(value))
    }

    fun onCompressionBlockSizeChanged(value: Int) {
        log("onCompressionBlockSizeChanged: $value")
        updateContextConfig(contextConfig.withCompressionBlockSize(value))
    }

    fun onAutoCompressionToggled(enabled: Boolean) {
        log("onAutoCompressionToggled: $enabled")
        updateContextConfig(contextConfig.withAutoCompression(enabled))
    }

    fun onTaskStateMachineToggled(enabled: Boolean) {
        log("onTaskStateMachineToggled: $enabled")
        updateContextConfig(contextConfig.withTaskStateMachine(enabled))
    }

    fun onStateMachineTemplateSelected(template: StateMachineTemplate) {
        log("onStateMachineTemplateSelected: $template")
        updateContextConfig(contextConfig.withStateMachineTemplate(template))
    }

    fun onStrategySelected(strategy: ContextStrategy) {
        log("onStrategySelected: $strategy")
        updateContextConfig(contextConfig.withStrategy(strategy))
        refreshBranchState()
    }

    fun onBranchSelected(scope: CoroutineScope, branchId: String) {
        agentIntrospection?.setActiveBranch("chat-ui", branchId)
        refreshBranchState()
        scope.launch {
            messages = getSessionMessagesUseCase()
            updateSummaryBlocks()
            refreshBranchState()
        }
    }

    fun onBranchCheckpointSizeChanged(value: Int) {
        branchCheckpointSize = value
    }

    fun createBranchFromCheckpoint(scope: CoroutineScope) {
        val agent = agentIntrospection ?: return
        val newBranchId = agent.createBranchFromCheckpoint("chat-ui", branchCheckpointSize)
        agent.setActiveBranch("chat-ui", newBranchId)
        refreshBranchState()
        scope.launch {
            messages = getSessionMessagesUseCase()
            updateSummaryBlocks()
            refreshBranchState()
        }
    }

    private fun refreshBranchState() {
        agentIntrospection?.let { agent ->
            branches = agent.getBranchIds("chat-ui")
            activeBranchId = agent.getActiveBranchId("chat-ui")
            branchCheckpointSize = messages.size
            workingMemory = agent.getWorkingMemory("chat-ui")
            agentMetrics = agent.getMetricsSnapshot("chat-ui")
            stmCount = agent.getStmCount("chat-ui")
            shortTermMessages = agent.getShortTermMessages("chat-ui")
            taskContext =
                if (contextConfig.enableTaskStateMachine) {
                    agent.getTaskContextSnapshot("chat-ui")
                } else {
                    null
                }
            val totalFacts = workingMemory.values.sumOf { it.size }
            log("Updated memory: stm=$stmCount wmGroups=${workingMemory.size} wmFacts=$totalFacts")
        }
    }

    fun approveTaskPlan(scope: CoroutineScope) {
        val agent = agentIntrospection ?: return
        if (isLoading) return
        isLoading = true
        statusMessage = "Утверждение плана..."
        scope.launch {
            runCatching { agent.approveTaskPlan("chat-ui") }
                .onSuccess {
                    refreshBranchState()
                    statusMessage = "План утверждён"
                }
                .onFailure { e ->
                    statusMessage = "Ошибка утверждения плана"
                    log("approveTaskPlan failed: ${e.message}")
                }
            isLoading = false
        }
    }

    fun approveTaskValidation(scope: CoroutineScope) {
        val agent = agentIntrospection ?: return
        if (isLoading) return
        isLoading = true
        statusMessage = "Подтверждение завершения..."
        scope.launch {
            runCatching { agent.approveTaskValidation("chat-ui") }
                .onSuccess {
                    refreshBranchState()
                    statusMessage = "Задача завершена"
                }
                .onFailure { e ->
                    statusMessage = "Ошибка завершения задачи"
                    log("approveTaskValidation failed: ${e.message}")
                }
            isLoading = false
        }
    }

    fun resetTask(scope: CoroutineScope) {
        val agent = agentIntrospection ?: return
        if (isLoading) return
        isLoading = true
        statusMessage = "Сброс задачи..."
        scope.launch {
            runCatching { agent.resetTask("chat-ui") }
                .onSuccess {
                    refreshBranchState()
                    statusMessage = "Задача сброшена"
                }
                .onFailure { e ->
                    statusMessage = "Ошибка сброса задачи"
                    log("resetTask failed: ${e.message}")
                }
            isLoading = false
        }
    }

    fun loadLongTermMemory(scope: CoroutineScope) {
        val agent = agentIntrospection ?: return
        scope.launch {
            longTermMemory = agent.getLongTermMemorySnapshot()
            log("Loaded LTM: ${longTermMemory.size}")
        }
    }

    private fun refreshContextMessages() {
        if (contextConfig.strategy != ContextStrategy.SLIDING_WINDOW) {
            contextMessages = emptyList()
            return
        }
        agentIntrospection?.let { agent ->
            val composed =
                agent.getComposedContext(
                    sessionId = "chat-ui",
                    systemPrompt = effectivePromptText,
                    userMessage = ""
                )
            contextMessages = composed.recentMessages
            log("Updated context messages: ${contextMessages.size}")
        }
    }

    private fun parseTemperatureOrNull(text: String): Double? {
        val normalized = text.trim().replace(',', '.')
        val value = normalized.toDoubleOrNull() ?: return null
        if (value < 0.0 || value > 2.0) return null
        val scaled = value * 10.0
        val roundedScaled = scaled.roundToInt().toDouble()
        if (abs(scaled - roundedScaled) > 1e-6) return null
        return roundedScaled / 10.0
    }

    fun onTemperatureTextChanged(text: String) {
        if (messages.isNotEmpty()) return
        temperatureText = text
        temperatureError =
            if (parseTemperatureOrNull(text) == null) {
                "Введите число от 0 до 2 с шагом 0.1"
            } else {
                null
            }
    }

    fun onInputTextChanged(text: String) {
        inputText = text
    }

    fun onModelSelected(model: String) {
        selectedModel = model
        updateTokenStatistics()
    }

    fun onPromptSelected(prompt: SystemPrompt) {
        if (messages.isEmpty() && !isLoading) {
            selectedPrompt = prompt
        }
    }

    fun onCustomPromptTextChanged(text: String) {
        customPromptText = text
        optimizedPromptText = null
        optimizePromptError = null
    }

    fun optimizeCustomPrompt(scope: CoroutineScope) {
        if (customPromptText.isBlank() || isOptimizingPrompt) return

        isOptimizingPrompt = true
        optimizePromptError = null
        statusMessage = "Оптимизация промпта..."

        scope.launch {
            val validationResult = validateApiKeyUseCase()
            if (validationResult.isFailure) {
                apiKeyError = validationResult.exceptionOrNull()?.message
                statusMessage = "Ошибка конфигурации"
                isOptimizingPrompt = false
                return@launch
            }

            val result = optimizePromptUseCase(customPromptText, selectedModel)

            when (result) {
                is OptimizePromptResult.Success -> {
                    optimizedPromptText = result.optimizedPrompt
                    statusMessage = "Промпт оптимизирован"
                }
                is OptimizePromptResult.Error -> {
                    optimizePromptError = result.message
                    statusMessage = "Ошибка оптимизации"
                }
            }
            isOptimizingPrompt = false
        }
    }

    fun useOriginalPrompt() {
        optimizedPromptText = null
        statusMessage = "Используется оригинальный промпт"
    }

    fun resetSession(scope: CoroutineScope) {
        scope.launch { resetChatSessionUseCase() }
        messages = emptyList()
        inputText = ""
        statusMessage = "Готов к работе"
        apiKeyError = null
        selectedPrompt = getSystemPromptsUseCase.getDefault()
        customPromptText = ""
        optimizedPromptText = null
        optimizePromptError = null
        temperatureText = "0.7"
        temperatureError = null
        tokenStatistics = SessionTokenStatistics.EMPTY
        summaryBlocks = emptyList()
        workingMemory = emptyMap()
        stmCount = 0
        shortTermMessages = emptyList()
        longTermMemory = emptyList()
        contextMessages = emptyList()
        lastCompressionError = null
        branches = emptyList()
        activeBranchId = "main"
        branchCheckpointSize = 0
        isHistoryLoaded = true
    }

    private fun jsonString(
        memoryOps: List<Map<String, Any?>>,
        uiAction: String,
        message: String
    ): String {
        val ops =
            memoryOps.joinToString(prefix = "[", postfix = "]") { op ->
                val cat = op["category"]?.let { "\"$it\"" } ?: "null"
                "{" +
                        "\"op\":\"${op["op"]}\"," +
                        "\"layer\":\"${op["layer"]}\"," +
                        "\"category\":$cat," +
                        "\"key\":\"${op["key"] ?: ""}\"," +
                        "\"value\":${op["value"]?.let { "\"$it\"" } ?: "null"}," +
                        "\"confidence\":${op["confidence"] ?: 1.0}" +
                        "}"
            }
        val m = message.replace("\n", "\\n")
        return "{\"memory_ops\":$ops,\"ui_action\":\"$uiAction\",\"message\":\"$m\"}"
    }

    private fun handleMemoryCommand(scope: CoroutineScope, raw: String): Boolean {
        val parsed = memoryCommandParser.parse(raw) ?: return false
        when (parsed) {
            is MemoryParseResult.Error -> {
                val err = jsonString(emptyList(), "none", parsed.message)
                messages = messages + Message.system(err)
                return true
            }

            is MemoryParseResult.Command -> {
                when (val cmd = parsed.command) {
                    MemoryCommand.MemoryPanel.Show -> {
                        isMemoryPanelVisible = true
                        val msg = jsonString(emptyList(), "memory_panel", "✓ Memory Panel открыта")
                        messages = messages + Message.system(msg)
                    }

                    MemoryCommand.MemoryPanel.Hide -> {
                        isMemoryPanelVisible = false
                        val msg = jsonString(emptyList(), "none", "✓ Memory Panel скрыта")
                        messages = messages + Message.system(msg)
                    }

                    MemoryCommand.MemoryPanel.Toggle -> {
                        isMemoryPanelVisible = !isMemoryPanelVisible
                        val action = if (isMemoryPanelVisible) "memory_panel" else "none"
                        val m =
                                if (isMemoryPanelVisible) "✓ Memory Panel открыта"
                                else "✓ Memory Panel скрыта"
                        val msg = jsonString(emptyList(), action, m)
                        messages = messages + Message.system(msg)
                    }

                    MemoryCommand.Stm.Clear -> {
                        val agent = agentIntrospection
                        if (agent != null) {
                            val removed = agent.clearShortTermMemory("chat-ui")
                            refreshBranchState()
                            val op =
                                mapOf(
                                    "op" to "CLEAR_STM",
                                    "layer" to "STM",
                                    "category" to null,
                                    "key" to "",
                                    "value" to null,
                                    "confidence" to 1.0
                                )
                            val msg =
                                jsonString(
                                    listOf(op),
                                    "memory_panel_refresh",
                                    "✓ STM очищена ($removed)"
                                )
                            messages = messages + Message.system(msg)
                        }
                    }

                    is MemoryCommand.Stm.Last -> {
                        val agent = agentIntrospection
                        if (agent != null) {
                            val items = agent.getShortTermMessages("chat-ui").takeLast(cmd.n)
                            val preview =
                                items.joinToString(separator = "\\n") { m ->
                                    "${m.timestamp} [${m.role}] ${m.content.take(120)}"
                                }
                            val msg =
                                jsonString(
                                    emptyList(),
                                    "memory_panel",
                                    "Последние ${cmd.n} сообщений:\\n$preview"
                                )
                            messages = messages + Message.system(msg)
                        }
                    }

                    is MemoryCommand.Wm.Set -> {
                        agentIntrospection?.setWorkingMemoryEntry(
                            "chat-ui",
                            cmd.category,
                            cmd.key,
                            cmd.value,
                            cmd.confidence
                        )
                        refreshBranchState()
                        val op =
                            mapOf(
                                        "op" to "SET_WM",
                                        "layer" to "WM",
                                "category" to cmd.category.name,
                                "key" to cmd.key,
                                "value" to cmd.value,
                                "confidence" to cmd.confidence
                            )
                        val msg =
                            jsonString(
                                        listOf(op),
                                        "memory_panel_refresh",
                                "✓ Запомнил в WM: ${cmd.key}"
                            )
                        messages = messages + Message.system(msg)
                    }

                    is MemoryCommand.Wm.Get -> {
                        val value = workingMemory[cmd.category]?.get(cmd.key)?.value ?: ""
                        val res = if (value.isBlank()) "Не найдено" else "${cmd.key}=$value"
                        val msg = jsonString(emptyList(), "memory_panel", res)
                        messages = messages + Message.system(msg)
                    }

                    is MemoryCommand.Wm.Delete -> {
                        val removed =
                            agentIntrospection?.deleteWorkingMemoryEntry(
                                        "chat-ui",
                                cmd.category,
                                cmd.key
                            )
                                        ?: false
                        refreshBranchState()
                        val op =
                            mapOf(
                                        "op" to "DELETE_WM",
                                        "layer" to "WM",
                                "category" to cmd.category.name,
                                "key" to cmd.key,
                                        "value" to null,
                                        "confidence" to 1.0
                            )
                        val msg =
                            jsonString(
                                        listOf(op),
                                        "memory_panel_refresh",
                                if (removed) "✓ Удалено из ${cmd.category.name}: ${cmd.key}"
                                else "Не найдено: ${cmd.key}"
                            )
                        messages = messages + Message.system(msg)
                    }

                    is MemoryCommand.Wm.List -> {
                        if (cmd.category == null) {
                            val count = workingMemory.values.sumOf { it.size }
                            val msg =
                                    jsonString(
                                        emptyList(),
                                        "memory_panel",
                                        "✓ Memory Panel открыта, WM записи: $count"
                                    )
                            messages = messages + Message.system(msg)
                        } else {
                            val count = workingMemory[cmd.category]?.size ?: 0
                            val msg =
                                    jsonString(
                                        emptyList(),
                                        "memory_panel",
                                        "✓ Memory Panel открыта на вкладке WM > ${cmd.category.name} (записей $count)"
                                    )
                            messages = messages + Message.system(msg)
                        }
                    }

                    is MemoryCommand.Ltm.Save -> {
                        scope.launch {
                            agentIntrospection?.saveToLtm(cmd.category, cmd.key, cmd.value, 1.0f)
                            loadLongTermMemory(this)
                            val op =
                                    mapOf(
                                        "op" to "SAVE_LTM",
                                        "layer" to "LTM",
                                        "category" to cmd.category.name,
                                        "key" to cmd.key,
                                        "value" to cmd.value,
                                        "confidence" to 1.0
                                    )
                            val msg =
                                    jsonString(
                                        listOf(op),
                                        "memory_panel_refresh",
                                        "✓ Сохранено в LTM: ${cmd.key}"
                                    )
                            messages = messages + Message.system(msg)
                        }
                    }

                    is MemoryCommand.Ltm.Find -> {
                        scope.launch {
                            val items = agentIntrospection?.findInLtm(cmd.query, 10) ?: emptyList()
                            val lines =
                                    items.joinToString(separator = "\\n") { item ->
                                        "${item.category.name}:${item.key}=${item.entry.value}"
                                    }
                            val msg =
                                    jsonString(
                                        emptyList(),
                                        "memory_panel",
                                        if (lines.isBlank()) "Ничего не найдено"
                                        else "🔍 Результаты:\\n$lines"
                                    )
                            messages = messages + Message.system(msg)
                        }
                    }

                    is MemoryCommand.Ltm.Delete -> {
                        scope.launch {
                            val n = agentIntrospection?.deleteFromLtmByKey(cmd.key) ?: 0
                            loadLongTermMemory(this)
                            val op =
                                    mapOf(
                                        "op" to "DELETE_LTM",
                                        "layer" to "LTM",
                                        "category" to null,
                                        "key" to cmd.key,
                                        "value" to null,
                                        "confidence" to 1.0
                                    )
                            val msg =
                                    jsonString(
                                        listOf(op),
                                        "memory_panel_refresh",
                                        if (n > 0) "✓ Удалено из LTM: ${cmd.key} ($n)"
                                        else "Ничего не удалено: ${cmd.key}"
                                    )
                            messages = messages + Message.system(msg)
                        }
                    }
                }
                return true
            }
        }
    }

    fun sendMessage(scope: CoroutineScope) {
        if (inputText.isBlank() || isLoading) return

        val temperature = parseTemperatureOrNull(temperatureText)
        if (temperature == null) {
            temperatureError = "Введите число от 0 до 2 с шагом 0.1"
            statusMessage = "Ошибка температуры"
            return
        }
        temperatureError = null

        val userMessage = inputText.trim()
        if (handleMemoryCommand(scope, userMessage)) {
            inputText = ""
            updateSummaryBlocks()
            refreshBranchState()
            refreshContextMessages()
            return
        }
        messages = messages + Message.user(userMessage)
        inputText = ""
        isLoading = true
        statusMessage = "Отправка запроса..."
        apiKeyError = null

        scope.launch {
            val validationResult = validateApiKeyUseCase()
            if (validationResult.isFailure) {
                apiKeyError = validationResult.exceptionOrNull()?.message
                statusMessage = "Ошибка конфигурации"
                isLoading = false
                return@launch
            }

            val result =
                sendMessageUseCase(userMessage, selectedModel, effectivePromptText, temperature)

            when (result) {
                is ChatResult.Success -> {
                    val metrics = result.metrics
                    val cost =
                        ModelPricing.calculateCostRub(
                            selectedModel,
                            metrics.promptTokens,
                            metrics.completionTokens
                        )
                    val stored = getSessionMessagesUseCase()
                    val enriched =
                        stored.map { m ->
                            if (m.role == MessageRole.ASSISTANT &&
                                m.content == result.message.content &&
                                m.metrics == null
                            ) {
                                m.copy(
                                    metrics =
                                        MessageMetrics(
                                            promptTokens = metrics.promptTokens,
                                            completionTokens =
                                                metrics.completionTokens,
                                            totalTokens = metrics.totalTokens,
                                            responseTimeMs = metrics.responseTimeMs,
                                            cost = cost
                                        )
                                )
                            } else {
                                m
                            }
                        }
                    messages = enriched
                    updateTokenStatistics()
                    updateSummaryBlocks()
                    refreshBranchState()
                    agentIntrospection?.let { agent ->
                        longTermMemory = agent.getLongTermMemorySnapshot()
                    }
                    refreshContextMessages()

                    if (summaryBlocks.isNotEmpty()) {
                        statusMessage = "Готов к работе (${summaryBlocks.size} блоков сжатия)"
                    } else if (tokenStatistics.isApproachingLimit) {
                        statusMessage =
                            "⚠ Внимание: ${"%.1f".format(tokenStatistics.contextUsagePercent)}% контекста использовано"
                    } else {
                        statusMessage = "Готов к работе"
                    }
                }
                is ChatResult.Error -> {
                    messages = messages + Message.error("Ошибка: ${result.exception.message}")
                    statusMessage = "Ошибка запроса"
                    refreshBranchState()
                    refreshContextMessages()
                }
            }
            isLoading = false
        }
    }

    companion object {
        fun create(): ChatViewModel {
            return ChatViewModel(
                sendMessageUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator.sendMessageUseCase,
                getAvailableModelsUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator
                        .getAvailableModelsUseCase,
                getSystemPromptsUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator
                        .getSystemPromptsUseCase,
                validateApiKeyUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator.validateApiKeyUseCase,
                optimizePromptUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator.optimizePromptUseCase,
                resetChatSessionUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator
                        .resetChatSessionUseCase,
                getChatHistoryUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator.getChatHistoryUseCase,
                getSessionMessagesUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator
                        .getSessionMessagesUseCase,
                getTokenStatisticsUseCase =
                    org.bothubclient.infrastructure.di.ServiceLocator
                        .getTokenStatisticsUseCase,
                userProfileRepository =
                    org.bothubclient.infrastructure.di.ServiceLocator.userProfileRepository,
                logger =
                    object : Logger {
                        override fun log(tag: String, message: String) {
                            org.bothubclient.infrastructure.logging.FileLogger.log(
                                tag,
                                message
                            )
                        }
                    },
                agentIntrospection =
                    org.bothubclient.infrastructure.di.ServiceLocator.compressingChatAgent
            )
        }
    }
}

data class ProfileDropdownItem(val id: String?, val title: String)
