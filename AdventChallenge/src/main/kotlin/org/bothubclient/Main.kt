package org.bothubclient

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.bothubclient.infrastructure.di.ServiceLocator
import org.bothubclient.presentation.ui.screen.MultiChatScreen

fun main() = application {
    val windowState = rememberWindowState(
        width = 1200.dp,
        height = 800.dp
    )

    val coroutineScope = rememberCoroutineScope()

    Window(
        onCloseRequest = {
            ServiceLocator.close()
            exitApplication()
        },
        state = windowState,
        title = "Bothub Multi-Chat Client"
    ) {
        MultiChatScreen(
            coroutineScope = coroutineScope
        )
    }
}
