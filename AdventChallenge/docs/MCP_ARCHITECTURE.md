# MCP Architecture (aligned with mcp-agents-architecture)

This document describes how MCP is integrated into the app, following the patterns
from [mcp-agents-architecture](https://github.com/datmt/mcp-agents-architecture) (Single Agent + Multiple MCP Servers,
with host-side tool execution).

## Reference

- **Pattern
  **: [Single Agent + Multiple MCP Servers](https://github.com/datmt/mcp-agents-architecture/tree/main/02-single-agent-multi-mcp) —
  one chat agent, multiple MCP servers; tools/resources from all servers are aggregated and exposed to the model.
- **Protocol**: [Model Context Protocol](https://modelcontextprotocol.io) — JSON-RPC 2.0 over stdio (newline-delimited
  messages).

## System overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         USER INTERFACE                          │
│                    (Chat UI / Message input)                     │
└─────────────────────────────────┬───────────────────────────────┘
                                  │ User message
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                          AI AGENT (Host)                         │
│                                                                  │
│  ┌─────────────┐  ┌──────────────────┐  ┌────────────────────┐ │
│  │   LLM       │  │   Agent Logic    │  │    MCP Client       │ │
│  │ (remote API)│◄─┤ CompressingChat  │◄─┤ StdioMcpClient      │ │
│  │             │  │ Agent + Router   │  │ - discover()        │ │
│  └─────────────┘  └──────────────────┘  │ - fetchContext()    │ │
│                                          │ - checkHealth()     │ │
│                                          └──────────┬─────────┘ │
└─────────────────────────────────────────────────────┼───────────┘
                                                      │
                                                      │ MCP (stdio, JSON-RPC)
                                                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                     MCP SERVERS (selected per request)           │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Context7 (documentation)  │  … future servers            │   │
│  │  Tools, Resources, Prompts (discovery)                   │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Connection lifecycle (MCP spec)

1. **Connect** — Host spawns MCP server process (e.g. `npx @upstash/context7-mcp`).
2. **Initialize** — Client sends `initialize` (protocolVersion, clientInfo, capabilities); server responds with
   serverInfo and capabilities.
3. **Initialized** — Client sends `notifications/initialized`; connection ready.
4. **Discovery** — Client requests:
    - `tools/list` → list of tools (name, description, inputSchema)
    - `resources/list` → list of resources (uri, name, description, mimeType)
    - `prompts/list` → list of prompts (name, description)
5. **Use** — Client invokes `tools/call`, optionally `resources/read`, `prompts/get`; host injects results into the
   agent context.

## Data flow (per user message)

1. **Router** — `McpRouter.selectForRequest(userMessage)` returns forced and optional servers (by enabled, force usage,
   Context7 relevance).
2. **Discovery** — For each selected server, `McpClient.discover(server)` runs one session: initialize → tools/list →
   resources/list → prompts/list. Results are aggregated into a discovery summary (tools, resources, prompts per
   server).
3. **Fetch** — For forced servers and (if relevant) first successful optional server,
   `McpClient.fetchContext(server, userMessage)` runs a session and calls MCP tools internally; returned text is the
   “MCP context” block.
4. **Harness** — System prompt is built from: base prompt + profile + **MCP behavior rules** + **Available MCP tools (
   discovery)** + **MCP context** (pre-fetched content). The LLM sees what capabilities exist and the pre-fetched
   content; it does not send tool_calls (chat API does not support tools yet).

## Components (aligned with 01-single-agent-single-mcp)

| Component          | Role                                                                                                                 |
|--------------------|----------------------------------------------------------------------------------------------------------------------|
| **User interface** | Chat screen, message input, MCP settings (enable/force, health).                                                     |
| **LLM**            | Remote API (e.g. Bothub); receives system prompt + user message.                                                     |
| **Agent logic**    | `CompressingChatAgent`: builds context, calls orchestrator, injects discovery + content.                             |
| **MCP client**     | `StdioMcpClient`: discovery (tools/list, resources/list, prompts/list), fetchContext (tool invocation), checkHealth. |
| **Router**         | `DefaultMcpRouter`: selects which MCP servers to use for the request.                                                |
| **Orchestrator**   | `McpContextOrchestrator`: runs discovery for selected servers, fetches context, returns `McpEnrichedContext`.        |

## MCP primitives (what the model “sees”)

- **Tools** — Functions the host can call (e.g. get-library-docs, resolve-library-id). Descriptions and parameter
  summaries are in the discovery block; the host invokes them and injects results as “MCP context”.
- **Resources** — Data the agent can read (URIs listed in discovery). Optional: host could later support
  `resources/read` and inject selected resource content.
- **Prompts** — Reusable prompt templates (e.g. for slash commands). Listed in discovery; invocation would be user- or
  host-driven.

## Differences from “full” model-controlled tools

In
the [reference architecture](https://github.com/datmt/mcp-agents-architecture/blob/main/01-single-agent-single-mcp/communication.md),
the LLM decides which tool to call and the client executes it. Here, the **chat API does not accept tools** in the
request, so:

- Discovery (tools/resources/prompts) is passed **in the system prompt** so the model knows what exists.
- **Tool invocation is done by the host** before the request (fetchContext); the model receives the result as the “---
  MCP context ---” block and uses it to answer.

To move to full model-controlled tool calling, the backend would need to support a tool-calling API and the host would
map MCP tools to that API and execute tool_calls from the LLM response.
