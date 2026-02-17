package org.bothubclient.domain.repository

interface ApiKeyProvider {
    fun getApiKey(): String
    fun hasApiKey(): Boolean
}
