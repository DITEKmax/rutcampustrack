<!--
Pull-request template (NEW-74, M07 G11).

Prefill чеклист ниже — ревьюер ожидает увидеть осознанные ответы. Если
PR маленький (<20 LOC, comment-only, typo-fix) — можно оставить только
Summary и удалить остальное.
-->

## Summary

<!-- 1–3 строки: что и зачем. Ссылки на issue/ADR/phase-report. -->

## Scope

- [ ] Code change (backend / frontend / infra)
- [ ] Documentation only
- [ ] Config only (Gradle / docker-compose / nginx / CI)
- [ ] Revert

## Areas touched

<!-- Помечай все, что применимо. Ревьюер использует labels для routing'а. -->

- [ ] `services/shared/**` — shared-модули (M01+). Трогать осторожно,
      ломает все сервисы.
- [ ] Backend contract (`*-api-contract/`) — public API, проверь
      openapi-drift CI (`docs/openapi/*.json` + generated types).
- [ ] Flyway migration — **НИКОГДА не редактировать применённые** `V{N}`,
      добавлять `V{N+1}` патчем.
- [ ] `frontends/landing/` — **label `landing-review`** (CSP self-host
      инвариант, design tokens). См. `docs/contributing.md`.
- [ ] `docs/**` — **label `docs-review`** для milestone artifacts
      (PLAN/CHECKLIST/NOTES/DECISIONS) и для CLAUDE.md.
- [ ] `nginx/` — reload-test обязателен (`docs/nginx-config.md`).
- [ ] `.github/workflows/` — действует сразу при merge в dev; проверь
      что ни один branch не крэшится CI.

## Verification

<!-- Приложи команды, которые реально гонял локально. -->

- [ ] `./gradlew :services:<svc>:check` — зелёный.
- [ ] `npm test` в затронутых frontend'ах — зелёный.
- [ ] `docker compose up -d` + smoke-test (describe).
- [ ] Нет новых CSP violations в DevTools Console.
- [ ] OpenAPI drift — generated types и `docs/openapi/*.json` в синке
      (M07 G3a/b: `npm run generate:types:offline` + `git diff --exit-code`).

## Risk / rollback

<!--
- Что сломается, если мердж в прод улетит мимо rollback'а?
- Есть ли feature-flag / migration идемпотентность / offline undo?
-->

## Screenshots / traces (optional)

<!-- Для UI-изменений — скриншоты light+dark. Для observability —
ссылка на Grafana dashboard или trace-id. -->

---

<!--
Reviewers:
- Code: 1+ owner или claude-code-guide (если менял intel).
- Если label `landing-review` → дизайнер/product.
- Если label `docs-review` → maintainer docs-хаба.
- Security-sensitive (auth, crypto, rate-limit) → @maksd + security-auditor agent.
-->
