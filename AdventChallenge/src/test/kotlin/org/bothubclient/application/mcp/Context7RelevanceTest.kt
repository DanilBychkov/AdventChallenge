package org.bothubclient.application.mcp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class Context7RelevanceTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Посмотри документацию по Kotlinx.coroutines",
            "Show official documentation for ktor client",
            "API lookup for OpenAI responses",
            "How to use this SDK with Kotlin?",
            "Give an example with npm package install",
            "Migration guide from v1 to v2",
            "Latest version of this framework"
        ]
    )
    fun `isContext7Relevant returns true for docs and sdk intents`(message: String) {
        assertTrue(isContext7Relevant(message))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Write a haiku about spring",
            "Summarize this email thread",
            "Translate this sentence to Spanish"
        ]
    )
    fun `isContext7Relevant returns false for unrelated intents`(message: String) {
        assertFalse(isContext7Relevant(message))
    }
}
