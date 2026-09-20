# JS-STUDENT-01 backend fix packet — FIX-r2

## Goal

Устранить два исходных HIGH finding в назначенной backend-области:

- F4: canonical student gRPC check-in должен серверно отклонять уроки,
  заблокированные старостой, включая вариант `is_geo_blocked=false` и
  `is_blocked_by_headman=true`.
- F5: legacy `POST /attendance/checkin` должен быть fail-closed на самом
  attendance-service с явным HTTP 410; путь не должен писать attendance,
  cooldown/dedup, receipt, request или outbox.

## Context / evidence

- Baseline: `cedce8c60aee04261ca87a18b898b148719bee89`.
- Original F4 evidence: `AttendanceStudentGrpcServiceImpl` переносит в
  `StudentCheckinModels.Lesson` только `lesson.getIsGeoBlocked()`; snapshot
  проверяет обе блокировки, а command-path — только domain `geoBlocked`.
- Original F5 evidence: legacy `CheckinService.checkin` выполняет rate limit,
  reads, dedup, Mongo upsert и event publish; при существующем `HEADMAN/ABSENT`
  документе без paired-coordination может заменить его на `PRESENT/STUDENT_GEO`.
- Existing canonical path: `StudentCheckinService` owns transaction, pair
  coordination, cooldown, idempotent receipt, late-request and outbox behavior.

## Relevant scope

Attendance backend and targeted tests only. Gateway changes are out of scope
unless an endpoint test proves service-level retirement is insufficient.

## Required behavior

1. F4 maps effective geo block as `is_geo_blocked || is_blocked_by_headman`
   without changing public DTO/proto contract. Both coordinates and unavailable
   geo inputs fail before any write. Existing canonical happy, retry,
   manual-absence and race invariants remain intact.
2. F5 rejects every legacy `POST /attendance/checkin` request with HTTP 410 at
   attendance-service before invoking mutable legacy service logic. The seeded
   `HEADMAN/ABSENT` record and all side-effect stores/events remain unchanged.
3. Keep auth boundary and current canonical endpoint behavior unchanged.

## Constraints / do not

- Sole writer in this worktree; preserve root, integration, FE, and unrelated
  work. Do not edit gateway, shared contracts, generated files, configs,
  lockfiles, Figma, auth, or mobile UI without evidence and coordination.
- Do not redesign the canonical API or retain a duplicated mutable legacy path.
- Do not treat WARN/ERROR as a defect without request linkage and reproduction.
- No production deploy, migration, data deletion, reset, or push.

## Acceptance criteria

- F4 regression test proves both block flag variants fail with
  `CHECKIN_NOT_ELIGIBLE`/mapped precondition and no attendance, pair, receipt,
  late-request, cooldown or outbox mutation.
- F5 endpoint test proves HTTP 410 and no legacy service invocation or data/event
  mutation, including a seeded `HEADMAN/ABSENT` record.
- Canonical existing tests for geo happy/retry, manual absence and race pass.
- Diff is limited to attendance backend and targeted tests plus this packet,
  evidence, checks, and summary.

## Verification

Use `rct-verification`: reproduce both failures on baseline first; run targeted
unit/integration tests and relevant attendance build/checks from the current
Gradle wrapper; run isolated runtime only if required and owned. Record command,
exit code, revision, environment, and evidence in `checks.json` and `evidence.md`.

## Model / effort / ownership

- Role: bounded developer, sole writer.
- Model: `gpt-5.6-luna`; effort: `max`.
- Worktree: `.agent/worktrees/js-student-01/fix-be`.
- Branch: `codex/js-student-01-fix-be`.
- Contract revision: `FIX-r2`.
