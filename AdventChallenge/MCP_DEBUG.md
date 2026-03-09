# Отладка MCP / Context7 по логам

## Как запустить приложение с логами

1. Из папки `AdventChallenge` выполните:
   ```bash
   .\gradlew.bat run --no-daemon
   ```
2. Либо запустите приложение из IDE (Run MainKt).

Логи пишутся в:

- **app.log** — в текущей рабочей папке процесса (обычно `AdventChallenge\app.log` при запуске через Gradle)
- **stdout** — все сообщения с префиксами `[INFO][StdioMcpClient]`, `[INFO][McpContextOrchestrator]`,
  `[CompressingChatAgent]` дублируются в консоль.

## Что сделать в приложении для проверки Context7

1. Убедитесь, что в настройках MCP включён и настроен сервер **context7** (иконка шестерёнки → MCP).
2. В поле ввода напишите: **Посмотри документацию Kotlinx.coroutines** (или «… по Kotlinx.coroutines»).
3. Нажмите **Отправить**.
4. Откройте **app.log** (или смотрите вывод в консоли) и найдите строки с тегами `[MCP]`, `McpContextOrchestrator`,
   `CompressingChatAgent`.

## Как читать логи (цепочка MCP)

| Этап | Что искать в логе                                           | Ожидание                                                                                |
|------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| 1    | `MCP orchestrator present=`                                 | `true` — MCP включён                                                                    |
| 2    | `MCP selection sessionId= ... forced= optional=`            | Список серверов; для доков должен быть context7 в forced или optional                   |
| 3    | `[MCP] fetchContext START server=context7`                  | Старт запроса к Context7                                                                |
| 4    | `[MCP] session started, requesting tools/list`              | Сессия MCP запущена                                                                     |
| 5    | `[MCP] tools/list count=`                                   | Должно быть ≥ 1; имена инструментов (resolve-library-id, query-docs / get-library-docs) |
| 6    | `resolveLibraryId extractLibraryName ... -> baseName='...'` | Имя библиотеки из запроса (например `Kotlinx.coroutines`)                               |
| 7    | `[MCP] resolveLibraryId namesToTry=`                        | Варианты имён для поиска                                                                |
| 8    | `resolveLibraryId attempt libraryName='...' ... parsedId=`  | Ответ resolve-library-id; **parsedId** — ID вида `/owner/repo` или `null`               |
| 9    | `[MCP] resolveLibraryId SUCCESS resolvedId='...'`           | Успешное определение ID библиотеки                                                      |
| 10   | `[MCP] callToolsForQuery tool='...' SUCCESS contentLen=`    | Успешный вызов инструмента документации (query-docs / get-library-docs)                 |
| 11   | `MCP enriched hasContent= hasDiscovery= lastError=`         | Итог: `hasContent=true`, `lastError=null` — контент загружен                            |

## Типичные ошибки по логам

- **MCP orchestrator present=false** — MCP не подключён (настройки, серверы).
- **tools/list count=0** или **no tools returned** — MCP-процесс не отдал инструменты (не запустился, таймаут, другая
  ошибка).
- **resolveLibraryId: no resolve-library-id tool found** — у сервера нет инструмента resolve-library-id.
- **resolveLibraryId FAIL no ID from any variant** — поиск по имени библиотеки не вернул ID (имя, API, сеть).
- **resolveLibraryId attempt ... parsedId=null** — ответ resolve-library-id не содержит разбираемый ID (формат ответа).
- **callToolsForQuery FAIL no tool returned usable content** — get-library-docs/query-docs не вернул контент (ID,
  лимиты, API).
- **fetchContext EXCEPTION** — исключение при общении с MCP (процесс, таймаут, stderr в сообщении).
- **MCP context fetch failed:** — исключение в оркестраторе; текст после двоеточия — причина.
- **MCP attaching mcpError to Success** — в UI под полем ввода будет показана эта причина (красная строка «MCP: …»).

После любого из этих сообщений смотрите следующую строку в логе (и при необходимости stack trace) — там будет причина
или контекст.
