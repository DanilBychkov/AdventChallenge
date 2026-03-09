# MCP — Developer Guide

## Overview

The app supports pluggable MCP (Model Context Protocol) servers. Context7 is the first supported server and is used to fetch up-to-date documentation and code examples. The architecture is generic and allows adding more MCP servers.

## Components

- **Domain**: `McpServerConfig`, `McpHealthStatus`, `McpTransportType`, `McpRegistry` (interface).
- **Application**: `McpRouter`, `McpSelectionResult`, `McpContextOrchestrator`, `Context7Relevance` (`isContext7Relevant`), `McpClient` / `McpFetchResult` / `McpHealthResult`; use cases: `GetMcpServersUseCase`, `UpdateMcpServerUseCase`, `CheckMcpHealthUseCase`.
- **Infrastructure**: `DefaultMcpRegistry`, `FileMcpSettingsStorage`, `DefaultMcpRouter`, `StdioMcpClient`, wiring in `ServiceLocator`.
- **Persistence**: `~/.bothubclient/mcp_servers.json` (list of `McpServerConfig`). Presets (e.g. Context7) are merged with saved overrides by id.
- **Agent integration**: `CompressingChatAgent` takes an optional `McpContextOrchestrator`; before sending, it calls `fetchEnrichedContext(userMessage, sessionId)` and appends the result to the system prompt. On failure it logs and continues without MCP context.

## Routing and orchestration

- See **MCP_ROUTING.md** in the project root for selection rules (enabled/disabled, force, priority).
- `McpContextOrchestrator` uses `McpRouter.selectForRequest(userMessage)` to get `forcedServers` and `optionalServers`. It calls `McpClient.fetchContext` for forced servers first, then for optional servers when `isContext7Relevant(userMessage)` is true. Results are merged and returned as a single string.

## Adding a new MCP server

1. Add a preset in `config/McpPresets.kt` (or extend `getAllPresets()`).
2. Ensure the server has a unique `id`, `command`/`args`/`env` for STDIO (or `url`/`headers` for HTTP when implemented).
3. No code change is required in the router for “optional” usage; for server-specific relevance, extend the relevance logic (e.g. a new helper like `isContext7Relevant`).

## Healthcheck and logging

- `CheckMcpHealthUseCase` uses `McpClient.checkHealth(server)` and updates the server’s `healthStatus` and `lastHealthCheckAt` in storage.
- `StdioMcpClient` logs fetch/health outcomes (e.g. “MCP fetch server=&lt;id&gt; success=&lt;bool&gt; reason=…”).

## Tests

- `DefaultMcpRouterTest`: forced/optional split, metadata, Context7 relevance in result.
- `Context7RelevanceTest`: keyword-based relevance.
- `McpContextOrchestratorTest`: orchestration with forced/optional and failure handling.
