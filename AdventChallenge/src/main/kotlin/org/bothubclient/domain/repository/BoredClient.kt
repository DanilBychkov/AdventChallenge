package org.bothubclient.domain.repository

interface BoredClient {
    suspend fun getRandomActivity(): String
}
