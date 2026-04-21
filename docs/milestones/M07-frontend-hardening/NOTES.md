# M07 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу,
технические долги.

---

## Вопросы к owner'у до старта

1. **Brand og-image** (Группа 2, QE4): нужен `1200×630` PNG с логотипом
   + слоганом + превью скриншота. У нас есть brand-гайд
   (`docs/Rutcampustrack brandbook.md`)? Или дизайн с нуля?
2. **mini-app scope** (Группа 3): мигрируем ли mini-app полностью на
   openapi-ts, или только critical-path (auth + schedule)? Mini-app
   ограничен Telegram viewport, сокращённый API surface.
3. **axe-core baseline** (Группа 10): WCAG 2.1 AA — `CRITICAL + SERIOUS`
   ноль, или включаем `MODERATE + MINOR` тоже? Первое реалистичнее на
   10-12д, второе может занять +3-5д.
4. **Real sparklines backend** (Группа 9): `admin-dashboard` требует
   time-series агрегатов (по дням/неделям attendance-rate). Для
   real-sparklines нужен новый endpoint `/attendance/stats/timeseries`.
   Это выходит за scope M07 → можно отложить в v0.1, оставить псевдо
   с "(coming in v0.1)" label?
5. **Schedule navigation bounds** (Группа 6, P2-7A/4): bounds =
   семестр? Как вести себя при переходе семестра в середине недели —
   показать «нет данных» или next semester week?

## Ожидаемые surprises

- **CSP self-host может сломать GSAP intellisense** — bundled vs CDN
  версии могут отличаться по API. Проверить что hero-анимация работает
  после self-host.
- **openapi-ts может конфликтовать с Angular TypeScript strict mode** —
  generated types используют `readonly` / `unknown` агрессивно, может
  потребоваться `strict: false` override в generated module.
- **Lazy-loading в web-panel уже частично сделан в v9.0** — проверить
  что именно лениво, что нет (Phase 50+).
- **P2-7A/6 forkJoin fix для subject lookup** — может требовать backend
  `/subjects?ids=[...]` batch endpoint (QC6-related). Если endpoint'а
  нет — создать в backend первыми.

## Deferred до M07 (из M06 post-mortem)

- **nginx `client_max_body_size` per-location** — в Группу 11 M07.
- **13 P1-3 nginx rate-limit** — переоценить после M03a Redis
  rate-limiter. Если Spring Cloud Gateway достаточный — skip.
- **C0-6 CSP self-host landing** — в Группу 1 M07 (основной фокус).
- **nginx/postgres/mongo/redis/rabbitmq digest-pin** — defer в M08,
  это supply-chain, не frontend.
- **/actuator/** excluded from tracing** — v0.1 (требует custom OTel
  Sampler, integration tests).

---

## Metrics baseline (до M07)

Соберём после старта для сравнения в post-mortem:

- PWA bundle size: [TBD]
- web-panel initial chunk: [TBD]
- axe-core findings: [TBD CRITICAL, TBD SERIOUS]
- Landing Lighthouse Performance: [TBD]
- Landing Lighthouse A11y: [TBD]

---
