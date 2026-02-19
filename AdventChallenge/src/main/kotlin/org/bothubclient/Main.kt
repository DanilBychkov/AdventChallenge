package org.bothubclient

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.bothubclient.infrastructure.di.ServiceLocator
import org.bothubclient.presentation.ui.screen.ChatScreen
import org.bothubclient.presentation.viewmodel.ChatViewModel

fun main() = application {
    val windowState = rememberWindowState(
        width = 900.dp,
        height = 700.dp
    )

    val coroutineScope = rememberCoroutineScope()
    val viewModel = ChatViewModel.create()

    Window(
        onCloseRequest = {
            ServiceLocator.close()
            exitApplication()
        },
        state = windowState,
        title = "Bothub Chat Client"
    ) {
        ChatScreen(
            viewModel = viewModel,
            coroutineScope = coroutineScope
        )
    }
}
