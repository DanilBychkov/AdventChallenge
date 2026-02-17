package org.bothubclient.application.usecase

import org.bothubclient.domain.repository.ApiKeyProvider

class ValidateApiKeyUseCase(
    private val apiKeyProvider: ApiKeyProvider
) {
    operator fun invoke(): Result<String> {
        return try {
            val apiKey = apiKeyProvider.getApiKey()
            if (apiKey.isNotBlank()) {
                Result.success(apiKey)
            } else {
                Result.failure(IllegalStateException("API ключ пуст"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
