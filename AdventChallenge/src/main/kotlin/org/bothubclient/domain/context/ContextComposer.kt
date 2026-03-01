package org.bothubclient.domain.context

import org.bothubclient.domain.entity.ComposedContext
import org.bothubclient.domain.entity.ContextConfig
import org.bothubclient.domain.entity.Message

interface ContextComposer {
    fun compose(
        sessionId: String,
        systemPrompt: String,
        userMessage: String,
        historyMessages: List<Message>,
        facts: Map<String, Map<String, String>>,
        config: ContextConfig
    ): ComposedContext

    fun estimateTokens(context: ComposedContext): Int

    fun fitsInContextLimit(context: ComposedContext, model: String): Boolean
}
