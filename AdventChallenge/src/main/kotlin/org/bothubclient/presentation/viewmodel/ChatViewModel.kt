package org.bothubclient.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bothubclient.application.usecase.GetAvailableModelsUseCase
import org.bothubclient.application.usecase.GetSystemPromptsUseCase
import org.bothubclient.application.usecase.SendMessageUseCase
import org.bothubclient.application.usecase.ValidateApiKeyUseCase
import org.bothubclient.config.SystemPrompt
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message

class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase,
    private val getSystemPromptsUseCase: GetSystemPromptsUseCase,
    private val validateApiKeyUseCase: ValidateApiKeyUseCase
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

    val availableModels: List<String> get() = getAvailableModelsUseCase()
    val availablePrompts: List<SystemPrompt> get() = getSystemPromptsUseCase()

    fun onInputTextChanged(text: String) {
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

    fun sendMessage(scope: CoroutineScope) {
        if (inputText.isBlank() || isLoading) return

        val userMessage = inputText.trim()
        inputText = ""
        messages = messages + Message.user(userMessage)
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
                sendMessageUseCase(userMessage, selectedModel, selectedPrompt.text)
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
                validateApiKeyUseCase = org.bothubclient.infrastructure.di.ServiceLocator.validateApiKeyUseCase
            )
        }
    }
}
