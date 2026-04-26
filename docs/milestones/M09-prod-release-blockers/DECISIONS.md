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

**Решение:** (b). Документируется в `docs/archive/future-ideas.md`. После v0.0.0
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

## D5 — `lesson.deleted` event оставляем (M09 G5)

**Контекст:** PLAN.md G5 (02 P2-11/5) требует «удаление `lesson.deleted`
event + publisher/consumer кода» одновременно с переходом на full snapshot
`lesson.cancelled`. CHECKLIST повторял это.

**Проблема:** `lesson.deleted` и `lesson.cancelled` — **разные use-case'ы**,
не синонимы:
- `lesson.cancelled` — статус-переход строки: `status=PLANNED → CANCELLED`,
  запись в БД остаётся, consumer'ы помечают attendance как CANCELLED.
- `lesson.deleted` — **physical DELETE строки** из Postgres. Publisher'ы:
  1. `LessonGenerationService.regenerateFromDate()` — удаляет PLANNED-строки
     перед regenerate из обновлённого ScheduleItem.
  2. `SubjectDeletedCascadeService` — удаляет cascade при `subject.deleted`
     из academic-service.
  Consumer attendance-service удаляет orphan-attendance-doc'и по ids
  (их иначе некем привязать).

Если `lesson.deleted` убрать — attendance-service получит orphan-docs при
regenerate/subject-delete, и они будут всплывать в отчётах как
«duplicate» рядом с свежими Lesson-строками.

**Альтернативы:**
- (a) Оставить `lesson.deleted` как самостоятельное событие для physical
  delete, `lesson.cancelled` — для status-change. Семантически чисто.
- (b) Слить в одно: `lesson.cancelled(reason="regenerate")` +
  `cancelled_by=system_user_id`. Но это искажает статистику (regenerate
  — это **не** отмена пары студентам, downstream не должен показывать
  «Пара отменена» в UI).

**Решение:** вариант (a). Оставляем `lesson.deleted` как есть. CHECKLIST
пункт «удалить lesson.deleted» помечается `[~]` со ссылкой на D5.

**Последствия:** G5 целиком посвящается расширению `lesson.cancelled`
до full snapshot. `lesson.deleted` не трогаем — работает как было.

## D6 — Оставляем single `excuse.decided` (M09 G6)

**Контекст:** PLAN.md G6 (02 P2-11/8) требует «`event-schemas/excuse.approved.json`
+ `excuse.rejected.json` с full snapshot». CHECKLIST повторял это.

**Текущее состояние (до G6):** существует единое событие `excuse.decided`
с `payload.status in {"approved","rejected"}` + `decision_by` + `decision_comment`.
Симметрично `late_checkin.decided` (используется в G3 late-checkin flow) и
`excuse.requested`. Bot consumer `test_excuse_decided.py` + attendance publisher
`ExcuseEventPublisher.publishDecided` — оба знают про единое событие и ветку
на status.

**Альтернативы:**
- (a) Разбить на два события: `excuse.approved` + `excuse.rejected`. Каждое
  со своим schema-файлом и своим consumer-handler'ом.
- (b) Оставить `excuse.decided` со status-полем (текущее). То же что
  `late_checkin.decided`.

**Решение:** вариант (b). Обоснование:
- Consumer практически всегда ветвится по status (template сообщения
  «одобрено/отклонено», сброс attendance только для approved). Разбивка
  на 2 события добавила бы дублирование handler-кода.
- Единообразие с `late_checkin.decided` — оба decision events одинаковой
  формы. Разбивка только excuse создала бы асимметрию.
- Schema `excuse.decided.json` и `late_checkin.decided.json` уже содержат
  все full snapshot поля: ticket_id/student_id/group_id/decision_by/
  decision_comment/decided_at. Никакой information loss нет.

**Последствия:** G6 закрывается без создания новых schemas. Focus — на
role check (06 P1-1) и audit asymmetric flows (NEW-121).

## D7 — G9 audit: HIGH findings deferred в v0.1 (2 штуки)

**Контекст:** `security-auditor` + `bug-hunter` на diff 17 коммитов M09
(`2996652..4fa58a4`) вытащили:
- **0 BLOCK** (tag `v0.0.0-alpha.10` валидный)
- **2 HIGH**: SA-H1 `verifyOtpByCode` без attempts counter; BH-H1
  дубли `lesson.cancelled` событий в bot dispatcher (event_id дедуп
  отсутствует).
- **8 MEDIUM** (SA-M1/M3/M4/M5, BH-M2/M3/M4/M5/M6).

**Что fix'нуто в G9 hot-patch:**
- **BH-M3** (коммит `2bba0e1`): `otp_requested.py` malformed-event
  warn-log писал весь event с plaintext OTP-кодом → Loki 14d retention.
  Хардкор security issue с низкой вероятностью срабатывания (нужен
  schema-mismatched event), но тривиальный fix — 1 строка, заменил
  на `list(event.keys())`. Мерит немедленного исправления.
- **BH-M4** (коммит `89afd44`): `ContainerMemoryHigh` PromQL давал
  `+Inf > 0.9 = true` для контейнеров без mem_limit → ~10 false-
  positive alerts. Один раз прод поднимется — alert-spam похоронит
  real issues в Telegram боте админа. Добавил guard
  `and on (name) container_spec_memory_limit_bytes > 0`. Validated
  `promtool check rules`.

**Что отложено в v0.1** (см. `docs/archive/future-ideas.md` → «OTP hardening
bundle»):
- **SA-H1** (verifyOtpByCode attempts counter) — архитектурное
  решение: need IP-resolve в сервисе или stricter Gateway RL.
  Gateway RL 5 req/min/IP держит single-IP атаки; distributed botnet
  — реальный vector, но не MVP-level threat (10 студентов pilot).
- **BH-H1** (bot dispatcher event_id дедуп) — это 50+ строк изменений
  в base infrastructure бота, задевает ВСЕ handler'ы. Регрессия
  риск. Должно идти через proper dispatcher-level test в v0.1.
- Все MEDIUM — в тот же future-ideas bundle.

**Альтернативы:**
- (a) Fix HIGH в G9 перед tag'ом — +1д на SA-H1 (need Gateway RL
  tune или IP counter) + +1д на BH-H1 (dispatcher refactor). Риск
  регрессии в стабильном code base.
- (b) Tag alpha.10 с documented HIGHs в v0.1. Дыры не критичные для
  pilot deployment (малая нагрузка, trusted network), закрываются
  до GA v0.0.0.

**Решение:** (b). Tag `v0.0.0-alpha.10` валидный — M09 scope был
"prod release blockers", и HIGH findings это **iterative hardening**,
а не новые blockers. Обе HIGH документированы в future-ideas bundle
с fix plans.

**Последствия:**
- `CHANGELOG.md` G9 bullet упоминает 0 BLOCK + 2 HIGH deferred.
- `docs/archive/future-ideas.md` новый раздел «OTP hardening bundle (v0.1)»
  с детальным fix plan'ом.
- v0.0.0 GA checkpoint должен включать OTP hardening bundle в scope.
