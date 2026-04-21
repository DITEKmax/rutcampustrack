# M09 Notes

Живой файл. Пиши сюда:
- **Отклонения от плана:** «решил сделать X вместо Y, потому что...»
- **Измерения:** «OTP flow p95: HTTP body 45ms → RabbitMQ event 180ms»
- **Surprises:** «обнаружил что cleanupOrphans ещё вызывается из IndexInitializer»
- **Вопросы к владельцу:** «нужна ли retry policy на OTP consumer при bot down?»
- **Технические долги:** «оставил TODO в X — закрою в M10 / v0.1»

Не пиши:
- Общие описания модулей (это в PLAN.md).
- WHY-обоснования (это в OWNER-ANSWERS.md Q-P0-4/5, Q-P0-1/2 в 14, Q-P0-2 в 12).
- Пошаговые инструкции (это в CHECKLIST.md).

---

## Открытые вопросы (решить до старта Группы 2)

1. **OTP consumer failure handling** — если `notification-bot` down
   дольше TTL (5 мин) когда приходит `otp.requested`, что делать?
   - Вариант A: DLQ + alert в Alertmanager → админ видит что student
     не получил код, может руками переотправить.
   - Вариант B: bot на `ApplicationReadyEvent` читает backlog, но коды
     уже просрочены → игнорирует.
   - Вариант C: auth-service при retry от клиента генерит новый код
     (старый в Redis перезаписывается через `SET EX=300`). Самый
     простой, self-healing, но каждый retry = новое событие в Rabbit.
   - **Рекомендую C** (consistency с текущей логикой `POST
     /auth/otp/request` — идемпотентность по `telegram_id`).

2. **`.env.prod` TELEGRAM_BOT_USERNAME** — использовать существующий
   `BOT_TOKEN` для авто-discovery через `getMe()` API, или явная
   переменная? Явная переменная проще и независима от Telegram API.

3. **Coverage-gate для latecheckin / handlers** — 70% как в
   OWNER-ANSWERS, или можно 80% для нового кода (pilot для M08
   `diff-coverage ≥ 80%`)? Если 80% — estimate растёт ~0.5д.

## Отложено в v0.1 (не делаем в M09)

- **01 P0-1 `auth-api-contract`** — структурный refactor (вынести
  `AuthController` + все DTO в отдельный Gradle-модуль). Не блокер
  прода. Документируется в `docs/future-ideas.md`.
- **NEW-54 CSP-Report endpoint** — `report-uri` в CSP web-panel
  (owner явно указал «v0.1»).
- **Magic-link для первого входа** (01-Q1 accepted tradeoff).

## YYYY-MM-DD
