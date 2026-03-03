package org.bothubclient.domain.context

import org.bothubclient.domain.entity.*

interface ContextComposer {
    fun compose(
        sessionId: String,
        systemPrompt: String,
        userMessage: String,
        historyMessages: List<Message>,
        facts: Map<WmCategory, Map<String, FactEntry>>,
        config: ContextConfig
    ): ComposedContext

    fun estimateTokens(context: ComposedContext): Int

    fun fitsInContextLimit(context: ComposedContext, model: String): Boolean
}
