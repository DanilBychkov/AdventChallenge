# MCP Routing Strategy

This project uses an MCP router/orchestrator that returns MCP server candidates for a request. It does not execute MCP calls yet.

## Selection rules

- `enabled = false`: server is skipped and never selected.
- `enabled = true` and `forceUsage = false`: server is returned in `optionalServers`.
  - Optional servers may be used by the agent when helpful.
- `enabled = true` and `forceUsage = true`: server is returned in `forcedServers`.
  - Forced servers must be tried before optional servers.

## Priority and ordering

- If multiple forced servers are selected, they are ordered by `priority` ascending.
- Lower `priority` value means higher execution priority.

## Context7 relevance helper

- The router module includes `isContext7Relevant(userMessage: String): Boolean`.
- This is a keyword-based heuristic for requests related to:
  - library/framework documentation;
  - API lookup;
  - code examples;
  - SDK usage;
  - migrations/upgrades between versions.

## Fallback behavior

- Fallback execution handling is planned for a follow-up task.
- Planned behavior: if MCP call fails, log the failure and continue without MCP context.
