# M16 — UAT verify checklist на проде (VPS)

**Production deploy:** 2026-04-27, commit `070369d6`, run 25010954397.
**URL:** https://ruttrack.site

После деплоя 4/7 backend checkpoint'ов автоматически verified через
`docker logs` / `psql` / `curl` (см. `docs/milestones/M16-post-deploy-hardening/CHECKLIST.md`).
Этот документ — **остальные 3 пункта**, требующие реального user flow
через UI, плюс smoke-тесты для critical paths которые могут быть
скрыто сломаны деплоем.

После прохождения всех зелёных галочек ниже — M16 **closes**, ставим
tag `v0.0.0-alpha.17` на коммит `070369d6`.

---

## Подготовка перед UAT

### Доступы

Тебе понадобятся:

| Что | Зачем | Где взять |
|-----|-------|-----------|
| Admin login (`admin` / пароль из `.env.prod`) | G6 (audit log), G3 OTP, общие smoke | На VPS: `grep ADMIN_PASSWORD /opt/rutcampustrack/.env.prod` |
| Headman test user (`student` с `is_headman=true`) | G7 (bulk-mark RL) | Проверь через admin → users list, пометь любого student как headman, либо используй seed data |
| Browser dev tools (F12) | Network tab для проверки HTTP-кодов | Любой Chromium |
| Telegram bot subscription | Уведомления (smoke) | Если ещё не subscribed — открой `https://t.me/rutcampustrack_bot` и `/start` |
| (опционально) `curl` + Bearer token | Если хочешь проверять напрямую | Получи через login flow |

### Точки входа в систему

| Роль | URL | Логин |
|------|-----|-------|
| Admin | https://ruttrack.site/admin | admin / `.env.prod` |
| Teacher | https://ruttrack.site/teacher | `teacher` / любой test user |
| Student/Headman | https://ruttrack.site/student | `student` / любой test user |
| PWA (mobile) | https://ruttrack.site/app/ | Те же креды |
| Landing | https://ruttrack.site/ → 301 → /login | — |

---

## Чеклист UAT (по группам M16)

> Отмечай галочки **прямо в этом файле** через `[ ]` → `[x]`. После
> выполнения всех — закоммить файл и пинни сюда — закроем M16.

### G1 — OTel tracing (sanity)

**Цель:** убедиться что distributed tracing работает на проде после
переключения порта 4317→4318.

- [ ] Открой Grafana: https://ruttrack.site/grafana/ (basic-auth admin /
      `GRAFANA_PASSWORD` из `.env.prod`)
- [ ] Перейди в **Explore** (иконка компаса слева) → выбери datasource
      **Tempo**
- [ ] В query type выбери `Search` → выбери service `auth-service` (или
      любой другой)
- [ ] Нажми **Run query** → должны появиться trace records за последний
      час
- [ ] Открой любой trace → должен показать spans с метаданными (HTTP
      method, status, duration)
- [ ] Если traces **не появляются** за 1 час — это регрессия, отметь
      ниже и я разберусь

**Результат:**
- [ ] Traces appearing → ✅ G1 verified end-to-end
- [ ] Traces missing → ⚠️ regression, escalate

---

### G3 — OTP brute-force counter (UI flow)

**Цель:** убедиться что OTP-counter работает через реальный login flow,
не только через Gateway RateLimiter.

> **Note:** Gateway RateLimiter (5/min/IP) срабатывает раньше нашего
> OTP counter (20/5min/IP), поэтому прямой curl-spam не проверит наш
> counter — Gateway отрубит на 5-й попытке. Через UI ты введёшь пару
> неверных кодов и проверишь что Prometheus метрика растёт.

- [ ] Открой https://ruttrack.site/login → выбери **OTP via Telegram**
      flow (если есть кнопка/ссылка) или войди как **STUDENT с
      Telegram-привязкой**
- [ ] Запроси OTP (`/auth/otp/request`) → должен прийти 6-значный код в
      Telegram-боте (notification-bot)
- [ ] Введи **неверный** код (например `000000`) — должна быть ошибка
      "Invalid OTP" (HTTP 401)
- [ ] Введи неверный код ещё **2-3 раза** (не больше 5 — упрёшься в
      Gateway RL)
- [ ] Открой Prometheus: https://ruttrack.site/prometheus/ (basic-auth)
      → query: `otp_verify_total{outcome="mismatch"}`
- [ ] Метрика должна **расти** с каждой неверной попыткой (counter
      инкрементируется в auth-service)
- [ ] (опционально) Через 5 минут query: `otp_verify_total` — не
      должно `outcome="throttled"` если ты не делал 20+ попыток

**Результат:**
- [ ] `outcome="mismatch"` инкрементируется при неверном OTP → ✅ G3
      counter работает (вот ваше доказательство G3)
- [ ] Метрика flat → ⚠️ G3 counter не работает, escalate

---

### G6 — Audit log на admin actions (UI flow)

**Цель:** убедиться что `@AdminAction` aspect пишет в `audit_log`
таблицу при реальных admin operations.

- [ ] Открой https://ruttrack.site/admin/users → list пользователей
- [ ] Создай **тестового user** (можно `test_audit_<timestamp>` чтобы
      легко найти и удалить потом):
      - Login: `test_audit_001`
      - Role: `STUDENT`
      - Group: любая существующая
- [ ] После create → проверь UI list → user появился
- [ ] Сделай **archive** (или delete soft) этого user через UI
- [ ] (опционально) Сделай **role change** другому test user'у

Теперь подключись на VPS и проверь audit_log:

```bash
ssh root@ruttrack.site
docker exec rct-postgres-academic psql -U rct_user academic_db -c \
  "SELECT id, user_id, action, target_type, target_id, succeeded, created_at FROM audit_log ORDER BY created_at DESC LIMIT 10;"
```

- [ ] Должны появиться **минимум 2 строки**:
  - `action='user.create'`, `succeeded=true`, `user_id=<твой admin id>`,
    `created_at=<сейчас>`
  - `action='user.archive'`, `succeeded=true`, `user_id=<admin id>`
- [ ] Проверь что `correlation_id` непустой (для tracing-связи)
- [ ] (если делал role change) → `action='user.update'` или `user.patch`

**Cleanup:** удали test user через admin UI → ещё одна строка
`user.archive` (это OK, не мешает).

**Результат:**
- [ ] Записи появились → ✅ G6 verified
- [ ] Таблица пуста после admin actions → ⚠️ aspect не сработал,
      escalate

---

### G7 — Headman rate-limit на bulk-mark (UI flow)

**Цель:** убедиться что новый Redis-backed RL (300/min) позволяет
bulk-mark группы без RESOURCE_EXHAUSTED, и счётчики появляются в Redis.

#### 7.1 Bulk-mark через PWA или admin UI

- [ ] Залогинься как **headman** (или student с `is_headman=true`)
- [ ] Открой PWA `/app/` → раздел journal/attendance group
- [ ] Найди свою группу с **минимум 20 студентами** (если меньше —
      добавь test users в группу через admin)
- [ ] Открой **bulk-mark** для активной пары (или создай fake пару
      через admin)
- [ ] Отметь **все 20+ студентов** через bulk-action как `present`
      (или mixed — present/absent/excused)
- [ ] Засеки время — должно быть **<30 секунд** для всей группы
- [ ] Проверь что **никаких ошибок 429 / RESOURCE_EXHAUSTED** в UI
      (если есть — F12 Network tab покажет)

#### 7.2 Redis state check

На VPS:

```bash
ssh root@ruttrack.site
source <(grep ^REDIS_PASSWORD /opt/rutcampustrack/.env.prod)
docker exec rct-redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning KEYS "rl:headman:*"
```

- [ ] Должны быть keys типа `rl:headman:<userId>:<minute>` со
      значениями (counter incremented при каждой gRPC `isHeadman()`
      call)
- [ ] Если **пусто** — возможно in-memory fallback активирован (не
      должно быть), escalate

#### 7.3 Headman RL Prometheus check

- [ ] Открой Prometheus → query: `headman_rl_redis_failures_total`
- [ ] Должна быть **0** (или близка к 0) — Redis не падал, fail-open
      не активировался

**Результат:**
- [ ] Bulk-mark прошёл <30 сек, Redis keys видны → ✅ G7 verified
- [ ] RESOURCE_EXHAUSTED в UI → ⚠️ G7 не работает, escalate

---

## Smoke checklist (общие critical paths)

После M16 деплоя ничего из «обычной» функциональности **не должно**
сломаться. Эти smoke-тесты проверяют что 6 hotfix'ов в этой сессии
не внесли регрессии.

### Login flow (все 4 роли)

- [ ] **Admin login** через https://ruttrack.site/login → перенаправляет
      на `/admin/...` → дашборд рендерится
- [ ] **Teacher login** → `/teacher/...` → видны journal, schedule
- [ ] **Student login** → `/student/...` → видны homeworks, schedule,
      attendance
- [ ] **Headman login** (student с `is_headman=true`) → `/headman/...`
      или `/student/...` с расширенными правами → видны headman
      sections (excuse approval, bulk-mark)

### PWA mobile

- [ ] Открой https://ruttrack.site/app/ на мобильном (или Chrome
      mobile emulation F12 Ctrl+Shift+M)
- [ ] Login flow работает
- [ ] Geolocation prompt появляется при попытке checkin
- [ ] WebSocket reconnect (попробуй turn-off wifi → turn-on, после
      ~10 сек должен переподключиться)

### Notifications

- [ ] **OTP** (как в G3 сценарии) — код приходит в Telegram bot
- [ ] **Lesson reminder** — поставь test пару через admin за 5-7 минут
      от текущего времени → за 5 мин до начала студенту должен прийти
      reminder в Telegram bot
- [ ] **Web push** в PWA (если subscribed на browser notifications) —
      такой же reminder через service worker

### API Gateway proxy

- [ ] `curl https://ruttrack.site/api/auth/public-key` → 200 + RSA
      public key JSON
- [ ] `curl https://ruttrack.site/api/academic/groups` без auth → 401
      (правильный rejection)
- [ ] (с admin Bearer) `/api/academic/groups` → list groups

### Backend health

На VPS:

```bash
ssh root@ruttrack.site

# Все контейнеры healthy
docker compose -f /opt/rutcampustrack/docker-compose.prod.yml ps -a

# Internal healthchecks
for svc in auth-service academic-service schedule-service attendance-service notification-web api-gateway; do
  echo "--- $svc ---"
  docker exec rct-$svc wget -qO- http://localhost:9090/actuator/health 2>&1 | head -1 || \
  docker exec rct-$svc wget -qO- http://localhost:8080/actuator/health 2>&1 | head -1
done
```

- [ ] Все 27 контейнеров `Up` или `healthy`
- [ ] Все Java сервисы `{"status":"UP"}`

### Grafana dashboards

- [ ] https://ruttrack.site/grafana/ (basic-auth) → Login OK
- [ ] Dashboard `rct-containers` → metrics for всех контейнеров (NEW в G9
      — verify что cadvisor работает без `privileged: true`)
- [ ] Dashboard `business-kpis` → number of users, active sessions, etc.
- [ ] Dashboard `service-health` → up status, response times, error
      rates

### Alertmanager

- [ ] https://ruttrack.site/alertmanager/ (basic-auth) → UI loads
- [ ] **No active alerts** at the moment (если есть — посмотри
      severity и message, escalate если critical)

---

## После всех ✅

Когда **минимум все G1, G3, G6, G7 + smoke login** зелёные — пинг сюда:

> «UAT прошло, можно закрывать M16»

Я сделаю:

1. **Финальный commit** в M16:
   - Update CHECKLIST.md с финальными UAT результатами
   - Update CLAUDE.md статус M16: `✅ Полностью verified end-to-end`
   - Update NEXT-SESSION.md (или archive — стартовая точка для M17)

2. **Tag** `v0.0.0-alpha.17` на текущий HEAD `main`:
   ```bash
   git tag -a v0.0.0-alpha.17 -m "M16 Post-Deploy Hardening — fully verified on VPS"
   git push origin v0.0.0-alpha.17
   ```

3. **NEXT-SESSION.md** для **M17** (или решим что отложено в backlog):
   - G8b full mTLS (deferred — trigger conditions в `future-ideas.md`)
   - CLAUDE.md DB rules update (lessons learned: `-- ##` artefact, `.sql.conf` real fix, CONCURRENTLY на пустых таблицах overkill)
   - actions/checkout v4 SHA bug — теперь зафиксирован в deploy.yml
   - audit_log retention/cleanup job (G6 follow-up)

---

## Если что-то сломалось — где смотреть

| Симптом | Куда смотреть | Команда |
|---------|---------------|---------|
| 502 на /grafana/ /prometheus/ | nginx + upstream healthy | `docker logs rct-nginx --tail 50` |
| 5xx на /api/ | api-gateway | `docker logs rct-api-gateway --tail 100` |
| Auth fail | auth-service + Redis | `docker logs rct-auth-service --tail 100` |
| Notifications не приходят | notification-bot + RabbitMQ | `docker logs rct-notification-bot --tail 100` |
| Slow page load | api-gateway / individual services | Grafana dashboard `service-health` |
| WebSocket disconnect loop | notification-web + nginx | `docker logs rct-notification-web --tail 100` |

Если что-то критичное — отметь в этом файле и выдели **escalate** —
я сделаю follow-up commit.

---

**Source of truth:** этот документ + `docs/milestones/M16-post-deploy-hardening/CHECKLIST.md`.
**Не дублируй галочки** — здесь UAT, там code-level + auto-verified.
