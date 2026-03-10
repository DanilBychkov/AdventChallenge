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

Bored API — это локальный MCP-сервер для подбора случайных занятий, когда вы ищете идеи или чем заняться.

### Как включить

1. Откройте **MCP Servers** (значок шестерёнки в заголовке главного окна).
2. Найдите **Bored API** в списке серверов.
3. Включите переключатель **Enabled**.
4. При желании включите **Force usage**, чтобы сервер использовался для всех подходящих запросов.

### Предварительные требования

Сервер запускается командой `node dist/index.js` из папки `mcp-servers/bored-api-mcp`. Перед первым использованием **обязательно соберите проект**:

```bash
cd mcp-servers/bored-api-mcp
npm install
npm run build
```

После сборки появится файл `dist/index.js`, необходимый для работы сервера. Без него healthcheck завершится с ошибкой.

### Проверка соединения

После включения нажмите **Check connection** в настройках MCP:

- **Online** — сервер работает, готов к использованию.
- **Offline** / **Error** — сервер не отвечает. Возможные причины:
  - Не выполнена сборка: `dist/index.js` отсутствует → выполните `npm run build` в папке `mcp-servers/bored-api-mcp`.
  - Файл не найден: проверьте, что папка `mcp-servers/bored-api-mcp` существует и содержит `dist/index.js`.
  - Ошибка запуска процесса: проверьте логи приложения или попробуйте запустить `node dist/index.js` вручную из папки сервера.

### Примеры запросов

Сервер активируется по ключевым словам в вашем сообщении. Примеры запросов, которые вызовут Bored API:

**На русском:**
- «Мне скучно»
- «Предложи занятие»
- «Чем заняться?»
- «Хочу идеи для развлечения»
- «Подскажи какое-нибудь занятие»

**In English:**
- «I'm bored»
- «Give me activity ideas»
- «What to do?»
- «Suggest something fun»
- «Random activity please»

Если сервер Offline или выключен, ассистент ответит без использования Bored API.
