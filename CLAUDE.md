# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Bothub Chat Client** — a Kotlin desktop application built with Jetbrains Compose that provides a UI for interacting with LLMs through the BOTHUB API. It features MCP (Model Context Protocol) integration, context compression, long-term memory, and background job scheduling.

## Commands

```bash
# Run the application
./gradlew run

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "org.bothubclient.application.mcp.DefaultMcpRouterTest"

# Build distribution (produces EXE on Windows)
./gradlew packageDistributionForCurrentOS
```

**Prerequisites:**
- Java 17+
- Set `BOTHUB_API_KEY` environment variable before running
- Node.js required for some MCP servers (e.g., bored-api-mcp)

## Architecture

Clean Architecture with four layers — dependencies flow inward:

```
Presentation → Application → Domain
     ↓             ↓
Infrastructure → ServiceLocator (DI)
```

**`domain/`** — interfaces, entities, use case definitions. No external dependencies.
**`application/`** — use case implementations, MCP routing/orchestration logic.
**`infrastructure/`** — concrete implementations: HTTP (Ktor), file persistence (JSON), MCP subprocess management, DI via `ServiceLocator`.
**`presentation/`** — Compose UI, `ChatViewModel`, reusable components, theme.
**`config/`** — constants: API endpoint, available models, system prompts, MCP presets.

### Dependency Injection

`infrastructure/di/ServiceLocator.kt` is the single DI container — a singleton with lazy-initialized properties wiring all dependencies. Add new dependencies here.

### Chat Agent Decorator Chain

The agent chain is assembled in `ServiceLocator`:

```
BothubChatAgent          (raw Ktor HTTP calls)
  ↓
CompressingChatAgent     (context compression, MCP context injection, LTM recall, profile injection)
  ↓
StateMachineAwareAgent   (task state tracking)
  ↓
AgentBackedChatRepository → Use Cases → ChatViewModel → UI
```

### MCP Integration

- **`DefaultMcpRouter`** — selects which MCP servers to call based on `enabled`/`force` flags and relevance strategy
- **Relevance strategies** — per-server implementations in `application/mcp/`: `Context7RelevanceStrategy` (doc queries), `BoredApiRelevanceStrategy` (activity queries), `FallbackRelevanceStrategy`
- **`McpContextOrchestrator`** — fetches context from selected servers and injects into the prompt
- **`StdioMcpClient`** — manages subprocess lifecycle for STDIO-based MCP servers
- MCP server presets defined in `config/McpPresets.kt`

### Context Management

`CompressingChatAgent` handles: token counting → summarization of old messages → fact extraction → LTM recall → profile injection → final context assembly before each API call.

### Persistence

All state is file-based JSON (no database). Storage classes live in `infrastructure/persistence/`. `ServiceLocator` wires paths and instances lazily.

## Key Files

| File | Purpose |
|------|---------|
| `infrastructure/di/ServiceLocator.kt` | DI wiring for entire app |
| `config/ApiConfig.kt` | API URL, token limits, env var name |
| `config/AvailableModels.kt` | 22+ supported LLM model IDs |
| `config/McpPresets.kt` | Default MCP server configurations |
| `application/mcp/DefaultMcpRouter.kt` | MCP server selection logic |
| `presentation/viewmodel/ChatViewModel.kt` | UI state management |
| `AGENTS.md` | Architecture overview (Russian) |
| `MCP_ROUTING.md` | MCP routing rules and relevance strategy docs |
| `docs/MCP_ARCHITECTURE.md` | Detailed MCP data flows |

## Testing

Tests use JUnit 5 + MockK. Structure mirrors `src/main/kotlin`. Domain and application layers are unit-tested with mocks; infrastructure tests cover persistence and scheduler logic.
