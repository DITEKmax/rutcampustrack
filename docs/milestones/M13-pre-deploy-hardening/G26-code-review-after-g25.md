# G26 — Code Review после G25 hot-fix marathon

**Diff:** `v0.0.0-alpha.14..v0.0.0-alpha.15` (25 коммитов, 1971 добавлено / 621 удалено)
**Дата ревью:** 2026-04-26
**Автор ревью:** Claude Code (Sonnet 4.6)

---

## 1. Executive Summary

G25 был написан в режиме «чтобы CI зеленело» — инфраструктурные проблемы (RSA entropy hang, MongoDB auth, Gateway RL bug, e2e TLS) устранены корректно и с достаточным объяснением в комментариях. Качество кода в целом приемлемое, но три категории требуют cleanup до v0.0.0 production deploy: (1) диагностические артефакты, которые нужно были только для G25, но остались в production path (`console.log` в @smoke тесте, `step-by-step` init-логи в `JwtService`); (2) дублирующая запись в `TEST_USERS` (`student` и `headman` идентичны); (3) `burstCapacity: 600` на `/api/auth/login` — это CI-workaround, а не production security config.

---

## 2. Findings Table

| # | Severity | File : Line | Issue | Fix | Why |
|---|----------|-------------|-------|-----|-----|
| F01 | P1 | `application.yml:122` | `burstCapacity: 600` на `auth-login` — burst из 50 логинов залпом неприемлем для prod; обходит смысл rate-limit против brute-force | Вернуть `60` (стандартная формула). Параллельность CI решить через `PLAYWRIGHT_WORKERS=1` или отдельный env для тестов | CI workaround просочился в prod config; злоумышленник с одного IP может слать 50 login-запросов без throttle |
| F02 | P1 | `tests/e2e/specs/auth.spec.ts:22–37` | Тест `diagnostic: direct POST /api/auth/login` с `console.log` в `@smoke` сьюте — диагностический тест, нужный только во время G25, теперь шумит в CI-репорте и не несёт ценности | Удалить тест либо перенести в отдельный `@diag` grep-tag, отключённый в CI | `console.log` в production тесте = anti-pattern; тест не является частью smoke coverage — он дублирует assertion за счёт side-effect print |
| F03 | P1 | `tests/e2e/fixtures/users.ts:50–55` | `headman` запись в `TEST_USERS` полностью идентична `student` (тот же login `student`, пароль `password`, `isHeadman: true`, `expectedLandingPath: '/headman/dashboard'`) — дублирование данных | Удалить запись `headman` из `TEST_USERS`. Все тесты использующие `TEST_USERS.headman` переключить на `TEST_USERS.student` | DRY violation; если seed изменится, нужно менять в двух местах; вводит в заблуждение будущего читателя |
| F04 | P2 | `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:64,66` | `log.info("RSA private key parsed (PKCS#8, {} bytes)", ...)` и `log.info("RSA public key parsed (X.509, {} bytes)", ...)` — step-by-step диагностика, добавленная в G25.16 для трассировки, теперь является noise в prod логах | Понизить до `log.debug(...)` | На каждом рестарте prod-сервиса логи засоряются bytes-count'ами; информация уже есть в `log.info("RSA kid resolved: {}", keyId)` |
| F05 | P2 | `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:92` | `log.info("RSA kid resolved: {}", keyId)` — избыточно, т.к. следующий `log.info("RSA key pair ready (kid={}), ...")` уже содержит kid | Удалить строку 92 | Дублирует информацию через 3 строки |
| F06 | P2 | `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:118,120` | `log.info("Caching public key in Redis (post-startup)...")` + `log.info("Public key cached in Redis successfully")` — обрамляют одну `set()` операцию; второй лог ценен, первый избыточен | Удалить строку 118 (`Caching public key in Redis (post-startup)...`) | Одна операция не нуждается в `start` + `end` логах уровня INFO; достаточно итогового |
| F07 | P2 | `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/service/JwtService.java:205–242` | Метод `nonBlockingSecureRandom()` используется только в `else`-ветке (regenerate ключей), которая на prod и CI никогда не выполняется (ключи pre-generated в Dockerfile через G25.15). Метод содержит тяжёлый Javadoc, объясняющий несколько уже устаревших попыток (G25.13, G25.14) | Метод оставить (safety net для dev-env без предгенерированных ключей), но сократить Javadoc до 3–4 строк ключевой причины; убрать историю G25.13/G25.14 | Javadoc объёмом 200+ слов описывает зачёркнутые попытки — confusion для следующего инженера; сам метод полезен как fallback |
| F08 | P2 | `services/api-gateway/src/main/resources/application.yml:38–47` | Закомментированный блок `# default-filters:\n#   - DedupeResponseHeader=...` остался в YAML с большим пояснительным комментарием. Комментарий объясняет bug SCG 4.x, но закомментированный код сигнализирует «возможно вернуть позже» | Удалить закомментированные строки YAML (строки `# default-filters:`, `#   - DedupeResponseHeader=...`); пояснение о bug оставить как обычный комментарий если нужно | Закомментированный конфиг в YAML вводит в заблуждение — выглядит как «легко раскомментировать», хотя это будет воспроизводить баг |
| F09 | P2 | `services/api-gateway/src/main/resources/application.yml:105–106` | `NEW-XXX` — unresolved placeholder для backlog-задачи (ip+login composite RL). Номер issue не назначен | Создать реальный issue в backlog и заменить `NEW-XXX` на реальный номер, либо написать `TODO(backlog)` без псевдо-номера | `NEW-XXX` — неработающий cross-reference; следующий инженер не найдёт задачу |
| F10 | P2 | `services/auth-service/Dockerfile:75` | `head -c 4 /dev/urandom \| od -A n -t x1 \| tr -d ' \n' > /keys/kid.txt` генерирует kid из 4 байт (8 hex символов). `JwtService:89` при fallback на code-path генерации использует `UUID.randomUUID().toString().substring(0, 8)` — тоже 8 символов. Но Dockerfile kid не strip'ает trailing newline через `tr -d ' \n'`, а `od` может добавлять пробел в начале | Проверить и добавить `| xargs` или использовать `openssl rand -hex 4` вместо `od` pipeline | Если kid.txt содержит leading space, `JwtService:87` (`Files.readString(...).strip()`) отработает корректно — `.strip()` есть. Проблемы нет, но pipeline избыточно сложный |
| F11 | P2 | `tests/e2e/specs/auth-token-lifecycle.spec.ts:136–142` | Комментарий `M13 G25.23 — student-headman пользователь...` поясняет почему URL `/headman/` вместо `/student/` — контекст ценен, но может быть короче | Сократить комментарий до одной строки: `// seed student: is_headman=true → landing /headman/` | Комментарий на 4 строки для очевидного следствия из users.ts |
| F12 | P2 | `services/auth-service/auth-app/src/main/java/ru/rutcampustrack/auth/AuthApplication.java:15–22` | Javadoc-комментарий к `Security.setProperty(...)` корректен и нужен, но упоминает `Tomcat session ID generator, CSRF token generator` — auth-service не использует Spring Security CSRF (stateless JWT). Вводит в заблуждение | Убрать упоминание CSRF из комментария, оставить суть: Tomcat + Lettuce/Netty могут вызывать `getInstanceStrong()` | Misleading comment снижает доверие к документации |
| F13 | P3 | `tests/e2e/specs/auth.spec.ts:99–104` | Тест `student-headman dashboard — TODO color-contrast fix` помечен `test.skip(true, ...)` с длинным runtime-сообщением. Это placeholder, но `test.skip` с `true` как первым аргументом означает unconditional skip — скипнутые тесты не очевидны в CI без `--reporter=list` | Добавить `// eslint-disable-next-line playwright/no-skipped-test` или зарегистрировать в TODO-list с issue number; убрать escape-пояснение из runtime skip message | Длинный skip message не читается в playwright report; лучше короткий `TODO: G25.24 color-contrast` + ссылка на issue |
| F14 | P3 | `services/notification-bot/tests/test_callback_excuse.py`, `test_callback_late_checkin.py` | ruff reformatting объединил многострочные `await handle_*(...\n    ...\n)` в однострочные вызовы длиной 100+ символов (например, `await handle_excuse_decision(cb, event_publisher=event_publisher_mock, academic_client=academic_client_mock)` — 99 символов). Формально не нарушает ruff default (100 chars), но граничный случай | Ничего не менять — ruff форматирование прошло; читаемость не нарушена | Стиль, не функциональность |
| F15 | P3 | `services/shared/shared-security/src/main/java/ru/rutcampustrack/shared/security/PublicKeyProvider.java:58–65` | Комментарий внутри `getPublicKey()` на 5 строк описывает M13 G25.22 контекст и историю. После стабилизации этот контекст будет неактуален | После G26 cleanup сократить до `// lazy retry: init() failed if auth-service was not ready at startup` | Operational context в production code; достаточно одной строки |

---

## 3. Детальный анализ спорных вопросов

### 3.1 `nonBlockingSecureRandom()` — мёртвый код или safety net?

**Вывод: SAFETY NET, оставить.**

В production и CI ключи генерируются через `openssl` в Dockerfile (G25.15), поэтому ветка `else` в `JwtService.init()` (которая вызывает `nonBlockingSecureRandom()`) не выполняется. Однако метод является защитой для dev-окружения (локальный запуск без Docker, первый запуск без `/keys` volume). Удалять его нельзя. Нужно только сократить Javadoc (F07).

### 3.2 `burstCapacity: 600` — CI workaround или валидный prod config?

**Вывод: CI WORKAROUND, нужна prod-версия.**

Комментарий явно объясняет: `8 specs × 2 workers × 2 retries = до 32 запросов от одного IP CI runner'а`. Это CI-специфичная причина. В prod:
- Легитимный пользователь не делает 50 login-запросов залпом
- Злоумышленник с одним IP получает burst из 50 попыток — это существенно снижает защиту против credential stuffing

Решение: использовать `burstCapacity: 60` (стандартный) в production config, а CI либо использовать `PLAYWRIGHT_WORKERS=1`, либо e2e-specific override через env-variable в docker-compose.e2e.yml.

### 3.3 Комментарии `M13 G25.NN` в production коде

Большинство комментариев вида `// M13 G25.22 — ...` содержат ценный architectural context объясняющий нетривиальные решения (SCG 4.x bug, entropy entropy hang, Lettuce lazy-init). Они НУЖНЫ и должны остаться. Исключение: короткие inline-комментарии которые можно сократить (F11, F15).

### 3.4 `PublicKeyProvider.getPublicKey()` lazy retry — корректно ли?

**Вывод: КОРРЕКТНО.**

Lazy retry на `null` key является idempotent: `fetchAndCache()` вызывает HTTP GET к auth-service, при ошибке silently returns (публичный ключ остаётся `null`), следующий вызов повторит. Synchronous call в WebFlux context — потенциальная проблема (blocking call в reactive thread), но `PublicKeyProvider` используется только в `DualModeUserContextFilter` который является servlet-stack фильтром, не reactive. Проблемы нет.

### 3.5 `init-mongo.js` — корректная идемпотентность?

**Вывод: КОРРЕКТНО**, с оговоркой. Комментарий утверждает что при повторном запуске на существующем volume Bitnami пропускает init-scripts. Это верно для Bitnami Mongo образа. Однако `createUser` на непустом volume выбросил бы ошибку `already exists`. Защита через Bitnami behaviour работает, но при смене образа на официальный `mongo` было бы падение. Это acceptable risk для current infrastructure lock.

---

## 4. Top-15 Cleanup для G26 commit

| Priority | Severity | Action | File | Lines |
|----------|----------|--------|------|-------|
| 1 | **P1** | Вернуть `burstCapacity: 60` для prod; документировать CI workaround в `.env.ci` или `docker-compose.e2e.yml` | `application.yml` | 122 |
| 2 | **P1** | Удалить диагностический тест `diagnostic: direct POST /api/auth/login` (или переместить в отдельный `@diag` tag, отключённый в CI) | `auth.spec.ts` | 19–38 |
| 3 | **P1** | Удалить дублирующую запись `headman` из `TEST_USERS`, заменить `TEST_USERS.headman` на `TEST_USERS.student` везде | `users.ts` | 50–55 |
| 4 | **P2** | Понизить `log.info("RSA private key parsed ...")` и `log.info("RSA public key parsed ...")` до `log.debug(...)` | `JwtService.java` | 64, 66 |
| 5 | **P2** | Удалить `log.info("RSA kid resolved: {}", keyId)` — дублирует следующий info-лог | `JwtService.java` | 92 |
| 6 | **P2** | Удалить `log.info("Caching public key in Redis (post-startup)...")` — оставить только итоговый лог | `JwtService.java` | 118 |
| 7 | **P2** | Сократить Javadoc `nonBlockingSecureRandom()`: убрать историю G25.13/G25.14, оставить суть (urandom non-blocking, SHA1PRNG software CSPRNG) | `JwtService.java` | 205–222 |
| 8 | **P2** | Удалить закомментированные строки `# default-filters:` / `#   - DedupeResponseHeader=...` из YAML | `application.yml` | 38–47 |
| 9 | **P2** | Заменить `NEW-XXX` на реальный backlog issue номер (создать issue если нет) | `application.yml` | 105–106 |
| 10 | **P2** | Убрать упоминание `CSRF token generator` из комментария в `AuthApplication.java` (auth-service stateless, нет CSRF) | `AuthApplication.java` | 19–21 |
| 11 | **P2** | Сократить комментарий `M13 G25.22` в `PublicKeyProvider.getPublicKey()` до 1 строки | `PublicKeyProvider.java` | 58–64 |
| 12 | **P2** | Сократить inline-комментарий `M13 G25.23` в `auth-token-lifecycle.spec.ts` строки 136–139 | `auth-token-lifecycle.spec.ts` | 136–139 |
| 13 | **P2** | Сократить Javadoc `cachePublicKeyInRedis()`: убрать speculation о `jwks.json endpoint` (endpoint не существует) | `JwtService.java` | 99–113 |
| 14 | **P3** | Добавить issue number к `test.skip` для headman color-contrast теста | `auth.spec.ts` | 99–104 |
| 15 | **P3** | Упростить `kid.txt` генерацию в Dockerfile: `openssl rand -hex 4 > /keys/kid.txt` вместо `head | od | tr` pipeline | `Dockerfile` (auth-service) | 75 |

---

## 5. Что НЕ нужно менять (rationale)

| Элемент | Почему оставить |
|---------|-----------------|
| `nonBlockingSecureRandom()` метод целиком | Safety net для dev-env без Docker; fallback при отсутствии `/dev/urandom` (Windows). Убрать = регрессия для локальной разработки |
| `Security.setProperty("securerandom.strongAlgorithms", ...)` в `AuthApplication.main()` | Реальный production fix для alpine JDK 21; Tomcat и Lettuce действительно вызывают `getInstanceStrong()`. Убирать нельзя |
| `@EventListener(ApplicationReadyEvent.class) cachePublicKeyInRedis()` | Архитектурно правильное решение — Redis cache должен заполняться после того как Tomcat готов принимать healthcheck |
| Lazy retry в `PublicKeyProvider.getPublicKey()` | Корректный fix для race condition при старте downstream-сервисов раньше auth-service |
| `Map.of()` вместо реальных RL headers в `FailOpenRateLimiter` | Единственный workaround для SCG 4.x bug с ReadOnlyHttpHeaders; убирать означает воспроизводить оригинальный баг |
| Комментарии `M13 G25.NN` с техническим контекстом | Большинство объясняют нетривиальные platform-specific workarounds (SCG bug, entropy issue, Bitnami authSource) — essential для future maintainability |
| `infra/mongo/init-mongo.js` вместо `MONGODB_EXTRA_USERNAMES` | Реальный fix Bitnami authSource bug; declarative env vars работают неверно в этом digest |
| `ignoreHTTPSErrors: true` в playwright.config.ts | E2E-only config (self-signed cert в тестовой среде); не влияет на prod |
| Комментарий к `DedupeResponseHeader` bug в `application.yml` | Объясняет неочевидное архитектурное решение; без него следующий инженер снова добавит filter и воспроизведёт баг |
| `head -c 4` для kid.txt (F10) | `.strip()` в JwtService корректно обрабатывает whitespace; risk = theoretical. P3 cleanup, не blocking |

---

## Checklist для Code Quality

### Clean Code

- [x] **DRY**: Нарушение в `TEST_USERS` (F03) — `student` и `headman` идентичны
- [x] **KISS**: Нарушение F02 (diagnostic test в @smoke) и F10 (od|tr pipeline)
- [x] **No Dead Code**: `log.info` на строках 64, 66, 92, 118 — noise (F04, F05, F06)
- [x] **No Magic Numbers**: `burstCapacity: 600` (F01) — magic number без именованной константы
- [x] **No Commented-out Code**: Закомментированный YAML блок (F08)

### Error Handling

- [x] **Consistent Strategy**: `cachePublicKeyInRedis()` глотает exception с `log.warn` — acceptable (best-effort cache), задокументировано
- [x] **Specific Catches**: `catch (IOException | NoSuchAlgorithmException e)` — специфичные типы, корректно

### Backend Specific

- [x] **No Business Logic in Controllers**: Без изменений в этом diff

**Quality Score: 22/30** *(с учётом только затронутых файлов; основные нарушения: F01-F03 P1, F04-F12 P2)*
