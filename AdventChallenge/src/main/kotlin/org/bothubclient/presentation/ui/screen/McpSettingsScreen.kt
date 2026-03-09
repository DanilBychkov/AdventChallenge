package org.bothubclient.presentation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.CoroutineScope
import org.bothubclient.domain.entity.McpHealthStatus
import org.bothubclient.domain.entity.McpServerConfig
import org.bothubclient.presentation.viewmodel.McpSettingsViewModel

/**
 * Dialog wrapper for MCP Settings screen.
 */
@Composable
fun McpSettingsDialog(
    viewModel: McpSettingsViewModel,
    coroutineScope: CoroutineScope,
    onClose: () -> Unit
) {
    val dialogState = rememberDialogState(
        width = 700.dp,
        height = 600.dp
    )

    DialogWindow(
        onCloseRequest = onClose,
        state = dialogState,
        title = "MCP Servers",
        resizable = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            McpSettingsScreen(
                viewModel = viewModel,
                coroutineScope = coroutineScope,
                onClose = onClose
            )
        }
    }
}

/**
 * Main content for MCP Settings screen.
 */
@Composable
fun McpSettingsScreen(
    viewModel: McpSettingsViewModel,
    coroutineScope: CoroutineScope,
    onClose: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadServers(this)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MCP Servers",
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

        // Help text
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "About MCP Server Settings",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enabled: Agent may use this server when appropriate for tasks.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Force usage: Agent must try to use this server for relevant tasks.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Error message
        viewModel.errorMessage?.let { error ->
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
                        imageVector = Icons.Default.Build,
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
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Loading indicator
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colors.primary
                )
            }
        } else if (viewModel.servers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No MCP servers configured",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            // Server list
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.servers.forEach { server ->
                    McpServerCard(
                        server = server,
                        isCheckingHealth = viewModel.isCheckingHealth(server.id),
                        onEnabledChange = { enabled ->
                            viewModel.setEnabled(coroutineScope, server.id, enabled)
                        },
                        onForceUsageChange = { force ->
                            viewModel.setForceUsage(coroutineScope, server.id, force)
                        },
                        onCheckHealth = {
                            viewModel.checkHealth(coroutineScope, server.id)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Footer with close button
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
private fun McpServerCard(
    server: McpServerConfig,
    isCheckingHealth: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onForceUsageChange: (Boolean) -> Unit,
    onCheckHealth: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Server name and health status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colors.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = server.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface
                    )
                }
                HealthStatusBadge(status = server.healthStatus)
            }

            // Description
            if (server.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = server.description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Server type and transport
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Type: ${server.type}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Transport: ${server.transportType.name}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Toggles row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Enabled switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Enabled",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = server.enabled,
                        onCheckedChange = onEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colors.primary,
                            checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                        )
                    )
                }

                // Force usage switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Force usage",
                        fontSize = 13.sp,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = server.forceUsage,
                        onCheckedChange = onForceUsageChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colors.secondary,
                            checkedTrackColor = MaterialTheme.colors.secondary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Check connection button
            OutlinedButton(
                onClick = onCheckHealth,
                enabled = !isCheckingHealth,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isCheckingHealth) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking...", fontSize = 12.sp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check connection", fontSize = 12.sp)
                }
            }

            // Last health check time
            server.lastHealthCheckAt?.let { timestamp ->
                Spacer(modifier = Modifier.height(6.dp))
                val timeStr = formatTimestamp(timestamp)
                Text(
                    text = "Last checked: $timeStr",
                    fontSize = 10.sp,
                    color = Color.Gray.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun HealthStatusBadge(status: McpHealthStatus) {
    val (color, icon, label) = when (status) {
        McpHealthStatus.UNKNOWN -> Triple(Color.Gray, Icons.Default.Info, "Unknown")
        McpHealthStatus.ONLINE -> Triple(Color(0xFF4CAF50), Icons.Default.Build, "Online")
        McpHealthStatus.OFFLINE -> Triple(Color(0xFFFF9800), Icons.Default.Close, "Offline")
        McpHealthStatus.ERROR -> Triple(Color(0xFFFF6B6B), Icons.Default.Close, "Error")
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
        else -> {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
            sdf.format(java.util.Date(timestamp))
        }
    }
}
