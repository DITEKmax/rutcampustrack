---
phase: 29
slug: pwa-scaffold-auth
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-06
---

# Phase 29 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | vitest (frontend) / JUnit 5 + Spring Boot Test (backend) |
| **Config file** | `frontends/pwa/vitest.config.ts` (Wave 0 creates) / `services/auth-service/auth-app/build.gradle.kts` |
| **Quick run command** | `cd frontends/pwa && npx vitest run --reporter=verbose` |
| **Full suite command** | `cd frontends/pwa && npx vitest run && cd ../../services/auth-service/auth-app && ../../gradlew.bat test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontends/pwa && npx vitest run --reporter=verbose`
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 29-01-01 | 01 | 1 | PWA-01 | T-29-01 | httpOnly cookie never exposed to JS | integration | `../../gradlew.bat :services:auth-service:auth-app:test` | ❌ W0 | ⬜ pending |
| 29-01-02 | 01 | 1 | PWA-01 | T-29-02 | SameSite=Strict prevents CSRF | integration | `../../gradlew.bat :services:auth-service:auth-app:test` | ❌ W0 | ⬜ pending |
| 29-02-01 | 02 | 1 | PWA-02 | — | N/A | unit | `cd frontends/pwa && npx vitest run` | ❌ W0 | ⬜ pending |
| 29-02-02 | 02 | 1 | PWA-03 | T-29-03 | Access token in memory only, not localStorage | unit | `cd frontends/pwa && npx vitest run` | ❌ W0 | ⬜ pending |
| 29-02-03 | 02 | 1 | PWA-04 | — | Silent refresh without user interaction | unit | `cd frontends/pwa && npx vitest run` | ❌ W0 | ⬜ pending |
| 29-02-04 | 02 | 1 | PWA-05 | — | A2HS prompt deferred correctly | unit | `cd frontends/pwa && npx vitest run` | ❌ W0 | ⬜ pending |
| 29-02-05 | 02 | 1 | PWA-06 | — | iOS onboarding shown once | unit | `cd frontends/pwa && npx vitest run` | ❌ W0 | ⬜ pending |
| 29-02-06 | 02 | 1 | PWA-07 | — | App shell loads offline | manual | — | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontends/pwa/vitest.config.ts` — vitest configuration
- [ ] `frontends/pwa/src/test/setup.ts` — test setup with jsdom
- [ ] `services/auth-service/auth-app/src/test/java/.../CookieAuthTest.java` — cookie auth integration tests

*Wave 0 is part of Plan 01 (backend) and Plan 02 (frontend) respectively.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| App shell loads offline from SW cache | PWA-07 | Requires browser with SW + network disconnect | 1. Install PWA 2. Toggle airplane mode 3. Open app — login page renders |
| A2HS banner displays on Android | PWA-05 | Requires Android Chrome with manifest heuristics | 1. Open in Android Chrome 2. Trigger engagement heuristic 3. Verify banner |
| iOS onboarding overlay | PWA-06 | Requires iOS Safari standalone detection | 1. Open in iOS Safari 2. Verify overlay appears 3. Dismiss 4. Reload — no overlay |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
