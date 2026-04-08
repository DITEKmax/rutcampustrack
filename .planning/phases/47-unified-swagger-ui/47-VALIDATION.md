---
phase: 47
slug: unified-swagger-ui
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-08
---

# Phase 47 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (existing) |
| **Config file** | `services/api-gateway/build.gradle.kts` |
| **Quick run command** | `./gradlew.bat :services:api-gateway:test` |
| **Full suite command** | `./gradlew.bat test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat :services:api-gateway:test`
- **After every plan wave:** Run `./gradlew.bat test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 47-01-01 | 01 | 1 | DOC-01 | — | N/A | integration | `curl -s http://localhost:8080/swagger-ui.html` | ❌ W0 | ⬜ pending |
| 47-01-02 | 01 | 1 | DOC-02 | — | N/A | integration | `curl -s http://localhost:8080/openapi/auth-service` | ❌ W0 | ⬜ pending |
| 47-01-03 | 01 | 1 | DOC-03 | — | N/A | config | `grep -r "2.8.6" services/*/build.gradle.kts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements. No new test frameworks needed.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Swagger UI renders all 4 service specs in dropdown | DOC-01 | Browser UI interaction | Navigate to Gateway /swagger-ui.html, verify dropdown shows auth, academic, schedule, attendance |
| "Try it out" executes requests through Gateway | DOC-02 | Requires running services + valid JWT | Open Swagger UI, select an endpoint, click Try It Out, verify response |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
