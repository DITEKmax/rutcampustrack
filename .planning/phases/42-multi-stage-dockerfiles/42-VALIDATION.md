---
phase: 42
slug: multi-stage-dockerfiles
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-07
---

# Phase 42 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | docker build smoke tests (manual commands, no test framework files) |
| **Config file** | none |
| **Quick run command** | `docker build -f services/auth-service/Dockerfile -t rct-auth-test .` |
| **Full suite command** | Run all 11 `docker build` commands |
| **Estimated runtime** | ~300 seconds (11 builds) |

---

## Sampling Rate

- **After every task commit:** Run `docker build` for the affected service, confirm exit code 0
- **After every plan wave:** Build all 11 images in sequence
- **Before `/gsd-verify-work`:** All 11 builds must succeed
- **Max feedback latency:** ~30 seconds per single build

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 42-01-01 | 01 | 1 | DOCK-01 | T-42-01 | Non-root runtime user, no dev tools | smoke | `docker build -f services/api-gateway/Dockerfile -t rct-gateway-test .` | ❌ W0 | ⬜ pending |
| 42-01-02 | 01 | 1 | DOCK-01 | T-42-01 | Non-root runtime user, no dev tools | smoke | `docker build -f services/auth-service/Dockerfile -t rct-auth-test .` | ❌ W0 | ⬜ pending |
| 42-01-03 | 01 | 1 | DOCK-01 | T-42-01 | Non-root runtime user, no dev tools | smoke | `docker build -f services/academic-service/academic-app/Dockerfile -t rct-academic-test .` | ❌ W0 | ⬜ pending |
| 42-01-04 | 01 | 1 | DOCK-01 | T-42-01 | Non-root runtime user, no dev tools | smoke | `docker build -f services/schedule-service/schedule-app/Dockerfile -t rct-schedule-test .` | ❌ W0 | ⬜ pending |
| 42-01-05 | 01 | 1 | DOCK-01 | T-42-01 | Non-root runtime user, no dev tools | smoke | `docker build -f services/attendance-service/attendance-app/Dockerfile -t rct-attendance-test .` | ❌ W0 | ⬜ pending |
| 42-02-01 | 02 | 1 | DOCK-02 | T-42-01 | Non-root runtime user, no dev tools | smoke | `docker build -f services/notification-service/notification-app/Dockerfile -t rct-notification-web-test .` | ❌ W0 | ⬜ pending |
| 42-02-02 | 02 | 1 | DOCK-03 | — | N/A | smoke | `docker build -t rct-bot-test services/notification-bot/ && docker run --rm rct-bot-test python -c "import grpc; print('ok')"` | ❌ W0 | ⬜ pending |
| 42-03-01 | 03 | 1 | DOCK-04 | T-42-02 | No source code in runtime image | smoke | `docker build -t rct-pwa-test frontends/pwa/` | ❌ W0 | ⬜ pending |
| 42-03-02 | 03 | 1 | DOCK-04 | T-42-02 | No source code in runtime image | smoke | `docker build -t rct-miniapp-test frontends/mini-app/` | ❌ W0 | ⬜ pending |
| 42-03-03 | 03 | 1 | DOCK-04 | T-42-02 | No source code in runtime image | smoke | `docker build -t rct-webpanel-test frontends/web-panel/` | ❌ W0 | ⬜ pending |
| 42-03-04 | 03 | 1 | DOCK-04 | — | N/A | smoke | `docker build -t rct-landing-test frontends/landing/` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Existing infrastructure covers all phase requirements. No test framework files to create.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Layered JAR structure in runtime image | DOCK-01 | Requires `docker run` inspection | `docker run --rm rct-auth-test ls /application/` — verify dependencies/, spring-boot-loader/, application/ dirs exist |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s per build
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
