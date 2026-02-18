# AGENT.md - Документация для AI-агентов

## Обзор проекта

Bothub Client — это десктопное приложение для взаимодействия с LLM через Bothub API. Приложение построено на Kotlin с
использованием Jetbrains Compose для GUI и Ktor для HTTP-запросов. Архитектура следует принципам Clean Architecture с
разделением на слои: domain, application, infrastructure, presentation.

## Архитектура

### Структура проекта (Clean Architecture)

```
org.bothubclient/
├── config/                          # Конфигурации
│   ├── ApiConfig.kt                 # URL API и константы
│   ├── AvailableModels.kt           # Доступные модели ИИ
│   └── SystemPrompts.kt             # Системные промпты
│
├── domain/                          # Domain слой (бизнес-логика)
│   ├── entity/
│   │   ├── Message.kt               # Сущность сообщения
│   │   ├── ChatRequest.kt           # Запрос к чату
│   │   └── ChatResult.kt            # Результат (sealed class)
│   └── repository/
│       ├── ChatRepository.kt        # Интерфейс репозитория
│       └── ApiKeyProvider.kt        # Интерфейс провайдера ключа
│
├── application/                     # Application слой (use-cases)
│   └── usecase/
│       ├── SendMessageUseCase.kt    # Отправка сообщения
│       ├── GetAvailableModelsUseCase.kt
│       ├── GetSystemPromptsUseCase.kt
│       └── ValidateApiKeyUseCase.kt
│
├── infrastructure/                  # Infrastructure слой
│   ├── api/
│   │   ├── ApiModels.kt             # DTO для API (сериализация)
│   │   └── BothubChatRepository.kt  # Реализация ChatRepository
│   ├── config/
│   │   └── EnvironmentApiKeyProvider.kt  # Реализация ApiKeyProvider
│   └── di/
│       └── ServiceLocator.kt        # DI контейнер (Singleton)
│
├── presentation/                    # Presentation слой (UI)
│   ├── ui/
│   │   ├── components/              # Переиспользуемые UI компоненты
│   │   │   ├── ChatInputField.kt
│   │   │   ├── DropdownSelector.kt
│   │   │   ├── ErrorCard.kt
│   │   │   ├── MessageBubble.kt
│   │   │   └── SendButton.kt
│   │   ├── screen/
│   │   │   └── ChatScreen.kt        # Главный экран
│   │   └── theme/
│   │       ├── AppColors.kt
│   │       └── BothubTheme.kt
│   └── viewmodel/
│       └── ChatViewModel.kt         # Управление состоянием UI
│
└── Main.kt                          # Точка входа
```

### Принципы архитектуры

#### SOLID

- **Single Responsibility** — каждый класс имеет одну ответственность
- **Open/Closed** — интерфейсы позволяют расширять без изменения существующего кода
- **Liskov Substitution** — реализации можно заменять (ChatRepository, ApiKeyProvider)
- **Interface Segregation** — узкоспециализированные интерфейсы
- **Dependency Inversion** — зависимость от абстракций (интерфейсов в domain/repository)

#### DRY (Don't Repeat Yourself)

- Константы вынесены в config/
- UI компоненты переиспользуемы
- ServiceLocator централизует создание зависимостей

#### KISS (Keep It Simple, Stupid)

- Чёткое разделение слоёв
- Прямолинейная структура use-cases
- Понятные имена файлов и классов

### Ключевые компоненты

#### 1. Config слой

Отдельные файлы для конфигураций:

- [AvailableModels.kt](src/main/kotlin/org/bothubclient/config/AvailableModels.kt) — список моделей
- [SystemPrompts.kt](src/main/kotlin/org/bothubclient/config/SystemPrompts.kt) — системные промпты
- [ApiConfig.kt](src/main/kotlin/org/bothubclient/config/ApiConfig.kt) — URL и параметры API

#### 2. Domain слой

Сущности:

- `Message` — сообщение с role, content, timestamp
- `ChatRequest` — параметры запроса
- `ChatResult` — sealed class (Success/Error)

Интерфейсы:

- `ChatRepository` — контракт для отправки сообщений
- `ApiKeyProvider` — контракт для получения API ключа

#### 3. Application слой

Use-cases (инкапсулируют бизнес-логику):

- `SendMessageUseCase` — отправка сообщения через репозиторий
- `GetAvailableModelsUseCase` — получение списка моделей
- `GetSystemPromptsUseCase` — получение списка промптов
- `ValidateApiKeyUseCase` — валидация API ключа

#### 4. Infrastructure слой

Реализации:

- `BothubChatRepository` — HTTP клиент для Bothub API
- `EnvironmentApiKeyProvider` — чтение ключа из переменных окружения
- `ServiceLocator` — DI контейнер для управления зависимостями

#### 5. Presentation слой

- `ChatViewModel` — управление состоянием UI (messages, isLoading, errors)
- `ChatScreen` — главный экран с чатом
- Компоненты: MessageBubble, DropdownSelector, ChatInputField, SendButton, ErrorCard

## Цели проекта

1. Предоставить удобный GUI для работы с различными LLM через единый API
2. Обеспечить гибкую настройку через выбор моделей и системных промтов
3. Поддерживать историю чата в рамках сессии
4. Демонстрировать Clean Architecture на Kotlin/Compose

## Поставщик LLM: BOTHUB

Проект использует **BOTHUB** в качестве поставщика LLM. BOTHUB предоставляет единый API, совместимый с OpenAI, для
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
| gemini-2.0-flash-lite-001                          | Модель Google Gemini    |
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

## Зависимости между слоями

```
Presentation → Application → Domain
     ↓              ↓
Infrastructure ← ServiceLocator
```

- Presentation зависит от Application (use-cases)
- Application зависит от Domain (interfaces)
- Infrastructure реализует Domain interfaces
- ServiceLocator связывает все слои

## Расширение проекта

### Добавление новой модели

Отредактировать [AvailableModels.kt](src/main/kotlin/org/bothubclient/config/AvailableModels.kt)

### Добавление нового системного промпта

Отредактировать [SystemPrompts.kt](src/main/kotlin/org/bothubclient/config/SystemPrompts.kt)

### Добавление нового use-case

1. Создать класс в `application/usecase/`
2. Зарегистрировать в `ServiceLocator`

### Добавление нового API провайдера

1. Реализовать `ChatRepository` interface
2. Добавить в `ServiceLocator`

## Ограничения

- API ключ должен быть установлен в переменной окружения `BOTHUB_API_KEY`
- Выбор системного промпта доступен только в начале разговора (при пустом чате)
- История чата хранится только в памяти (не сохраняется между сессиями)
