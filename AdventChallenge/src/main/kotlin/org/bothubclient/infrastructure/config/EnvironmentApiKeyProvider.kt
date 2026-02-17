package org.bothubclient.infrastructure.config

import org.bothubclient.config.ApiConfig
import org.bothubclient.domain.repository.ApiKeyProvider

class EnvironmentApiKeyProvider : ApiKeyProvider {

    override fun getApiKey(): String {
        var apiKey = System.getenv(ApiConfig.ENV_API_KEY)

        if (apiKey.isNullOrBlank()) {
            apiKey = readFromWindowsRegistry()
        }

        if (apiKey.isNullOrBlank()) {
            throw IllegalStateException(createErrorMessage())
        }

        return apiKey
    }

    override fun hasApiKey(): Boolean {
        return try {
            getApiKey().isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun readFromWindowsRegistry(): String? {
        return try {
            val process = ProcessBuilder(
                "powershell.exe",
                "-Command",
                "[Environment]::GetEnvironmentVariable('${ApiConfig.ENV_API_KEY}', 'User')"
            ).start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            result.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun createErrorMessage(): String {
        return """
            |Ошибка: переменная окружения ${ApiConfig.ENV_API_KEY} не задана.
            |Пожалуйста, установите её перед запуском:
            |  Windows (PowerShell): `$${ApiConfig.ENV_API_KEY}='ваш_ключ'
            |  Windows (CMD): set ${ApiConfig.ENV_API_KEY}=ваш_ключ
            |  Linux/Mac: export ${ApiConfig.ENV_API_KEY}='ваш_ключ'
            |API-ключ можно получить на https://bothub.chat/profile/for-developers
        """.trimMargin()
    }
}
