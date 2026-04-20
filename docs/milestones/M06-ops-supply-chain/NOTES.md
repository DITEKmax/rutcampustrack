# M06 Notes

Живой файл. Отклонения, измерения, surprises, вопросы, технические долги.

---

## 2026-04-21 — старт milestone'а

- M05 закрыт 2026-04-21, tag `v0.0.0-alpha.6`.
- Последний коммит: `e03e74b docs(m05): закрытие milestone`.
- Инфра поднята (rct-postgres-academic/schedule, rct-mongo-attendance, rct-redis, rct-rabbitmq — healthy).
- Scope М06 согласован — 9 групп по образцу M05 G1-G10.

### Уточнения scope vs 99-executive-summary

- **Alertmanager (P2-9/5) уже сделан** — нашёл `prom/alertmanager:v0.27.0` в `docker-compose.prod.yml` (M04 реализация). Из scope M06 убран.
- **Tempo 2.3.1 уже пиннутый** (M04) — не трогаем.
- **C0-9 `.env.prod.example` + C0-10 LE cert-name** — перекатились в **M09 Prod Release Blockers** (Фаза 0 hardening в 99-executive).
- **P2-9/9 JVM resource limits** — отложено в M07 или prod-deploy checklist (требует VPS smoke).
- **P2-9/3 nginx body-size, P1-3 rate-limit nginx, C0-6 CSP self-host** — **M07 Frontend Hardening**.

### M05 defer'ы (Группа 8, 5 пунктов)

Перенесены из M05 post-mortem. Все из audit findings (bug-hunter/security-auditor):

1. Redis Jackson `LaissezFaireSubTypeValidator` → `BasicPolymorphicTypeValidator` whitelist `ru.rutcampustrack.*` — security MEDIUM.
2. `isHeadman` gRPC rate-limit — security LOW (key-space DoS).
3. Redis cache hit/miss metrics через `@Aspect` — MINOR (deferred в M05 из-за namespace-TTL регрессии).
4. `GrpcClientMetricsInterceptor` Timer caching + `startNs` в `start()` — LOW (bug-hunter 5.1+5.3).
5. `/actuator/**` excluded from tracing sampling — M04 backlog.

---
