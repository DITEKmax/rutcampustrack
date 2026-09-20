# Decisions — JS-STUDENT-01

## 2026-09-06 — owner product delta

Literal owner statement relayed by root: «если староста не подтвердил то по
истечению таймера можно подать снова если староста отказал то тоже ... но если уже
сам староста поставил н то тут только через заявку на н».

Applied interpretation: server retry is allowed at five minutes for PENDING and
after REJECTED; at most one active PENDING per student/lesson and a failed retry
reuses it. A manual headman “н” blocks geo/automatic escalation and requires the
separate appeal flow. This explicitly supersedes the old manual-absence override in
`backend-conflicts.md:541-543,553-555` and the old permanent PENDING block. It does
not supersede the end+5m window, non-expiring automatic request, or the distinction
from the semester-limited manual appeal.

## 2026-09-06 — canonical generation

Root accepted Java-first BFF contracts as the sole public canon for r1. Exported
OpenAPI, generated TypeScript and contract fixtures are derivatives. No parallel
handwritten OpenAPI is allowed.

## 2026-09-06 — PWA offline read policy

Literal owner statement relayed by root: «офлайн ... видеть всё расписание до конца
семестра и ... все данные которые ... особо не меняются и постоянные и не влияют на
восприятие ложное (статистику можно тоже показывать но с плашкой что офлайн)».

Applied interpretation for this vertical: PWA persists a user-partitioned semester
schedule/stable snapshot in IndexedDB, keeps it visible through session expiry until
explicit logout/account switch, labels offline data with `updatedAt`, and disables
all mutations. Tokens and fresh eligibility are never inferred from cache. TMA stays
online-only. Statistics are a policy allowance, not JS-STUDENT-01 scope.

Existing code evidence supports the semester promise: `proto/schedule.proto:16-17,
49-54` exposes the group/semester/date range RPC, while `LessonGenerationService`
computes and saves dates from semester start/effective start through semester end.

## 2026-09-06 — local Git authority

Owner authorized local commits, branches, tags and merges for testing and required a
final local merge into a `master` branch, while forbidding push. Root coordinates
Git mutations so this worker cannot overlap them; current dirty main checkout is not
reset or rewritten. `master` does not yet exist and will be created from local
`main` before the verified integration merge.

## 2026-09-06 — required-nullable OpenAPI encoding

The canonical export is pinned to OpenAPI 3.0.1. Springdoc drops Java's nullable
metadata for referenced DTOs under its 3.1 export, which made required-nullable
fields non-null in generated TypeScript. The export customizer now wraps only the
annotated reference properties as `allOf + nullable`; schema assertions and real
null fixtures guard the behavior.

## 2026-09-06 — Telegram host boundary

The unused `@telegram-apps/sdk` foundation dependency was deprecated and introduced
the only four high audit findings through valibot. The TMA lane uses narrow local
types around Telegram's injected `window.Telegram.WebApp` transport for init data,
BackButton, MainButton and location. This keeps the official host API without the
unused vulnerable package; `npm audit` is clean.

## 2026-09-06 — confirmed and cancelled eligibility

Today represents an existing PRESENT mark as disabled `ALREADY_PRESENT`. A POST with
a new idempotency key returns a mutation-free PRESENT ACK with current attendance;
the original key still replays the original ACK. A cancelled lesson, when returned
by schedule, uses `LESSON_CANCELLED`, and POST returns 409 CHECKIN_NOT_ELIGIBLE.
