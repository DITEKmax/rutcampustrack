# Phase 58: Admin Functionality — BUG-006 Fixes — Verification Plan

**Created:** 2026-04-14
**Type:** seed for `/gsd-verifier` after execution

---

## Goal-backward checks

Каждый Acceptance Criterion из RESEARCH.md (AC-1..AC-10) должен быть подтверждён конкретной проверкой:

| AC | Что проверяем | Как |
|---|---|---|
| AC-1 | 409 + ProblemDetail при дубле | Bash: создать пользователя с уже существующим telegramId → ожидать 409, в теле `field=telegramId` |
| AC-2 | ILIKE-search возвращает релевантных | curl `GET /api/academic/users?search=иван` → проверить, что `login` или ФИО содержат «иван» (case-insensitive) |
| AC-3 | Разные сообщения в frontend по 409 | UAT: создать пользователя с дубликатами login/email/telegram/employee — проверить 4 разных сообщения в диалоге |
| AC-4 | Telegram required для STUDENT | curl POST с role=student без telegramId → 400 «Telegram ID обязателен» |
| AC-5 | Колонка init password в таблице | UAT: создать пользователя — увидеть пароль в колонке. Сменить пароль — колонка для строки пуста |
| AC-6 | Удаление поля code из контракта/UI | grep `\.code\|"code"\|getCode` в `services/academic-service/src/main/`, `services/academic-service/academic-api-contract/`, `frontends/web-panel/src/app/features/admin` → 0 результатов |
| AC-7 | Manual promote endpoint | curl POST `/groups/promote` от ADMIN → 200 с summary; от не-ADMIN → 403 |
| AC-8 | Scheduled promotion корректно меняет курс | Unit-тест с заглушкой `Clock` — за 14 дней до 2026-09-01 запустить promote, ожидать `ИВТ11-001`→`ИВТ21-001` |
| AC-9 | Семестры валидация | Bash три POST'а: прошлая дата → 400, пересекающиеся даты → 409, edit завершённого → 409 |
| AC-10 | Миграции проходят чисто | `./gradlew.bat :services:academic-service:academic-app:flywayMigrate` против чистой БД и против тест-сидов; статус «Successfully applied 3 migrations» |

## Регрессионные проверки

- [ ] Все существующие тесты `:services:academic-service:academic-app:test` — зелёные (требуется Docker).
- [ ] Все vitest для admin/* — зелёные.
- [ ] Web-panel `npm run build` — без новых TS-ошибок.
- [ ] Существующие admin-страницы (groups, users, semesters) визуально не сломаны (UAT по 5 ключевым flow).
- [ ] gRPC контракты (`academic.proto`) не задеты — поле avatar_id Фазы B остаётся.
- [ ] Auth-service тесты — зелёные (баseline V1 в test/resources синхронизирован).

## Smoke tests на проде

После выкатки (CI/CD):
1. ADMIN заходит, ищет «test» — видит только тестовых пользователей.
2. ADMIN создаёт студента без Telegram ID — получает понятную ошибку.
3. ADMIN создаёт студента с дублирующим Telegram ID — получает «уже используется» (не «Не удалось сохранить»).
4. ADMIN видит начальный пароль в таблице.
5. ADMIN создаёт группу `ИВТ11-001` — успех; пытается создать `ИВТ11-001` повторно — 409.
6. ADMIN создаёт семестр на 2025-01-01..2025-06-30 — 400 (прошлое).
7. ADMIN создаёт семестр на 2026-09-01..2027-01-31 — успех.
8. ADMIN создаёт второй семестр на 2026-12-01..2027-03-31 — 409 (пересечение).
9. ADMIN дёргает `POST /groups/promote` — получает summary (в тестовой среде с заглушкой даты или просто увидеть «too early»).

## Безопасность

- [ ] Все новые эндпоинты помечены `@RequireRole({ADMIN})`.
- [ ] DataIntegrityViolationException не утекает SQL-детали (имя constraint раскрывается в `field`, но не значение).
- [ ] Поиск не позволяет SQL-инъекции (Specification API параметризует автоматически).
- [ ] Init password в API-ответе не попадает в логи (logback паттерн уже маскирует — проверить).
