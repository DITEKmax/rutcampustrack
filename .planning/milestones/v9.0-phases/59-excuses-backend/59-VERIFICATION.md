# Phase 59: Excuse Tickets Backend — Verification Plan

**Created:** 2026-04-14
**Type:** seed for `/gsd-verifier` after execution

---

## Goal-backward checks

| AC | Что проверяем | Как |
|---|---|---|
| AC-1 | Создание тикета | curl POST `/excuses` от STUDENT → 201 + ExcuseTicketResponse с status=pending |
| AC-2 | Дубликат lessonId | Создать второй тикет на тот же lessonId → 409 «Тикет на этот урок уже существует» |
| AC-3 | Староста не может создать | curl POST от STUDENT с is_headman=true → 409 «Староста проставляет через журнал» |
| AC-4 | Чужой тикет невидим | curl GET `/excuses/{id}` (не свой) → 403 |
| AC-5 | Approve каскадирует | Approve тикет на 3 lessonIds → проверить, что AttendanceRecord для всех 3 урокаов создан/обновлён со статусом excused |
| AC-6 | Староста ≠ свой судья | Одобрить свой тикет от старосты → 409 |
| AC-7 | RabbitMQ event valid | Создать тикет → проверить, что в RabbitMQ exchange лежит сообщение типа `excuse.requested`; парсить JSON в Python и сравнить с `notification-bot/tests/fixtures/excuse_requested.json` |
| AC-8 | bot consumer для excuse.decided | pytest `test_excuse_decided.py` зелёный |
| AC-9 | Frontend student | UAT: открыть `/student/excuses`, создать тикет с причиной → увидеть в списке свой тикет |
| AC-10 | Frontend headman | UAT: войти как headman, увидеть pending тикет, одобрить → проверить, что статус сменился и студенту пришло уведомление |
| AC-11 | gRPC LessonsByIds | curl/grpcurl к schedule-service → возвращает корректные lesson info |
| AC-12 | Все тесты зелёные | `./gradlew :services:attendance-service:attendance-app:test` + `cd frontends/web-panel && npm test` + `pytest services/notification-bot/tests` |

## Регрессионные проверки

- [ ] Существующие attendance тесты — без регрессий.
- [ ] schedule-service тесты после расширения proto — зелёные.
- [ ] notification-bot интеграционные тесты — зелёные (включая существующий headman_alerts test).
- [ ] Frontend admin-страницы (не тронутые) — без регрессий.
- [ ] Web-panel `npm run build` — чисто.

## Smoke tests на проде

После выкатки:
1. STUDENT создаёт тикет на завтрашний урок — успех; староста получает push в Telegram.
2. STUDENT пробует создать второй тикет на тот же урок — получает понятное «уже создан».
3. Староста (другой пользователь) видит тикет на `/headman/excuses`, отклоняет с комментарием «нет подтверждения».
4. STUDENT видит у тикета статус rejected и комментарий старосты; ему пришло уведомление в Telegram.
5. STUDENT создаёт тикет на 3 будущих урока с причиной «Командировка» (excuseType=OTHER + comment).
6. Староста одобряет — все 3 урока в журнале посещаемости автоматически становятся «уважительные».

## Безопасность

- [ ] STUDENT не видит чужие тикеты (даже через прямой ID в URL).
- [ ] Headman не видит тикеты других групп.
- [ ] gRPC LessonsByIds защищён существующим GRPC_SECRET (см. existing rpcs).
- [ ] RabbitMQ event не содержит чувствительных данных (пароль, токены, telegram chat_id и пр.).
- [ ] mongo-injection не возможен (используем Spring Data, не build dynamic strings).

## Контракт-тесты с ботом

- [ ] **Обязательно**: интеграционный тест в attendance-service публикует event и асинхронно проверяет, что бот его принял и распарсил без ошибок (можно через тестконтейнерный RabbitMQ + отдельный consumer-stub в тесте).
- [ ] Альтернатива: статический контракт-тест на JSON-схему (jsonschema) с фикстурой, синхронизированной между attendance-service и notification-bot.
