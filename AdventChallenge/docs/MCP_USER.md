# MCP Settings — User Guide

## What are MCP servers?

MCP (Model Context Protocol) servers provide extra context to the assistant, for example up-to-date documentation and code examples. **Context7** helps with library/framework docs and API lookup. **Bored API** suggests random activities when you ask for ideas or things to do.

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

## Как формулировать запрос на документацию

Запросы можно писать **простыми фразами** — приложение само подставит нужный идентификатор библиотеки (Context7).
Достаточно указать название библиотеки или темы.

**Примеры корректных запросов:**

- «Посмотри документацию Kotlin Coroutines»
- «Документация по React»
- «Найди информацию о Ktor»
- «Документация по Spring Boot»

Не обязательно писать «через Context7» или указывать формат вида `/owner/repo` — это делает приложение. Если
документация не загрузилась (сеть, таймаут), попробуйте повторить запрос или указать точное имя пакета (например
`kotlinx-coroutines`, `react`).

## Context7

Context7 runs via `npx` and needs network access to fetch documentation. The preset matches
the [official installation](https://github.com/upstash/context7#installation). For higher rate limits, get a free API
key at [context7.com/dashboard](https://context7.com/dashboard) and add env `CONTEXT7_API_KEY` or args
`--api-key YOUR_KEY` in MCP server settings. If it is offline or errors, the assistant will still answer without it; you
can turn it off in MCP settings if you don’t want it used.

## Bored API

Bored API is a local MCP server (in the repo at `mcp-servers/bored-api-mcp`). It suggests random activities when you ask for ideas or things to do. Enable it in MCP settings; the app runs it with `node dist/index.js` from that folder. Build the server first: `cd mcp-servers/bored-api-mcp && npm install && npm run build`. Use **Check connection** in MCP settings to verify it is online.
