# Future ideas — backlog для пост-v0.0.0

Идеи и решения, которые осознанно отложены на будущее. Не блокируют релиз,
но фиксируются здесь, чтобы не потерять.

---

## Безопасность

### Magic-link для первого входа (вместо plaintext initial_password)

**Текущее состояние (v0.0.0, accepted by owner 2026-04-18):**
Поле `users.initial_password` хранится в БД в открытом виде, отдаётся через
REST `/academic/users`, через gRPC `GetUserByTelegramId`, и Telegram-бот
пересылает пароль студенту в `/start`. По решению владельца (см.
`docs/report-before-v0.0.0/OWNER-ANSWERS.md` M1 + 01-Q1) это by design:
проект вне юрисдикции РФ, 152-ФЗ не применяется, технический риск принят.

**Идея на будущее:**
Перейти на одноразовый setup-токен ("magic-link"):
1. При создании пользователя `auth-service` генерирует
   `setup_token` (random 32-byte), хранит хэш в `password_setup_tokens`
   с TTL 24 часа.
2. `academic-service` отдаёт через gRPC не `initial_password`, а ссылку
   вида `https://ruttrack.site/setup-password?token={raw_token}`.
3. Бот шлёт ссылку студенту. По клику — форма «задайте свой пароль»,
   токен сжигается после первого использования.
4. В БД остаётся только bcrypt-хэш пользовательского пароля.
   Поле `initial_password` удаляется.

**Что это закроет (когда сделаем):**
- 01 P0-2 (initial_password в БД)
- 02 P0-1 (initial_password в REST/gRPC)
- 06 P0-3 (plaintext в Telegram)
- 08 P0-1 (initial_password в proto-контракте)
- 10 P2-13 (admin UI показывает пароль)
- Снимает риск утечки через XSS / supply-chain / Telegram-оператора

**Оценка работы:**
~3-4 человеко-дня (auth-service migration + endpoint, academic-service
изменения REST/gRPC, бот update, web-panel/PWA setup-страница, e2e-тест).

**Когда делать:**
Скорее всего v0.1 или позже. Не критично пока проект — узкий круг
пользователей и accept-tradeoff явно зафиксирован.

---

## Архитектура

### Выделить auth-owned схему `users` (вариант b из 01-Q-P0-3)

**Текущее состояние (v0.0.0, accepted by owner 2026-04-18):**
`auth-service` подключён к `academic_db`, таблица `users` владеется
academic-service'ом (Flyway-миграции в academic). Это **shared-DB**
между двумя bounded contexts. Нарушает CLAUDE.md "Database-per-Service",
но осознанно принято: один разработчик, тесная связь auth+users,
performance важнее изоляции.

**Идея на будущее:**
Сделать auth-service владельцем таблицы `users` без выделения отдельного
PostgreSQL-контейнера:
1. Перенести Flyway-миграцию `V1__baseline.sql` (часть про `users`)
   из `academic-app/src/main/resources/db/migration/` в
   `auth-service/src/main/resources/db/migration/V1__users.sql`.
2. В academic-service: `flyway.enabled: false` для users-related таблиц,
   academic читает users через gRPC к auth (как notification-bot уже делает).
3. Деплой-порядок: auth → academic → schedule → ... (auth владеет схемой
   и поднимается первым).
4. Разделить `flyway_schema_history` через разные `flyway.table` или схемы
   (`auth.flyway_schema_history`, `academic.flyway_schema_history`),
   чтобы оба сервиса не топтались по одной таблице.

**Что это закроет (когда сделаем):**
- 01 P0-3 (auth подключён к чужой БД)
- Чёткая граница ownership: auth владеет users
- Изменение users-схемы (mfa_secret, last_login_at, lockout_policy)
  не требует трогать academic
- Архитектура соответствует CLAUDE.md

**Оценка работы:**
~1 человеко-день (перенос миграции + правка academic чтобы не валидировал
users + gRPC-вызовы вместо JOIN'ов в admin-flow + тесты).

**Когда делать:**
Когда понадобится auth-only функционал (MFA, login-аналитика, lockout
policies, password recovery поверх magic-link, audit logs логинов).
Сейчас accept tradeoff (вариант c из 01-Q-P0-3).

---

## Frontend

### PWA для admin/teacher ролей

**Текущее состояние (v0.0.0, accepted by owner 2026-04-18):**
PWA (`frontends/pwa/`) обслуживает только роли STUDENT и STUDENT+headman.
Admin и teacher работают исключительно в web-panel (Angular) на десктопе.
`RoleGuard` в роутере PWA пускает только STUDENT — остальные роли
редиректятся на `/forbidden` или на `/login` web-panel'а.

**Идея на будущее:**
Расширить PWA на admin и teacher роли — для работы с мобильного устройства
в рамках дня:
- Teacher: быстрый просмотр журнала текущей пары, отметка «занятие
  проведено», закрытие пары раньше срока.
- Admin: approve excuse-тикетов на ходу, просмотр статистики группы,
  уведомления о красной зоне.

**Что нужно для реализации:**
- Добавить `/teacher/*` и `/admin/*` роуты в PWA роутер.
- Расширить `RoleGuard` (из 09 P0-3 фикса) на допуск роли.
- Адаптировать существующие web-panel фичи под мобильный UX (touch,
  маленький экран, offline-режим для excuse-approval).
- Переиспользовать Angular web-panel'овские API-вызовы — структура
  бэкенда общая, нужен только новый React-UI.

**Оценка работы:**
~1-2 недели (каждая роль — отдельный набор страниц, pagination, поиск,
filtering, push-триггеры).

**Когда делать:**
Когда появится запрос от реальных пользователей («хочу approve на ходу»).
В v0.0.0 фокус на студенческий core-flow.

---

## NEW-146: Mongo aggregation pipeline для ReportService (M05 G5 deferred)

**Origin:** M05 Группа 5 / DECISIONS D9. OWNER-ANSWERS P2-10/5
предписывал «SQL-aggregate вместо in-memory stream», но в текущем
коде применить нельзя без архитектурных изменений.

**Blocker:** `ReportService.filterExistingLessons` (строки 417-426)
делает defence-in-depth фильтр удалённых уроков через gRPC
`scheduleGrpcClient.getLessonsByIds` **после** загрузки attendance
records. Mongo `$group` pipeline не знает про alive-lesson state в
schedule-service (кросс-сервис), поэтому полный aggregation pipeline
нарушит invariant (stale docs попадут в отчёт при missed
`lesson.deleted` event).

**Варианты реализации в будущем:**

1. **Денормализация `lesson_alive` флага в attendance docs.** При
   `lesson.deleted` event'е attendance-service выставляет
   `lesson_alive=false` на всех matching doc'ах (batch update). Mongo
   pipeline фильтрует `$match: { lesson_alive: true }` и делает
   `$group` по статусам. Trade-off: потенциальное рассогласование
   при broker downtime (лечится reconciliation job).

2. **Отдельная коллекция `dead_lessons` с TTL index.** При
   `lesson.deleted` — insert в `dead_lessons`, pipeline делает
   `$lookup` или `$nin`. Проще миграция, но extra collection.

3. **Pre-aggregated stats collection (materialized view).** Раз в
   N минут считаем per-user per-semester stats в отдельную Mongo
   collection. Hot-path reads — тривиальные `findById`. Trade-off:
   eventual consistency + scheduler complexity.

**Ожидаемый выигрыш:** O(N) server RAM → O(1) + Mongo-side
aggregation. Для студента с 200 lesson-records per semester —
~200× reduction на network transfer (stats только, не raw records).

**Когда делать:** M06 (Ops hardening) или M07 (если появится
конкретная latency-проблема на production traffic). Сейчас
single-pass accumulators (D9) закрывают immediate need.

---

## NEW-146-checklist: Аудит-чеклист для `.collect(toList())` агрегации

Использовать при PR-ревью service-слоя:

- [ ] Если результат `.collect(toList())` передаётся в `.stream().count()` /
      `.stream().sum()` / `.stream().filter(...).count()` — это **hotspot**.
      Вариант: single-pass accumulator с `for` + int counter'ами (cheap,
      invariant-preserving) или SQL `GROUP BY` (если нет cross-service
      validation после load'а).
- [ ] Если результат `.collect(toList())` передаётся в `.subList(offset, end)`
      — это **in-memory pagination hotspot**. OOM-risk на 1000+ rows.
      Вариант: Spring Data `Pageable` с native `countQuery` (см.
      `LessonRepository.pageByScheduleItemIdInAndDateBetweenAndStatusIn`
      как reference в schedule-service).
- [ ] Если результат используется как `Map<K, V>` с агрегацией
      (count/sum) — `toMap(key, v -> seed, (a,b) -> merge)` уже
      single-pass, менять не нужно.

---
