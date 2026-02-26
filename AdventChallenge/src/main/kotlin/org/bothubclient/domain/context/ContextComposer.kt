package org.bothubclient.domain.context

import org.bothubclient.domain.entity.ComposedContext
import org.bothubclient.domain.entity.ContextConfig

interface ContextComposer {
    fun compose(
        sessionId: String,
        systemPrompt: String,
        userMessage: String,
        config: ContextConfig
    ): ComposedContext

    fun estimateTokens(context: ComposedContext): Int

    fun fitsInContextLimit(context: ComposedContext, model: String): Boolean
}
