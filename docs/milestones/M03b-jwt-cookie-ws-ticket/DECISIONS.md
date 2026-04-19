# M03b Decisions

Micro-ADR формата «решение + причина» для выборов, которые НЕ покрыты
OWNER-ANSWERS.md. Каждая запись — 5-10 строк, не больше.

Не дублировать сюда:
- Решения из OWNER-ANSWERS.md (на них ссылаются через Q-ID / P2-N/M).
- Общие архитектурные принципы (они в `docs/architecture.md` / CLAUDE.md).
- Детали реализации (они в коде + DECISIONS не для how, а для why).

Дублировать сюда:
- Выборы между равнозначными опциями.
- Отклонения от типового подхода с пояснением.
- Trade-off'ы, которые будут актуальны через полгода.

---

## 2026-04-20 — Cookie Path scope

**Выбрано:** `Path=/api/auth` (как в OWNER-ANSWERS 02-Q-frontend-security)
**Отвергнуто:** `/api/auth/refresh`, `/`
**Причина:** OWNER-ANSWERS 2026-04-18 уже зафиксировал `Path=/api/auth` —
cookie доступна на всех auth endpoints (login/refresh/logout). Узкий
`Path=/api/auth/refresh` был бы плюсом, но требует лишней настройки на
logout-endpoint (пришлось бы для clear-cookie тоже слать Path=/api/auth/refresh).
**Последствия:** cookie видна на любом `/api/auth/**` endpoint'е. Для
mutating endpoint'ов same-origin + SameSite=Strict закрывают CSRF-риск.

## 2026-04-20 — SameSite attribute

**Выбрано:** `SameSite=Strict` (как в OWNER-ANSWERS 02-Q-frontend-security)
**Отвергнуто:** `Lax`
**Причина:** OWNER-ANSWERS 2026-04-18 зафиксировал `SameSite=Strict`.
Мы 100% same-origin (`ruttrack.site` отдаёт и frontend, и `/api` через
общий nginx) — Strict не мешает UX. OAuth-интеграция не планируется до
v1.0. Strict полностью закрывает cross-origin CSRF, поэтому отдельный
CSRF-token не нужен.
**Последствия:** WebView (Telegram Mini App) должны отправлять cookie
на same-origin requests — это их default behavior. Если в будущем
появится OAuth cross-site redirect, переключаемся на Lax с добавлением
double-submit token (зафиксировать в M07 или v1.0).

## 2026-04-20 — CSRF mechanism

**Выбрано:** NONE — same-origin + `SameSite=Strict` достаточно
**Отвергнуто:** double-submit cookie, sync-token
**Причина:** OWNER-ANSWERS NEW-14 — «при same-origin это не нужно...
Решить при имплементации». `ruttrack.site` — единственный origin,
`SameSite=Strict` запрещает cookie на любой cross-site request. Доп.
double-submit token добавил бы ~100 LoC (filter + frontend interceptor)
без real security benefit.
**Последствия:** если в будущем фронтенд будет на другом домене (или
OAuth-callback потребует переход на Lax) — добавить double-submit
token. Пометить в Post-mortem M03b как «follow-up conditional».

## 2026-04-20 — WS-ticket storage key scheme

**Выбрано:** (c) комбинация `ws_ticket:<uuid>` (value JSON) +
`ws_ticket_user:<userId>` (Set<uuid>)
**Отвергнуто:** opaque-uuid только, composite-key с KEYS-scan
**Причина:** Группа 8 требует invalidate всех ticket'ов пользователя
при logout без `KEYS` (production-safe). Atomic consume через Lua-script
(GET+DEL на `ws_ticket:<uuid>` + SREM из `ws_ticket_user:<userId>` в одном
shot).
**Последствия:** два ключа на ticket; cleanup осиротевших членов set'а
при expiry ticket'ов требует либо отдельного TTL на set (refresh на каждый
issue), либо periodic sweep. Выберу TTL на set = max(ticket TTL) * 2 = 60s
с refresh при каждом новом ticket'е для userId.

## 2026-04-20 — /auth/refresh-body deprecation timeline

**Выбрано:** (b) оставить 1 milestone с `Deprecation: true` header,
удалить в M04 или M05
**Отвергнуто:** удалить в M03b (breaking), оставить навсегда
**Причина:** frontend получает переходный период — даже если PWA/web-panel
сразу мигрируют, integration-тесты и старые клиенты/админские скрипты
успеют заметить warning-header. Native mobile не запланирован до v1.0.
**Последствия:** в M03b оставляем старый `POST /auth/refresh` с body
{refreshToken} и header `Deprecation: Tue, 01 Jun 2026 00:00:00 GMT` +
`Sunset: ...`. Планирую удаление в M04 (или M05 если M04 занят
observability-первоочерёдностями).

## 2026-04-20 — Event `user.logged-out` отложен в M04

**Выбрано:** НЕ publish event в M03b, откладываем в M04 Observability
**Отвергнуто:** publish через новый модуль в auth-service (shared-outbox,
RabbitMQ direct, Redis pub/sub)
**Причина:** auth-service — stateless issuer без БД/outbox (по архитектуре
M01-M03a). Добавление publish-инфраструктуры только ради одного audit-event
требует 300+ LoC (outbox-module + Flyway migration + publisher job + consumer),
что раздувает scope M03b. M04 Observability уже включает structured logging +
OTel tracing, где audit-event можно сделать через single `log.info("auth.logout",
mdc={user_id, ip, ticket_count_revoked, session_duration})` — zero infra cost.
**Последствия:** до M04 audit-trail logout'а не виден в аналитике. Access log
Gateway (`POST /api/auth/logout 204`) + structured log в auth-service
(добавляется в этой группе) покрывают 80% use-case. В M04 добавим явный
event при переходе на Alertmanager/Tempo.

---

_Формат записи (после подтверждения):_

## YYYY-MM-DD — Короткое название решения

**Выбрано:** X
**Отвергнуто:** Y, Z
**Причина:** одна-две фразы.
**Последствия:** что теперь иначе в будущем (опционально).
