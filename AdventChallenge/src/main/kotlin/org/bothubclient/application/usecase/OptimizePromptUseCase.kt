package org.bothubclient.application.usecase

import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.repository.ChatRepository

class OptimizePromptUseCase(
    private val chatRepository: ChatRepository
) {
    private val optimizationSystemPrompt = """Ты эксперт по созданию эффективных системных промптов для LLM.
Твоя задача — улучшить и оптимизировать промпт пользователя, сохраняя его исходный смысл и намерение.

Правила оптимизации:
1. Сохрани основную цель и意图 пользователя
2. Добавь чёткую структуру (если применимо)
3. Убери неоднозначности
4. Добавь конкретные инструкции по формату ответа
5. Сделай промпт более точным и эффективным

Ответь ТОЛЬКО оптимизированным промптом, без объяснений и комментариев."""

    suspend operator fun invoke(
        userPrompt: String,
        model: String
    ): OptimizePromptResult {
        if (userPrompt.isBlank()) {
            return OptimizePromptResult.Error("Промпт не может быть пустым")
        }

        val request = "Оптимизируй следующий промпт:\n\n$userPrompt"

        return when (val result = chatRepository.sendMessage(request, model, optimizationSystemPrompt)) {
            is ChatResult.Success -> OptimizePromptResult.Success(result.message.content)
            is ChatResult.Error -> OptimizePromptResult.Error(result.exception.message ?: "Ошибка оптимизации")
        }
    }
}

sealed class OptimizePromptResult {
    data class Success(val optimizedPrompt: String) : OptimizePromptResult()
    data class Error(val message: String) : OptimizePromptResult()
}
