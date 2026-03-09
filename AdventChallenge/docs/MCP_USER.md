# MCP Settings — User Guide

## What are MCP servers?

MCP (Model Context Protocol) servers provide extra context to the assistant, for example up-to-date documentation and code examples. **Context7** is the first built-in server and helps with library/framework docs, API lookup, and migration examples.

## Opening MCP settings

- In the main window header, click the **Settings (gear)** icon next to the profile button to open **MCP Servers**.
- In the dialog you can see all available servers, turn them on or off, and check their connection status.

## Enabled vs Force usage

- **Enabled**: When enabled, the assistant *may* use this MCP server when it thinks it is useful for your request (for example when you ask about documentation or APIs).
- **Force usage**: When enabled *and* “Force usage” is on, the assistant *must* try to use this server for every relevant coding task before answering. Use this if you always want up-to-date docs or examples for that kind of request.

Summary:

| Enabled | Force usage | Behavior |
|--------|-------------|----------|
| Off    | —           | Server is never used. |
| On     | Off         | Assistant may use it when appropriate. |
| On     | On          | Assistant must try to use it for relevant tasks. |

## Health status

- **Unknown**: Not checked yet.
- **Online**: Last health check succeeded.
- **Offline** / **Error**: Server did not respond or returned an error.

Click **Check connection** to run a health check for that server. The status and “Last checked” time will update.

## Saving

Your choices (enabled, force usage) are saved automatically and persist after restart. They are stored in your user data directory (e.g. `~/.bothubclient/mcp_servers.json`).

## Context7

Context7 runs via `npx` and needs network access to fetch documentation. If it is offline or errors, the assistant will still answer without it; you can turn it off in MCP settings if you don’t want it used.
