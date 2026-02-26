package org.bothubclient.infrastructure.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.context.ContextComposer
import org.bothubclient.domain.context.SummaryGenerator
import org.bothubclient.domain.context.SummaryResult
import org.bothubclient.domain.context.SummaryStorage
import org.bothubclient.domain.entity.*
import org.bothubclient.infrastructure.context.DefaultContextComposer
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.logging.FileLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

sealed class CompressionEvent {
    data class Complete(val block: SummaryBlock) : CompressionEvent()
    data class Error(val exception: Exception) : CompressionEvent()
    object NotNeeded : CompressionEvent()
}

class CompressingChatAgent(
    private val delegate: ChatAgent,
    private val summaryGenerator: SummaryGenerator,
    private val summaryStorage: SummaryStorage,
    private val contextComposer: ContextComposer,
    initialConfig: ContextConfig = ContextConfig.DEFAULT
) : ChatAgent {

    private val _config = AtomicReference(initialConfig)
    val config: ContextConfig get() = _config.get()

    private val pendingCompressions = ConcurrentHashMap<String, Boolean>()

    private val _compressionEvents = MutableSharedFlow<CompressionEvent>(replay = 0)
    val compressionEvents: SharedFlow<CompressionEvent> = _compressionEvents.asSharedFlow()

    companion object {
        private const val TAG = "CompressingChatAgent"
    }

    private fun log(message: String) = FileLogger.log(TAG, message)
    private fun section(title: String) = FileLogger.section(title)

    override suspend fun getHistory(sessionId: String): List<Message> =
        delegate.getHistory(sessionId)

    override suspend fun getSessionMessages(sessionId: String): List<Message> =
        delegate.getSessionMessages(sessionId)

    override fun getSessionTokenStatistics(sessionId: String, model: String): SessionTokenStatistics =
        delegate.getSessionTokenStatistics(sessionId, model)

    override fun getTotalHistoryTokens(sessionId: String): Int =
        delegate.getTotalHistoryTokens(sessionId)

    override fun isApproachingContextLimit(sessionId: String, model: String, threshold: Float): Boolean =
        delegate.isApproachingContextLimit(sessionId, model, threshold)

    override suspend fun reset(sessionId: String) {
        delegate.reset(sessionId)
        summaryStorage.clear(sessionId)
        pendingCompressions.remove(sessionId)
        log("Reset session with summaries: $sessionId")
    }

    override fun truncateHistory(sessionId: String, keepLast: Int) {
        delegate.truncateHistory(sessionId, keepLast)
        val currentConfig = config
        val blocks = summaryStorage.getBlocks(sessionId)
        if (blocks.size > currentConfig.maxSummaryBlocks) {
            val toRemove = blocks.take(blocks.size - currentConfig.maxSummaryBlocks)
            toRemove.forEach { summaryStorage.removeBlock(sessionId, it.id) }
        }
    }

    override fun removeOldestMessages(sessionId: String, count: Int): List<Message> {
        return delegate.removeOldestMessages(sessionId, count)
    }

    override suspend fun send(
        sessionId: String,
        userMessage: String,
        model: String,
        systemPrompt: String,
        temperature: Double
    ): ChatResult {
        val currentConfig = config
        section("SEND START")
        log("sessionId=$sessionId, model=$model")
        log("Config: $currentConfig")

        if (currentConfig.enableAutoCompression) {
            log("Auto-compression ENABLED, checking...")
            val compressionResult = tryCompressIfNeeded(sessionId, currentConfig)
            when (compressionResult) {
                is CompressionResult.Success -> {
                    log("SUCCESS Compression: block ${compressionResult.newBlock.id}")
                    _compressionEvents.emit(CompressionEvent.Complete(compressionResult.newBlock))
                }

                is CompressionResult.Partial -> {
                    log("PARTIAL Compression: ${compressionResult.warning}")
                    _compressionEvents.emit(CompressionEvent.Complete(compressionResult.newBlock))
                }

                is CompressionResult.Failed -> {
                    log("FAILED Compression: ${compressionResult.error.message}")
                    _compressionEvents.emit(CompressionEvent.Error(compressionResult.error))
                }

                CompressionResult.NotNeeded -> {
                    log("Compression NOT NEEDED")
                }
            }
        } else {
            log("Auto-compression DISABLED")
        }

        val currentMessages = delegate.getSessionMessages(sessionId)
        log("Current messages count: ${currentMessages.size}")

        if (contextComposer is DefaultContextComposer) {
            contextComposer.updateCachedMessages(sessionId, currentMessages)
        }

        val composedContext = contextComposer.compose(
            sessionId = sessionId,
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            config = config
        )

        log("Composed: ${composedContext.summaryBlocks.size} summaries + ${composedContext.recentMessages.size} recent = ~${composedContext.totalEstimatedTokens} tokens")

        val enhancedSystemPrompt = composedContext.buildSystemPromptWithContext()

        if (composedContext.summaryBlocks.isNotEmpty()) {
            log("Enhanced prompt length: ${enhancedSystemPrompt.length}")
            log("=== FULL SYSTEM PROMPT WITH CONTEXT ===")
            log(enhancedSystemPrompt.take(500))
            if (enhancedSystemPrompt.length > 500) log("... (truncated, total ${enhancedSystemPrompt.length} chars)")
            log("=== END SYSTEM PROMPT ===")
        }

        section("SEND END")

        return delegate.send(
            sessionId = sessionId,
            userMessage = userMessage,
            model = model,
            systemPrompt = enhancedSystemPrompt,
            temperature = temperature
        )
    }

    private suspend fun tryCompressIfNeeded(sessionId: String, currentConfig: ContextConfig): CompressionResult {
        log("tryCompressIfNeeded: checking pending...")
        if (pendingCompressions.putIfAbsent(sessionId, true) != null) {
            log("Compression already in progress for session $sessionId")
            return CompressionResult.NotNeeded
        }

        try {
            val history = delegate.getHistory(sessionId)
            log("History size: ${history.size}, threshold: (historySize - keepLastN=${currentConfig.keepLastN}) >= blockSize=${currentConfig.compressionBlockSize}")

            val shouldCompress = currentConfig.shouldCompress(history.size)
            log("shouldCompress result: $shouldCompress")

            if (!shouldCompress) {
                return CompressionResult.NotNeeded
            }

            log("Compression condition MET! Taking ${currentConfig.compressionBlockSize} oldest messages...")
            val messagesToCompress = history.take(currentConfig.compressionBlockSize)
            if (messagesToCompress.isEmpty()) {
                log("No messages to compress")
                return CompressionResult.NotNeeded
            }

            log("Messages to compress: ${messagesToCompress.size} messages")

            log("Calling LLM to generate summary (maxTokens=${currentConfig.summaryMaxTokens})...")
            val summaryResult = summaryGenerator.generateSummary(
                messages = messagesToCompress,
                maxTokens = currentConfig.summaryMaxTokens
            )

            return when (summaryResult) {
                is SummaryResult.Success -> {
                    log("Summary generated (${summaryResult.block.estimatedTokens} tokens)")

                    log("Removing ${currentConfig.compressionBlockSize} oldest messages from history...")
                    val removedMessages = delegate.removeOldestMessages(sessionId, currentConfig.compressionBlockSize)
                    log("Actually removed ${removedMessages.size} messages")

                    if (removedMessages.size < currentConfig.compressionBlockSize) {
                        AppLogger.w(
                            TAG,
                            "Only removed ${removedMessages.size} messages instead of ${currentConfig.compressionBlockSize}"
                        )
                    }

                    summaryStorage.addBlock(sessionId, summaryResult.block)
                    log("Summary block added to storage")

                    val blocks = summaryStorage.getBlocks(sessionId)
                    log("Total summary blocks: ${blocks.size} (max: ${currentConfig.maxSummaryBlocks})")

                    if (blocks.size > currentConfig.maxSummaryBlocks) {
                        val removedBlock = summaryStorage.removeBlock(sessionId, blocks.first().id)
                        log("Removed oldest summary block: ${removedBlock?.id}")
                    }

                    CompressionResult.Success(summaryResult.block)
                }

                is SummaryResult.Error -> {
                    log("Summary generation ERROR: ${summaryResult.exception.message}")
                    CompressionResult.Failed(summaryResult.exception, messagesToCompress)
                }
            }
        } finally {
            pendingCompressions.remove(sessionId)
            log("Compression lock released for session $sessionId")
        }
    }

    fun updateConfig(newConfig: ContextConfig) {
        _config.set(newConfig)
        log("Config updated: $newConfig")
    }

    fun getSummaryBlocks(sessionId: String): List<SummaryBlock> =
        summaryStorage.getBlocks(sessionId)

    fun getComposedContext(
        sessionId: String,
        systemPrompt: String,
        userMessage: String
    ): ComposedContext = contextComposer.compose(
        sessionId = sessionId,
        systemPrompt = systemPrompt,
        userMessage = userMessage,
        config = config
    )
}
