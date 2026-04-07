---
phase: 43
slug: docker-compose-prod-yml
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 43 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | docker compose smoke test (manual CLI, no framework files) |
| **Config file** | none |
| **Quick run command** | `docker compose -f docker-compose.prod.yml --env-file .env.prod config` |
| **Full suite command** | `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d && docker compose -f docker-compose.prod.yml ps` |
| **Estimated runtime** | ~5 seconds (config), ~120 seconds (full up) |

---

## Sampling Rate

- **After every task commit:** Run `docker compose -f docker-compose.prod.yml --env-file .env.prod config`
- **After every plan wave:** Run `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d && docker compose ps`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 5 seconds (config validation)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 43-01-01 | 01 | 1 | DOCK-07 | T-43-04 | .env.prod gitignored before creation | smoke | `git check-ignore -v .env.prod` | N/A | ⬜ pending |
| 43-01-02 | 01 | 1 | DOCK-05 | — | Missing prod profiles created | smoke | `test -f services/api-gateway/src/main/resources/application-prod.yml` | N/A | ⬜ pending |
| 43-01-03 | 01 | 1 | DOCK-05, DOCK-06, DOCK-07, MON-03 | T-43-01,02,03 | Compose file valid with all services | smoke | `docker compose -f docker-compose.prod.yml --env-file .env.prod config` | N/A | ⬜ pending |
| 43-01-04 | 01 | 1 | DOCK-06 | T-43-01 | No DB ports exposed to host | smoke | `docker compose -f docker-compose.prod.yml config \| grep -c "published"` — only api-gateway:80 | N/A | ⬜ pending |
| 43-01-05 | 01 | 1 | MON-03 | — | All backends have healthchecks | smoke | `docker compose -f docker-compose.prod.yml config \| grep -c "healthcheck"` — 7+ entries | N/A | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements. No test framework files needed — validation uses pure CLI commands.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| All services reach healthy state | DOCK-05, MON-03 | Requires running containers with real DB init | Run `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d`, wait 120s, check `docker compose ps` |
| JWT key volume shared correctly | DOCK-05 | Requires auth-service to generate keys on first start | Check `docker compose exec notification-web ls /keys/public.key` returns 0 |

*If none: "All phase behaviors have automated verification."*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 5s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
