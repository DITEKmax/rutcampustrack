---
phase: 22
slug: bot-infrastructure-layer
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-05
---

# Phase 22 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | pytest 8.x + pytest-asyncio 1.1.0 |
| **Config file** | `services/notification-bot/pytest.ini` (Wave 0 creates) |
| **Quick run command** | `cd services/notification-bot && python -m pytest tests/ -x -q` |
| **Full suite command** | `cd services/notification-bot && python -m pytest tests/ -v --tb=short` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd services/notification-bot && python -m pytest tests/ -x -q`
- **After every plan wave:** Run `cd services/notification-bot && python -m pytest tests/ -v --tb=short`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 15 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 22-01-01 | 01 | 1 | BINFRA-01 | T-22-01 / — | Watchdog reconnects after RabbitMQ restart within 60s | integration | `pytest tests/test_event_consumer.py -k watchdog` | ❌ W0 | ⬜ pending |
| 22-01-02 | 01 | 1 | BINFRA-01 | — | prefetch_count=10 set on channel | unit | `pytest tests/test_event_consumer.py -k prefetch` | ❌ W0 | ⬜ pending |
| 22-02-01 | 02 | 1 | BINFRA-02 | — | gRPC async client returns telegram_ids without blocking | unit | `pytest tests/test_grpc_client.py -k group_members` | ❌ W0 | ⬜ pending |
| 22-02-02 | 02 | 1 | BINFRA-02 | — | In-memory cache with 5min TTL for GetGroupMembers | unit | `pytest tests/test_grpc_client.py -k cache` | ❌ W0 | ⬜ pending |
| 22-03-01 | 03 | 1 | BINFRA-03 | — | Redis RPUSH/LRANGE for reminder message_ids | unit | `pytest tests/test_redis_client.py -k reminder` | ❌ W0 | ⬜ pending |
| 22-03-02 | 03 | 1 | BINFRA-03 | — | Redis connection graceful degradation | unit | `pytest tests/test_redis_client.py -k degradation` | ❌ W0 | ⬜ pending |
| 22-04-01 | 04 | 2 | BINFRA-03 | — | Token bucket rate limits to 30 msg/sec | unit | `pytest tests/test_send_queue.py -k rate_limit` | ❌ W0 | ⬜ pending |
| 22-04-02 | 04 | 2 | BINFRA-03 | — | 429 Retry-After handling | unit | `pytest tests/test_send_queue.py -k retry_after` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `services/notification-bot/pytest.ini` — asyncio_mode = auto
- [ ] `services/notification-bot/tests/conftest.py` — shared fixtures (event_loop, mock Redis, mock gRPC)
- [ ] `services/notification-bot/requirements-dev.txt` — pytest, pytest-asyncio, fakeredis[aioredis]

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| RabbitMQ restart recovery within 60s | BINFRA-01 | Requires actual RabbitMQ container restart | `docker restart rutcampustrack-rabbitmq-1`, observe bot logs for reconnection within 60s |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
