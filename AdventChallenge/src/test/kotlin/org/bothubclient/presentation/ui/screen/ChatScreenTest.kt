package org.bothubclient.presentation.ui.screen

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.mockk.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.bothubclient.application.usecase.*
import org.bothubclient.config.SystemPrompt
import org.bothubclient.domain.entity.ChatResult
import org.bothubclient.domain.entity.Message
import org.bothubclient.domain.entity.RequestMetrics
import org.bothubclient.domain.entity.SessionTokenStatistics
import org.bothubclient.presentation.viewmodel.ChatViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import kotlin.test.assertEquals
import org.junit.Test as JunitTest

class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sendMessageUseCase: SendMessageUseCase = mockk()
    private val getAvailableModelsUseCase: GetAvailableModelsUseCase = mockk()
    private val getSystemPromptsUseCase: GetSystemPromptsUseCase = mockk()
    private val validateApiKeyUseCase: ValidateApiKeyUseCase = mockk()
    private val optimizePromptUseCase: OptimizePromptUseCase = mockk()
    private val resetChatSessionUseCase: ResetChatSessionUseCase = mockk()
    private val getChatHistoryUseCase: GetChatHistoryUseCase = mockk()
    private val getSessionMessagesUseCase: GetSessionMessagesUseCase = mockk()
    private val getTokenStatisticsUseCase: GetTokenStatisticsUseCase = mockk()

    private lateinit var viewModel: ChatViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        every { getAvailableModelsUseCase() } returns listOf("gpt-4", "gpt-3.5-turbo", "claude-3")
        every { getAvailableModelsUseCase.getDefault() } returns "gpt-4"
        every { getSystemPromptsUseCase() } returns listOf(
            SystemPrompt("Default", "You are a helpful assistant", false),
            SystemPrompt("Code Expert", "You are a coding expert", false),
            SystemPrompt("Custom", "", true)
        )
        every { getSystemPromptsUseCase.getDefault() } returns
                SystemPrompt("Default", "You are a helpful assistant", false)
        every { validateApiKeyUseCase() } returns Result.success("test-api-key")
        coEvery { getChatHistoryUseCase() } returns emptyList()
        coEvery { getSessionMessagesUseCase() } returns emptyList()
        coEvery { resetChatSessionUseCase() } just Runs
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

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @JunitTest
    fun should_display_header_title() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNodeWithText("Bothub Chat Client")
            .assertIsDisplayed()
    }

    @JunitTest
    fun should_display_model_selector() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNodeWithText("Model")
            .assertIsDisplayed()
    }

    @JunitTest
    fun should_display_system_prompt_selector() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNodeWithText("System prompt")
            .assertIsDisplayed()
    }

    @JunitTest
    fun should_display_temperature_field_when_no_messages() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNodeWithText("Температура")
            .assertIsDisplayed()
    }

    @JunitTest
    fun should_display_empty_state_message() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNodeWithText("Введите сообщение для начала общения")
            .assertIsDisplayed()
    }

    @JunitTest
    fun should_display_input_field() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNode(hasSetTextAction())
            .assertIsDisplayed()
    }

    @JunitTest
    fun should_display_send_button_as_disabled_when_input_is_empty() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Отправить")
            .assertIsNotEnabled()
    }

    @JunitTest
    fun typing_in_input_field_updates_viewModel() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNode(hasSetTextAction())
            .performTextInput("Hello, AI!")

        assertEquals("Hello, AI!", viewModel.inputText)
    }

    @JunitTest
    fun send_button_becomes_enabled_when_input_has_text() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNode(hasSetTextAction())
            .performTextInput("Test message")

        composeTestRule
            .onNodeWithContentDescription("Отправить")
            .assertIsEnabled()
    }

    @JunitTest
    fun clicking_model_dropdown_shows_options() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNodeWithText("gpt-4")
            .performClick()

        composeTestRule
            .onNodeWithText("gpt-3.5-turbo")
            .assertIsDisplayed()
    }

    @JunitTest
    fun selecting_model_updates_viewModel() {
        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNodeWithText("gpt-4")
            .performClick()

        composeTestRule
            .onNodeWithText("gpt-3.5-turbo")
            .performClick()

        assertEquals("gpt-3.5-turbo", viewModel.selectedModel)
    }

    @JunitTest
    fun should_display_user_message_after_sending() {
        coEvery {
            sendMessageUseCase(any(), any(), any(), any())
        } returns ChatResult.Success(
            message = Message.assistant("Response"),
            metrics = RequestMetrics()
        )

        composeTestRule.setContent {
            ChatScreen(
                viewModel = viewModel,
                coroutineScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
            )
        }

        composeTestRule
            .onNode(hasSetTextAction())
            .performTextInput("Hello")

        composeTestRule
            .onNodeWithContentDescription("Отправить")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Hello", substring = true)
            .assertIsDisplayed()
    }
}
