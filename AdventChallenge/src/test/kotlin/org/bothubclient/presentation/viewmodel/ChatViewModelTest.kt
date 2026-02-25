package org.bothubclient.presentation.viewmodel

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.bothubclient.application.usecase.*
import org.bothubclient.config.SystemPrompt
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.RequestMetrics
import org.bothubclient.domain.entity.SessionTokenStatistics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*
import org.junit.jupiter.api.Test as JunitTest

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var viewModel: ChatViewModel

    private val sendMessageUseCase: SendMessageUseCase = mockk()
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase = mockk()
    private val getSystemPromptsUseCase: GetSystemPromptsUseCase = mockk()
    private val validateApiKeyUseCase: ValidateApiKeyUseCase = mockk()
    private val optimizePromptUseCase: OptimizePromptUseCase = mockk()
    private val resetChatSessionUseCase: ResetChatSessionUseCase = mockk()
    private val getChatHistoryUseCase: GetChatHistoryUseCase = mockk()
    private val getSessionMessagesUseCase: GetSessionMessagesUseCase = mockk()
    private val getTokenStatisticsUseCase: GetTokenStatisticsUseCase = mockk()

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { getAvailableModelsUseCase() } returns listOf("gpt-4", "gpt-3.5-turbo")
        every { getAvailableModelsUseCase.getDefault() } returns "gpt-4"
        every { getSystemPromptsUseCase() } returns listOf(
            SystemPrompt("Default", "You are helpful", false),
            SystemPrompt("Custom", "", true)
        )
        every { getSystemPromptsUseCase.getDefault() } returns SystemPrompt("Default", "You are helpful", false)
        every { validateApiKeyUseCase() } returns Result.success("test-api-key")
        coEvery { getChatHistoryUseCase() } returns emptyList()
        coEvery { getSessionMessagesUseCase() } returns emptyList()
        every { getTokenStatisticsUseCase(any()) } returns SessionTokenStatistics.EMPTY

        viewModel = ChatViewModel(
            sendMessageUseCase = sendMessageUseCase,
            getAvailableModelsUseCase = getAvailableModelsUseCase,
            getSystemPromptsUseCase = getSystemPromptsUseCase,
            validateApiKeyUseCase = validateApiKeyUseCase,
            optimizePromptUseCase = optimizePromptUseCase,
            resetChatSessionUseCase = resetChatSessionUseCase,
            getChatHistoryUseCase = getChatHistoryUseCase,
            getSessionMessagesUseCase = getSessionMessagesUseCase,
            getTokenStatisticsUseCase = getTokenStatisticsUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @JunitTest
    fun loadHistory_should_load_session_messages_from_storage() = runTest {
        val savedMessages = listOf(
            Message.user("Previous question"),
            Message.assistant("Previous answer")
        )
        coEvery { getSessionMessagesUseCase() } returns savedMessages

        viewModel.loadHistory(this)
        advanceUntilIdle()

        assertEquals(2, viewModel.messages.size)
        assertEquals("Previous question", viewModel.messages[0].content)
        assertEquals("Previous answer", viewModel.messages[1].content)
        assertTrue(viewModel.isHistoryLoaded)
    }

    @JunitTest
    fun loadHistory_should_not_load_twice() = runTest {
        val messages = listOf(Message.user("Test"))
        coEvery { getSessionMessagesUseCase() } returns messages

        viewModel.loadHistory(this)
        advanceUntilIdle()
        viewModel.loadHistory(this)
        advanceUntilIdle()

        coVerify(exactly = 1) { getSessionMessagesUseCase() }
    }

    @JunitTest
    fun loadHistory_should_handle_empty_history() = runTest {
        coEvery { getSessionMessagesUseCase() } returns emptyList()

        viewModel.loadHistory(this)
        advanceUntilIdle()

        assertTrue(viewModel.messages.isEmpty())
        assertEquals("Готов к работе", viewModel.statusMessage)
    }

    @JunitTest
    fun sendMessage_should_add_user_message_and_call_useCase() = runTest {
        val userMessage = "Hello, AI!"
        val assistantResponse = "Hello, human!"
        viewModel.onInputTextChanged(userMessage)

        coEvery { sendMessageUseCase(any(), any(), any(), any()) } returns
                ChatResult.Success(
                    message = Message.assistant(assistantResponse),
                    metrics = RequestMetrics(promptTokens = 10, completionTokens = 5, totalTokens = 15)
                )

        viewModel.sendMessage(this)
        advanceUntilIdle()

        assertEquals(2, viewModel.messages.size)
        assertEquals(Message.user(userMessage).content, viewModel.messages[0].content)
        assertEquals(assistantResponse, viewModel.messages[1].content)
        assertTrue(viewModel.inputText.isBlank())
        assertFalse(viewModel.isLoading)
    }

    @JunitTest
    fun sendMessage_should_not_send_empty_message() = runTest {
        viewModel.onInputTextChanged("   ")

        viewModel.sendMessage(this)
        advanceUntilIdle()

        coVerify(exactly = 0) { sendMessageUseCase(any(), any(), any(), any()) }
        assertTrue(viewModel.messages.isEmpty())
    }

    @JunitTest
    fun sendMessage_should_handle_API_error() = runTest {
        viewModel.onInputTextChanged("Test message")
        coEvery { sendMessageUseCase(any(), any(), any(), any()) } returns
                ChatResult.Error(exception = RuntimeException("API Error"))

        viewModel.sendMessage(this)
        advanceUntilIdle()

        assertEquals(2, viewModel.messages.size)
        assertEquals(Message.user("Test message").content, viewModel.messages[0].content)
        assertTrue(viewModel.messages[1].content.contains("Ошибка"))
        assertTrue(viewModel.statusMessage.contains("Ошибка"))
        assertFalse(viewModel.isLoading)
    }

    @JunitTest
    fun sendMessage_should_handle_invalid_API_key() = runTest {
        viewModel.onInputTextChanged("Test")
        every { validateApiKeyUseCase() } returns Result.failure(IllegalStateException("Invalid API key"))

        viewModel.sendMessage(this)
        advanceUntilIdle()

        assertNotNull(viewModel.apiKeyError)
        assertTrue(viewModel.apiKeyError!!.contains("Invalid API key"))
        coVerify(exactly = 0) { sendMessageUseCase(any(), any(), any(), any()) }
    }

    @JunitTest
    fun sendMessage_should_include_metrics_in_response() = runTest {
        viewModel.onInputTextChanged("Test")
        val metrics = RequestMetrics(
            promptTokens = 100,
            completionTokens = 50,
            totalTokens = 150,
            responseTimeMs = 2000
        )
        coEvery { sendMessageUseCase(any(), any(), any(), any()) } returns
                ChatResult.Success(message = Message.assistant("Response"), metrics = metrics)

        viewModel.sendMessage(this)
        advanceUntilIdle()

        val assistantMessage = viewModel.messages.last()
        assertNotNull(assistantMessage.metrics)
        assertEquals(100, assistantMessage.metrics!!.promptTokens)
        assertEquals(50, assistantMessage.metrics!!.completionTokens)
    }

    @JunitTest
    fun onTemperatureTextChanged_should_accept_valid_values() {
        viewModel.onTemperatureTextChanged("0.5")
        assertNull(viewModel.temperatureError)
        assertEquals("0.5", viewModel.temperatureText)

        viewModel.onTemperatureTextChanged("1.0")
        assertNull(viewModel.temperatureError)

        viewModel.onTemperatureTextChanged("2.0")
        assertNull(viewModel.temperatureError)
    }

    @JunitTest
    fun onTemperatureTextChanged_should_reject_invalid_values() {
        viewModel.onTemperatureTextChanged("2.5")
        assertNotNull(viewModel.temperatureError)

        viewModel.onTemperatureTextChanged("-0.1")
        assertNotNull(viewModel.temperatureError)

        viewModel.onTemperatureTextChanged("abc")
        assertNotNull(viewModel.temperatureError)
    }

    @JunitTest
    fun onTemperatureTextChanged_should_accept_comma_as_decimal_separator() {
        viewModel.onTemperatureTextChanged("0,7")
        assertNull(viewModel.temperatureError)
        assertEquals("0,7", viewModel.temperatureText)
    }

    @JunitTest
    fun onModelSelected_should_update_selected_model() {
        viewModel.onModelSelected("gpt-3.5-turbo")
        assertEquals("gpt-3.5-turbo", viewModel.selectedModel)
    }

    @JunitTest
    fun resetSession_should_call_useCase() = runTest {
        coEvery { resetChatSessionUseCase() } just Runs

        viewModel.resetSession(this)
        advanceUntilIdle()

        coVerify { resetChatSessionUseCase() }
    }

    @JunitTest
    fun onInputTextChanged_should_update_input_text() {
        viewModel.onInputTextChanged("New input")
        assertEquals("New input", viewModel.inputText)
    }

    @JunitTest
    fun effectivePromptText_should_return_selected_prompt_text_for_non_custom() {
        assertEquals("You are helpful", viewModel.effectivePromptText)
    }

    @JunitTest
    fun tokenStatistics_should_be_initialized() {
        assertEquals(SessionTokenStatistics.EMPTY, viewModel.tokenStatistics)
    }
}
