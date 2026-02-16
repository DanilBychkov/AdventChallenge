package lesson1

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Константы для работы с Bothub API.
 */
object BothubConfig {
    const val API_URL = "https://bothub.chat/api/v2/openai/v1/chat/completions"
    const val MODEL_NAME = "grok-4.1-fast"
    const val ENV_API_KEY = "BOTHUB_API_KEY"
}

/**
 * Структура сообщения для OpenAI-совместимого API.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

/**
 * Структура запроса к OpenAI-совместимому API.
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 150,
    val temperature: Double = 0.7
)

/**
 * Структура ответа от OpenAI-совместимого API.
 */
@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<ChatChoice>? = null,
    val error: ChatError? = null
)

/**
 * Структура выбора в ответе.
 */
@Serializable
data class ChatChoice(
    val index: Int? = null,
    val message: ChatMessage? = null,
    val finish_reason: String? = null
)

/**
 * Структура ошибки от API.
 */
@Serializable
data class ChatError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

/**
 * Получает API-ключ из переменной окружения BOTHUB_API_KEY.
 * Сначала проверяет переменную процесса, затем читает из реестра Windows (пользовательский уровень).
 *
 * @return API-ключ
 * @throws IllegalStateException если переменная окружения не задана
 */
fun getApiKey(): String {
    // Сначала проверяем переменную окружения процесса
    var apiKey = System.getenv(BothubConfig.ENV_API_KEY)

    // Если не найдена, пробуем получить из реестра Windows (пользовательский уровень)
    if (apiKey.isNullOrBlank()) {
        try {
            val userEnv = System.getenv("USERPROFILE")
            if (!userEnv.isNullOrBlank()) {
                // Читаем через PowerShell из реестра
                val process = ProcessBuilder(
                    "powershell.exe",
                    "-Command",
                    "[Environment]::GetEnvironmentVariable('${BothubConfig.ENV_API_KEY}', 'User')"
                ).start()
                apiKey = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
            }
        } catch (e: Exception) {
            // Игнорируем ошибки при чтении из реестра
        }
    }

    if (apiKey.isNullOrBlank()) {
        throw IllegalStateException(
            """
            |Ошибка: переменная окружения ${BothubConfig.ENV_API_KEY} не задана.
            |Пожалуйста, установите её перед запуском:
            |  Windows (PowerShell): `$${BothubConfig.ENV_API_KEY}='ваш_ключ'
            |  Windows (CMD): set ${BothubConfig.ENV_API_KEY}=ваш_ключ
            |  Linux/Mac: export ${BothubConfig.ENV_API_KEY}='ваш_ключ'
            |API-ключ можно получить на https://bothub.chat/profile/for-developers
            """.trimMargin()
        )
    }

    return apiKey
}

/**
 * Создаёт HTTP-клиент с настройками для работы с Bothub API.
 */
fun createHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}

/**
 * Выполняет синхронный запрос к Bothub API с вопросом пользователя.
 *
 * @param client HTTP-клиент
 * @param apiKey API-ключ Bothub
 * @param userMessage сообщение пользователя
 * @return ответ ассистента
 */
suspend fun sendMessage(
    client: HttpClient,
    apiKey: String,
    userMessage: String
): String {
    val request = ChatRequest(
        model = BothubConfig.MODEL_NAME,
        messages = listOf(
            ChatMessage(
                role = "system",
                content = "Ты полезный ассистент. Отвечай кратко и по делу. На английском языке"
            ),
            ChatMessage(role = "user", content = userMessage)
        ),
        max_tokens = 150,
        temperature = 0.7
    )

    val response: HttpResponse = client.post(BothubConfig.API_URL) {
        headers {
            append(HttpHeaders.Authorization, "Bearer $apiKey")
            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            append(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        setBody(request)
    }

    // Проверяем статус-код
    if (!response.status.isSuccess()) {
        val errorBody = response.bodyAsText()
        throw Exception("HTTP ошибка ${response.status}: $errorBody")
    }

    // Парсим ответ
    val chatResponse = response.body<ChatResponse>()

    // Проверяем на ошибку в ответе
    if (chatResponse.error != null) {
        throw Exception("API ошибка: ${chatResponse.error.message}")
    }

    // Извлекаем ответ ассистента
    val choices = chatResponse.choices
    if (choices.isNullOrEmpty()) {
        return "Не удалось получить ответ от модели."
    }

    val firstChoice = choices.first()
    val message = firstChoice.message
    if (message != null) {
        return message.content
    }

    return "Не удалось получить ответ от модели."
}

/**
 * Основная функция: выполняет запрос к Bothub API и выводит ответ.
 */
fun main() = runBlocking {
    val client = createHttpClient()

    try {
        val apiKey = getApiKey()
        println("Endpoint: ${BothubConfig.API_URL}")
        println("Model: ${BothubConfig.MODEL_NAME}\n")

        val userMessage = "How are you?"
        val response = sendMessage(client, apiKey, userMessage)

        println(response)
        println("-".repeat(50))
    } catch (e: IllegalStateException) {
        System.err.println("\n${e.message}")
    } catch (e: Exception) {
        System.err.println("\nОшибка: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}
