# M07 Decisions (Micro-ADR)

Фиксируй каждое решение которое не описано в OWNER-ANSWERS, но нужно
для реализации. Формат: `## YYYY-MM-DD — D{N}: короткий заголовок`,
дальше 3-10 строк: что выбрано, почему, альтернативы.

---

_Открытых развилок на старт M07 нет — scope зафиксирован в
OWNER-ANSWERS QC1-7 (строки 1809-2053), QE3/4 (2430-2510), P2-7A/1..8
(5215-5365), P2-7B/1..4 (5366-5550). Если появится micro-решение —
записать как `## YYYY-MM-DD — D{N}: заголовок`._

Ожидаемые точки разветвления (предварительно):
- CSP self-host strategy — bundle vs separate `@font-face` vs inline
  base64 (Группа 1).
- openapi-typescript code-gen output structure — single-file vs
  per-service directory (Группа 3).
- axe-core severity threshold (Группа 10).
- Lazy-loading boundary для web-panel role-split (Группа 8).

---

## 2026-04-21 — D1: Owner-answers по 5 стартовым вопросам

Обсуждалось в первом сообщении сессии 2026-04-21 (см. NOTES.md).

**Что выбрано:**

1. **Brand og-image (G2):** SVG-first → PNG через `sharp`/`@resvg/resvg-js`.
   SVG коммитится в git как source-of-truth, PNG генерируется
   npm-скриптом. Альтернативы: (a) Pillow/sharp-скрипт с рендером
   с нуля — отвергнут (overkill), (c) defer в v0.1 — отвергнут
   (маркетинг-значимо для public-alpha).

2. **mini-app scope G3:** полностью out-of-scope M07. Миграция
   произойдёт copy+adapt из PWA **после M12** + стабилизации PWA.
   Альтернативы: полная migration — дублирование работы; generate-only
   — не value, формально закрывает ticket но не удаляет interface-копии.
   Записано в `docs/future-ideas.md` → "Mini-app unification: copy+adapt
   from PWA after M12".

3. **axe-core baseline (G10):** `CRITICAL + SERIOUS = 0`.
   MODERATE/MINOR tracked в `docs/a11y-checklist.md` как "a11y pass 2"
   для v0.1. Альтернативы: +MODERATE — раздувает M07 до 12-14д,
   толкает M08/M09; +MINOR — false-positive heavy (Material Design
   часто неправ по axe AAA).

4. **Sparklines placeholder text (G9):** «Графики посещаемости появятся
   в следующем релизе» + skeleton UI + info-badge. Альтернативы:
   "доступно в v0.1" (внутренняя терминология), "аналитика скоро"
   (generic), без текста (риск "кажется баг, напишу в поддержку").

5. **Schedule navigation bounds (G6, P2-7A/4):** bounds = активный
   семестр. За границей показывать info-screen («Семестр закончился
   YYYY-MM-DD, новый начнётся YYYY-MM-DD» или «Семестр начнётся
   YYYY-MM-DD»), не blank + disabled button. Edge-case «каникулы без
   активного семестра» — пока без активного семестра, показать
   «Сейчас каникулы. Ближайший семестр начнётся Y.» + (опционально)
   кнопку «Посмотреть прошлый семестр» как отдельное navigation action,
   не часть prev/next. Альтернативы: disabled-кнопки — UX ambiguity;
   auto-cross-semester navigation — требует backend endpoint
   `/semesters/adjacent` (overkill для prev/next).
