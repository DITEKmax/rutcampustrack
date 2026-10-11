# Обзор баз данных проекта

В проекте не одна общая БД, а несколько хранилищ под разные зоны ответственности.

Главная идея:

- `academic_db` хранит людей и учебную структуру.
- `schedule_db` хранит расписание.
- `attendance_db` хранит отметки и заявки.
- `notification_db` хранит уведомления.
- Redis хранит временное и быстрое состояние.
- RabbitMQ передает события, но не является источником истины.

## 1. `academic_db`

PostgreSQL. Владелец: `academic-service`. Эту же БД читает `auth-service` для логина.

Это главная справочная БД системы: пользователи, группы, семестры, предметы, домашние задания, пороги посещаемости и аудит.

### `users`

Пользователи всех ролей. Староста не отдельная роль, а `student` с `is_headman=true`.

Поля:

- `id`
- `login`
- `password_hash`
- `last_name`
- `first_name`
- `middle_name`
- `email`
- `phone`
- `telegram_id`
- `telegram_username`
- `employee_number`
- `role`
- `status`
- `is_headman`
- `group_id`
- `initial_password`
- `password_changed`
- `avatar_id`
- `created_at`
- `updated_at`

Роли:

- `admin`
- `teacher`
- `student`

Статусы:

- `active`
- `expelled`
- `suspended`
- `archived`

Комментарий: это ключевая таблица для нового UX. Отсюда строятся профили, роли, доступы, список студентов, преподавателей и старост.

### `groups`

Учебные группы.

Поля:

- `id`
- `name`
- `is_active`
- `created_at`
- `archived_at`

Раньше был `code`, но он удален. Теперь имя группы единое и уникальное. Например: `УИТ-411`.

### `student_group_history`

История движения студента по группам.

Поля:

- `id`
- `user_id`
- `group_id`
- `joined_at`
- `left_at`
- `reason`
- `created_at`

Нужно для переводов, отчислений, академического отпуска и истории принадлежности к группе.

### `semesters`

Семестры.

Поля:

- `id`
- `name`
- `date_from`
- `date_to`
- `is_active`
- `created_at`
- `first_week_type`

`first_week_type`: `odd` или `even`.

Есть защита от пересечения дат семестров и ограничение на активный семестр.

### `subjects`

Предметы.

Поля:

- `id`
- `name`
- `type`
- `group_id`
- `created_at`

Типы:

- `lecture`
- `practice`
- `lab`

Комментарий: предмет сейчас принадлежит группе. Это важно для UX, потому что "Математика" у разных групп может быть разной записью.

### `teacher_subject_groups`

Связка преподаватель-предмет-группа-семестр.

Поля:

- `id`
- `teacher_id`
- `subject_id`
- `group_id`
- `semester_id`
- `assigned_at`

Это отвечает на вопрос: какой преподаватель ведет какой предмет у какой группы в каком семестре.

### `headman_assistants`

Помощники старосты.

Поля:

- `id`
- `group_id`
- `student_id`
- `permissions`
- `assigned_by`
- `is_active`
- `assigned_at`
- `revoked_at`

`permissions` - массив строк, например права на отметки или справки.

### `campus_settings`

Геозона кампуса.

Поля:

- `id`
- `name`
- `lat`
- `lng`
- `radius_m`
- `updated_at`

Используется для проверки геоотметки.

### `attendance_thresholds`

Пороги "красной зоны" по посещаемости.

Поля:

- `id`
- `group_id`
- `subject_id`
- `threshold_pct`
- `set_by`
- `created_at`

Может быть порог на группу или предмет. Используется в отчетах и предупреждениях.

### `homeworks`

Домашние задания.

Поля:

- `id`
- `group_id`
- `subject_id`
- `semester_id`
- `lesson_date`
- `lesson_number`
- `title`
- `description`
- `link`
- `published_by`
- `created_at`
- `updated_at`
- `due_reminder_sent_at`

Комментарий: ДЗ привязано не к `lesson_id`, а к natural key: группа + дата пары + номер пары.

### `homework_completions`

Личная отметка студента, что ДЗ выполнено.

Поля:

- `id`
- `homework_id`
- `student_id`
- `completed_at`

### `homework_weekly_digest_runs`

Защита от повторной отправки недельного дайджеста ДЗ.

Поля:

- `id`
- `group_id`
- `semester_id`
- `week_start`
- `sent_at`

### `password_reset_tokens`

Токены сброса пароля.

Поля:

- `id`
- `user_id`
- `token_hash`
- `expires_at`
- `used_at`
- `created_at`

### `audit_log`

Аудит админских действий.

Поля:

- `id`
- `user_id`
- `action`
- `target_type`
- `target_id`
- `correlation_id`
- `extras`
- `succeeded`
- `error_message`
- `created_at`

### Технические таблицы `academic_db`

`academic_outbox` хранит события перед отправкой в RabbitMQ.

Поля:

- `id`
- `event_type`
- `payload`
- `status`
- `retry_count`
- `last_error`
- `created_at`
- `sent_at`

`event_consumer_processed` защищает от повторной обработки одного события.

Поля:

- `consumer_id`
- `event_id`
- `processed_at`

`shedlock` блокирует scheduled jobs, чтобы они не запускались параллельно.

Поля:

- `name`
- `lock_until`
- `locked_at`
- `locked_by`

## 2. `schedule_db`

PostgreSQL. Владелец: `schedule-service`.

Это БД расписания. Она не хранит полные данные пользователей, групп и предметов, а держит их ID как логические ссылки на `academic_db`.

### `schedule_items`

Шаблон регулярного расписания.

Поля:

- `id`
- `group_id`
- `subject_id`
- `semester_id`
- `day_of_week`
- `lesson_number`
- `start_time`
- `end_time`
- `week_type`
- `room`
- `is_active`
- `created_at`

`week_type`:

- `all`
- `odd`
- `even`

`teacher_id` был, но удален. Преподаватель определяется через `teacher_subject_groups` в `academic_db`.

### `lessons`

Конкретные пары на конкретную дату.

Поля:

- `id`
- `schedule_item_id`
- `date`
- `status`
- `is_geo_blocked`
- `is_blocked_by_headman`
- `blocked_by_user_id`
- `blocked_at`
- `cancel_reason`
- `cancelled_by`
- `cancelled_at`
- `created_at`
- `closed_at`
- `reminder_midpoint_sent_at`
- `reminder_near_end_sent_at`

Статусы:

- `planned`
- `active`
- `closed`
- `cancelled`

Комментарий: для UX это основа журнала, календаря, состояния "пара идет сейчас", "отменена", "закрыта", "гео заблокировано".

### `schedule_one_off_lessons`

Разовые пары, которые добавляет староста или админ.

Поля:

- `id`
- `group_id`
- `subject_id`
- `semester_id`
- `date`
- `lesson_number`
- `classroom`
- `created_by`
- `created_at`

### `iso_parity_reconciliation`

Техническая таблица, чтобы один раз выровнять четность недель.

Поля:

- `id`
- `executed_at`
- `note`

### Технические таблицы `schedule_db`

`schedule_outbox` хранит события перед отправкой в RabbitMQ.

Поля:

- `id`
- `event_type`
- `payload`
- `status`
- `retry_count`
- `last_error`
- `created_at`
- `sent_at`

`event_consumer_processed` защищает от повторной обработки одного события.

Поля:

- `consumer_id`
- `event_id`
- `processed_at`

`shedlock` блокирует scheduled jobs.

## 3. `attendance_db`

MongoDB. Владелец: `attendance-service`.

Это БД фактической посещаемости, заявок на уважительную причину и опоздалые отметки.

### `attendances`

Фактические отметки посещаемости.

Поля:

- `_id`
- `lesson_id`
- `user_id`
- `group_id`
- `subject_id`
- `semester_id`
- `lesson_number`
- `lesson_date`
- `status`
- `source`
- `marked_by`
- `excuse_reason`
- `created_at`
- `updated_at`

Статусы:

- `PRESENT`
- `ABSENT`
- `EXCUSED`
- `FREE_ATTENDANCE`
- `CANCELLED`

Источники:

- `STUDENT_GEO`
- `HEADMAN`
- `AUTO_SCHEDULER`
- `LATE_CHECKIN`
- `HEADMAN_EXCUSE`

Есть уникальность `(lesson_id, user_id)`: один студент не может иметь две разные отметки на одну пару.

### `excuse_tickets`

Заявки на уважительную причину.

Поля:

- `_id`
- `student_id`
- `group_id`
- `student_name`
- `lesson_ids`
- `excuse_type`
- `comment`
- `status`
- `decision_by`
- `decision_comment`
- `decision_at`
- `created_at`
- `updated_at`

Типы:

- `ILLNESS`
- `SUMMONS`
- `UNIVERSITY_ORDER`
- `EXEMPTION`
- `FREE_ATTENDANCE`
- `OTHER`

Статусы:

- `DRAFT`
- `SUBMITTED`
- `APPROVED`
- `REJECTED`

### `late_checkin_requests`

Запросы на опоздалую отметку, когда студент не смог отметиться сам.

Поля:

- `_id`
- `student_id`
- `group_id`
- `lesson_id`
- `student_name`
- `status`
- `decision_by`
- `decision_at`
- `created_at`
- `updated_at`

Статусы:

- `PENDING`
- `APPROVED`
- `REJECTED`

### Технические коллекции `attendance_db`

`attendance_outbox` хранит события перед отправкой в RabbitMQ.

Поля:

- `_id`
- `event_type`
- `payload`
- `status`
- `retry_count`
- `last_error`
- `created_at`
- `sent_at`

`event_consumer_processed` дедуплицирует входящие события.

Поля:

- `consumer_id`
- `event_id`
- `processed_at`

`shedLock` - Mongo-версия ShedLock для scheduled jobs.

## 4. `notification_db`

MongoDB. Владелец: `notification-service`.

Это БД пользовательских уведомлений и Web Push.

### `notification_history`

История уведомлений.

Поля:

- `_id`
- `user_id`
- `type`
- `payload`
- `sent_at`
- `read_at`
- `trace_id`

`payload` хранит snapshot события на момент отправки.

Есть TTL по `sent_at`, по умолчанию 30 дней.

Типы уведомлений:

- `EXCUSE_REQUESTED`
- `EXCUSE_APPROVED`
- `EXCUSE_REJECTED`
- `LATE_CHECKIN_REQUESTED`
- `LATE_CHECKIN_APPROVED`
- `LATE_CHECKIN_REJECTED`
- `LESSON_STARTED`
- `LESSON_CLOSED`
- `LESSON_CANCELLED`
- `LESSON_REMINDER`
- `HOMEWORK_WEEKLY_DIGEST`
- `HOMEWORK_DUE_REMINDER`
- `ATTENDANCE_RED_ZONE`
- `ATTENDANCE_MARKED_BY_HEADMAN`

### `push_subscriptions`

Подписки браузера или PWA на Web Push.

Поля:

- `_id`
- `user_id`
- `group_id`
- `endpoint`
- `p256dh`
- `auth`
- `is_headman`
- `created_at`
- `last_seen`

Есть уникальность `(user_id, endpoint)`.

### `reminder_attendance_state`

Состояние, кто уже отметился, чтобы не слать лишние reminder-уведомления.

Поля:

- `_id`
- `lesson_id`
- `user_id`
- `status`
- `marked_at`

### `event_consumer_processed`

Техническая дедупликация входящих RabbitMQ-событий.

Поля:

- `consumer_id`
- `event_id`
- `processed_at`

## 5. Redis

Redis у тебя не основная БД, а runtime-хранилище.

### Auth

- `refresh:{userId}:{jti}` - валидные refresh-токены.
- `jwt:public_key` - кеш публичного ключа JWT.
- `otp:{telegramId}` - OTP-код.
- `otp_code:{code}` - обратный индекс код -> telegramId.
- `otp_sent:{telegramId}` - cooldown повторной отправки.
- `otp_attempts:{telegramId}` - лимит запросов OTP.
- `otp_verify_attempts:{telegramId}` - ошибки проверки OTP.
- `otp_verify_by_code_miss:{ip}` - защита от brute-force.
- `login_attempts:{ip}:{login}` - счетчик ошибок логина.
- `login_blocked:{ip}:{login}` - временная блокировка логина.
- `ws_ticket:{uuid}` - одноразовый WebSocket ticket.
- `ws_ticket_user:{userId}` - набор ticket'ов пользователя.

### Academic

Redis cache:

- `groups`
- `group_members`
- `users`
- `active_semester`
- `campus_geofence`
- `rbac`
- `subject`

### Attendance

- `attendance:dedup:{lessonId}:{userId}` - защита от двойной отправки отметки.
- `attendance:rate:{userId}` - лимит попыток отметки.

### Notification

- `notif:prefs:user:{userId}` - hash настроек уведомлений.

Поля настроек:

- `lessons`
- `reminders`
- `homework`
- `tickets`
- `schedule`
- `group`
- `mute_until`

### Telegram bot

- `reminder:msgs:{lesson_id}:{user_id}` - ids Telegram-сообщений reminder'ов.
- `reminder:marked:{lesson_id}:{user_id}` - студент уже отмечен.
- OTP message tracker и request message tracker - чтобы удалять или обновлять сообщения в Telegram.

### API Gateway

Redis token bucket для rate-limit'ов по маршрутам.

## 6. RabbitMQ

RabbitMQ не стоит считать БД. Это транспорт событий.

Через него идут события:

- `otp.requested`
- `otp.verified`
- `lesson.started`
- `lesson.closed`
- `lesson.cancelled`
- `lesson.reminder`
- `attendance.marked`
- `excuse.requested`
- `excuse.decided`
- `late_checkin.requested`
- `late_checkin.decided`
- `homework.published`
- `homework.updated`
- `group.renamed`
- `group.archived`

Источник истины все равно в PostgreSQL или MongoDB. RabbitMQ нужен, чтобы `notification-service`, bot и другие consumers получили событие.

## Главный вывод для нового UX

Для Vue-фронта основные экраны будут ложиться так:

- Пользователи, роли, группы, предметы, семестры, ДЗ, настройки кампуса: `academic_db`.
- Расписание, календарь, статусы пар, отмены, блокировки гео: `schedule_db`.
- Журнал посещаемости, заявки, опоздалые отметки, отчеты: `attendance_db`.
- История уведомлений, push-подписки, unread/read: `notification_db`.
- Сессии, OTP, rate-limit, preferences, временные состояния: Redis.

Архитектурно это нормальное разнесение. Не стоит смешивать эти данные обратно в одну БД: границы уже довольно логичные.

Следующий полезный шаг для редизайна: сделать карту `экран -> API -> таблицы`, чтобы новый Vue-фронт не повторил старую путаницу страниц.
