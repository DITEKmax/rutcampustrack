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

**Роль:** выдача и управление токенами. Единственный сервис, знающий секреты подписи JWT.

**REST API (через Gateway):**

| Метод | Путь | Роль | Описание |
|-------|------|------|----------|
| POST | `/auth/login` | Public | Логин по email/password → JWT |
| POST | `/auth/otp/request` | Public | Запрос OTP через Telegram |
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

**Не общается** с другими сервисами — источник доверия, не потребитель.

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

**Стек:** Java Spring Boot + Spring WebSocket + webpush-java  
**Порт:** 9094

**Роль:** push-уведомления через три канала доставки:

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
- Отправка OTP-кодов для авторизации
- Push-уведомления студентам о начале пар
- Команды бота: `/start`, `/login`, `/status`

**Подписан на RabbitMQ-события:** те же, что и Notification Web

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

## 11. Дизайн-решения

Подробные дизайн-решения (иконки, анимации, PWA, брендинг) вынесены в отдельный файл: **`docs/design-decisions.md`**.
