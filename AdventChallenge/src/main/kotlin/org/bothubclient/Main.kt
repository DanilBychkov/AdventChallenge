package org.bothubclient

import androidx.compose.runtime.remember
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
        width = 600.dp,
        height = 700.dp
    )

    val viewModel = remember { ChatViewModel.create() }
    val coroutineScope = rememberCoroutineScope()

    Window(
        onCloseRequest = {
            ServiceLocator.close()
            exitApplication()
        },
        state = windowState,
        title = "Bothub Client - ${viewModel.selectedModel} - ${viewModel.selectedPrompt.name}"
    ) {
        ChatScreen(
            viewModel = viewModel,
            coroutineScope = coroutineScope
        )
    }
}
