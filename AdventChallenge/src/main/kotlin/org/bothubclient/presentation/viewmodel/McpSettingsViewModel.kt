package org.bothubclient.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bothubclient.application.usecase.CheckMcpHealthUseCase
import org.bothubclient.application.usecase.GetMcpServersUseCase
import org.bothubclient.application.usecase.UpdateMcpServerUseCase
import org.bothubclient.domain.entity.McpHealthStatus
import org.bothubclient.domain.entity.McpServerConfig

/**
 * ViewModel for managing MCP server settings UI state.
 */
class McpSettingsViewModel(
    private val getMcpServersUseCase: GetMcpServersUseCase,
    private val updateMcpServerUseCase: UpdateMcpServerUseCase,
    private val checkMcpHealthUseCase: CheckMcpHealthUseCase
) {
    var servers by mutableStateOf<List<McpServerConfig>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val checkingHealthIds = mutableStateMapOf<String, Boolean>()

    fun isCheckingHealth(serverId: String): Boolean = checkingHealthIds[serverId] == true

    /**
     * Loads the list of MCP servers from the registry.
     */
    fun loadServers(scope: CoroutineScope) {
        scope.launch {
            isLoading = true
            errorMessage = null
            runCatching { getMcpServersUseCase() }
                .onSuccess { serverList ->
                    servers = serverList
                }
                .onFailure { e ->
                    errorMessage = "Failed to load MCP servers: ${e.message}"
                }
            isLoading = false
        }
    }

    /**
     * Sets the enabled state of a server and persists the change.
     */
    fun setEnabled(scope: CoroutineScope, serverId: String, enabled: Boolean) {
        val server = servers.find { it.id == serverId } ?: return
        val updated = server.withEnabled(enabled)
        updateServer(scope, updated)
    }

    /**
     * Sets the force usage state of a server and persists the change.
     */
    fun setForceUsage(scope: CoroutineScope, serverId: String, forceUsage: Boolean) {
        val server = servers.find { it.id == serverId } ?: return
        val updated = server.copy(forceUsage = forceUsage)
        updateServer(scope, updated)
    }

    private fun updateServer(scope: CoroutineScope, updated: McpServerConfig) {
        scope.launch {
            runCatching { updateMcpServerUseCase(updated) }
                .onSuccess {
                    servers = servers.map { if (it.id == updated.id) updated else it }
                }
                .onFailure { e ->
                    errorMessage = "Failed to update server: ${e.message}"
                }
        }
    }

    /**
     * Triggers a health check for the specified server and refreshes the list.
     */
    fun checkHealth(scope: CoroutineScope, serverId: String) {
        if (checkingHealthIds[serverId] == true) return

        scope.launch {
            checkingHealthIds[serverId] = true
            runCatching { checkMcpHealthUseCase(serverId) }
                .onSuccess { status ->
                    servers = servers.map { server ->
                        if (server.id == serverId) server.withHealthStatus(status) else server
                    }
                }
                .onFailure { e ->
                    errorMessage = "Health check failed: ${e.message}"
                    servers = servers.map { server ->
                        if (server.id == serverId) server.withHealthStatus(McpHealthStatus.ERROR) else server
                    }
                }
            checkingHealthIds[serverId] = false
        }
    }

    companion object {
        fun create(): McpSettingsViewModel {
            return McpSettingsViewModel(
                getMcpServersUseCase = org.bothubclient.infrastructure.di.ServiceLocator.getMcpServersUseCase,
                updateMcpServerUseCase = org.bothubclient.infrastructure.di.ServiceLocator.updateMcpServerUseCase,
                checkMcpHealthUseCase = org.bothubclient.infrastructure.di.ServiceLocator.checkMcpHealthUseCase
            )
        }
    }
}
