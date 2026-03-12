package org.bothubclient.infrastructure.di

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.bothubclient.application.mcp.DefaultMcpRelevanceStrategyRegistry
import org.bothubclient.application.mcp.DefaultMcpRouter
import org.bothubclient.application.mcp.McpContextOrchestrator
import org.bothubclient.application.usecase.*
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.context.SummaryStorage
import org.bothubclient.domain.repository.ApiKeyProvider
import org.bothubclient.domain.repository.ChatHistoryStorage
import org.bothubclient.domain.repository.ChatRepository
import org.bothubclient.domain.repository.UserProfileRepository
import org.bothubclient.infrastructure.agent.AgentBackedChatRepository
import org.bothubclient.infrastructure.agent.BothubChatAgent
import org.bothubclient.infrastructure.agent.CompressingChatAgent
import org.bothubclient.infrastructure.agent.StateMachineAwareAgent
import org.bothubclient.infrastructure.api.BothubChatRepository
import org.bothubclient.infrastructure.config.EnvironmentApiKeyProvider
import org.bothubclient.infrastructure.context.*
import org.bothubclient.infrastructure.logging.FileLogger
import org.bothubclient.infrastructure.mcp.Context7StdioFetchStrategy
import org.bothubclient.infrastructure.mcp.DefaultStdioMcpFetchStrategy
import org.bothubclient.infrastructure.mcp.StdioMcpClient
import org.bothubclient.infrastructure.memory.LtmRecaller
import org.bothubclient.infrastructure.persistence.*
import org.bothubclient.infrastructure.repository.DefaultMcpRegistry
import org.bothubclient.infrastructure.scheduler.BackgroundJobManager
import org.bothubclient.infrastructure.scheduler.HttpBoredClient
import org.bothubclient.infrastructure.scheduler.LlmReportGenerator
import org.bothubclient.infrastructure.scheduler.LlmScheduleIntentExtractor
import org.bothubclient.infrastructure.repository.UserProfileRepository as InfraUserProfileRepository

object ServiceLocator {
    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
        }
    }

    private val apiKeyProvider: ApiKeyProvider by lazy { EnvironmentApiKeyProvider() }

    private val chatHistoryStorage: ChatHistoryStorage by lazy { FileChatHistoryStorage() }

    private val userProfileStorage: UserProfileStorage by lazy { UserProfileStorage() }

    private val taskContextStorage: FileTaskContextStorage by lazy { FileTaskContextStorage() }

    private val infraUserProfileRepository: InfraUserProfileRepository by lazy {
        InfraUserProfileRepository(storage = userProfileStorage)
    }

    val userProfileRepository: UserProfileRepository by lazy { infraUserProfileRepository }

    private val statelessChatRepository: ChatRepository by lazy {
        BothubChatRepository(httpClient) { apiKeyProvider.getApiKey() }
    }

    private val baseChatAgent: ChatAgent by lazy {
        BothubChatAgent(client = httpClient, getApiKey = { apiKeyProvider.getApiKey() })
    }

    private val summaryStorage: SummaryStorage by lazy { ConcurrentSummaryStorage() }

    private val summaryGenerator: LlmSummaryGenerator by lazy {
        LlmSummaryGenerator(client = httpClient, getApiKey = { apiKeyProvider.getApiKey() })
    }

    private val llmFactsExtractor: LlmFactsExtractor by lazy {
        LlmFactsExtractor(client = httpClient, getApiKey = { apiKeyProvider.getApiKey() })
    }

    private val factsExtractor: HeuristicFactsExtractor by lazy {
        HeuristicFactsExtractor(llmFactsExtractor = llmFactsExtractor)
    }

    private val ltmRecaller: LtmRecaller by lazy {
        LtmRecaller(client = httpClient, getApiKey = { apiKeyProvider.getApiKey() })
    }

    private val contextComposer: DefaultContextComposer by lazy {
        DefaultContextComposer(summaryStorage = summaryStorage)
    }

    private val mcpSettingsStorage: FileMcpSettingsStorage by lazy { FileMcpSettingsStorage() }

    private val mcpRegistry: DefaultMcpRegistry by lazy {
        DefaultMcpRegistry(storage = mcpSettingsStorage)
    }

    private val diagnosticsLogger by lazy {
        object : org.bothubclient.domain.logging.Logger {
            override fun log(tag: String, message: String) {
                FileLogger.log(tag, message)
            }
        }
    }

    private val mcpClient: StdioMcpClient by lazy {
        val defaultStrategy = DefaultStdioMcpFetchStrategy()
        val context7Strategy = Context7StdioFetchStrategy()
        StdioMcpClient(
            logger = diagnosticsLogger,
            fetchStrategySelector = { server ->
                if (server.type.equals("context7", ignoreCase = true)) context7Strategy else defaultStrategy
            }
        )
    }

    private val mcpRelevanceRegistry: DefaultMcpRelevanceStrategyRegistry by lazy {
        DefaultMcpRelevanceStrategyRegistry.withDefaults()
    }

    private val mcpRouter: DefaultMcpRouter by lazy {
        DefaultMcpRouter(registry = mcpRegistry, relevanceRegistry = mcpRelevanceRegistry)
    }

    private val mcpContextOrchestrator: McpContextOrchestrator by lazy {
        McpContextOrchestrator(mcpRouter = mcpRouter, mcpClient = mcpClient)
    }

    val compressingChatAgent: CompressingChatAgent by lazy {
        CompressingChatAgent(
            delegate = baseChatAgent,
            summaryGenerator = summaryGenerator,
            summaryStorage = summaryStorage,
            contextComposer = contextComposer,
            factsExtractor = factsExtractor,
            ltmRecaller = ltmRecaller,
            userProfileRepository = infraUserProfileRepository,
            taskContextStorage = taskContextStorage,
            chatHistoryStorage = chatHistoryStorage,
            mcpContextOrchestrator = mcpContextOrchestrator,
            mcpClient = mcpClient,
            mcpRegistry = mcpRegistry
        )
    }

    private val chatAgent: ChatAgent by lazy { StateMachineAwareAgent(compressingChatAgent) }

    private val chatRepository: ChatRepository by lazy {
        AgentBackedChatRepository(agent = chatAgent, sessionId = "chat-ui")
    }

    val sendMessageUseCase: SendMessageUseCase by lazy { SendMessageUseCase(chatRepository) }

    val resetChatSessionUseCase: ResetChatSessionUseCase by lazy {
        ResetChatSessionUseCase(chatRepository)
    }

    val getChatHistoryUseCase: GetChatHistoryUseCase by lazy {
        GetChatHistoryUseCase(chatRepository)
    }

    val getSessionMessagesUseCase: GetSessionMessagesUseCase by lazy {
        GetSessionMessagesUseCase(chatRepository)
    }

    val getAvailableModelsUseCase: GetAvailableModelsUseCase by lazy { GetAvailableModelsUseCase() }

    val getSystemPromptsUseCase: GetSystemPromptsUseCase by lazy { GetSystemPromptsUseCase() }

    val validateApiKeyUseCase: ValidateApiKeyUseCase by lazy {
        ValidateApiKeyUseCase(apiKeyProvider)
    }

    val optimizePromptUseCase: OptimizePromptUseCase by lazy {
        OptimizePromptUseCase(statelessChatRepository)
    }

    val getTokenStatisticsUseCase: GetTokenStatisticsUseCase by lazy {
        GetTokenStatisticsUseCase(chatAgent) { "chat-ui" }
    }

    val getMcpServersUseCase: GetMcpServersUseCase by lazy {
        GetMcpServersUseCase(registry = mcpRegistry)
    }

    val updateMcpServerUseCase: UpdateMcpServerUseCase by lazy {
        UpdateMcpServerUseCase(registry = mcpRegistry)
    }

    val checkMcpHealthUseCase: CheckMcpHealthUseCase by lazy {
        CheckMcpHealthUseCase(
            registry = mcpRegistry,
            mcpClient = mcpClient,
            logger = diagnosticsLogger
        )
    }

    private val jsonBackgroundJobRepository: JsonBackgroundJobRepository by lazy {
        JsonBackgroundJobRepository()
    }

    private val jsonBoredReportRepository: JsonBoredReportRepository by lazy {
        JsonBoredReportRepository()
    }

    private val httpBoredClient: HttpBoredClient by lazy {
        HttpBoredClient(client = httpClient)
    }

    private val llmReportGenerator: LlmReportGenerator by lazy {
        LlmReportGenerator(client = httpClient, getApiKey = { apiKeyProvider.getApiKey() })
    }

    private val llmScheduleIntentExtractor: LlmScheduleIntentExtractor by lazy {
        LlmScheduleIntentExtractor(client = httpClient, getApiKey = { apiKeyProvider.getApiKey() })
    }

    private val backgroundJobManagerScope: kotlinx.coroutines.CoroutineScope by lazy {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
        )
    }

    val backgroundJobManager: BackgroundJobManager by lazy {
        BackgroundJobManager(
            jobRepo = jsonBackgroundJobRepository,
            reportRepo = jsonBoredReportRepository,
            boredClient = httpBoredClient,
            reportGenerator = llmReportGenerator,
            scope = backgroundJobManagerScope
        )
    }

    val configureBackgroundJobUseCase: ConfigureBackgroundJobUseCase by lazy {
        ConfigureBackgroundJobUseCase(jsonBackgroundJobRepository)
    }

    val listBackgroundJobsUseCase: ListBackgroundJobsUseCase by lazy {
        ListBackgroundJobsUseCase(jsonBackgroundJobRepository)
    }

    val toggleBackgroundJobUseCase: ToggleBackgroundJobUseCase by lazy {
        ToggleBackgroundJobUseCase(jsonBackgroundJobRepository)
    }

    val runBackgroundJobNowUseCase: RunBackgroundJobNowUseCase by lazy {
        RunBackgroundJobNowUseCase(
            repository = jsonBackgroundJobRepository,
            jobExecutor = { jobId -> backgroundJobManager.runJobNow(jobId) }
        )
    }

    val listBoredReportsUseCase: ListBoredReportsUseCase by lazy {
        ListBoredReportsUseCase(jsonBoredReportRepository)
    }

    val parseScheduleIntentUseCase: ParseScheduleIntentUseCase by lazy {
        ParseScheduleIntentUseCase(llmExtractor = { msg -> llmScheduleIntentExtractor.extract(msg) })
    }

    fun close() {
        backgroundJobManager.stop()
        httpClient.close()
    }
}
