# Data Retention Policy (NEW-148)

**Статус:** v0.0.0 draft, M05 Группа 7.
**Owner:** Platform / Security.

Документ описывает как долго система хранит данные разной природы, чем
обеспечивается автоматическое удаление и когда нужно пересматривать
retention. Покрывает все «долгоживущие» хранилища (PostgreSQL, MongoDB,
Redis) — эфемерные in-memory кэши (Caffeine / Spring `@Cacheable`) не
включены, их TTL описан в `docs/caching-strategy.md`.

## Retention matrix

| # | Данные | Хранилище | Retention | Механизм | Обоснование |
|---|--------|-----------|-----------|----------|-------------|
| 1 | **Refresh tokens** (`refresh:<uid>:<jti>`) | Redis (auth-service) | 7 дней | Redis TTL (`Duration.ofSeconds(jwtProperties.refreshTokenExpiration())`) в `AuthService.login:88`, `AuthService.refresh:125`, `TmaService:78`, `OtpService:192`. `refresh-token-expiration=604800` в `application.yml:57`. | GDPR + минимизация — сессии живут ≤7d. Access-токен 15м. |
| 2 | **OTP-коды** (`otp:<telegramId>`) | Redis (auth-service) | 120 секунд | Redis TTL через `OtpService:97` (`redisTemplate.opsForValue().set("otp:" + telegramId, code, ttl)`). Настройка `otp.ttl-seconds=120` в `application.yml:70`. | Минимальное окно для ввода; после — новый запрос OTP. |
| 3 | **OTP попытки** (`otp_attempts:<tid>`) | Redis (auth-service) | 300 секунд | Redis TTL | Anti-bruteforce окно, синхронизировано с `otp.attempts-window-seconds`. |
| 4 | **Login rate-limit** (`login_failures:<ip>:<login>`) | Redis (auth-service) | 900 секунд | Redis TTL (composite key, M03a Группа 11) | Block окно 15 мин для (IP, login) комбинации. |
| 5 | **Web Push subscriptions** | MongoDB `push_subscriptions` (notification-web) | 90 дней без активности | `PushSubscriptionCleanupJob` — `@Scheduled(cron="0 0 3 * * SUN") @SchedulerLock("cleanupStalePushSubs")`. Удаляет подписки с `last_seen < now - 90d`. Поле `last_seen` обновляется bulk `$set` в `WebPushDeliveryService.touchLastSeen` на каждый successful send. На HTTP 410 Gone — мгновенный delete (PUSH-07, D-10). | Устройства могут быть оффлайн неделями; после 90d — вероятно deinstall. Индекс `idx_last_seen` (PushMongoConfig) обеспечивает IXSCAN на cleanup. |
| 6 | **Outbox events** | PostgreSQL `academic_outbox`, `schedule_outbox`; MongoDB `attendance_outbox` | 7 дней после публикации | `OutboxCleanupJob` (shared-outbox M02) — `@Scheduled` + `@SchedulerLock`. Параметр `rutcampustrack.outbox.retention-days=7`. | Достаточно для replay при broker downtime ≤ 48ч + safety-margin. |
| 7 | **ShedLock locks** | MongoDB `shedLock`, PostgreSQL `shedlock` | Авто (ShedLock) | Библиотека сама управляет lifecycle. | — |
| 8 | **Attendance history** (`attendances`, `late_checkin_requests`) | MongoDB (attendance) | Бессрочно (accept) | Нет автоматического удаления. Данные академические — нужны для аналитики по всему периоду обучения. | FZ-152 persona данных: минимум имя/фамилия/студ-ID; чувствительных полей нет. |
| 9 | **Users / Groups / Subjects / Semesters** | PostgreSQL (academic) | Soft-delete (status=`archived`) | `UserService.archiveUser`, `GroupService.deleteGroup` | Академические записи не удаляются (соблюдение истории), только архивируются. |
| 10 | **JWT public key cache** | in-memory (gateway, сервисы) | 60 минут | `rutcampustrack.security.internal-jwt.public-key-refresh-minutes=60` | Rotate window; меньше — лишний HTTP trip. |
| 11 | **Alertmanager webhook payloads** | — | Не хранятся | Обработаны синхронно, forwarded в telegram/bot | — |
| 12 | **WebSocket session state** | in-memory (notification-web) | До disconnect | — | Stateless после M04. |

## Триггеры пересмотра retention

- **Push-subs 90d:** если Prometheus `mongo_db_collection_count{collection="push_subscriptions"}`
  растёт быстрее чем количество активных пользователей × 2 — значит
  cleanup не догоняет. Проверить: (a) работает ли `@Scheduled`, (b)
  выполняется ли ShedLock на правильной instance, (c) не заблокирован
  ли job exception.
- **Refresh-tokens 7d:** если пользователи жалуются на «входите заново
  каждую неделю» — обсудить «sliding» sessions с security.
- **Outbox 7d:** если growing-pattern на `outbox_pending_older_than_1h{gauge}`
  (M02 observability) — увеличить retention **не поможет**, проблема
  в consumer'е. Retention трогать только если нужен longer replay.
- **OTP 120s:** пользователи не успевают ввести код на slow-ввод →
  +30-60s; сейчас достаточно.

## Миграция / backfill

- **M05 G7 bootstrap:** `PushCleanupConfig.backfillOnStart` проставляет
  `last_seen = now` для всех pre-M05 подписок (поле отсутствует). Это
  безопасно — подписки живые на момент деплоя, через 90d их natural-
  cleanup удалит, если не будет push-активности.
- **Retention параметры** — через env-vars
  (`RUTCAMPUSTRACK_PUSH_CLEANUP_RETENTION_DAYS`,
  `RUTCAMPUSTRACK_OUTBOX_RETENTION_DAYS`). Менять без redeploy не
  требуется; настройки стабильные.

## Что сделано в M05 Группе 7

- ✅ Поле `last_seen: Instant` в `PushSubscriptionDocument`.
- ✅ Индекс `idx_last_seen` в `PushMongoConfig.initIndexes()` (programmatic,
  не Flyway — коллекция MongoDB, см. D10).
- ✅ Bulk `$set` обновление `last_seen` в `WebPushDeliveryService.touchLastSeen`
  (один Mongo call на fanout, не N save'ов).
- ✅ `PushSubscriptionCleanupJob` — weekly cron + ShedLock-Mongo.
- ✅ Bootstrap backfill `last_seen = now` для pre-M05 подписок.
- ✅ Integration-тест `PushSubscriptionCleanupJobIT` — 3 сценария:
  dead/fresh/boundary + backfill.
- ✅ Refresh-token TTL audit — подтверждено `EX=604800` (7d) во всех
  4 call-site'ах (AuthService login/refresh, TmaService, OtpService).

## Что отложено (M06+)

- **Per-user consent retention** (GDPR right-to-forget) — требует UI
  «удалить мой аккаунт» + cascade delete на attendance history. M06
  compliance scope.
- **Push history audit log** — кто/когда отправил что. Сейчас только
  `log.debug` строки. Если появится аудит-требование — добавим
  отдельную коллекцию.
- **Retention policy enforcement test** — CI-проверка что в новых
  Scheduled job'ах присутствует `@SchedulerLock` (reference: attendance
  `ScheduledMustHaveSchedulerLockTest`). В notification-web ещё нет
  такого ArchUnit-правила. Кандидат на M07/M08.
