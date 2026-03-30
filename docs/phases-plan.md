# RutCampusTrack — План фаз разработки

## Обзор фаз

| Фаза | Содержание | Статус |
|------|-----------|--------|
| 0 | Каркас, контракты, инфраструктура | ✅ ЗАВЕРШЕНА |
| 1 | Auth Service + API Gateway | ✅ ЗАВЕРШЕНА |
| 2 | Academic Service | ⏳ СЛЕДУЮЩАЯ |
| 3 | Schedule Service + Attendance Service | ⬜ |
| 4 | Notification Service (Web + Bot) | ⬜ |
| 5 | Фронтенды (Mini App, Web Panel, Landing) | ⬜ |
| 6 | CI/CD, мониторинг, документация | ⬜ |

---

## Фаза 0: Каркас, контракты, инфраструктура ✅

### Что сделано

- Monorepo на Gradle Kotlin DSL (Java 21, Spring Boot 3.4)
- Структура: каждый сервис = `*-api-contract` (чистый java-library) + `*-app` (Spring Boot)
- Docker Compose: PostgreSQL×2 (academic_db, schedule_db), MongoDB (attendance_db), Redis, RabbitMQ
- Proto-контракты: `academic.proto` (7 RPC), `schedule.proto` (3 RPC)
- Event schemas: 7 JSON Schema файлов для RabbitMQ событий
- Java Enum-ы во всех контрактных модулях
- LowercaseEnumConverter + EnumConverters с autoApply
- Flyway baseline миграции (academic_db: 12 таблиц, schedule_db: 2 таблицы)
- application.yml для всех сервисов
- ErrorResponse (RFC 7807) + ResourceNotFoundException
- Application-классы заглушки для всех Spring Boot сервисов
- .gitignore, README.md, CLAUDE.md

### Файлы

- `docker-compose.yml`
- `proto/academic.proto`, `proto/schedule.proto`
- `event-schemas/*.json` (7 файлов)
- `services/*/build.gradle.kts`
- `services/*/src/main/resources/application.yml`
- `services/*/src/main/resources/db/migration/V1__baseline.sql`
- Enum-ы в `*-api-contract/src/.../enums/`
- `docs/phase-0-report.md`

---

## Фаза 1: Auth Service + API Gateway ✅

**Завершена:** 2026-03-30 | **Отчёт:** `docs/phase-1-report.md`

### Цель

Работающая авторизация: пользователь может залогиниться, получить JWT, обновить токен, сбросить пароль через OTP. Gateway валидирует JWT и маршрутизирует запросы.

### Auth Service (порт 9090)

#### Что реализовать

1. **JWT генерация и валидация**
   - Генерация пары RSA ключей (private/public) при первом старте, сохранение в файл
   - Access Token (JWT, 15 мин): claims = `sub` (user_id), `role`, `group_id`, `is_headman`
   - Refresh Token (JWT, 7 дней): хранится в Redis как `refresh:{user_id}:{jti}`
   - Публичный ключ доступен через endpoint `GET /auth/public-key` (Gateway забирает при старте)

2. **REST API endpoints (контракт в auth-service, т.к. у Auth нет отдельного api-contract)**
   - `POST /auth/login` — вход по login + password → JWT пара
   - `POST /auth/refresh` — обновление Access Token по Refresh Token
   - `POST /auth/logout` — инвалидация Refresh Token (удаление из Redis)
   - `GET /auth/public-key` — публичный ключ для Gateway
   - `POST /auth/otp/request` — запрос OTP кода (принимает telegram_id, генерирует 6-значный код, сохраняет в Redis с TTL 120 сек)
   - `POST /auth/otp/verify` — проверка OTP кода → JWT пара
   - `POST /auth/change-password` — смена пароля (currentPassword + newPassword), требует JWT

3. **Redis-ключи**
   ```
   otp:{telegram_id}           → "481927"    TTL: 120 сек
   otp_attempts:{telegram_id}  → "2"         TTL: 300 сек (макс 3)
   otp_sent:{telegram_id}      → "true"      TTL: 60 сек
   refresh:{user_id}:{jti}     → "valid"     TTL: 7 дней
   jwt:public_key              → "<PEM>"     TTL: 3600 сек
   ```

4. **Безопасность**
   - BCrypt для хэширования паролей
   - Rate limiting на OTP: макс 3 попытки за 5 минут, повторная отправка не чаще 60 сек
   - Spring Security конфигурация: `/auth/login`, `/auth/otp/**`, `/auth/public-key` — public; остальное — authenticated

5. **Подключение к Academic DB** (read-only для проверки пользователя)
   - Auth Service должен читать таблицу `users` из `academic_db` для проверки логина/пароля
   - Вариант: Auth Service подключается к той же PostgreSQL `academic_db` read-only
   - НЕ создаёт свою БД — использует `academic_db` для чтения `users`

#### Пакетная структура

```
services/auth-service/src/main/java/ru/rutcampustrack/auth/
├── AuthApplication.java
├── config/
│   └── SecurityConfig.java              ← Spring Security
├── controller/
│   └── AuthController.java              ← REST endpoints
├── service/
│   ├── JwtService.java                  ← генерация/валидация JWT
│   ├── OtpService.java                  ← OTP логика + Redis
│   └── AuthService.java                 ← логин, регистрация, смена пароля
├── dto/
│   ├── LoginRequest.java                ← record
│   ├── TokenResponse.java               ← record (accessToken, refreshToken, expiresIn)
│   ├── RefreshRequest.java              ← record
│   ├── OtpRequest.java                  ← record (telegramId)
│   ├── OtpVerifyRequest.java            ← record (telegramId, code)
│   └── ChangePasswordRequest.java       ← record (currentPassword, newPassword)
└── exception/
    ├── InvalidCredentialsException.java
    ├── OtpExpiredException.java
    └── OtpRateLimitException.java
```

### API Gateway (порт 8080)

#### Что реализовать

1. **JWT фильтр**
   - При старте загружает публичный ключ из Auth Service (`GET http://auth-service:9090/auth/public-key`)
   - Кэширует ключ (обновляет каждый час)
   - На каждый запрос (кроме public routes): проверяет `Authorization: Bearer <token>`, валидирует подпись, проверяет `exp`
   - Извлекает claims и добавляет заголовки: `X-User-Id`, `X-User-Role`, `X-Group-Id`, `X-Is-Headman`
   - Если токен невалиден → 401 Unauthorized

2. **Маршрутизация** (уже в application.yml, но нужно проверить)
   - `/api/auth/**` → auth-service:9090
   - `/api/academic/**` → academic-service:9091
   - `/api/schedule/**` → schedule-service:9092
   - `/api/attendance/**`, `/api/reports/**` → attendance-service:9093
   - `/api/ws/**` → notification-web:9094

3. **Public routes** (без JWT проверки)
   - `/api/auth/login`
   - `/api/auth/otp/**`
   - `/api/auth/public-key`
   - `/api/auth/refresh`

4. **Rate limiting**
   - На `/api/attendance/check-in` — защита от флуда геолокацией

#### Пакетная структура

```
services/api-gateway/src/main/java/ru/rutcampustrack/gateway/
├── GatewayApplication.java
├── config/
│   └── RouteConfig.java                 ← маршруты (если не хватит YAML)
└── filter/
    └── JwtAuthenticationFilter.java     ← GatewayFilter: валидация JWT, инжекция заголовков
```

### Тестирование фазы 1

- `POST /api/auth/login` с тестовым пользователем → получить JWT
- Запрос к любому защищённому endpoint с JWT → 200
- Запрос без JWT → 401
- Запрос с expired JWT → 401
- `POST /api/auth/refresh` с Refresh Token → новый Access Token
- OTP flow: request → verify → JWT

### Зависимости от других фаз

- Auth Service читает `users` из `academic_db` → таблица уже создана Flyway в Фазе 0
- Нужен тестовый пользователь в `users` для проверки логина → Flyway V2 migration или seed data

---

## Фаза 2: Academic Service

### Цель

Полноценный CRUD структуры вуза: пользователи, группы, семестры, предметы, привязки преподавателей, помощники старосты, домашние задания, пороги красной зоны. gRPC-сервер для внутренних вызовов. Redis-кэширование.

### Что реализовать

1. **REST API через контракт** (`academic-api-contract`)
   - Интерфейсы: `UserApi`, `GroupApi`, `SemesterApi`, `SubjectApi`, `AssignmentApi`, `HomeworkApi`, `ThresholdApi`
   - DTO: Request (record) + Response (класс с HATEOAS) для каждой сущности
   - Assembler для каждой Response
   - Пагинация через `PagedResponse<T>` (свой record, не Spring Data Page)

2. **Endpoints по ролям**

   ADMIN:
   - CRUD users (создание с автогенерацией логина/пароля)
   - CRUD groups
   - CRUD semesters (с подтверждением удаления кодовой фразой)
   - Назначение/снятие старосты
   - Перевод студента между группами
   - Dashboard: общая статистика

   HEADMAN:
   - CRUD subjects (с типом: lecture/practice/lab)
   - Назначение преподавателей на предметы (поиск по табельному номеру)
   - Управление помощниками (назначение, отзыв, выбор прав)
   - CRUD homeworks (привязка к lesson_id, ссылка опциональна)
   - Настройка порога красной зоны (группа / предмет)

   STUDENT:
   - GET свой профиль
   - GET состав своей группы
   - GET домашние задания группы
   - POST/DELETE homework_completions (личный трекер)

   TEACHER:
   - GET свои предметы и группы

3. **gRPC-сервер** (реализация proto/academic.proto)
   - `GetGroup`, `GetGroupMembers`, `GetTeacherSubjects`, `IsHeadman`, `GetActiveSemester`, `GetCampusGeofence`, `GetUserById`
   - Подключить `grpc-spring-boot-starter`

4. **Redis-кэширование**
   - `@Cacheable` на read-heavy методах
   - Инвалидация при изменении данных
   - Кэш-ключи: `group:{id}:info`, `group:{id}:members`, `teacher:{id}:subjects`, `semester:active`, `campus:geofence`

5. **RabbitMQ events**
   - Publish `group.updated` при изменении состава группы
   - Publish `semester.archived` при деактивации семестра
   - Publish `homework.published` / `homework.updated` при ДЗ

6. **Автогенерация логинов**
   - При создании пользователя: `student00001`, `student00002`, ... (BIGSERIAL sequence)
   - `teacher00001`, ...
   - `student`, `teacher`, `admin` — зарезервированные тестовые аккаунты
   - Пароль: случайная строка 8–12 символов, сохраняется в `initial_password` до первой смены

### Зависимости

- Auth Service (Фаза 1) должен уметь читать `users` из `academic_db`
- gRPC будет вызываться из Schedule, Attendance, Notification Bot (Фазы 3–4)

---

## Фаза 3: Schedule Service + Attendance Service

### Цель

Полный цикл: пара создаётся → автогенерируется на семестр → начинается → студент отмечается → пара закрывается → absent автоматически проставляется → отчёт формируется.

### Schedule Service (порт 9092)

1. **REST API**
   - HEADMAN: CRUD schedule_items (шаблон расписания), отмена/восстановление пар, массовая отмена
   - ALL: GET расписание группы на период
   - Блокировка геоотметки на конкретной паре

2. **Автогенерация lessons**
   - При создании schedule_item → генерация всех `lessons` на даты семестра с учётом чётности недель
   - Уникальный индекс (schedule_item_id, date) защищает от дублей

3. **Смена статусов (@Scheduled)**
   - Cron: каждую минуту проверять `lessons` с `status='planned'` и `start_time ≤ now()` → ACTIVE
   - Cron: `status='active'` и `end_time + 5 мин ≤ now()` → CLOSED
   - При ACTIVE → RabbitMQ `lesson.started`
   - При CLOSED → RabbitMQ `lesson.closed`
   - При отмене → RabbitMQ `lesson.cancelled`

4. **gRPC-сервер** (реализация proto/schedule.proto)
   - `GetActiveLesson`, `GetLessonById`, `GetLessonsByGroup`

5. **gRPC-клиент**
   - Вызывает Academic Service: `GetGroup`, `GetTeacherSubjects` — для валидации при создании расписания

### Attendance Service (порт 9093)

1. **Модуль checkin/ — REST API**
   - `POST /attendance/check-in` — студент отправляет {lat, lng}
     - Проверка: gRPC → Schedule.GetActiveLesson (есть ли активная пара)
     - Проверка: gRPC → Academic.GetCampusGeofence (координаты в радиусе)
     - Проверка: окно ±5 минут от начала/конца пары
     - Проверка: пара не заблокирована для геоотметки
     - MongoDB upsert: {lesson_id, user_id} → status=present
     - RabbitMQ publish: `attendance.marked`
   - `POST /attendance/manual` — староста вручную ставит статус (автосохранение при каждом нажатии)
   - `PUT /attendance/{id}/excuse` — студент ставит уважительную причину
   - `PUT /attendance/{id}/excuse/confirm` — староста подтверждает/отклоняет

2. **Excuse-тикеты**
   - `POST /attendance/excuse-tickets` — студент создаёт тикет (draft)
   - `PUT /attendance/excuse-tickets/{id}/submit` — отправить старосте (draft → submitted)
   - `PUT /attendance/excuse-tickets/{id}/review` — староста одобряет/отклоняет
   - При approve: все lesson_ids в тикете получают status=excused в attendances
   - Файлы: принимаются через multipart, пересылаются старосте через Telegram (RabbitMQ event), НЕ хранятся

3. **Late checkin**
   - `POST /attendance/late-checkin` — студент запрашивает «был, но забыл»
   - `PUT /attendance/late-checkin/{id}/confirm` — староста подтверждает
   - RabbitMQ: `late_checkin.requested`

4. **Автоматический absent**
   - Слушает RabbitMQ `lesson.closed`
   - Для каждого студента группы: если нет записи в attendances → insert status=absent, marked_by=auto_scheduler

5. **Модуль report/ — REST API**
   - `GET /reports/group/{groupId}/subject/{subjectId}` — журнал (TEACHER, HEADMAN)
   - `GET /reports/student/{studentId}` — посещаемость студента (STUDENT свой, HEADMAN)
   - `GET /reports/lesson/{lessonId}` — все отметки пары
   - `GET /reports/semester/{semesterId}/summary` — сводка (ADMIN)
   - `GET /reports/student/{studentId}/stats` — статистика: проценты, тренд по неделям, рейтинг предметов
   - `GET /reports/group/{groupId}/top-skippers` — топ прогульщиков
   - Экспорт: `GET /reports/export?format=pdf|excel&groupId=...&subjectId=...&dateFrom=...&dateTo=...`
   - Правило: cancelled и planned НЕ учитываются в статистике

6. **gRPC-клиент**
   - Academic Service: GetGroupMembers, GetCampusGeofence, GetTeacherSubjects
   - Schedule Service: GetActiveLesson, GetLessonById

---

## Фаза 4: Notification Service (Web + Bot)

### Цель

Push-уведомления в реальном времени: в Telegram через бота и в веб-панель через WebSocket. Оба контейнера подписаны на RabbitMQ.

### Notification Web (Java, порт 9094)

1. **WebSocket endpoint** `/ws`
   - При подключении: клиент отправляет JWT → сервер извлекает user_id, group_id
   - Подписка на группу: уведомления доставляются всем подключённым пользователям группы
   - Real-time обновление блока уведомлений в веб-панели без перезагрузки

2. **RabbitMQ consumer**
   - Очередь `notification-web.events` ← fanout exchange `rut-uit.events`
   - Слушает все события: lesson.started/closed/cancelled, attendance.marked, excuse.requested, late_checkin.requested, homework.published/updated

3. **Маппинг событий → WebSocket сообщения**
   - `lesson.started` → push студентам группы: «Пара началась»
   - `lesson.cancelled` → push студентам группы: «Пара отменена»
   - `excuse.requested` → push старосте: «Студент X запросил у.п.»
   - `late_checkin.requested` → push старосте: «Студент X просит подтвердить присутствие»
   - `homework.published` → push студентам группы: «Новое ДЗ по предмету Y»

### Notification Bot (Python/Aiogram)

1. **Telegram Bot**
   - `/start` — привязка telegram_id к учётной записи. Если `initial_password` не null — отправить логин и пароль
   - `/login` — OTP авторизация (вызов Auth Service API)
   - `/status` — текущий статус посещаемости студента

2. **RabbitMQ consumer (aio-pika)**
   - Очередь `notification-bot.events` ← fanout exchange `rut-uit.events`
   - Те же события что и Notification Web

3. **Уведомления через Telegram**
   - `lesson.started` → сообщение студентам группы с inline-кнопкой «Отметиться» (открывает Mini App)
   - Напоминания: начало, середина, конец пары (для неотметившихся). Хранить message_id в Redis для последующего удаления
   - `lesson.closed` → удалить все напоминания через `deleteMessage`
   - `lesson.cancelled` → сообщение студентам
   - `excuse_ticket.submitted` → переслать файлы старосте + кнопки «Одобрить» / «Отклонить»
   - `homework.published` → сообщение группе

4. **gRPC-клиент (grpcio)**
   - Academic Service: GetGroupMembers — для рассылки по группе

### Инфраструктура

- RabbitMQ exchange: `rut-uit.events` (type: fanout)
- Queues: `notification-web.events`, `notification-bot.events` (каждый получает копию)
- Redis: `reminder:msgs:{lesson_id}:{user_id}` — хранение message_id напоминаний

---

## Фаза 5: Фронтенды

### Цель

Три фронтенда, каждый с обоснованием технологии.

### Telegram Mini App (React + Vite + TypeScript)

Аудитория: студенты.

Экраны:
- Геоотметка (запрос координат, отправка check-in)
- Моя посещаемость (по предметам, проценты)
- Расписание на сегодня/неделю
- Уважительные причины (выбор, отправка)
- «Забыл отметиться» (список пар с absent)
- ДЗ (список, личный трекер выполнения)

Технологии: React, Vite, TypeScript, Telegram Mini App SDK, TanStack Query, Tailwind CSS.

### Веб-панель (Angular + TypeScript)

Аудитория: администраторы, преподаватели, старосты.

Модули:
- Dashboard (ADMIN: общая статистика, красная зона)
- Пользователи (ADMIN: CRUD, назначение старост)
- Группы (ADMIN: CRUD)
- Семестры (ADMIN: CRUD с подтверждением удаления)
- Предметы (HEADMAN: CRUD с типом)
- Расписание (HEADMAN: создание, отмена пар)
- Журнал посещаемости (HEADMAN: редактируемая сетка, TEACHER: read-only)
- Отчёты (ALL: экспорт PDF/Excel)
- ДЗ (HEADMAN: публикация)
- Помощники (HEADMAN: назначение, права)
- Уведомления (ALL: WebSocket real-time)

Технологии: Angular 18, TypeScript, Angular Material (MD3), RxJS, WebSocket.

### Лендинг (HTML + CSS)

Аудитория: внешние посетители.

Страницы:
- Главная (описание системы)
- Swagger UI (embed)
- Контакты

Технологии: чистый HTML5, CSS3, минимум JS.

---

## Фаза 6: CI/CD, мониторинг, документация

### Цель

Автоматический деплой на VPS при пуше в main, мониторинг здоровья сервисов, финальная документация.

### CI/CD (GitHub Actions)

- `ci-java.yml`: build + test всех Java-сервисов при PR
- `ci-python.yml`: lint + test notification-bot
- `ci-frontend.yml`: build + lint фронтендов
- `deploy.yml`: при push в main → build Docker images → push to registry → SSH deploy to VPS

### Docker production

- Dockerfile для каждого Java-сервиса (multi-stage: gradle build → JRE runtime)
- Dockerfile для notification-bot (Python slim)
- Dockerfile для фронтендов (nginx)
- `docker-compose.prod.yml` с production-паролями, без management UI, с restart: always

### Мониторинг

- Spring Boot Actuator: `/actuator/health`, `/actuator/metrics`
- Централизованные логи: correlation ID на каждый запрос через Gateway

### VPS

- Новый сервер, новый домен
- Nginx reverse proxy → Gateway :8080
- SSL через Let's Encrypt
- Firewall: только 80, 443, 22

### Документация

- Swagger UI через Gateway: `https://domain.com/swagger-ui.html`
- README.md обновлённый
- Все docs/ актуализированы
