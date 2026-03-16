package org.bothubclient.presentation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.CoroutineScope
import org.bothubclient.presentation.viewmodel.ComparisonResult
import org.bothubclient.presentation.viewmodel.DocumentIndexViewModel

@Composable
fun StrategyComparisonDialog(
    viewModel: DocumentIndexViewModel,
    coroutineScope: CoroutineScope,
    onClose: () -> Unit
) {
    val dialogState = rememberDialogState(
        width = 600.dp,
        height = 500.dp
    )

    DialogWindow(
        onCloseRequest = onClose,
        state = dialogState,
        title = "Strategy Comparison",
        resizable = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            StrategyComparisonContent(
                viewModel = viewModel,
                coroutineScope = coroutineScope,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun StrategyComparisonContent(
    viewModel: DocumentIndexViewModel,
    coroutineScope: CoroutineScope,
    onClose: () -> Unit
) {
    var testQueriesText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Strategy Comparison",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colors.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Test Queries (one per line)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = testQueriesText,
            onValueChange = { testQueriesText = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("Enter test queries, one per line", color = Color.Gray) },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = MaterialTheme.colors.onSurface,
                backgroundColor = MaterialTheme.colors.surface,
                focusedBorderColor = MaterialTheme.colors.primary,
                unfocusedBorderColor = Color.Gray
            ),
            shape = RoundedCornerShape(8.dp),
            enabled = !viewModel.isComparing
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val queries = testQueriesText.lines().filter { it.isNotBlank() }
                viewModel.startComparison(coroutineScope, queries)
            },
            enabled = !viewModel.isComparing && testQueriesText.isNotBlank(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary)
        ) {
            if (viewModel.isComparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Running...", color = Color.White)
            } else {
                Text("Run Comparison", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.comparisonResults.isNotEmpty()) {
            ComparisonResultsTable(
                results = viewModel.comparisonResults,
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Enter queries and run comparison to see results",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClose) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun ComparisonResultsTable(
    results: List<ComparisonResult>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Strategy", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), color = MaterialTheme.colors.onSurface)
            Text("Chunks", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), color = MaterialTheme.colors.onSurface)
            Text("Avg Tokens", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
            Text("Avg Sim", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), color = MaterialTheme.colors.onSurface)
            Text("Time", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), color = MaterialTheme.colors.onSurface)
        }

        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))

        for (result in results) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(result.strategyName, fontSize = 11.sp, modifier = Modifier.weight(1.2f), color = MaterialTheme.colors.onSurface)
                Text("${result.chunkCount}", fontSize = 11.sp, modifier = Modifier.weight(0.8f), color = MaterialTheme.colors.onSurface)
                Text("${result.avgTokens}", fontSize = 11.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colors.onSurface)
                Text("%.3f".format(result.avgSimilarity), fontSize = 11.sp, modifier = Modifier.weight(0.8f), color = MaterialTheme.colors.onSurface)
                Text("${result.timeMs}ms", fontSize = 11.sp, modifier = Modifier.weight(0.8f), color = MaterialTheme.colors.onSurface)
            }
            Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
        }
    }
}
