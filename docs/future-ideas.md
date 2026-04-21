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

### Mini-app unification: copy+adapt from PWA after M12

**Текущее состояние (v0.0.0, accepted by owner 2026-04-21, M07 старт):**
Telegram Mini App (`frontends/mini-app/`) живёт как отдельный React-проект
с ручными TypeScript interfaces скопированными из backend DTO. M07 G3
(openapi-typescript) затронул только PWA + web-panel — mini-app
**осознанно опущен**, чтобы избежать двойной работы при предстоящем
переезде.

**План миграции:**
1. Дождаться закрытия всех v0.0.0 milestones (M07→M12), стабилизации PWA
   в проде.
2. После M12 — скопировать `frontends/pwa/` → `frontends/mini-app/` как
   новый baseline (с openapi-ts, RFC 7807 interceptor, axe-core baseline,
   ConfirmWithReasonDialog, `useSwipeHandler`/`useDateNavigation`/
   `PullToRefresh`, unified DrawerMenu — всё из M07).
3. Адаптировать под Telegram WebApp SDK constraints:
   - `window.Telegram.WebApp` init (viewport, theme, BackButton, MainButton).
   - Auth через `initData` (HMAC-verify на backend) вместо login-формы.
   - Geolocation через `Telegram.WebApp.requestLocation` (не browser API).
   - Haptic feedback через `Telegram.WebApp.HapticFeedback`.
4. Провести security-audit + code-review diff'а для Telegram-specific кода.

**Что закрывается:**
- Дублирование interfaces между PWA и mini-app.
- Drift-guard наконец начинает защищать mini-app от breaking DTO changes.
- Технический долг v0.0.0: mini-app на ручных interfaces, на которые не
  распространяется CI drift-check.

**Trigger:** после tag `v0.0.0` (вся M07-M12 закрыта) и после минимум
2 недель стабилизации PWA в проде без критичных багов.

**Estimate:** ~3-5 дней (copy = 1д, Telegram SDK adapter = 1-2д,
security/code review + hot-patches = 1-2д).

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

## FSM storage миграция в Redis для Telegram-бота (v0.1)

**Источник:** `06-notification-bot.md` P2-7, отложено из M08/M09.

**Текущее состояние:** `notification-bot/__main__.py:105` использует
`MemoryStorage()` для Aiogram FSM. При рестарте контейнера состояние
теряется.

**Почему не блокер сейчас:** FSM реально не используется (bot имеет
только однокадровые callback_query handler'ы). Фикс preventive —
чтобы при добавлении первого multi-step сценария (excuse-wizard,
reminder-customization) не словить regression.

**Идея на будущее:**
```python
from aiogram.fsm.storage.redis import RedisStorage
storage = RedisStorage.from_url(settings.redis_url)
dp = Dispatcher(storage=storage)
```

**Оценка работы:** ~2 часа + тесты (вместе с первой multi-step
фичей).

---

## `/actuator/**` исключить из OTel tracing (v0.1)

**Источник:** M04 backlog → M05 → M06 G8e → M07 NOTES → **v0.1**.

**Текущее состояние:** OpenTelemetry трейсит все HTTP requests,
включая `/actuator/health`, `/actuator/prometheus`, `/actuator/metrics`.
Это засоряет Tempo метриками scraping'а (Prometheus опрашивает
/actuator/prometheus каждые 15s → ~5760 spans/сутки per сервис
только от scraping'а).

**Почему не блокер сейчас:** severity MINOR; отмечено в
`application.yml` комментарием honest-о («до custom Sampler»).

**Идея на будущее:** custom `OtelSampler` bean в shared-observability:
```java
@Bean
Sampler skipActuator(@Value("${otel.sampler.skip-paths}") List<String> paths) {
    return Sampler.parentBased(Sampler.alwaysOn()).and(
        (ctx, name, kind, attrs, links) -> {
            var httpTarget = attrs.get(AttributeKey.stringKey("http.target"));
            if (httpTarget != null && paths.stream().anyMatch(httpTarget::startsWith)) {
                return SamplingResult.create(SamplingDecision.DROP);
            }
            return SamplingResult.recordAndSample();
        }
    );
}
```

**Оценка работы:** ~1 день (shared-observability bean + integration
tests per service).

---

## Real sparklines backend для admin-dashboard (v0.1)

**Источник:** `10-frontend-web-panel.md` QC7, `OWNER-ANSWERS.md`
NEW-94, отложено в M07 (placeholder «доступно в v0.1»).

**Текущее состояние (M07 G9, commit завершает милестоун):**
admin-dashboard показывает skeleton-bars + info-сообщение «Графики
посещаемости появятся в следующем релизе» вместо Chart.js с
псевдо-данными. `buildSpark` helper и `chart.js`/`ng2-charts`
import'ы в admin-dashboard удалены (chart.js остаётся transitive в
teacher-stats и student-stats, где используются реальные данные
per-subject). Реального time-series endpoint'а нет.

**Идея на будущее:** `GET /api/admin/dashboard/metrics` → time-series
агрегаты:
```json
{
  "active_users_7d": 245,
  "new_check_ins_24h": 1820,
  "red_zone_count": 12,
  "sparklines": {
    "check_ins_by_day": [{"day": "2026-04-15", "count": 1820}, ...],
    "active_users_by_day": [...]
  }
}
```

**Архитектурный выбор (NEW-94):**
- v0.1: миграция на **Prometheus** как источник time-series
  (уже запущен в M04 observability stack). Grafana-style query
  через `PrometheusMeterRegistry.query()` или proxy.
- **НЕ** делать SQL-агрегацию в attendance_db (MongoDB `$group`
  тяжёлый, блокировки при writes, через 3-6 месяцев всё равно
  переписывать).

**Оценка работы:** ~2 дня (Prometheus query client + caching +
frontend wiring).

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
