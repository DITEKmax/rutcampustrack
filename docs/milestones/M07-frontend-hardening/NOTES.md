# M07 Notes

Живой файл. Отклонения, измерения, surprises, вопросы к владельцу,
технические долги.

---

## Вопросы к owner'у до старта — ОТВЕЧЕНЫ (2026-04-21)

1. **Brand og-image** (G2, QE4): ответ **(b) SVG-first → PNG через
   sharp/resvg**. SVG коммитится в git, PNG генерируется из него
   npm-скриптом. Если brandbook не найду или не содержит RGB — вернуться
   к owner'у с уточняющим вопросом.
2. **mini-app scope** (G3): **(a) не трогаем mini-app в M07**. Owner
   planned path: после M12 закрытия + стабилизация PWA в проде →
   mini-app мигрируется **copy+adapt** из PWA. G3 scope = только
   PWA + web-panel (2 frontend'а). Запись про план — в
   `docs/future-ideas.md` → "Mini-app unification: copy+adapt from PWA
   after M12".
3. **axe-core baseline** (G10): **(a) CRITICAL + SERIOUS = 0**.
   MODERATE/MINOR трекаем в `docs/a11y-checklist.md` как "a11y pass 2"
   (v0.1), не блокируем M07.
4. **Sparklines placeholder text** (G9): **(e) "Графики посещаемости
   появятся в следующем релизе"** + skeleton UI + info-badge.
5. **Schedule navigation bounds** (G6, P2-7A/4): **(d) bounds =
   активный семестр + info-screen** ("Семестр начнётся Y" /
   "Семестр закончился X, новый — Y"). Edge-case «каникулы без
   активного семестра» → отдельное D1-решение в DECISIONS.md.

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
