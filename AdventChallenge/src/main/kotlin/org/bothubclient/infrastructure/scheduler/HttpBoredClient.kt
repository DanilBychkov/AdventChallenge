package org.bothubclient.infrastructure.scheduler

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bothubclient.domain.repository.BoredClient
import org.bothubclient.infrastructure.logging.AppLogger

@Serializable
private data class BoredActivity(
    val activity: String = ""
)

class HttpBoredClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://bored-api.appbrewery.com"
) : BoredClient {

    companion object {
        private const val TAG = "HttpBoredClient"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun getRandomActivity(): String {
        val response: HttpResponse = client.get("$baseUrl/random")
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("Bored API error ${response.status}: $body")
        }
        val body = response.bodyAsText()
        val activity = json.decodeFromString<BoredActivity>(body)
        AppLogger.i(TAG, "Fetched activity: ${activity.activity}")
        return activity.activity.ifBlank { "Learn something new" }
    }
}
