# CLAUDE.md — RutCampusTrack

## Что это за проект

RutCampusTrack — микросервисная система учёта посещаемости для вуза РУТ МИИТ. Monorepo на Java 21 + Spring Boot 3.4 + Gradle.

## Текущий статус

- **Фаза 0**: ЗАВЕРШЕНА (каркас, контракты, инфраструктура)
- **Фаза 1**: ЗАВЕРШЕНА (Auth Service + API Gateway) — 26 тестов, отчёт: `docs/phase-1-report.md`
- **Фаза 2**: В ОЧЕРЕДИ (Academic Service)
- Полный план фаз: см. `docs/phases-plan.md`

## Архитектура (кратко)

5 сервисов + Gateway + 2 контейнера Notification:

| Сервис | Порт | Стек | БД |
|--------|------|------|----|
| API Gateway | 8080 | Spring Cloud Gateway | — |
| Auth Service | 9090 | Spring Boot | Redis |
| Academic Service | 9091 | Spring Boot | PostgreSQL (academic_db) + Redis cache |
| Schedule Service | 9092 | Spring Boot | PostgreSQL (schedule_db) |
| Attendance Service | 9093 | Spring Boot | MongoDB (attendance_db) |
| Notification Web | 9094 | Spring Boot WebSocket | — |
| Notification Bot | — | Python Aiogram 3 | — |

Между сервисами: gRPC. Асинхронные события: RabbitMQ (fanout exchange).

## Правила кодирования (ОБЯЗАТЕЛЬНО)

### Contract-first

- Каждый сервис имеет `*-api-contract` (чистый `java-library`) и `*-app` (Spring Boot)
- Контроллер `implements` интерфейс из контракта. Маппинги ТОЛЬКО в интерфейсе
- Request DTO = Java `record`. Response DTO = класс (для HATEOAS `RepresentationModel`)
- **БЕЗ Lombok в контрактных модулях** (`*-api-contract`). Lombok допустим только в `*-app` (entity, внутренние классы)

### Enum-ы

- В Java: `UPPER_CASE` (например `UserRole.ADMIN`)
- В PostgreSQL: `lowercase` строки (например `'admin'`)
- Конвертация через `LowercaseEnumConverter` с `autoApply=true`
- **НИКОГДА** не используй `@Enumerated(EnumType.ORDINAL)` — только строки

### База данных

- Все значения в PostgreSQL хранятся в **нижнем регистре**
- Миграции через Flyway (`src/main/resources/db/migration/V{N}__description.sql`)
- `ddl-auto: validate` — Hibernate только проверяет, НЕ создаёт схему
- Soft delete для пользователей (status = 'archived'), никогда DELETE
- PK: `BIGSERIAL` (Long в Java)
- Временные метки: `TIMESTAMPTZ` (UTC)

### REST API

- HATEOAS Level 3: `EntityModel<T>`, `PagedModel<EntityModel<T>>`, `_links`
- Ошибки: RFC 7807 Problem Details (`ErrorResponse` record)
- Swagger/OpenAPI: аннотации `@Operation`, `@ApiResponse` в контрактных интерфейсах
- `@ControllerAdvice` — централизованная обработка ошибок, контроллер только бросает исключения
- PUT = полное обновление, PATCH = частичное (отдельные DTO)

### Пакетная структура (Attendance Service)

- `checkin/` — домен отметок
- `report/` — домен отчётов (ИЗОЛИРОВАН)
- `shared/port/` — интерфейс `AttendanceReadPort` для связи между доменами
- `report/` НИКОГДА не импортирует из `checkin/` напрямую

### Именование

- Пакеты: `ru.rutcampustrack.{service}.{module}`
- REST пути: `/api/{service}/...` (через Gateway)
- gRPC: `ru.rutcampustrack.{service}.grpc`
- Event types: `{domain}.{action}` (например `lesson.started`, `attendance.marked`)

## Структура репозитория

```
rutcampustrack/
├── CLAUDE.md                           ← ЭТОТ ФАЙЛ
├── build.gradle.kts                    ← корневой Gradle
├── settings.gradle.kts                 ← все подпроекты
├── docker-compose.yml                  ← PostgreSQL×2, MongoDB, Redis, RabbitMQ
├── proto/                              ← gRPC контракты (.proto)
├── event-schemas/                      ← JSON Schema для событий RabbitMQ
├── docs/
│   ├── phase-0-report.md              ← отчёт фазы 0 (контекст проекта)
│   ├── architecture.md                ← детальная архитектура
│   ├── job-stories.md                 ← все user/job stories
│   └── database-schema.md            ← схема БД
├── services/
│   ├── api-gateway/                    ← Spring Cloud Gateway
│   ├── auth-service/                   ← JWT, OTP
│   ├── academic-service/
│   │   ├── academic-api-contract/     ← DTO, интерфейсы, enum-ы
│   │   └── academic-app/             ← Spring Boot app
│   ├── schedule-service/
│   │   ├── schedule-api-contract/
│   │   └── schedule-app/
│   ├── attendance-service/
│   │   ├── attendance-api-contract/
│   │   └── attendance-app/
│   ├── notification-web/              ← Java WebSocket push
│   └── notification-bot/              ← Python Aiogram
└── frontends/
    ├── mini-app/                       ← React (Telegram Mini App)
    ├── web-panel/                      ← Angular (админка)
    └── landing/                        ← HTML + CSS
```

## Роли в системе

- `ADMIN` — управление пользователями, группами, семестрами
- `TEACHER` — read-only журнал, статистика. БЕЗ Telegram
- `STUDENT` — геоотметка, excuse-тикеты, ДЗ трекер
- `STUDENT + is_headman=true` — расширенные права старосты
- Помощник старосты — студент с делегированными правами (`headman_assistants`)

## Статусы посещаемости

| Статус | В журнале | В статистике |
|--------|-----------|-------------|
| `present` | б | да |
| `absent` | н | да |
| `excused` | у | да (уважит.) |
| `free_attendance` | сп | да (уважит.) |
| `cancelled` | отменена | нет |

## Ключевые бизнес-правила

- Окно геоотметки: 5 мин до пары → вся пара → 5 мин после
- 3 напоминания об отметке: начало, середина, конец пары. После пары — удалить сообщения
- Автоматический `absent` при закрытии пары для неотметившихся
- Будущие пары (planned) и отменённые (cancelled) НЕ влияют на статистику
- Excuse-тикет: студент создаёт → выбирает пары → прикрепляет файлы → файлы пересылаются старосте через Telegram (не хранятся в системе)
- Логин: `student00001`, `teacher00001`. Тестовые: `student`, `teacher`, `admin`
- Порог красной зоны: глобальный (admin) → группа (headman) → предмет (headman)

## Запуск для разработки

```bash
# Инфраструктура
docker compose up -d

# Сборка
$env:JAVA_HOME = "C:\Users\maksd\.jdks\ms-21.0.9"
.\gradlew.bat build

# Проверка
docker compose ps
```

## Детальная документация

- **План всех фаз**: `docs/phases-plan.md` ← ЧИТАТЬ ПЕРЕД НАЧАЛОМ КАЖДОЙ ФАЗЫ
- Архитектура: `docs/architecture.md`
- Job Stories: `docs/job-stories.md`
- Схема БД: `docs/database-schema.md`
- Отчёт Фазы 0: `docs/phase-0-report.md`

## Инструкция для Claude Code

При начале работы над новой фазой:
1. Прочитай `docs/phases-plan.md` — там детальное описание что реализовать
2. Прочитай `docs/job-stories.md` — бизнес-требования
3. Прочитай `docs/database-schema.md` — структура БД
4. Создавай код в соответствии с правилами из раздела "Правила кодирования" выше
5. После завершения фазы — обнови `docs/phase-{N}-report.md` и статус в этом файле