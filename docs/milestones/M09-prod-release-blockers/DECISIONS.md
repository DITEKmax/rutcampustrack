# M09 Decisions

Микро-решения, принятые в ходе milestone. Формат: `D{N}` + одна фраза,
затем контекст/альтернативы/последствия в 3-5 строк.

---

## D1 — 01 P0-1 (`auth-api-contract`) отложен в v0.1

**Контекст:** Аудит (`01-auth-service.md:59`) помечает как 🔧 TO-FIX,
owner подтвердил создание модуля (Q-P0-1). Но это **структурный
refactor** (вынесение `AuthController` + DTO + OpenAPI-аннотаций в
отдельный Gradle-модуль) — не блокер безопасности/корректности.

**Альтернативы:**
- (a) Сделать в M09 — +1д, риск breaking change в сборке фронтов.
- (b) Отложить в v0.1 — scope M09 фокусируется на security/tests блокерах.
- (c) Сделать в M07 (Frontend Hardening) — не по теме M07.

**Решение:** (b). Документируется в `docs/future-ideas.md`. После v0.0.0
можно сделать независимо, auth-service — единственный нарушитель (4 других
сервиса уже contract-first), изменение чисто внутреннее.

**Последствия:** `CLAUDE.md` «Правила кодирования → Contract-first»
уточняется: (1) применяется к сервисам с REST API; (2) api-gateway
освобождён (прокси); (3) auth-service — TODO v0.1.

## D2 — api-gateway освобождён от contract-first навсегда

**Контекст:** Правило CLAUDE.md «Каждый сервис имеет `*-api-contract` +
`*-app`» читается буквально, но api-gateway — прокси, собственного
REST API не публикует.

**Альтернатива:** Создать `gateway-api-contract` с пустыми интерфейсами
для соблюдения правила — cargo cult.

**Решение:** Явно исключить gateway из правила в CLAUDE.md. Контракты —
у тех сервисов, куда gateway проксирует.

**Последствия:** 07-api-gateway отчёт P0-1/P0-2 касаются CORS и JWT-
фильтра, не структуры модулей. Эти пункты закрыты в M03a/M03b и M07.

## D3 — landing deep-link: hardcode `https://t.me/ruttrack_bot/ruttrack`

**Контекст:** 12 P0-2 требует кнопки лендинга вести в Telegram, не на
внутренний `/login` (web-panel). NOTES.md Q2 обсуждал явная переменная
vs `getMe()` auto-discovery.

**Альтернативы:**
- (a) Env-переменная `TELEGRAM_BOT_USERNAME=ruttrack_bot` с build-time
  inlining через vite/script. Landing — статический HTML без bundler'а
  (`package.json:6` — "dist/ is canonical source"), build-tooling
  нет, пришлось бы его внедрять.
- (b) `getMe()` на бэкенде + runtime endpoint → лишний HTTP call для
  статической страницы.
- (c) Hardcode `https://t.me/ruttrack_bot/ruttrack` (это же значение
  уже в `.env.prod` → `MINI_APP_URL`).

**Решение:** (c). Bot username — стабильный identifier (смена = rename
бота = плановое событие, requires coordinated rollout). 4 ссылки в
index.html замены одной grep-командой. Cost of change ≪ cost of
adding a build pipeline к статическому landing'у.

**Последствия:** При смене username — grep + sed в landing и коммит.
Если станет чаще — поднять вопрос build-pipeline для landing в v0.1.
`target="_blank" rel="noopener noreferrer"` добавлены (security).

## D4 — OTP события публикуются напрямую, не через shared-outbox

**Контекст:** CHECKLIST G2.2 предписывал «OtpRequestedPublisher через
shared-outbox (M02 OutboxStorage)». Shared-outbox — transactional
outbox pattern: event пишется в таблицу в той же DB-транзакции, что
и основная запись → publisher-job его publish'ит → at-least-once.

**Проблема:** OTP flow не имеет «основной записи» в транзакции.
Код живёт в Redis (TTL 5 мин), а не в Postgres/Mongo. Чтобы
встроить outbox, надо:
- (a) сохранять OTP-код в Postgres (нарушение security-model: код
  теряет эфемерность, появляется в бэкапах DB, расширяет attack
  surface) — **отвергнуто**;
- (b) писать outbox-запись без attached-транзакции, что ломает
  саму гарантию outbox'а (at-least-once без atomicity — те же
  failure modes, что fire-and-forget, но сложнее).

**Альтернатива:** существующий `DomainEventListener`
(auth/event/DomainEventListener.java) — `@EventListener` +
`RabbitTemplate.convertAndSend(EXCHANGE, "", event)`. Уже публикует
`OtpVerifiedEvent` fire-and-forget. Тот же pattern для
`OtpRequestedEvent` — минимум кода, ноль новых зависимостей.

**Failure mode и mitigation:** Rabbit down → event потерян. Но:
- OTP в Redis уже записан → клиент при retry (POST /auth/otp/request)
  перезаписывает Redis-код (new SecureRandom) и публикует новое
  событие. Self-healing (NOTES Q1 вариант C).
- Rate-limit (`otp_sent` cooldown) защищает от spam: пока cooldown
  держит — retry не произойдёт, но TTL самого OTP (5 мин) короче
  Rabbit SLA (минуты в xудшем случае), так что практически
  пользователь просто видит «код не пришёл» и retry'ит.

**Решение:** публиковать `OtpRequestedEvent` через существующий
`DomainEventListener`. CHECKLIST G2.2 закрывается с deviation —
пункт помечен `[~]` и отсылает на D4.

**Последствия:** `OtpRequestedPublisher` как отдельный класс не
создаётся. Вся интеграция — `ApplicationEventPublisher.publishEvent(
new OtpRequestedEvent(...))` в `OtpService.requestOtp`.
