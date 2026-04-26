# RUT-UIT v2 — Архитектура микросервисной системы учёта посещаемости

## 1. Обзор

Система учёта посещаемости для вуза, построенная на микросервисной архитектуре с polyglot persistence. Заменяет три отдельных бэкенда (Spring Boot web, Python FastAPI + Aiogram bot, Telegram Mini App) единой платформой с одним API Gateway.

### Принципы

- **Database per Service** — каждый сервис владеет только своей БД, прямых межсервисных SQL/Mongo-запросов нет
- **Contract-first** — все межсервисные интерфейсы описаны в `.proto` (gRPC) и OpenAPI (REST), код генерируется из контрактов
- **Single Responsibility** — сервис делает одно и отвечает за одно
- **Один внешний порт** — клиенты видят только API Gateway `:8080`; все внутренние сервисы изолированы в Docker private network
- **Polyglot stack** — Java (Spring Boot) для бизнес-сервисов, Python (Aiogram) для Telegram-бота, React для Mini App, Angular для веб-панели

### Стек технологий

| Компонент | Технология | Версия |
|-----------|-----------|--------|
| Бизнес-сервисы | Java 21 + Spring Boot 3.3 | LTS |
| API Gateway | Spring Cloud Gateway | 4.x |
| Telegram Bot | Python 3.12 + Aiogram 3.x | Stable |
| gRPC | grpc-spring-boot-starter | 3.x |
| Message Broker | RabbitMQ | 3.13 |
| СУБД (структура) | PostgreSQL | 16 |
| СУБД (посещаемость) | MongoDB | 7 |
| Кэш / OTP | Redis | 7 |
| Контейнеризация | Docker + Docker Compose | Latest |
| CI/CD | GitHub Actions | — |
| Фронтенд (Mini App) | React + Vite + TypeScript | — |
| Фронтенд (PWA «RutTrack») | React + Vite + TypeScript + PWA | — |
| Фронтенд (Веб-панель) | Angular + TypeScript | 18 |
| Фронтенд (Лендинг) | HTML + CSS + минимум JS | — |

---

## 2. Топология сервисов

### Перечень сервисов (5 + Gateway + 2 контейнера Notification)

```
┌──────────────────────────────────────────────────────────────────────┐
│                        DOCKER PRIVATE NETWORK                        │
│                                                                      │
│  Клиенты ──► [API Gateway :8080]  Spring Cloud Gateway               │
│  (Web Panel, PWA, Mini App)                                          │
│               │  JWT-валидация (публичный ключ, локально)            │
│               │  Маршрутизация по пути                               │
│               │  Rate Limiting, Correlation ID                       │
│               │                                                      │
│               ├──► [Auth Service :9090]          → Redis (OTP, JWT)  │
│               ├──► [Academic Service :9091]      → PostgreSQL        │
│               │                                    + Redis (кэш)     │
│               ├──► [Schedule Service :9092]      → PostgreSQL        │
│               └──► [Attendance Service :9093]    → MongoDB           │
│                     ├── checkin/  (домен отметок)                    │
│                     └── report/   (домен отчётов, изолирован)        │
│                                                                      │
│  [Notification Web :9094]  Java                                      │
│     ├── WebSocket push    → Web Panel, PWA (real-time)               │
│     ├── Web Push adapter  → Service Worker → PWA (background push)   │
│     └── REST: /push/subscribe, /vapid-public-key                     │
│  [Notification Bot]        Python — Telegram уведомления             │
│                                                                      │
│  [RabbitMQ :5672]  ← события от Schedule, Attendance                │
│  [Redis :6379]     ← OTP, кэш Academic, reminder msgs, VAPID keys  │
└──────────────────────────────────────────────────────────────────────┘
```

### Матрица: сервис → хранилище

| Сервис | PostgreSQL | MongoDB | Redis | RabbitMQ |
|--------|-----------|---------|-------|----------|
| Auth Service | ❌ | ❌ | ✅ (OTP, JWT key) | ❌ |
| Academic Service | ✅ (academic_db) | ❌ | ✅ (кэш) | Publish: `group.updated`, `semester.archived` |
| Schedule Service | ✅ (schedule_db) | ❌ | ❌ | Publish: `lesson.started`, `lesson.closed` |
| Attendance Service | ❌ | ✅ (attendance_db) | ❌ | Publish: `attendance.marked`, `attendance.session.closed` |
| Notification Web | ❌ | ✅ (push_subscriptions) | ✅ (VAPID keys) | Consume: все события |
| Notification Bot | ❌ | ❌ | ✅ (reminder msgs) | Consume: все события |

---

## 3. Описание сервисов

### 3.1 API Gateway

**Стек:** Spring Cloud Gateway (Java)

**Роль:** единственная точка входа для всех клиентов. Никакой бизнес-логики, никакого обращения к БД.

**Функции:**
- JWT-валидация (проверка подписи по публичному ключу, без сетевого вызова к Auth Service)
- Инжектирование заголовков: `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman`
- Маршрутизация по пути: `/auth/**` → Auth, `/academic/**` → Academic, `/schedule/**` → Schedule, `/attendance/**` → Attendance, `/reports/**` → Attendance (модуль report)
- Rate limiting (защита от флуда геолокационными check-in)
- Correlation ID на каждый запрос (для трассировки в логах)

**Порт наружу:** `8080` (единственный открытый порт всей системы)

---

### 3.2 Auth Service

**Стек:** Java Spring Boot
**Порт:** 9090
**Хранилище:** Redis
**Gradle-модули (M12):** `auth-api-contract` (java-library, DTO + interfaces `AuthApi`/`WsTicketApi`/`InternalIssuerApi`/`InternalWsTicketApi`, без Lombok) + `auth-app` (Spring Boot runtime, controllers `implements` интерфейсы). Contract-first compliant — mapping annotations только в интерфейсах.

**Роль:** выдача и управление токенами. Единственный сервис, знающий секреты подписи JWT.

**REST API (через Gateway):**

| Метод | Путь | Роль | Описание |
|-------|------|------|----------|
| POST | `/auth/login` | Public | Логин по email/password → JWT |
| POST | `/auth/otp/request` | Public | Запрос OTP → 204 No Content (код не в ответе, летит боту через Rabbit) |
| POST | `/auth/otp/verify` | Public | Проверка OTP → JWT |
| POST | `/auth/refresh` | Authenticated | Обновление Access Token |
| POST | `/auth/logout` | Authenticated | Инвалидация Refresh Token |
| POST | `/auth/change-password` | Authenticated | Смена пароля (currentPassword + newPassword) |

**JWT Claims:**
```json
{
  "sub": "user_id (UUID)",
  "role": "ADMIN | TEACHER | STUDENT",
  "group_id": "UUID (для студентов)",
  "is_headman": true,
  "exp": 1234567890
}
```

**Redis-ключи:**
```
otp:{telegram_id}           → "481927"          TTL: 120 сек
otpAttempts:{telegram_id}   → "2"               TTL: 300 сек
otpSent:{telegram_id}       → "true"            TTL: 60 сек
jwt:public_key              → "<PEM>"           TTL: 3600 сек
refresh:{user_id}:{jti}     → "valid"           TTL: 7 дней
```

**OTP flow (M09 G2 · 08 P0-2, event-driven):**

Раньше `POST /auth/otp/request` возвращал код в HTTP body — он
просачивался в логи/proxy/APM и ослаблял security-модель OTP.
Теперь Auth публикует событие `otp.requested` в RabbitMQ, а bot читает
код из payload и отправляет его в Telegram.

```
┌──────────┐  POST /auth/otp/request             ┌────────────┐
│ Frontend │─────────────────────────────────────>│ Auth :9090 │
│ (Login)  │<──────── 204 No Content ─────────── │            │
└──────────┘                                      └─────┬──────┘
                                                        │
                                         Redis SET      │
                                         otp:{tg} = {code}
                                         TTL 120s       │
                                                        │
                                        Event publish   │
                               rut-uit.events (fanout)  ▼
                                                 ┌─────────────┐
                                                 │  RabbitMQ   │
                                                 └─────┬───────┘
                                                       │
                                          otp.requested│ payload:
                                          consumer     │   telegram_id
                                                       │   code
                                                       ▼   ttl_seconds
                                                 ┌─────────────┐
                                                 │  Bot        │
                                                 │  /login     │
                                                 │  send_msg   │
                                                 └─────────────┘
```

**Свойства:**
- 204 ответ без body — код **не** возвращается REST-клиенту.
- `code` существует в двух местах: Redis-ключ `otp:{telegram_id}` (для
  последующего `verifyOtp`) и Rabbit-payload (для отправки боту).
- Fire-and-forget: если Rabbit недоступен, `DomainEventListener`
  логирует warning, но `/auth/otp/request` всё равно возвращает 204.
  Клиент нажимает «Resend» → новый код в Redis (TTL перезаписывается) +
  новый event. Старый TTL выпадает без последствий (self-healing).
- Прямая публикация (без shared-outbox): OTP-код эфемерен, persistence
  в Postgres `auth_outbox` ослабила бы security-модель
  (DECISIONS M09 D4).

**Не общается** с другими сервисами — источник доверия, не потребитель.
Единственный Rabbit publisher — эфемерные OTP-события для бота.

---

### 3.3 Academic Service

**Стек:** Java Spring Boot  
**Порт:** 9091  
**Хранилище:** PostgreSQL (academic_db) + Redis (кэш)

**Роль:** справочник структуры вуза. Данные меняются редко, читаются часто всеми остальными сервисами.

**PostgreSQL-таблицы:**
- `users` — все роли в одной таблице (ADMIN, TEACHER, STUDENT), флаг `is_headman`, статус (ACTIVE, EXPELLED, SUSPENDED)
- `semesters` — с ограничением: только один активный (partial unique index)
- `groups` — учебные группы
- `student_group_history` — история переводов/отчислений
- `subjects` — предметы (без уникальности по имени)
- `teacher_subject_groups` — привязка преподаватель–предмет–группа
- `campus_settings` — геофенс кампуса (одна строка)
- `password_reset_tokens` — токены смены пароля

**REST API (через Gateway):**

| Метод | Путь | Роль | Описание |
|-------|------|------|----------|
| GET | `/academic/semesters` | ADMIN | Список семестров |
| POST | `/academic/semesters` | ADMIN | Создать семестр |
| PUT | `/academic/semesters/{id}/activate` | ADMIN | Активировать семестр |
| GET/POST/PUT/DELETE | `/academic/groups/**` | ADMIN | CRUD групп |
| GET/POST/PUT/DELETE | `/academic/subjects/**` | ADMIN | CRUD предметов |
| GET/POST/DELETE | `/academic/assignments/**` | ADMIN | Привязки преподаватель–предмет–группа |
| GET/POST/PUT | `/academic/users/**` | ADMIN | Управление пользователями |
| GET | `/academic/groups/{id}/members` | ADMIN, TEACHER, HEADMAN | Состав группы |

**gRPC-сервер (для внутренних вызовов):**
```protobuf
service AcademicService {
  rpc GetGroup(GroupRequest) returns (GroupResponse);
  rpc GetGroupMembers(GroupMembersRequest) returns (GroupMembersResponse);
  rpc GetTeacherSubjects(TeacherSubjectsRequest) returns (TeacherSubjectsResponse);
  rpc IsHeadman(HeadmanCheckRequest) returns (HeadmanCheckResponse);
  rpc GetActiveSemester(Empty) returns (SemesterResponse);
  rpc GetCampusGeofence(Empty) returns (GeofenceResponse);
  rpc GetUserById(UserRequest) returns (UserResponse);
}
```

**Redis-кэш:**
```
group:{group_id}:info         → JSON          TTL: 10 мин
group:{group_id}:members      → JSON          TTL: 5 мин
teacher:{teacher_id}:subjects → JSON          TTL: 10 мин
semester:active               → JSON          TTL: 30 мин
campus:geofence               → JSON          TTL: 60 мин
```

**Публикует события в RabbitMQ:** `group.updated`, `semester.archived`

---

### 3.4 Schedule Service

**Стек:** Java Spring Boot  
**Порт:** 9092  
**Хранилище:** PostgreSQL (schedule_db)

**Роль:** управление расписанием и жизненным циклом пар.

**PostgreSQL-таблицы:**
- `schedule_items` — шаблон расписания (повторяющийся еженедельно, с учётом чётности недели)
- `lessons` — конкретные экземпляры пар на дату (генерируются лениво)

**REST API (через Gateway):**

| Метод | Путь | Роль | Описание |
|-------|------|------|----------|
| GET | `/schedule/groups/{id}/items` | HEADMAN, TEACHER | Шаблон расписания группы |
| POST | `/schedule/items` | HEADMAN | Создать слот расписания |
| PUT | `/schedule/items/{id}` | HEADMAN | Изменить слот |
| DELETE | `/schedule/items/{id}` | HEADMAN | Удалить слот |
| GET | `/schedule/groups/{id}/lessons` | HEADMAN, TEACHER, STUDENT | Пары на период |
| PUT | `/schedule/lessons/{id}/cancel` | HEADMAN | Отменить пару |
| PUT | `/schedule/lessons/{id}/uncancel` | HEADMAN | Восстановить пару |

**gRPC-сервер:**
```protobuf
service ScheduleService {
  rpc GetActiveLesson(ActiveLessonRequest) returns (LessonResponse);
  rpc GetLessonById(LessonByIdRequest) returns (LessonResponse);
  rpc GetLessonsByGroup(LessonsByGroupRequest) returns (LessonsResponse);
}
```

**Вызывает gRPC:**
- Academic Service: `GetGroup`, `GetTeacherSubjects` — проверка прав на создание/изменение расписания

**Публикует события:** `lesson.started`, `lesson.closed`

**Автоматические задачи (Spring `@Scheduled`):**
- Смена статуса пар: PLANNED → ACTIVE (по времени начала), ACTIVE → CLOSED (по времени окончания)
- Ленивая генерация `lessons` из `schedule_items` при первом запросе на неделю

---

### 3.5 Attendance Service

**Стек:** Java Spring Boot  
**Порт:** 9093  
**Хранилище:** MongoDB (attendance_db)

**Роль:** приём фактов посещаемости и построение отчётов. Два изолированных домена внутри одного сервиса.

#### Пакетная структура (изоляция доменов)

```
attendance-service/
└── src/main/java/ru/rutuit/attendance/
    ├── checkin/                         ← домен "отметки"
    │   ├── controller/
    │   │   └── CheckInController.java
    │   ├── service/
    │   │   └── CheckInService.java
    │   ├── repository/
    │   │   └── AttendanceRepository.java
    │   ├── model/
    │   │   └── AttendanceRecord.java
    │   └── grpc/
    │       └── AttendanceGrpcService.java
    │
    ├── report/                          ← домен "отчёты" (изолирован)
    │   ├── controller/
    │   │   └── ReportController.java
    │   ├── service/
    │   │   └── ReportService.java
    │   └── dto/
    │       └── AttendanceSummaryDto.java
    │
    ├── shared/
    │   └── port/
    │       └── AttendanceReadPort.java  ← интерфейс связи между доменами
    │
    └── config/
        └── ArchUnitRules.java           ← тесты на изоляцию пакетов
```

**Правило:** `report/` НИКОГДА не импортирует из `checkin/` напрямую. Связь только через `AttendanceReadPort` в `shared/`. При необходимости выноса Report в отдельный сервис — меняем реализацию порта с локального вызова на gRPC-клиент.

**ArchUnit-тест:**
```java
@ArchTest
static final ArchRule reportDoesNotAccessCheckinInternals =
    noClasses().that().resideInAPackage("..report..")
        .should().accessClassesThat()
        .resideInAnyPackage("..checkin.repository..", "..checkin.service..", "..checkin.model..");
```

**MongoDB-коллекция `attendances`:**
```json
{
  "_id": "ObjectId",
  "lesson_id": "Long",
  "user_id": "Long",
  "semester_id": "Long",
  "group_id": "Long",
  "subject_id": "Long",
  "lesson_date": "ISODate",
  "lesson_number": "Int",
  "status": "present | absent | excused | cancelled",
  "excuse": {
    "type": "ill | summons | order | exempt | other",
    "status": "PENDING | APPROVED | REJECTED",
    "note": "String",
    "approved_by": "Long",
    "approved_at": "ISODate"
  },
  "checkin_location": {
    "lat": "Double",
    "lng": "Double",
    "accuracy_m": "Double",
    "distance_from_campus_m": "Double"
  },
  "marked_by": "student_geo | headman | auto_scheduler | teacher_override",
  "created_at": "ISODate",
  "updated_at": "ISODate"
}
```

**Индексы MongoDB:**
```javascript
{ lesson_id: 1, user_id: 1 }                        // unique — идемпотентность
{ user_id: 1, semester_id: 1, lesson_date: -1 }     // отчёт студента
{ group_id: 1, semester_id: 1, subject_id: 1 }      // отчёт группы
{ lesson_id: 1 }                                     // все отметки пары
{ "excuse.status": 1, group_id: 1 }                  // фильтр по причинам
```

**REST API — модуль checkin:**

| Метод | Путь | Роль | Описание |
|-------|------|------|----------|
| POST | `/attendance/check-in` | STUDENT | Отметка с геолокацией |
| POST | `/attendance/manual` | HEADMAN | Ручная отметка студента |
| PUT | `/attendance/{id}/excuse` | HEADMAN | Установить уважительную причину |
| PUT | `/attendance/{id}/excuse/approve` | HEADMAN | Подтвердить причину |
| PUT | `/attendance/{id}/status` | TEACHER | Переопределить статус |

**REST API — модуль report:**

| Метод | Путь | Роль | Описание |
|-------|------|------|----------|
| GET | `/reports/group/{groupId}/subject/{subjectId}` | TEACHER, HEADMAN | Журнал группы по предмету |
| GET | `/reports/student/{studentId}` | STUDENT (свой), HEADMAN | Посещаемость студента |
| GET | `/reports/lesson/{lessonId}` | TEACHER, HEADMAN | Все отметки конкретной пары |
| GET | `/reports/semester/{semesterId}/summary` | ADMIN | Сводка по семестру |

**Вызывает gRPC:**
- Schedule Service: `GetActiveLesson` — есть ли активная пара
- Academic Service: `GetGroupMembers` — проверка членства, `GetCampusGeofence` — параметры геофенса, `GetTeacherSubjects` — авторизация преподавателя

**Публикует события:** `attendance.marked`, `attendance.session.closed`

---

### 3.6 Notification Service (два контейнера)

Один логический сервис, два контейнера с разными рантаймами. Оба подписаны на RabbitMQ через fanout exchange — каждый получает копию каждого события.

#### Notification Web (Java)

**Стек:** Java Spring Boot + Spring WebSocket + webpush-java + Caffeine
**Порт:** 9094
**Хранилище:** MongoDB `notification_db` (с M10) + Redis (VAPID)

**Роль:** stateful push-уведомления через три канала доставки **+
персистентная история** (M10, см. раздел «Notification History» ниже)
для cross-session unread-count и retrospective просмотра.

**Каналы доставки:**

**Канал 1 — WebSocket (real-time, вкладка открыта):**
- STOMP endpoint `/ws` с JWT-аутентификацией
- Доставка событий подключённым клиентам (Web Panel, PWA)

**Канал 2 — Web Push (background, PWA свёрнута/закрыта):**
- VAPID-ключи для подписи push-уведомлений
- MongoDB-коллекция `push_subscriptions` для хранения подписок
- REST API: `POST /api/ws/push/subscribe`, `DELETE /api/ws/push/subscribe`, `GET /api/ws/vapid-public-key`
- Автоматическое удаление подписки при HTTP 410 Gone
- Payload: JSON с `title`, `body`, `action_url`, `event_type`

**Канал 3 — Telegram (через Notification Bot):**
- Отдельный контейнер, см. ниже

**Подписан на RabbitMQ-события:**
- `lesson.started` → WebSocket push + Web Push студентам: «Пара началась, отметьтесь»
- `lesson.closed` → WebSocket push старосте: «Сессия отметки завершена»
- `lesson.cancelled` → WebSocket push + Web Push студентам: «Пара отменена»
- `attendance.marked` → WebSocket push студенту: подтверждение отметки
- `attendance.session.closed` → WebSocket push преподавателю: итоги посещаемости
- `homework.published` → WebSocket push + Web Push студентам: «Новое ДЗ»
- `excuse.requested`, `late_checkin.requested` → WebSocket push + Web Push старосте

**Redis-ключи:**
```
vapid:public_key    → "<base64>"     TTL: нет (постоянный)
vapid:private_key   → "<base64>"     TTL: нет (постоянный)
```

**WebSocket endpoint:** `ws://notification-web:9094/ws`

#### Notification Bot (Python)

**Стек:** Python 3.12 + Aiogram 3.x + aio-pika

**Роль:** Telegram-бот с OTP-авторизацией и push-уведомлениями.

**Функции:**
- Отправка OTP-кодов для авторизации (consumer `otp.requested` из Auth)
- Push-уведомления студентам о начале пар
- Команды бота: `/start`, `/login`, `/status`

**Подписан на RabbitMQ-события:** те же, что и Notification Web, плюс:
- `otp.requested` → `bot.send_message(telegram_id, code)` —
  payload.code подписан Auth'ом с TTL 120 сек, bot не сохраняет код сам
- `otp.verified` → удаление предыдущих Telegram-сообщений с кодом

**Вызывает gRPC:**
- Academic Service: `GetGroupMembers` — для массовой рассылки по группе

---

## 4. Протоколы и коммуникации

### Матрица коммуникаций

| От → К | Протокол | Тип | Формат |
|--------|---------|-----|--------|
| Клиенты → API Gateway | HTTP/REST | Синхронный | JSON |
| Gateway → Все сервисы | HTTP/REST | Синхронный | JSON |
| Attendance → Schedule | gRPC | Синхронный | Protobuf |
| Attendance → Academic | gRPC | Синхронный | Protobuf |
| Schedule → Academic | gRPC | Синхронный | Protobuf |
| Notification Bot → Academic | gRPC | Синхронный | Protobuf |
| Schedule → RabbitMQ | AMQP | Асинхронный | JSON |
| Attendance → RabbitMQ | AMQP | Асинхронный | JSON |
| RabbitMQ → Notification Web | AMQP | Асинхронный | JSON |
| RabbitMQ → Notification Bot | AMQP | Асинхронный | JSON |
| Notification Web → Клиенты | WebSocket | Двунаправленный | JSON |
| Notification Web → Service Worker → PWA | Web Push (VAPID) | Односторонний | JSON |

### JWT и передача identity

Gateway проверяет JWT локально и добавляет в заголовки downstream-запроса:
```
X-User-Id:     <long>
X-User-Role:   ADMIN | TEACHER | STUDENT
X-Group-Id:    <long>   (для студентов)
X-Is-Headman:  true | false
```

Downstream-сервисы НЕ знают о JWT — работают только с заголовками.

### Формат событий RabbitMQ

```json
{
  "event_type": "lesson.started",
  "event_id": "uuid",
  "occurred_at": "2025-09-01T08:30:00Z",
  "payload": {
    "lesson_id": 123,
    "group_id": 45,
    "subject_id": 67,
    "teacher_id": 89
  }
}
```

Exchange: `rut-uit.events` (fanout) → Queues: `notification-web.events`, `notification-bot.events`

### Lesson lifecycle (NEW-118, M09 G5)

Ключевой жизненный цикл `lessons` в schedule-service и события, которыми
он синхронизируется с downstream:

```
                   [ScheduleItem]
                        │
                        │ LessonGenerationService
                        ▼
                   ┌─────────┐      auto @ start  ┌────────┐
                   │ PLANNED │─────────────────▶│ ACTIVE │
                   └────┬────┘                   └────┬───┘
                        │                              │
                        │ headman/admin cancel          │ auto @ end
                        ▼                              ▼
                   ┌───────────┐                  ┌────────┐
                   │ CANCELLED │◀────────────────│ CLOSED │
                   └─────┬─────┘   retrospective  └────────┘
                         │
                         │ headman/admin restore
                         ▼
                     (→ PLANNED)
```

**Автоматические переходы** (`LessonStatusTransitionJob` tick):
- `PLANNED → ACTIVE` — в момент `start_time` в ту же дату.
- `ACTIVE → CLOSED` — в момент `end_time`. Неотметившиеся студенты
  помечаются `absent` (defence-in-depth в attendance).

**Ручные переходы** (staros+admin через `LessonService`):
- `cancelLesson(id, reason)` — допустим из **PLANNED/ACTIVE/CLOSED**.
  Ретроспективная отмена CLOSED полезна когда надо заменить пару и
  переразметить посещаемость задним числом (UX-требование).
  Записывает `cancel_reason`, `cancelled_by`, `cancelled_at`.
  Публикует **`lesson.cancelled`** (full snapshot, M09 G5).
- `restoreLesson(id)` — только из **CANCELLED → PLANNED**. Очищает
  `cancel_reason`/`cancelled_by`/`cancelled_at`. Если `start_time`
  уже прошло, следующий tick `LessonStatusTransitionJob` автоматически
  перепроведёт в ACTIVE/CLOSED.

**Physical DELETE** — отдельный сценарий, НЕ путать с CANCELLED:
- `LessonGenerationService.regenerateFromDate()` удаляет PLANNED-строки
  перед regenerate из обновлённого ScheduleItem.
- `SubjectDeletedCascadeService` удаляет cascade при `subject.deleted`
  из academic.

В обоих случаях публикуется **`lesson.deleted`** с массивом `lesson_ids`.
attendance-service удаляет orphan-attendance-doc'и по этим id'ам
(без этого они всплывают как дубликаты рядом с regenerated-lesson'ами).

**Matrix: какой event на что влияет**

| Event | Row state | attendance docs | UI message |
|-------|-----------|-----------------|------------|
| `lesson.cancelled` | `status=cancelled`, reason/by/at | docs → status=CANCELLED | «Пара отменена» студентам + старосте |
| `lesson.deleted` | row DELETED | orphan docs purged | silent (система, не user-facing) |

---

## 5. Фронтенды

### 5.1 Telegram Mini App (React + Vite + TypeScript)

**Аудитория:** студенты  
**Обоснование React:** Telegram Mini App SDK оптимизирован для React, быстрый старт через Vite, компактный bundle для мобильного контекста.

**Функции:**
- Геолокационная отметка (check-in)
- Просмотр своей посещаемости
- Просмотр расписания
- Уведомления через Telegram Bot

### 5.2 PWA Mobile Client «RutTrack» (React + Vite + TypeScript)

**Аудитория:** студенты, старосты, преподаватели (НЕ админы — они на веб-панели)  
**Обоснование React:** общий стек с Mini App, переиспользование API-клиента и компонентов, PWA-ready из коробки через Vite PWA plugin.

**Функции:**
- Расписание (кэшируется для офлайн-доступа)
- Геолокационная отметка (check-in, только онлайн)
- Журнал посещаемости (староста: редактирование, преподаватель: read-only)
- Статистика (кэшируется для офлайн-доступа)
- ДЗ (кэшируется для офлайн-доступа)
- Excuse и late_checkin (староста: подтверждение/отклонение)
- Web Push уведомления (background push через Service Worker)

**Service Worker:**
- Cache strategy: stale-while-revalidate для API-ответов, cache-first для static assets
- Offline fallback: расписание, статистика, ДЗ доступны для чтения
- Push event handler: показ нативного уведомления с action-кнопкой «Отметиться»
- При check-in офлайн: сообщение «Нет подключения к сети»

**Install UX:**
- Android/Chrome: перехват `beforeinstallprompt` → кастомный баннер «Установить RutTrack»
- iOS Safari: инструкция «Поделиться → На экран Домой» при первом визите

**Сосуществование с Mini App:**
- Mini App — быстрые действия в Telegram (геоотметка, статус)
- PWA — полноценный мобильный клиент (расписание, журнал, статистика, ДЗ, уведомления)
- Оба канала активны, не заменяют друг друга

### 5.3 Веб-панель (Angular + TypeScript)

**Аудитория:** администраторы, преподаватели, старосты  
**Обоснование Angular:** enterprise-фреймворк с встроенным DI, реактивными формами, HTTP-клиентом, роутингом — идеально для сложной админки с множеством форм, таблиц и дашбордов. Material Design 3 через Angular Material.

**Функции:**
- CRUD семестров, групп, предметов, привязок (ADMIN)
- Управление расписанием (HEADMAN)
- Журнал посещаемости с редактируемой сеткой (HEADMAN, TEACHER)
- Отчёты и аналитика (TEACHER, ADMIN)
- WebSocket-уведомления в реальном времени

### 5.4 Лендинг (HTML + CSS)

**Аудитория:** внешние посетители  
**Обоснование:** статическая страница не требует фреймворка, демонстрирует владение чистым HTML/CSS, SEO-friendly.

**Функции:**
- Описание системы
- Документация API (Swagger UI embed)
- Контакты и обратная связь

---

## 6. Хранилища данных

### Распределение

| Хранилище | Сервис-владелец | Обоснование |
|-----------|----------------|-------------|
| Redis | Auth Service (OTP, JWT key), Academic Service (кэш) | TTL для эфемерных данных, sub-ms latency |
| PostgreSQL: academic_db | Academic Service | ACID, сложные JOIN, FK-целостность для справочников |
| PostgreSQL: schedule_db | Schedule Service | Транзакционные изменения статусов пар |
| MongoDB: attendance_db | Attendance Service | Write-heavy, гибкая схема excuse, горизонтальное масштабирование |

### Связь между хранилищами

БД между собой **НЕ связаны**. Никаких cross-database JOIN. UUID/Long ID — общий язык: один и тот же `user_id` живёт в PostgreSQL (кто этот человек) и в MongoDB (что он делал), но физической связи нет — только gRPC-вызов в нужный момент.

### Soft delete

Пользователи никогда не удаляются физически. `users.status = 'EXPELLED'` + `student_group_history.left_at` сохраняет полную историю. Записи посещаемости в MongoDB остаются связными по `user_id` после отчисления.

---

## 7. Инфраструктура

### Docker Compose (упрощённая схема)

```yaml
services:
  # === GATEWAY ===
  api-gateway:
    build: ./services/api-gateway
    ports: ["8080:8080"]
    networks: [private_net]

  # === БИЗНЕС-СЕРВИСЫ ===
  auth-service:
    build: ./services/auth-service
    expose: ["9090"]
    depends_on: [redis]
    networks: [private_net]

  academic-service:
    build: ./services/academic-service
    expose: ["9091"]
    depends_on: [postgres-academic, redis]
    networks: [private_net]

  schedule-service:
    build: ./services/schedule-service
    expose: ["9092"]
    depends_on: [postgres-schedule]
    networks: [private_net]

  attendance-service:
    build: ./services/attendance-service
    expose: ["9093"]
    depends_on: [mongo-attendance]
    networks: [private_net]

  # === NOTIFICATION ===
  notification-web:
    build: ./services/notification-web
    expose: ["9094"]
    depends_on: [rabbitmq]
    networks: [private_net]

  notification-bot:
    build: ./services/notification-bot
    depends_on: [rabbitmq]
    networks: [private_net]

  # === ИНФРАСТРУКТУРА ===
  postgres-academic:
    image: postgres:16
    expose: ["5432"]
    volumes: [pg-academic-data:/var/lib/postgresql/data]
    networks: [private_net]

  postgres-schedule:
    image: postgres:16
    expose: ["5432"]
    volumes: [pg-schedule-data:/var/lib/postgresql/data]
    networks: [private_net]

  mongo-attendance:
    image: mongo:7
    expose: ["27017"]
    volumes: [mongo-data:/data/db]
    networks: [private_net]

  redis:
    image: redis:7
    expose: ["6379"]
    volumes: [redis-data:/data]
    networks: [private_net]

  rabbitmq:
    image: rabbitmq:3.13-management
    expose: ["5672"]
    ports: ["15672:15672"]  # Management UI (только для dev)
    networks: [private_net]

networks:
  private_net:
    driver: bridge

volumes:
  pg-academic-data:
  pg-schedule-data:
  mongo-data:
  redis-data:
```

### Структура монорепозитория

```
rut-uit/
├── proto/                              ← общие .proto контракты
│   ├── academic.proto
│   ├── schedule.proto
│   └── attendance.proto
├── event-schemas/                      ← JSON Schema для событий RabbitMQ
│   ├── lesson.started.json
│   ├── lesson.closed.json
│   ├── attendance.marked.json
│   └── attendance.session.closed.json
├── services/
│   ├── shared/                         ← shared foundations (M01)
│   │   ├── shared-web/                    RFC 9457 ErrorResponse + handlers + validation
│   │   ├── shared-events/                 DomainEvent base + publisher/consumer MDC
│   │   ├── shared-logback/                JSON appender + secrets masking
│   │   └── shared-test-containers/        Testcontainers fixtures (testFixtures scope)
│   ├── api-gateway/                    (Java Spring Boot)
│   ├── auth-service/                   (Java Spring Boot)
│   ├── academic-service/               (Java Spring Boot)
│   ├── schedule-service/               (Java Spring Boot)
│   ├── attendance-service/             (Java Spring Boot)
│   ├── notification-web/               (Java Spring Boot)
│   └── notification-bot/               (Python Aiogram)
├── frontends/
│   ├── mini-app/                       (React + Vite)
│   ├── pwa/                            (React + Vite + PWA «RutTrack»)
│   ├── web-panel/                      (Angular)
│   └── landing/                        (HTML + CSS)
├── docker-compose.yml
├── docker-compose.dev.yml
├── .github/
│   └── workflows/
│       ├── ci-java.yml
│       ├── ci-python.yml
│       ├── ci-frontend.yml
│       └── deploy.yml
└── README.md
```

### Shared modules (M01)

Четыре `java-library` модуля под `services/shared/` — foundations, на которых
строятся все 5 Java-сервисов. Подключаются как обычные зависимости, без
Spring Boot autoconfiguration (правило NEW-34). Подробный quick-start —
`shared-modules-usage.md`.

- **`shared-web`** — централизованный RFC 9457 `ErrorResponse` + `GlobalExceptionHandler`
  (9 стандартных Spring MVC handlers + catch-all), cross-field validation
  аннотации (`@StartBeforeEnd`, `@DateRangeValid`, `@ValidFile`), `JacksonConfig`
  (READ_UNKNOWN_ENUM_VALUES_AS_NULL / FAIL_ON_UNKNOWN_PROPERTIES=false /
  WRITE_DATES_AS_TIMESTAMPS=false), `@AdminAction` + aspect-заглушка (audit в M04),
  `SharedOpenApiCustomizer` заглушка (обогащение спеки в M06).
- **`shared-events`** — `DomainEvent` abstract base (`event_version`, `trace_id`,
  `occurred_at`, `source` в snake_case JSON), `@EventVersion` marker (reflection,
  default 1, inheritable), `AbstractEventPublisher.fillDefaults()` auto-заполняет
  из MDC/reflection/clock, `AbstractEventConsumer.withTraceContext()` put/restore MDC
  с cleanup на exception-path. Без привязки к AMQP — интеграция в M02.
- **`shared-logback`** — `shared/logback-base.xml` (JSON stdout через
  `LoggingEventCompositeJsonEncoder` с полями ts/level/logger/thread/msg/service/
  MDC[traceId,userId,eventType]/stack) + `MaskingJsonProvider` с regex-маскированием
  Bearer JWT / telegram_id / FCM endpoint в поле `msg`. Подключается одной
  строкой `<include resource="shared/logback-base.xml"/>`.
- **`shared-test-containers`** — `java-test-fixtures` модуль (подключается через
  `testImplementation(testFixtures(project(...)))`): `ContainerTestBase` со всеми
  4 контейнерами (Postgres 16 / Mongo 7 / Redis 7 / RabbitMQ 3.13, `reuse=true`)
  + `@DynamicPropertySource`, `GrpcInProcessFixture` (real gRPC round-trip без
  сети), `WireMockFixture` (динамический порт), `MigrationTestUtils` (поэтапный
  прогон Flyway).

Первый сервис-потребитель — `notification-web` (M01 Группа 8 acceptance).
Миграция остальных 4 сервисов — в M03/M04/M08.

### Reliable eventing (M02)

Доставка событий между сервисами через RabbitMQ гарантируется
**transactional outbox**-паттерном. Листенер пишет событие в таблицу
`{service}_outbox` в той же `@Transactional`, что и доменная операция;
отдельный `OutboxPublisherJob` читает `pending` и публикует в Rabbit.
Закрыто в M02 (cм. `docs/milestones/M02-reliable-eventing/`).

```
┌────────────┐
│ Service    │   @Transactional BEGIN
│ (academic, │      domain write  (rows in PG/Mongo)
│  schedule, │      applicationEventPublisher.publishEvent(event)
│  attendance)│          │
└────────────┘          ▼
         DomainEventListener @TransactionalEventListener(BEFORE_COMMIT)
                             outboxStorage.save(eventType, json)      // row в {service}_outbox
                         @Transactional COMMIT (либо rollback → outbox rollback вместе)
                         │
                         │ async, раз в 5s
                         ▼
                  OutboxPublisherJob @Scheduled + @SchedulerLock("outbox-publisher")
                    findPending(100) → for each: sender.send() → markSent
                    ошибка транспорта → markFailed (retry_count++)
                         │
                         ▼
                    RabbitTemplate.send(rut-uit.events, payload, Content-Type=application/json)
                         │
                         ▼
                    RabbitMQ fanout exchange → 3+ consumer queues
```

**Ключевые гарантии:**
- **Atomicity** — outbox.save идёт в той же tx что и доменная запись. Откат
  одного = откат другого (решено 02 P0-6 message loss).
- **At-least-once delivery** — если Rabbit недоступен, row остаётся `pending`,
  следующий tick публикует. `markSent` выполняется только после успеха sender'а.
- **Cluster safety** — `@SchedulerLock` (ShedLock) исключает конкурентную публикацию
  при scale-out (решено 03 P0-2 double-publish race). Lock name `outbox-publisher`
  — у каждого сервиса своя ShedLock-таблица в своей БД, так что 3 сервиса не
  конкурируют.
- **Retention** — `OutboxCleanupJob` (cron 3am) удаляет `sent` rows старше 7 дней
  (NEW-7). Настраивается через `rutcampustrack.outbox.retention-days:7`.
- **Observability** — Micrometer: `outbox.lag` (gauge, pending count),
  `outbox.published.total` / `outbox.failed.total` (counter, tag event_type).
- **Архитектурный инвариант** — ArchUnit `ScheduledMustHaveSchedulerLockTest`
  проверяет что любой `@Scheduled` метод имеет `@SchedulerLock` или явный
  `@SuppressWarnings("SingleInstance")` (NEW-28).

**shared-outbox** (`services/shared/shared-outbox/`) предоставляет:
- `OutboxStorage` — storage-agnostic API (save/findPending/markSent/markFailed/
  deleteSentBefore/countPending).
- `JpaOutboxStorage<E extends OutboxEntity>` — для PG (academic/schedule).
- `MongoOutboxStorage` — для Mongo (attendance), через `MongoTemplate`.
- `OutboxPublisherJob` — scheduled tick + SchedulerLock.
- `OutboxCleanupJob` — retention scheduler.
- `OutboxMetrics` — Micrometer gauge.

**Contract-тесты** (`*ContractIT` в каждом сервисе) валидируют реальный
payload из outbox против JSON Schema в `event-schemas/`:
- lesson.started + lesson.closed (schedule)
- lesson.cancelled (schedule)
- group.updated (academic)
- attendance.marked (attendance)

**JSON Schema $defs** — `event-schemas/_common.json` содержит shared-определения
(`eventId`, `occurredAt`, `traceId`, `eventVersion`, `lessonNumber`). 19 схем
используют их через `$ref` (versioning policy — `event-schemas.md`).

### Internal JWT и rate-limiting (M03a)

**Цель:** устранить plain-text trust boundary между Gateway и downstream
(Zero Trust Level 2) + защитить sensitive endpoints от brute/DoS.

**Token Exchange pipeline** — индустриальный pattern RFC 8693, GCP `iam.signJwt`:

```
Client → Gateway → auth-service.POST /internal/issue-internal-jwt
                   (X-Internal-Issuer-Secret, timing-safe compare)
         ↓
         Caffeine cache (userId,role) → IssuedToken (TTL 240s)
         ↓ cache miss / expiry
         Gateway получает подписанный RSA-256 Internal JWT (TTL 5 мин)
         ↓
         Downstream получает X-Internal-Token
         ↓
         shared-security/InternalJwtValidator:
         - проверяет signature через /auth/public-key
         - проверяет iss="rutcampustrack-auth", aud="rutcampustrack-internal"
         - exp + clock-skew 30s
         ↓
         DualModeUserContextFilter → RequestContext
```

**Ключевые инварианты:**
- Приватный ключ ТОЛЬКО в auth-service. Gateway — read-only consumer
  публичного ключа. Компрометация Gateway не даёт выпустить Internal JWT.
- Dual-mode (M03a deploy): Gateway шлёт `X-Internal-Token` **и** legacy
  `X-User-*` — downstream принимает любой (переходный период).
- Strict-mode (M03a финальный commit, v0.0.0-alpha.3): Gateway strip'ает
  `X-User-*`, downstream отвергает запросы без Internal JWT.
- Полная спецификация: `docs/api/internal-jwt-spec.md`.

**Rate-limiting** через Spring Cloud Gateway `RedisRateLimiter`:

- `/api/auth/otp/request` — 1 req / burst per IP (SMS-cost guard)
- `/api/auth/otp/verify-by-code` — 5 / burst per IP
- `/api/auth/login` — 5 per IP + 10 per `(ip, login)` composite (X-Login header)
- `/api/auth/refresh` — 30 per userId
- `/api/attendance/check-in` — 10 per userId
- `/api/{academic,schedule,attendance,push}/**` — 600 per IP (DDoS guard)

При 429: RFC 7807 Problem Details + `Retry-After: 60`.

**Fail-open:** при Redis outage `FailOpenRateLimiter` пропускает
запросы с WARN-логом, чтобы не DoS-нуть свой же сервис.

**Composite login key** (`LoginRateLimiter` в auth-service):
прогрессивная блокировка на composite `(ip, login)` — 5/10/20 fails
→ 5min/30min/2h block. Атакующий с одного IP больше не может DoS-
лочить чужой аккаунт.

Полная таблица лимитов и клиентские рекомендации: `docs/api/api-rate-limits.md`.

### Auth flow (cookie + ws-ticket + logout lifecycle) (M03b)

**Цель:** устранить XSS-уязвимость через localStorage для refresh-token,
заменить raw-JWT в WebSocket query на single-use ticket, обеспечить
полный logout lifecycle (revoke refresh + invalidate ws-tickets + clear
cookie + push-unsubscribe).

**Cookie-based refresh:**

- `rct_refresh` cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth;
  Max-Age=604800`. `AuthCookies` factory в auth-service гарантирует
  идентичные атрибуты для `issue()` и `clear()` (иначе браузер не
  overwrite'ит).
- `POST /auth/refresh` читает cookie, ротирует refresh-token в Redis,
  возвращает новый access + rotated cookie. Body-based fallback
  `/auth/refresh-body` удалён в M13 G4 — cookie-only flow.
- CSRF не нужен: same-origin `ruttrack.site` + `SameSite=Strict`
  закрывают cross-site attacks (DECISIONS 2026-04-20).

**WebSocket ticket handshake:**

```
Client → POST /auth/ws-ticket (Authorization: Bearer <access-JWT>)
       ← {ticket: "<uuid>", expiresAt}
       |
       | auth-service:
       |   SETEX ws_ticket:<uuid> payload TTL=30s
       |   Lua atomic: SADD ws_ticket_user:<uid> <uuid> + EXPIRE 60s
       |
Client → wss://.../ws?ticket=<uuid>
       |
       | notification-web TicketHandshakeInterceptor:
       |   REST POST auth-service:/internal/consume-ws-ticket
       |   (X-Internal-Issuer-Secret, timing-safe)
       |   Lua atomic: GET + DEL + SREM
       |   ← {userId, role, groupId, isHeadman}
       |
       | → STOMP session attrs (used by SubscriptionAuthInterceptor)
```

Payload — pipe-separated `uid|role|grp|hd|exp`. Single-use (DEL через Lua).
TTL 30s. Invalidate-all через `ws_ticket_user:<uid>` set при logout.

**Logout lifecycle:**

```
Client → POST /auth/logout (cookie + optional Bearer)
       |
       | auth-service AuthController:
       |   1) Если Bearer → WsTicketService.invalidateAllFor(userId)
       |      (SMEMBERS + batch DEL + DEL user-set)
       |   2) authService.logout(refreshToken) — Redis DEL refresh:<uid>:<jti>
       |   3) Set-Cookie: rct_refresh=; Max-Age=0; ... (clear)
       |   4) Audit log: auth.logout userId revoked_tickets cookie_logout
       |
       ← 204 No Content
       |
Client → clearAllClientState(accessToken):
         - localStorage.clear, sessionStorage.clear
         - SW runtime caches (headman-api-cache*)
         - Push unsubscribe + DELETE /api/notifications/push/subscribe
           (с Bearer, чтобы Gateway пропустил)
```

Event `user.logged-out` через shared-outbox отложен в M04 (structured log
покрывает audit-trail до появления event-infra).

**Hot-patches из M03a post-mortem:**

- **KI-3** (clock drift): `InternalJwtIssuerClient` проверяет `expiresAt
  < now+5s` → invalidate cache + retry loader.
- **KI-6** (Redis TTL race): `LoginRateLimiter.recordFailure` → atomic
  Lua `INCR + EXPIRE` (на первой попытке).
- **KI-7** (bcrypt DoS): `BcryptConcurrencyGuard` — Semaphore fair N=20
  permits вокруг bcrypt в `login` и `changePassword`. Fail-fast 429.
- **KI-8** (composite rate-limit broken): Gateway `LoginBodyExtractionFilter`
  (GlobalFilter order=-50) читает JSON body POST `/api/auth/login`,
  ставит X-Login header внутренне. Composite `(ip, login)` теперь
  реально работает.

Полный runbook: `docs/auth-flow.md`.

### Observability stack (M04)

**Три signals:** метрики (Prometheus + Micrometer), логи (Loki +
structlog/logback JSON), трейсы (Tempo + OTel SDK). Все связываются
через `trace_id` (UUID v4): Java-сервисы через Micrometer Tracing
bridge, Python-бот через structlog contextvars, события RabbitMQ
несут `trace_id` в unified envelope (`shared-events.DomainEvent`).

**Бизнес-метрики:** `shared-observability/BusinessMetrics` даёт
fluent-helper для counter'ов (`auth.login{role}`, `attendance.checkin{status}`,
`excuse.created{kind}`, `internal_jwt.fallback{from,to}` — KI-2).
Три gauge'а: `attendance.students_in_red_zone` (RedZoneGauge scheduled
5 мин + @SchedulerLock), `notification.active_ws_sessions` (через
Spring `SessionConnected/DisconnectEvent`), `outbox.lag.seconds`
(JpaOutboxStorage/MongoOutboxStorage `oldestPendingAgeSeconds()`).

**Alerting:** Prometheus rules → Alertmanager → webhook
`notification-web:9094/internal/alert` → RabbitMQ `alert.fired` →
notification-bot → Telegram админы. Тихий час 22-08 MSK применяется
только к `warning`; `critical` fire'ит всегда. 8 правил в 4 группах
(service-health, outbox-eventing, infra, business-anomaly) —
полный каталог в `docs/alerts.md`.

**Retention:** Prometheus 14d, Loki 336h (14d, compactor enabled),
Tempo 14d.

**Документация:** `docs/observability.md` (runbook) + `docs/alerts.md`
(каталог).

### Notification History (M10)

**Цель:** перевести `notification-web` из stateless event forwarder в
**stateful history store**, чтобы непрочитанные уведомления и история
сохранялись через logout/login (P2-6/4) и были доступны cross-session
через REST.

**MongoDB schema** (`notification_db.notification_history`):

```
{
  _id:       ObjectId,
  user_id:   Long,            // adressee
  type:      String,          // NotificationType enum (11 values)
  payload:   { ... },         // denormalized snapshot, immutable
  sent_at:   ISODate,         // server clock на момент persist
  read_at:   ISODate | null,  // populated при mark-as-read
  trace_id:  String           // MDC traceId события
}
```

Индексы (создаются `NotificationHistoryMongoConfig.@PostConstruct`):
- `idx_user_sent_desc` — `{user_id:1, sent_at:-1}` для list per user
- `idx_user_read` — `{user_id:1, read_at:1}` для unread badge
- `ttl_sent_at` — `{sent_at:1} expireAfterSeconds` env
  `NOTIFICATION_HISTORY_TTL_DAYS` (default 30, см.
  `data-retention-policy.md`)

Mongo user — отдельный `notification_user` (PoLP, M10 D2): readWrite
+ dbAdmin только на `notification_db`. Существующий `MONGO_USER`
остаётся для `attendance_db`. Init-script —
`infra/mongo/init-mongo.js`.

**Consumer flow** (separate queue от STOMP delivery):

```
producers (academic / schedule / attendance / auth)
    ↓ publish event
fanout exchange `rut-uit.events`
    ├──► queue `notification-web.events`   → STOMP push (live UX)
    └──► queue `notification-web.history`  → NotificationHistoryConsumer
                                              ├─ map event → NotificationType
                                              ├─ skip broadcast (lesson.*)
                                              ├─ persist NotificationHistoryDocument
                                              ├─ invalidateUnreadCount(userId)
                                              └─ try/catch: warn-log на fail,
                                                 НЕ rethrow (DLQ
                                                 `notification-web.history.dlq`
                                                 для manual replay)
```

**Маппер** (M10 D6) persist'ит только 9 user-facing event types c
`payload.user_id` (excuse.*/late_checkin.*/attendance.marked).
Broadcast-events (`lesson.started`/`closed`/`cancelled`) skip'аются —
у них нет per-user adressee, STOMP push достаточно для live UX.
Headman-facing items (excuse.requested на стороне старосты) отложены
в v0.1 (требуется gRPC resolve `headman_id` по `group_id`).

**REST surface** (`NotificationApi` в `notification-api-contract`,
гейтуется через `/api/notifications/**` → notification-web:9094 с
rate-limit 600 rps):

| Метод | Путь | Роль | Описание |
|-------|------|------|----------|
| GET | `/notifications?unreadOnly={bool}&page&size` | STUDENT/TEACHER/ADMIN | HATEOAS `PagedModel<EntityModel<NotificationHistoryDto>>` |
| GET | `/notifications/unread-count` | STUDENT/TEACHER/ADMIN | `{count}` (Caffeine-cached 30s) |
| PATCH | `/notifications/{id}/read` | STUDENT/TEACHER/ADMIN | 204; 403 если чужое (cache evict per userId) |
| POST | `/notifications/mark-all-read` | STUDENT/TEACHER/ADMIN | 204 (cache evict per userId) |

**Caffeine cache + STOMP invalidation** (M10 G4):

- `@Cacheable(cacheNames="unread-count", key="#userId")` на
  `NotificationHistoryService.getUnreadCount` — `maximumSize=10000`,
  `expireAfterWrite=30s`.
- `@CacheEvict` при `markAsRead` / `markAllRead`.
- `invalidateUnreadCount(userId)` вызывается из
  `NotificationHistoryConsumer` сразу после persist: badge моментально
  отражает новое событие, не дожидаясь TTL.
- Single-instance assumption (`CaffeineConfig` javadoc) — при scale-out
  notification-web cache мигрирует на Redis (P2-6/4).

**Frontend integration** (M10 G6/G7, hybrid strategy D7):

- PWA: `useNotificationHistory` (TanStack `useInfiniteQuery`),
  `useUnreadCount` + STOMP frame → `queryClient.invalidateQueries`.
  sessionStorage остаётся authoritative для **broadcast** events
  (lesson.*) и live UX внутри сессии — backend history только для
  cross-session sync.
- web-panel: `NotificationHistoryService` (Signal-based) +
  `notification-history.api.ts` (HttpClient + HATEOAS parser),
  интегрирован в `NotificationCenterService` (`refreshUnreadCount()`
  на STOMP frame, best-effort `markAllRead()`).
- Полный server-side infinite-scroll UI и optimistic mutations
  отложены в v0.1 (`future-ideas.md`).

---

## 8. Сценарии взаимодействия

### Сценарий 1: Студент отмечается на паре

```
Student → [Mini App] → POST /attendance/check-in + JWT + {lat, lng}
  → [API Gateway] → JWT валидация → добавляет X-User-Id, X-User-Role, X-Group-Id
    → [Attendance Service / checkin]
      → gRPC → [Schedule Service] GetActiveLesson(group_id, now)
        ← LessonResponse {lesson_id, schedule_item_id, status: ACTIVE}
      → gRPC → [Academic Service] GetCampusGeofence()
        ← GeofenceResponse {lat, lng, radius_m}
      → Проверка: расстояние от студента до кампуса ≤ radius_m
      → MongoDB upsert: attendance_records {lesson_id, user_id, status: "present"}
      → RabbitMQ publish: "attendance.marked"
        → [Notification Web] → WebSocket push студенту
        → [Notification Bot] → Telegram сообщение (опционально)
    ← HTTP 200 {status: "present", distance_m: 45}
```

### Сценарий 2: Преподаватель смотрит журнал

```
Teacher → [Web Panel] → GET /reports/group/45/subject/67 + JWT
  → [API Gateway] → JWT → X-User-Role: TEACHER, X-User-Id: 89
    → [Attendance Service / report]
      → gRPC → [Academic Service] GetTeacherSubjects(teacher_id: 89)
        ← Проверка: subject 67 в списке → авторизован
      → gRPC → [Academic Service] GetGroupMembers(group_id: 45)
        ← [{user_id, display_name, is_headman}, ...]
      → MongoDB aggregate: attendances where group_id=45, subject_id=67
      → Объединение в памяти: user_id → display_name + статусы
    ← HTTP 200 {students: [{name, lessons: [{date, status}]}]}
```

### Сценарий 3: Пара началась (автоматически)

```
[Schedule Service] @Scheduled cron
  → PostgreSQL: UPDATE lessons SET status='ACTIVE' WHERE scheduled_at ≤ now()
  → RabbitMQ publish: "lesson.started" {lesson_id, group_id, subject_id}
    → [Notification Web]
      → WebSocket push всем подключённым студентам группы
    → [Notification Bot]
      → gRPC → [Academic Service] GetGroupMembers(group_id)
      → Telegram Bot API: массовая рассылка: «Пара началась, отметьтесь»
```

---

## 9. План разработки (фазы)

| Фаза | Содержание | Срок | Результат |
|------|-----------|------|-----------|
| **0** | Proto-контракты, OpenAPI спецификации, JSON Schema событий, docker-compose с инфраструктурой, структура монорепо | 1–2 нед | `docker compose up` поднимает все БД и RabbitMQ |
| **1** | Auth Service + API Gateway | 2–3 нед | Логин, JWT, OTP, Gateway маршрутизирует с валидацией |
| **2** | Academic Service | 2 нед | CRUD структуры вуза, gRPC-сервер, Redis-кэш |
| **3** | Schedule Service + Attendance Service | 3–4 нед | Полный цикл: пара → отметка → отчёт |
| **5** | Web Push Backend | 1 нед | VAPID, подписки, Web Push delivery adapter |
| **6** | Notification Service (оба контейнера) | 1–2 нед | Push через WebSocket, Web Push и Telegram |
| **7** | PWA Mobile Client «RutTrack» | 2–3 нед | PWA, Service Worker, offline, Web Push подписка |
| **8** | Фронтенды (Mini App, Web Panel, Landing) | 4–6 нед | Mini App, Web Panel, Landing |
| **9** | CI/CD, мониторинг, документация | 1–2 нед | GitHub Actions, health checks, Swagger |

---

## 10. Ключевые решения и обоснования

| Решение | Обоснование |
|---------|------------|
| MongoDB для посещаемости | Polyglot persistence для портфолио + гибкая схема excuse без миграций + денормализация для быстрых запросов без JOIN |
| Report внутри Attendance | Нет своей БД, только читает данные Attendance. Изоляция через порт-интерфейс + ArchUnit. При необходимости выносится в отдельный сервис за один рефакторинг |
| 2 контейнера Notification | Разные рантаймы (JVM vs Python), независимый деплой, общий fanout exchange RabbitMQ |
| gRPC между сервисами | Типобезопасные контракты, кодогенерация, обнаружение несовместимости на этапе компиляции |
| Angular для веб-панели | Enterprise-фреймворк для сложных форм и таблиц, встроенный DI, реактивные формы |
| React для Mini App | Telegram SDK оптимизирован для React, лёгкий bundle через Vite |
| React для PWA | Общий стек с Mini App, переиспользование API-клиента и компонентов |
| PWA отдельно от Web Panel | Мобильный UX для студентов/старост/преподавателей vs enterprise-админка — разные задачи и целевые устройства |
| Web Push как третий канал | Дублирует Telegram push для PWA-пользователей, работает когда приложение закрыто |
| `is_headman` флаг вместо роли | Староста — это студент с расширенными правами, не отдельная роль. Упрощает авторизацию |
| Long ID вместо UUID | PostgreSQL BIGSERIAL быстрее UUID в индексах, компактнее в MongoDB |
| Phosphor Icons везде | Единая иконочная система для всех фронтендов, 6 весов для разных контекстов |

---

## 11. JPA convention: FK как Long, без entity relations (NEW-143)

**Decision (M05 Группа 2, 2026-04-20):** все JPA entity в v0.0.0 используют
FK как `Long`, без `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`.
Инвариант защищён ArchUnit rule `RepositoryNPlusOneGuardTest` в
schedule-service и academic-service.

### Почему

1. **Прозрачный SQL.** Один JPQL / native query = один SELECT. Нет
   «невидимых» дополнительных запросов при обращении к `getX()` на
   lazy-поле. Всё что выполняется в БД — видно в коде.
2. **Нет lazy surprises.** `LazyInitializationException` после закрытия
   Hibernate-сессии — частая ловушка при serialization entity для REST
   response'ов. Конвенция устраняет проблему by design.
3. **Пересечение gRPC-границ.** Связь `Lesson.schedule_item_id` ↔
   `ScheduleItem` живёт в **одной** БД, но `Attendance.user_id` ↔
   `User` — в **разных** сервисах (attendance ↔ academic). FK как
   Long одинаково работает в обоих случаях. `@ManyToOne` с cross-service
   FK невозможен.
4. **Нет N+1 рисков.** Entity без relations не может вызвать N+1 при
   list-запросе. Это архитектурный инвариант, защищённый ArchUnit.

### Образец паттерна «collect itemIds → findByIdIn»

`LessonService.massCancelLessons` ([ссылка на
строки 137-142](../services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/LessonService.java)):

```java
List<ScheduleItem> items = scheduleItemRepository
        .findByGroupId(groupId);
List<Long> itemIds = items.stream().map(ScheduleItem::getId).toList();
List<Lesson> lessons = lessonRepository
        .findByScheduleItemIdInAndDateBetweenAndStatusIn(itemIds, from, to, statuses);
```

Два SELECT'а для всей операции (вместо N+1). Такой же подход в
`ScheduleGrpcServiceImpl.getLessonsByGroup`.

### Что делать, когда relation всё-таки нужна

Если в будущем миграция требует relation (например, для
cross-aggregate lock или JSONB-embedded subentity), снять инвариант
осознанно:

1. **Удалить** rule `entitiesMustNotUseJpaRelations` из
   `RepositoryNPlusOneGuardTest`.
2. **Оставить** rule `repositoriesReturningCollectionsMustGuardNPlusOne`
   — он автоматически активируется на репозиторий-методы новой entity.
3. **Защитить** hot repo-методы через:
   - `@EntityGraph(attributePaths = {...})` — Hibernate делает JOIN
     FETCH для указанных relations в одном SELECT;
   - interface projection (см.
     [LessonDetailsProjection](../services/schedule-service/schedule-app/src/main/java/ru/rutcampustrack/schedule/lesson/projection/LessonDetailsProjection.java)
     как reference) — возвращаем subset полей без lazy-триггера;
   - `Pageable` parameter — ограничиваем размер выборки (N+1 всё
     равно возможен, но в ограниченной области);
   - JPQL с `JOIN FETCH` или native с explicit `JOIN`.

### Что НЕ даёт конвенция

- **Не** предотвращает медленные запросы сами по себе (для этого —
  composite indexes, см. [performance-indexes.md](performance-indexes.md)).
- **Не** заменяет нормальный API design (DTO, HATEOAS-assembler'ы
  остаются).
- **Не** запрещает Spring Data projections — они безопасны и поощряются
  для payload-optimization.

---

## 11.1. Performance & Ops runbooks (M05)

Оперативные runbook'и, порождённые M05 «Performance»:

- [`performance-indexes.md`](performance-indexes.md) — composite indexes
  PG/Mongo, EXPLAIN before/after, процесс добавления новых.
- [`caching-strategy.md`](caching-strategy.md) — 7 Redis namespaces, TTL
  matrix, invalidation triggers, migration plan.
- [`connection-pool-tuning.md`](connection-pool-tuning.md) — HikariCP
  sizing формула, триггеры пересмотра, alert
  `HikariPoolExhaustion`.
- [`data-retention-policy.md`](data-retention-policy.md) — таблица 12
  видов данных (push-subs 90д, refresh-tokens 7д, OTP 5м, ...), mechanism,
  триггеры.
- [`api-error-conventions.md`](api-error-conventions.md) — RFC 7807 error
  schema, pseudo-atomic vs partial-success для batch-endpoint'ов.
- [`future-ideas.md`](future-ideas.md) — отложенные варианты (Mongo
  `$group` aggregation в ReportService, cache hit/miss metrics через
  `@Aspect`).

---

## 12. Дизайн-решения

Подробные дизайн-решения (иконки, анимации, PWA, брендинг) вынесены в отдельный файл: **`docs/design-decisions.md`**.
