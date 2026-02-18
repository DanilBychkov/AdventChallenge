package org.bothubclient.infrastructure.config

import java.util.prefs.Preferences

object PanelSizePreferences {
    private const val PROMPT_PANEL_HEIGHT_KEY = "prompt_panel_height"
    private const val MESSAGES_PANEL_RATIO_KEY = "messages_panel_ratio"

    private const val DEFAULT_PROMPT_HEIGHT = 200
    private const val DEFAULT_MESSAGES_RATIO = 0.5f

    private val prefs: Preferences = Preferences.userNodeForPackage(PanelSizePreferences::class.java)

    var promptPanelHeight: Int
        get() = prefs.getInt(PROMPT_PANEL_HEIGHT_KEY, DEFAULT_PROMPT_HEIGHT)
        set(value) {
            prefs.putInt(PROMPT_PANEL_HEIGHT_KEY, value)
        }

    var messagesPanelRatio: Float
        get() = prefs.getFloat(MESSAGES_PANEL_RATIO_KEY, DEFAULT_MESSAGES_RATIO)
        set(value) {
            prefs.putFloat(MESSAGES_PANEL_RATIO_KEY, value.coerceIn(0.2f, 0.8f))
        }

    fun reset() {
        prefs.remove(PROMPT_PANEL_HEIGHT_KEY)
        prefs.remove(MESSAGES_PANEL_RATIO_KEY)
    }
}
