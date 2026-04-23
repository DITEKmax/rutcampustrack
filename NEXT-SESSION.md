# Next Session Pointer

**Активный milestone:** M09 Prod Release Blockers (⏳ G1-G8 ✅, осталась G9 Audit).

**В новом терминале напиши:**

```
Прочитай docs/milestones/NEXT-SESSION.md и продолжай
```

Или короче:

```
Продолжай M09 G9
```

## Быстрые ссылки

- `docs/milestones/NEXT-SESSION.md` — полный промпт + hand-off для G9.
- `docs/milestones/M09-prod-release-blockers/PLAN.md` — scope + acceptance.
- `docs/milestones/M09-prod-release-blockers/CHECKLIST.md` — 9 групп (G1-G8 ✅).
- `docs/milestones/M09-prod-release-blockers/NOTES.md` — post-mortem по группам.
- `docs/milestones/M09-prod-release-blockers/DECISIONS.md` — D1-D6.

## Состояние (2026-04-24)

Закрыто в последних сессиях (~12 коммитов M09):

| Группа | Commit | Итог |
|--------|--------|------|
| G1 Quick wins | `2996652..0c465f1` | 3 P0 fix (MessageDigest + cleanupOrphans + landing) |
| G2 OTP event | `3d6dfd1..bda6a35` | 204 + `otp.requested` + bot consumer + AuthOtpFlowIT |
| G3 latecheckin | `48a63f7` | 24 tests, jacoco 70% latecheckin gate |
| G4 bot callbacks | `25da2d9` | 25 tests, handlers coverage 92.83% |
| G5 lesson.cancelled | `b5a7e2e` | V13 migration + full snapshot + architecture.md |
| G6 headman role | `e332d41` | `_verify_headman` + NEW-121 audit |
| G7 prod-deploy | `c5bf621` | 4 docs + compose mem_limits + Prom alert |
| G8 docs cleanup | `4fa58a4` | admin-scripts + future-ideas + CLAUDE/CHANGELOG |

**Следующее — G9 Audit (~0.5д)**: полный `./gradlew build` + pytest,
параллельные `security-auditor` и `bug-hunter` агенты на diff M09
(25 commits `2996652..4fa58a4`), hot-patches если найдутся, post-mortem
в PLAN.md, tag `v0.0.0-alpha.10` локально, hand-off в M10.

Push на origin отложен до конца v0.0.0 (17 коммитов ahead, станет 18
после G9 + 9 tags после G9 tag).
