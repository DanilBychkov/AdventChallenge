package org.bothubclient.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.bothubclient.presentation.ui.components.ChatPanel
import org.bothubclient.presentation.ui.theme.AppColors
import org.bothubclient.presentation.viewmodel.ChatPanelViewModel

@Composable
fun MultiChatScreen(
    coroutineScope: CoroutineScope
) {
    val viewModels = remember {
        List(4) { ChatPanelViewModel.create() }
    }

    val panelTitles = listOf(
        "LLM #1",
        "LLM #2",
        "LLM #3",
        "LLM #4"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ChatPanel(
                viewModel = viewModels[0],
                coroutineScope = coroutineScope,
                panelTitle = panelTitles[0],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
            )

            ChatPanel(
                viewModel = viewModels[1],
                coroutineScope = coroutineScope,
                panelTitle = panelTitles[1],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            ChatPanel(
                viewModel = viewModels[2],
                coroutineScope = coroutineScope,
                panelTitle = panelTitles[2],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
            )

            ChatPanel(
                viewModel = viewModels[3],
                coroutineScope = coroutineScope,
                panelTitle = panelTitles[3],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp)
            )
        }
    }
}
