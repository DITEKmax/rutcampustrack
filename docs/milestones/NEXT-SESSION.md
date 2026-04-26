# M14 закрыт ✅ — `v0.0.0-alpha.16` tagged

**M14 «Post-Audit Fixes» завершён 2026-04-26.** 9 групп закрыто, 22
коммита pushed на `origin/dev`, tag `v0.0.0-alpha.16` создан и
запушен. CI зелёный.

**Следующий milestone — first VPS deploy v0.0.0** (не делается через
GSD workflow — это operator action на VPS через `docker-compose.prod.yml`
+ `.env.prod`). Подробности — `docs/runbooks/prod-deploy-checklist.md`.

После first deploy появятся real-user сигналы (Grafana, инциденты,
обратная связь) — тогда **M15 «Post-Deploy Cleanup»** при
необходимости (Pre-v0.1 sweep из `docs/future-ideas.md`).

## История milestone'ов (архив)

- M01-M08 ✅ (`v0.0.0-alpha.1..alpha.9`)
- M09-M12 ✅ 2026-04-24 (`alpha.10..alpha.13`)
- M13 Pre-Deploy Hardening ✅ 2026-04-25 (`v0.0.0-alpha.15`)
- **M14 Post-Audit Fixes ✅ 2026-04-26 (`v0.0.0-alpha.16`)**
- → first VPS deploy v0.0.0
- → M15 (TBD после real-user signal)

## M14 итоговая статистика

| Группа | Тема | Коммит |
|--------|------|--------|
| G1 | Legacy headers strict default (CSO CRIT-01) | `dc40929` |
| G2 | SHA-pin appleboy/ssh-action (CSO CRIT-02) | `a93859b` |
| G3 | PKCS#8 idempotent JWT keygen (CSO HIGH-05) | `7e69067` |
| G4 v2 | RequiredSecretsValidator (CSO HIGH-06) | `bf915ec` |
| G5 | aiohttp+aiogram bump (CSO HIGH-07) | `607af81` |
| G6 | SHA-pin 16 actions × 3 workflows (CSO HIGH-03/04 + MED-09) | `7fbd908` |
| G7 | G26 false-pass tests + headman bulk-mark skip | `f24f22f` + `11e6a13` |
| G8 | burstCapacity prod default + DRY (G26 F01-F03) | `c09b002` |
| G9 | UAT regression fixes (validator JUnit detection + compose env) | `d7e900f` + `a4b4cea` |

**Итого:** 22 коммита, 9 functional groups, 9 docs followups, 2
corrective patches. Все 4 аудита (CSO comprehensive + G26 test
+ G26 code-review + tech-debt deferred) обработаны.

## Полный план M14 (архив)

- `docs/milestones/M14-post-audit-fixes/PLAN.md` — план + триаж 4 аудитов
- `docs/milestones/M14-post-audit-fixes/CHECKLIST.md` — 9 групп с галочками
- `docs/milestones/M14-post-audit-fixes/NOTES.md` — pre-flight surprises,
  post-mortems, lessons (G4 v1 deferred → v2 success, G7 wrong-client tests,
  G8 env override pattern, G9 validator regression)

## Что делать в следующей сессии

1. **first VPS deploy v0.0.0** — operator action (не Claude/GSD).
2. После deploy: monitor Grafana 1-2 недели. Любые инциденты идут в
   issue tracker, потом группируются в M15.
3. M15 «Post-Deploy Cleanup» — если (a) накопится критическая масса
   real-user findings, либо (b) пора начинать pre-v0.1 sweep из
   `docs/future-ideas.md` (MED-08 audit log, MED-11 mTLS Alertmanager,
   etc.).

Roadmap: `docs/milestones/README.md`.
Aудиторские отчёты: `docs/milestones/M13-pre-deploy-hardening/G2{6,7}-*.md`.
