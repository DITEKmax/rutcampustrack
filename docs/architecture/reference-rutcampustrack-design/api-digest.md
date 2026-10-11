# API дайджест — RutCampusTrack

Все публичные REST-эндпоинты по сервисам, сгенерировано из `docs/openapi/*.json`.
Роли доступа указаны прямо в описаниях (например `(HEADMAN/ADMIN)`).

Базовый путь через API Gateway: `/api/{service}/...`
Формат ошибок: RFC 9457 Problem Details.

**Как использовать:** это справка о том, какие данные СЕЙЧАС доступны.
Если экрану нужно что-то другое — описывай потребность и помечай `[предложение по бэкенду]`.
Бэкенд можно менять под фронт.

## Сводка

| Сервис | Операций |
|---|---|
| academic | 54 |
| attendance | 22 |
| auth | 10 |
| notification | 10 |
| schedule | 16 |
| **Всего** | **112** |

---

## academic (54 операций)

- `GET /academic/assignments` — Список назначений для группы и семестра
- `POST /academic/assignments` — Назначить преподавателя на предмет/группу/семестр (HEADMAN/ADMIN)
- `GET /academic/assignments/my` — Мои назначения (TEACHER)
- `DELETE /academic/assignments/{id}` — Удалить назначение (HEADMAN/ADMIN)
- `GET /academic/assistants` — Список помощников старосты для группы
- `POST /academic/assistants` — Назначить помощника старосты (HEADMAN/ADMIN)
- `DELETE /academic/assistants/{id}` — Отозвать помощника (HEADMAN/ADMIN)
- `PATCH /academic/assistants/{id}/permissions` — Обновить права помощника (HEADMAN/ADMIN)
- `GET /academic/dashboard/stats` — Сводная статистика системы (ADMIN)
- `GET /academic/groups` — Список групп: фильтр по статусу + поиск (ADMIN/TEACHER)
- `POST /academic/groups` — Создать группу (ADMIN)
- `GET /academic/groups/my/members` — Участники группы текущего пользователя (STUDENT/HEADMAN)
- `POST /academic/groups/promote` — Выполнить промоушен групп (ADMIN). Запускать после preview.
- `POST /academic/groups/promote/preview` — Preview промоушена групп (dry-run, ADMIN)
- `GET /academic/groups/{id}` — Получить группу по ID
- `PUT /academic/groups/{id}` — Полное обновление группы (PUT, ADMIN)
- `DELETE /academic/groups/{id}` — Удалить группу (ADMIN)
- `GET /academic/homeworks` — Список заданий для группы и семестра
- `POST /academic/homeworks` — Создать домашнее задание (HEADMAN)
- `GET /academic/homeworks/{id}` — Получить задание по ID
- `PUT /academic/homeworks/{id}` — Полное обновление задания (PUT, HEADMAN, только автор)
- `DELETE /academic/homeworks/{id}` — Удалить задание (HEADMAN, только автор)
- `POST /academic/homeworks/{id}/complete` — Отметить задание выполненным (STUDENT)
- `DELETE /academic/homeworks/{id}/complete` — Снять отметку выполнения (STUDENT)
- `GET /academic/semesters` — Список семестров
- `POST /academic/semesters` — Создать семестр (ADMIN)
- `GET /academic/semesters/overlap` — Проверка пересечения дат семестра (ADMIN)
- `GET /academic/semesters/{id}` — Получить семестр по ID
- `PUT /academic/semesters/{id}` — Полное обновление семестра (PUT, ADMIN)
- `DELETE /academic/semesters/{id}` — Удалить семестр с подтверждением (ADMIN)
- `PATCH /academic/semesters/{id}/activate` — Активировать семестр (ADMIN)
- `GET /academic/subjects` — Список предметов (HEADMAN — только своя группа, ADMIN — все)
- `POST /academic/subjects` — Создать предмет (HEADMAN/ADMIN)
- `GET /academic/subjects/{id}` — Получить предмет по ID
- `PUT /academic/subjects/{id}` — Полное обновление предмета (PUT, HEADMAN/ADMIN)
- `DELETE /academic/subjects/{id}` — Удалить предмет (HEADMAN/ADMIN)
- `POST /academic/subjects/{id}/teachers/{teacherId}` — Добавить преподавателя к предмету (HEADMAN/ADMIN)
- `DELETE /academic/subjects/{id}/teachers/{teacherId}` — Удалить преподавателя из предмета (HEADMAN/ADMIN)
- `GET /academic/thresholds` — Список всех настроенных порогов
- `PUT /academic/thresholds/global` — Установить глобальный порог посещаемости (ADMIN)
- `PUT /academic/thresholds/group` — Установить порог для группы (HEADMAN/ADMIN)
- `GET /academic/thresholds/resolve` — Разрешить эффективный порог
- `PUT /academic/thresholds/subject` — Установить порог для предмета в группе (HEADMAN/ADMIN)
- `GET /academic/users` — Список пользователей
- `POST /academic/users` — Создать пользователя
- `GET /academic/users/by-ids` — Batch-резолв display-имён пользователей по ID
- `GET /academic/users/me` — Получить профиль текущего пользователя (из X-User-Id header)
- `PATCH /academic/users/me/avatar` — Сменить аватар текущего пользователя (BUG-004)
- `GET /academic/users/teachers` — Список преподавателей (без постраничной разбивки)
- `GET /academic/users/{id}` — Получить пользователя по ID
- `PUT /academic/users/{id}` — Полное обновление пользователя (PUT). Только ADMIN.
- `DELETE /academic/users/{id}` — Архивировать пользователя (soft delete). Только ADMIN.
- `PATCH /academic/users/{id}` — Частичное обновление пользователя (PATCH)
- `POST /academic/users/{id}/transfer` — Перевести студента в другую группу

## attendance (22 операций)

- `POST /attendance/checkin` — Геоотметка студента
- `POST /attendance/excuses` — Создать тикет о пропуске
- `GET /attendance/excuses/group/{groupId}` — Тикеты группы
- `GET /attendance/excuses/me` — Мои тикеты
- `POST /attendance/excuses/with-file` — Создать тикет о пропуске с файлом
- `GET /attendance/excuses/{id}` — Детали тикета
- `PATCH /attendance/excuses/{id}/status` — Одобрить/отклонить тикет
- `GET /attendance/health-check`
- `GET /attendance/late-checkin/group/{groupId}` — Group late-checkin requests
- `GET /attendance/late-checkin/pending` — Список ожидающих запросов группы (для старосты)
- `POST /attendance/late-checkin/{lessonId}` — Попросить старосту отметить
- `POST /attendance/late-checkin/{requestId}/decision` — Решение старосты по запросу (веб-канал)
- `PUT /attendance/lessons/{lessonId}/students/{userId}` — Ручная отметка посещаемости
- `POST /attendance/marks/batch` — Пакетная отметка посещаемости (M05 P2-10/4)
- `GET /attendance/reports/headman-weekly/current` — Export one headman weekly report
- `POST /attendance/reports/headman-weekly/export` — Export multiple selected headman weekly reports
- `GET /attendance/reports/headman-weekly/weeks` — Weeks of the active semester available for headman weekly report export
- `GET /attendance/reports/journal` — Journal grid (students x dates)
- `GET /attendance/reports/lesson/{lessonId}` — Lesson attendance list
- `GET /attendance/reports/student/dashboard` — Aggregated dashboard for the student PWA/Mini-App home screen (v9.0): overall %, donut breakdown, weekly timeseries, top missed subjects
- `GET /attendance/reports/student/records` — Student attendance records
- `GET /attendance/reports/student/stats` — Student attendance stats

## auth (10 операций)

- `POST /auth/change-password` — Change password
- `POST /auth/login` — Login with credentials
- `POST /auth/logout` — Logout
- `POST /auth/otp/request` — Request OTP code
- `POST /auth/otp/verify` — Verify OTP code
- `POST /auth/otp/verify-by-code` — Verify OTP code without telegram ID
- `GET /auth/public-key` — Get RSA public key
- `POST /auth/refresh` — Refresh access token
- `POST /auth/tma` — Authenticate via Telegram Mini App
- `POST /auth/ws-ticket` — Issue WebSocket ticket

## notification (10 операций)

- `POST /internal/alert`
- `GET /notifications` — Пагинированный список уведомлений текущего user'а
- `POST /notifications/mark-all-read` — Пометить все уведомления прочитанными (bulk)
- `GET /notifications/preferences` — Get current user's notification preferences
- `PUT /notifications/preferences` — Replace current user's notification preferences
- `GET /notifications/unread-count` — Счётчик непрочитанных уведомлений (Caffeine-cached 30s)
- `PATCH /notifications/{id}/read` — Пометить уведомление прочитанным
- `POST /push/subscribe` — Subscribe to push notifications
- `DELETE /push/subscribe` — Unsubscribe from push notifications
- `GET /push/vapid-public-key` — Get VAPID public key

## schedule (16 операций)

- `GET /schedule/groups/{groupId}/lessons` — Получить расписание группы за диапазон дат (все роли)
- `GET /schedule/health-check`
- `GET /schedule/items` — Список шаблонов расписания для группы и семестра
- `POST /schedule/items` — Создать шаблон расписания (HEADMAN/ADMIN)
- `GET /schedule/items/{id}` — Получить шаблон по ID
- `PUT /schedule/items/{id}` — Полное обновление шаблона (PUT, HEADMAN/ADMIN)
- `DELETE /schedule/items/{id}` — Деактивировать шаблон расписания (HEADMAN/ADMIN)
- `POST /schedule/lessons/mass-cancel` — Массовая отмена уроков для группы (HEADMAN/ADMIN)
- `POST /schedule/lessons/{id}/blockage` — Заблокировать пару старостой — запретить геоотметку, посещаемость ставит только староста вручную (HEADMAN)
- `DELETE /schedule/lessons/{id}/blockage` — Снять блокировку пары старостой (HEADMAN)
- `PATCH /schedule/lessons/{id}/cancel` — Отменить урок (HEADMAN/ADMIN). Допускается отмена PLANNED/ACTIVE/CLOSED.
- `PATCH /schedule/lessons/{id}/geo-block` — Включить/выключить гео-блокировку урока (HEADMAN/ADMIN)
- `PATCH /schedule/lessons/{id}/restore` — Восстановить отменённый урок (HEADMAN/ADMIN)
- `GET /schedule/one-off-lessons` — Список разовых пар для группы в диапазоне дат
- `POST /schedule/one-off-lessons` — Создать разовую пару (HEADMAN)
- `DELETE /schedule/one-off-lessons/{id}` — Удалить разовую пару (HEADMAN, любая дата D-22)
