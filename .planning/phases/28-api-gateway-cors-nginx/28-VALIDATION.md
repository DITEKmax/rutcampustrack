---
phase: 28
slug: api-gateway-cors-nginx
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 28 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (existing) |
| **Config file** | `services/api-gateway/src/test/` (existing) |
| **Quick run command** | `./gradlew :services:api-gateway:test` |
| **Full suite command** | `./gradlew :services:api-gateway:test` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :services:api-gateway:test`
- **After every plan wave:** Run `./gradlew :services:api-gateway:test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 28-01-01 | 01 | 1 | INFRA-01 | T-28-01 | OPTIONS bypass only on OPTIONS method | unit | `./gradlew :services:api-gateway:test` | ❌ W0 | ⬜ pending |
| 28-01-02 | 01 | 1 | INFRA-01 | T-28-02 | CORS headers present on preflight, no duplicates | unit | `./gradlew :services:api-gateway:test` | ❌ W0 | ⬜ pending |
| 28-02-01 | 02 | 1 | INFRA-03 | — | N/A | smoke/manual | `curl -I http://localhost:80` | manual | ⬜ pending |
| 28-02-02 | 02 | 1 | INFRA-03 | — | sw.js and index.html served with no-cache | smoke/manual | `curl -I http://localhost:80/sw.js` | manual | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `CorsFilterTest.java` in `services/api-gateway/src/test/java/ru/rutcampustrack/gateway/filter/` — covers INFRA-01 (OPTIONS passthrough, CORS headers present)
- [ ] Add OPTIONS test case to existing `JwtAuthenticationFilterTest.java` — verifies bypass works

*Existing infrastructure covers test framework requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| nginx serves index.html at localhost:80 | INFRA-03 | Requires running docker compose | `docker compose up pwa-nginx -d && curl -I http://localhost:80` |
| sw.js served with Cache-Control: no-cache | INFRA-03 | Requires running nginx container | `curl -I http://localhost:80/sw.js` — check Cache-Control header |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
