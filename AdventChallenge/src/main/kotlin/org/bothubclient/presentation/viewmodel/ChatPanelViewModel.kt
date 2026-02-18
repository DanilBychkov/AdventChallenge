package org.bothubclient.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bothubclient.application.usecase.*
import org.bothubclient.config.SystemPrompt
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message

class ChatPanelViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase,
    private val getSystemPromptsUseCase: GetSystemPromptsUseCase,
    private val validateApiKeyUseCase: ValidateApiKeyUseCase,
    private val optimizePromptUseCase: OptimizePromptUseCase
) {
    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> get() = _messages.toList()

    var inputText by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf("Готов")
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

    val effectivePromptText: String
        get() = if (selectedPrompt.isCustom) {
            optimizedPromptText ?: customPromptText
        } else {
            selectedPrompt.text
        }

    val hasOptimizedPrompt: Boolean
        get() = selectedPrompt.isCustom && optimizedPromptText != null

    val availableModels: List<String> get() = getAvailableModelsUseCase()
    val availablePrompts: List<SystemPrompt> get() = getSystemPromptsUseCase()

    fun onInputTextChanged(text: String) {
        inputText = text
    }

    fun onModelSelected(model: String) {
        selectedModel = model
    }

    fun onPromptSelected(prompt: SystemPrompt) {
        if (_messages.isEmpty() && !isLoading) {
            selectedPrompt = prompt
        }
    }

    fun onCustomPromptTextChanged(text: String) {
        customPromptText = text
        optimizedPromptText = null
    }

    fun optimizeCustomPrompt(scope: CoroutineScope) {
        if (customPromptText.isBlank() || isOptimizingPrompt) return

        isOptimizingPrompt = true
        statusMessage = "Оптимизация..."

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                optimizePromptUseCase(customPromptText, selectedModel)
            }

            when (result) {
                is OptimizePromptResult.Success -> {
                    optimizedPromptText = result.optimizedPrompt
                    statusMessage = "Оптимизирован"
                }

                is OptimizePromptResult.Error -> {
                    statusMessage = "Ошибка"
                }
            }
            isOptimizingPrompt = false
        }
    }

    fun useOriginalPrompt() {
        optimizedPromptText = null
        statusMessage = "Готов"
    }

    fun resetSession() {
        _messages.clear()
        inputText = ""
        statusMessage = "Готов"
        selectedPrompt = getSystemPromptsUseCase.getDefault()
        customPromptText = ""
        optimizedPromptText = null
    }

    fun sendMessage(scope: CoroutineScope) {
        if (inputText.isBlank() || isLoading) return

        val userMessage = inputText.trim()
        inputText = ""
        _messages.add(Message.user(userMessage))
        isLoading = true
        statusMessage = "Отправка..."

        scope.launch {
            val validationResult = validateApiKeyUseCase()
            if (validationResult.isFailure) {
                statusMessage = "Ошибка API"
                isLoading = false
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                sendMessageUseCase(userMessage, selectedModel, effectivePromptText)
            }

            when (result) {
                is ChatResult.Success -> {
                    _messages.add(result.message)
                    statusMessage = "Готов"
                }

                is ChatResult.Error -> {
                    _messages.add(Message.error("Ошибка: ${result.exception.message}"))
                    statusMessage = "Ошибка"
                }
            }
            isLoading = false
        }
    }

    companion object {
        fun create(): ChatPanelViewModel {
            return ChatPanelViewModel(
                sendMessageUseCase = org.bothubclient.infrastructure.di.ServiceLocator.sendMessageUseCase,
                getAvailableModelsUseCase = org.bothubclient.infrastructure.di.ServiceLocator.getAvailableModelsUseCase,
                getSystemPromptsUseCase = org.bothubclient.infrastructure.di.ServiceLocator.getSystemPromptsUseCase,
                validateApiKeyUseCase = org.bothubclient.infrastructure.di.ServiceLocator.validateApiKeyUseCase,
                optimizePromptUseCase = org.bothubclient.infrastructure.di.ServiceLocator.optimizePromptUseCase
            )
        }
    }
}
