# RutCampusTrack — Отчёт Фазы 0: Контракты и инфраструктура

## Дата: Март 2026

## Контекст проекта

RutCampusTrack (ранее RUT-UIT) — микросервисная система учёта посещаемости для вуза РУТ МИИТ. Проект перестраивается с нуля: три отдельных бэкенда (Spring Boot web, Python FastAPI + Aiogram bot, Telegram Mini App) заменяются единой микросервисной архитектурой.

**Разработчик**: один человек (Persik), lead developer и sysadmin.  
**Целевая аудитория**: 500–5000 студентов, несколько факультетов.  
**Цели**: продакшен-система + портфолио-кейс с полноценной микросервисной архитектурой.

---

## Архитектурные решения (зафиксированы)

### Сервисы (5 + Gateway + 2 контейнера Notification)

1. **API Gateway** (Spring Cloud Gateway, :8080) — единственный внешний порт, JWT-валидация, маршрутизация
2. **Auth Service** (Java, :9090) → Redis — JWT, OTP, управление токенами
3. **Academic Service** (Java, :9091) → PostgreSQL (academic_db) + Redis (кэш) — структура вуза, пользователи, группы, предметы, ДЗ
4. **Schedule Service** (Java, :9092) → PostgreSQL (schedule_db) — расписание, пары
5. **Attendance Service** (Java, :9093) → MongoDB (attendance_db) — отметки + отчёты (report/ изолирован через порт-интерфейс)
6. **Notification Web** (Java, :9094) — WebSocket push для веб-клиентов
7. **Notification Bot** (Python/Aiogram) — Telegram уведомления

### Ключевые решения

- **Monorepo** на GitHub, Gradle Kotlin DSL, Java 21, Spring Boot 3.4
- **Contract-first**: каждый сервис имеет `*-api-contract` модуль (DTO, интерфейсы, enum-ы, исключения, валидация). Без Lombok в контрактах. Request — Java record, Response — класс с ручными геттерами + HATEOAS
- **Polyglot persistence**: PostgreSQL (структура, расписание) + MongoDB (посещаемость) + Redis (OTP, кэш)
- **Enum-ы как lowercase строки** в PostgreSQL через `LowercaseEnumConverter`
- **HATEOAS Level 3**: `RepresentationModel`, `EntityModel`, `PagedModel` с `_links`
- **RFC 7807 Problem Details** для ошибок
- **gRPC** между сервисами (proto-контракты в `proto/`)
- **RabbitMQ** (fanout exchange) для событий → оба Notification-контейнера получают копию
- **Flyway** для миграций PostgreSQL
- **Swagger/OpenAPI** через springdoc-openapi, аннотации в контрактных интерфейсах

### Роли

- `ADMIN` — управление пользователями, группами, семестрами, dashboard
- `TEACHER` — read-only журнал, статистика, экспорт. Без Telegram
- `STUDENT` — геоотметка, excuse-тикеты, личный трекер ДЗ
- `STUDENT + is_headman=true` — расширенные права: отметка, расписание, предметы, помощники

### Авторизация

- Логин: `student00001`, `teacher00001` (автогенерация при создании пользователя)
- Тестовые: `student`, `teacher`, `admin` (без цифр)
- Студенты получают пароль через Telegram-бота при `/start`
- Преподаватели получают пароль от админа лично
- OTP через Redis с TTL 120 сек

### Статусы посещаемости

| Статус | Символ | В статистике |
|--------|--------|-------------|
| present | б | да (+) |
| absent | н | да (−) |
| excused | у | да (уваж.) |
| free_attendance | сп | да (уваж.) |
| cancelled | отменена | нет |

### Уважительные причины

- `illness`, `summons`, `university_order`, `exemption`, `free_attendance`, `other`
- Excuse-тикет: студент создаёт тикет на несколько пар, прикрепляет файлы → файлы пересылаются старосте через Telegram (не хранятся в системе)
- `free_attendance` — требует подтверждения старосты

### Помощники старосты

Права: `mark_attendance`, `manage_excuses`, `manage_homework`, `cancel_lessons`, `view_stats`

### Порог «красной зоны»

Каскад: предмет+группа (высший) → группа → глобальный (низший). Админ задаёт глобальный, староста переопределяет.

### Фронтенды

- **Telegram Mini App** (React + Vite) — студенты, геоотметка
- **Веб-панель** (Angular) — админы, преподаватели, старосты
- **Лендинг** (HTML + CSS) — статика

---

## Что сделано в Фазе 0

### 1. Структура монорепо `rutcampustrack/`

Полное дерево директорий для всех сервисов с разделением на `api-contract` и `app`.

### 2. Gradle build-файлы

- Корневой `settings.gradle.kts` (10 подпроектов) + `build.gradle.kts` (Java 21, UTF-8)
- 3 API-контракта: чистые `java-library` (Jakarta Validation + Spring Web + HATEOAS + Swagger annotations)
- 6 Spring Boot приложений с правильными зависимостями
- `gradle-wrapper.properties` (Gradle 8.12)

### 3. Docker Compose

PostgreSQL ×2 (academic_db, schedule_db), MongoDB, Redis, RabbitMQ. Все с health checks, named volumes, private network. `.env.example` с дефолтными паролями.

### 4. Proto-контракты (gRPC)

- `academic.proto` — 7 RPC: GetGroup, GetGroupMembers, GetTeacherSubjects, IsHeadman, GetActiveSemester, GetCampusGeofence, GetUserById
- `schedule.proto` — 3 RPC: GetActiveLesson, GetLessonById, GetLessonsByGroup

### 5. Event schemas (JSON Schema)

6 событий: `lesson.started`, `lesson.closed`, `lesson.cancelled`, `attendance.marked`, `excuse.requested`, `late_checkin.requested`, `homework.published`

### 6. Java Enum-ы

В контрактных модулях: `UserRole`, `AccountStatus`, `SubjectType`, `AssistantPermission`, `WeekType`, `LessonStatus`, `AttendanceStatus`, `ExcuseType`, `AttendanceSource`, `ExcuseTicketStatus`

### 7. LowercaseEnumConverter + конкретные конвертеры

Универсальный `LowercaseEnumConverter<E>` + `EnumConverters` с `autoApply=true` для Academic и Schedule сервисов.

### 8. Flyway baseline миграции

- `academic_db`: V1__baseline.sql — 12 таблиц (users, groups, semesters, subjects, teacher_subject_groups, headman_assistants, campus_settings, attendance_thresholds, homeworks, homework_completions, student_group_history, password_reset_tokens)
- `schedule_db`: V1__baseline.sql — 2 таблицы (schedule_items, lessons)

### 9. application.yml для всех сервисов

Порты, подключения к БД, Redis, RabbitMQ, Flyway, Swagger.

### 10. ErrorResponse (RFC 7807) + ResourceNotFoundException

В `academic-api-contract` — переиспользуется всеми сервисами.

### 11. Notification Bot

`requirements.txt` (aiogram 3.15, aio-pika, grpcio) + `.env.example`

### 12. .gitignore, README.md

---

## Что НЕ сделано (следующие фазы)

- [ ] **Фаза 1**: Auth Service + API Gateway (JWT, OTP, login/refresh/logout)
- [ ] **Фаза 2**: Academic Service (CRUD, gRPC-сервер, Redis-кэш)
- [ ] **Фаза 3**: Schedule + Attendance (пары, отметки, отчёты)
- [ ] **Фаза 4**: Notification (WebSocket + Telegram bot)
- [ ] **Фаза 5**: Фронтенды (Mini App, Web Panel, Landing)
- [ ] **Фаза 6**: CI/CD, мониторинг, документация

---

## Как запустить

```bash
git clone https://github.com/YOUR_USERNAME/rutcampustrack.git
cd rutcampustrack
cp .env.example .env
docker compose up -d        # поднять БД, Redis, RabbitMQ
docker compose ps            # проверить статус
./gradlew build              # собрать Java-сервисы (пока без запуска)
```

---

## Дерево файлов

```
rutcampustrack/
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
├── .env.example
├── .gitignore
├── README.md
├── gradle/wrapper/gradle-wrapper.properties
├── proto/
│   ├── academic.proto
│   └── schedule.proto
├── event-schemas/
│   ├── lesson.started.json
│   ├── lesson.closed.json
│   ├── lesson.cancelled.json
│   ├── attendance.marked.json
│   ├── excuse.requested.json
│   ├── late_checkin.requested.json
│   └── homework.published.json
├── docs/
│   └── phase-0-report.md (этот файл)
├── services/
│   ├── api-gateway/
│   │   ├── build.gradle.kts
│   │   └── src/main/resources/application.yml
│   ├── auth-service/
│   │   ├── build.gradle.kts
│   │   └── src/main/resources/application.yml
│   ├── academic-service/
│   │   ├── academic-api-contract/
│   │   │   ├── build.gradle.kts
│   │   │   └── src/main/java/ru/rutcampustrack/academic/contract/
│   │   │       ├── enums/ (UserRole, AccountStatus, SubjectType, AssistantPermission)
│   │   │       └── exception/ (ErrorResponse, ResourceNotFoundException)
│   │   └── academic-app/
│   │       ├── build.gradle.kts
│   │       └── src/main/
│   │           ├── java/ru/rutcampustrack/academic/config/
│   │           │   ├── LowercaseEnumConverter.java
│   │           │   └── EnumConverters.java
│   │           └── resources/
│   │               ├── application.yml
│   │               └── db/migration/V1__baseline.sql
│   ├── schedule-service/
│   │   ├── schedule-api-contract/
│   │   │   ├── build.gradle.kts
│   │   │   └── src/.../enums/ (WeekType, LessonStatus)
│   │   └── schedule-app/
│   │       ├── build.gradle.kts
│   │       └── src/main/
│   │           ├── java/.../config/EnumConverters.java
│   │           └── resources/ (application.yml, db/migration/V1__baseline.sql)
│   ├── attendance-service/
│   │   ├── attendance-api-contract/
│   │   │   ├── build.gradle.kts
│   │   │   └── src/.../enums/ (AttendanceStatus, ExcuseType, AttendanceSource, ExcuseTicketStatus)
│   │   └── attendance-app/
│   │       ├── build.gradle.kts
│   │       └── src/main/resources/application.yml
│   ├── notification-web/
│   │   ├── build.gradle.kts
│   │   └── src/main/resources/application.yml
│   └── notification-bot/
│       ├── requirements.txt
│       └── .env.example
└── frontends/
    ├── mini-app/
    ├── web-panel/
    └── landing/
```

---

## Инструкция для восстановления контекста

Если этот файл загружен в новый чат, Claude должен понять:

1. Проект: микросервисная система учёта посещаемости для вуза
2. Текущая фаза: 0 завершена, следующая — Фаза 1 (Auth Service + API Gateway)
3. Все архитектурные решения зафиксированы выше
4. Стек: Java 21, Spring Boot 3.4, Gradle, PostgreSQL, MongoDB, Redis, RabbitMQ, gRPC, React, Angular, Python/Aiogram
5. Подход: contract-first, HATEOAS, RFC 7807, enum lowercase, Flyway, без Lombok в DTO
6. Разработчик работает в IntelliJ IDEA на Windows, деплой на VPS с Docker

Полные документы: Job Stories, Database Schema, Architecture — см. папку docs/ или запросить у пользователя.
