# AGENT.md - Документация для AI-агентов

## Обзор проекта

Bothub Client — это десктопное приложение для взаимодействия с LLM через Bothub API. Приложение построено на Kotlin с
использованием Jetbrains Compose для GUI и Ktor для HTTP-запросов.

## Архитектура

### Структура проекта

```
AdventChallenge/
├── src/main/kotlin/
│   ├── lesson1/
│   │   ├── BothubClient.kt    # API клиент и конфигурация
│   │   └── BothubGui.kt       # GUI на Jetbrains Compose
│   └── Main.kt                # Альтернативная точка входа
├── build.gradle.kts           # Конфигурация сборки Gradle
└── gradle.properties          # Настройки Gradle
```

### Ключевые компоненты

#### 1. BothubConfig (BothubClient.kt)

Центральный объект конфигурации:

- `API_URL` — endpoint для Bothub API
- `AVAILABLE_MODELS` — список поддерживаемых моделей (GPT-4, Claude, Gemini, Grok, DeepSeek, Llama)
- `SYSTEM_PROMPTS` — предустановленные системные промты для разных сценариев
- `DEFAULT_MODEL` / `DEFAULT_PROMPT` — значения по умолчанию

#### 2. Data Classes (BothubClient.kt)

- `ChatMessage` — структура сообщения (role, content)
- `ChatRequest` — структура запроса к API
- `ChatResponse` — структура ответа от API
- `SystemPrompt` — системный промт с именем и содержимым

#### 3. API Functions (BothubClient.kt)

- `createHttpClient()` — создание HTTP клиента с JSON сериализацией
- `sendMessage()` — отправка сообщения в Bothub API с параметрами модели и системного промта
- `getApiKey()` — получение API ключа из переменной окружения

#### 4. GUI Components (BothubGui.kt)

- `BothubChatApp` — главный компонент приложения
- `ChatMessageItem` — модель сообщения для отображения
- Dropdown меню для выбора модели и системного промта
- Карта с отображением полного текста выбранного промта
- Список сообщений с аватарами и временными метками

## Цели проекта

1. Предоставить удобный GUI для работы с различными LLM через единый API
2. Обеспечить гибкую настройку через выбор моделей и системных промтов
3. Поддерживать историю чата в рамках сессии
4. Демонстрировать интеграцию Kotlin/Compose с OpenAI-совместимым API

## Поставщик LLM: BOTHUB

本项目 использует **BOTHUB** в качестве поставщика LLM. BOTHUB предоставляет единый API, совместимый с OpenAI, для
доступа к различным языковым моделям.

### Получение API ключа

1. Перейдите на страницу https://bothub.chat/profile/for-developers
2. Нажмите кнопку "Добавить ключ"
3. Скопируйте сгенерированный API ключ
4. Установите переменную окружения:
    - Windows (PowerShell): `$env:BOTHUB_API_KEY = "ваш_ключ"`
    - Windows (CMD): `set BOTHUB_API_KEY=ваш_ключ`
    - Linux/macOS: `export BOTHUB_API_KEY="ваш_ключ"`

### Официальная документация

Полная документация BOTHUB API доступна по адресу:
**https://bothub.chat/api/documentation**

## Доступные модели

| Модель                                             | Описание                |
|----------------------------------------------------|-------------------------|
| grok-4.1-fast, grok-3                              | Модели xAI Grok         |
| gpt-4.1, gpt-4.1-mini, gpt-4o, gpt-4o-mini         | Модели OpenAI GPT       |
| o3, o3-mini, o4-mini                               | Модели OpenAI O-серии   |
| claude-sonnet-4, claude-3-5-sonnet                 | Модели Anthropic Claude |
| gemini-2.5-pro, gemini-2.5-flash, gemini-2.0-flash | Модели Google Gemini    |
| deepseek-chat, deepseek-reasoner                   | Модели DeepSeek         |
| llama-3.3-70b, llama-4-scout                       | Модели Meta Llama       |

## Системные промты

Предустановленные промты для различных сценариев:

- **Полезный ассистент** — общий помощник на русском языке
- **Программист** — помощь с кодом и техническими вопросами
- **Переводчик** — перевод текста между языками
- **Учитель** — объяснение тем простым языком
- **Креативный писатель** — генерация идей и текстов
- **Аналитик данных** — анализ информации и выводы
- **Английский собеседник** — практика английского языка

## Запуск проекта

```bash
# Убедитесь, что API ключ установлен
./gradlew run   # Linux/macOS
.\gradlew.bat run   # Windows
```

## Технический стек

- **Kotlin** — основной язык
- **Jetbrains Compose** — GUI фреймворк
- **Ktor Client** — HTTP клиент
- **Kotlinx Serialization** — JSON сериализация
- **OkHttp** — HTTP engine
- **Gradle** — система сборки

## API Format

Bothub использует OpenAI-совместимый формат:

```json
{
  "model": "gpt-4o",
  "messages": [
    {
      "role": "system",
      "content": "Системный промт"
    },
    {
      "role": "user",
      "content": "Сообщение пользователя"
    }
  ],
  "max_tokens": 150,
  "temperature": 0.7
}
```

## Ограничения

- API ключ должен быть установлен в переменной окружения `BOTHUB_API_KEY`
- Выбор системного промта доступен только в начале разговора (при пустом чате)
- История чата хранится только в памяти (не сохраняется между сессиями)
