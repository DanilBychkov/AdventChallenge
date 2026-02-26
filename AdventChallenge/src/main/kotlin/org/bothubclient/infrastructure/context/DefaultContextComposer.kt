package org.bothubclient.infrastructure.context

import org.bothubclient.config.ModelContextLimits
import org.bothubclient.domain.agent.ChatAgent
import org.bothubclient.domain.context.ContextComposer
import org.bothubclient.domain.context.SummaryStorage
import org.bothubclient.domain.entity.ComposedContext
import org.bothubclient.domain.entity.ContextConfig
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.logging.FileLogger

class DefaultContextComposer(
    private val chatAgent: ChatAgent,
    private val summaryStorage: SummaryStorage
) : ContextComposer {

    companion object {
        private const val TAG = "DefaultContextComposer"
    }

    private fun log(message: String) = FileLogger.log(TAG, message)

    private val cachedMessages = mutableMapOf<String, List<org.bothubclient.domain.entity.Message>>()

    fun updateCachedMessages(sessionId: String, messages: List<org.bothubclient.domain.entity.Message>) {
        cachedMessages[sessionId] = messages
        log("Updated cached messages for session $sessionId: ${messages.size} messages")
    }

    override fun compose(
        sessionId: String,
        systemPrompt: String,
        userMessage: String,
        config: ContextConfig
    ): ComposedContext {
        log("compose: sessionId=$sessionId, keepLastN=${config.keepLastN}")

        val allMessages = cachedMessages[sessionId].orEmpty()
        log("Cached messages: ${allMessages.size}")

        val summaryBlocks = summaryStorage.getBlocks(sessionId)
        log("Summary blocks from storage: ${summaryBlocks.size}")

        val recentMessages = if (config.keepLastN > 0 && allMessages.size > config.keepLastN) {
            val recent = allMessages.takeLast(config.keepLastN)
            log("Taking last ${config.keepLastN} messages (total: ${allMessages.size})")
            recent
        } else {
            log("Using all messages (size: ${allMessages.size})")
            allMessages
        }

        val context = ComposedContext(
            systemPrompt = systemPrompt,
            summaryBlocks = summaryBlocks,
            recentMessages = recentMessages,
            userMessage = userMessage
        )

        log("Composed: ${summaryBlocks.size} summaries + ${recentMessages.size} recent = ~${context.totalEstimatedTokens} tokens")

        if (summaryBlocks.isNotEmpty()) {
            summaryBlocks.forEachIndexed { index, block ->
                log("  Block[$index]: ${block.originalMessageCount} msgs -> ${block.estimatedTokens} tokens")
            }
        }

        AppLogger.i(
            TAG,
            "Composed context: ${summaryBlocks.size} summaries, ${recentMessages.size} recent messages, ~${context.totalEstimatedTokens} tokens"
        )

        return context
    }

    override fun estimateTokens(context: ComposedContext): Int = context.totalEstimatedTokens

    override fun fitsInContextLimit(context: ComposedContext, model: String): Boolean {
        val tokens = estimateTokens(context)
        val limit = ModelContextLimits.getContextLimit(model)
        return tokens < limit
    }
}
