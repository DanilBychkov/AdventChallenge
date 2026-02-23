package org.bothubclient.config

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

data class ModelPrice(
    val inputPricePer1M: Double,
    val outputPricePer1M: Double
)

object ModelPricing {

    private val pricingUsd = mapOf(
        "gpt-5.2" to ModelPrice(1.75, 14.0),
        "gpt-4.1" to ModelPrice(2.0, 8.0),
        "gpt-4.1-mini" to ModelPrice(0.4, 1.6),
        "gpt-4o" to ModelPrice(2.5, 10.0),
        "gpt-4o-mini" to ModelPrice(0.15, 0.6),
        "o3" to ModelPrice(10.0, 40.0),
        "o3-mini" to ModelPrice(1.1, 4.4),
        "o4-mini" to ModelPrice(1.1, 4.4),
        "claude-sonnet-4" to ModelPrice(3.0, 15.0),
        "claude-3-5-sonnet" to ModelPrice(3.0, 15.0),
        "gemini-2.5-pro" to ModelPrice(1.25, 10.0),
        "gemini-2.5-flash" to ModelPrice(0.075, 0.3),
        "gemini-2.0-flash" to ModelPrice(0.1, 0.4),
        "gemini-2.0-flash-lite-001" to ModelPrice(0.075, 0.3),
        "deepseek-chat" to ModelPrice(0.14, 0.28),
        "deepseek-reasoner" to ModelPrice(0.55, 2.19),
        "llama-3.3-70b" to ModelPrice(0.6, 0.6),
        "llama-4-scout" to ModelPrice(0.6, 0.6),
        "grok-4.1-fast" to ModelPrice(2.0, 10.0),
        "grok-3" to ModelPrice(3.0, 15.0)
    )

    private val pricingRub = mapOf(
        "gemini-2.0-flash-lite-001" to ModelPrice(8.84, 35.36),
        "grok-4.1-fast" to ModelPrice(23.57, 58.93),
        "gpt-5.2" to ModelPrice(206.25, 1650.0)
    )

    fun calculateCost(model: String, promptTokens: Int, completionTokens: Int): Double? {
        val price = pricingUsd[model] ?: return null
        val inputCost = (promptTokens / 1_000_000.0) * price.inputPricePer1M
        val outputCost = (completionTokens / 1_000_000.0) * price.outputPricePer1M
        return inputCost + outputCost
    }

    fun calculateCostRub(model: String, promptTokens: Int, completionTokens: Int): Double? {
        val price = pricingRub[model] ?: return null
        val inputCost = (promptTokens / 1_000_000.0) * price.inputPricePer1M
        val outputCost = (completionTokens / 1_000_000.0) * price.outputPricePer1M
        return inputCost + outputCost
    }

    fun formatCost(cost: Double): String {
        return if (cost < 0.01) {
            String.format("$%.6f", cost)
        } else if (cost < 1.0) {
            String.format("$%.4f", cost)
        } else {
            String.format("$%.2f", cost)
        }
    }

    fun formatCostRub(cost: Double): String {
        val locale = Locale("ru", "RU")
        val symbols = DecimalFormatSymbols(locale).apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val pattern = if (cost < 1.0) "#,##0.0000" else "#,##0.00"
        val df = DecimalFormat(pattern, symbols)
        return df.format(cost) + " ₽"
    }

    fun getModelPrice(model: String): ModelPrice? = pricingUsd[model]
}
