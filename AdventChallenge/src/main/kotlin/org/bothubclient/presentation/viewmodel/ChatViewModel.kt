package org.bothubclient.presentation.viewmodel

import androidx.compose.runtime.getValue
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
import kotlin.math.abs
import kotlin.math.roundToInt

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase,
    private val getSystemPromptsUseCase: GetSystemPromptsUseCase,
    private val validateApiKeyUseCase: ValidateApiKeyUseCase,
    private val optimizePromptUseCase: OptimizePromptUseCase
) {
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

    var isInputLocked by mutableStateOf(false)
        private set

    var temperatureText by mutableStateOf("0.7")
        private set

    var temperatureError by mutableStateOf<String?>(null)
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

    val availableModels: List<String> get() = getAvailableModelsUseCase()
    val availablePrompts: List<SystemPrompt> get() = getSystemPromptsUseCase()

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
        temperatureError = if (parseTemperatureOrNull(text) == null) {
            "Введите число от 0 до 2 с шагом 0.1"
        } else {
            null
        }
    }

    fun onInputTextChanged(text: String) {
        if (isInputLocked) return
        inputText = text
    }

    fun onModelSelected(model: String) {
        selectedModel = model
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

            val result = withContext(Dispatchers.IO) {
                optimizePromptUseCase(customPromptText, selectedModel)
            }

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

    fun resetSession() {
        messages = emptyList()
        inputText = ""
        statusMessage = "Готов к работе"
        apiKeyError = null
        selectedPrompt = getSystemPromptsUseCase.getDefault()
        customPromptText = ""
        optimizedPromptText = null
        optimizePromptError = null
        isInputLocked = false
        temperatureText = "0.7"
        temperatureError = null
    }

    fun sendMessage(scope: CoroutineScope) {
        if (inputText.isBlank() || isLoading || isInputLocked) return

        val temperature = parseTemperatureOrNull(temperatureText)
        if (temperature == null) {
            temperatureError = "Введите число от 0 до 2 с шагом 0.1"
            statusMessage = "Ошибка температуры"
            return
        }
        temperatureError = null

        val userMessage = inputText.trim()
        inputText = userMessage
        messages = messages + Message.user(userMessage)
        isInputLocked = true
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

            val result = withContext(Dispatchers.IO) {
                sendMessageUseCase(userMessage, selectedModel, effectivePromptText, temperature)
            }

            when (result) {
                is ChatResult.Success -> {
                    messages = messages + result.message
                    statusMessage = "Готов к работе"
                }

                is ChatResult.Error -> {
                    messages = messages + Message.error("Ошибка: ${result.exception.message}")
                    statusMessage = "Ошибка запроса"
                }
            }
            isLoading = false
        }
    }

    companion object {
        fun create(): ChatViewModel {
            return ChatViewModel(
                sendMessageUseCase = org.bothubclient.infrastructure.di.ServiceLocator.sendMessageUseCase,
                getAvailableModelsUseCase = org.bothubclient.infrastructure.di.ServiceLocator.getAvailableModelsUseCase,
                getSystemPromptsUseCase = org.bothubclient.infrastructure.di.ServiceLocator.getSystemPromptsUseCase,
                validateApiKeyUseCase = org.bothubclient.infrastructure.di.ServiceLocator.validateApiKeyUseCase,
                optimizePromptUseCase = org.bothubclient.infrastructure.di.ServiceLocator.optimizePromptUseCase
            )
        }
    }
}
