# Teacher personal day identity

## Scope

S2; base `c7436b3bb240605c99795e63b800745c0d7b964d`; candidate branch `codex/teacher-day-identity-20260923`. A selected-day result uses a positive assignment ID owned by the actor on that date, with matching assigned teacher, group, subject, and lesson type. Changed product files:

- `services/mobile-bff/mobile-bff-app/src/main/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacade.java`
- `services/mobile-bff/mobile-bff-app/src/test/java/ru/rutcampustrack/mobilebff/teacher/TeacherReadFacadeTest.java`

Evidence policy: MAIN `RULES.md` SHA256 `1FF4F775373990D2FC1CDEB2DC525AB826929161BB0A4B679F69FE956428B39A`; `services/AGENTS.md`, `tests/AGENTS.md`, and `rct-verification/SKILL.md` read.

## Criteria

- For teachers A/B sharing group, subject, and lesson type, A retains A's lesson and excludes B's.
- B's assignment ID, a missing/default ID, mismatched assigned teacher, or mismatched context is excluded from A's day.
- Preserve output IDs, sort order, API shape, and group-wide historical journal reads.

## Source evidence and change

Before the fix, `day()` already filtered `lesson.assignedTeacherId == actor`; the leak was a same-context lesson with actor teacher ID but B's or missing `assignmentId`, accepted by the tuple fallback. Also, protobuf ID `0` could match an Academic assignment ID `0` via `byId`. The fix requires positive IDs on Academic assignments and Schedule lessons, an actor-owned date-filtered by-ID match, and exact teacher/group/subject/type. The regression asserts returned lesson IDs for A, B, wrong/missing/zero IDs, teacher mismatch, and context mismatch. Historical journal matching is untouched.

## Checks and runtime evidence

Selector: `:services:mobile-bff:mobile-bff-app:test --tests "ru.rutcampustrack.mobilebff.teacher.TeacherReadFacadeTest"`.

1. Restricted sandbox attempt: **exit 1**, before tests, `AccessDeniedException` on `shared-web-api-0.1.0.jar`; log `gradle-targeted-test.log`.
2. Same command escalated before the zero-ID correction: **exit 0**, 2m01s; log `gradle-targeted-test-escalated.log`. Superseded by the next run.
3. Final run after the zero-ID correction: **exit 0**, `BUILD SUCCESSFUL in 34s`; 39 tasks (3 executed, 36 up-to-date); log `gradle-final-targeted-test.log`.

Command: `"-Dorg.gradle.java.compile-classpath-packaging=true" --no-daemon --no-parallel --max-workers=1 --no-problems-report --console=plain :services:mobile-bff:mobile-bff-app:test --tests "ru.rutcampustrack.mobilebff.teacher.TeacherReadFacadeTest"`. Environment: Windows PowerShell, Gradle wrapper 8.12, OpenJDK VM. This is JVM evidence for the facade's returned lesson IDs; no live HTTP/database/Docker behavior is claimed.

## Limits

Limited to personal-day identity. No broad ID audit; journal history, statistics, mutation paths, DTOs, and generated contracts are unchanged. Foreign dirty `RULES.md` and `LEAF-PACKET.md` are excluded from the candidate commit.
