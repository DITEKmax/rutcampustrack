# Next Session Pointer

**Активный milestone:** M07 Frontend Hardening (⏳ в работе, 7/12 групп закрыто).

**В новом терминале напиши:**

```
Прочитай docs/milestones/NEXT-SESSION.md и продолжай
```

Или короче:

```
Продолжай M07
```

## Быстрые ссылки

- `docs/milestones/NEXT-SESSION.md` — полный промпт + hand-off.
- `docs/milestones/M07-frontend-hardening/PLAN.md` — scope + acceptance.
- `docs/milestones/M07-frontend-hardening/CHECKLIST.md` — 12 групп (G1-G5, G7, G11 ✅).
- `docs/milestones/M07-frontend-hardening/NOTES.md` — G3b discovery + metrics baseline.
- `docs/milestones/M07-frontend-hardening/DECISIONS.md` — D1-D6.

## Состояние (2026-04-22)

Закрыто в сессии 2026-04-22 (5 коммитов):
- **G3b** `b5e66f6` — PWA + web-panel types-only миграция на generated
  OpenAPI. 23 файла мигрированы, 566 тестов зелёных.
- **G4** `9f628aa` — RFC 7807 error interceptor (adapter `fieldErrors
  → invalidParams`), traceId copy button, suppress flags.
- **G5** `9120544` — unified NotificationCenter + exponential backoff,
  StudentStomp/HeadmanStomp → thin adapter'ы.
- **G7** `bfa780f` — Material ConfirmWithReasonDialog + replace
  `window.prompt` в headman-lessons.
- **G11** `65640f4` — nginx `client_max_body_size` 2m global + 25m
  per-location excuse, `.github/pull_request_template.md`,
  `docs/nginx-config.md`, `docs/contributing.md`.

**Следующее — G6 (UX P2-7A/1..8, ~2д)**: самый крупный remaining
scope. Подзадачи: PullToRefresh, useSwipeHandler, useDateNavigation,
schedule bounds, scroll preservation, forkJoin fix, DrawerMenu,
geolocation UX. После — G8 (lazy-loading), G9 (StatsPage aggregate +
sparklines), G10 (a11y axe-core), финал G12 (audit + tag alpha.8).

Push на origin отложен до конца v0.0.0 (80+ коммитов ahead).
