package org.bothubclient.domain.entity

sealed class ChatResult {
    data class Success(val message: Message) : ChatResult()
    data class Error(val exception: Exception) : ChatResult()
}
