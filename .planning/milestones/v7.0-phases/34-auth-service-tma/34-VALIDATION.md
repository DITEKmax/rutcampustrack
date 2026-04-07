---
phase: 34
slug: auth-service-tma
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 34 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers |
| **Config file** | `services/auth-service/build.gradle.kts` |
| **Quick run command** | `./gradlew.bat :services:auth-service:test --tests "*Tma*"` |
| **Full suite command** | `./gradlew.bat :services:auth-service:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew.bat :services:auth-service:test --tests "*Tma*"`
- **After every plan wave:** Run `./gradlew.bat :services:auth-service:test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 34-01-01 | 01 | 1 | AUTH-01 | T-34-01 | HMAC-SHA256 validates initData integrity | integration | `./gradlew.bat :services:auth-service:test --tests "*TmaIntegrationTest*"` | ❌ W0 | ⬜ pending |
| 34-01-02 | 01 | 1 | AUTH-01 | T-34-02 | Tampered initData returns 401 | integration | `./gradlew.bat :services:auth-service:test --tests "*TmaIntegrationTest*"` | ❌ W0 | ⬜ pending |
| 34-01-03 | 01 | 1 | AUTH-02 | — | Body-based refresh returns new token pair | integration | `./gradlew.bat :services:auth-service:test --tests "*RefreshBody*"` | ❌ W0 | ⬜ pending |
| 34-01-04 | 01 | 1 | AUTH-01 | — | telegram_id lookup finds linked user | unit | `./gradlew.bat :services:auth-service:test --tests "*TmaServiceTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `TmaIntegrationTest.java` — stubs for AUTH-01 (initData validation + JWT issuance)
- [ ] `RefreshBodyIntegrationTest.java` — stubs for AUTH-02 (body-based refresh)
- [ ] `TmaServiceTest.java` — unit test stubs for HMAC validation logic

*Existing test infrastructure (AbstractIntegrationTest, Testcontainers PostgreSQL+Redis) covers all framework needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real Telegram initData validation | AUTH-01 | Requires Telegram bot token + real Mini App session | Generate initData from Telegram test environment, POST to /api/auth/tma |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
