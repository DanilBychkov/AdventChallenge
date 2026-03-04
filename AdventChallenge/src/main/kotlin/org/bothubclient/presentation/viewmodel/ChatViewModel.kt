package org.bothubclient.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bothubclient.application.usecase.*
import org.bothubclient.config.ModelPricing
import org.bothubclient.config.SystemPrompt
import org.bothubclient.domain.entity.*
import org.bothubclient.domain.memory.MemoryItem
import org.bothubclient.infrastructure.agent.CompressingChatAgent
import org.bothubclient.infrastructure.logging.FileLogger
import org.bothubclient.infrastructure.repository.UserProfileRepository
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
    private val compressingChatAgent: CompressingChatAgent? = null
) {

    private fun log(message: String) = FileLogger.log("ChatViewModel", message)

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
        compressingChatAgent?.let { agent ->
            summaryBlocks = agent.getSummaryBlocks("chat-ui")
            log("Updated summary blocks: ${summaryBlocks.size}")
        }
    }

    fun updateContextConfig(newConfig: ContextConfig) {
        log("CONFIG UPDATE: old=$contextConfig -> new=$newConfig")
        contextConfig = newConfig
        compressingChatAgent?.updateConfig(newConfig)
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

    fun onStrategySelected(strategy: ContextStrategy) {
        log("onStrategySelected: $strategy")
        updateContextConfig(contextConfig.withStrategy(strategy))
        refreshBranchState()
    }

    fun onBranchSelected(scope: CoroutineScope, branchId: String) {
        compressingChatAgent?.setActiveBranch("chat-ui", branchId)
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
        val agent = compressingChatAgent ?: return
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
        compressingChatAgent?.let { agent ->
            branches = agent.getBranchIds("chat-ui")
            activeBranchId = agent.getActiveBranchId("chat-ui")
            branchCheckpointSize = messages.size
            workingMemory = agent.getWorkingMemory("chat-ui")
            stmCount = agent.getStmCount("chat-ui")
            shortTermMessages = agent.getShortTermMessages("chat-ui")
            taskContext = agent.getTaskContextSnapshot("chat-ui")
            val totalFacts = workingMemory.values.sumOf { it.size }
            log("Updated memory: stm=$stmCount wmGroups=${workingMemory.size} wmFacts=$totalFacts")
        }
    }

    fun approveTaskPlan(scope: CoroutineScope) {
        val agent = compressingChatAgent ?: return
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
        val agent = compressingChatAgent ?: return
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
        val agent = compressingChatAgent ?: return
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
        val agent = compressingChatAgent ?: return
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
        compressingChatAgent?.let { agent ->
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

    private fun tryParseCategory(text: String): WmCategory? {
        return runCatching { WmCategory.valueOf(text.trim().uppercase()) }.getOrNull()
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
        val text = raw.trim()
        if (!text.startsWith("!")) return false
        val parts = text.split(Regex("\\s+"))
        if (parts.isEmpty()) return false
        when (parts[0].lowercase()) {
            "!memory" -> {
                if (parts.size >= 3 && parts[1].lowercase() == "panel") {
                    when (parts[2].lowercase()) {
                        "show" -> {
                            isMemoryPanelVisible = true
                            val msg =
                                jsonString(
                                    emptyList(),
                                    "memory_panel",
                                    "✓ Memory Panel открыта"
                                )
                            messages = messages + Message.system(msg)
                        }
                        "hide" -> {
                            isMemoryPanelVisible = false
                            val msg = jsonString(emptyList(), "none", "✓ Memory Panel скрыта")
                            messages = messages + Message.system(msg)
                        }
                        "toggle" -> {
                            isMemoryPanelVisible = !isMemoryPanelVisible
                            val action = if (isMemoryPanelVisible) "memory_panel" else "none"
                            val m =
                                if (isMemoryPanelVisible) "✓ Memory Panel открыта"
                                else "✓ Memory Panel скрыта"
                            val msg = jsonString(emptyList(), action, m)
                            messages = messages + Message.system(msg)
                        }
                    }
                    return true
                }
            }
            "!stm" -> {
                if (parts.size >= 2 && parts[1].lowercase() == "clear") {
                    val agent = compressingChatAgent
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
                    return true
                }
                if (parts.size >= 3 && parts[1].lowercase() == "last") {
                    val n = parts[2].toIntOrNull() ?: 10
                    val agent = compressingChatAgent
                    if (agent != null) {
                        val items = agent.getShortTermMessages("chat-ui").takeLast(n)
                        val preview =
                            items.joinToString(separator = "\\n") { m ->
                                "${m.timestamp} [${m.role}] ${m.content.take(120)}"
                            }
                        val msg =
                            jsonString(
                                emptyList(),
                                "memory_panel",
                                "Последние $n сообщений:\\n$preview"
                            )
                        messages = messages + Message.system(msg)
                    }
                    return true
                }
            }
            "!wm" -> {
                if (parts.size >= 2) {
                    when (parts[1].lowercase()) {
                        "set" -> {
                            if (parts.size >= 4) {
                                val category = tryParseCategory(parts[2])
                                if (category == null) {
                                    val err =
                                        jsonString(
                                            emptyList(),
                                            "none",
                                            "Ошибка: CATEGORY должен быть одним из USER_INFO/TASK/CONTEXT/PROGRESS"
                                        )
                                    messages = messages + Message.system(err)
                                    return true
                                }
                                val tail =
                                    text.substringAfter(
                                        parts[0] + " " + parts[1] + " " + parts[2]
                                    )
                                        .trim()
                                val eqIndex = tail.indexOf('=')
                                if (eqIndex <= 0) {
                                    val err =
                                        jsonString(
                                            emptyList(),
                                            "none",
                                            "Ошибка: ожидается key=value"
                                        )
                                    messages = messages + Message.system(err)
                                    return true
                                }
                                val key = tail.substring(0, eqIndex).trim()
                                val rest = tail.substring(eqIndex + 1).trim()
                                val tokens = rest.split(Regex("\\s+"))
                                val lastAsConf = tokens.lastOrNull()?.toFloatOrNull()
                                val value =
                                    if (lastAsConf != null) {
                                        rest.removeSuffix(tokens.last()).trim()
                                    } else {
                                        rest
                                    }
                                val conf = lastAsConf ?: 1.0f
                                compressingChatAgent?.setWorkingMemoryEntry(
                                    "chat-ui",
                                    category,
                                    key,
                                    value,
                                    conf
                                )
                                refreshBranchState()
                                val op =
                                    mapOf(
                                        "op" to "SET_WM",
                                        "layer" to "WM",
                                        "category" to category.name,
                                        "key" to key,
                                        "value" to value,
                                        "confidence" to conf
                                    )
                                val msg =
                                    jsonString(
                                        listOf(op),
                                        "memory_panel_refresh",
                                        "✓ Запомнил в WM: $key"
                                    )
                                messages = messages + Message.system(msg)
                            } else {
                                val err =
                                    jsonString(
                                        emptyList(),
                                        "none",
                                        "Синтаксис: !wm set <CATEGORY> <key>=<value> [confidence]"
                                    )
                                messages = messages + Message.system(err)
                            }
                            return true
                        }
                        "get" -> {
                            if (parts.size >= 4) {
                                val category = tryParseCategory(parts[2])
                                val key = parts.drop(3).joinToString(" ").trim()
                                if (category == null || key.isBlank()) {
                                    val err =
                                        jsonString(
                                            emptyList(),
                                            "none",
                                            "Синтаксис: !wm get <CATEGORY> <key>"
                                        )
                                    messages = messages + Message.system(err)
                                    return true
                                }
                                val value = workingMemory[category]?.get(key)?.value ?: ""
                                val res = if (value.isBlank()) "Не найдено" else "$key=$value"
                                val msg = jsonString(emptyList(), "memory_panel", res)
                                messages = messages + Message.system(msg)
                                return true
                            } else {
                                val err =
                                    jsonString(
                                        emptyList(),
                                        "none",
                                        "Синтаксис: !wm get <CATEGORY> <key>"
                                    )
                                messages = messages + Message.system(err)
                                return true
                            }
                        }
                        "delete" -> {
                            if (parts.size >= 4) {
                                val category = tryParseCategory(parts[2])
                                val key = parts.drop(3).joinToString(" ").trim()
                                if (category == null || key.isBlank()) {
                                    val err =
                                        jsonString(
                                            emptyList(),
                                            "none",
                                            "Синтаксис: !wm delete <CATEGORY> <key>"
                                        )
                                    messages = messages + Message.system(err)
                                    return true
                                }
                                val removed =
                                    compressingChatAgent?.deleteWorkingMemoryEntry(
                                        "chat-ui",
                                        category,
                                        key
                                    )
                                        ?: false
                                refreshBranchState()
                                val op =
                                    mapOf(
                                        "op" to "DELETE_WM",
                                        "layer" to "WM",
                                        "category" to category.name,
                                        "key" to key,
                                        "value" to null,
                                        "confidence" to 1.0
                                    )
                                val msg =
                                    jsonString(
                                        listOf(op),
                                        "memory_panel_refresh",
                                        if (removed) "✓ Удалено из ${category.name}: $key"
                                        else "Не найдено: $key"
                                    )
                                messages = messages + Message.system(msg)
                                return true
                            } else {
                                val err =
                                    jsonString(
                                        emptyList(),
                                        "none",
                                        "Синтаксис: !wm delete <CATEGORY> <key>"
                                    )
                                messages = messages + Message.system(err)
                                return true
                            }
                        }
                        "list" -> {
                            if (parts.size == 2) {
                                val count = workingMemory.values.sumOf { it.size }
                                val msg =
                                    jsonString(
                                        emptyList(),
                                        "memory_panel",
                                        "✓ Memory Panel открыта, WM записи: $count"
                                    )
                                messages = messages + Message.system(msg)
                                return true
                            } else {
                                val category = tryParseCategory(parts[2])
                                if (category == null) {
                                    val err =
                                        jsonString(
                                            emptyList(),
                                            "none",
                                            "Синтаксис: !wm list [CATEGORY]"
                                        )
                                    messages = messages + Message.system(err)
                                    return true
                                }
                                val count = workingMemory[category]?.size ?: 0
                                val msg =
                                    jsonString(
                                        emptyList(),
                                        "memory_panel",
                                        "✓ Memory Panel открыта на вкладке WM > ${category.name} (записей $count)"
                                    )
                                messages = messages + Message.system(msg)
                                return true
                            }
                        }
                    }
                }
            }
            "!ltm" -> {
                if (parts.size >= 2) {
                    when (parts[1].lowercase()) {
                        "save" -> {
                            val tail = text.substringAfter("!ltm save").trim()
                            val eqIndex = tail.indexOf('=')
                            if (eqIndex <= 0) {
                                val err =
                                    jsonString(
                                        emptyList(),
                                        "none",
                                        "Синтаксис: !ltm save <key>=<value> [CATEGORY]"
                                    )
                                messages = messages + Message.system(err)
                                return true
                            }
                            val key = tail.substring(0, eqIndex).trim()
                            val after = tail.substring(eqIndex + 1).trim()
                            val tokens = after.split(Regex("\\s+"))
                            val categoryText = tokens.lastOrNull()
                            val value =
                                if (categoryText != null &&
                                    tryParseCategory(categoryText) != null
                                ) {
                                    after.removeSuffix(categoryText).trim()
                                } else {
                                    after
                                }
                            val category =
                                categoryText?.let { tryParseCategory(it) } ?: WmCategory.CONTEXT
                            scope.launch {
                                compressingChatAgent?.saveToLtm(category, key, value, 1.0f)
                                loadLongTermMemory(this)
                                val op =
                                    mapOf(
                                        "op" to "SAVE_LTM",
                                        "layer" to "LTM",
                                        "category" to category.name,
                                        "key" to key,
                                        "value" to value,
                                        "confidence" to 1.0
                                    )
                                val msg =
                                    jsonString(
                                        listOf(op),
                                        "memory_panel_refresh",
                                        "✓ Сохранено в LTM: $key"
                                    )
                                messages = messages + Message.system(msg)
                            }
                            return true
                        }
                        "find" -> {
                            val query = text.substringAfter("!ltm find").trim()
                            if (query.isBlank()) {
                                val err =
                                    jsonString(
                                        emptyList(),
                                        "none",
                                        "Синтаксис: !ltm find <query>"
                                    )
                                messages = messages + Message.system(err)
                                return true
                            }
                            scope.launch {
                                val items =
                                    compressingChatAgent?.findInLtm(query, 10) ?: emptyList()
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
                            return true
                        }
                        "delete" -> {
                            val key = text.substringAfter("!ltm delete").trim()
                            if (key.isBlank()) {
                                val err =
                                    jsonString(
                                        emptyList(),
                                        "none",
                                        "Синтаксис: !ltm delete <key>"
                                    )
                                messages = messages + Message.system(err)
                                return true
                            }
                            scope.launch {
                                val n = compressingChatAgent?.deleteFromLtmByKey(key) ?: 0
                                loadLongTermMemory(this)
                                val op =
                                    mapOf(
                                        "op" to "DELETE_LTM",
                                        "layer" to "LTM",
                                        "category" to null,
                                        "key" to key,
                                        "value" to null,
                                        "confidence" to 1.0
                                    )
                                val msg =
                                    jsonString(
                                        listOf(op),
                                        "memory_panel_refresh",
                                        if (n > 0) "✓ Удалено из LTM: $key ($n)"
                                        else "Ничего не удалено: $key"
                                    )
                                messages = messages + Message.system(msg)
                            }
                            return true
                        }
                    }
                }
            }
        }
        return false
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
                    compressingChatAgent?.let { agent ->
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
                compressingChatAgent =
                    org.bothubclient.infrastructure.di.ServiceLocator.compressingChatAgent
            )
        }
    }
}

data class ProfileDropdownItem(val id: String?, val title: String)
