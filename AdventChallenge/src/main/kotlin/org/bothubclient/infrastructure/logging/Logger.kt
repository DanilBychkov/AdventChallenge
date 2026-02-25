package org.bothubclient.infrastructure.logging

object AppLogger {
    private var debugEnabled = true

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
    }

    private fun timestamp(): String {
        val now = System.currentTimeMillis()
        val ms = now % 1000
        val totalSeconds = (now / 1000) % 60
        val totalMinutes = (now / (1000 * 60)) % 60
        val totalHours = (now / (1000 * 60 * 60)) % 24

        return "${totalHours.toString().padStart(2, '0')}:" +
                "${totalMinutes.toString().padStart(2, '0')}:" +
                "${totalSeconds.toString().padStart(2, '0')}." +
                "${ms.toString().padStart(3, '0')}"
    }

    fun d(tag: String, message: String) {
        if (debugEnabled) {
            println("${timestamp()} [DEBUG][$tag] $message")
        }
    }

    fun i(tag: String, message: String) {
        println("${timestamp()} [INFO][$tag] $message")
    }

    fun w(tag: String, message: String) {
        println("${timestamp()} [WARN][$tag] $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val errorMsg = if (throwable != null) {
            "$message | Exception: ${throwable.javaClass.simpleName}: ${throwable.message}"
        } else {
            message
        }
        println("${timestamp()} [ERROR][$tag] $errorMsg")
    }

    fun tokenInfo(
        tag: String,
        sessionId: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        contextUsagePercent: Float,
        model: String
    ) {
        i(tag, buildString {
            append("Session[$sessionId] | ")
            append("Prompt: $promptTokens | ")
            append("Completion: $completionTokens | ")
            append("Total: $totalTokens | ")
            append("Context: ${formatDecimal(contextUsagePercent.toDouble(), 1)}% | ")
            append("Model: $model")
        })
    }

    fun requestStart(tag: String, sessionId: String, model: String, historySize: Int, messageLength: Int) {
        i(tag, buildString {
            append("→ Request started | ")
            append("Session: $sessionId | ")
            append("Model: $model | ")
            append("History size: $historySize | ")
            append("Message length: $messageLength chars")
        })
    }

    fun requestEnd(tag: String, sessionId: String, responseTimeMs: Long, tokens: Int) {
        i(tag, buildString {
            append("← Request completed | ")
            append("Session: $sessionId | ")
            append("Response time: ${responseTimeMs}ms | ")
            append("Tokens: $tokens")
        })
    }

    fun contextWarning(tag: String, sessionId: String, usagePercent: Float, remainingTokens: Int) {
        w(tag, buildString {
            append("⚠ Context limit approaching | ")
            append("Session: $sessionId | ")
            append("Usage: ${formatDecimal(usagePercent.toDouble(), 1)}% | ")
            append("Remaining: $remainingTokens tokens")
        })
    }

    fun contextCritical(tag: String, sessionId: String, usagePercent: Float) {
        e(tag, buildString {
            append("🚨 Context limit critical! | ")
            append("Session: $sessionId | ")
            append("Usage: ${formatDecimal(usagePercent.toDouble(), 1)}%")
        })
    }

    private fun formatDecimal(value: Double, decimalPlaces: Int): String {
        var multiplier = 1.0
        repeat(decimalPlaces) { multiplier *= 10.0 }
        val rounded = (value * multiplier).toInt() / multiplier
        return rounded.toString()
    }
}
