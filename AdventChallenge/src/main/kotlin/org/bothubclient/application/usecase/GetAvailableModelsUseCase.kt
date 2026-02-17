package org.bothubclient.application.usecase

import org.bothubclient.config.AvailableModels

class GetAvailableModelsUseCase {
    operator fun invoke(): List<String> = AvailableModels.ALL
    fun getDefault(): String = AvailableModels.DEFAULT
}
