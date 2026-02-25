package org.bothubclient.config

object AvailableModels {
    val ALL: List<String> = listOf(
        "gemini-2.0-flash-lite-001",
        "gpt-5.2",
        "grok-4.1-fast",
        "gemma-2-9b-it",
        "grok-3",
        "gpt-4.1",
        "gpt-4.1-mini",
        "gpt-4o",
        "gpt-4o-mini",
        "o3",
        "o3-mini",
        "o4-mini",
        "claude-sonnet-4",
        "claude-3-5-sonnet",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "deepseek-chat",
        "deepseek-reasoner",
        "llama-3.3-70b",
        "llama-3-70b-instruct",
        "llama-4-scout"
    )

    val DEFAULT: String get() = ALL.first()
}
