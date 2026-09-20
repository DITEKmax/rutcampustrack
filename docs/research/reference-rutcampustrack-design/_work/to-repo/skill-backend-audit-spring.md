---
name: backend-audit-spring
description: Read-only multi-phase audit of a Spring Boot (Java) microservice — security, authz/IDOR, data integrity & concurrency, JPA/Flyway correctness, gRPC/REST contracts, eventing (outbox/RabbitMQ), architecture. Use when the user invokes /backend-audit-spring, asks to "audit the backend", "аудит бэкенда", "проверь сервис на корректность/безопасность", or wants a forensic review of one service. MANDATORY: never audit the whole monorepo in one pass — scope to ONE service (auth/academic/schedule/attendance/notification/api-gateway).
---

# backend-audit-spring

Read-only форензик-аудит одного Spring Boot микросервиса из RutCampusTrack. Каждая фаза пишется в отдельный timestamped файл до начала следующей. Отчёты — только в `_reports/`, НИКОГДА не в репозиторий.

**STRICT READ-ONLY:** не меняй, не рефактори, не создавай файлы внутри `services/`. Только чтение + запись отчётов в `_reports/`.

Для рефакторинга найденного — отдельно скилл `care-refactoring-java`. Для отладки конкретного бага — промпт `root-cause-debug`.

---

## Scope Discipline (обязательно)

**Никогда не аудируй весь monorepo за один проход.** Большой скоуп перегружает контекст, размывает находки, повышает галлюцинации.

Один запуск = **один сервис**: `auth-service`, `academic-service`, `schedule-service`, `attendance-service`, `notification-service` или `api-gateway`.
Для полного аудита — прогони скилл по каждому сервису отдельно, свежим саб-агентом без общего контекста.

Учитывай стек конкретного сервиса (из `CLAUDE.md`):
- auth → Redis, JWT, `shared-security`, gateway issuer
- academic → PostgreSQL + Redis cache
- schedule → PostgreSQL
- attendance → MongoDB, домены `checkin/` + `report/` через `shared/port/`
- notification-web → MongoDB + STOMP/Caffeine; notification-bot → Python Aiogram + Redis
- api-gateway → Spring Cloud Gateway, WebFlux, Redis (единственное исключение из contract-first)

---

## Pre-flight (до любой фазы)

1. **Output dir:** `_reports/<service>/<YYYY-MM-DD_HHMM>/` в домашней директории пользователя (НЕ в репо). Создай, прервись если не удалось.
2. **Repo context** (в шапку каждого отчёта): `git rev-parse --short HEAD`, `git branch --show-current`, `git status --short`, имя сервиса.
3. **Snapshot сервиса:** `rg --files services/<service>` и `./gradlew :services:<service>:<module>:dependencies --configuration runtimeClasspath` (какие стартеры реально подключены).
4. **Быстрый скан-сигнал** (ориентиры до глубокого чтения):
   ```bash
   rg "findAll\(\)|findAll\(Pageable" services/<service> -l           # unbounded queries
   rg -i "password\s*=|secret\s*=|apiKey\s*=|token\s*=" services/<service> --type java -l   # hardcoded secrets
   rg "@Transactional" services/<service> --type java -l              # tx boundaries
   rg "@PreAuthorize|@Secured|SecurityFilterChain|authorizeHttpRequests" services/<service> -l
   rg "log\.(info|debug|warn|error)\(.*(password|token|secret|jwt)" services/<service> --type java
   rg "TODO|FIXME|HACK" services/<service> --type java
   ```
5. **Read before writing:** прочитай контроллеры, сервисы, entity, конфиги безопасности, миграции затронутого сервиса ДО выводов. Не аудируй по памяти.

---

## Формат находки

```
[file:line] SEVERITY — короткий заголовок

Observed: что код реально делает (cite file:line)
Expected: что подразумевает контракт/бизнес-правило/best practice
Impact: конкретное последствие для пользователя/данных/безопасности
Fix direction: конкретный следующий шаг (без переписывания всего файла)
```
Severity: CRITICAL / HIGH / MEDIUM / LOW. Каждая находка — с `file:line`. Никаких «в целом рекомендуется добавить X» без якоря в коде.

---

## Phase 1 — Security, Auth & Data Privacy

**Роль:** Senior Security Engineer (OWASP), Spring Security expert.

- **Authz / IDOR:** каждый бизнес-эндпоинт имеет явную проверку (`@PreAuthorize` / SecurityFilterChain)? Есть ли owner-check на CLIENT-scoped ресурсах, или id можно подменить (перебор id → чужие данные)? Это главный риск — в проекте уже фиксили 12 IDOR (M13).
- **Роли:** может ли неаутентифицированный/низкороль передать `role: ADMIN` в payload? Проверка ролей headman/teacher/admin реально enforced на бэке, а не только на фронте?
- **JWT / gateway issuer:** секреты не имеют пустых/дефолтных фолбэков? Expiration enforced? Refresh-token rotation атомарна и replay-safe? `shared-security` dual-mode корректен?
- **Rate limit:** на `/login`, `/refresh`, OTP-эндпоинтах — RL присутствует и адекватен? (OTP brute-force counter — M16 G3).
- **Секреты:** нет хардкодов ключей/паролей в `.java`/`application.yml`? Логи не печатают token/hash/PII? (gitleaks ловит commit'ы, но проверь runtime-логи).
- **Внутренние секреты:** `INTERNAL_ISSUER_SECRET`, gRPC secrets — fail-fast при отсутствии (RequiredSecretsValidator)?

**Вывод:** `audit_1_security.md`. Проверь: файл существует, >500 байт, иначе стоп.

---

## Phase 2 — Data Integrity, Transactions & Concurrency

**Роль:** Principal engineer, JPA/PostgreSQL/MongoDB expert.

- **Конкурентность:** параллельные запросы (напр. две отметки/два accept) не создают дубль? `@Transactional` + правильный isolation, или TOCTOU-гонка (`findById` + условный `save` вне транзакции)?
- **Идемпотентность событий:** consumer dedup (PG/Mongo/Redis) на RabbitMQ-событиях реально работает? Повторная доставка не дублирует эффект?
- **N+1 и unbounded:** `findAll()` без Pageable/лимита — перечисли все. Дашборд-метрики без батчинга? `@OneToMany` без пагинации?
- **JPA-корректность:** enum-ы строками (`LowercaseEnumConverter`), нет `EnumType.ORDINAL`? Значения в нижнем регистре? Soft delete вместо DELETE?
- **Flyway:** нет ли редактирования applied миграций? Индексы на hot-таблицах — `CONCURRENTLY IF NOT EXISTS`? `ddl-auto: validate` не создаёт схему?
- **Индексы:** поля в `where`/`orderBy`/join имеют индекс, или full scan? Отсутствуют ли уникальные ограничения там, где логически должны быть?
- **MongoDB (attendance/notification):** TTL-индексы, `@Transactional` на replica set, изоляция доменов `checkin/`↔`report/`.

**Вывод:** `audit_2_data.md`. Cross-phase dedup: если уже в Phase 1 — пиши `→ см. Phase 1`, не дублируй.

---

## Phase 3 — Contracts, Architecture & Resilience

**Роль:** Solution Architect, SRE.

- **Contract-first:** контроллер `implements` интерфейс из `*-api-contract`? Маппинги только в интерфейсе? Request=`record`, Response=класс? Без Lombok в контрактах?
- **Ошибки:** единый RFC 9457 `ErrorResponse`? per-service `@ControllerAdvice` не дублирует Spring MVC handler'ы (только domain exceptions)?
- **gRPC:** контракты в `proto/`, генерённое не правится руками, fail-fast на отсутствии секретов?
- **Изоляция доменов:** `report/` не тянет из `checkin/` напрямую (только `shared/port/`)? God-классы (>3 ответственностей в одном файле)?
- **Resilience:** таймзоны/cron (DST-safe?), batch-циклы с per-item try/catch (одна ошибка не валит весь батч)? Startup fail-fast при отсутствии `JWT_SECRET`/`DATABASE_URL`?
- **Config:** нет silent dry-run фолбэка в prod при отсутствующей env-переменной? Stack trace / SQL не утекают в response?

**Вывод:** `audit_3_architecture.md`.

---

## Challenge Pass (до финального саммари)

Провалидируй каждую CRITICAL/HIGH находку из фаз 1–3:
- Если доступны саб-агенты: свежий read-only агент без родительского контекста, только текст находки + путь. По каждой: **Confirmed / Overstated (даунгрейд) / Not an issue (дроп)** + одна строка причины.
- Если нет: перечитай `file:line` свежим взглядом, тот же трёхчастный вердикт.

Применённые вердикты (drop/downgrade/keep) — до сводной таблицы. В саммари укажи, сколько находок дропнуто/даунгрейднуто — это сигнал качества аудита, не провал.

---

## Финальный вывод

`audit_master_summary.md`:
- сводная таблица severity по фазам (CRITICAL/HIGH/MEDIUM/LOW);
- топ-5 проблем безопасности/authz;
- топ-5 проблем данных/конкурентности;
- архитектурные/контрактные замечания;
- **рекомендованный порядок фиксов** (сначала CRITICAL IDOR/authz/secrets → потом данные → потом архитектура).

```
| Phase          | CRITICAL | HIGH | MEDIUM | LOW |
|----------------|----------|------|--------|-----|
| 1 Security     |        N |    N |      N |   N |
| 2 Data         |        — |    N |      N |   N |
| 3 Architecture |        — |    N |      N |   N |
| TOTAL          |        N |    N |      N |   N |
```

## Constraints (все фазы)

- READ-ONLY на `services/`. Ноль записей/правок/веток внутри репозитория.
- Timestamped output — не перезаписывай прошлые аудиты.
- Каждый файл начинается с шапки: дата, git hash, branch, сервис.
- Проверка записи: каждый файл фазы > 500 байт до перехода дальше.
- Никаких full-file rewrite в отчётах. Diff'ы точечные (≤30 строк контекста).
- Никаких деструктивных фиксов: не предлагай удалять валидацию/`@Valid`. Фиксы расширяют логику.
- Severity = влияние на пользователя/данные/безопасность, не элегантность кода.
- Уважай, что проект уже прошёл аудиты M01–M16: сверяйся с `docs/archive/report-before-v0.0.0/`, не выдавай уже закрытое за новое.
```
