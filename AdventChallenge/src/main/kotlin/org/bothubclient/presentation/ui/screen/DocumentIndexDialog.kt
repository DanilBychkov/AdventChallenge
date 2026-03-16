package org.bothubclient.presentation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bothubclient.domain.docindex.ChunkingStrategyType
import org.bothubclient.domain.docindex.IndexingState
import org.bothubclient.presentation.viewmodel.DocumentIndexViewModel
import javax.swing.JFileChooser

@Composable
fun DocumentIndexDialog(
    viewModel: DocumentIndexViewModel,
    coroutineScope: CoroutineScope,
    onClose: () -> Unit
) {
    val dialogState = rememberDialogState(
        width = 600.dp,
        height = 700.dp
    )

    DialogWindow(
        onCloseRequest = {
            viewModel.cancelIndexing()
            onClose()
        },
        state = dialogState,
        title = "Document Indexing",
        resizable = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            DocumentIndexContent(
                viewModel = viewModel,
                coroutineScope = coroutineScope,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun DocumentIndexContent(
    viewModel: DocumentIndexViewModel,
    coroutineScope: CoroutineScope,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isIndexing = viewModel.indexingProgress.state in listOf(
        IndexingState.SCANNING, IndexingState.CHUNKING, IndexingState.EMBEDDING, IndexingState.SAVING
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Document Indexing",
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

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FolderPickerRow(
                selectedPath = viewModel.selectedDirectory,
                onPathSelected = { viewModel.setDirectory(it) },
                enabled = !isIndexing
            )

            StrategySelector(
                selectedStrategy = viewModel.selectedStrategy,
                onStrategySelected = { viewModel.setStrategy(it) },
                enabled = !isIndexing
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.startIndexing(coroutineScope) },
                    enabled = !isIndexing && viewModel.selectedDirectory.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (viewModel.isIndexReady) "Rebuild Index" else "Build Index",
                        color = Color.White
                    )
                }

                if (isIndexing) {
                    OutlinedButton(
                        onClick = { viewModel.cancelIndexing() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }

            if (isIndexing) {
                IndexingProgressSection(viewModel)
            }

            if (viewModel.isIndexReady) {
                IndexStatsSection(viewModel, coroutineScope)
            }

            viewModel.errorMessage?.let { error ->
                ErrorSection(error)
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
private fun FolderPickerRow(
    selectedPath: String,
    onPathSelected: (String) -> Unit,
    enabled: Boolean
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(8.dp),
        elevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Document Folder",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (selectedPath.isBlank()) "No folder selected" else selectedPath,
                    fontSize = 13.sp,
                    color = if (selectedPath.isBlank()) Color.Gray else MaterialTheme.colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                val chooser = JFileChooser().apply {
                                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                    dialogTitle = "Select document folder"
                                }
                                val result = chooser.showOpenDialog(null)
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    chooser.selectedFile.absolutePath
                                } else null
                            }
                            path?.let { onPathSelected(it) }
                        }
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Browse",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Browse", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StrategySelector(
    selectedStrategy: ChunkingStrategyType,
    onStrategySelected: (ChunkingStrategyType) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(8.dp),
        elevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Chunking Strategy",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (selectedStrategy) {
                            ChunkingStrategyType.FIXED_SIZE -> "Fixed Size (512 tokens)"
                            ChunkingStrategyType.STRUCTURAL -> "Structural (by headings)"
                        },
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(onClick = {
                        onStrategySelected(ChunkingStrategyType.FIXED_SIZE)
                        expanded = false
                    }) {
                        Text("Fixed Size (512 tokens)", fontSize = 13.sp)
                    }
                    DropdownMenuItem(onClick = {
                        onStrategySelected(ChunkingStrategyType.STRUCTURAL)
                        expanded = false
                    }) {
                        Text("Structural (by headings)", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexingProgressSection(viewModel: DocumentIndexViewModel) {
    val progress = viewModel.indexingProgress

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Indexing: ${progress.state.name}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            val progressValue = when (progress.state) {
                IndexingState.SCANNING -> -1f
                IndexingState.CHUNKING -> {
                    if (progress.totalFiles > 0) progress.processedFiles.toFloat() / progress.totalFiles else -1f
                }
                IndexingState.EMBEDDING -> {
                    if (progress.totalChunks > 0) progress.processedChunks.toFloat() / progress.totalChunks else -1f
                }
                IndexingState.SAVING -> -1f
                else -> 0f
            }

            if (progressValue < 0) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colors.primary
                )
            } else {
                LinearProgressIndicator(
                    progress = progressValue,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colors.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("Files: ${progress.processedFiles}/${progress.totalFiles}")
                    append(" | Chunks: ${progress.processedChunks}/${progress.totalChunks}")
                    append(" | ${progress.elapsedMs / 1000}s elapsed")
                },
                fontSize = 11.sp,
                color = Color.Gray
            )

            if (progress.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${progress.errors.size} errors",
                    fontSize = 11.sp,
                    color = Color(0xFFFF6B6B)
                )
            }
        }
    }
}

@Composable
private fun IndexStatsSection(viewModel: DocumentIndexViewModel, coroutineScope: CoroutineScope) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.08f),
        shape = RoundedCornerShape(8.dp),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Index Ready",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4CAF50)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Chunks: ${viewModel.indexStats.chunkCount} | Files: ${viewModel.indexStats.fileCount}",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "Created: ${viewModel.indexStats.createdDate}",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Enable in chat",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = viewModel.isIndexEnabled,
                        onCheckedChange = { viewModel.toggleIndexEnabled() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colors.primary,
                            checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.deleteIndex(coroutineScope) },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Index", fontSize = 12.sp, color = Color(0xFFFF6B6B))
                }

                OutlinedButton(
                    onClick = { viewModel.openComparisonDialog() },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compare Strategies", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ErrorSection(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFFFF6B6B).copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = error,
                fontSize = 12.sp,
                color = Color(0xFFFF6B6B)
            )
        }
    }
}
