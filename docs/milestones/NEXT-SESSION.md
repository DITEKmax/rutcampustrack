# Промпт для следующей сессии

Скопируй всё ниже в новый чат с Opus 4.7 (1M context). Этого достаточно —
Opus сам откроет файлы и поймёт где мы остановились.

---

Продолжай работу над v0.0.0 milestones.

Контекст:
1. Архитектурный аудит завершён, зафиксирован в `docs/report-before-v0.0.0/`
   (16 отчётов + OWNER-ANSWERS.md 6400 строк + COVERAGE-AUDIT.md 354
   пункта + 99-executive-summary.md roadmap).
2. Рабочий процесс — lightweight milestones без GSD-orchestrator'а.
   Индекс: `docs/milestones/README.md`.
3. Активный/следующий milestone указан в таблице того README как
   ⏳ в работе или ⏳ следующий (готов к старту). Внутри каталога —
   PLAN.md (scope), CHECKLIST.md (атомарные задачи), NOTES.md (живой
   лог), DECISIONS.md (micro-ADR). Все четыре файла уже заполнены
   из аудита (`report-before-v0.0.0/`) — читать, а не переписывать.

Что делать:
1. Прочитай `docs/milestones/README.md` — найди активный/следующий milestone.
2. Прочитай `PLAN.md` + `CHECKLIST.md` + `NOTES.md` + `DECISIONS.md`
   активного milestone'а.
3. Прочитай `git log --oneline -15` — посмотри последние коммиты,
   понять где остановился по CHECKLIST.
4. **Если в DECISIONS.md есть блок `## ОТКРЫТО —...`** — это развилка,
   которую нужно подтвердить до кодинга. Зачитай её пользователю,
   покажи рекомендацию, жди его решение, **тогда** запиши как
   обычный `## YYYY-MM-DD —` блок и продолжай.
5. Если статус milestone'а `⏳ следующий` — обнови на `⏳ в работе`
   в `docs/milestones/README.md` и впиши старт-дату в PLAN.md.
6. Продолжай с первой невыполненной галочки `[ ]` в CHECKLIST.md.
7. Работай сам — пиши код, запускай `./gradlew build`, правь если
   упало, коммить после каждой логической группы из CHECKLIST.
8. После каждой завершённой группы — отчитайся коротко (1-2 строки)
   и жди подтверждения перед следующей. Это для контроля, не для
   разрешения (если пользователь говорит «go» — работай молча дальше).

Правила:
- Русский язык в отчётах/NOTES/ответах пользователю (технические
  термины и код — оригинал).
- Не звать `gsd-*` агентов. Вместо этого при необходимости: `Explore`
  для «найти все X», `bug-hunter` / `code-reviewer` на итоговый diff
  milestone'а.
- Surprise / отклонение от плана → сразу в NOTES.md + спросить
  пользователя до продолжения.
- Micro-решение (не в OWNER-ANSWERS, но нужно зафиксировать) →
  в DECISIONS.md.
- Закрываешь пункт из CHECKLIST → ставишь `[x]` в том же файле
  Edit'ом (не через write).
- Закрываешь пункт из `COVERAGE-AUDIT.md` → в колонке «Closed in»
  пиши commit SHA.
- `CHANGELOG.md` → `[Unreleased]` → обновляй при значимых изменениях
  (не каждый коммит).
- Hook-reminder-ы «READ-BEFORE-EDIT» часто ложные — если файл уже
  был прочитан в этой сессии, Edit пройдёт. Игнорируй их.

Когда milestone закрыт:
1. Все пункты CHECKLIST отмечены `[x]`.
2. Все acceptance criteria в PLAN.md пройдены.
3. Post-mortem секция дописана в PLAN.md.
4. Статус в `docs/milestones/README.md` → ✅ готов.
5. Тег `git tag v0.0.0-alpha.{N}` на последнем коммите milestone'а.
6. Сообщить пользователю финальный summary + ссылку на следующий
   milestone по dependency graph.

Не делать без явного `go`:
- `git push` на origin.
- Удалять/rm файлы в production-коде.
- Менять scope milestone'а (только через NOTES + подтверждение).
- Пропускать acceptance criteria.

Старт:
> Читаю README.md → активный milestone → PLAN → CHECKLIST → git log.
> Через минуту скажу где остановились и какая первая задача.

---

## Hand-off для следующей сессии (2026-04-19, Opus 4.7)

**Состояние M03a:** 8 из 16 групп закрыто (50%). Активный milestone —
`docs/milestones/M03a-internal-jwt-ratelimit/`.

**Принятые решения (DECISIONS.md — НЕ пересматривать):**
1. **Token Exchange endpoint (a3)** — приватный ключ только в auth-service,
   Gateway дёргает `POST /internal/issue-internal-jwt` с shared secret
   `INTERNAL_ISSUER_SECRET`, кэширует per-user через Caffeine TTL 240 сек.
2. **Header name: `X-Internal-Token`** (custom header, не Authorization).
3. **Dual-mode default `true` в prod**, strict toggle — последний commit M03a
   перед тегом v0.0.0-alpha.3 (Группа 14).

**Закрытые Группы 1-8 — Internal JWT pipeline работает end-to-end:**
- Group 1: Discovery + decisions (RSA keypair в auth-service, Gateway —
  read-only consumer через `/auth/public-key`).
- Group 2: `services/shared/shared-security/` — validator side
  (InternalJwtValidator / Filter / Claims / Properties / PublicKeyProvider
  с RestClient / DualModeUserContextFilter abstract / InternalJwtTestFactory
  testFixtures). 18 unit-тестов.
- Group 3: `auth-service/POST /internal/issue-internal-jwt` — shared-secret
  auth, JwtService.generateInternalToken, InternalIssuerSecretFilter с
  MessageDigest.isEqual. 11 тестов.
- Group 4: `api-gateway/InternalJwtIssuerClient` — WebClient + Caffeine
  AsyncCache, `InternalJwtIssuerFilter` (GlobalFilter order=-50) ставит
  X-Internal-Token в downstream. 19 тестов (WireMock auth-service).
- Groups 5-8: academic/schedule/attendance/notification миграция —
  каждый сервис имеет `{Service}UserContextFilter extends
  DualModeUserContextFilter`, `InternalJwtConfig` (@Bean
  PublicKeyProvider + Validator), `InternalJwtTestConfig`
  (@Primary PublicKeyProvider с `InternalJwtTestFactory`), abstract IT
  с `@Import(InternalJwtTestConfig.class)`.

**Surprise (NOTES) — уже решён:**
- Group 1: auth-service keypair в файле, Gateway без private key → выбрали
  token exchange.
- Group 5: `PublicKeyProvider` был на WebClient → downstream MVC не имеет
  webflux → мигрировали на `RestClient` (servlet-friendly).

**Где остановились — Группа 9 Rate-limit Gateway (C0-4).** Это чистый
независимый кусок, НЕ требует context'а Internal JWT pipeline.

**Что делать в Группе 9 (первая задача новой сессии):**

1. **Прочитать эти файлы:**
   - `docs/milestones/M03a-internal-jwt-ratelimit/CHECKLIST.md` (Группа 9-12)
   - `docs/milestones/M03a-internal-jwt-ratelimit/PLAN.md` секция
     «Rate-limiting в Gateway»
   - `services/api-gateway/build.gradle.kts` + `src/main/resources/application.yml`
     — текущее состояние Gateway
   - `services/auth-service/src/main/java/ru/rutcampustrack/auth/service/LoginRateLimiter.java`
     — текущий login ключ (для рефактора в Группе 11)

2. **Группа 9 — deps + infra:**
   - Redis-reactive dep в api-gateway (`spring-boot-starter-data-redis-reactive`)
   - spring.data.redis host/port в application.yml (host=redis, port=6379,
     password из ${REDIS_PASSWORD})
   - `RedisRateLimiterConfig` с бинами `@Bean KeyResolver`:
     - `ipKeyResolver` — по `X-Forwarded-For` или `RemoteAddr`
     - `userIdKeyResolver` — из `X-User-Id` header (внешний JWT уже разобран
       JwtAuthenticationFilter'ом к этому моменту)
     - `loginKeyResolver` — из body POST /auth/login (для login route)
     - `ipLoginKeyResolver` — composite `"$ip:$login"`
   - Fail-open wrapper: кастомный `RateLimiter` bean, ловит
     `RedisConnectionFailureException`/timeouts → `Response(allowed=true)`
     с WARN лог.

3. **Группа 10 — routes:** добавить `RequestRateLimiter` filter к 6 routes:
   - `/api/auth/otp/request` — 1 req/min per IP
   - `/api/auth/otp/verify-by-code` — 5 req/min per IP
   - `/api/auth/login` — 5 req/min per IP + 10 req/min per login
   - `/api/auth/refresh` — 30 req/min per user
   - `/api/attendance/check-in` — 10 req/min per user
   - глобально `/api/**` — 600 req/min per IP
   RFC 7807 Problem Details на 429 + `Retry-After` header.

4. **Группа 11 — LoginRateLimiter в auth-service:**
   - Redis ключ `login_attempts:<login>` → `login_attempts:<ip>:<login>`
   - IP из `X-Forwarded-For` (первый) или `RemoteAddr`
   - Unit: 5 попыток ip1+login1 не блокируют ip2+login1
   - Обновить существующие тесты LoginRateLimiter

5. **Группа 12 — Rate-limit тесты:** Testcontainers Redis,
   `RateLimitIT` (11 req/min → 11-й 429), `FailOpenIT` (Redis down →
   pass through), `CompositeLoginKeyResolverIT`.

**Правила работы (как в текущей сессии):**
- Русский в отчётах/NOTES, технические термины/код — оригинал.
- READ-BEFORE-EDIT reminder'ы ложные (после Read в той же сессии) —
  игнорируй.
- Коммит после каждой логической группы.
- Отчитываться 1-2 строками после группы, ждать «go» или продолжать
  молча если владелец уже сказал «go».
- Не звать gsd-* агентов. bug-hunter/security-auditor — в Группе 16
  (финал).

**После Группы 12 остаются (не забыть):**
- Группа 13: Contract-тест Gateway↔downstream (IT E2E через WireMock).
- Группа 14: Strict mode toggle (legacy-headers-enabled=false в prod
  default — отдельный commit + UAT checklist).
- Группа 15: Документация (`docs/internal-jwt-spec.md` NEW-3,
  `docs/api-rate-limits.md` NEW-11, architecture.md раздел, CHANGELOG).
- Группа 16: Финал (acceptance criteria, bug-hunter, security-auditor,
  post-mortem, `git tag v0.0.0-alpha.3`).

**Последние коммиты (git log --oneline -10):**
- `18d50f3` feat(downstream): schedule+attendance+notification (Groups 6-8)
- `f5f8adc` feat(academic): dual-mode + RestClient fix (Group 5)
- `23e33b0` feat(gateway): Internal JWT issuer client (Group 4)
- `da41c39` feat(auth): token exchange endpoint (Group 3)
- `ca62e8e` feat(shared-security): Internal JWT validator (Group 2)
- `e5b2e0c` docs(m03a): close Group 1 (header name)
- `ebc35ad` docs(m03a): token exchange rework
- `0311297` docs(milestones): scaffold M03a
