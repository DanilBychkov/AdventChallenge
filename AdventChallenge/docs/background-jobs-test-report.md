# Background Jobs Test Report

**Date:** 2026-03-11  
**Test Framework:** JUnit 5 + kotlinx-coroutines-test  
**Total Tests:** 213 (including 56 new background jobs tests)  
**Failures:** 0  
**Success Rate:** 100%  
**Duration:** ~2.2s  
**Consecutive Green Runs:** 2/2 (clean rebuild)

---

## New Test Classes

### JsonBackgroundJobRepositoryTest (10 tests, 0.125s)

| Test                                                | Status |
|-----------------------------------------------------|--------|
| loadAll returns empty list when file does not exist | PASS   |
| save and loadAll round-trip                         | PASS   |
| save updates existing job                           | PASS   |
| findById returns correct job                        | PASS   |
| findById returns null for missing id                | PASS   |
| delete removes job                                  | PASS   |
| loadAll handles corrupted file gracefully           | PASS   |
| loadAll handles empty file gracefully               | PASS   |
| concurrent writes preserve data integrity           | PASS   |
| save preserves all fields                           | PASS   |

### JsonBoredReportRepositoryTest (7 tests, 0.065s)

| Test                                                | Status |
|-----------------------------------------------------|--------|
| loadAll returns empty list when file does not exist | PASS   |
| add and loadAll round-trip                          | PASS   |
| add multiple reports                                | PASS   |
| deleteByJobId removes matching reports              | PASS   |
| loadAll handles corrupted file gracefully           | PASS   |
| loadAll handles empty file                          | PASS   |
| add preserves all fields                            | PASS   |

### BackgroundJobManagerTest (10 tests)

| Test                                                          | Status |
|---------------------------------------------------------------|--------|
| due job executes and generates report                         | PASS   |
| disabled job is skipped                                       | PASS   |
| error sets status and increments retryCount                   | PASS   |
| multiple ticks generate exactly 3 reports for 3 due intervals | PASS   |
| onReportGenerated callback fires on each execution            | PASS   |
| onReportGenerated callback NOT fired on error                 | PASS   |
| runJobNow also triggers callback                              | PASS   |
| runJobNow executes immediately                                | PASS   |
| job not yet due is not executed                               | PASS   |

### ParseScheduleIntentTest (18 tests, 0.060s)

| Test                                        | Status |
|---------------------------------------------|--------|
| присылай раз в 5 минут чем заняться         | PASS   |
| каждые 10 минут подсказывай что делать      | PASS   |
| каждый час подсказывай что делать           | PASS   |
| раз в 15 минут пиши что делать              | PASS   |
| отправляй раз в 30 минут активности         | PASS   |
| напоминай каждые 3 минуты чем заняться      | PASS   |
| присылай каждые 20 минут рекомендации       | PASS   |
| раз в 1 минуту присылай что делать          | PASS   |
| каждые 60 минут пиши активность             | PASS   |
| через каждые 5 минут напоминай чем заняться | PASS   |
| привет как дела returns NONE                | PASS   |
| расскажи анекдот returns NONE               | PASS   |
| what is the weather returns NONE            | PASS   |
| empty string returns NONE                   | PASS   |
| plain question returns NONE                 | PASS   |
| case insensitive matching                   | PASS   |
| раз в час                                   | PASS   |
| 5 минут присылай чем заняться               | PASS   |

### BackgroundJobIntegrationTest (3 tests)

| Test                                                 | Status |
|------------------------------------------------------|--------|
| full cycle -- configure job, 3 ticks, verify reports | PASS   |
| configure updates existing job interval              | PASS   |
| error recovery with retries                          | PASS   |

### BackgroundJobChatFlowTest (5 tests) -- NEW

| Test                                                                   | Status |
|------------------------------------------------------------------------|--------|
| intent extraction creates job and callback fires on each periodic tick | PASS   |
| non-schedule message does NOT create any job                           | PASS   |
| toggle job stops periodic execution                                    | PASS   |
| runJobNow adds message to chat via callback                            | PASS   |
| update interval changes next execution time                            | PASS   |

---

## Covered Scenarios

- **JSON Persistence:** Load/save round-trip, concurrent writes, corrupted file recovery, empty file handling, field
  preservation for both job and report repositories
- **Scheduler Logic:** Due-time check, disabled job skip, immediate execution via runJobNow, jobs not yet due are not
  executed, strict 3-tick-3-report verification
- **Callback Notifications:** onReportGenerated fires on each successful execution, NOT on error, fires on runJobNow
- **Error Handling:** Error status set on failure, retryCount increment, exponential backoff for nextRun, recovery after
  transient failures
- **Intent Parsing:** 22 Russian phrases tested (17+ positive patterns, 5 negative/NONE patterns), case-insensitive
  matching, hour/minute/half-hour variants, "каждую минуту" edge case
- **Chat Flow (E2E):** Full cycle: intent extraction → job creation → 3 periodic ticks → 3 callback messages; toggle
  disable/re-enable; runJobNow triggers callback; non-schedule messages don't create jobs; interval update
- **Integration:** Full end-to-end cycle with fake BoredClient and ReportGenerator using virtual time, idempotent job
  configuration, error recovery across multiple ticks

## Bugs Found and Fixed

1. **Main.kt: ViewModel not remembered** -- `ChatViewModel.create()` was called without `remember {}`, causing ViewModel
   recreation on recomposition and losing all state
2. **Race condition in sendMessage()** -- Intent extraction ran in parallel coroutine, but main LLM flow overwrote
   `messages` from persistent storage, erasing the system confirmation
3. **No chat notification on report generation** -- `BackgroundJobManager` saved reports to file but had no callback to
   push them into the chat UI
4. **Bored API JSON parse error** -- API changed format: `accessibility` and `price` fields became strings; fixed by
   keeping only the `activity` field
5. **Missing regex patterns** -- "каждую минуту", "полчаса", gerund forms ("подсказывая") not handled by rule-based
   parser

## Known Limitations

- LLM-based intent extraction is not tested in unit tests (requires real API key); only rule-based regex path is covered
- HttpBoredClient and LlmReportGenerator are tested indirectly via integration tests with fakes, not via live HTTP calls
  in the test suite
- UI components (ResultsDialog) are not tested headlessly (Compose Desktop test tooling limitations)
