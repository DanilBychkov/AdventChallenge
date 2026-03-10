# MCP — Developer Guide

## Overview

The app supports pluggable MCP (Model Context Protocol) servers. Architecture is aligned
with [mcp-agents-architecture](https://github.com/datmt/mcp-agents-architecture) (**Single Agent + Multiple MCP Servers
**). See **docs/MCP_ARCHITECTURE.md** for the system diagram, lifecycle, and data flow.

MCP is a protocol between the **host (this app)** and **MCP servers**; the LLM does not talk to MCP directly. For the
agent to use MCP, the host must: (1) connect and run **initialize**, (2) perform **discovery** (tools/list,
resources/list, prompts/list), (3) pass discovery and behavioral **instructions** into the agent harness. Context7 is
the first supported server (documentation and code examples).

## How MCP is exposed to the agent

Mental model: **LLM ↔ agent harness / host ↔ MCP client ↔ MCP server.**

- **Discovery**: After selecting servers, the host calls `McpClient.discover(server)` for each: one session per server
  with **tools/list**, **resources/list**, and **prompts/list** (resources/prompts are optional; unsupported methods are
  ignored). Results are summarized as "Available MCP tools (discovery)" with tools, resources, and prompts per server (
  name, description, params/URI).
- **Content**: The host calls `McpClient.fetchContext(server, userMessage)` to pre-fetch context (host invokes MCP tools
  internally and gets text). This is injected as the "--- MCP context ---" block.
- **Instruction**: The system prompt includes behavioral rules: prefer MCP context over guessing, use it for
  docs/APIs/libraries, do not invent MCP outputs. So the model *sees* what tools/resources/prompts exist (discovery) and
  *gets* pre-fetched results (content), and knows *when* to use them (rules).

Full model-controlled tool calling (LLM returns tool_call, host executes via MCP) would require the chat API to support
tools and the host to map MCP tools to API tool definitions; currently the host does tool invocation and injects
results.

## Components

- **Domain**: `McpServerConfig`, `McpHealthStatus`, `McpTransportType`, `McpRegistry` (interface).
- **Application**: `McpRouter`, `McpSelectionResult`, `McpContextOrchestrator`, `McpEnrichedContext` (discovery +
  content), `McpDiscoveryResult` / `McpToolInfo` / `McpResourceInfo` / `McpPromptInfo`, `McpServerCapabilities`,
  `McpRelevanceStrategy`, `McpRelevanceResult`, `McpRelevanceStrategyRegistry`, `Context7RelevanceStrategy`,
  `BoredApiRelevanceStrategy`, `FallbackRelevanceStrategy`, `Context7Relevance`, `McpClient` / `McpFetchResult` / `McpHealthResult`; use cases:
  `GetMcpServersUseCase`, `UpdateMcpServerUseCase`, `CheckMcpHealthUseCase`.
- **Infrastructure**: `DefaultMcpRegistry`, `DefaultMcpRelevanceStrategyRegistry`, `FileMcpSettingsStorage`,
  `DefaultMcpRouter`, `StdioMcpClient`, `StdioMcpFetchStrategy` / `Context7StdioFetchStrategy` / `DefaultStdioMcpFetchStrategy` (discover; fetchContext via strategy; checkHealth), wiring in `ServiceLocator`.
- **Persistence**: `~/.bothubclient/mcp_servers.json`. Presets (e.g. Context7) are merged with saved overrides by id.
- **Context7**: Uses the API search method (resolve-library-id → GET /api/v2/libs/search with libraryName + query
  per [API Guide](https://context7.com/docs/api-guide)), then get-library-docs for context. No LLM normalization; the
  search API returns the library ID. Context7-specific tool ordering and argument building live in `Context7StdioFetchStrategy`.
- **Bored API**: Local MCP server in `mcp-servers/bored-api-mcp` (tools: get-random-activity, find-activity). Preset id `bored-api`, type `bored-api`; use `workingDirectory` so the process runs from that folder (e.g. `node dist/index.js`). Relevance: `BoredApiRelevanceStrategy` (keywords: activity, ideas, bored, suggestion, etc.).
- **Agent integration**: `CompressingChatAgent` calls `orchestrator.fetchEnrichedContext(...)` which returns
  `McpEnrichedContext(discoverySummary, content)`. It injects into the system prompt: (1) MCP behavior rules, (2) "---
  Available MCP tools (discovery) ---" + discoverySummary (tools, resources, prompts per server), (3) "--- MCP
  context ---" + content when non-empty.

## Routing and orchestration

- See **MCP_ROUTING.md** for selection rules (enabled/disabled, force, priority, **per-server relevance**).
- The **router** filters optional servers using `McpRelevanceStrategyRegistry`: each optional server’s
  `McpRelevanceStrategy` (by server type or id) decides whether the server is relevant for the user message; unknown
  types use a fallback strategy (default: not relevant).
- `McpContextOrchestrator.fetchEnrichedContext`: calls router → **discovers** each selected server → **fetches** context
  from forced then optional servers (no extra relevance gate; orchestrator trusts router).

## Adding a new MCP server

1. Add a preset in `config/McpPresets.kt` (or extend `getAllPresets()`).
2. Ensure the server has a unique `id`, `type`, and `command`/`args`/`env` for STDIO (or `url`/`headers` for HTTP when
   implemented).
3. **Relevance (optional servers):** implement `McpRelevanceStrategy` for your server (e.g. keyword heuristic, API
   check). Register it in the registry by **server type** or **server id**: in `ServiceLocator` the router uses
   `DefaultMcpRelevanceStrategyRegistry.withDefaults()`; to add a new strategy, either extend `withDefaults()` (register
   your strategy for your preset’s `type`) or build a custom registry and pass it to `DefaultMcpRouter`. See *
   *MCP_ROUTING.md** for the strategy/fallback model.

### Bored API (example preset)

#### Preset and Location

- **Preset id**: `bored-api`
- **Type**: `bored-api`
- **Location**: `mcp-servers/bored-api-mcp/` (relative to repo root)
- **Tools**: `get-random-activity`, `find-activity`
- **Preset file**: `config/McpPresets.kt` → `BORED_API_PRESET`

#### Lifecycle and Launch

The host launches Bored API via STDIO transport:

- **Command**: `node dist/index.js`
- **Working directory**: `mcp-servers/bored-api-mcp` (resolved relative to process cwd)
- **Build prerequisite**: `npm run build` must be run first to produce `dist/index.js`

**Build steps (required before use):**

```bash
cd mcp-servers/bored-api-mcp
npm install
npm run build
```

**How the host starts it:**

1. `StdioMcpClient` receives `McpServerConfig` with `workingDirectory = "mcp-servers/bored-api-mcp"`.
2. Working directory is resolved via `java.io.File(workingDirectory).absoluteFile` (relative to cwd).
3. Process is launched: `node dist/index.js` in that directory.
4. Client performs MCP handshake (initialize → tools/list → resources/list → prompts/list).

**Development mode (alternative):**

```bash
cd mcp-servers/bored-api-mcp
npm run dev  # Uses tsx for hot-reload, no pre-build needed
```

Note: The production preset uses `node dist/index.js` to avoid npx overhead on each launch.

#### Relevance Strategy

- **Strategy class**: `BoredApiRelevanceStrategy`
- **Registration**: By server type `bored-api` in `DefaultMcpRelevanceStrategyRegistry.withDefaults()` (see `McpRelevanceStrategyRegistry.kt:37-38`)
- **Keywords (EN)**: activity, activities, idea, ideas, bored, boredom, what to do, something to do, suggestion, suggestions, random activity
- **Keywords (RU)**: скучно, мне скучно, занятие, занятия, чем заняться, что делать, предложи, предложение, идея, идеи, развлечение, развлечения, чем развлечься, какое занятие, подскажи чем заняться, хочу чем-то заняться

When the router evaluates an optional Bored API server, `BoredApiRelevanceStrategy.isRelevant()` checks for keyword matches (case-insensitive). If matched, the server is selected for context fetching.

#### Limitations and Expected Behavior

- **Build required**: If `dist/index.js` is missing, healthcheck and context fetching will fail with a process launch error.
- **Working directory**: Must be correct relative to app cwd (typically repo root). If cwd changes, the path resolution may break.
- **Healthcheck**: Uses the same session path as context fetching (initialize → tools/list). A running server is required.
- **No persistence**: Server state is not saved between sessions; each session starts fresh.
- **Network dependency**: The server calls the external Bored API (https://bored-api.appbrewery.com/) — network issues will affect results.

#### Verification

1. Build: `cd mcp-servers/bored-api-mcp && npm install && npm run build`
2. In the app: enable "Bored API" in MCP settings → click "Check connection" (should show Online).
3. Test routing: send "I'm bored, give me an activity idea" — the router should select Bored API as relevant.

## Healthcheck and logging

- `CheckMcpHealthUseCase` uses `McpClient.checkHealth(server)` and updates the server’s `healthStatus` and `lastHealthCheckAt` in storage.
- `StdioMcpClient` logs fetch/health outcomes (e.g. “MCP fetch server=&lt;id&gt; success=&lt;bool&gt; reason=…”).

## Checklist (for debugging “agent doesn’t see MCP”)

- Server starts and passes initialize (health check uses the same session path).
- After initialize, the client runs tools/list, resources/list, prompts/list (see `StdioMcpClient.discover`);
  unsupported methods are skipped.
- Each tool has name, description, inputSchema (summarized in discovery); resources and prompts are listed when present.
- Discovery and content are injected into the system prompt (see `CompressingChatAgent` and “--- Available MCP
  tools ---” / “--- MCP context ---”).
- System prompt includes when to use MCP (behavior rules in `MCP_BEHAVIOR_RULES`).

## Tests

- `DefaultMcpRouterTest`: forced/optional split, optional filtered by per-server strategy, forced independent of
  relevance, unknown type uses fallback, metadata (optionalPassed, optionalFiltered), bored-api optional selection.
- `Context7RelevanceTest`: keyword-based relevance for Context7.
- `BoredApiRelevanceTest`: keyword-based relevance for Bored API (activity, ideas, bored, suggestion, etc.).
- `DefaultMcpRegistryAtomicUpdateTest`: concurrent runAtomicUpdate preserves all updates (no lost update).
- `McpContextOrchestratorTest`: orchestration with forced/optional and failure handling; optional behavior driven by
  router selection only; result is `McpEnrichedContext.content` / `.discoverySummary`.
