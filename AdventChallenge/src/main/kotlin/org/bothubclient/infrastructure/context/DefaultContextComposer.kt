package org.bothubclient.infrastructure.context

import org.bothubclient.config.ModelContextLimits
import org.bothubclient.domain.context.ContextComposer
import org.bothubclient.domain.context.SummaryStorage
import org.bothubclient.domain.entity.ComposedContext
import org.bothubclient.domain.entity.ContextConfig
import org.bothubclient.domain.entity.ContextStrategy
import org.bothubclient.domain.entity.Message
import org.bothubclient.infrastructure.logging.AppLogger
import org.bothubclient.infrastructure.logging.FileLogger

class DefaultContextComposer(private val summaryStorage: SummaryStorage) : ContextComposer {

    companion object {
        private const val TAG = "DefaultContextComposer"
    }

    private fun log(message: String) = FileLogger.log(TAG, message)

    override fun compose(
        sessionId: String,
        systemPrompt: String,
        userMessage: String,
        historyMessages: List<Message>,
        facts: Map<String, Map<String, String>>,
        config: ContextConfig
    ): ComposedContext {
        log(
            "compose: sessionId=$sessionId, strategy=${config.strategy}, keepLastN=${config.keepLastN}"
        )

        val summaryBlocks = summaryStorage.getBlocks(sessionId)
        log("Summary blocks from storage: ${summaryBlocks.size}")

        val recentMessages =
            when (config.strategy) {
                ContextStrategy.SLIDING_WINDOW,
                ContextStrategy.STICKY_FACTS,
                ContextStrategy.BRANCHING -> {
                    if (config.keepLastN > 0 && historyMessages.size > config.keepLastN) {
                        val recent = historyMessages.takeLast(config.keepLastN)
                        log(
                            "Taking last ${config.keepLastN} messages (total: ${historyMessages.size})"
                        )
                        recent
                    } else {
                        log("Using all messages (size: ${historyMessages.size})")
                        historyMessages
                    }
                }
            }

        val factsForContext =
            if (config.enableFactsMemory &&
                (config.strategy == ContextStrategy.STICKY_FACTS ||
                        config.strategy == ContextStrategy.BRANCHING)
            ) {
                facts
            } else {
                emptyMap()
            }

        val context =
            ComposedContext(
                systemPrompt = systemPrompt,
                summaryBlocks = summaryBlocks,
                facts = factsForContext,
                recentMessages = recentMessages,
                userMessage = userMessage,
                includeAgentPrimer = config.includeAgentPrimer
            )

        log(
            "Composed: ${summaryBlocks.size} summaries + ${recentMessages.size} recent = ~${context.totalEstimatedTokens} tokens"
        )

        if (summaryBlocks.isNotEmpty()) {
            summaryBlocks.forEachIndexed { index, block ->
                log(
                    "  Block[$index]: ${block.originalMessageCount} msgs -> ${block.estimatedTokens} tokens"
                )
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
