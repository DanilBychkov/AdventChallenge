package org.bothubclient.application.usecase

import org.bothubclient.config.SystemPrompt
import org.bothubclient.config.SystemPrompts

class GetSystemPromptsUseCase {
    operator fun invoke(): List<SystemPrompt> = SystemPrompts.ALL
    fun getDefault(): SystemPrompt = SystemPrompts.DEFAULT
}
