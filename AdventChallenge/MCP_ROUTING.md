# MCP Routing Strategy

This project uses an MCP router/orchestrator that returns MCP server candidates for a request. It does not execute MCP calls yet.

## Selection rules

- `enabled = false`: server is skipped and never selected.
- `enabled = true` and `forceUsage = false`: server is returned in `optionalServers`.
    - Optional servers are filtered by **per-server relevance strategies** before being included.
    - Only servers whose strategy returns `relevant = true` are included in the final selection.
- `enabled = true` and `forceUsage = true`: server is returned in `forcedServers`.
    - Forced servers are always included regardless of relevance.

## Priority and ordering

- If multiple forced servers are selected, they are ordered by `priority` ascending.
- Lower `priority` value means higher execution priority.

## Per-server relevance strategy model

The router uses `McpRelevanceStrategyRegistry` to determine which optional servers are relevant for a given request:

1. **Strategy lookup priority**: Server ID → Server Type → Fallback
2. **Default strategies**:
    - `context7` type: `Context7RelevanceStrategy` (keyword-based heuristic)
    - `bored-api` type: `BoredApiRelevanceStrategy` (activity/ideas/boredom keywords)
    - Unknown types: `FallbackRelevanceStrategy(defaultRelevant = false)`

### Relevance strategies

| Strategy                     | Description                                                         |
|------------------------------|---------------------------------------------------------------------|
| `Context7RelevanceStrategy`  | Keyword-based matching for documentation/API/SDK queries            |
| `BoredApiRelevanceStrategy`  | Keyword-based matching for activity/ideas/boredom/suggestions         |
| `FallbackRelevanceStrategy`  | Default fallback; configurable `defaultRelevant` (default: `false`) |

### How relevance is determined

For each optional server, the router:

1. Gets the strategy from `McpRelevanceStrategyRegistry.getStrategy(server)`
2. Calls `strategy.isRelevant(server, userMessage, context)`
3. Includes the server in `optionalServers` only if `result.relevant == true`

### Router metadata

The router includes diagnostic metadata in `McpSelectionResult`:

- `strategy`: `"enabled_split_forced_optional_relevance"`
- `optionalPassed`: Comma-separated list of server IDs that passed relevance
- `optionalFiltered`: Semicolon-separated list of `"serverId: reason"` for filtered servers

## Adding a new MCP server with relevance logic

1. **Create a relevance strategy** implementing `McpRelevanceStrategy`:
   ```kotlin
   class MyServerRelevanceStrategy : McpRelevanceStrategy {
       override fun isRelevant(
           server: McpServerConfig,
           userMessage: String,
           context: McpRequestContext?
       ): McpRelevanceResult {
           // Your relevance logic here
           return McpRelevanceResult(
               relevant = /* true or false */,
               reason = "optional_reason_for_logging"
           )
       }
   }
   ```

2. **Register the strategy** in `ServiceLocator` when building the registry:
   ```kotlin
   val relevanceRegistry = DefaultMcpRelevanceStrategyRegistry.withDefaults().apply {
       registerForType("my-server-type", MyServerRelevanceStrategy())
       // Or register by specific server ID:
       // registerForServerId("my-server-id", MyServerRelevanceStrategy())
   }
   ```

3. **Add the server preset** in `config/McpPresets.kt` with the matching `type`.

## Context7 relevance keywords

The `Context7RelevanceStrategy` checks for these keywords (case-insensitive):

- English: `documentation`, `docs`, `api`, `how to use`, `example`, `examples`, `migration`, `migrate`, `upgrade`,
  `library`, `framework`, `sdk`, `package`, `npm`, `pip`, `latest version`, `coroutines`
- Russian: `документация`, `документацию`

## Bored API relevance keywords

The `BoredApiRelevanceStrategy` checks for (case-insensitive): `activity`, `activities`, `idea`, `ideas`, `bored`, `boredom`, `what to do`, `something to do`, `suggestion`, `suggestions`, `random activity`.

## Fallback behavior

- If no strategy is registered for a server's type or ID, the `FallbackRelevanceStrategy` is used.
- By default, `FallbackRelevanceStrategy(defaultRelevant = false)` means unknown server types are **not** included in
  optional servers.
- To change this, pass a different fallback when creating the registry:
  ```kotlin
  DefaultMcpRelevanceStrategyRegistry(
      fallbackStrategy = FallbackRelevanceStrategy(defaultRelevant = true)
  )
  ```
